const state = {
  overview: null,
  pairingTimer: null,
  deviceHistoryExpanded: false,
};

const DEVICE_ONLINE_WINDOW_MS = 25_000;

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
  deviceHistoryToggle: document.querySelector('#device-history-toggle'),
  deviceHistory: document.querySelector('#device-history'),
  deviceHistoryList: document.querySelector('#device-history-list'),
  uploadList: document.querySelector('#upload-list'),
  uploadCount: document.querySelector('#upload-count'),
  performanceSampleNote: document.querySelector('#performance-sample-note'),
  performanceEmpty: document.querySelector('#performance-empty'),
  performanceContent: document.querySelector('#performance-content'),
  performanceSamples: document.querySelector('#performance-samples'),
  performanceSuccess: document.querySelector('#performance-success'),
  performanceP50: document.querySelector('#performance-p50'),
  performanceP95: document.querySelector('#performance-p95'),
  destinationPerformance: document.querySelector('#destination-performance'),
  phasePerformance: document.querySelector('#phase-performance'),
  recentPerformance: document.querySelector('#recent-performance'),
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
    const [overview, devices, uploads, performance] = await Promise.all([
      request('/api/v1/admin/overview'),
      request('/api/v1/devices'),
      request('/api/v1/admin/uploads'),
      request('/api/v1/admin/performance'),
    ]);
    state.overview = overview;
    renderOverview(overview);
    renderStorage(overview.destinations || []);
    renderDevices(devices || []);
    renderUploads(uploads || []);
    renderPerformance(performance);
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
  elements.heroTitle.textContent = ready ? 'Ready for your phone.' : 'One more thing before you connect.';
  elements.heroCopy.textContent = !overview.serverUrl
    ? 'Connect this PC to your home Wi-Fi so your phone can reach it.'
    : !overview.expoReady
      ? 'The private server is ready. Expo is still starting for the phone.'
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
  const revokedDevices = devices.filter((device) => device.revokedAt);
  elements.deviceCount.textContent = String(activeDevices.length);
  elements.deviceList.replaceChildren();
  elements.deviceList.className = activeDevices.length ? 'record-list' : 'record-list empty-state';
  if (!activeDevices.length) {
    elements.deviceList.textContent = revokedDevices.length
      ? 'No active phones. Create a pairing code to reconnect one.'
      : 'No phones paired yet.';
  }
  for (const device of activeDevices.slice(0, 8)) {
    elements.deviceList.append(deviceRecord(device));
  }

  elements.deviceHistoryToggle.hidden = revokedDevices.length === 0;
  elements.deviceHistoryToggle.textContent = `Previously paired (${revokedDevices.length}) ${state.deviceHistoryExpanded ? '−' : '+'}`;
  elements.deviceHistory.hidden = revokedDevices.length === 0 || !state.deviceHistoryExpanded;
  elements.deviceHistoryList.replaceChildren();
  for (const device of revokedDevices.slice(0, 12)) {
    elements.deviceHistoryList.append(deviceRecord(device));
  }
}

function deviceRecord(device) {
  const record = element('div', 'record');
  const main = element('div', 'record-main');
  main.append(textElement('span', 'record-title', device.displayName));
  const storageReportedAt = Date.parse(device.storageReportedAt || '');
  const statusIsFresh = !device.revokedAt
    && Number.isFinite(storageReportedAt)
    && Date.now() - storageReportedAt <= DEVICE_ONLINE_WINDOW_MS;
  const platform = platformLabel(device.platform);
  const activity = device.revokedAt
    ? `${platform} · Unpaired ${relativeTime(device.revokedAt)}`
    : statusIsFresh
      ? `${platform} · Shove is open now`
      : device.lastSeenAt
        ? `${platform} · Last connected ${relativeTime(device.lastSeenAt)}`
        : `${platform} · Paired ${relativeTime(device.pairedAt)}`;
  main.append(textElement('span', 'record-meta', activity));
  if (!device.revokedAt && validStorage(device.storageAvailableBytes, device.storageTotalBytes)) {
    main.append(textElement(
      'span',
      'device-storage',
      `${formatBytes(device.storageAvailableBytes)} free of ${formatBytes(device.storageTotalBytes)} · updated ${relativeTime(device.storageReportedAt)}`,
    ));
  } else if (!device.revokedAt) {
    main.append(textElement('span', 'device-storage unavailable', 'Storage appears while Shove is open on this phone.'));
  }
  const side = element('div', 'record-side');
  if (device.revokedAt) {
    side.append(textElement('span', 'revoked', 'Unpaired'));
  } else {
    side.append(textElement('span', statusIsFresh ? 'device-state online' : 'device-state idle', statusIsFresh ? 'Connected' : 'Idle'));
    const button = textElement('button', 'button danger', 'Unpair');
    button.type = 'button';
    button.addEventListener('click', () => revokeDevice(device, button));
    side.append(button);
  }
  record.append(main, side);
  return record;
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
    const performance = performanceSummary(upload);
    if (performance) main.append(textElement('span', 'performance-meta', performance));
    const statusClass = upload.verified ? 'verified' : upload.state === 'failed' ? 'failed' : 'processing';
    const statusText = uploadStatus(upload.state);
    record.append(main, textElement('span', statusClass, statusText));
    elements.uploadList.append(record);
  }
}

