(function () {
  const { I, esc, fmtSize, fmtDur, fmtTime, statusText, statusClass, emptyBox } = window.U;
  const P = {};

  const cov = (u, size) => u ? (String(u).split('http://').join('https://') + (String(u).indexOf('?') < 0 ? '?param=' + (size || 120) + 'y' + (size || 120) : '')) : '';
  const greet = () => { const h = new Date().getHours(); return h < 6 ? '夜深了' : h < 11 ? '早上好' : h < 14 ? '中午好' : h < 19 ? '下午好' : '晚上好'; };

  P.songRow = function (s, i, selected, extra) {
    const c = cov(s.coverUrl);
    const tags = [];
    if (s.downloaded) tags.push('<span class="tag ok">已下载</span>');
    if (s.duplicate) tags.push('<span class="tag warn">重复</span>');
    if (s.available === false) tags.push('<span class="tag bad" title="' + esc(s.unavailableReason || '') + '">不可下载</span>');
    else if (s.available === true) tags.push('<span class="tag ok">可下载</span>');
    if (s.level) tags.push('<span class="tag info">' + esc(String(s.level).toUpperCase()) + '</span>');
    return '<div class="song ' + (selected ? 'sel' : '') + '" data-act="toggleSong" data-idx="' + i + '">' +
      '<div class="cbox ' + (selected ? 'on' : '') + '">' + I.check + '</div>' +
      '<div class="idx">' + (i + 1) + '</div>' +
      (c ? '<img class="cov" src="' + esc(c) + '" loading="lazy" onerror="this.style.display=\'none\'">' : '<div class="cov"></div>') +
      '<div class="meta"><div class="t">' + esc(s.title) + '</div><div class="a">' + esc(s.artist) +
      (s.album ? ' · ' + esc(s.album) : '') + '</div></div>' +
      '<div class="row" style="gap:6px">' + tags.join('') + '</div>' +
      '<div class="dur actxt">' + fmtDur(s.duration) + '</div>' +
      '</div>';
  };

  P.home = function (S) {
    const st = S.stats || {}, q = st.queue || {}, db = st.db || {}, storage = st.storage || {};
    const tasks = (S.tasks || []).filter(t => ['running', 'parsing', 'queued', 'paused'].includes(t.status)).slice(0, 3);
    const recent = (S.history || []).slice(0, 5);
    const noPerm = S.info && S.info.allFiles === false;
    return '<div class="grid" style="gap:16px">' +
      '<div class="hero"><h2>' + greet() + '，欢迎使用云音乐管家</h2>' +
      '<p>导入网易云音乐歌单或单曲链接，自动解析歌曲信息与可用音源，下载后按规则写入元数据、封面与歌词，并按歌手/专辑/歌单自动归档。</p>' +
      '<div class="hero-actions">' +
      '<button class="btn primary" data-act="go" data-route="playlist">' + I.list + ' 歌单批量下载</button>' +
      '<button class="btn" data-act="go" data-route="single">' + I.music + ' 单曲下载</button>' +
      '<button class="btn ghost" data-act="go" data-route="tasks">' + I.download + ' 任务中心</button>' +
      '</div></div>' +
      (noPerm ? '<div class="card" style="border-color:rgba(251,191,36,.35)"><div class="row between"><div><div style="font-size:13.5px;font-weight:600">需要「所有文件访问」权限</div>' +
        '<div class="hint" style="margin-top:3px">未授权时文件将保存到受限目录。授权后才能写入 /Download 等公共目录。</div></div>' +
        '<button class="btn sm primary" data-act="askAllFiles">去授权</button></div></div>' : '') +
      '<div class="grid g4">' +
      '<div class="stat"><div class="k">今日下载</div><div class="v">' + (db.todayCount || 0) + '</div><div class="s">首</div></div>' +
      '<div class="stat"><div class="k">累计下载</div><div class="v">' + (db.totalCount || 0) + '</div><div class="s">' + fmtSize(db.totalBytes || 0) + '</div></div>' +
      '<div class="stat"><div class="k">进行中</div><div class="v">' + (q.running || 0) + '</div><div class="s">等待 ' + (q.queued || 0) + ' · 暂停 ' + (q.paused || 0) + '</div></div>' +
      '<div class="stat"><div class="k">下载空间</div><div class="v">' + fmtSize(storage.used || 0) + '</div><div class="s">可用 ' + fmtSize(storage.free || 0) + '</div></div>' +
      '</div>' +
      '<div class="grid g2">' +
      '<div class="card pad-0"><div class="row between" style="padding:14px 16px"><b style="font-size:13.5px">当前任务</b>' +
      '<button class="btn sm ghost" data-act="go" data-route="tasks">查看全部</button></div>' +
      (tasks.length ? tasks.map(t => '<div class="task" style="padding:12px 16px"><div class="tm"><div class="t">' + esc(t.title) + '</div>' +
        '<div class="a">' + esc(t.artist) + ' · ' + statusText(t.status) + '</div></div>' +
        '<div class="bar sm" style="margin-top:8px"><i style="width:' + (t.progress || 0) + '%"></i></div></div>').join('')
        : emptyBox('暂无进行中的任务', '在歌单或单曲页面发起下载', I.download)) +
      '</div>' +
      '<div class="card pad-0"><div class="row between" style="padding:14px 16px"><b style="font-size:13.5px">最近下载</b>' +
      '<button class="btn sm ghost" data-act="go" data-route="history">下载历史</button></div>' +
      (recent.length ? recent.map(h => '<div class="song" style="padding:10px 16px"><div class="meta"><div class="t">' + esc(h.title) + '</div>' +
        '<div class="a">' + esc(h.artist) + ' · ' + fmtSize(h.fileSize) + '</div></div><div class="dur">' + fmtTime(h.downloadedAt) + '</div></div>').join('')
        : emptyBox('还没有下载记录', '完成一次下载后会显示在这里', I.history)) +
      '</div></div></div>';
  };

  P.playlist = function (S) {
    const pl = S.playlist;
    let out = '<div class="card" style="margin-bottom:14px">' +
      '<div class="field"><label>歌单 / 专辑链接或 ID</label>' +
      '<input type="text" id="plInput" placeholder="https://music.163.com/playlist?id=3778678 或直接输入 ID" value="' + esc(pl.input || '') + '"></div>' +
      '<div class="row" style="margin-top:12px"><button class="btn primary" data-act="loadPlaylist">' + I.search + ' 解析歌单</button>' +
      '<span class="hint">支持歌单链接、分享链接、专辑链接与纯数字 ID</span>' +
      '<span class="spacer"></span>' +
      (S.playlists && S.playlists.length ? '<select id="recentPl" style="max-width:190px" data-act="pickRecent"><option value="">最近导入…</option>' +
        S.playlists.map(p => '<option value="' + p.platformId + '">' + esc(p.name) + '</option>').join('') + '</select>' : '') +
      '</div>' +
      (pl.loading ? '<div class="row" style="margin-top:12px;color:var(--fg-3);font-size:12.5px"><span class="spin"></span> 正在解析歌单…</div>' : '') +
      (pl.error ? '<div class="row" style="margin-top:12px;color:var(--danger);font-size:12.5px">' + esc(pl.error) + '</div>' : '') +
      '</div>';

    if (!pl.info) {
      return out + '<div class="card pad-0">' + emptyBox('尚未导入歌单', '粘贴网易云歌单链接开始，解析后可批量下载', I.list) + '</div>';
    }
    const info = pl.info, rep = pl.report || {}, songs = pl.songs || [];
    const f = pl.filter;
    const sel = pl.selected;
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
    const shown = list.slice(0, pl.limit);
    const artists = Array.from(new Set(songs.map(s => s.artist))).slice(0, 120);

    out += '<div class="card" style="margin-bottom:14px"><div class="row" style="gap:14px;align-items:flex-start">' +
      (info.cover ? '<img src="' + esc(cov(info.cover)) + '" style="width:78px;height:78px;border-radius:12px;object-fit:cover">' : '') +
      '<div style="flex:1;min-width:0"><div style="font-size:17px;font-weight:650">' + esc(info.name || '未知歌单') + '</div>' +
      '<div class="hint" style="margin-top:4px">' + esc(info.creator || '') + ' · 共 ' + (info.songCount || songs.length) + ' 首 · 已解析 ' + songs.length + ' 首</div>' +
      '<div class="row" style="margin-top:10px;gap:6px">' +
      '<span class="chip">已下载 ' + (rep.downloaded || 0) + '</span>' +
      '<span class="chip">重复 ' + (rep.duplicate || 0) + '</span>' +
      '<span class="chip">待核验 ' + (rep.unavailable || 0) + '</span>' +
      '</div></div>' +
      '<button class="btn ghost sm" data-act="checkAvail">核验可下载性</button>' +
      '</div></div>';

    out += '<div class="card pad-0">' +
      '<div class="toolbar" style="padding:12px 14px 0">' +
      '<div class="search">' + I.search + '<input type="text" id="plSearch" placeholder="搜索歌曲或歌手" value="' + esc(f.q || '') + '"></div>' +
      '<select id="plArtist" style="max-width:150px"><option value="">全部歌手</option>' +
      artists.map(a => '<option value="' + esc(a) + '"' + (f.artist === a ? ' selected' : '') + '>' + esc(a) + '</option>').join('') + '</select>' +
      '</div>' +
      '<div class="toolbar" style="padding:8px 14px">' +
      ['all', 'undone', 'done', 'dup'].map(k => '<span class="chip ' + (f.state === k ? 'on' : '') + '" data-act="plState" data-v="' + k + '">' +
        ({ all: '全部', undone: '未下载', done: '已下载', dup: '重复' })[k] + '</span>').join('') +
      '<span class="spacer"></span>' +
      '<button class="btn sm ghost" data-act="plSelectAll">全选</button>' +
      '<button class="btn sm ghost" data-act="plSelectNone">全不选</button>' +
      '<button class="btn sm ghost" data-act="plInvert">反选</button>' +
      '<button class="btn sm ghost" data-act="plSelectUndone">仅未下载</button>' +
      '</div>' +
      '<div id="plList">' + (shown.length ? shown.map(function (x) { return P.songRow(x.s, x.i, sel.has(x.s.platformId)); }).join('') :
        emptyBox('没有符合条件的歌曲', '调整筛选或搜索条件试试', I.music)) + '</div>' +
      (list.length > shown.length ? '<div style="padding:12px;text-align:center"><button class="btn sm" data-act="plMore">加载更多（还有 ' + (list.length - shown.length) + ' 首）</button></div>' : '') +
      '</div>' +
      '<div class="card pl-bar"><div class="row between">' +
      '<div><b style="font-size:14px">已选 ' + sel.size + ' 首</b>' +
      '<div class="hint" style="margin-top:2px">音质偏好：' + esc(S.settings.quality || 'standard') + ' · 保存到 ' + esc((S.settings.downloadDir || '').split('/').pop() || '') + '</div></div>' +
      '<div class="row">' +
      '<select id="plQuality" style="width:118px"><option value="standard"' + (S.settings.quality === 'standard' ? ' selected' : '') + '>标准 128k</option>' +
      '<option value="higher"' + (S.settings.quality === 'higher' ? ' selected' : '') + '>较高 192k</option>' +
      '<option value="exhigh"' + (S.settings.quality === 'exhigh' ? ' selected' : '') + '>极高 320k</option>' +
      '<option value="lossless"' + (S.settings.quality === 'lossless' ? ' selected' : '') + '>无损</option></select>' +
      '<button class="btn primary" data-act="downloadSelected">' + I.download + ' 下载选中</button>' +
      '</div></div></div>';
    return out;
  };

  P.single = function (S) {
    const g = S.single;
    let out = '<div class="card" style="margin-bottom:14px">' +
      '<div class="field"><label>歌曲链接（每行一个，可批量）</label>' +
      '<textarea id="sgInput" placeholder="https://music.163.com/song?id=347230&#10;也可直接粘贴分享文本或歌曲 ID">' + esc(g.input || '') + '</textarea></div>' +
      '<div class="row" style="margin-top:12px"><button class="btn primary" data-act="parseSingle">' + I.search + ' 解析歌曲</button>' +
      '<span class="hint">支持分享链接、短链、纯数字 ID，最多 100 条</span>' +
      (g.loading ? '<span class="hint"><span class="spin"></span> 解析中…</span>' : '') + '</div>' +
      (g.error ? '<div class="row" style="margin-top:10px;color:var(--danger);font-size:12.5px">' + esc(g.error) + '</div>' : '') +
      '</div>';

    out += '<div class="card" style="margin-bottom:14px"><b style="font-size:13.5px">下载选项</b><div style="margin-top:8px">' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">音质</div><div class="sr-desc">以平台实际提供的音源为准，不同歌曲可用音质不同</div></div>' +
      '<select id="sgQuality" style="width:130px">' +
      [['standard', '标准 128k'], ['higher', '较高 192k'], ['exhigh', '极高 320k'], ['lossless', '无损']].map(o =>
        '<option value="' + o[0] + '"' + (S.settings.quality === o[0] ? ' selected' : '') + '>' + o[1] + '</option>').join('') + '</select></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">下载歌词 LRC</div></div><div class="switch ' + (S.settings.saveLyric ? 'on' : '') + '" data-act="setFlag" data-k="saveLyric"></div></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">保存封面图片</div></div><div class="switch ' + (S.settings.saveCover ? 'on' : '') + '" data-act="setFlag" data-k="saveCover"></div></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">写入歌曲元数据</div></div><div class="switch ' + (S.settings.writeMeta ? 'on' : '') + '" data-act="setFlag" data-k="writeMeta"></div></div>' +
      '</div></div>';

    if (g.songs.length) {
      out += '<div class="card pad-0"><div class="toolbar" style="padding:12px 14px"><b style="font-size:13.5px">解析结果 ' + g.songs.length + ' 首</b>' +
        '<span class="spacer"></span><button class="btn sm ghost" data-act="sgSelectAll">全选</button>' +
        '<button class="btn sm ghost" data-act="sgSelectNone">全不选</button></div>' +
        g.songs.map(function (s, i) {
          const sel = g.selected.has(s.platformId);
          const c = cov(s.coverUrl);
          return '<div class="song ' + (sel ? 'sel' : '') + '" data-act="toggleSingle" data-idx="' + i + '">' +
            '<div class="cbox ' + (sel ? 'on' : '') + '">' + I.check + '</div>' +
            (c ? '<img class="cov" src="' + esc(c) + '" loading="lazy">' : '<div class="cov"></div>') +
            '<div class="meta"><div class="t">' + esc(s.title) + '</div><div class="a">' + esc(s.artist) + (s.album ? ' · ' + esc(s.album) : '') + '</div></div>' +
            '<div class="row" style="gap:6px">' +
            (s.downloaded ? '<span class="tag ok">已下载</span>' : '') +
            (s.available ? '<span class="tag ok">可下载</span>' : '<span class="tag bad" title="' + esc(s.unavailableReason || '') + '">不可下载</span>') +
            (s.level ? '<span class="tag info">' + esc(String(s.level).toUpperCase()) + '</span>' : '') +
            (s.format ? '<span class="tag">' + esc(String(s.format).toUpperCase()) + '</span>' : '') +
            '</div><div class="dur actxt">' + fmtDur(s.duration) + '</div></div>';
        }).join('') +
        '<div style="padding:14px;border-top:1px solid var(--line)"><div class="row between">' +
        '<b style="font-size:13.5px">已选 ' + g.selected.size + ' 首</b>' +
        '<button class="btn primary" data-act="downloadSingles">' + I.download + ' 开始下载</button></div></div></div>';
    }
    if (g.invalid.length) {
      out += '<div class="card" style="margin-top:14px"><b style="font-size:13.5px;color:var(--warn)">无法解析 ' + g.invalid.length + ' 条</b>' +
        g.invalid.map(x => '<div class="row" style="margin-top:8px;font-size:12.5px;color:var(--fg-2)"><code style="color:var(--fg-3)">' +
          esc(x.input) + '</code><span class="spacer"></span><span style="color:var(--danger)">' + esc(x.reason) + '</span></div>').join('') + '</div>';
    }
    return out;
  };

  window.P = P;
})();
