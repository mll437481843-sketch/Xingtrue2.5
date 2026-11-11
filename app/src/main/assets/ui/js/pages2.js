(function () {
  const { I, esc, fmtSize, fmtDur, fmtTime, statusText, statusClass, emptyBox } = window.U;
  const P = window.P;

  P.taskCard = function (t) {
    const sc = statusClass(t.status);
    const pct = t.status === 'done' ? 100 : (t.progress || 0);
    const speed = t.speed ? fmtSize(t.speed) + '/s' : '';
    const ops = [];
    if (t.status === 'running' || t.status === 'parsing') ops.push('<button class="btn sm ghost" data-act="taskPause" data-id="' + t.taskId + '">' + I.pause + ' 暂停</button>');
    if (t.status === 'paused' || t.status === 'failed' || t.status === 'canceled') ops.push('<button class="btn sm ghost" data-act="taskResume" data-id="' + t.taskId + '">' + I.play + ' 继续</button>');
    if (['running', 'parsing', 'queued', 'paused'].includes(t.status)) ops.push('<button class="btn sm ghost" data-act="taskCancel" data-id="' + t.taskId + '">' + I.x + ' 取消</button>');
    if (t.status === 'done' && t.filePath) ops.push('<button class="btn sm ghost" data-act="openPath" data-path="' + esc(t.filePath) + '">' + I.folder + ' 打开</button>');
    ops.push('<button class="btn sm ghost danger" data-act="taskDelete" data-id="' + t.taskId + '">' + I.trash + '</button>');
    return '<div class="task" data-task="' + t.taskId + '"><div class="th">' +
      (t.coverUrl ? '<img class="cov" style="width:42px;height:42px;border-radius:9px;object-fit:cover" src="' + esc(String(t.coverUrl).replace(/^http:\/\//, 'https://') + '?param=90y90') + '">' : '<div class="cov" style="width:42px;height:42px;border-radius:9px;background:var(--panel-2)"></div>') +
      '<div class="tm"><div class="t">' + esc(t.title) + '</div><div class="a">' + esc(t.artist) +
      (t.playlistName ? ' · ' + esc(t.playlistName) : '') + '</div></div>' +
      '<span class="tag ' + sc + '">' + statusText(t.status) + '</span></div>' +
      '<div class="bar" style="margin-top:10px"><i style="width:' + pct + '%"></i></div>' +
      '<div class="prog-row"><span>' + pct + '% · ' + fmtSize(t.totalBytes) + (speed ? ' · ' + speed : '') +
      (t.retryCount ? ' · 重试 ' + t.retryCount + ' 次' : '') + '</span><span>' + (t.errorMessage ? esc(t.errorMessage) : '') + '</span></div>' +
      '<div class="ops">' + ops.join('') + '</div></div>';
  };

  P.tasks = function (S) {
    const q = (S.stats && S.stats.queue) || {};
    const list = S.tasks || [];
    return '<div class="grid g4" style="margin-bottom:14px">' +
      '<div class="stat"><div class="k">总任务</div><div class="v">' + (q.total || 0) + '</div><div class="s">完成 ' + (q.done || 0) + '</div></div>' +
      '<div class="stat"><div class="k">下载中</div><div class="v">' + (q.running || 0) + '</div><div class="s">并发上限 ' + (q.maxConcurrent || 2) + '</div></div>' +
      '<div class="stat"><div class="k">等待</div><div class="v">' + (q.queued || 0) + '</div><div class="s">暂停 ' + (q.paused || 0) + '</div></div>' +
      '<div class="stat"><div class="k">失败 / 不可获取</div><div class="v">' + ((q.failed || 0) + (q.unavailable || 0)) + '</div><div class="s">已取消 ' + (q.canceled || 0) + '</div></div>' +
      '</div>' +
      '<div class="card" style="margin-bottom:14px"><div class="row between">' +
      '<div style="flex:1"><div class="row between" style="font-size:12px;color:var(--fg-3)"><span>总体进度 ' + (q.percent || 0) + '%</span>' +
      '<span>' + (q.speed ? fmtSize(q.speed) + '/s' : '空闲') + '</span></div>' +
      '<div class="bar" style="margin-top:6px"><i style="width:' + (q.percent || 0) + '%"></i></div></div>' +
      '<div class="row" style="margin-left:16px">' +
      (q.pausedAll ? '<button class="btn sm primary" data-act="resumeAll">' + I.play + ' 恢复队列</button>'
        : '<button class="btn sm ghost" data-act="pauseAll">' + I.pause + ' 暂停队列</button>') +
      '<button class="btn sm ghost" data-act="retryFailed">' + I.refresh + ' 重试失败</button>' +
      '<button class="btn sm ghost" data-act="clearFinished">' + I.trash + ' 清空已完成</button>' +
      '</div></div></div>' +
      '<div class="card pad-0">' + (list.length ? list.map(t => P.taskCard(t)).join('') :
        emptyBox('任务队列为空', '在歌单批量下载或单曲下载中创建任务', I.download)) + '</div>';
  };

  P.library = function (S) {
    const L = S.library;
    const st = L.stats || {};
    const f = L.filter || {};
    const tracks = (L.tracks || []).filter(function (t) {
      const q = (f.q || '').toLowerCase();
      if (q && (t.title + t.artist + t.album).toLowerCase().indexOf(q) < 0) return false;
      if (f.kind === 'nocover' && t.hasCover) return false;
      if (f.kind === 'nometa' && t.hasMeta) return false;
      return true;
    }).slice(0, 400);
    return '<div class="card" style="margin-bottom:14px">' +
      '<div class="row"><div class="field" style="flex:1"><label>扫描目录</label>' +
      '<input type="text" id="libDir" value="' + esc(L.dir || (S.info && S.info.dir) || '') + '"></div>' +
      '<button class="btn primary" style="margin-top:19px" data-act="scanLibrary">' + I.refresh + ' 扫描</button>' +
      '<button class="btn" style="margin-top:19px" data-act="libDups">查重</button>' +
      '</div>' +
      '<div class="row" style="margin-top:10px;gap:14px;font-size:12px;color:var(--fg-3)">' +
      '<span>曲目 ' + (st.count || 0) + '</span><span>缺封面 ' + (st.noCover || 0) + '</span>' +
      '<span>缺元数据 ' + (st.noMeta || 0) + '</span><span>占用 ' + fmtSize(st.totalSize || 0) + '</span>' +
      (L.scanning ? '<span><span class="spin"></span> 扫描中 ' + (L.progress || 0) + '%</span>' : '') + '</div>' +
      (L.error ? '<div class="row" style="margin-top:8px;color:var(--danger);font-size:12.5px">' + esc(L.error) + '</div>' : '') +
      '</div>' +
      '<div class="card pad-0"><div class="toolbar" style="padding:12px 14px">' +
      '<div class="search">' + I.search + '<input type="text" id="libSearch" placeholder="搜索本地歌曲" value="' + esc(f.q || '') + '"></div>' +
      ['all', 'nocover', 'nometa'].map(k => '<span class="chip ' + (f.kind === k || (!f.kind && k === 'all') ? 'on' : '') +
        '" data-act="libKind" data-v="' + k + '">' + ({ all: '全部', nocover: '缺封面', nometa: '缺元数据' })[k] + '</span>').join('') +
      '</div>' +
      (tracks.length ? tracks.map(t => '<div class="song" data-act="openPath" data-path="' + esc(t.path) + '">' +
        '<div class="cov" style="display:grid;place-items:center;color:var(--fg-3)">' + I.music + '</div>' +
        '<div class="meta"><div class="t">' + esc(t.title) + '</div><div class="a">' + esc(t.artist || '未知歌手') +
        (t.album ? ' · ' + esc(t.album) : '') + '</div></div>' +
        '<div class="row" style="gap:6px">' +
        (t.hasCover ? '' : '<span class="tag warn">缺封面</span>') +
        (t.hasMeta ? '' : '<span class="tag warn">缺元数据</span>') +
        '<span class="tag">' + esc(t.format) + '</span></div>' +
        '<div class="dur">' + fmtDur(t.duration) + '<br><span style="font-size:10px">' + fmtSize(t.size) + '</span></div></div>').join('')
        : emptyBox('目录内没有音频文件', '扫描其他目录或先下载歌曲', I.library)) + '</div>';
  };

  P.history = function (S) {
    const items = S.history || [];
    return '<div class="card pad-0"><div class="toolbar" style="padding:12px 14px">' +
      '<div class="search">' + I.search + '<input type="text" id="hisSearch" placeholder="搜索历史记录" value="' + esc(S.historyQuery || '') + '"></div>' +
      '<button class="btn sm ghost" data-act="loadHistory">' + I.refresh + ' 刷新</button></div>' +
      (items.length ? items.map(h => '<div class="song">' +
        '<div class="cov" style="display:grid;place-items:center;color:var(--fg-3)">' + I.music + '</div>' +
        '<div class="meta"><div class="t">' + esc(h.title) + '</div><div class="a">' + esc(h.artist) +
        ' · ' + esc(h.sourceType) + ' · ' + fmtSize(h.fileSize) + '</div></div>' +
        '<div class="dur" style="text-align:right">' + fmtTime(h.downloadedAt) + '</div>' +
        '<button class="btn sm ghost" data-act="openPath" data-path="' + esc(h.filePath) + '">' + I.folder + '</button>' +
        '</div>').join('') : emptyBox('暂无下载历史', '下载完成的歌曲会记录在此', I.history)) + '</div>';
  };

  P.settings = function (S) {
    const s = S.settings || {};
    const sw = (k, v) => '<div class="switch ' + (v ? 'on' : '') + '" data-act="setFlag" data-k="' + k + '"></div>';
    return '<div class="grid g2">' +
      '<div class="card"><b style="font-size:13.5px">下载设置</b><div style="margin-top:4px">' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">下载目录</div><div class="sr-desc">' + esc(s.downloadDir || '') + '</div></div></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">最大并发数</div><div class="sr-desc">同时下载的任务数量，建议 1-2 保证稳定</div></div>' +
      '<select data-act="setNum" data-k="maxConcurrent" style="width:88px">' + [1, 2, 3, 4, 5, 6].map(n =>
        '<option value="' + n + '"' + (s.maxConcurrent === n ? ' selected' : '') + '>' + n + '</option>').join('') + '</select></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">下载超时（秒）</div></div>' +
      '<select data-act="setNum" data-k="timeoutSec" style="width:88px">' + [15, 30, 60, 90, 120].map(n =>
        '<option value="' + n + '"' + (s.timeoutSec === n ? ' selected' : '') + '>' + n + '</option>').join('') + '</select></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">自动重试次数</div><div class="sr-desc">网络波动时按指数退避重试</div></div>' +
      '<select data-act="setNum" data-k="autoRetry" style="width:88px">' + [0, 1, 2, 3, 4, 5].map(n =>
        '<option value="' + n + '"' + (s.autoRetry === n ? ' selected' : '') + '>' + n + '</option>').join('') + '</select></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">跳过已下载的歌曲</div></div>' + sw('skipDuplicate', s.skipDuplicate) + '</div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">完成后打开目录</div></div>' + sw('openDirAfter', s.openDirAfter) + '</div>' +
      '</div></div>' +
      '<div class="card"><b style="font-size:13.5px">文件设置</b><div style="margin-top:4px">' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">命名模板</div><div class="sr-desc">可用：{歌手} {歌曲名称} {专辑} {序号} {音质}</div></div></div>' +
      '<div style="padding-bottom:10px"><select data-act="setStr" data-k="nameTemplate" style="width:100%">' +
      ['{歌手} - {歌曲名称}', '{歌曲名称}', '{专辑} - {歌曲名称}', '{歌手} - {专辑} - {歌曲名称}', '{序号} - {歌手} - {歌曲名称}'].map(t =>
        '<option value="' + esc(t) + '"' + (s.nameTemplate === t ? ' selected' : '') + '>' + esc(t) + '</option>').join('') + '</select></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">自动分类</div></div>' +
      '<select data-act="setStr" data-k="classify" style="width:118px">' +
      [['none', '不分类'], ['artist', '按歌手'], ['album', '按专辑'], ['playlist', '按歌单'], ['date', '按日期']].map(o =>
        '<option value="' + o[0] + '"' + (s.classify === o[0] ? ' selected' : '') + '>' + o[1] + '</option>').join('') + '</select></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">歌词格式</div></div>' +
      '<select data-act="setStr" data-k="lyricFormat" style="width:88px"><option value="lrc"' + (s.lyricFormat === 'lrc' ? ' selected' : '') + '>LRC</option>' +
      '<option value="txt"' + (s.lyricFormat === 'txt' ? ' selected' : '') + '>TXT</option></select></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">封面格式</div></div>' +
      '<select data-act="setStr" data-k="coverFormat" style="width:88px"><option value="jpg"' + (s.coverFormat === 'jpg' ? ' selected' : '') + '>JPG</option>' +
      '<option value="png"' + (s.coverFormat === 'png' ? ' selected' : '') + '>PNG</option></select></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">写入元数据</div><div class="sr-desc">写入标题/歌手/专辑/封面/歌词到 MP3</div></div>' + sw('writeMeta', s.writeMeta) + '</div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">保存歌词文件</div></div>' + sw('saveLyric', s.saveLyric) + '</div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">保存封面文件</div></div>' + sw('saveCover', s.saveCover) + '</div>' +
      '</div></div>' +
      '<div class="card"><b style="font-size:13.5px">外观</b><div style="margin-top:4px">' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">主题</div></div>' +
      '<select data-act="setStr" data-k="theme" style="width:110px">' +
      [['dark', '深色'], ['light', '浅色'], ['system', '跟随系统']].map(o =>
        '<option value="' + o[0] + '"' + (s.theme === o[0] ? ' selected' : '') + '>' + o[1] + '</option>').join('') + '</select></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">界面缩放</div></div>' +
      '<select data-act="setNum" data-k="uiScale" style="width:88px">' + [0.9, 1, 1.1, 1.2].map(n =>
        '<option value="' + n + '"' + (Number(s.uiScale) === n ? ' selected' : '') + '>' + Math.round(n * 100) + '%</option>').join('') + '</select></div>' +
      '</div></div>' +
      '<div class="card"><b style="font-size:13.5px">高级</b><div style="margin-top:4px">' +
      '<div class="setting-row" style="display:block"><div class="sr-main"><div class="sr-title">账号 Cookie（可选）</div>' +
      '<div class="sr-desc">填入你自己登录后的 MUSIC_U 等 Cookie，平台会按你账号的权限返回音源与音质；不填则按匿名权限获取。仅保存在本机数据库/偏好中，不上传。</div></div>' +
      '<textarea id="cookieInput" placeholder="MUSIC_U=xxxxxxx; __csrf=xxxxxxx" style="margin-top:8px;min-height:62px"></textarea>' +
      '<div class="row" style="margin-top:8px"><button class="btn sm primary" data-act="saveCookie">保存 Cookie</button>' +
      '<button class="btn sm ghost" data-act="clearCookie">清除</button>' +
      '<span class="hint">' + (s.cookie ? '当前：已设置' : '当前：未设置（匿名获取）') + '</span></div></div>' +
      
'<div class="setting-row"><div class="sr-main"><div class="sr-title">网络连接测试</div><div class="sr-desc">' + esc(S.netText || '未测试') + '</div></div>' +
      '<button class="btn sm" data-act="netCheck">测试</button></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">查看运行日志</div></div>' +
      '<button class="btn sm" data-act="showLog">查看</button></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">数据库备份</div><div class="sr-desc">导出到下载目录</div></div>' +
      '<button class="btn sm" data-act="dbBackup">备份</button></div>' +
      '<div class="setting-row"><div class="sr-main"><div class="sr-title">恢复默认设置</div><div class="sr-desc">不会删除已下载文件</div></div>' +
      '<button class="btn sm danger" data-act="resetSettings">重置</button></div>' +
      '</div></div></div>';
  };

  P.about = function (S) {
    const info = S.info || {};
    return '<div class="grid" style="gap:14px">' +
      '<div class="hero"><h2>云音乐管家 ' + esc(info.version || '1.0') + '</h2>' +
      '<p>一款本地运行的音乐下载与管理工具：解析网易云音乐公开页面信息，下载平台实际提供的音频源，并按规则写入元数据、封面与歌词。</p></div>' +
      '<div class="card"><b style="font-size:13.5px">数据来源与限制</b>' +
      '<div style="margin-top:8px;font-size:12.5px;color:var(--fg-2);line-height:1.8">' +
      '<div>· 歌曲信息、歌单、封面、歌词：来自网易云音乐公开 Web 接口</div>' +
      '<div>· 音频：仅使用平台自身返回的可用播放源（标准/较高/极高音质以实际返回为准）</div>' +
      '<div>· 会员/付费/版权受限曲目不会提供音频，界面上标记为「无法获取」并记录原因</div>' +
      '<div>· 本应用不破解、不伪造身份、不绕过任何版权限制</div>' +
      '<div>· 下载内容请在合法授权范围内使用，仅限个人学习与研究</div>' +
      '</div></div>' +
      '<div class="card"><b style="font-size:13.5px">运行信息</b>' +
      '<div style="margin-top:8px;font-size:12.5px;color:var(--fg-2);line-height:1.9">' +
      '<div>设备：' + esc(info.device || '') + ' · Android SDK ' + (info.sdk || '') + '</div>' +
      '<div>保存目录：' + esc(info.dir || '') + '</div>' +
      '<div>文件权限：' + (info.allFiles ? '已授权' : '未授权（可能无法写入公共目录）') + '</div>' +
      '<div>数据库：SQLite（本地） · 版本 ' + esc(info.build || '') + '</div>' +
      '</div></div>' +
      '<div class="card"><b style="font-size:13.5px">运行自检</b>' +
      '<div class="row between" style="margin-top:10px"><span class="hint">检查原生桥接、网络、保存目录与权限状态</span>' +
      '<button class="btn sm primary" data-act="selfCheck">开始自检</button></div></div>' +
      '<div class="card"><b style="font-size:13.5px">免责声明</b>' +
      '<p style="margin-top:8px;font-size:12.5px;color:var(--fg-3);line-height:1.8">本工具不存储、不分发任何音频文件，所有内容由用户主动请求并从平台公开接口获取。请遵守网易云音乐用户协议与相关法律法规，不要将下载内容用于商业用途或二次传播。</p></div>' +
      '</div>';
  };

  window.P = P;
})();
