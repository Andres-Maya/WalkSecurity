/*
 * Simulador web del reloj de WalkSecurity.
 * Reproduce las reglas de WatchController (watch-core) y las pantallas de WatchApp:
 * mismos estados, textos, tamaños lógicos y patrones de vibración. El teléfono es simulado.
 */
(() => {
  const LEVELS = {
    SAFE: { order: 0, title: 'Zona segura', color: '#2e7d32', text: '#81c784', zone: null, score: 12 },
    CAUTION: { order: 1, title: 'Precaución', color: '#f9a825', text: '#f9a825', zone: 'Parque Central', score: 52 },
    ALERT: { order: 2, title: 'Alerta', color: '#c62828', text: '#ef9a9a', zone: 'Calle 19 con Cra. 7', score: 86 },
  };
  // Tamaño lógico de pantalla (dp) de relojes Wear OS reales
  const SHAPES = { large: { size: 227, round: true }, small: { size: 192, round: true }, square: { size: 180, round: false } };
  const PATTERNS = { CAUTION: [0, 250, 150, 250], ALERT: [0, 600, 200, 600, 200, 600], SENT: [0, 100, 100, 100, 100, 400] };
  const COUNTDOWN_SECONDS = 5;

  const $ = (id) => document.getElementById(id);
  const device = $('device'), screen = $('screen'), logList = $('log'), vibrationLabel = $('vibration');

  // Estado del teléfono simulado y del reloj
  const phone = { level: 'SAFE', connected: true, contacts: 2 };
  const watch = { risk: status('SAFE'), phoneReachable: true, sos: { kind: 'idle' }, acknowledgedAt: 0 };
  let shape = 'large', sosTimer = null, vibrationRun = 0, firstLog = true;

  function status(level) {
    return { level, zone: LEVELS[level].zone, updatedAt: Date.now() };
  }

  // ---------- Registro de eventos ----------
  function log(source, text) {
    if (firstLog) { logList.innerHTML = ''; firstLog = false; }
    const labels = { phone: 'Teléfono', watch: 'Reloj', user: 'Usuario' };
    const li = document.createElement('li');
    const time = document.createElement('time');
    time.textContent = new Date().toLocaleTimeString('es-CO', { hour12: false });
    const tag = document.createElement('span');
    tag.className = 'tag ' + source;
    tag.textContent = labels[source];
    const msg = document.createElement('span');
    msg.textContent = text;
    li.append(time, tag, msg);
    logList.prepend(li);
    while (logList.children.length > 60) logList.lastChild.remove();
  }

  // ---------- Vibración (sacudida en pantalla; vibración real en móviles que la soporten) ----------
  async function vibrate(pattern, label) {
    const run = ++vibrationRun;
    if (label) { log('watch', 'Vibra: ' + label); vibrationLabel.textContent = 'Vibrando: ' + label; }
    if (navigator.vibrate) navigator.vibrate(pattern.slice(1));
    for (let i = 0; i < pattern.length && run === vibrationRun; i++) {
      const on = i % 2 === 1;
      device.classList.toggle('shake', on);
      device.classList.toggle('vibrating', on);
      await sleep(pattern[i]);
    }
    if (run !== vibrationRun) return;
    device.classList.remove('shake', 'vibrating');
    if (label) { await sleep(1200); if (run === vibrationRun) vibrationLabel.innerHTML = '&nbsp;'; }
  }
  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

  // ---------- Teléfono simulado ----------
  function publish(level) {
    phone.level = level;
    const info = LEVELS[level];
    log('phone', `Calcula la zona: ${info.title}${info.zone ? ' · ' + info.zone : ''} (riesgo estimado ${info.score} %)`);
    if (phone.connected) deliver(); else log('phone', 'Reloj desconectado: se entregará al reconectar');
    syncControls();
  }

  function deliver() {
    const previous = watch.risk.level;
    watch.risk = status(phone.level);
    log('watch', 'Recibe el estado: ' + LEVELS[phone.level].title);
    // Solo vibra cuando el riesgo SUBE
    if (LEVELS[phone.level].order > LEVELS[previous].order) {
      vibrate(PATTERNS[phone.level], phone.level === 'ALERT' ? 'vibración larga (alerta)' : 'vibración corta (precaución)');
    }
    render();
  }

  function setConnected(value) {
    phone.connected = value;
    watch.phoneReachable = value;
    log('user', value ? 'Reconecta el reloj con el teléfono' : 'Desconecta el reloj del teléfono');
    if (value && watch.risk.level !== phone.level) deliver();
    syncControls();
    render();
  }

  // ---------- Lógica del reloj (igual que WatchController) ----------
  const pendingAlert = () => watch.risk.level === 'ALERT' && watch.risk.updatedAt > watch.acknowledgedAt;

  function onSosPressed() {
    if (watch.sos.kind !== 'idle') return;
    log('user', 'Pulsa SOS en el reloj (cuenta regresiva de 5 s)');
    let seconds = COUNTDOWN_SECONDS;
    watch.sos = { kind: 'countdown', seconds };
    tick();
    render();
    sosTimer = setInterval(() => {
      seconds -= 1;
      if (seconds <= 0) { clearInterval(sosTimer); send('botón SOS'); return; }
      watch.sos = { kind: 'countdown', seconds };
      tick();
      render();
    }, 1000);
  }

  function tick() {
    device.classList.remove('tick');
    void device.offsetWidth; // reinicia la animación
    device.classList.add('tick');
  }

  function sendNow() {
    if (watch.sos.kind !== 'countdown') return;
    clearInterval(sosTimer);
    log('user', 'Pulsa «Enviar»');
    send('botón SOS');
  }

  function cancelSos() {
    if (watch.sos.kind !== 'countdown') return;
    clearInterval(sosTimer);
    watch.sos = { kind: 'idle' };
    log('user', 'Cancela el SOS');
    render();
  }

  function acknowledge() {
    watch.acknowledgedAt = watch.risk.updatedAt;
  }

  function confirmEmergency() {
    log('user', 'Confirma «Emergencia» en la alerta');
    acknowledge();
    if (watch.sos.kind === 'idle') send('confirmó emergencia');
    else render();
  }

  async function send(origin) {
    watch.sos = { kind: 'sending' };
    render();
    await sleep(400);
    if (!phone.connected) {
      log('watch', 'No encuentra el teléfono: el SOS no se pudo entregar');
      watch.phoneReachable = false;
      watch.sos = { kind: 'failed', message: 'Teléfono no conectado. Usa el teléfono para pedir ayuda.' };
      render();
      return;
    }
    log('watch', `Envía SOS al teléfono (${origin})`);
    watch.sos = { kind: 'waiting' };
    render();
    await sleep(1200); // el teléfono obtiene su GPS y envía los SMS
    const total = phone.contacts;
    log('phone', total > 0
      ? `Envía SMS con la ubicación a ${total} de ${total} contactos y registra la alerta (simulado)`
      : 'No hay contactos de emergencia: no se envió ningún SMS');
    if (total > 0) vibrate(PATTERNS.SENT, 'vibración de confirmación (SOS enviado)');
    watch.sos = { kind: 'done', sent: total, total };
    render();
  }

  // ---------- Pantallas (igual que WatchApp) ----------
  function render() {
    const clock = new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit', hour12: false });
    let body;
    if (watch.sos.kind !== 'idle') body = sosScreen();
    else if (pendingAlert()) body = alertScreen();
    else body = homeScreen();
    screen.innerHTML = `<div class="w-clock">${clock}</div>${body}`;
  }

  function homeScreen() {
    const info = LEVELS[watch.risk.level];
    const subtitle = !watch.phoneReachable
      ? 'Teléfono desconectado'
      : [info.zone, 'simulado'].filter(Boolean).join(' · ');
    return `<div class="w-col w-home">
      <div class="w-title" style="color:${info.text}"><span class="w-dot" style="background:${info.color}"></span>${info.title}</div>
      <div class="w-sub">${subtitle}</div>
      <button class="w-btn w-sosbtn" data-action="sos" aria-label="Enviar alerta SOS">SOS</button>
    </div>`;
  }

  function alertScreen() {
    const zone = LEVELS.ALERT.zone;
    return `<div class="w-col w-alert">
      <div style="font-size:16px;font-weight:700">¿Estás bien?</div>
      <div style="font-size:11px">Entraste en una zona con riesgo estimado alto: ${zone}.</div>
      <div class="w-row">
        <button class="w-btn w-pill gray" data-action="ok">Estoy bien</button>
        <button class="w-btn w-pill red" data-action="emergency">Emergencia</button>
      </div>
      <div style="font-size:9px;color:#bdbdbd">Estimación, no garantía.</div>
    </div>`;
  }

  function sosScreen() {
    const sos = watch.sos;
    let inner;
    if (sos.kind === 'countdown') {
      inner = `<div style="font-size:12px">Enviando SOS en</div>
        <div style="font-size:40px;font-weight:900;color:#d32f2f;line-height:1.1">${sos.seconds}</div>
        <div class="w-row">
          <button class="w-btn w-pill gray" data-action="cancel">Cancelar</button>
          <button class="w-btn w-pill red" data-action="sendnow">Enviar</button>
        </div>`;
    } else if (sos.kind === 'sending') {
      inner = '<div style="font-size:14px">Enviando al teléfono…</div>';
    } else if (sos.kind === 'waiting') {
      inner = '<div style="font-size:14px">Avisando a tus contactos…</div>';
    } else if (sos.kind === 'done') {
      const [title, detail] = sos.sent > 0
        ? ['Alerta enviada', `SMS a ${sos.sent} de ${sos.total} contactos.`]
        : ['Sin contactos', 'Agrega contactos en el teléfono.'];
      inner = `<div style="font-size:16px;font-weight:700">${title}</div>
        <div style="font-size:12px">${detail}</div>
        <button class="w-btn w-pill single gray" data-action="dismiss">Cerrar</button>`;
    } else {
      inner = `<div style="font-size:16px;font-weight:700;color:#d32f2f">No se envió</div>
        <div style="font-size:12px">${sos.message}</div>
        <button class="w-btn w-pill single gray" data-action="dismiss">Cerrar</button>`;
    }
    return `<div class="w-col w-sos">${inner}</div>`;
  }

  screen.addEventListener('click', (event) => {
    const action = event.target.closest('[data-action]')?.dataset.action;
    if (action === 'sos') onSosPressed();
    else if (action === 'sendnow') sendNow();
    else if (action === 'cancel') cancelSos();
    else if (action === 'emergency') confirmEmergency();
    else if (action === 'ok') { log('user', 'Responde «Estoy bien»'); acknowledge(); render(); }
    else if (action === 'dismiss') { watch.sos = { kind: 'idle' }; render(); }
  });

  // ---------- Panel ----------
  function syncControls() {
    document.querySelectorAll('[data-level]').forEach((b) => b.setAttribute('aria-pressed', b.dataset.level === phone.level));
    document.querySelectorAll('[data-contacts]').forEach((b) => b.setAttribute('aria-pressed', Number(b.dataset.contacts) === phone.contacts));
    document.querySelectorAll('[data-shape]').forEach((b) => b.setAttribute('aria-pressed', b.dataset.shape === shape));
    $('conn-label').textContent = phone.connected ? 'Reloj conectado al teléfono' : 'Reloj desconectado';
  }

  document.querySelectorAll('[data-level]').forEach((b) => b.addEventListener('click', () => publish(b.dataset.level)));
  document.querySelectorAll('[data-contacts]').forEach((b) => b.addEventListener('click', () => {
    phone.contacts = Number(b.dataset.contacts);
    log('user', 'Contactos de emergencia en el teléfono: ' + phone.contacts);
    syncControls();
  }));
  document.querySelectorAll('[data-shape]').forEach((b) => b.addEventListener('click', () => { shape = b.dataset.shape; syncControls(); layout(); }));
  $('connected').addEventListener('change', (e) => setConnected(e.target.checked));

  // La pantalla se dibuja a su tamaño lógico real y se escala al tamaño de la caja
  function layout() {
    const { size, round } = SHAPES[shape];
    const available = Math.min(device.parentElement.clientWidth - 40, 380);
    const caseSize = Math.max(220, available);
    device.style.setProperty('--case', caseSize + 'px');
    device.classList.toggle('square', !round);
    const inner = caseSize - 2 * caseSize * 0.055 - 4;
    screen.style.width = size + 'px';
    screen.style.height = size + 'px';
    screen.style.transform = `translate(-50%, -50%) scale(${inner / size})`;
  }

  window.addEventListener('resize', layout);
  setInterval(() => { if (watch.sos.kind === 'idle' && !pendingAlert()) render(); }, 15000);
  layout();
  syncControls();
  render();
})();
