export type ServerInfo = {
  name: string;
  protocolVersion: number;
  capabilities: string[];
};

export type PairedDevice = {
  deviceId: string;
  deviceName: string;
  token: string;
};

export type StorageDestination = {
  id: string;
  displayName: string;
  path: string;
  available: boolean;
  defaultDestination: boolean;
  freeBytes: number | null;
  fileSystem: string | null;
};

export type DeviceStatus = {
  deviceName: string;
  platform: string;
  availableBytes: number;
  totalBytes: number;
};

const REQUEST_TIMEOUT_MS = 5_000;

export class ServerRequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = 'ServerRequestError';
  }
}

export function isUnauthorized(error: unknown): error is ServerRequestError {
  return error instanceof ServerRequestError && error.status === 401;
}

export class ServerClient {
  async getServerInfo(rawBaseUrl: string): Promise<ServerInfo> {
    const baseUrl = normalizeBaseUrl(rawBaseUrl);
    const response = await fetchWithTimeout(`${baseUrl}/api/v1/server`, {
      headers: { Accept: 'application/json' },
    });

    if (!response.ok) {
      throw new ServerRequestError(`Server returned HTTP ${response.status}`, response.status);
    }

    return (await response.json()) as ServerInfo;
  }

  async pair(rawBaseUrl: string, code: string, deviceName: string): Promise<PairedDevice> {
    const baseUrl = normalizeBaseUrl(rawBaseUrl);
    const response = await fetchWithTimeout(`${baseUrl}/api/v1/pair`, {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ code, deviceName }),
    });

    if (!response.ok) {
      throw new ServerRequestError(
        response.status === 401 ? 'Pairing code is invalid or expired.' : `Pairing failed (HTTP ${response.status}).`,
        response.status,
      );
    }

    return (await response.json()) as PairedDevice;
  }

  async unpair(rawBaseUrl: string, token: string): Promise<void> {
    const baseUrl = normalizeBaseUrl(rawBaseUrl);
    const response = await fetchWithTimeout(`${baseUrl}/api/v1/device`, {
      method: 'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });

    if (!response.ok) {
      throw new ServerRequestError(
        response.status === 401 ? 'This pairing is no longer valid.' : `Unpair failed (HTTP ${response.status}).`,
        response.status,
      );
    }
  }

  async getDestinations(rawBaseUrl: string, token: string): Promise<StorageDestination[]> {
    const baseUrl = normalizeBaseUrl(rawBaseUrl);
    const response = await fetchWithTimeout(`${baseUrl}/api/v1/destinations`, {
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
    });

    if (!response.ok) {
      throw new ServerRequestError(
        response.status === 401 ? 'Pair again to view storage destinations.' : `Storage check failed (HTTP ${response.status}).`,
        response.status,
      );
    }

    return (await response.json()) as StorageDestination[];
  }

  async updateDeviceStatus(rawBaseUrl: string, token: string, status: DeviceStatus): Promise<void> {
    const baseUrl = normalizeBaseUrl(rawBaseUrl);
    const response = await fetchWithTimeout(`${baseUrl}/api/v1/device/status`, {
      method: 'PUT',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(status),
    });

    if (!response.ok) {
      throw new ServerRequestError(
        response.status === 401 ? 'This pairing is no longer valid.' : `Device status failed (HTTP ${response.status}).`,
        response.status,
      );
    }
  }
}

async function fetchWithTimeout(url: string, init: RequestInit): Promise<Response> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      throw new Error('The laptop did not respond within 5 seconds. Check that both devices are on the same Wi-Fi.');
    }
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

export function normalizeBaseUrl(value: string): string {
  const trimmed = value.trim().replace(/\/+$/, '');
  if (!trimmed) {
    throw new Error('Enter the laptop address shown by the Shove server.');
  }
  return /^https?:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`;
}
