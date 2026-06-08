# react-native-jc-v8-sdk

React Native bridge for JC / V8 wearable bands on Android and iOS.

It provides one shared JavaScript API for:
- BLE scanning
- device connection and disconnection
- realtime data streaming
- historical data requests
- sleep payload normalization
- a built-in `BleScreen` demo UI

## Features

- Unified JS API for Android and iOS
- Native event listeners for scan, connect, data, state, error, and logs
- Realtime BLE packet support
- History helpers for sleep, activity, heart rate, SpO2, temperature, HRV, and battery/device info
- Built-in scan demo screen for quick testing

## Installation

### npm

```bash
npm install react-native-jc-v8-sdk
```

### yarn

```bash
yarn add react-native-jc-v8-sdk
```

### GitHub tag

```bash
yarn add "git+https://github.com/TalhaSwifter1122/react-native-jc-v8-sdk.git#v1.0.0"
```

### Local development

```bash
yarn add file:../react-native-jc-v8-sdk
```

## Native setup

### iOS

Run CocoaPods after installing the package:

```bash
cd ios
pod install
cd ..
```

Make sure your `Info.plist` includes Bluetooth usage strings:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app uses Bluetooth to communicate with the wearable.</string>
```

### Android

No manual linking is required.

Recommended Android baseline:
- `minSdkVersion`: `26`
- `targetSdkVersion`: match your app
- Bluetooth scan/connect permissions for Android 12+

## Permissions

### Android

For Android 12+ you should request the runtime Bluetooth permissions before scanning or connecting:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

If you support Android 11 or lower, keep the legacy location permission for scanning:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
```

Notes:
- `BLUETOOTH_SCAN` is required to discover BLE devices.
- `BLUETOOTH_CONNECT` is required to connect and communicate with a paired device.
- On Android 12 and higher, the system shows the Nearby Devices permission dialog.
- If your app derives physical location from BLE scans, follow the Android platform guidance for location permissions.

### iOS

Add Bluetooth usage strings to `Info.plist`:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app uses Bluetooth to communicate with the wearable.</string>
```

If you need to support an iOS deployment target earlier than iOS 13, also include:

```xml
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app uses Bluetooth to communicate with the wearable.</string>
```

## Compatibility

- React Native: `>=0.60.0`
- React: `>=18.0.0`
- Android: `minSdkVersion 26` or higher
- Android compile/target baseline: `35`
- iOS: `15.2` or higher
- Java language level: `17`

## Quick Start

Render the demo screen directly:

```js
import BleSdk, {BleScreen} from 'react-native-jc-v8-sdk';

export default function App() {
  return <BleScreen />;
}
```

Or wire the BLE flow yourself:

```js
import BleSdk, {
  BleEvents,
  addBleListener,
  requestBlePermissions,
} from 'react-native-jc-v8-sdk';
```

## Recommended Flow

```js
async function startWearableFlow() {
  const granted = await requestBlePermissions();
  if (!granted) {
    return;
  }

  const onFound = addBleListener(BleEvents.DEVICE_FOUND, device => {
    console.log('Found device:', device.uuid, device.name, device.rssi);
    BleSdk.stopScan();
    BleSdk.connect(device.uuid, device.name);
  });

  const onConnected = addBleListener(BleEvents.CONNECTED, device => {
    console.log('Connected:', device?.uuid);
    BleSdk.getSleepHistory(0);
    BleSdk.getAutomaticSpo2History(0);
    BleSdk.setRealtimeData(true);
  });

  const onData = addBleListener(BleEvents.DATA, payload => {
    console.log('Packet type:', payload.dataType);
    console.log('Payload:', payload.data);
  });

  const onDisconnected = addBleListener(BleEvents.DISCONNECTED, () => {
    console.log('Disconnected');
  });

  BleSdk.startScan();

  return () => {
    onFound.remove();
    onConnected.remove();
    onData.remove();
    onDisconnected.remove();
    BleSdk.stopScan();
  };
}
```

## Events

- `BleEvents.SCAN_RESULT` / `BleEvents.DEVICE_FOUND` - discovered device
- `BleEvents.CONNECTED` - device is ready
- `BleEvents.DATA` - parsed realtime or history packet
- `BleEvents.DISCONNECTED` - device disconnected
- `BleEvents.STATE` - scan/connect state updates
- `BleEvents.ERROR` - native error
- `BleEvents.LOG` - native status or upload log

## API Reference

### Connection and scanning

- `requestBlePermissions()`
- `startScan(nameFilter?)`
- `stopScan()`
- `connect(address, name?)`
- `disconnect()`
- `setRealtimeData(enable)`

### Data and parsing

- `addBleListener(eventName, handler)`
- `processSleepPayload(payload)`
- `configureSleepLogic(options)`
- `setTransformConfig(config)`

### History helpers

- `getSleepHistory(mode?, dateOfLastData?)`
- `getSleepAndActivityHistory(mode?, dateOfLastData?)`
- `getTotalActivityData(mode?, dateOfLastData?)`
- `getContinuousHRHistory(mode?, dateOfLastData?)`
- `getSingleHRHistory(mode?, dateOfLastData?)`
- `getAutomaticSpo2History(mode?, dateOfLastData?)`
- `getManualSpo2History(mode?, dateOfLastData?)`
- `getTemperatureHistory(mode?, dateOfLastData?)`
- `getHRVHistory(mode?, dateOfLastData?)`
- `getPPIHistory(mode?, dateOfLastData?)`
- `getDetailActivityData(mode?, dateOfLastData?)`

### Device and utility commands

- `getBatteryLevel()`
- `getDeviceVersion()`
- `getDeviceTime()`
- `getPersonalInfo()`
- `getStepGoal()`
- `getMacAddress()`
- `ppgControl(ppgMode, ppgStatus?)`
- `clearAllHistoryData()`

## Best Practices

- Ask for Bluetooth permissions before starting a scan.
- Start scanning only when the app is ready to connect.
- Stop scan once the target device is found.
- Call `setRealtimeData(true)` after a successful connection.
- Fetch history packets after `CONNECTED`, then listen for `DATA` updates.
- Always remove listeners when the screen unmounts.
- Test on a physical watch and phone before release.

## Notes

- Android uses the native BLE bridge included in this package.
- iOS uses the bundled V8 / JC wearable static SDK.
- The package is designed to work with npm and yarn.
- For release builds, verify the native package on a real device.

## References

- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Apple: NSBluetoothAlwaysUsageDescription](https://developer.apple.com/documentation/bundleresources/information-property-list/nsbluetoothalwaysusagedescription)

## Package Files

The published package includes:
- `index.js`
- `index.d.ts`
- `src/`
- `android/`
- `ios/`
- `README.md`
- `react-native.config.js`
- `react-native-jc-v8-sdk.podspec`

## License

Private and proprietary. Internal use only. See [LICENSE](./LICENSE) for terms.
