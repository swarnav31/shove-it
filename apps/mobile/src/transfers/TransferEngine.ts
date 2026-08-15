export type TransferTaskState =
  | 'queued'
  | 'running'
  | 'waiting-for-network'
  | 'verifying'
  | 'completed'
  | 'failed'
  | 'cancelled';

export type TransferRequest = {
  uploadId: string;
  sourceAssetId: string;
  stagedFileUri: string;
  destinationUrl: string;
  byteSize: number;
  headers: Record<string, string>;
};

export type TransferTaskSnapshot = {
  uploadId: string;
  nativeTaskId: string;
  state: TransferTaskState;
  bytesSent: number;
  bytesExpected: number;
  responseStatus?: number;
  errorCode?: string;
  updatedAt: string;
};

export type TransferSubscription = {
  remove(): void;
};

export interface TransferEngine {
  /**
   * Persists the request before scheduling a file-backed platform upload.
   * Resolving means the OS accepted the task, not that the server verified it.
   */
  enqueue(request: TransferRequest): Promise<TransferTaskSnapshot>;

  cancel(uploadId: string): Promise<void>;

  get(uploadId: string): Promise<TransferTaskSnapshot | null>;

  /** Reconciles durable native state after launch/resume or missed events. */
  list(): Promise<TransferTaskSnapshot[]>;

  subscribe(listener: (snapshot: TransferTaskSnapshot) => void): TransferSubscription;
}

