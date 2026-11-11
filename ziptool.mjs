import fs from 'fs';
import zlib from 'zlib';

const table = (() => { const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1; t[n] = c; } return t; })();
const crc32 = (buf) => { let c = -1; for (let i = 0; i < buf.length; i++) c = table[(c ^ buf[i]) & 0xFF] ^ (c >>> 8); return (c ^ -1) >>> 0; };

function readEntries(apk) {
  const buf = fs.readFileSync(apk);
  let eocd = -1;
  for (let i = buf.length - 22; i >= 0 && i > buf.length - 66000; i--) { if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; } }
  if (eocd < 0) throw new Error('no EOCD');
  const cdSize = buf.readUInt32LE(eocd + 12), cdOff = buf.readUInt32LE(eocd + 16), n = buf.readUInt16LE(eocd + 10);
  const entries = [];
  let p = cdOff;
  for (let i = 0; i < n; i++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error('bad CD');
    const nameLen = buf.readUInt16LE(p + 28), extraLen = buf.readUInt16LE(p + 30), cmtLen = buf.readUInt16LE(p + 32);
    const name = buf.slice(p + 46, p + 46 + nameLen).toString('utf8');
    entries.push({ name, cd: buf.slice(p, p + 46 + nameLen + extraLen + cmtLen) });
    p += 46 + nameLen + extraLen + cmtLen;
  }
  return { buf, eocd, entries, cdOff, cdSize };
}

function add(apk, files) {
  const { buf, eocd, entries } = readEntries(apk);
  const body = buf.slice(0, eocd);
  const parts = [body];
  let off = body.length;
  const newCd = [];
  for (const f of files) {
    const data = fs.readFileSync(f.src);
    const comp = zlib.deflateRawSync(data, { level: 9 });
    const useDeflate = comp.length < data.length;
    const payload = useDeflate ? comp : data;
    const crc = crc32(data);
    const nameBuf = Buffer.from(f.name, 'utf8');
    const lh = Buffer.alloc(30);
    lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4); lh.writeUInt16LE(0x0800, 6);
    lh.writeUInt16LE(useDeflate ? 8 : 0, 8); lh.writeUInt16LE(0, 10); lh.writeUInt16LE(0x21, 12);
    lh.writeUInt32LE(crc, 14); lh.writeUInt32LE(payload.length, 18); lh.writeUInt32LE(data.length, 22);
    lh.writeUInt16LE(nameBuf.length, 26); lh.writeUInt16LE(0, 28);
    parts.push(lh, nameBuf, payload);
    const ch = Buffer.alloc(46);
    ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6); ch.writeUInt16LE(0x0800, 8);
    ch.writeUInt16LE(useDeflate ? 8 : 0, 10); ch.writeUInt16LE(0, 12); ch.writeUInt16LE(0x21, 14);
    ch.writeUInt32LE(crc, 16); ch.writeUInt32LE(payload.length, 20); ch.writeUInt32LE(data.length, 24);
    ch.writeUInt16LE(nameBuf.length, 28); ch.writeUInt32LE(off, 42);
    newCd.push(Buffer.concat([ch, nameBuf]));
    off += 30 + nameBuf.length + payload.length;
  }
  const cdBuf = Buffer.concat([...entries.map(e => e.cd), ...newCd]);
  const eocdBuf = Buffer.alloc(22);
  eocdBuf.writeUInt32LE(0x06054b50, 0);
  eocdBuf.writeUInt16LE(entries.length + newCd.length, 8);
  eocdBuf.writeUInt16LE(entries.length + newCd.length, 10);
  eocdBuf.writeUInt32LE(cdBuf.length, 12);
  eocdBuf.writeUInt32LE(off, 16);
  fs.writeFileSync(apk, Buffer.concat([...parts, cdBuf, eocdBuf]));
  console.log('zip: added ' + files.map(f => f.name).join(',') + ' (' + (off + cdBuf.length + 22) + ' bytes)');
}

const [, , cmd, apk, ...rest] = process.argv;
if (cmd === 'add') {
  const files = [];
  for (let i = 0; i < rest.length; i += 2) files.push({ src: rest[i], name: rest[i + 1] });
  add(apk, files);
} else if (cmd === 'list') {
  console.log(readEntries(apk).entries.map(e => e.name).join('\n'));
}
