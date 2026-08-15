import { useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  AppState,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { Paths } from 'expo-file-system';
import * as ImagePicker from 'expo-image-picker';
import * as SecureStore from 'expo-secure-store';

import { isUnauthorized, normalizeBaseUrl, ServerClient, ServerInfo, StorageDestination } from './server/ServerClient';
import { TransferTaskSnapshot } from './transfers/TransferEngine';
import { createTransferEngine } from './transfers/TransferEngineProvider';

const TOKEN_KEY = 'shove.deviceToken';
const SERVER_KEY = 'shove.serverUrl';
const DESTINATION_POLL_MS = 2_000;
const DEFAULT_SERVER_URL = process.env.EXPO_PUBLIC_SHOVE_SERVER_URL?.trim() || 'http://192.168.1.8:8787';

type ConnectionState =
  | { kind: 'idle' }
  | { kind: 'checking' }
  | { kind: 'connected'; server: ServerInfo }
  | { kind: 'error'; message: string };

type PhoneStorage = {
  availableBytes: number;
  totalBytes: number;
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
  const [transfer, setTransfer] = useState<TransferTaskSnapshot | null>(null);
  const [transferDestination, setTransferDestination] = useState<StorageDestination | null>(null);
  const [transferError, setTransferError] = useState<string | null>(null);
  const [phoneStorage, setPhoneStorage] = useState<PhoneStorage | null>(() => readPhoneStorage());

  useEffect(() => {
    void Promise.all([SecureStore.getItemAsync(TOKEN_KEY), SecureStore.getItemAsync(SERVER_KEY)]).then(
      ([savedToken, savedServer]) => {
        if (savedToken) setToken(savedToken);
        if (savedServer) setAddress(savedServer);
      },
    );
    const subscription = transferEngine.subscribe(setTransfer);
    return () => subscription.remove();
  }, [transferEngine]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') setPhoneStorage(readPhoneStorage());
    });
    return () => subscription.remove();
  }, []);

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
          setTransfer(null);
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
      const device = await serverClient.pair(normalizedAddress, pairingCode, 'Mobile device');
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
      setTransfer(null);
      setTransferDestination(null);
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
    if (!token || !selectedDestinationId) return;
    setTransferError(null);
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      setTransferError('Photo-library permission is required to choose an original.');
      return;
    }

    const result = await ImagePicker.launchImageLibraryAsync({
      allowsEditing: false,
      mediaTypes: ['images', 'videos'],
      quality: 1,
    });
    if (result.canceled) return;

    const asset = result.assets[0];
    if (!asset) return;
    const uploadId = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
    const uploadDestination = destinations.find((destination) => destination.id === selectedDestinationId) ?? null;

    try {
      setTransferDestination(uploadDestination);
      await transferEngine.enqueue({
        uploadId,
        sourceAssetId: asset.assetId ?? uploadId,
        stagedFileUri: asset.uri,
        destinationUrl: `${normalizeBaseUrl(address)}/api/v1/upload`,
        byteSize: asset.fileSize ?? 0,
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': asset.mimeType ?? 'application/octet-stream',
          'X-Shove-Destination': selectedDestinationId,
          'X-Shove-Filename': asset.fileName ?? `${uploadId}.bin`,
        },
      });
    } catch (error) {
      setTransferError(messageOf(error));
    }
  }

  const progress = transfer && transfer.bytesExpected > 0
    ? Math.round((transfer.bytesSent / transfer.bytesExpected) * 100)
    : 0;
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
        <Text style={styles.title}>Shove one original home.</Text>
        <Text style={styles.subtitle}>Phone → local Wi-Fi → Windows storage. Nothing else yet.</Text>

        <View style={styles.phoneStorageStrip}>
          <View>
            <Text style={styles.phoneStorageLabel}>THIS PHONE</Text>
            <Text style={styles.phoneStorageValue}>
              {phoneStorage ? `${formatBytes(phoneStorage.availableBytes)} free` : 'Storage unavailable'}
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
                disabled={unpairing}
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
            disabled={!token || availableDestinations.length === 0}
            onPress={() => setDestinationMenuOpen((open) => !open)}
            style={[
              styles.destinationSelector,
              (!token || availableDestinations.length === 0) && styles.buttonDisabled,
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
            disabled={!token || !selectedDestination || transfer?.state === 'running'}
            onPress={chooseAndUpload}
            text="Choose photo or video"
          />
          {transfer && (
            <View style={styles.transferBlock}>
              <Text style={styles.transferState}>
                {transferStatus(transfer, progress, transferDestination)}
              </Text>
              <Text style={styles.transferBytes}>
                {formatBytes(transfer.bytesSent)} / {formatBytes(transfer.bytesExpected)}
              </Text>
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

function readPhoneStorage(): PhoneStorage | null {
  try {
    const totalBytes = Paths.totalDiskSpace;
    const availableBytes = Paths.availableDiskSpace;
    return totalBytes > 0 && availableBytes >= 0 ? { availableBytes, totalBytes } : null;
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
  if (transfer.state === 'running' && progress >= 100) {
    return destination?.id === 'local'
      ? 'Upload complete · Verifying on Windows…'
      : `Upload complete · Saving to ${destination?.displayName ?? 'external storage'}…`;
  }
  return `${transfer.state} · ${progress}%`;
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
  transferBlock: { borderTopColor: '#234336', borderTopWidth: 1, marginTop: 15, paddingTop: 13 },
  transferState: { color: '#e5fff2', fontSize: 15, fontWeight: '600' },
  transferBytes: { color: '#789187', fontSize: 12, marginTop: 5 },
});
