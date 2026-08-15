const state = {
  overview: null,
  pairingTimer: null,
};

const elements = {
  overallStatus: document.querySelector('#overall-status'),
  heroTitle: document.querySelector('#hero-title'),
  heroCopy: document.querySelector('#hero-copy'),
  errorBanner: document.querySelector('#error-banner'),
  refreshButton: document.querySelector('#refresh-button'),
  qrWrap: document.querySelector('#qr-wrap'),
  qr: document.querySelector('#expo-qr'),
  qrPlaceholder: document.querySelector('#qr-placeholder'),
  copyExpoUrl: document.querySelector('#copy-expo-url'),
  pairButton: document.querySelector('#pair-button'),
  pairingPanel: document.querySelector('#pairing-code-panel'),
  pairingCode: document.querySelector('#pairing-code'),
  pairingCountdown: document.querySelector('#pairing-countdown'),
  storageList: document.querySelector('#storage-list'),
  deviceList: document.querySelector('#device-list'),
  deviceCount: document.querySelector('#device-count'),
  uploadList: document.querySelector('#upload-list'),
  uploadCount: document.querySelector('#upload-count'),
  serverAddress: document.querySelector('#server-address'),
};

async function request(path, options = {}) {
  const response = await fetch(path, {
    cache: 'no-store',
    headers: { Accept: 'application/json', ...(options.headers || {}) },
    ...options,
  });
  if (!response.ok) {
    const detail = await response.text().catch(() => '');
    throw new Error(detail || `Shove returned ${response.status}`);
  }
  if (response.status === 204) return null;
  return response.json();
}

async function refreshAll({ quiet = false } = {}) {
  if (!quiet) elements.refreshButton.disabled = true;
  try {
    const [overview, devices, uploads] = await Promise.all([
      request('/api/v1/admin/overview'),
      request('/api/v1/devices'),
      request('/api/v1/admin/uploads'),
    ]);
    state.overview = overview;
    renderOverview(overview);
    renderStorage(overview.destinations || []);
    renderDevices(devices || []);
    renderUploads(uploads || []);
    hideError();
  } catch (error) {
    showError(`The control panel could not refresh. ${messageOf(error)}`);
    setOverallStatus('problem', 'Needs attention');
  } finally {
    elements.refreshButton.disabled = false;
  }
}

function renderOverview(overview) {
  const availableStorage = overview.destinations?.some((destination) => destination.available);
  const ready = overview.expoReady && overview.serverUrl && availableStorage;
  setOverallStatus(ready ? 'ready' : 'waiting', ready ? 'Ready' : 'Almost ready');
  elements.heroTitle.textContent = ready ? 'Ready for your iPhone.' : 'One more thing before you connect.';
  elements.heroCopy.textContent = !overview.serverUrl
    ? 'Connect this PC to your home Wi-Fi so your iPhone can reach it.'
    : !overview.expoReady
      ? 'The private server is ready. Expo is still starting for the iPhone.'
      : !availableStorage
        ? 'Reconnect a configured storage destination to receive originals.'
        : 'Scan, pair, and send an original straight to storage on this PC.';
  elements.serverAddress.textContent = overview.serverUrl || 'No home Wi-Fi address detected';
  elements.copyExpoUrl.disabled = !overview.expoProjectUrl;

  if (overview.expoReady && overview.expoProjectUrl) {
    const expectedSource = `${location.origin}/api/v1/admin/expo-qr.svg`;
    if (!elements.qr.src.startsWith(expectedSource)) elements.qr.src = expectedSource;
    elements.qr.hidden = false;
    elements.qrPlaceholder.hidden = true;
    elements.qrWrap.classList.remove('loading');
  } else {
    elements.qr.hidden = true;
    elements.qrPlaceholder.hidden = false;
    elements.qrPlaceholder.textContent = overview.serverUrl ? 'Starting Expo…' : 'Connect home Wi-Fi';
    elements.qrWrap.classList.add('loading');
  }
}

function renderStorage(destinations) {
  elements.storageList.replaceChildren();
  if (!destinations.length) {
    elements.storageList.append(emptyMessage('No storage is configured.'));
    return;
  }
  for (const destination of destinations) {
    const item = element('div', `storage-item${destination.available ? '' : ' unavailable'}`);
    const identity = element('div', 'storage-identity');
    const icon = element('span', 'storage-icon');
    icon.innerHTML = '<svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z" stroke="currentColor" stroke-width="1.7"/><path d="M7 16h.01M17 16h.01M7 8h10" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>';
    const labels = element('span');
    labels.append(textElement('span', 'storage-name', destination.displayName));
    const details = destination.available
      ? `${destination.path} · ${formatBytes(destination.freeBytes)} free`
      : destination.path;
    labels.append(textElement('span', 'storage-path', details));
    identity.append(icon, labels);
    item.append(identity, textElement('span', 'availability', destination.available ? 'Available' : 'Disconnected'));
    elements.storageList.append(item);
  }
}

