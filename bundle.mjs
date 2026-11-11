import fs from 'fs';
const src = process.argv[2], dst = process.argv[3];
let html = fs.readFileSync(src + '/index.html', 'utf8');
html = html.replace(/<link rel="stylesheet" href="([^"]+)">/g, function (m, f) {
  return '<style>' + fs.readFileSync(src + '/' + f, 'utf8') + '</style>';
});
html = html.replace(/<script src="([^"]+)"><\/script>/g, function (m, f) {
  return '<script>' + fs.readFileSync(src + '/' + f, 'utf8') + '</script>';
});
fs.mkdirSync(dst, { recursive: true });
fs.writeFileSync(dst + '/index.html', html);
console.log('bundle: ' + (html.length / 1024).toFixed(1) + ' KB inline');