function renderPerformance(performance) {
  const hasSamples = performance && performance.sampleCount > 0;
  elements.performanceEmpty.hidden = hasSamples;
  elements.performanceContent.hidden = !hasSamples;
  elements.performanceSampleNote.innerHTML = '<i></i>' + (hasSamples
    ? `${performance.sampleCount} measured ${performance.sampleCount === 1 ? 'transfer' : 'transfers'}`
    : 'Waiting for a measured transfer');
  if (!hasSamples) return;

  elements.performanceSamples.textContent = String(performance.sampleCount);
  elements.performanceSuccess.textContent = formatPercent(performance.successRate);
  elements.performanceP50.textContent = formatOptionalDuration(performance.p50TotalMs);
  elements.performanceP95.textContent = formatOptionalDuration(performance.p95TotalMs);

  elements.destinationPerformance.replaceChildren();
  for (const destination of performance.destinations || []) {
    const row = element('div', 'performance-row');
    const heading = element('div', 'performance-row-heading');
    heading.append(
      textElement('strong', '', destinationName(destination.destinationId)),
      textElement('span', '', `${destination.sampleCount} sample${destination.sampleCount === 1 ? '' : 's'} · ${formatPercent(destination.successRate)} verified`),
    );
    const details = element('div', 'performance-row-details');
    details.append(performanceDatum('Typical', formatOptionalDuration(destination.p50TotalMs)));
    details.append(performanceDatum('Slow-end', formatOptionalDuration(destination.p95TotalMs)));
    details.append(performanceDatum('Receive', formatRate(destination.receiveBytesPerSecond)));
    if (destination.driveSaveBytesPerSecond != null) {
      details.append(performanceDatum('Drive save', formatRate(destination.driveSaveBytesPerSecond)));
    }
    row.append(heading, details);
    elements.destinationPerformance.append(row);
  }

  elements.phasePerformance.replaceChildren();
  const phases = (performance.phases || []).filter((phase) => phase.sampleCount > 0);
  const maximumPhase = Math.max(1, ...phases.map((phase) => Number(phase.p95Ms || 0)));
  for (const phase of phases) {
    const row = element('div', 'phase-row');
    const heading = element('div', 'phase-row-heading');
    heading.append(
      textElement('strong', '', phaseName(phase.phase)),
      textElement('span', '', `${formatOptionalDuration(phase.p50Ms)} typical · ${formatOptionalDuration(phase.p95Ms)} slow-end`),
    );
    const track = element('div', 'phase-track');
    const bar = element('span', `phase-bar ${phaseClass(phase.phase)}`);
    bar.style.width = `${Math.max(2, Number(phase.p95Ms || 0) / maximumPhase * 100)}%`;
    track.append(bar);
    row.append(heading, track);
    elements.phasePerformance.append(row);
  }

  elements.recentPerformance.replaceChildren();
  for (const upload of performance.recent || []) {
    elements.recentPerformance.append(renderWaterfall(upload));
  }
}

function performanceDatum(label, value) {
  const datum = element('span', 'performance-datum');
  datum.append(textElement('small', '', label), textElement('strong', '', value));
  return datum;
}

