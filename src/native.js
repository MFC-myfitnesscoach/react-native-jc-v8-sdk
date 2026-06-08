const {
  NativeModules,
  NativeEventEmitter,
  PermissionsAndroid,
  Platform,
} = require('react-native');

const NativeBleSdk = NativeModules.RNBleSdkV8;
const emitter = NativeBleSdk ? new NativeEventEmitter(NativeBleSdk) : null;
const isIOS = Platform.OS === 'ios';

const BleEvents = Object.freeze({
  CONNECTED: isIOS ? 'BleConnected' : 'RNBleSdkV8.connected',
  DISCONNECTED: isIOS ? 'BleDisconnected' : 'RNBleSdkV8.disconnected',
  DATA: isIOS ? 'BleData' : 'RNBleSdkV8.data',
  SCAN_RESULT: isIOS ? 'BleDeviceFound' : 'RNBleSdkV8.scanResult',
  DEVICE_FOUND: isIOS ? 'BleDeviceFound' : 'RNBleSdkV8.scanResult',
  CONNECT_FAILED: isIOS ? 'BleConnectFailed' : 'RNBleSdkV8.error',
  BLE_STATE_CHANGED: isIOS ? 'BlePowerStateChanged' : 'RNBleSdkV8.state',
  ERROR: isIOS ? 'BleConnectFailed' : 'RNBleSdkV8.error',
  STATE: isIOS ? 'BlePowerStateChanged' : 'RNBleSdkV8.state',
  LOG: isIOS ? 'BleUploadStatus' : 'RNBleSdkV8.log',
});

let sleepLogic = {
  awakeWindowToIgnoreMinutes: 5,
  minimumSleepSessionMinutes: 45,
  totalSleepTimeSource: 'sdk',
};

function callNative(method, ...args) {
  if (!NativeBleSdk || typeof NativeBleSdk[method] !== 'function') {
    return undefined;
  }

  return NativeBleSdk[method](...args);
}

function addBleListener(eventName, handler) {
  if (!emitter || !eventName || typeof handler !== 'function') {
    return {remove() {}};
  }

  return emitter.addListener(eventName, payload => {
    if (isIOS && eventName === BleEvents.SCAN_RESULT) {
      handler({
        ...payload,
        address: payload?.address || payload?.uuid,
      });
      return;
    }

    handler(payload);
  });
}

function configureSleepLogic(options = {}) {
  sleepLogic = {
    ...sleepLogic,
    ...(options || {}),
  };

  return sleepLogic;
}

function toNumberOrNull(value) {
  if (value === null || value === undefined || value === '') {
    return null;
  }

  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function normalizeData(data = {}) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    return {};
  }

  const normalized = {...data};

  const steps =
    data.steps ??
    data.step ??
    data.stepCount ??
    data.totalSteps ??
    data.total_step ??
    data.step_value ??
    data.Step;

  if (steps !== undefined) {
    normalized.steps = steps;
    normalized.step = steps;
    normalized.stepCount = steps;
  }

  const heartRate =
    data.heartRate ??
    data.hr ??
    data.HeartRate ??
    data.heartValue ??
    data.HR ??
    data.pulse;

  if (heartRate !== undefined) {
    normalized.heartRate = heartRate;
    normalized.hr = heartRate;
  }

  const calories = data.calories ?? data.Calories;
  if (calories !== undefined) {
    normalized.calories = calories;
  }

  const distance = data.distance ?? data.Distance;
  if (distance !== undefined) {
    normalized.distance = distance;
  }

  const spo2 =
    data.spo2 ??
    data.blood_oxygen ??
    data.bloodOxygen ??
    data.automaticSpo2Data ??
    data.manualSpo2Data;

  if (spo2 !== undefined) {
    normalized.spo2 = spo2;
  }

  const temperature =
    data.temperature ??
    data.bodyTemperature ??
    data.axillaryTemperature ??
    data.temp;

  if (temperature !== undefined) {
    normalized.temperature = temperature;
  }

  const hrv = data.hrv ?? data.HRV ?? data.hrvValue;
  if (hrv !== undefined) {
    normalized.hrv = hrv;
  }

  return normalized;
}

function processSleepPayload(payload) {
  const nextPayload =
    payload && typeof payload === 'object' ? {...payload} : {data: {}};

  const sourceData =
    (nextPayload.data && typeof nextPayload.data === 'object' && nextPayload.data) ||
    (nextPayload.dicData && typeof nextPayload.dicData === 'object' && nextPayload.dicData) ||
    {};

  nextPayload.data = normalizeData(sourceData);

  const dataType = toNumberOrNull(nextPayload.dataType);
  if (dataType !== null) {
    nextPayload.dataType = dataType;
  }

  if (nextPayload.dataEnd !== undefined) {
    nextPayload.dataEnd = Boolean(nextPayload.dataEnd);
  }

  nextPayload.sleepLogic = {...sleepLogic};
  return nextPayload;
}

