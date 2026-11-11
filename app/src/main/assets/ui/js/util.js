(function () {
  const $ = (s, r) => (r || document).querySelector(s);
  const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));

  const I = {
    home: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/></svg>',
    list: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/></svg>',
    music: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>',
    download: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12"/><path d="m7 11 5 5 5-5"/><path d="M4 21h16"/></svg>',
    library: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4v16"/><path d="M9 4v16"/><path d="M14 5l6 14"/></svg>',
    history: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
    settings: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-2.9 1.2 2 2 0 1 1-4 0 1.7 1.7 0 0 0-2.9-1.2l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.7 1.7 0 0 0 4.6 15a2 2 0 1 1 0-4 1.7 1.7 0 0 0 1.2-2.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1A1.7 1.7 0 0 0 11.5 4a2 2 0 1 1 4 0 1.7 1.7 0 0 0 2.9 1.2l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1A1.7 1.7 0 0 0 22 11.5a2 2 0 1 1 0 4z"/></svg>',
    info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/></svg>',
    search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>',
    check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="m5 13 4 4L19 7"/></svg>',
    play: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>',
    pause: '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="5" width="4" height="14" rx="1"/><rect x="14" y="5" width="4" height="14" rx="1"/></svg>',
    x: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>',
    trash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/></svg>',
    refresh: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-3-6.7"/><path d="M21 4v5h-5"/></svg>',
    folder: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>',
    sun: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M19.1 4.9l-1.4 1.4M6.3 17.7l-1.4 1.4"/></svg>',
    moon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.8A8.5 8.5 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>',
    warn: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3 2 20h20z"/><path d="M12 9v5M12 17h.01"/></svg>',
    clock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 8v4l3 2"/></svg>'
  };

  const _pending = new Map();
  let _seq = 0;

  function api(method, args) {
    return new Promise(function (resolve, reject) {
      const id = 'c' + (++_seq);
      _pending.set(id, resolve);
      try {
        const payload = Object.assign({}, args || {}, { __id: id });
        const raw = window.Native ? window.Native.call(method, JSON.stringify(payload)) : '{"error":"no bridge"}';
        const res = JSON.parse(raw);
        if (res && res.data && res.data.pending) return;
        _pending.delete(id);
        resolve(res.data || res);
      } catch (e) {
        _pending.delete(id);
        reject(e);
      }
    });
  }

  const hooks = {};
  window.__hooks = hooks;
  window.__evt = function (type, payload) {
    if (type === 'cb') {
      const r = _pending.get(payload.id);
      if (r) { _pending.delete(payload.id); r(payload.data); }
      return;
    }
    const h = hooks[type];
    if (h) { try { h(payload); } catch (e) { console.error(type, e); } }
  };

  function toast(msg, kind, ms) {
    const box = $('#toasts');
    const d = document.createElement('div');
    d.className = 'toast ' + (kind || '');
    d.innerHTML = '<i class="dot"></i><span></span>';
    d.querySelector('span').textContent = msg;
    box.appendChild(d);
    setTimeout(function () { d.classList.add('out'); setTimeout(function () { d.remove(); }, 260); }, ms || 2600);
  }

  function confirmBox(opt) {
    return new Promise(function (resolve) {
      const mask = document.createElement('div');
      mask.className = 'mask';
      mask.innerHTML = '<div class="modal"><h3></h3><p></p><div class="m-actions">' +
        '<button class="btn ghost" data-no>取消</button><button class="btn primary" data-yes>确定</button></div></div>';
      mask.querySelector('h3').textContent = opt.title || '确认操作';
      mask.querySelector('p').textContent = opt.text || '';
      mask.querySelector('[data-yes]').textContent = opt.ok || '确定';
      if (opt.danger) mask.querySelector('[data-yes]').classList.add('danger');
      document.body.appendChild(mask);
      const done = function (v) { mask.remove(); resolve(v); };
      mask.querySelector('[data-yes]').onclick = function () { done(true); };
      mask.querySelector('[data-no]').onclick = function () { done(false); };
      mask.onclick = function (e) { if (e.target === mask) done(false); };
    });
  }

  function fmtSize(b) {
    b = Number(b) || 0;
    if (b >= 1073741824) return (b / 1073741824).toFixed(2) + ' GB';
    if (b >= 1048576) return (b / 1048576).toFixed(1) + ' MB';
    if (b >= 1024) return (b / 1024).toFixed(0) + ' KB';
    return b + ' B';
  }
  function fmtDur(s) {
    s = Number(s) || 0;
    return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
  }
  function fmtTime(ms) {
    if (!ms) return '—';
    const d = new Date(Number(ms));
    const p = function (n) { return String(n).padStart(2, '0'); };
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
  }
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c];
    });
  }
  function emptyBox(title, sub, icon) {
    return '<div class="empty"><div class="ico">' + (icon || I.music) + '</div><div class="t">' + esc(title) +
      '</div><div class="s">' + esc(sub || '') + '</div></div>';
  }
  function statusText(st) {
    return ({
      queued: '等待中', parsing: '解析中', running: '下载中', paused: '已暂停',
      done: '已完成', failed: '失败', canceled: '已取消', unavailable: '无法获取'
    })[st] || st;
  }
  function statusClass(st) {
    return ({ done: 'ok', failed: 'bad', unavailable: 'warn', running: 'info', parsing: 'info' })[st] || '';
  }

  window.U = { $, $$, I, api, toast, confirmBox, fmtSize, fmtDur, fmtTime, esc, emptyBox, statusText, statusClass, hooks };
})();
