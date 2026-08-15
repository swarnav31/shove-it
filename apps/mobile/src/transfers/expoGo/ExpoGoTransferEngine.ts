import {
  TransferEngine,
  TransferRequest,
  TransferSubscription,
  TransferTaskSnapshot,
} from '../TransferEngine';
import * as FileSystem from 'expo-file-system/legacy';

/**
 * Protocol-development adapter only. It intentionally does not pretend to
 * survive suspension or process termination. The production iOS adapter will
 * be a Swift Expo module backed by background URLSession upload tasks.
 */
export class ExpoGoTransferEngine implements TransferEngine {
  private readonly tasks = new Map<string, TransferTaskSnapshot>();
  private readonly nativeTasks = new Map<
    string,
    ReturnType<typeof FileSystem.createUploadTask>
  >();
  private readonly listeners = new Set<(snapshot: TransferTaskSnapshot) => void>();

  async enqueue(request: TransferRequest): Promise<TransferTaskSnapshot> {
    const snapshot: TransferTaskSnapshot = {
      uploadId: request.uploadId,
      nativeTaskId: `expo-go:${request.uploadId}`,
      state: 'running',
      bytesSent: 0,
      bytesExpected: request.byteSize,
      updatedAt: new Date().toISOString(),
    };
    this.tasks.set(request.uploadId, snapshot);
    this.emit(snapshot);

    const nativeTask = FileSystem.createUploadTask(
      request.destinationUrl,
      request.stagedFileUri,
      {
        headers: request.headers,
        httpMethod: 'POST',
        uploadType: FileSystem.FileSystemUploadType.BINARY_CONTENT,
      },
      ({ totalBytesSent, totalBytesExpectedToSend }) => {
        this.update(request.uploadId, {
          state: 'running',
          bytesSent: totalBytesSent,
          bytesExpected: totalBytesExpectedToSend || request.byteSize,
        });
      },
    );
    this.nativeTasks.set(request.uploadId, nativeTask);

    try {
      const response = await nativeTask.uploadAsync();
      if (!response || response.status < 200 || response.status >= 300) {
        throw new Error(`Upload failed with HTTP ${response?.status ?? 'unknown'}`);
      }
      return this.update(request.uploadId, {
        state: 'completed',
        bytesSent: request.byteSize,
        bytesExpected: request.byteSize,
        responseStatus: response.status,
      });
    } catch (error) {
      this.update(request.uploadId, {
        state: 'failed',
        errorCode: error instanceof Error ? error.message : 'upload-failed',
      });
      throw error;
    } finally {
      this.nativeTasks.delete(request.uploadId);
    }
  }

  async cancel(uploadId: string): Promise<void> {
    await this.nativeTasks.get(uploadId)?.cancelAsync();
    const current = this.tasks.get(uploadId);
    if (!current) return;

    const cancelled = { ...current, state: 'cancelled' as const, updatedAt: new Date().toISOString() };
    this.tasks.set(uploadId, cancelled);
    this.emit(cancelled);
  }

  async get(uploadId: string): Promise<TransferTaskSnapshot | null> {
    return this.tasks.get(uploadId) ?? null;
  }

  async list(): Promise<TransferTaskSnapshot[]> {
    return [...this.tasks.values()];
  }

  subscribe(listener: (snapshot: TransferTaskSnapshot) => void): TransferSubscription {
    this.listeners.add(listener);
    return { remove: () => this.listeners.delete(listener) };
  }

  private emit(snapshot: TransferTaskSnapshot): void {
    for (const listener of this.listeners) listener(snapshot);
  }

  private update(
    uploadId: string,
    changes: Partial<Omit<TransferTaskSnapshot, 'uploadId' | 'nativeTaskId'>>,
  ): TransferTaskSnapshot {
    const current = this.tasks.get(uploadId);
    if (!current) throw new Error(`Unknown upload ${uploadId}`);
    const updated = { ...current, ...changes, updatedAt: new Date().toISOString() };
    this.tasks.set(uploadId, updated);
    this.emit(updated);
    return updated;
  }
}
