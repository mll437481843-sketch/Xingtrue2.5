import fs from 'fs';
import vm from 'vm';
const UIDIR = process.env.UIDIR;
const ctx = { console, setTimeout, clearTimeout, Promise, Date, Math, Set, Map, JSON, Number, String, Array, Object, RegExp, Boolean, parseInt, parseFloat, isNaN };
ctx.window = ctx;
ctx.globalThis = ctx;
ctx.document = { querySelector: () => null, querySelectorAll: () => [], createElement: () => ({ classList: { add(){}, remove(){}, toggle(){} }, style:{}, appendChild(){}, querySelector: () => null }), addEventListener(){}, body: { appendChild(){}, style:{} }, documentElement: { setAttribute(){}, getAttribute: () => 'dark', style:{setProperty(){}} } };
ctx.localStorage = { getItem: () => null, setItem(){} };
vm.createContext(ctx);
for (const f of ['js/util.js', 'js/pages.js', 'js/pages2.js']) {
  vm.runInContext(fs.readFileSync(UIDIR + '/' + f, 'utf8'), ctx, { filename: f });
}
const { P } = ctx;
const mk = (o) => Object.assign({}, o);
const S = {
  settings: { downloadDir: '/sdcard/Download/云音乐管家', nameTemplate: '{歌手} - {歌曲名称}', classify: 'artist', maxConcurrent: 2, autoRetry: 3, timeoutSec: 30, quality: 'standard', saveLyric: true, saveCover: true, writeMeta: true, lyricFormat: 'lrc', coverFormat: 'jpg', embedCover: true, theme: 'dark', uiScale: 1, skipDuplicate: true, duplicateAction: 'skip', openDirAfter: false, autoCreateFolder: true },
  stats: { queue: { total: 5, done: 2, running: 1, queued: 1, paused: 1, failed: 0, canceled: 0, unavailable: 0, percent: 40, speed: 120000, maxConcurrent: 2, pausedAll: false }, db: { todayCount: 3, totalCount: 12, totalBytes: 123456789 }, storage: { free: 1e10, used: 2e9, dir: '/sdcard/Download/x' } },
  tasks: [{ taskId: 't1', title: '测试歌曲', artist: '歌手', status: 'running', progress: 42, totalBytes: 3e6, speed: 100000, retryCount: 0, errorMessage: '', coverUrl: 'http://p1.music.126.net/x.jpg', playlistName: '热歌榜', platformId: '1', filePath: '/a.mp3', duration: 200 }],
  history: [{ title: '测试歌曲', artist: '歌手', album: '专辑', filePath: '/a.mp3', fileSize: 1234567, format: 'mp3', downloadedAt: Date.now(), sourceType: '单曲' }],
  playlists: [{ platformId: '3778678', name: '热歌榜', songCount: 200 }],
  info: { version: '1.1', device: 'Test', sdk: 34, dir: '/x', allFiles: true, build: '2026.11' },
  netText: '连接正常 · 120ms',
  playlist: { input: '', loading: false, error: '', info: { name: '热歌榜', creator: '网易云音乐', songCount: 3, cover: 'http://p1.music.126.net/a.jpg' }, songs: [
    { platformId: '1', title: '歌A', artist: '歌手A', album: '专辑A', duration: 200, coverUrl: 'http://x/1.jpg', available: true, downloaded: false, level: 'standard', format: 'mp3', duplicate: false },
    { platformId: '2', title: '歌B', artist: '歌手B', album: '专辑B', duration: 180, coverUrl: '', available: false, unavailableReason: '会员曲目', downloaded: true, duplicate: true },
    { platformId: '3', title: '歌C', artist: '歌手A', album: '专辑A', duration: 240, coverUrl: 'http://x/3.jpg', available: true, downloaded: false }
  ], report: { downloaded: 1, duplicate: 1, unavailable: 1 }, selected: new Set(['1']), filter: { q: '', artist: '', state: 'all' }, limit: 200 },
  single: { input: '', loading: false, error: '', songs: [{ platformId: '347230', title: '歌', artist: '人', album: '专', duration: 200, coverUrl: 'http://x', available: true, downloaded: false, level: 'standard', format: 'mp3' }], invalid: [{ input: 'x', reason: 'y' }], selected: new Set(['347230']) },
  library: { dir: '/sdcard/Music', tracks: [{ path: '/a.mp3', title: 't', artist: 'a', album: 'b', duration: 200, size: 1e6, format: 'MP3', hasCover: true, hasMeta: true }], stats: { count: 1, noCover: 0, noMeta: 0, totalSize: 1e6 }, filter: { q: '', kind: 'all' }, scanning: false, progress: 0, error: '' },
  historyQuery: ''
};
let bad = [];
for (const name of ['home', 'playlist', 'single', 'tasks', 'library', 'history', 'settings', 'about']) {
  try {
    const html = P[name](S);
    if (!html || html.length < 100) bad.push(name + ': too short');
    if (/undefined/.test(html)) bad.push(name + ': contains undefined');
    if (/NaN/.test(html)) bad.push(name + ': contains NaN');
    console.log('OK  ' + name + '  len=' + html.length);
  } catch (e) {
    bad.push(name + ': ' + e.message);
    console.log('ERR ' + name + '  ' + e.message);
  }
}
// 空状态渲染
const emptyS = JSON.parse(JSON.stringify({ settings: S.settings, stats: { queue: {}, db: {}, storage: {} }, tasks: [], history: [], playlists: [], info: {}, netText: '', playlist: { input: '', loading: false, error: '', info: null, songs: [], report: null, selected: [], filter: { q: '', artist: '', state: 'all' }, limit: 200 }, single: { input: '', loading: false, error: '', songs: [], invalid: [], selected: [] }, library: { dir: '', tracks: [], stats: null, scanning: false, progress: 0, filter: { q: '', kind: 'all' }, error: '' }, historyQuery: '' }));
emptyS.playlist.selected = new Set(); emptyS.single.selected = new Set();
for (const name of ['home', 'playlist', 'single', 'tasks', 'library', 'history', 'settings', 'about']) {
  try {
    const html = P[name](emptyS);
    if (/undefined|NaN/.test(html)) bad.push('empty-' + name + ': undefined/NaN');
    console.log('OK-empty  ' + name + '  len=' + html.length);
  } catch (e) { bad.push('empty-' + name + ': ' + e.message); console.log('ERR-empty ' + name + ' ' + e.message); }
}
console.log(bad.length ? ('\nFAILURES: ' + bad.join(' | ')) : '\nALL_RENDER_OK');
process.exit(bad.length ? 1 : 0);
