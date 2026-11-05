/* AuraPet web dashboard – JavaScript thuần, không cần build.
 * Mọi lệnh điều khiển đi qua REST API của backend -> MQTT -> ESP32 thật. */
(() => {
  'use strict';

  // ------------------------------------------------------------------ tiện ích
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch { return null; } },
    set(k, v) { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch { /* chế độ riêng tư */ } },
  };
  const fmtTime = (iso) => {
    if (!iso) return '–';
    const d = new Date(iso);
    const today = new Date();
    const same = d.toDateString() === today.toDateString();
    const t = d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
    return same ? t : `${t} ${d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit' })}`;
  };
  const ago = (iso) => {
    if (!iso) return '–';
    const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
    if (s < 60) return 'vừa xong';
    if (s < 3600) return `${Math.floor(s / 60)} phút trước`;
    if (s < 86400) return `${Math.floor(s / 3600)} giờ trước`;
    return fmtTime(iso);
  };
  const dayLabel = (isoDate) => {
    const d = new Date(isoDate + 'T00:00:00');
    return ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'][d.getDay()];
  };

  function toast(msg, kind = '') {
    const el = document.createElement('div');
    el.className = `toast ${kind}`;
    el.textContent = msg;
    $('#toasts').append(el);
    setTimeout(() => el.remove(), kind === 'err' ? 5000 : 2600);
  }

  // ------------------------------------------------------------------ API
  function defaultBase() {
    const saved = store.get('aurapet_api');
    if (saved) return saved;
    const { protocol, hostname, origin } = location;
    if (protocol.startsWith('http') && !hostname.endsWith('github.io')) return origin;
    return '';
  }
  let API = defaultBase();
  let token = store.get('aurapet_token');

  async function api(path, { method = 'GET', body } = {}) {
    if (!API) throw new Error('Chưa cấu hình địa chỉ máy chủ');
    let res;
    try {
      res = await fetch(API + path, {
        method,
        headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch {
      throw new Error(`Không kết nối được máy chủ ${API}`);
    }
    let json = {};
    try { json = await res.json(); } catch { /* không phải JSON */ }
    if (res.status === 401 && token) {
      logout('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại');
      throw new Error('Phiên đăng nhập đã hết hạn');
    }
    if (!res.ok || json.success === false) {
      throw Object.assign(new Error(json.message || `Lỗi ${res.status}`), { status: res.status });
    }
    return json.data;
  }

  async function withBusy(btn, fn) {
    if (btn) btn.classList.add('busy');
    try { return await fn(); }
    catch (e) { toast(e.message, 'err'); return undefined; }
    finally { if (btn) btn.classList.remove('busy'); }
  }

  // ------------------------------------------------------------------ trạng thái
  const S = { user: null, device: null, stats: null, pets: [], selectedPet: null, gram: 30, chat: [], pet: null, dismissed: new Set(), actAll: false };
  const nf = (v, d = 2) => Number(v).toLocaleString('vi-VN', { maximumFractionDigits: d });
  const isToday = (iso) => iso && new Date(iso).toDateString() === new Date().toDateString();
  let pollTimer = null;
  let statsTimer = null;

  // ------------------------------------------------------------------ giao diện sáng/tối
  function applyTheme(t) {
    document.documentElement.dataset.theme = t;
    $('meta[name="theme-color"]').content = t === 'dark' ? '#141A17' : '#F6F2EA';
  }
  applyTheme(store.get('aurapet_theme') || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'));
  $('#theme-btn').addEventListener('click', () => {
    const t = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
    applyTheme(t);
    store.set('aurapet_theme', t);
  });

  // ------------------------------------------------------------------ đăng nhập
  let authMode = 'login';
  function setAuthMode(mode) {
    authMode = mode;
    $$('[data-auth-tab]').forEach((b) => b.setAttribute('aria-selected', String(b.dataset.authTab === mode)));
    $$('[data-only]').forEach((el) => { el.hidden = el.dataset.only !== mode; });
    $('#auth-submit').textContent = mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản';
    $('#auth-form [name=password]').autocomplete = mode === 'login' ? 'current-password' : 'new-password';
    $('#auth-error').textContent = '';
  }
  $$('[data-auth-tab]').forEach((b) => b.addEventListener('click', () => setAuthMode(b.dataset.authTab)));

  function renderServerLabel() {
    $('#server-label').textContent = API || 'chưa cấu hình';
    $('#server-input').value = store.get('aurapet_api') || '';
    if (!API) $('#server-box').open = true;
  }
  $('#server-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    let v = $('#server-input').value.trim().replace(/\/+$/, '');
    if (v && !/^https?:\/\//.test(v)) v = 'http://' + v;
    try {
      if (v) {
        const r = await fetch(v + '/api/v1/ping');
        const j = await r.json();
        if (j?.data?.app !== 'aurapet') throw new Error();
      }
      store.set('aurapet_api', v || null);
      API = v || defaultBase();
      renderServerLabel();
      toast(v ? 'Đã kết nối máy chủ' : 'Dùng máy chủ cùng địa chỉ trang', 'ok');
    } catch {
      toast(`Không tìm thấy máy chủ AuraPet tại ${v}`, 'err');
    }
  });

  async function doAuth(email, password, name) {
    $('#auth-error').textContent = '';
    const btn = $('#auth-submit');
    btn.classList.add('busy');
    try {
      const data = authMode === 'login' || !name
        ? await api('/api/v1/auth/login', { method: 'POST', body: { email, password } })
        : await api('/api/v1/auth/register', { method: 'POST', body: { name, email, password } });
      token = data.token;
      store.set('aurapet_token', token);
      S.user = data.user;
      enterApp();
    } catch (err) {
      $('#auth-error').textContent = err.message;
    } finally {
      btn.classList.remove('busy');
    }
  }
  $('#auth-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const f = e.target;
    if (authMode === 'register' && !f.name.value.trim()) { $('#auth-error').textContent = 'Vui lòng nhập họ tên'; return; }
    doAuth(f.email.value.trim(), f.password.value, authMode === 'register' ? f.name.value.trim() : null);
  });
  $$('[data-demo]').forEach((b) => b.addEventListener('click', () => {
    const admin = b.dataset.demo === 'admin';
    authMode = 'login';
    doAuth(admin ? 'admin@aurapet.com' : 'user@aurapet.com', admin ? 'Admin@123' : 'User@123');
  }));

  function logout(msg) {
    token = null;
    store.set('aurapet_token', null);
    S.user = null;
    clearInterval(pollTimer);
    clearInterval(statsTimer);
    $('#app').hidden = true;
    $('#auth').hidden = false;
    setAuthMode('login');
    renderServerLabel();
    if (msg) $('#auth-error').textContent = msg;
  }
  $('#logout-btn').addEventListener('click', () => logout());

  // ------------------------------------------------------------------ điều hướng
  const views = ['control', 'pets', 'history', 'ai', 'account'];
  const TITLES = { control: 'Tổng quan', pets: 'Thú cưng', history: 'Lịch sử cho ăn', ai: 'Hỏi AI về bé', account: 'Cài đặt' };
  function show(view) {
    if (!views.includes(view)) view = 'control';
    views.forEach((v) => { $(`#view-${v}`).hidden = v !== view; });
    $('#page-title').textContent = TITLES[view];
    document.title = `${TITLES[view]} – AuraPet`;
    window.scrollTo(0, 0);
    $$('.nav-btn').forEach((b) => (b.dataset.view === view ? b.setAttribute('aria-current', 'page') : b.removeAttribute('aria-current')));
    store.set('aurapet_view', view);
    if (view === 'control') camLoop();
    if (view === 'pets') loadPets();
    if (view === 'history') loadHistory();
    if (view === 'ai') initChat();
    if (view === 'account') renderAccount();
  }
  $$('.nav-btn').forEach((b) => b.addEventListener('click', () => show(b.dataset.view)));
  $('#conn').addEventListener('click', () => show('control'));

  async function enterApp() {
    $('#auth').hidden = true;
    $('#app').hidden = false;
    try {
      S.user = await api('/api/v1/auth/me');
    } catch (e) {
      if (token) logout(e.message);
      return;
    }
    $('#who').textContent = S.user.name;
    await Promise.all([refreshState(), loadPets(true)]);
    await refreshStats();
    clearInterval(pollTimer);
    clearInterval(statsTimer);
    pollTimer = setInterval(() => { if (!document.hidden) refreshState(); }, 2000);
    statsTimer = setInterval(() => { if (!document.hidden) refreshStats(); }, 20000);
    show(store.get('aurapet_view') || 'control');
  }

  // ------------------------------------------------------------------ máy cho ăn
  async function refreshState() {
    try {
      S.device = await api('/api/v1/device/state');
      renderDevice();
    } catch (e) {
      setConn('bad', e.message);
    }
  }
  async function refreshStats() {
    try {
      S.stats = await api('/api/v1/dashboard/stats');
      renderStats();
    } catch { /* đã báo ở refreshState */ }
    await loadPetSummary();
  }

  // Bé đang gán cho máy cho ăn (hoặc bé đầu tiên) -> dải thông tin + khay vệ sinh
  function mainPet() {
    const id = S.device?.settings?.feederPetId;
    return S.pets.find((p) => p.id === id) || S.pets[0] || null;
  }
  async function loadPetSummary() {
    const p = mainPet();
    if (!p) { S.pet = null; renderPetStrip(); return; }
    try {
      const [summary, today, litter] = await Promise.all([
        api(`/api/v1/health/${p.id}/summary`),
        api(`/api/v1/health/${p.id}/food/daily?days=1`),
        api(`/api/v1/health/${p.id}/litter`),
      ]);
      S.pet = { pet: p, summary, today: (today || [])[0] || null, litter: litter || [] };
    } catch {
      S.pet = { pet: p, summary: null, today: null, litter: [] };
    }
    renderPetStrip();
  }

  const LITTER = { NORMAL: 'Bình thường', ABNORMAL: 'Bất thường', DIARRHEA: 'Tiêu chảy', CONSTIPATION: 'Táo bón' };
  function setBadge(el, kind, text) {
    el.className = `badge ${kind}`;
    el.innerHTML = `<i></i><span>${esc(text)}</span>`;
  }

  function renderPetStrip() {
    const d = S.pet;
    if (!d) {
      $('#ps-avatar').innerHTML = '<span class="avatar">?</span>';
      $('#ps-name').textContent = 'Chưa có bé nào';
      $('#ps-meta').textContent = 'Thêm thú cưng ở mục Thú cưng';
      setBadge($('#ps-health'), 'off', 'Chưa có dữ liệu');
      ['#m-food', '#m-meals', '#m-litter', '#m-weight'].forEach((s) => { $(s).textContent = '–'; });
      $('#m-food-goal').textContent = '';
      $('#m-weight-delta').textContent = '';
      $('#litter-last').innerHTML = '<span class="na">Chưa có dữ liệu</span>';
      $('#litter-week').innerHTML = '<span class="na">–</span>';
      return;
    }
    const { pet: p, summary: h, today, litter } = d;
    $('#ps-avatar').innerHTML = avatar(p);
    $('#ps-name').textContent = p.name;
    $('#ps-meta').textContent = `${p.age} tuổi · ${p.breed} · ${p.gender === 'MALE' ? 'đực' : 'cái'}`;
    if (h) {
      const kind = h.healthScore >= 90 ? 'ok' : h.healthScore >= 75 ? 'warn' : 'bad';
      setBadge($('#ps-health'), kind, `${h.status} · ${h.healthScore}`);
    } else setBadge($('#ps-health'), 'off', 'Chưa có dữ liệu');

    $('#m-food').textContent = today ? `${today.gram} g` : '–';
    $('#m-food-goal').textContent = h?.expectedDailyFoodGram ? `/ ${h.expectedDailyFoodGram} g` : '';
    $('#m-meals').textContent = today ? today.meals : '–';
    $('#m-litter').textContent = litter.filter((l) => isToday(l.timestamp)).length;
    $('#m-weight').textContent = `${nf(p.weight)} kg`;
    const dw = h?.weightChangeKg ?? 0;
    const dEl = $('#m-weight-delta');
    dEl.textContent = dw ? `${dw > 0 ? '+' : ''}${nf(dw)} kg` : 'ổn định';
    dEl.className = Math.abs(dw) >= 0.2 ? 'up' : '';

    const last = litter[0];
    $('#litter-last').innerHTML = last
      ? `${esc(ago(last.timestamp))} <small class="${last.result === 'NORMAL' ? 'ok-t' : 'bad-t'}">${esc(LITTER[last.result] || last.result)}</small>`
      : '<span class="na">Chưa có ghi nhận</span>';
    $('#litter-week').innerHTML = h ? `${h.weeklyLitterVisits} lần` : '<span class="na">–</span>';
  }
  $('#ps-open').addEventListener('click', () => {
    const p = S.pet?.pet;
    show('pets');
    if (p) selectPet(p.id);
  });

  function setConn(kind, text) {
    const c = $('#conn');
    c.className = `conn ${kind}`;
    $('#conn-text').textContent = text;
  }

  function setSwitch(id, on, disabled = false) {
    const s = $(id);
    s.setAttribute('aria-checked', String(!!on));
    s.disabled = disabled;
  }

  let gramDirty = 0; // thời điểm người dùng vừa chỉnh khẩu phần (để không bị poll ghi đè)
  let feedPendingUntil = 0; // vừa gửi lệnh, chờ ESP32 báo đang nhả

  // Giờ hẹn kế tiếp tính theo giờ trên máy người dùng
  function nextMeal(times) {
    if (!times?.length) return null;
    const now = new Date();
    const cur = now.getHours() * 60 + now.getMinutes();
    const mins = times.map((t) => { const [h, m] = t.split(':').map(Number); return h * 60 + m; }).sort((a, b) => a - b);
    const n = mins.find((x) => x > cur);
    const v = n ?? mins[0];
    return { time: `${String(Math.floor(v / 60)).padStart(2, '0')}:${String(v % 60).padStart(2, '0')}`, tomorrow: n == null };
  }

  function cameraText(d) {
    if (!d.cameraLastSeen) return 'Chưa có camera AI';
    const s = (Date.now() - new Date(d.cameraLastSeen).getTime()) / 1000;
    return s < 90 ? 'Camera AI đang theo dõi' : `Camera AI mất tín hiệu ${ago(d.cameraLastSeen)}`;
  }

  function renderDevice() {
    const d = S.device;
    if (!d) return;
    const set = d.settings || {};
    const online = d.online;
    const feeding = online && d.feeding;
    if (feeding) feedPendingUntil = 0;
    const pending = online && !feeding && Date.now() < feedPendingUntil;

    if (!d.brokerConnected) setConn('bad', 'Mất kết nối MQTT');
    else if (!online) setConn('warn', 'Máy cho ăn offline');
    else setConn('ok', 'Thiết bị đã kết nối');

    if (Date.now() - gramDirty > 3000 && set.targetGram) S.gram = set.targetGram;
    renderGram();

    const card = $('#feeder');
    card.classList.toggle('feeding', !!feeding);
    card.classList.toggle('feeding-now', !!feeding);
    card.classList.toggle('offline', !online);

    // Trạng thái máy
    const st = $('#feed-state');
    if (!d.brokerConnected) setBadge(st, 'bad', 'Mất kết nối');
    else if (!online) setBadge(st, 'bad', 'Offline');
    else if (feeding) setBadge(st, 'busy', 'Đang nhả hạt');
    else if (pending) setBadge(st, 'busy', 'Đang gửi lệnh');
    else if (d.lowFood) setBadge(st, 'warn', 'Sắp hết hạt');
    else setBadge(st, 'ok', 'Đang hoạt động');

    // Bát ăn (số đo thật từ loadcell; offline thì không hiển thị số cũ)
    const w = online ? Math.max(0, d.bowlWeightGram ?? 0) : null;
    $('#bowl-gram').textContent = w == null ? '–' : Math.round(w);
    const cap = Math.max(60, (set.targetGram || 30) * 2);
    $('#bowl-fill').setAttribute('y', String(122 - (w == null ? 0 : Math.min(1, w / cap)) * 82));

    // Thùng chứa
    const pct = online && d.foodLevelPercent >= 0 ? d.foodLevelPercent : null;
    $('#hopper-fill').style.width = (pct ?? 0) + '%';
    $('#hopper-fill').parentElement.classList.toggle('low', !!d.lowFood);
    const hp = $('#hopper-pct');
    hp.textContent = pct == null ? 'Không rõ' : `${pct}%`;
    hp.className = pct == null ? 'na' : d.lowFood ? 'warn-t' : '';

    // Bữa hẹn giờ kế tiếp (dự kiến)
    const nm = set.scheduleEnabled ? nextMeal(set.scheduleTimes) : null;
    $('#next-meal').innerHTML = !set.scheduleEnabled ? '<span class="na">Lịch đang tắt</span>'
      : !nm ? '<span class="na">Chưa đặt giờ</span>'
      : `${nm.time} <small>${nm.tomorrow ? 'ngày mai' : 'hôm nay'}, dự kiến</small>`;

    $('#last-feed').innerHTML = d.lastFeedAt
      ? `${esc(d.lastFeedGram ?? '?')} g <small>${esc(ago(d.lastFeedAt))}${d.lastFeedSource ? ' · ' + esc(d.lastFeedSource) : ''}</small>`
      : '<span class="na">Chưa có</span>';
    $('#cat-present').innerHTML = !online ? '<span class="na">Không rõ</span>' : d.catPresent ? '<span class="ok-t">Có mèo</span>' : 'Không';

    // Nút cho ăn
    const feedBtn = $('#feed-btn');
    feedBtn.disabled = !d.brokerConnected || !online || feeding || pending;
    $('#feed-btn-text').textContent = pending ? 'Đang gửi lệnh…' : feeding ? `Đang nhả ${S.gram} g…` : `Cho ăn ngay ${S.gram} g`;
    $('#stop-btn').hidden = !feeding;
    const hint = $('#feed-hint');
    hint.className = 'dev-note' + (!d.brokerConnected || !online ? ' bad' : '');
    hint.textContent = !d.brokerConnected
      ? 'Không thể điều khiển: máy chủ chưa kết nối MQTT broker (kiểm tra MQTT_* trong .env).'
      : !online ? `Không thể cho ăn từ xa: máy ${d.lastSeen ? 'mất kết nối ' + ago(d.lastSeen) : 'chưa từng kết nối'}. Kiểm tra nguồn điện và Wi-Fi của ESP32.`
      : pending ? 'Đã gửi lệnh, đang chờ máy xác nhận.' : '';

    const rssi = d.wifiRssi;
    const wifi = !online ? '' : rssi == null ? 'Wi-Fi đã kết nối' : rssi > -60 ? 'Wi-Fi mạnh' : rssi > -75 ? 'Wi-Fi khá' : 'Wi-Fi yếu';
    $('#feeder-meta').textContent = [wifi, cameraText(d)].filter(Boolean).join(' · ');
    $('#feeder-meta').title = online ? `IP ${d.ip || '?'}, ${rssi ?? '?'} dBm` : '';
    $('#tare-btn').disabled = !online || feeding;

    // Tự động
    setSwitch('#auto-sw', set.autoMode);
    setSwitch('#sched-sw', set.scheduleEnabled);
    renderSchedule(set.scheduleTimes || [], nm, set.scheduleEnabled);

    // Bình nước (cảm biến siêu âm trên ESP32 máy dọn cát; bơm tuần hoàn chạy liên tục, không điều khiển từ xa)
    const wOn = d.waterOnline;
    const wp = wOn && d.waterSensorOk ? d.waterPercent : null;
    const ws = $('#water-state');
    if (!wOn) setBadge(ws, d.waterLastSeen ? 'bad' : 'off', d.waterLastSeen ? 'Mất tín hiệu' : 'Chưa kết nối');
    else if (!d.waterSensorOk) setBadge(ws, 'warn', 'Lỗi cảm biến');
    else if (d.lowWater) setBadge(ws, 'warn', 'Sắp hết nước');
    else setBadge(ws, 'ok', 'Đủ nước');
    $('#water-fill').style.width = (wp ?? 0) + '%';
    $('#water-fill').parentElement.classList.toggle('low', wOn && !!d.lowWater);
    const wpe = $('#water-pct');
    wpe.textContent = wp == null ? 'Không rõ' : `${wp}%`;
    wpe.className = wp == null ? 'na' : d.lowWater ? 'warn-t' : '';
    $('#water-seen').innerHTML = d.waterLastSeen ? esc(ago(d.waterLastSeen)) : '<span class="na">Chưa có</span>';
    const wn = $('#water-note');
    wn.className = 'dev-note' + (wOn && d.lowWater ? ' bad' : !wOn && d.waterLastSeen ? ' bad' : '');
    wn.textContent = !wOn ? (d.waterLastSeen
        ? 'ESP32 máy dọn cát mất kết nối, kiểm tra nguồn điện và Wi-Fi.'
        : 'Chưa nhận mực nước. Nạp firmware esp32_litter_water cho ESP32 máy dọn cát.')
      : !d.waterSensorOk ? 'Cảm biến siêu âm không đo được mặt nước, kiểm tra dây TRIG/ECHO.'
      : d.lowWater ? 'Hãy đổ thêm nước để bơm không chạy khô và bé luôn có nước uống.' : '';

    renderNotices();
  }

  // Thông báo cần người dùng xử lý (chỉ những gì có thao tác cụ thể)
  function renderNotices() {
    const d = S.device;
    const items = [];
    if (d && !d.brokerConnected) items.push({ id: 'mqtt', kind: 'bad', text: '<b>Mất kết nối MQTT.</b> Lệnh cho ăn và số liệu bình nước sẽ không cập nhật cho đến khi máy chủ kết nối lại.' });
    if (d?.online && d.lowFood) items.push({ id: 'food', kind: 'warn', text: `<b>Thùng chứa sắp hết hạt${d.foodLevelPercent >= 0 ? ` (còn ${d.foodLevelPercent}%)` : ''}.</b> Hãy châm thêm để máy nhả đủ khẩu phần.` });
    if (d?.waterOnline && d.lowWater) items.push({ id: 'water', kind: 'warn', text: `<b>Bình nước sắp cạn${d.waterPercent != null ? ` (còn ${d.waterPercent}%)` : ''}.</b> Hãy đổ thêm nước cho bé.` });
    const dayAgo = Date.now() - 86400000;
    (S.stats?.latestAlerts || [])
      .filter((a) => a.level === 'CRITICAL' && new Date(a.createdAt).getTime() > dayAgo)
      .slice(0, 2)
      .forEach((a) => items.push({ id: a.id, kind: 'bad', text: `<b>${esc(fmtTime(a.createdAt))}:</b> ${esc(a.message)}` }));
    $('#notices').innerHTML = items.filter((n) => !S.dismissed.has(String(n.id))).map((n) => `
      <div class="notice ${n.kind}" role="status"><svg class="ico"><use href="#i-alert"/></svg><span>${n.text}</span>
        <button class="notice-x" data-dismiss="${esc(n.id)}" aria-label="Ẩn thông báo"><svg class="ico"><use href="#i-x"/></svg></button></div>`).join('');
  }
  $('#notices').addEventListener('click', (e) => {
    const b = e.target.closest('[data-dismiss]');
    if (b) { S.dismissed.add(b.dataset.dismiss); renderNotices(); }
  });

  const TYPE_ICO = { FEEDING: 'i-bowl', LITTER: 'i-box', HEALTH: 'i-heart', SYSTEM: 'i-bolt' };
  const LEVEL = { WARNING: 'Chú ý', CRITICAL: 'Nghiêm trọng' };
  function renderStats() {
    const s = S.stats;
    if (!s) return;
    const all = s.latestAlerts || [];
    const list = S.actAll ? all : all.slice(0, 5);
    $('#alerts').innerHTML = list.map((a) => `
      <li class="${esc(a.level)}">
        <span class="tl-ico"><svg class="ico"><use href="#${TYPE_ICO[a.type] || 'i-bolt'}"/></svg></span>
        <span class="tl-msg">${LEVEL[a.level] ? `<span class="tl-lv">${LEVEL[a.level]}</span>` : ''}${esc(a.message)}<time datetime="${esc(a.createdAt)}">${esc(fmtTime(a.createdAt))}</time></span>
      </li>`).join('') || '<li class="empty">Chưa có hoạt động nào.</li>';
    const more = $('#act-more');
    more.hidden = all.length <= 5;
    more.textContent = S.actAll ? 'Thu gọn' : `Xem thêm (${all.length - 5})`;
    renderNotices();
  }
  $('#act-more').addEventListener('click', () => { S.actAll = !S.actAll; renderStats(); });

  function renderBars(el, days) {
    const max = Math.max(1, ...days.map((d) => d.gram));
    const last = days.length - 1;
    el.innerHTML = days.map((d, i) => `
      <div class="bar ${i === last ? 'today' : ''}" title="${esc(d.date)}: ${d.gram} g, ${d.meals} bữa">
        <b>${d.gram || ''}</b>
        <div class="col" style="height:${Math.round((d.gram / max) * 100)}%"></div>
        <span>${i === last ? 'Nay' : dayLabel(d.date)}</span>
      </div>`).join('');
  }

  // ------------------------------------------------------------------ camera trực tiếp
  // Lấy khung mới nhất bằng long-polling (máy chủ giữ yêu cầu tới khi Pi gửi khung mới, tối đa 2 giây).
  // Chỉ chạy khi đang mở trang Tổng quan và tab đang hiển thị để tiết kiệm băng thông.
  const CAM = { seq: 0, running: false, paused: false, url: null, hadFrame: false, keyConfigured: null };
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  const hms = (ms) => new Date(ms).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });

  function renderCam(s) {
    const frame = $('#cam-frame');
    const live = s.online && !CAM.paused;
    frame.classList.toggle('stale', CAM.hadFrame && !live);
    $('#cam-img').hidden = !CAM.hadFrame;
    $('#cam-empty').hidden = CAM.hadFrame;
    $('#cam-live').hidden = !live;
    $('#cam-time').textContent = !s.time ? '' : live ? hms(s.time) : `Lần cuối ${hms(s.time)}`;

    const st = $('#cam-state');
    if (CAM.paused) setBadge(st, 'off', 'Tạm dừng');
    else if (s.online) setBadge(st, 'ok', 'Trực tiếp');
    else if (CAM.hadFrame || s.time) setBadge(st, 'warn', 'Mất tín hiệu');
    else setBadge(st, 'off', 'Chưa kết nối');

    if (!CAM.hadFrame) {
      $('#cam-empty-text').innerHTML = s.error ? esc(s.error)
        : CAM.keyConfigured === false ? 'Máy chủ chưa đặt <code>CAMERA_KEY</code> trong file .env.'
        : 'Chưa có hình. Chạy <code>cat_detection_gateway.py</code> trên Raspberry Pi<br>với <code>AURAPET_SERVER</code> và <code>CAMERA_KEY</code>.';
    }
    $('#cam-info').innerHTML = !s.online ? (s.error ? esc(s.error) : CAM.hadFrame ? 'Đang chờ camera gửi lại hình…' : '')
      : (s.cat ? `<b class="cat">Có mèo</b> · tin cậy ${Math.round(s.conf * 100)}%` : '<b>Không thấy mèo</b>')
        + (s.fps ? ` · ${nf(s.fps, 1)} khung/giây` : '');
    $('#cam-pause').textContent = CAM.paused ? 'Xem tiếp' : 'Tạm dừng';
    $('#cam-pause').setAttribute('aria-pressed', String(CAM.paused));
  }

  const camActive = () => token && !CAM.paused && !document.hidden && !$('#view-control').hidden;
  async function camLoop() {
    if (CAM.running || !camActive()) return;
    CAM.running = true;
    if (CAM.keyConfigured === null) {
      try { CAM.keyConfigured = (await api('/api/v1/camera/status')).keyConfigured; } catch { /* bỏ qua */ }
    }
    while (camActive()) {
      try {
        const res = await fetch(`${API}/api/v1/camera/frame?after=${CAM.seq}&wait=2000`, {
          headers: { Authorization: `Bearer ${token}` }, cache: 'no-store',
        });
        if (res.status === 401) { logout('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại'); break; }
        const h = (k) => res.headers.get(k);
        const s = {
          online: h('X-Camera-Online') === 'true', time: Number(h('X-Frame-Time')) || null,
          cat: h('X-Cat-Present') === 'true', conf: Number(h('X-Cat-Confidence')) || 0, fps: Number(h('X-Camera-Fps')) || 0,
        };
        if (res.status === 200) {
          const blob = await res.blob();
          const url = URL.createObjectURL(blob);
          const img = $('#cam-img');
          const old = CAM.url;
          img.onload = () => { if (old) URL.revokeObjectURL(old); };
          img.src = url;
          CAM.url = url;
          CAM.hadFrame = true;
          CAM.seq = Number(h('X-Frame-Seq')) || CAM.seq;
          renderCam(s);
        } else if (res.status === 204) {
          renderCam(s);
        } else {
          renderCam({ online: false, error: `Máy chủ trả lỗi ${res.status}` });
          await sleep(3000);
        }
      } catch {
        renderCam({ online: false, error: 'Không tải được hình từ máy chủ' });
        await sleep(3000);
      }
    }
    CAM.running = false;
  }
  $('#cam-pause').addEventListener('click', () => {
    CAM.paused = !CAM.paused;
    renderCam({ online: false });
    if (!CAM.paused) camLoop();
  });
  $('#cam-full').addEventListener('click', () => {
    const f = $('#cam-frame');
    if (document.fullscreenElement) document.exitFullscreen();
    else f.requestFullscreen?.().catch(() => toast('Trình duyệt không cho phóng to toàn màn hình', 'err'));
  });
  document.addEventListener('fullscreenchange', () => {
    $('#cam-full').textContent = document.fullscreenElement ? 'Thu nhỏ' : 'Phóng to';
  });

  // Khẩu phần
  const PRESETS = [15, 25, 40, 60];
  $('#presets').innerHTML = PRESETS.map((g) => `<button class="preset" data-g="${g}" aria-pressed="false">${g} g</button>`).join('');
  function renderGram() {
    $('#gram-out').textContent = `${S.gram} g`;
    $('#feed-btn-text').textContent = `Cho ăn ngay ${S.gram} g`;
    $$('.preset').forEach((b) => b.setAttribute('aria-pressed', String(Number(b.dataset.g) === S.gram)));
  }
  let gramSaveTimer = null;
  function changeGram(g) {
    S.gram = Math.max(5, Math.min(150, g));
    gramDirty = Date.now();
    renderGram();
    clearTimeout(gramSaveTimer);
    gramSaveTimer = setTimeout(() => saveSettings({ targetGram: S.gram }, true), 600);
  }
  $('#gram-minus').addEventListener('click', () => changeGram(S.gram - 5));
  $('#gram-plus').addEventListener('click', () => changeGram(S.gram + 5));
  $('#presets').addEventListener('click', (e) => { const b = e.target.closest('.preset'); if (b) changeGram(Number(b.dataset.g)); });

  async function saveSettings(patch, quiet = false) {
    try {
      S.device = await api('/api/v1/device/settings', { method: 'PUT', body: patch });
      renderDevice();
      if (!quiet) toast('Đã lưu cài đặt', 'ok');
      if ('feederPetId' in patch) loadPetSummary();
    } catch (e) {
      toast(e.message, 'err');
      refreshState();
    }
  }

  async function command(path, body, btn, okMsg) {
    const data = await withBusy(btn, () => api(path, { method: 'POST', body: body ?? {} }));
    if (data) {
      S.device = data;
      renderDevice();
      if (okMsg) toast(okMsg, 'ok');
      setTimeout(refreshStats, 4000);
    }
    return data;
  }
  $('#feed-btn').addEventListener('click', async (e) => {
    feedPendingUntil = Date.now() + 5000;
    renderDevice();
    const ok = await command('/api/v1/device/feed', { gram: S.gram, source: 'Web' }, e.currentTarget, `Đã gửi lệnh nhả ${S.gram} g`);
    if (!ok) { feedPendingUntil = 0; renderDevice(); return; }
    setTimeout(refreshState, 700);
  });
  $('#stop-btn').addEventListener('click', (e) => command('/api/v1/device/stop', {}, e.currentTarget, 'Đã dừng động cơ'));
  $('#tare-btn').addEventListener('click', (e) => {
    if (!confirm('Hãy lấy hết hạt ra khỏi khay trước. Trừ bì cân về 0 g ngay bây giờ?')) return;
    command('/api/v1/device/tare', {}, e.currentTarget, 'Đã trừ bì cân');
  });
  $('#auto-sw').addEventListener('click', (e) => saveSettings({ autoMode: e.currentTarget.getAttribute('aria-checked') !== 'true' }));
  $('#sched-sw').addEventListener('click', (e) => saveSettings({ scheduleEnabled: e.currentTarget.getAttribute('aria-checked') !== 'true' }));

  // Lịch hẹn giờ
  function renderSchedule(times, next, enabled) {
    $('.sched').classList.toggle('off', !enabled);
    $('#sched-chips').innerHTML = times.length
      ? [...times].sort().map((t) => `<span class="chip${enabled && next?.time === t ? ' next' : ''}"${enabled && next?.time === t ? ' title="Bữa kế tiếp"' : ''}>${esc(t)}<button data-del="${esc(t)}" aria-label="Xoá giờ ${esc(t)}"><svg class="ico"><use href="#i-x"/></svg></button></span>`).join('')
      : '<span class="hint">Chưa có giờ nào.</span>';
  }
  $('#sched-chips').addEventListener('click', (e) => {
    const b = e.target.closest('[data-del]');
    if (!b) return;
    const times = (S.device?.settings?.scheduleTimes || []).filter((t) => t !== b.dataset.del);
    saveSettings({ scheduleTimes: times });
  });
  $('#sched-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const v = $('#sched-time').value;
    if (!v) return;
    const times = [...new Set([...(S.device?.settings?.scheduleTimes || []), v])];
    saveSettings({ scheduleTimes: times, scheduleEnabled: true });
  });

  // ------------------------------------------------------------------ thú cưng
  async function loadPets(silent) {
    try {
      const q = $('#pet-search').value.trim();
      const data = await api('/api/v1/pets?size=100' + (q ? `&search=${encodeURIComponent(q)}` : ''));
      S.pets = data.items || [];
      renderPets();
    } catch (e) {
      if (!silent) toast(e.message, 'err');
    }
  }
  let searchTimer;
  $('#pet-search').addEventListener('input', () => { clearTimeout(searchTimer); searchTimer = setTimeout(loadPets, 300); });

  const avatar = (p) => p.imageUrl
    ? `<img class="avatar" src="${esc(p.imageUrl)}" alt="" loading="lazy" data-initial="${esc(p.name.charAt(0))}">`
    : `<span class="avatar">${esc(p.name.charAt(0))}</span>`;
  // Ảnh lỗi -> hiện chữ cái đầu (không dùng onerror inline để tránh chèn mã)
  document.addEventListener('error', (e) => {
    const img = e.target;
    if (img instanceof HTMLImageElement && img.classList.contains('avatar')) {
      const span = document.createElement('span');
      span.className = 'avatar';
      span.textContent = img.dataset.initial || '?';
      img.replaceWith(span);
    }
  }, true);

  function renderPets() {
    const feederId = S.device?.settings?.feederPetId;
    $('#pet-list').innerHTML = S.pets.length ? S.pets.map((p) => `
      <li class="pet-row" data-id="${esc(p.id)}" aria-selected="${S.selectedPet === p.id}" tabindex="0">
        ${avatar(p)}
        <div><b>${esc(p.name)}${p.id === feederId ? '<span class="tag">Máy cho ăn</span>' : ''}</b>
          <small>${esc(p.breed)}, ${esc(p.age)} tuổi, ${esc(Number(p.weight).toFixed(2))} kg, ${p.gender === 'MALE' ? 'đực' : 'cái'}</small></div>
        <div class="row-actions">
          <button class="icon-btn" data-edit="${esc(p.id)}" aria-label="Sửa ${esc(p.name)}"><svg class="ico"><use href="#i-edit"/></svg></button>
          <button class="icon-btn" data-delete="${esc(p.id)}" aria-label="Xoá ${esc(p.name)}"><svg class="ico"><use href="#i-trash"/></svg></button>
        </div>
      </li>`).join('') : '<li class="empty">Chưa có thú cưng nào. Bấm "Thêm thú cưng" để bắt đầu.</li>';
    renderPetSelects();
  }

  $('#pet-list').addEventListener('click', async (e) => {
    const edit = e.target.closest('[data-edit]');
    const del = e.target.closest('[data-delete]');
    if (edit) return openPetDialog(S.pets.find((p) => p.id === edit.dataset.edit));
    if (del) {
      const p = S.pets.find((x) => x.id === del.dataset.delete);
      if (!confirm(`Xoá ${p.name}? Lịch sử ăn của bé vẫn được giữ.`)) return;
      await withBusy(del, async () => {
        await api(`/api/v1/pets/${p.id}`, { method: 'DELETE' });
        toast(`Đã xoá ${p.name}`, 'ok');
        if (S.selectedPet === p.id) { S.selectedPet = null; $('#pet-detail').innerHTML = '<p class="empty">Chọn một bé để xem sức khoẻ và lượng ăn.</p>'; }
        await loadPets();
        loadPetSummary();
      });
      return;
    }
    const row = e.target.closest('.pet-row');
    if (row) selectPet(row.dataset.id);
  });
  $('#pet-list').addEventListener('keydown', (e) => {
    const row = e.target.closest('.pet-row');
    if (row && (e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); selectPet(row.dataset.id); }
  });

  async function selectPet(id) {
    S.selectedPet = id;
    $$('.pet-row').forEach((r) => r.setAttribute('aria-selected', String(r.dataset.id === id)));
    const p = S.pets.find((x) => x.id === id);
    const box = $('#pet-detail');
    box.innerHTML = '<p class="empty">Đang tải…</p>';
    try {
      const [h, daily, weights] = await Promise.all([
        api(`/api/v1/health/${id}/summary`),
        api(`/api/v1/health/${id}/food/daily?days=7`),
        api(`/api/v1/health/${id}/weight`),
      ]);
      const cls = h.healthScore >= 90 ? '' : h.healthScore >= 75 ? 'mid' : 'low';
      const isFeeder = S.device?.settings?.feederPetId === id;
      box.innerHTML = `
        <div class="pd-head">${avatar(p)}
          <div><h2>${esc(p.name)}</h2><p class="hint">${esc(p.breed)}, ${esc(p.age)} tuổi, ${p.neutered ? 'đã triệt sản' : 'chưa triệt sản'}</p></div>
          <div class="score ${cls}" title="Điểm sức khoẻ">${h.healthScore}</div>
        </div>
        <div class="mini-stats">
          <div><b>${Math.round(h.dailyAvgFoodGram)} g</b><span>ăn/ngày (nên ~${h.expectedDailyFoodGram} g)</span></div>
          <div><b>${h.weeklyLitterVisits}</b><span>lần vệ sinh/tuần</span></div>
          <div><b>${h.weightChangeKg > 0 ? '+' : ''}${h.weightChangeKg} kg</b><span>so với lần cân trước</span></div>
        </div>
        <div class="pd-sec"><h3>${esc(h.status)}</h3><ul class="notes">${(h.notes || []).map((n) => `<li>${esc(n)}</li>`).join('')}</ul></div>
        <div class="pd-sec"><h3>Lượng ăn 7 ngày</h3><div class="bars" id="pd-bars"></div></div>
        <div class="pd-sec"><h3>Cân nặng</h3>${sparkline(weights)}</div>
        <button class="btn ${isFeeder ? 'btn-quiet' : ''}" id="pd-feeder" ${isFeeder ? 'disabled' : ''}>
          <svg class="ico"><use href="#i-bowl"/></svg>${isFeeder ? 'Bé đang dùng máy cho ăn' : 'Gán bé cho máy cho ăn'}</button>`;
      renderBars($('#pd-bars'), daily);
      const fb = $('#pd-feeder');
      if (fb && !isFeeder) fb.addEventListener('click', async () => { await saveSettings({ feederPetId: id }); renderPets(); selectPet(id); });
    } catch (e) {
      box.innerHTML = `<p class="empty">${esc(e.message)}</p>`;
    }
  }

  function sparkline(points) {
    if (!points || points.length < 2) return '<p class="hint">Cần ít nhất 2 lần cân để vẽ biểu đồ.</p>';
    const W = 300, H = 90, pad = 8;
    const ws = points.map((p) => p.weight);
    const min = Math.min(...ws), max = Math.max(...ws), span = Math.max(0.05, max - min);
    const xy = points.map((p, i) => [pad + (i * (W - 2 * pad)) / (points.length - 1), H - pad - ((p.weight - min) / span) * (H - 2 * pad)]);
    const line = xy.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
    const area = `${line} L${xy[xy.length - 1][0].toFixed(1)} ${H} L${xy[0][0].toFixed(1)} ${H} Z`;
    return `<svg class="spark" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" role="img" aria-label="Cân nặng từ ${min.toFixed(2)} đến ${max.toFixed(2)} kg">
      <path class="area" d="${area}"/><path class="line" d="${line}"/></svg>
      <p class="hint">Hiện tại ${ws[ws.length - 1].toFixed(2)} kg, thấp nhất ${min.toFixed(2)} kg, cao nhất ${max.toFixed(2)} kg</p>`;
  }

  // Hộp thoại thêm/sửa
  let editingId = null;
  function openPetDialog(p) {
    editingId = p ? p.id : null;
    const f = $('#pet-form');
    f.reset();
    $('#pet-error').textContent = '';
    $('#pet-dialog-title').textContent = p ? `Sửa ${p.name}` : 'Thêm thú cưng';
    if (p) {
      f.name.value = p.name; f.breed.value = p.breed; f.age.value = p.age; f.weight.value = p.weight;
      f.gender.value = p.gender; f.neutered.value = String(p.neutered); f.imageUrl.value = p.imageUrl || '';
    }
    $('#pet-dialog').showModal();
  }
  $('#pet-add').addEventListener('click', () => openPetDialog(null));
  $('#pet-form').addEventListener('submit', async (e) => {
    if (e.submitter?.value !== 'save') return;
    e.preventDefault();
    const f = e.target;
    if (!f.reportValidity()) return;
    const body = {
      name: f.name.value.trim(), breed: f.breed.value.trim(), age: Number(f.age.value), weight: Number(f.weight.value),
      gender: f.gender.value, neutered: f.neutered.value === 'true', imageUrl: f.imageUrl.value.trim(),
    };
    try {
      await api(editingId ? `/api/v1/pets/${editingId}` : '/api/v1/pets', { method: editingId ? 'PUT' : 'POST', body });
      $('#pet-dialog').close();
      toast(editingId ? 'Đã lưu thay đổi' : `Đã thêm ${body.name}`, 'ok');
      await loadPets();
      loadPetSummary();
      if (editingId) selectPet(editingId);
    } catch (err) {
      $('#pet-error').textContent = err.message;
    }
  });

  function renderPetSelects() {
    const opts = S.pets.map((p) => `<option value="${esc(p.id)}">${esc(p.name)}</option>`).join('');
    const ai = $('#ai-pet');
    const keep = ai.value;
    ai.innerHTML = '<option value="">Chung</option>' + opts;
    if (keep) ai.value = keep;
    const fs = $('#feeder-pet-select');
    fs.innerHTML = opts || '<option value="">Chưa có thú cưng</option>';
    if (S.device?.settings?.feederPetId) fs.value = S.device.settings.feederPetId;
  }

  // ------------------------------------------------------------------ lịch sử
  async function loadHistory() {
    try {
      const [rows] = await Promise.all([api('/api/v1/device/feedings?limit=100'), refreshStats()]);
      const week = S.stats?.last7Days || [];
      renderBars($('#hist-bars'), week);
      const tg = week.reduce((a, d) => a + d.gram, 0);
      const tm = week.reduce((a, d) => a + d.meals, 0);
      $('#hist-sum').textContent = `Tổng ${tg} g · ${tm} bữa · TB ${Math.round(tg / Math.max(1, week.length))} g/ngày`;
      $('#hist-rows').innerHTML = rows.length ? rows.map((r) => `
        <tr><td>${esc(new Date(r.timestamp).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' }))}</td>
        <td>${esc(r.petName)}</td><td class="num">${r.foodGram} g</td><td>${esc(r.source || '–')}</td></tr>`).join('')
        : '<tr><td colspan="4" class="empty">Chưa có lần cho ăn nào.</td></tr>';
    } catch (e) {
      toast(e.message, 'err');
    }
  }

  // ------------------------------------------------------------------ bác sĩ AI
  const SUGGEST = ['Bé ăn như vậy có đủ không?', 'Nên chia mấy bữa một ngày?', 'Bé cần uống bao nhiêu nước?', 'Dấu hiệu nào cần đi bác sĩ?'];
  let chatReady = false;
  async function initChat() {
    if (chatReady) return;
    chatReady = true;
    $('#ai-suggest').innerHTML = SUGGEST.map((q) => `<button class="chip-btn" data-q="${esc(q)}">${esc(q)}</button>`).join('');
    addMsg('bot', 'Chào bạn! Mình đọc được lượng ăn, cân nặng và trạng thái máy của bé. Bạn muốn hỏi gì?');
    try {
      const ok = await api('/api/v1/ai/status');
      $('#ai-mode').textContent = ok ? 'Đang dùng Gemini' : 'Chế độ cơ bản (chưa có GEMINI_API_KEY)';
    } catch { /* bỏ qua */ }
  }
  function addMsg(role, text) {
    const el = document.createElement('div');
    el.className = `msg ${role}`;
    // Câu trả lời AI có **in đậm** kiểu Markdown: thoát HTML trước, rồi chỉ chuyển phần in đậm
    if (role === 'bot') {
      el.innerHTML = esc(text)
        .replace(/^[ \t]*[*-][ \t]+/gm, '• ')
        .replace(/\*\*([^*\n]+)\*\*/g, '<b>$1</b>')
        .replace(/\*([^*\n]+)\*/g, '$1');
    }
    else el.textContent = text;
    $('#chat-log').append(el);
    $('#chat-log').scrollTop = 1e9;
    return el;
  }
  async function ask(q) {
    if (!q.trim()) return;
    addMsg('user', q);
    const typing = addMsg('bot typing', 'Đang suy nghĩ…');
    const history = S.chat.slice(-8);
    try {
      const r = await api('/api/v1/ai/chat', { method: 'POST', body: { message: q, petId: $('#ai-pet').value || null, history } });
      typing.remove();
      addMsg('bot', r.reply);
      S.chat.push({ role: 'user', text: q }, { role: 'model', text: r.reply });
    } catch (e) {
      typing.remove();
      addMsg('bot', e.message);
    }
  }
  $('#ai-suggest').addEventListener('click', (e) => { const b = e.target.closest('[data-q]'); if (b) ask(b.dataset.q); });
  $('#chat-form').addEventListener('submit', (e) => {
    e.preventDefault();
    const t = $('#chat-input');
    const q = t.value;
    t.value = '';
    t.style.height = '';
    ask(q);
  });
  $('#chat-input').addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); $('#chat-form').requestSubmit(); }
  });
  $('#chat-input').addEventListener('input', (e) => { e.target.style.height = ''; e.target.style.height = Math.min(140, e.target.scrollHeight) + 'px'; });

  // ------------------------------------------------------------------ tài khoản
  function renderAccount() {
    const f = $('#profile-form');
    f.name.value = S.user?.name || '';
    f.email.value = S.user?.email || '';
    $('#api-base-label').textContent = API;
    const d = S.device;
    $('#device-info').textContent = d?.online ? `ESP32 tại ${d.ip}, đã nhả ${d.feedCountSinceBoot} lần từ lúc bật nguồn.` : 'Máy cho ăn đang offline.';
    renderPetSelects();
  }
  $('#profile-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = e.submitter;
    const u = await withBusy(btn, () => api('/api/v1/auth/me', { method: 'PUT', body: { name: e.target.name.value.trim() } }));
    if (u) { S.user = u; $('#who').textContent = u.name; toast('Đã lưu tên', 'ok'); }
  });
  $('#password-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = e.target;
    const u = await withBusy(e.submitter, () => api('/api/v1/auth/me', { method: 'PUT', body: { currentPassword: f.currentPassword.value, newPassword: f.newPassword.value } }));
    if (u) { f.reset(); toast('Đã đổi mật khẩu', 'ok'); }
  });
  $('#feeder-pet-select').addEventListener('change', (e) => { if (e.target.value) saveSettings({ feederPetId: e.target.value }); });

  // ------------------------------------------------------------------ khởi động
  document.addEventListener('visibilitychange', () => { if (!document.hidden && token) { refreshState(); camLoop(); } });
  setAuthMode('login');
  renderServerLabel();
  if (token && API) enterApp();
  else $('#auth').hidden = false;
})();