function renderWaterfall(upload) {
  const timings = upload.timings || {};
  const total = Math.max(1, Number(timings.totalMs || 0));
  const values = [
    ['receive', Number(timings.receiveHashMs || 0)],
    ['drive', Number(timings.externalCopyMs || 0)],
    ['promote', Number(timings.promoteMs || 0)],
    ['audit', Number(timings.auditMs || 0)],
  ];
  const measured = values.reduce((sum, [, value]) => sum + value, 0);
  values.push(['other', Math.max(0, total - measured)]);

  const row = element('div', 'waterfall-row');
  const labels = element('div', 'waterfall-labels');
  labels.append(
    textElement('strong', '', upload.originalFilename),
    textElement('span', '', `${destinationName(upload.destinationId)} · ${formatBytes(upload.bytes)} · ${formatDuration(total)}`),
  );
  const track = element('div', 'waterfall-track');
  for (const [kind, value] of values) {
    if (value <= 0) continue;
    const segment = element('span', `waterfall-segment ${kind}`);
    segment.style.width = `${Math.max(0.7, value / total * 100)}%`;
    segment.title = `${phaseName(kind)}: ${formatDuration(value)}`;
    track.append(segment);
  }
  row.append(labels, track);
  return row;
}

function destinationName(destinationId) {
  return state.overview?.destinations?.find((destination) => destination.id === destinationId)?.displayName
    || (destinationId === 'local' ? 'Windows storage' : destinationId);
}

function phaseName(phase) {
  return ({
    receive_hash_force: 'Receive + hash',
    external_copy_force: 'Drive save',
    atomic_promote: 'Promote',
    audit_commit: 'Audit',
    receive: 'Receive + hash',
    drive: 'Drive save',
    promote: 'Promote',
    audit: 'Audit',
    other: 'Other',
  })[phase] || phase.replaceAll('_', ' ');
}

function phaseClass(phase) {
  return ({
    receive_hash_force: 'receive',
    external_copy_force: 'drive',
    atomic_promote: 'promote',
    audit_commit: 'audit',
  })[phase] || 'other';
}

function formatRate(bytesPerSecond) {
  return bytesPerSecond == null ? 'Not enough data' : `${formatBytes(bytesPerSecond)}/s`;
}

function formatPercent(value) {
  return `${Math.round(Number(value || 0) * 100)}%`;
}

function formatOptionalDuration(value) {
  return value == null ? 'Not enough data' : formatDuration(value);
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
function validStorage(available, total) {
  return Number.isFinite(Number(available))
    && Number.isFinite(Number(total))
    && Number(total) > 0
    && Number(available) >= 0
    && Number(available) < Number(total);
}
function platformLabel(value) {
  if (value === 'ios') return 'iPhone';
  if (value === 'android') return 'Android';
  return 'Phone';
}
function uploadStatus(state) {
  if (state === 'verified') return 'Verified';
  if (state === 'failed') return 'Failed';
  if (state === 'copying') return 'Saving to drive';
  if (state === 'promoting') return 'Finalizing';
  return 'Receiving';
}
function performanceSummary(upload) {
  const timings = upload.timings;
  if (!timings || timings.totalMs == null) return '';
  const phases = [`Server ${formatDuration(timings.totalMs)}`];
  if (timings.receiveHashMs != null) {
    const rate = timings.receiveHashMs > 0 && upload.bytes > 0
      ? ` · ${formatBytes(upload.bytes / (timings.receiveHashMs / 1000))}/s`
      : '';
    phases.push(`receive + hash ${formatDuration(timings.receiveHashMs)}${rate}`);
  }
  if (timings.externalCopyMs > 0) phases.push(`drive save ${formatDuration(timings.externalCopyMs)}`);
  if (timings.promoteMs != null) phases.push(`promote ${formatDuration(timings.promoteMs)}`);
  if (timings.auditMs != null) phases.push(`audit ${formatDuration(timings.auditMs)}`);
  if (timings.failurePhase) phases.push(`failed in ${timings.failurePhase.replaceAll('_', ' ')}`);
  return phases.join(' · ');
}
function formatDuration(value) {
  const milliseconds = Number(value);
  if (milliseconds < 1000) return `${milliseconds} ms`;
  return `${(milliseconds / 1000).toFixed(milliseconds < 10_000 ? 2 : 1)} s`;
}
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
elements.deviceHistoryToggle.addEventListener('click', () => {
  state.deviceHistoryExpanded = !state.deviceHistoryExpanded;
  void refreshAll({ quiet: true });
});
elements.copyExpoUrl.addEventListener('click', async () => {
  if (!state.overview?.expoProjectUrl) return;
  await navigator.clipboard.writeText(state.overview.expoProjectUrl);
  elements.copyExpoUrl.textContent = 'Copied';
  setTimeout(() => { elements.copyExpoUrl.textContent = 'Copy project address'; }, 1500);
});

refreshAll();
setInterval(() => refreshAll({ quiet: true }), 2500);
