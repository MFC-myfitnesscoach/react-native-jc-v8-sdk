import type {ComponentType} from 'react';

export type BleEventPayload = Record<string, any>;

export declare const BleEvents: {
  readonly CONNECTED: string;
  readonly DISCONNECTED: string;
  readonly DATA: string;
  readonly SCAN_RESULT: string;
  readonly DEVICE_FOUND: string;
  readonly CONNECT_FAILED: string;
  readonly BLE_STATE_CHANGED: string;
  readonly ERROR: string;
  readonly STATE: string;
  readonly LOG: string;
};

export declare function addBleListener(
  eventName: string,
  handler: (payload: BleEventPayload) => void,
): {remove(): void};

export declare function startScan(nameFilter?: string): void;
export declare function stopScan(): void;
export declare function connect(address: string, name?: string): void;
export declare function disconnect(): void;
export declare function setTransformConfig(config?: Record<string, number>): void;
export declare function getDeviceTime(): void;
export declare function getPersonalInfo(): void;
export declare function setRealtimeData(enable: boolean): void;
export declare function getStepGoal(): void;
export declare function getMacAddress(): void;
export declare function getSleepHistory(mode?: number, dateOfLastData?: string): void;
export declare function getSleepAndActivityHistory(
  mode?: number,
  dateOfLastData?: string,
): void;
export declare function getTotalActivityData(
  mode?: number,
  dateOfLastData?: string,
): void;
export declare function getContinuousHRHistory(
  mode?: number,
  dateOfLastData?: string,
): void;
export declare function getSingleHRHistory(
  mode?: number,
  dateOfLastData?: string,
): void;
export declare function getAutomaticSpo2History(
  mode?: number,
  dateOfLastData?: string,
): void;
export declare function getManualSpo2History(
  mode?: number,
  dateOfLastData?: string,
): void;
export declare function getTemperatureHistory(
  mode?: number,
  dateOfLastData?: string,
): void;
export declare function getHRVHistory(mode?: number, dateOfLastData?: string): void;
export declare function getPPIHistory(mode?: number, dateOfLastData?: string): void;
export declare function getDetailActivityData(mode?: number, dateOfLastData?: string): void;
export declare function getBatteryLevel(): void;
export declare function getDeviceVersion(): void;
export declare function ppgControl(ppgMode: number, ppgStatus?: number): void;
export declare function clearAllHistoryData(): void;
export declare function configureSleepLogic(options?: Record<string, any>): Record<string, any>;
export declare function processSleepPayload(payload: BleEventPayload): BleEventPayload;
export declare function requestBlePermissions(): Promise<boolean>;

declare const BleScreen: ComponentType<any>;
export {BleScreen};
export default BleScreen;
