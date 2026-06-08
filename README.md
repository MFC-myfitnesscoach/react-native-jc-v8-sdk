# react-native-jc-v8-sdk

Single React Native V8/JCVital watch SDK package for Android and iOS.

## Install in MFC

```json
"react-native-jc-v8-sdk": "file:../react-native-jc-v8-sdk"
```

Then reinstall dependencies from `MFC-App`. For iOS, run `pod install` in
`MFC-App/ios`.

The package contains:

- Android native bridge and `blesdk_2301-release.aar`
- iOS native bridge and vendor `libBleSDK.a`
- One shared JavaScript API and `BleScreen`

The vendor iOS static library contains the arm64 device architecture. Test the
iOS SDK on a physical iPhone.

## API

The package keeps the same JS surface used by MFC:

- `BleEvents`
- `addBleListener(...)`
- `setRealtimeData(...)`
- `getSleepHistory(...)`
- `getSleepAndActivityHistory(...)`
- `getTotalActivityData(...)`
- `getContinuousHRHistory(...)`
- `getSingleHRHistory(...)`
- `getAutomaticSpo2History(...)`
- `getManualSpo2History(...)`
- `getTemperatureHistory(...)`
- `getHRVHistory(...)`
- `getBatteryLevel(...)`
- `getDeviceVersion(...)`
- `getDeviceTime(...)`
- `getPersonalInfo(...)`
- `getStepGoal(...)`
- `getMacAddress(...)`
- `getPPIHistory(...)`
- `getDetailActivityData(...)`
- `setTransformConfig(...)`
- `ppgControl(...)`
- `clearAllHistoryData(...)`
- `configureSleepLogic(...)`
- `processSleepPayload(...)`
- `BleScreen`
