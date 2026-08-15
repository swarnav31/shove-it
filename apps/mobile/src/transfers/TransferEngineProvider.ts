import { TransferEngine } from './TransferEngine';
import { ExpoGoTransferEngine } from './expoGo/ExpoGoTransferEngine';

/**
 * Expo Go cannot contain Shove's Swift/Kotlin module. This factory deliberately
 * exposes a development-only foreground adapter until an EAS development build
 * can load the native engine.
 */
export function createTransferEngine(): TransferEngine {
  return new ExpoGoTransferEngine();
}

