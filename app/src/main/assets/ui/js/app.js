(function () {
  const { $, $$, I, api, toast, confirmBox, fmtSize, esc } = window.U;
  const P = window.P;
  const { hooks } = window.U;

  const ROUTES = {
    home: { t: '首页', s: '总览你的下载与音乐库', icon: 'home' },
    playlist: { t: '歌单批量下载', s: '导入歌单，批量解析与下载', icon: 'list' },
    single: { t: '单曲下载', s: '粘贴链接，按需下载单曲', icon: 'music' },
    tasks: { t: '下载任务', s: '队列、进度与失败重试', icon: 'download' },
    library: { t: '本地音乐库', s: '扫描并整理本地歌曲', icon: 'library' },
    history: { t: '下载历史', s: '所有已完成的记录', icon: 'history' },
    settings: { t: '设置', s: '下载、文件与外观偏好', icon: 'settings' },
    about: { t: '关于', s: '数据来源与使用说明', icon: 'info' }
  };

  const S = {
    route: 'home', settings: {}, info: {}, netText: '未测试',
    stats: { queue: {}, db: {}, storage: {} }, tasks: [], history: [], playlists: [],
    playlist: { input: '', loading: false, error: '', info: null, songs: [], report: null, selected: new Set(), filter: { q: '', artist: '', state: 'all' }, limit: 200 },
    single: { input: '', loading: false, error: '', songs: [], invalid: [], selected: new Set() },
    library: { dir: '', tracks: [], stats: null, scanning: false, progress: 0, filter: { q: '', kind: 'all' }, error: '' },
    historyQuery: ''
  };
  window.S = S;

  // ---------- nav ----------
  function renderNav() {
    $('#nav').innerHTML = Object.keys(ROUTES).map(k =>
      '<a class="nav-item ' + (S.route === k ? 'active' : '') + '" data-act="go" data-route="' + k + '">' +
      I[ROUTES[k].icon] + '<span>' + ROUTES[k].t + '</span></a>').join('');
    const tabs = ['home', 'playlist', 'single', 'tasks', 'library'];
    $('#tabbar').innerHTML = tabs.map(k =>
      '<a class="tab ' + (S.route === k ? 'active' : '') + '" data-act="go" data-route="' + k + '">' +
      I[ROUTES[k].icon] + '<span>' + ROUTES[k].t.replace('批量下载', '').replace('下载', '') + '</span></a>').join('');
  }

  function renderTop() {
    const q = S.stats.queue || {};
    const active = (q.running || 0) + (q.queued || 0);
    const mini = $('#miniStats');
    if (mini) mini.innerHTML = '<div><b>' + (q.done || 0) + '</b><span>已完成</span></div>' +
      '<div><b>' + active + '</b><span>进行中</span></div>' +
      '<div><b>' + fmtSize((S.stats.db || {}).totalBytes || 0) + '</b><span>已占用</span></div>';
  }

  function setRoute(r) {
    S.route = r;
    renderNav();
    $('#pageTitle').textContent = ROUTES[r].t;
    $('#pageSub').textContent = ROUTES[r].s;
    render();
    window.scrollTo(0, 0);
  }

  function render() {
    const fn = P[S.route];
    $('#view').innerHTML = fn ? fn(S) : '<div class="card">页面不存在</div>';
    renderTop();
  }

  // ---------- event delegation ----------
  function onClick(e) {
    const el = e.target.closest('[data-act]');
    if (!el) return;
    const act = el.getAttribute('data-act');
    const h = ACTIONS[act];
    if (h) { e.preventDefault(); h(el, e); }
  }

  function onChange(e) {
    const el = e.target.closest('[data-act]');
    if (!el) return;
    const act = el.getAttribute('data-act');
    const h = ACTIONS[act];
    if (h) h(el, e);
  }

  let inputTimer = null;
  function onInput(e) {
    const t = e.target;
    if (t.id === 'plSearch') {
      S.playlist.filter.q = t.value;
      clearTimeout(inputTimer);
      inputTimer = setTimeout(refreshSongList, 220);
    } else if (t.id === 'libSearch') {
      S.library.filter.q = t.value;
      clearTimeout(inputTimer);
      inputTimer = setTimeout(render, 220);
    } else if (t.id === 'hisSearch') {
      S.historyQuery = t.value;
      clearTimeout(inputTimer);
      inputTimer = setTimeout(loadHistory, 300);
    }
  }

  // ---------- playlist ----------
  function refreshSongList() {
    const pl = S.playlist;
    if (!pl.info) return;
    const songs = pl.songs, f = pl.filter;
    const q = (f.q || '').toLowerCase();
    const list = songs.map(function (s, i) { return { s: s, i: i }; }).filter(function (x) { var s = x.s;
      if (q && (s.title || '').toLowerCase().indexOf(q) < 0 && (s.artist || '').toLowerCase().indexOf(q) < 0) return false;
      if (f.artist && s.artist !== f.artist) return false;
      if (f.state === 'undone' && s.downloaded) return false;
      if (f.state === 'done' && !s.downloaded) return false;
      if (f.state === 'unavailable' && s.available !== false) return false;
      if (f.state === 'available' && s.available === false) return false;
      if (f.state === 'dup' && !s.duplicate) return false;
      return true;
    });
    const box = $('#plList');
    if (!box) return;
    const shown = list.slice(0, pl.limit);
    box.innerHTML = shown.length ? shown.map(x => P.songRow(x.s, x.i, pl.selected.has(x.s.platformId))).join('') :
      '<div class="empty"><div class="ico">' + I.music + '</div><div class="t">没有符合条件的歌曲</div></div>';
  }

  async function loadPlaylist(input) {
    if (!input || !input.trim()) { toast('请输入歌单链接或 ID', 'err'); return; }
    S.playlist.input = input;
    S.playlist.loading = true; S.playlist.error = '';
    render();
    const r = await api('playlist.load', { input: input });
    S.playlist.loading = false;
    if (!r || !r.ok) {
      S.playlist.error = (r && r.error) || '解析失败';
      render(); toast(S.playlist.error, 'err'); return;
    }
    S.playlist.info = r.playlist;
    S.playlist.songs = r.songs || [];
    S.playlist.report = r.report || {};
    S.playlist.selected = new Set();
    S.playlist.limit = 200;
    S.playlist.filter = { q: '', artist: '', state: 'all' };
    S.playlist.error = '';
    render();
    toast('解析完成，共 ' + S.playlist.songs.length + ' 首', 'ok');
    loadPlaylists();
  }

  async function checkAvailability() {
    const pl = S.playlist;
    if (!pl.songs.length) return;
    const target = pl.selected.size ? pl.songs.filter(s => pl.selected.has(s.platformId)) : pl.songs.slice(0, 60);
    toast('正在核验 ' + target.length + ' 首的可用音源…');
    const r = await api('availability.check', { songs: target, limit: 60 });
    if (!r || !r.ok) { toast((r && r.error) || '核验失败', 'err'); return; }
    const map = new Map((r.songs || []).map(s => [s.platformId, s]));
    pl.songs.forEach(function (s) {
      const u = map.get(s.platformId);
      if (u) { s.available = u.available; s.unavailableReason = u.unavailableReason; s.level = u.level; s.format = u.format; }
    });
    render();
    const bad = (r.songs || []).filter(s => !s.available).length;
    toast('核验完成：' + (r.songs || []).length + ' 首，其中 ' + bad + ' 首不可获取', bad ? '' : 'ok');
  }

  async function downloadSongs(songs, taskType, playlistId, playlistName) {
    if (!songs.length) { toast('没有选中歌曲', 'err'); return; }
    const qEl = $('#plQuality') || $('#sgQuality');
    const quality = qEl ? qEl.value : (S.settings.quality || 'standard');
    const r = await api('tasks.create', {
      songs: songs, taskType: taskType, playlistId: playlistId || '',
      playlistName: playlistName || '', quality: quality
    });
    if (!r || !r.ok) { toast((r && r.error) || '创建任务失败', 'err'); return; }
    toast('已创建 ' + r.count + ' 个下载任务', 'ok');
    if (S.route === 'tasks') render(); else setRoute('tasks');
    loadTasks();
  }

  // ---------- tasks ----------
  async function loadTasks() {
    const r = await api('tasks.snapshot');
    if (r && r.ok) { S.tasks = r.tasks || []; S.stats.queue = r.queue || S.stats.queue; }
    if (S.route === 'tasks') render();
  }

  async function loadStats() {
    const r = await api('stats');
    if (r && r.ok) { S.stats = { queue: r.queue || {}, db: r.db || {}, storage: r.storage || {} }; renderTop(); }
  }

  async function loadHistory() {
    const r = await api('history.list', { q: S.historyQuery });
    if (r && r.ok) S.history = r.items || [];
    if (S.route === 'history') render();
  }

  async function loadPlaylists() {
    const r = await api('playlists.recent');
    if (r && r.ok) S.playlists = r.items || [];
  }

  async function netCheck() {
    const r = await api('net.check');
    const badge = $('#netBadge');
    if (r && r.ok && r.ok !== false) {
      S.netText = (r.message || '连接正常') + ' · ' + (r.latency || 0) + 'ms';
      if (badge) { badge.className = 'badge ok'; badge.textContent = '在线 ' + (r.latency || 0) + 'ms'; }
    } else {
      S.netText = (r && r.message) || '连接失败';
      if (badge) { badge.className = 'badge bad'; badge.textContent = '离线'; }
    }
  }

  // ---------- settings ----------
  async function saveSettings(values) {
    Object.assign(S.settings, values);
    const r = await api('settings.set', { values: values });
    if (r && r.ok) { S.settings = r; applyTheme(S.settings.theme); }
    if (values.theme) applyTheme(values.theme);
    if (values.uiScale) document.body.style.zoom = String(values.uiScale);
    render();
  }

  function applyTheme(t) {
    let mode = t || 'dark';
    if (mode === 'system') {
      mode = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    }
    document.documentElement.setAttribute('data-theme', mode === 'light' ? 'light' : 'dark');
    const btn = $('#themeBtn');
    if (btn) btn.innerHTML = mode === 'light' ? I.moon : I.sun;
  }

  // ---------- actions ----------
  const ACTIONS = {
    go: el => setRoute(el.getAttribute('data-route')),
    askAllFiles: async () => { await api('perm.askAllFiles'); toast('请在系统设置中授予「所有文件访问」权限'); },
    loadPlaylist: () => { const v = ($('#plInput') || {}).value || ''; loadPlaylist(v); },
    pickRecent: el => { if (el.value) loadPlaylist(el.value); },
    plState: el => { S.playlist.filter.state = el.getAttribute('data-v'); render(); },
    plSelectAll: () => { S.playlist.songs.forEach(s => S.playlist.selected.add(s.platformId)); render(); },
    plSelectNone: () => { S.playlist.selected.clear(); render(); },
    plInvert: () => {
      S.playlist.songs.forEach(function (s) {
        if (S.playlist.selected.has(s.platformId)) S.playlist.selected.delete(s.platformId);
        else S.playlist.selected.add(s.platformId);
      });
      render();
    },
    plSelectUndone: () => {
      S.playlist.selected.clear();
      S.playlist.songs.forEach(function (s) { if (!s.downloaded) S.playlist.selected.add(s.platformId); });
      render();
    },
    plMore: () => { S.playlist.limit += 200; render(); },
    toggleSong: el => {
      const i = Number(el.getAttribute('data-idx'));
      const s = S.playlist.songs[i];
      if (!s) return;
      if (S.playlist.selected.has(s.platformId)) S.playlist.selected.delete(s.platformId);
      else S.playlist.selected.add(s.platformId);
      el.classList.toggle('sel');
      const cb = el.querySelector('.cbox');
      if (cb) cb.classList.toggle('on');
      updateSelBar();
    },
    checkAvail: checkAvailability,
    downloadSelected: () => {
      const pl = S.playlist;
      const sel = pl.songs.filter(s => pl.selected.has(s.platformId) && s.available !== false);
      const skipped = pl.selected.size - sel.length;
      if (!sel.length) { toast('选中的歌曲都不可下载或未选择', 'err'); return; }
      if (skipped > 0) toast(skipped + ' 首不可下载已跳过');
      downloadSongs(sel, 'playlist', pl.info ? pl.info.platformId : '', pl.info ? pl.info.name : '');
    },
    parseSingle: async () => {
      const text = ($('#sgInput') || {}).value || '';
      if (!text.trim()) { toast('请输入歌曲链接', 'err'); return; }
      S.single.input = text; S.single.loading = true; S.single.error = '';
      render();
      const r = await api('single.parse', { text: text });
      S.single.loading = false;
      if (!r || !r.ok) { S.single.error = (r && r.error) || '解析失败'; render(); toast(S.single.error, 'err'); return; }
      S.single.songs = r.songs || [];
      S.single.invalid = r.invalid || [];
      S.single.selected = new Set(S.single.songs.filter(s => s.available).map(s => s.platformId));
      render();
      toast('解析成功 ' + S.single.songs.length + ' 首', 'ok');
    },
    toggleSingle: el => {
      const i = Number(el.getAttribute('data-idx'));
      const s = S.single.songs[i];
      if (!s) return;
      if (S.single.selected.has(s.platformId)) S.single.selected.delete(s.platformId);
      else S.single.selected.add(s.platformId);
      el.classList.toggle('sel');
      const cb = el.querySelector('.cbox');
      if (cb) cb.classList.toggle('on');
    },
    sgSelectAll: () => { S.single.songs.forEach(s => S.single.selected.add(s.platformId)); render(); },
    sgSelectNone: () => { S.single.selected.clear(); render(); },
    downloadSingles: () => {
      const sel = S.single.songs.filter(s => S.single.selected.has(s.platformId));
      downloadSongs(sel, 'single', '', '');
    },
    setFlag: el => { const k = el.getAttribute('data-k'); el.classList.toggle('on'); saveSettings({ [k]: el.classList.contains('on') }); },
    setStr: el => { const k = el.getAttribute('data-k'); const v = el.value; saveSettings({ [k]: v }); },
    setNum: el => { const k = el.getAttribute('data-k'); const v = Number(el.value); saveSettings({ [k]: v }); },
    saveCookie: async function () {
      const el = document.getElementById('cookieInput');
      const v = el ? el.value.trim() : '';
      if (!v) { toast('请先粘贴 Cookie', 'err'); return; }
      if (v.length < 8) { toast('Cookie 看起来不完整', 'err'); return; }
      await saveSettings({ cookie: v });
      toast('Cookie 已保存，重新核验可用性试试', 'ok');
    },
    clearCookie: async function () {
      if (!await confirmBox({ title: '清除账号 Cookie', text: '清除后将恢复匿名访问，会员曲目将重新变为不可获取。', ok: '清除', danger: true })) return;
      await saveSettings({ cookie: '' });
      toast('已清除');
    },
    netCheck: async () => { await netCheck(); render(); toast(S.netText); },
    showLog: async () => {
      const r = await api('log.tail');
      const mask = document.createElement('div');
      mask.className = 'mask';
      mask.innerHTML = '<div class="modal" style="width:min(680px,96vw)"><h3>运行日志</h3>' +
        '<pre style="max-height:56vh;overflow:auto;font-size:11px;line-height:1.6;color:var(--fg-2);white-space:pre-wrap;word-break:break-all;margin-top:10px;background:var(--bg-2);padding:12px;border-radius:10px">' +
        esc((r && r.text) || '暂无日志') + '</pre><div class="m-actions"><button class="btn primary" data-close>关闭</button></div></div>';
      document.body.appendChild(mask);
      mask.querySelector('[data-close]').onclick = () => mask.remove();
      mask.onclick = e => { if (e.target === mask) mask.remove(); };
    },
    dbBackup: async () => {
      const r = await api('db.backup');
      if (r && r.ok) toast('已备份到 ' + r.path, 'ok', 4000);
      else toast((r && r.error) || '备份失败', 'err');
    },
    resetSettings: async () => {
      if (!await confirmBox({ title: '恢复默认设置', text: '所有偏好将恢复默认，已下载文件不受影响。', ok: '恢复', danger: true })) return;
      const r = await api('settings.reset');
      if (r && r.ok) { S.settings = r; applyTheme('dark'); render(); toast('已恢复默认设置', 'ok'); }
    },
    taskPause: el => api('tasks.pause', { taskId: el.getAttribute('data-id') }).then(loadTasks),
    taskResume: el => api('tasks.resume', { taskId: el.getAttribute('data-id') }).then(loadTasks),
    taskCancel: el => api('tasks.cancel', { taskId: el.getAttribute('data-id') }).then(loadTasks),
    taskDelete: async el => {
      if (!await confirmBox({ title: '删除任务', text: '仅从列表移除任务记录，不会删除已下载的文件。', ok: '删除', danger: true })) return;
      await api('tasks.delete', { taskId: el.getAttribute('data-id') });
      loadTasks();
    },
    pauseAll: async () => { await api('tasks.pauseAll'); toast('队列已暂停'); loadTasks(); },
    resumeAll: async () => { await api('tasks.resumeAll'); toast('队列已恢复', 'ok'); loadTasks(); },
    retryFailed: async () => { const r = await api('tasks.retryFailed'); toast('已重新排队 ' + (r ? r.count : 0) + ' 个任务', 'ok'); loadTasks(); },
    clearFinished: async () => {
      if (!await confirmBox({ title: '清空已完成', text: '将移除已完成/失败/取消的任务记录，文件不受影响。', ok: '清空', danger: true })) return;
      await api('tasks.clearFinished'); loadTasks();
    },
    openPath: async el => {
      const p = el.getAttribute('data-path');
      const r = await api('fs.openPath', { path: p });
      if (!r || !r.ok || r.opened === false) toast('无法打开，路径：' + (p || ''), '', 4200);
    },
    scanLibrary: async () => {
      const dir = ($('#libDir') || {}).value || '';
      S.library.dir = dir; S.library.scanning = true; S.library.progress = 0; S.library.error = '';
      render();
      const r = await api('library.scan', { dir: dir });
      S.library.scanning = false;
      if (!r || !r.ok) { S.library.error = (r && r.error) || '扫描失败'; render(); toast(S.library.error, 'err'); return; }
      S.library.tracks = r.tracks || [];
      S.library.stats = r.stats || {};
      render();
      toast('扫描完成，识别 ' + (S.library.tracks.length) + ' 首', 'ok');
    },
    libKind: el => { S.library.filter.kind = el.getAttribute('data-v'); render(); },
    libDups: async () => {
      toast('正在检查重复文件…');
      const r = await api('library.dups', { dir: S.library.dir || (S.info && S.info.dir) });
      if (!r || !r.ok) { toast('检查失败', 'err'); return; }
      const groups = r.groups || [];
      const mask = document.createElement('div');
      mask.className = 'mask';
      mask.innerHTML = '<div class="modal" style="width:min(560px,96vw)"><h3>重复文件检查</h3>' +
        '<pre style="max-height:50vh;overflow:auto;font-size:11.5px;line-height:1.7;color:var(--fg-2);white-space:pre-wrap;word-break:break-all;margin-top:10px;background:var(--bg-2);padding:12px;border-radius:10px">' +
        (groups.length ? groups.map(g => '【' + esc(g.key) + '】\n' + g.files.map(f => '  ' + f.path + '  (' + fmtSize(f.size) + ')').join('\n')).join('\n\n')
          : '未发现同名重复文件') + '</pre><div class="m-actions"><button class="btn primary" data-close>关闭</button></div></div>';
      document.body.appendChild(mask);
      mask.querySelector('[data-close]').onclick = () => mask.remove();
      mask.onclick = e => { if (e.target === mask) mask.remove(); };
    },
    loadHistory: loadHistory,
    selfCheck: async function () {
      const lines = [];
      const add = function (k, v) { lines.push(k + '：' + v); };
      try {
        const pg = await api('ping');
        add('原生桥接', pg && pg.msg === 'pong' ? '正常 v' + (pg.version || '') : '异常');
      } catch (e) { add('原生桥接', '调用失败'); }
      try {
        const n = await api('net.check');
        add('网络', (n && n.ok) ? '在线，' + (n.latency || 0) + ' ms' : ('离线 — ' + ((n && n.message) || '未知')));
      } catch (e) { add('网络', '检测失败'); }
      try {
        const st = await api('stats');
        add('保存目录', (st.storage && st.storage.dir) || '—');
        add('可用空间', fmtSize((st.storage && st.storage.free) || 0));
        add('已占用', fmtSize((st.storage && st.storage.used) || 0));
      } catch (e) { add('存储', '读取失败'); }
      try {
        const ls = await api('fs.list', { path: (S.info && S.info.dir) || '' });
        add('目录读取', ls && ls.ok ? ('正常，' + ((ls.files || []).length) + ' 个条目') : '失败');
      } catch (e) { add('目录读取', '失败'); }
      add('文件权限', S.info && S.info.allFiles ? '已授权（可写公共目录）' : '未授权（建议到首页授权）');
      const q = S.stats.queue || {};
      add('任务队列', (q.total || 0) + ' 个任务，进行中 ' + (q.running || 0));
      const db = S.stats.db || {};
      add('数据库', (db.totalCount || 0) + ' 条历史记录');
      const mask = document.createElement('div');
      mask.className = 'mask';
      const pre = document.createElement('pre');
      pre.style.cssText = 'max-height:52vh;overflow:auto;font-size:12px;line-height:1.8;color:var(--fg-2);white-space:pre-wrap;margin-top:10px;background:var(--bg-2);padding:12px;border-radius:10px';
      pre.textContent = lines.join(String.fromCharCode(10));
      const box = document.createElement('div');
      box.className = 'modal';
      const h = document.createElement('h3');
      h.textContent = '运行自检';
      const acts = document.createElement('div');
      acts.className = 'm-actions';
      const btn = document.createElement('button');
      btn.className = 'btn primary';
      btn.textContent = '关闭';
      btn.onclick = function () { mask.remove(); };
      acts.appendChild(btn);
      box.appendChild(h); box.appendChild(pre); box.appendChild(acts);
      mask.appendChild(box);
      mask.onclick = function (e) { if (e.target === mask) mask.remove(); };
      document.body.appendChild(mask);
    }
  };

  function updateSelBar() {
    const bar = document.querySelector('[data-act="downloadSelected"]');
    if (!bar) return;
    const p = bar.parentElement.parentElement.querySelector('b');
    if (p) p.textContent = '已选 ' + S.playlist.selected.size + ' 首';
  }

  // ---------- push events ----------
  hooks.tasks = function (list) {
    if (!Array.isArray(list)) return;
    list.forEach(function (t) {
      const i = S.tasks.findIndex(x => x.taskId === t.taskId);
      if (i >= 0) S.tasks[i] = t; else S.tasks.unshift(t);
      if (S.route === 'tasks') {
        const node = document.querySelector('[data-task="' + t.taskId + '"]');
        if (node) { const d = document.createElement('div'); d.innerHTML = P.taskCard(t); node.replaceWith(d.firstChild); }
      }
    });
    if (S.route === 'tasks') {
      const box = document.querySelector('#view .card.pad-0');
      if (box && !box.querySelector('[data-task]') && S.tasks.length) render();
      const bar = document.querySelector('#view .bar i');
      const q = S.stats.queue || {};
      if (bar && q.percent != null) bar.style.width = q.percent + '%';
    }
  };
  hooks.stats = function (p) {
    if (!p) return;
    S.stats.queue = p.queue || S.stats.queue;
    S.stats.db = p.db || S.stats.db;
    renderTop();
    if (S.route === 'home' || S.route === 'tasks') {
      clearTimeout(hooks._t);
      hooks._t = setTimeout(function () { if (S.route === 'home') render(); }, 1200);
    }
  };
  hooks.progress = function (p) {
    if (p && p.stage === 'library') {
      S.library.progress = p.total ? Math.round(p.cur * 100 / p.total) : 0;
      if (S.route === 'library') {
        const box = document.querySelector('#view .card');
        if (box) {
          const el = box.querySelector('.spin');
          if (el) el.parentElement.innerHTML = '<span class="spin"></span> 扫描中 ' + S.library.progress + '%';
        }
      }
    }
  };
  hooks.ready = function () { init(); };

  // ---------- init ----------
  let inited = false;
  async function init() {
    if (inited) return;
    inited = true;
    try {
      const info = await api('app.info');
      if (info && info.ok !== false) S.info = info;
      const st = await api('settings.get');
      if (st && st.ok !== false) S.settings = st;
      applyTheme(S.settings.theme || 'dark');
      if (S.settings.uiScale) document.body.style.zoom = String(S.settings.uiScale);
      $('#themeBtn').innerHTML = I.sun;
      await Promise.all([loadStats(), loadTasks(), loadHistory(), loadPlaylists()]);
      netCheck();
      setRoute('home');
      hideBoot();
    } catch (e) {
      console.error(e);
      toast('初始化失败：' + e.message, 'err');
    }
  }

  document.addEventListener('click', onClick);
  document.addEventListener('change', onChange);
  document.addEventListener('input', onInput);
  $('#themeBtn').addEventListener('click', function () {
    const cur = document.documentElement.getAttribute('data-theme');
    saveSettings({ theme: cur === 'dark' ? 'light' : 'dark' });
  });

  window.__onBack = function () {
    if (S.route !== 'home') { setRoute('home'); return true; }
    return false;
  };

  function hideBoot() {
    const b = $('#boot');
    if (b) { b.classList.add('done'); setTimeout(function () { if (b.parentNode) b.remove(); }, 430); }
  }
  setTimeout(function () { init(); }, 60);
  setTimeout(function () {
    const b = $('#boot');
    if (b && !b.classList.contains('done')) {
      const m = $('#bootMsg');
      if (m) m.innerHTML = '启动超时：界面未能完成初始化。<br>请完全退出应用后重新打开；若仍然如此，请将此提示截图反馈。';
    }
  }, 6000);
})();
