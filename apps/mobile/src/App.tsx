import { useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  AppState,
  Platform,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import * as Device from 'expo-device';
import * as FileSystem from 'expo-file-system/legacy';
import * as ImagePicker from 'expo-image-picker';
import * as SecureStore from 'expo-secure-store';

import { isUnauthorized, normalizeBaseUrl, ServerClient, ServerInfo, StorageDestination } from './server/ServerClient';
import { TransferTaskSnapshot } from './transfers/TransferEngine';
import { createTransferEngine } from './transfers/TransferEngineProvider';

const TOKEN_KEY = 'shove.deviceToken';
const SERVER_KEY = 'shove.serverUrl';
const DESTINATION_POLL_MS = 2_000;
const DEVICE_STATUS_INTERVAL_MS = 10_000;
const DEFAULT_SERVER_URL = process.env.EXPO_PUBLIC_SHOVE_SERVER_URL?.trim() || 'http://192.168.1.8:8787';
const DEVICE_NAME = Device.modelName?.trim() || 'Mobile device';

type ConnectionState =
  | { kind: 'idle' }
  | { kind: 'checking' }
  | { kind: 'connected'; server: ServerInfo }
  | { kind: 'error'; message: string };

type PhoneStorage = {
  availableBytes: number;
  totalBytes: number;
};

type TransferQueueItem = {
  uploadId: string;
  fileName: string;
  destination: StorageDestination;
  snapshot: TransferTaskSnapshot;
};

export default function App() {
  const serverClient = useMemo(() => new ServerClient(), []);
  const transferEngine = useMemo(() => createTransferEngine(), []);
  const [address, setAddress] = useState(DEFAULT_SERVER_URL);
  const [connection, setConnection] = useState<ConnectionState>({ kind: 'idle' });
  const [pairingCode, setPairingCode] = useState('');
  const [token, setToken] = useState<string | null>(null);
  const [pairing, setPairing] = useState(false);
  const [unpairing, setUnpairing] = useState(false);
  const [destinations, setDestinations] = useState<StorageDestination[]>([]);
  const [selectedDestinationId, setSelectedDestinationId] = useState<string | null>(null);
  const [destinationMenuOpen, setDestinationMenuOpen] = useState(false);
  const [destinationError, setDestinationError] = useState<string | null>(null);
  const [transferQueue, setTransferQueue] = useState<TransferQueueItem[]>([]);
  const [batchRunning, setBatchRunning] = useState(false);
  const [transferError, setTransferError] = useState<string | null>(null);
  const [phoneStorage, setPhoneStorage] = useState<PhoneStorage | null>(null);

  useEffect(() => {
    void Promise.all([SecureStore.getItemAsync(TOKEN_KEY), SecureStore.getItemAsync(SERVER_KEY)]).then(
      ([savedToken, savedServer]) => {
        if (savedToken) setToken(savedToken);
        if (savedServer) setAddress(savedServer);
      },
    );
    const subscription = transferEngine.subscribe((snapshot) => {
      setTransferQueue((current) => current.map((item) => (
        item.uploadId === snapshot.uploadId ? { ...item, snapshot } : item
      )));
    });
    return () => subscription.remove();
  }, [transferEngine]);

  useEffect(() => {
    let active = true;
    let requestInFlight = false;

    async function refreshPhoneStorage() {
      if (requestInFlight || AppState.currentState === 'background' || AppState.currentState === 'inactive') return;
      requestInFlight = true;
      try {
        const storage = await readPhoneStorage();
        if (!active) return;
        setPhoneStorage(storage);
        if (token && storage) {
          await serverClient.updateDeviceStatus(address, token, {
            deviceName: DEVICE_NAME,
            platform: Platform.OS,
            availableBytes: storage.availableBytes,
            totalBytes: storage.totalBytes,
          });
        }
      } catch {
        // Device telemetry is best-effort UI data, never a transfer prerequisite.
      } finally {
        requestInFlight = false;
      }
    }

    void refreshPhoneStorage();
    const interval = token
      ? setInterval(() => void refreshPhoneStorage(), DEVICE_STATUS_INTERVAL_MS)
      : null;
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') void refreshPhoneStorage();
    });
    return () => {
      active = false;
      if (interval) clearInterval(interval);
      subscription.remove();
    };
  }, [address, serverClient, token]);

  useEffect(() => {
    if (!token) {
      setDestinations([]);
      setSelectedDestinationId(null);
      setDestinationMenuOpen(false);
      setDestinationError(null);
      return;
    }

    let active = true;
    let requestInFlight = false;
    const activeToken = token;

    async function refreshDestinations() {
      if (requestInFlight) return;
      requestInFlight = true;
      try {
        const [server, nextDestinations] = await Promise.all([
          connection.kind === 'connected' ? Promise.resolve(null) : serverClient.getServerInfo(address),
          serverClient.getDestinations(address, activeToken),
        ]);
        if (!active) return;
        if (server) setConnection({ kind: 'connected', server });
        const available = nextDestinations.filter((destination) => destination.available);
        setDestinations(nextDestinations);
        setSelectedDestinationId((current) => {
          if (current && available.some((destination) => destination.id === current)) return current;
          return available.find((destination) => destination.defaultDestination)?.id ?? available[0]?.id ?? null;
        });
        setDestinationError(null);
      } catch (error) {
        if (!active) return;
        if (isUnauthorized(error)) {
          await SecureStore.deleteItemAsync(TOKEN_KEY);
          if (!active) return;
          setToken((current) => (current === activeToken ? null : current));
          setDestinationError('The laptop was reset or no longer recognizes this phone. Pair it again.');
          setTransferQueue([]);
        } else {
          setDestinationError(messageOf(error));
        }
      } finally {
        requestInFlight = false;
      }
    }

    void refreshDestinations();
    const interval = setInterval(() => void refreshDestinations(), DESTINATION_POLL_MS);
    return () => {
      active = false;
      clearInterval(interval);
    };
  }, [address, connection.kind, serverClient, token]);

  async function checkServer() {
    setConnection({ kind: 'checking' });
    try {
      const server = await serverClient.getServerInfo(address);
      await transferEngine.list();
      setConnection({ kind: 'connected', server });
    } catch (error) {
      setConnection({ kind: 'error', message: messageOf(error) });
    }
  }

  async function pair() {
    setPairing(true);
    setTransferError(null);
    try {
      const normalizedAddress = normalizeBaseUrl(address);
      const device = await serverClient.pair(normalizedAddress, pairingCode, DEVICE_NAME);
      await SecureStore.setItemAsync(TOKEN_KEY, device.token);
      await SecureStore.setItemAsync(SERVER_KEY, normalizedAddress);
      setAddress(normalizedAddress);
      setToken(device.token);
    } catch (error) {
      setTransferError(messageOf(error));
    } finally {
      setPairing(false);
    }
  }

  async function unpair() {
    if (!token) return;
    setUnpairing(true);
    setTransferError(null);
    try {
      try {
        await serverClient.unpair(address, token);
      } catch (error) {
        if (!isUnauthorized(error)) throw error;
      }
      await SecureStore.deleteItemAsync(TOKEN_KEY);
      setToken(null);
      setPairingCode('');
      setTransferQueue([]);
      setDestinations([]);
      setSelectedDestinationId(null);
      setConnection({ kind: 'idle' });
    } catch (error) {
      setTransferError(messageOf(error));
    } finally {
      setUnpairing(false);
    }
  }

  async function chooseAndUpload() {
    if (!token || !selectedDestinationId || batchRunning) return;
    setTransferError(null);
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      setTransferError('Photo-library permission is required to choose an original.');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      allowsEditing: false,
      allowsMultipleSelection: true,
      mediaTypes: ['images', 'videos'],
      orderedSelection: true,
      preferredAssetRepresentationMode: ImagePicker.UIImagePickerPreferredAssetRepresentationMode.Current,
      quality: 1,
      selectionLimit: 0,
    });
    if (result.canceled) return;

    const uploadDestination = destinations.find((destination) => destination.id === selectedDestinationId) ?? null;
    if (!uploadDestination || !result.assets.length) return;

    const batchId = Date.now();
    const prepared = result.assets.map((asset, index) => {
      const uploadId = `${batchId}-${index}-${Math.random().toString(16).slice(2)}`;
      const fileName = asset.fileName ?? `${uploadId}.bin`;
      return {
        asset,
        fileName,
        uploadId,
        queueItem: {
          uploadId,
          fileName,
          destination: uploadDestination,
          snapshot: queuedSnapshot(uploadId, asset.fileSize ?? 0),
        } satisfies TransferQueueItem,
      };
    });

    setTransferQueue(prepared.map((item) => item.queueItem));
    setBatchRunning(true);

    try {
      for (const [index, item] of prepared.entries()) {
        const liveDestinations = await serverClient.getDestinations(address, token);
        const destinationStillAvailable = liveDestinations.some(
          (destination) => destination.id === selectedDestinationId && destination.available,
        );
        if (!destinationStillAvailable) {
          const stoppedAt = new Date().toISOString();
          const remainingIds = new Set(prepared.slice(index).map((remaining) => remaining.uploadId));
          setTransferQueue((current) => current.map((queued) => (
            remainingIds.has(queued.uploadId)
              ? {
                  ...queued,
                  snapshot: {
                    ...queued.snapshot,
                    state: 'failed',
                    errorCode: 'destination-unavailable',
                    updatedAt: stoppedAt,
                  },
                }
              : queued
          )));
          setTransferError(
            `${uploadDestination.displayName} disconnected. ${prepared.length - index} remaining ${prepared.length - index === 1 ? 'item was' : 'items were'} not sent.`,
          );
          break;
        }

        try {
          await transferEngine.enqueue({
            uploadId: item.uploadId,
            sourceAssetId: item.asset.assetId ?? item.uploadId,
            stagedFileUri: item.asset.uri,
            destinationUrl: `${normalizeBaseUrl(address)}/api/v1/upload`,
            byteSize: item.asset.fileSize ?? 0,
            headers: {
              Authorization: `Bearer ${token}`,
              'Content-Type': item.asset.mimeType ?? 'application/octet-stream',
              'X-Shove-Destination': selectedDestinationId,
              'X-Shove-Filename': item.fileName,
            },
          });
        } catch {
          // The engine emitted a failed snapshot. Continue with the next original.
        }
      }
    } catch (error) {
      const failedAt = new Date().toISOString();
      setTransferQueue((current) => current.map((item) => (
        item.snapshot.state === 'queued'
          ? {
              ...item,
              snapshot: {
                ...item.snapshot,
                state: 'failed',
                errorCode: 'batch-interrupted',
                updatedAt: failedAt,
              },
            }
          : item
      )));
      setTransferError(messageOf(error));
    } finally {
      setBatchRunning(false);
    }
  }

  const verifiedCount = transferQueue.filter((item) => item.snapshot.state === 'completed').length;
  const failedCount = transferQueue.filter((item) => item.snapshot.state === 'failed').length;
  const availableDestinations = destinations.filter((destination) => destination.available);
  const selectedDestination = availableDestinations.find(
    (destination) => destination.id === selectedDestinationId,
  );
  const unavailableDestinations = destinations.filter((destination) => !destination.available);
  const destinationPlaceholder = !token
    ? 'Pair to choose storage'
    : destinationError
      ? 'Storage unavailable'
      : 'Checking storage…';

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="light-content" />
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Text style={styles.eyebrow}>SHOVE-IT PROTOTYPE</Text>
        <Text style={styles.title}>Shove your originals home.</Text>
        <Text style={styles.subtitle}>Choose one or many. Phone → local Wi-Fi → Windows storage.</Text>

        <View style={styles.phoneStorageStrip}>
          <View>
            <Text style={styles.phoneStorageLabel}>THIS PHONE</Text>
            <Text style={styles.phoneStorageValue}>
              {phoneStorage ? `${formatBytes(phoneStorage.availableBytes)} free` : 'Storage estimate unavailable'}
            </Text>
          </View>
          {phoneStorage && (
            <Text style={styles.phoneStorageTotal}>{formatBytes(phoneStorage.totalBytes)} total</Text>
          )}
        </View>

        <View style={styles.card}>
          <Text style={styles.step}>1 · CONNECT</Text>
          <TextInput
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
            onChangeText={setAddress}
            placeholder="http://192.168.1.8:8787"
            placeholderTextColor="#667085"
            style={styles.input}
            value={address}
          />
          <ActionButton disabled={connection.kind === 'checking'} onPress={checkServer} text="Find my server" />
          {connection.kind === 'connected' && (
            <Text style={styles.success}>● Connected to {connection.server.name}</Text>
          )}
          {connection.kind === 'error' && <Text style={styles.error}>{connection.message}</Text>}
        </View>

        <View style={styles.card}>
          <Text style={styles.step}>2 · PAIR</Text>
          {token ? (
            <>
              <Text style={styles.success}>● This phone is paired</Text>
              <ActionButton
                disabled={unpairing || batchRunning}
                loading={unpairing}
                onPress={unpair}
                text="Unpair this phone"
              />
            </>
          ) : (
            <>
              <TextInput
                keyboardType="number-pad"
                maxLength={6}
                onChangeText={setPairingCode}
                placeholder="6-digit code"
                placeholderTextColor="#667085"
                style={styles.input}
                value={pairingCode}
              />
              <ActionButton
                disabled={pairing || pairingCode.length !== 6}
                loading={pairing}
                onPress={pair}
                text="Pair phone"
              />
            </>
          )}
        </View>

        <View style={styles.card}>
          <Text style={styles.step}>3 · SHOVE</Text>
          <View style={styles.destinationHeader}>
            <Text style={styles.fieldLabel}>DESTINATION</Text>
            <Text style={styles.liveLabel}>● LIVE · 2s</Text>
          </View>
          <TouchableOpacity
            disabled={!token || availableDestinations.length === 0 || batchRunning}
            onPress={() => setDestinationMenuOpen((open) => !open)}
            style={[
              styles.destinationSelector,
              (!token || availableDestinations.length === 0 || batchRunning) && styles.buttonDisabled,
            ]}
          >
            <View style={styles.destinationTextBlock}>
              <Text style={styles.destinationName}>
                {selectedDestination?.displayName ?? destinationPlaceholder}
              </Text>
              {selectedDestination && (
                <>
                  <Text style={styles.destinationSpeed}>{destinationSpeed(selectedDestination)}</Text>
                  <Text style={styles.destinationDetail} numberOfLines={1}>
                    {destinationSummary(selectedDestination)}
                  </Text>
                </>
              )}
            </View>
            <Text style={styles.chevron}>{destinationMenuOpen ? '⌃' : '⌄'}</Text>
          </TouchableOpacity>
          {destinationMenuOpen && (
            <View style={styles.destinationMenu}>
              {availableDestinations.map((destination) => (
                <TouchableOpacity
                  key={destination.id}
                  onPress={() => {
                    setSelectedDestinationId(destination.id);
                    setDestinationMenuOpen(false);
                  }}
                  style={[
                    styles.destinationOption,
                    destination.id === selectedDestinationId && styles.destinationOptionSelected,
                  ]}
                >
                  <Text style={styles.destinationName}>{destination.displayName}</Text>
                  <Text style={styles.destinationSpeed}>{destinationSpeed(destination)}</Text>
                  <Text style={styles.destinationDetail} numberOfLines={1}>
                    {destinationSummary(destination)}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          )}
          {unavailableDestinations.length > 0 && (
            <Text style={styles.destinationUnavailable}>
              {unavailableDestinations.map((destination) => destination.displayName).join(', ')} disconnected
            </Text>
          )}
          {destinationError && <Text style={styles.error}>{destinationError}</Text>}
          <ActionButton
            disabled={!token || !selectedDestination || batchRunning}
            loading={batchRunning}
            onPress={chooseAndUpload}
            text="Choose photos or videos"
          />
          {transferQueue.length > 0 && (
            <View style={styles.queueBlock}>
              <View style={styles.queueHeader}>
                <Text style={styles.queueTitle}>
                  {batchRunning ? 'Sending batch' : failedCount ? 'Batch finished with issues' : 'Batch verified'}
                </Text>
                <Text style={styles.queueSummary}>
                  {verifiedCount} verified{failedCount ? ` · ${failedCount} failed` : ''} · {transferQueue.length} total
                </Text>
              </View>
              {transferQueue.map((item, index) => {
                const progress = transferProgress(item.snapshot);
                return (
                  <View key={item.uploadId} style={styles.queueItem}>
                    <View style={styles.queueItemHeader}>
                      <Text numberOfLines={1} style={styles.queueFileName}>{index + 1}. {item.fileName}</Text>
                      <Text style={[
                        styles.queueState,
                        item.snapshot.state === 'completed' && styles.queueStateVerified,
                        item.snapshot.state === 'failed' && styles.queueStateFailed,
                        item.snapshot.state === 'running' && styles.queueStateRunning,
                      ]}>{queueStateLabel(item.snapshot.state)}</Text>
                    </View>
                    <Text style={styles.transferState}>
                      {transferStatus(item.snapshot, progress, item.destination)}
                    </Text>
                    <Text style={styles.transferBytes}>
                      {formatBytes(item.snapshot.bytesSent)} / {formatBytes(item.snapshot.bytesExpected)}
                    </Text>
                    <View style={styles.progressTrack}>
                      <View style={[styles.progressFill, { width: `${progress}%` }]} />
                    </View>
                  </View>
                );
              })}
            </View>
          )}
          {transferError && <Text style={styles.error}>{transferError}</Text>}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function ActionButton({
  disabled,
  loading = false,
  onPress,
  text,
}: {
  disabled: boolean;
  loading?: boolean;
  onPress: () => void;
  text: string;
}) {
  return (
    <TouchableOpacity disabled={disabled} onPress={onPress} style={[styles.button, disabled && styles.buttonDisabled]}>
      {loading ? <ActivityIndicator color="#08130f" /> : <Text style={styles.buttonText}>{text}</Text>}
    </TouchableOpacity>
  );
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong.';
}

function formatBytes(value: number): string {
  if (!value) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  return `${(value / 1024 ** index).toFixed(index > 1 ? 1 : 0)} ${units[index]}`;
}

function queuedSnapshot(uploadId: string, bytesExpected: number): TransferTaskSnapshot {
  return {
    uploadId,
    nativeTaskId: `queued:${uploadId}`,
    state: 'queued',
    bytesSent: 0,
    bytesExpected,
    updatedAt: new Date().toISOString(),
  };
}

function transferProgress(transfer: TransferTaskSnapshot): number {
  if (transfer.state === 'completed') return 100;
  if (transfer.bytesExpected <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((transfer.bytesSent / transfer.bytesExpected) * 100)));
}

function queueStateLabel(state: TransferTaskSnapshot['state']): string {
  switch (state) {
    case 'queued': return 'Queued';
    case 'running': return 'Running';
    case 'verifying': return 'Verifying';
    case 'completed': return 'Verified';
    case 'failed': return 'Failed';
    case 'cancelled': return 'Cancelled';
    case 'waiting-for-network': return 'Waiting';
  }
}

async function readPhoneStorage(): Promise<PhoneStorage | null> {
  try {
    // Expo SDK 54's new iOS totalDiskSpace getter returns free space. The legacy
    // async calls use volumeTotalCapacity and available-capacity APIs correctly.
    const [availableBytes, totalBytes] = await Promise.all([
      FileSystem.getFreeDiskStorageAsync(),
      FileSystem.getTotalDiskCapacityAsync(),
    ]);
    const valid = Number.isFinite(totalBytes)
      && Number.isFinite(availableBytes)
      && totalBytes > 0
      && availableBytes >= 0
      && availableBytes < totalBytes;
    return valid ? { availableBytes, totalBytes } : null;
  } catch {
    return null;
  }
}

function destinationSummary(destination: StorageDestination): string {
  return destination.freeBytes === null
    ? destination.path
    : `${destination.path} · ${formatBytes(destination.freeBytes)} free`;
}

function destinationSpeed(destination: StorageDestination): string {
  return destination.id === 'local'
    ? 'Fastest · completes directly on this Windows PC'
    : 'Extra save step · Windows copies it here after upload';
}

function transferStatus(
  transfer: TransferTaskSnapshot,
  progress: number,
  destination: StorageDestination | null,
): string {
  if (transfer.state === 'completed') return '✓ Verified on Windows';
  if (transfer.state === 'queued') return 'Waiting for the previous item';
  if (transfer.state === 'failed') {
    if (transfer.errorCode === 'destination-unavailable') return 'Not sent · destination disconnected';
    if (transfer.errorCode === 'batch-interrupted') return 'Not sent · batch stopped';
    return 'Upload failed · continuing with the next item';
  }
  if (transfer.state === 'cancelled') return 'Cancelled';
  if (transfer.state === 'waiting-for-network') return 'Waiting for Wi-Fi';
  if (transfer.state === 'verifying') return 'Upload complete · Verifying on Windows…';
  if (transfer.state === 'running' && progress >= 100) {
    return destination?.id === 'local'
      ? 'Upload complete · Verifying on Windows…'
      : `Upload complete · Saving to ${destination?.displayName ?? 'external storage'}…`;
  }
  return `Uploading · ${progress}%`;
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: '#07110e' },
  container: { paddingBottom: 40, paddingHorizontal: 22, paddingTop: 52 },
  eyebrow: { color: '#65e6ad', fontSize: 12, fontWeight: '700', letterSpacing: 1.8 },
  title: { color: '#f4fff9', fontSize: 39, fontWeight: '700', lineHeight: 44, marginTop: 10 },
  subtitle: { color: '#9bb0a7', fontSize: 16, lineHeight: 23, marginBottom: 18, marginTop: 12 },
  phoneStorageStrip: { alignItems: 'center', backgroundColor: '#0c1b16', borderColor: '#1d3b30', borderRadius: 14, borderWidth: 1, flexDirection: 'row', justifyContent: 'space-between', marginBottom: 3, paddingHorizontal: 15, paddingVertical: 12 },
  phoneStorageLabel: { color: '#789187', fontSize: 10, fontWeight: '700', letterSpacing: 1.1 },
  phoneStorageValue: { color: '#f4fff9', fontSize: 17, fontWeight: '700', marginTop: 3 },
  phoneStorageTotal: { color: '#9bb0a7', fontSize: 12 },
  card: { backgroundColor: '#10221b', borderColor: '#1d3b30', borderRadius: 18, borderWidth: 1, marginTop: 14, padding: 17 },
  step: { color: '#a9bdb4', fontSize: 12, fontWeight: '700', letterSpacing: 1.2, marginBottom: 11 },
  input: { backgroundColor: '#091713', borderColor: '#2b4d40', borderRadius: 11, borderWidth: 1, color: '#f4fff9', fontSize: 16, paddingHorizontal: 13, paddingVertical: 12 },
  destinationHeader: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between', marginBottom: 8 },
  fieldLabel: { color: '#a9bdb4', fontSize: 11, fontWeight: '700', letterSpacing: 1.1 },
  liveLabel: { color: '#65e6ad', fontSize: 10, fontWeight: '700', letterSpacing: 0.7 },
  destinationSelector: { alignItems: 'center', backgroundColor: '#091713', borderColor: '#2b4d40', borderRadius: 11, borderWidth: 1, flexDirection: 'row', justifyContent: 'space-between', minHeight: 60, paddingHorizontal: 13, paddingVertical: 10 },
  destinationTextBlock: { flex: 1, paddingRight: 8 },
  destinationName: { color: '#f4fff9', fontSize: 15, fontWeight: '700' },
  destinationSpeed: { color: '#65e6ad', fontSize: 11, fontWeight: '600', marginTop: 3 },
  destinationDetail: { color: '#789187', fontSize: 11, marginTop: 3 },
  chevron: { color: '#65e6ad', fontSize: 20 },
  destinationMenu: { backgroundColor: '#091713', borderColor: '#2b4d40', borderRadius: 11, borderWidth: 1, marginTop: 6, overflow: 'hidden' },
  destinationOption: { borderBottomColor: '#1d3b30', borderBottomWidth: 1, paddingHorizontal: 13, paddingVertical: 12 },
  destinationOptionSelected: { backgroundColor: '#18382c' },
  destinationUnavailable: { color: '#ffbf7a', fontSize: 12, marginTop: 8 },
  button: { alignItems: 'center', backgroundColor: '#65e6ad', borderRadius: 11, justifyContent: 'center', marginTop: 10, minHeight: 47 },
  buttonDisabled: { opacity: 0.42 },
  buttonText: { color: '#08130f', fontSize: 15, fontWeight: '700' },
  success: { color: '#65e6ad', fontSize: 13, marginTop: 12 },
  error: { color: '#ff9a9a', fontSize: 13, lineHeight: 18, marginTop: 12 },
  queueBlock: { borderTopColor: '#234336', borderTopWidth: 1, marginTop: 15, paddingTop: 13 },
  queueHeader: { marginBottom: 4 },
  queueTitle: { color: '#f4fff9', fontSize: 16, fontWeight: '700' },
  queueSummary: { color: '#9bb0a7', fontSize: 12, marginTop: 4 },
  queueItem: { borderTopColor: '#1d3b30', borderTopWidth: 1, marginTop: 12, paddingTop: 12 },
  queueItemHeader: { alignItems: 'center', flexDirection: 'row', gap: 10, justifyContent: 'space-between' },
  queueFileName: { color: '#f4fff9', flex: 1, fontSize: 13, fontWeight: '700' },
  queueState: { color: '#9bb0a7', fontSize: 11, fontWeight: '700' },
  queueStateVerified: { color: '#65e6ad' },
  queueStateFailed: { color: '#ff9a9a' },
  queueStateRunning: { color: '#f8c86b' },
  transferState: { color: '#e5fff2', fontSize: 15, fontWeight: '600' },
  transferBytes: { color: '#789187', fontSize: 12, marginTop: 5 },
  progressTrack: { backgroundColor: '#091713', borderRadius: 999, height: 5, marginTop: 8, overflow: 'hidden' },
  progressFill: { backgroundColor: '#65e6ad', borderRadius: 999, height: '100%' },
});