async function requestBlePermissions() {
  if (Platform.OS !== 'android') {
    return true;
  }

  const permissions = [];

  if (Platform.Version >= 31) {
    permissions.push(
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
    );
  } else {
    permissions.push(PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION);
  }

  const result = await PermissionsAndroid.requestMultiple(permissions);
  return permissions.every(permission => result[permission] === PermissionsAndroid.RESULTS.GRANTED);
}

function startScan(nameFilter = '') {
  return isIOS
    ? callNative('startScan', nameFilter || null)
    : callNative('startScan');
}

function stopScan() {
  return callNative('stopScan');
}

function connect(address, name = '') {
  return isIOS ? callNative('connect', address) : callNative('connect', address, name);
}

function disconnect() {
  return callNative('disconnect');
}

function setRealtimeData(enable) {
  return callNative('setRealtimeData', isIOS ? (enable ? 1 : 0) : Boolean(enable));
}

function getSleepHistory(mode = 0, dateOfLastData = '') {
  return callNative('getSleepHistory', mode, normalizeHistoryDate(dateOfLastData));
}

function getSleepAndActivityHistory(mode = 0, dateOfLastData = '') {
  return callNative('getSleepAndActivityHistory', mode, normalizeHistoryDate(dateOfLastData));
}

function getTotalActivityData(mode = 0, dateOfLastData = '') {
  return callNative('getTotalActivityData', mode, normalizeHistoryDate(dateOfLastData));
}

function getContinuousHRHistory(mode = 0, dateOfLastData = '') {
  return callNative('getContinuousHRHistory', mode, normalizeHistoryDate(dateOfLastData));
}

function getSingleHRHistory(mode = 0, dateOfLastData = '') {
  return callNative('getSingleHRHistory', mode, normalizeHistoryDate(dateOfLastData));
}

function getAutomaticSpo2History(mode = 0, dateOfLastData = '') {
  return callNative('getAutomaticSpo2History', mode, normalizeHistoryDate(dateOfLastData));
}

function getManualSpo2History(mode = 0, dateOfLastData = '') {
  return callNative('getManualSpo2History', mode, normalizeHistoryDate(dateOfLastData));
}

function getTemperatureHistory(mode = 0, dateOfLastData = '') {
  return callNative('getTemperatureHistory', mode, normalizeHistoryDate(dateOfLastData));
}

function getHRVHistory(mode = 0, dateOfLastData = '') {
  return callNative('getHRVHistory', mode, normalizeHistoryDate(dateOfLastData));
}

function getBatteryLevel() {
  return callNative('getBatteryLevel');
}

function getDeviceVersion() {
  return callNative('getDeviceVersion');
}

function normalizeHistoryDate(value) {
  if (isIOS) {
    if (value instanceof Date) {
      return value.toISOString();
    }

    return value || null;
  }

  return value || '';
}

function setTransformConfig(config = {}) {
  return callNative('setTransformConfig', config);
}

function getDeviceTime() {
  return callNative('getDeviceTime');
}

function getPersonalInfo() {
  return callNative('getPersonalInfo');
}

function getStepGoal() {
  return callNative('getStepGoal');
}

function getMacAddress() {
  return callNative('getMacAddress');
}

function getPPIHistory(mode = 0, dateOfLastData = '') {
  return callNative('getPPIHistory', mode, normalizeHistoryDate(dateOfLastData));
}

function getDetailActivityData(mode = 0, dateOfLastData = '') {
  return callNative('getDetailActivityData', mode, normalizeHistoryDate(dateOfLastData));
}

function ppgControl(ppgMode, ppgStatus = 0) {
  return callNative('ppgControl', ppgMode, ppgStatus);
}

function clearAllHistoryData() {
  return callNative('clearAllHistoryData');
}

module.exports = {
  BleEvents,
  addBleListener,
  configureSleepLogic,
  connect,
  disconnect,
  getAutomaticSpo2History,
  getBatteryLevel,
  getContinuousHRHistory,
  getDetailActivityData,
  getDeviceTime,
  getDeviceVersion,
  getHRVHistory,
  getMacAddress,
  getManualSpo2History,
  getPersonalInfo,
  getPPIHistory,
  getSingleHRHistory,
  getSleepAndActivityHistory,
  getSleepHistory,
  getStepGoal,
  getTemperatureHistory,
  getTotalActivityData,
  clearAllHistoryData,
  ppgControl,
  processSleepPayload,
  requestBlePermissions,
  setRealtimeData,
  setTransformConfig,
  startScan,
  stopScan,
};
