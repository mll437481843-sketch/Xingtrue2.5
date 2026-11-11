import fs from 'fs';
const H = { 'User-Agent': 'Mozilla/5.0', 'Referer': 'https://music.163.com/' };
const post = async (u, body) => {
  const r = await fetch(u, { method: 'POST', headers: { ...H, 'Content-Type': 'application/x-www-form-urlencoded' }, body, signal: AbortSignal.timeout(20000) });
  return r.json();
};
const ids = ['347230', '1330348068', '186016', '1974443814'];
fs.mkdirSync('dl', { recursive: true });
let pass = 0, fail = 0;
for (const id of ids) {
  const d = await post('https://music.163.com/api/song/enhance/player/url?ids=[' + id + ']&br=128000', 'ids=[' + id + ']&br=128000');
  const x = d.data && d.data[0];
  const detail = await fetch('https://music.163.com/api/song/detail?ids=[' + id + ']', { headers: H }).then(r => r.json());
  const song = detail.songs && detail.songs[0];
  const name = song ? song.name : '?';
  const durSec = song ? Math.round((song.dt || song.duration) / 1000) : 0;
  if (!x || !x.url) {
    console.log('SKIP  ' + name + ' -> 平台无可用音源 (' + ((d.data && d.data[0] && d.data[0].code) || 'n/a') + ')');
    continue;
  }
  const res = await fetch(x.url, { headers: H, signal: AbortSignal.timeout(60000) });
  const buf = Buffer.from(await res.arrayBuffer());
  const expect = x.size;
  const sizeOk = Math.abs(buf.length - expect) <= Math.max(2048, expect * 0.01);
  const estSec = Math.round(buf.length * 8 / x.br);
  const durOk = Math.abs(estSec - durSec) <= 5;
  const isMp3 = buf[0] === 0x49 && buf[1] === 0x44 && buf[2] === 0x33 || (buf[0] === 0xFF && (buf[1] & 0xE0) === 0xE0);
  fs.writeFileSync('dl/' + id + '.mp3', buf);
  const ok = sizeOk && durOk && isMp3;
  if (ok) pass++; else fail++;
  console.log((ok ? 'PASS  ' : 'FAIL  ') + name + ' | ' + (buf.length / 1048576).toFixed(2) + 'MB | 期望 ' + (expect / 1048576).toFixed(2) + 'MB | 歌曲 ' + durSec + 's vs 音频 ' + estSec + 's | mp3=' + isMp3);
}
console.log('\nRESULT pass=' + pass + ' fail=' + fail);
process.exit(fail ? 1 : 0);