function renderDevices(devices) {
  const activeDevices = devices.filter((device) => !device.revokedAt);
  elements.deviceCount.textContent = String(activeDevices.length);
  elements.deviceList.replaceChildren();
  elements.deviceList.className = devices.length ? 'record-list' : 'record-list empty-state';
  if (!devices.length) {
    elements.deviceList.textContent = 'No phones paired yet.';
    return;
  }
  for (const device of devices.slice(0, 8)) {
    const record = element('div', 'record');
    const main = element('div', 'record-main');
    main.append(textElement('span', 'record-title', device.displayName));
    const activity = device.lastSeenAt ? `Last connected ${relativeTime(device.lastSeenAt)}` : `Paired ${relativeTime(device.pairedAt)}`;
    main.append(textElement('span', 'record-meta', activity));
    const side = element('div', 'record-side');
    if (device.revokedAt) {
      side.append(textElement('span', 'revoked', 'Unpaired'));
    } else {
      const button = textElement('button', 'button danger', 'Unpair');
      button.type = 'button';
      button.addEventListener('click', () => revokeDevice(device, button));
      side.append(button);
    }
    record.append(main, side);
    elements.deviceList.append(record);
  }
}

function renderUploads(uploads) {
  elements.uploadCount.textContent = String(uploads.length);
  elements.uploadList.replaceChildren();
  elements.uploadList.className = uploads.length ? 'record-list' : 'record-list empty-state';
  if (!uploads.length) {
    elements.uploadList.textContent = 'Your verified transfers will appear here.';
    return;
  }
  for (const upload of uploads.slice(0, 8)) {
    const record = element('div', 'record');
    const main = element('div', 'record-main');
    main.append(textElement('span', 'record-title', upload.originalFilename));
    main.append(textElement('span', 'record-meta', `${formatBytes(upload.bytes)} · ${relativeTime(upload.updatedAt)} · ${upload.destinationId}`));
    const statusClass = upload.verified ? 'verified' : upload.state === 'failed' ? 'failed' : 'revoked';
    const statusText = upload.verified ? 'Verified' : upload.state === 'failed' ? 'Failed' : 'Receiving';
    record.append(main, textElement('span', statusClass, statusText));
    elements.uploadList.append(record);
  }
}

async function createPairingCode() {
  elements.pairButton.disabled = true;
  try {
    const session = await request('/api/v1/pairing/sessions', { method: 'POST' });
    startPairingCountdown(session.code, session.expiresAt);
    hideError();
  } catch (error) {
    showError(`A pairing code could not be created. ${messageOf(error)}`);
  } finally {
    elements.pairButton.disabled = false;
  }
}

function startPairingCountdown(code, expiresAt) {
  clearInterval(state.pairingTimer);
  elements.pairingCode.textContent = `${code.slice(0, 3)} ${code.slice(3)}`;
  elements.pairingPanel.className = 'pairing-code-panel';
  elements.pairButton.textContent = 'Make a new code';
  const update = () => {
    const seconds = Math.max(0, Math.ceil((new Date(expiresAt).getTime() - Date.now()) / 1000));
    if (!seconds) {
      clearInterval(state.pairingTimer);
      elements.pairingCountdown.textContent = 'Expired — make a new code';
      elements.pairingPanel.classList.add('expired');
      return;
    }
    elements.pairingCountdown.textContent = `Expires in ${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
  };
  update();
  state.pairingTimer = setInterval(update, 250);
}

async function revokeDevice(device, button) {
  if (!confirm(`Unpair ${device.displayName}? It will need a new pairing code to upload again.`)) return;
  button.disabled = true;
  try {
    await request(`/api/v1/devices/${encodeURIComponent(device.deviceId)}`, { method: 'DELETE' });
    await refreshAll({ quiet: true });
  } catch (error) {
    showError(`The phone could not be unpaired. ${messageOf(error)}`);
    button.disabled = false;
  }
}

function setOverallStatus(kind, label) {
  elements.overallStatus.className = `status-pill ${kind}`;
  elements.overallStatus.lastChild.textContent = label;
}

function showError(message) {
  elements.errorBanner.textContent = message;
  elements.errorBanner.hidden = false;
}
function hideError() { elements.errorBanner.hidden = true; }
function messageOf(error) { return error instanceof Error ? error.message : String(error); }
function element(tag, className = '') {
  const node = document.createElement(tag);
  if (className) node.className = className;
  return node;
}
function textElement(tag, className, text) {
  const node = element(tag, className);
  node.textContent = text;
  return node;
}
function emptyMessage(text) { return textElement('div', 'empty-state', text); }
function formatBytes(value) {
  if (value == null || Number.isNaN(Number(value))) return 'Space unknown';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let amount = Number(value);
  let index = 0;
  while (amount >= 1000 && index < units.length - 1) { amount /= 1000; index += 1; }
  return `${amount >= 10 || index === 0 ? amount.toFixed(0) : amount.toFixed(1)} ${units[index]}`;
}
function relativeTime(value) {
  const seconds = Math.round((new Date(value).getTime() - Date.now()) / 1000);
  const formatter = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
  const ranges = [['year', 31536000], ['month', 2592000], ['day', 86400], ['hour', 3600], ['minute', 60]];
  for (const [unit, size] of ranges) {
    if (Math.abs(seconds) >= size) return formatter.format(Math.round(seconds / size), unit);
  }
  return formatter.format(seconds, 'second');
}

elements.refreshButton.addEventListener('click', () => refreshAll());
elements.pairButton.addEventListener('click', createPairingCode);
elements.copyExpoUrl.addEventListener('click', async () => {
  if (!state.overview?.expoProjectUrl) return;
  await navigator.clipboard.writeText(state.overview.expoProjectUrl);
  elements.copyExpoUrl.textContent = 'Copied';
  setTimeout(() => { elements.copyExpoUrl.textContent = 'Copy project address'; }, 1500);
});

refreshAll();
setInterval(() => refreshAll({ quiet: true }), 2500);
