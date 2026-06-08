package com.reactnativeblesdkv8;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.jstyle.blesdkv8.Util.BleSDK;
import com.jstyle.blesdkv8.callback.DataListener2301;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

public class RNBleSdkV8Module extends ReactContextBaseJavaModule implements LifecycleEventListener {
    private static final String TAG = "RNBleSdkV8";
    private static final UUID SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID WRITE_UUID = UUID.fromString("0000fff6-0000-1000-8000-00805f9b34fb");
    private static final UUID NOTIFY_UUID = UUID.fromString("0000fff7-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String EVENT_LOG = "RNBleSdkV8.log";

    private final ReactApplicationContext reactContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Queue<byte[]> commandQueue = new ArrayDeque<>();
    private final Map<String, Long> seenScanDevices = new HashMap<>();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private BluetoothGattCharacteristic notifyCharacteristic;
    private BluetoothGattDescriptor notifyDescriptor;
    private boolean scanning = false;
    private boolean connected = false;
    private boolean notificationsReady = false;
    private boolean writing = false;
    private String connectedAddress = null;
    private String connectedName = null;

    private final DataListener2301 parserListener = new DataListener2301() {
        @Override
        public void dataCallback(Map<String, Object> maps) {
            WritableMap payload = mapToWritableMap(maps);

            if (connectedAddress != null) {
                payload.putString("uuid", connectedAddress);
                if (connectedName != null) {
                    payload.putString("name", connectedName);
                }
            }

            payload.putString("rawHex", "");
            emitEvent("RNBleSdkV8.data", payload);
        }

        @Override
        public void dataCallback(byte[] value) {
            WritableMap payload = Arguments.createMap();
            payload.putString("rawHex", BleSDK.byte2Hex(value));
            if (connectedAddress != null) {
                payload.putString("uuid", connectedAddress);
                if (connectedName != null) {
                    payload.putString("name", connectedName);
                }
            }
            emitEvent("RNBleSdkV8.data", payload);
        }
    };

    public RNBleSdkV8Module(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.reactContext.addLifecycleEventListener(this);
        initAdapter();
    }

    @NonNull
    @Override
    public String getName() {
        return "RNBleSdkV8";
    }

    @ReactMethod
    public void addListener(String eventName) {
        // Required by NativeEventEmitter. No-op.
    }

    @ReactMethod
    public void removeListeners(Integer count) {
        // Required by NativeEventEmitter. No-op.
    }

    @ReactMethod
    public void startScan() {
        if (!ensureAdapter()) {
            emitError("Bluetooth adapter is not available");
            return;
        }

        if (!canScan()) {
            emitError("Missing Bluetooth scan permissions");
            return;
        }

        if (!isBluetoothEnabled()) {
            emitError("Bluetooth is off. Turn it on and try again.");
            return;
        }

        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        if (bluetoothLeScanner == null) {
            emitError("Bluetooth scanner is not available");
            return;
        }

        stopScanInternal(false);
        seenScanDevices.clear();
        scanning = true;
        logNative("startScan", "Starting BLE scan");

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        try {
            bluetoothLeScanner.startScan((List<ScanFilter>) null, settings, scanCallback);
            emitState("scanning");
            logNative("startScan", "Scan started");
        } catch (SecurityException securityException) {
            emitError("Scan permission denied");
        }
    }

    @ReactMethod
    public void stopScan() {
        stopScanInternal(true);
    }

    @ReactMethod
    public void connect(String address, String deviceName) {
        if (!ensureAdapter()) {
            emitError("Bluetooth adapter is not available");
            return;
        }

        if (address == null || address.trim().isEmpty()) {
            emitError("Missing device address");
            return;
        }

        if (!canConnect()) {
            emitError("Missing Bluetooth connect permissions");
            return;
        }

        if (!isBluetoothEnabled()) {
            emitError("Bluetooth is off. Turn it on and try again.");
            return;
        }

        stopScanInternal(false);
        closeGatt(false);
        connectedAddress = address;
        connectedName = deviceName;
        notificationsReady = false;
        writing = false;
        commandQueue.clear();

        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bluetoothGatt = device.connectGatt(reactContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            } else {
                bluetoothGatt = device.connectGatt(reactContext, false, gattCallback);
            }
            emitState("connecting");
        } catch (IllegalArgumentException exception) {
            emitError("Invalid device address");
        } catch (SecurityException securityException) {
            emitError("Connect permission denied");
        }
    }

    @ReactMethod
    public void disconnect() {
        closeGatt(true);
    }

    @ReactMethod
    public void setRealtimeData(boolean enable) {
        queueCommand(BleSDK.RealTimeStep(enable, false));
    }

    @ReactMethod
    public void getSleepHistory(int mode, String dateOfLastData) {
        queueCommand(BleSDK.GetDetailSleepDataWithMode((byte) mode, safeDate(dateOfLastData)));
    }

    @ReactMethod
    public void getSleepAndActivityHistory(int mode, String dateOfLastData) {
        queueCommand(BleSDK.GetActivityModeDataWithMode((byte) mode));
    }

    @ReactMethod
    public void getTotalActivityData(int mode, String dateOfLastData) {
        queueCommand(BleSDK.GetTotalActivityDataWithMode((byte) mode, safeDate(dateOfLastData)));
    }

    @ReactMethod
    public void getContinuousHRHistory(int mode, String dateOfLastData) {
        queueCommand(BleSDK.GetDynamicHRWithMode((byte) mode, safeDate(dateOfLastData)));
    }

    @ReactMethod
    public void getSingleHRHistory(int mode, String dateOfLastData) {
        queueCommand(BleSDK.GetStaticHRWithMode((byte) mode, safeDate(dateOfLastData)));
    }

    @ReactMethod
    public void getAutomaticSpo2History(int mode, String dateOfLastData) {
        queueCommand(BleSDK.Oxygen_data((byte) mode, safeDate(dateOfLastData)));
    }

    @ReactMethod
    public void getManualSpo2History(int mode, String dateOfLastData) {
        queueCommand(BleSDK.Oxygen_data((byte) mode, safeDate(dateOfLastData)));
    }

    @ReactMethod
    public void getTemperatureHistory(int mode, String dateOfLastData) {
        queueCommand(BleSDK.GetTemperature_historyData((byte) mode, safeDate(dateOfLastData)));
    }

    @ReactMethod
    public void getHRVHistory(int mode, String dateOfLastData) {
        queueCommand(BleSDK.GetHRVDataWithMode((byte) mode, safeDate(dateOfLastData)));
    }

    @ReactMethod
    public void getBatteryLevel() {
        queueCommand(BleSDK.GetDeviceBatteryLevel());
    }

    @ReactMethod
    public void getDeviceVersion() {
        queueCommand(BleSDK.GetDeviceVersion());
    }

    @Override
    public void onHostResume() {
        // No-op.
    }

    @Override
    public void onHostPause() {
        // No-op.
    }

    @Override
    public void onHostDestroy() {
        closeGatt(true);
    }

    private void initAdapter() {
        BluetoothManager bluetoothManager = (BluetoothManager) reactContext.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }
    }

    private boolean ensureAdapter() {
        if (bluetoothAdapter == null) {
            initAdapter();
        }

        return bluetoothAdapter != null;
    }

    private boolean canScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasPermission(Manifest.permission.BLUETOOTH_SCAN);
        }

        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private boolean canConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasPermission(Manifest.permission.BLUETOOTH_CONNECT);
        }

        return true;
    }

    private boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(reactContext, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void stopScanInternal(boolean emitState) {
        if (bluetoothLeScanner != null && scanning) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
                logNative("stopScan", "Scan stopped");
            } catch (SecurityException ignored) {
                // Ignore.
            }
        }

        scanning = false;
        if (emitState) {
            emitState("scan_stopped");
        }
    }

    private void queueCommand(byte[] command) {
        if (command == null || command.length == 0) {
            return;
        }

        synchronized (commandQueue) {
            commandQueue.offer(command);
        }

        flushCommandQueue();
    }

    private void flushCommandQueue() {
        mainHandler.post(() -> {
            if (bluetoothGatt == null || writeCharacteristic == null || !notificationsReady || writing) {
                return;
            }

            byte[] next;
            synchronized (commandQueue) {
                next = commandQueue.poll();
            }

            if (next == null) {
                return;
            }

            try {
                writeCharacteristic.setValue(next);
                if ((writeCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                } else {
                    writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                }

                writing = bluetoothGatt.writeCharacteristic(writeCharacteristic);
                if (!writing) {
                    synchronized (commandQueue) {
                        ((ArrayDeque<byte[]>) commandQueue).addFirst(next);
                    }
                    emitError("Unable to write BLE command");
                }
            } catch (SecurityException exception) {
                synchronized (commandQueue) {
                    ((ArrayDeque<byte[]>) commandQueue).addFirst(next);
                }
                emitError("Missing Bluetooth connect permission");
            }
        });
    }

    private void closeGatt(boolean emitDisconnected) {
        boolean hadSession = connected || connectedAddress != null || connectedName != null;
        stopScanInternal(false);
        notificationsReady = false;
        writing = false;

        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
            } catch (SecurityException ignored) {
                // Ignore.
            }

            try {
                bluetoothGatt.close();
            } catch (Exception ignored) {
                // Ignore.
            }

            refreshGattCache(bluetoothGatt);
            bluetoothGatt = null;
        }

        writeCharacteristic = null;
        notifyCharacteristic = null;
        notifyDescriptor = null;

        synchronized (commandQueue) {
            commandQueue.clear();
        }

        if (hadSession) {
            WritableMap payload = Arguments.createMap();
            if (connectedAddress != null) {
                payload.putString("uuid", connectedAddress);
            }
            if (connectedName != null) {
                payload.putString("name", connectedName);
            }
            if (emitDisconnected) {
                emitEvent("RNBleSdkV8.disconnected", payload);
            }

            emitState("disconnected");
        }

        connected = false;
        connectedAddress = null;
        connectedName = null;
    }

    private void refreshGattCache(BluetoothGatt gatt) {
        try {
            Method refresh = gatt.getClass().getMethod("refresh");
            refresh.setAccessible(true);
            refresh.invoke(gatt);
        } catch (Exception ignored) {
            // Ignore.
        }
    }

    private void prepareNotification(BluetoothGatt gatt) {
        BluetoothGattService service = gatt.getService(SERVICE_UUID);
        if (service == null) {
            emitError("BLE service not found");
            closeGatt(true);
            return;
        }

        writeCharacteristic = service.getCharacteristic(WRITE_UUID);
        notifyCharacteristic = service.getCharacteristic(NOTIFY_UUID);

        if (writeCharacteristic == null || notifyCharacteristic == null) {
            emitError("BLE characteristics not found");
            closeGatt(true);
            return;
        }

        try {
            if (!gatt.setCharacteristicNotification(notifyCharacteristic, true)) {
                emitError("Unable to enable notifications");
                closeGatt(true);
                return;
            }
        } catch (SecurityException exception) {
            emitError("Missing Bluetooth connect permission");
            closeGatt(true);
            return;
        }

        notifyDescriptor = notifyCharacteristic.getDescriptor(CCCD_UUID);
        if (notifyDescriptor == null) {
            emitError("Notification descriptor not found");
            closeGatt(true);
            return;
        }

        notifyDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

        try {
            if (!gatt.writeDescriptor(notifyDescriptor)) {
                emitError("Unable to write notification descriptor");
                closeGatt(true);
            }
        } catch (SecurityException exception) {
            emitError("Missing Bluetooth connect permission");
            closeGatt(true);
        }
    }

    private void emitConnected() {
        connected = true;
        WritableMap payload = Arguments.createMap();
        payload.putString("uuid", connectedAddress);
        if (connectedName != null) {
            payload.putString("name", connectedName);
        }
        emitEvent("RNBleSdkV8.connected", payload);
        emitState("connected");
        flushCommandQueue();
    }

    private void emitState(String state) {
        WritableMap payload = Arguments.createMap();
        payload.putString("state", state);
        if (connectedAddress != null) {
            payload.putString("uuid", connectedAddress);
        }
        if (connectedName != null) {
            payload.putString("name", connectedName);
        }
        emitEvent("RNBleSdkV8.state", payload);
    }

    private void emitError(String message) {
        WritableMap payload = Arguments.createMap();
        payload.putString("message", message);
        emitEvent("RNBleSdkV8.error", payload);
        Log.w(TAG, message);
    }

    private void logNative(String stage, String message) {
        String text = stage + ": " + message;
        Log.d(TAG, text);
        WritableMap payload = Arguments.createMap();
        payload.putString("stage", stage);
        payload.putString("message", message);
        emitEvent(EVENT_LOG, payload);
    }

    private void emitEvent(String eventName, WritableMap payload) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, payload);
    }

    private WritableMap mapToWritableMap(Map<String, Object> map) {
        WritableMap writableMap = Arguments.createMap();
        if (map == null) {
            return writableMap;
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            putValue(writableMap, entry.getKey(), entry.getValue());
        }

        return writableMap;
    }

    private void putValue(WritableMap map, String key, Object value) {
        if (value == null) {
            map.putNull(key);
            return;
        }

        if (value instanceof Boolean) {
            map.putBoolean(key, (Boolean) value);
            return;
        }

        if (value instanceof Integer) {
            map.putInt(key, (Integer) value);
            return;
        }

        if (value instanceof Long) {
            long longValue = (Long) value;
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                map.putInt(key, (int) longValue);
            } else {
                map.putDouble(key, (double) longValue);
            }
            return;
        }

        if (value instanceof Float) {
            map.putDouble(key, ((Float) value).doubleValue());
            return;
        }

        if (value instanceof Double) {
            map.putDouble(key, (Double) value);
            return;
        }

        if (value instanceof Number) {
            map.putDouble(key, ((Number) value).doubleValue());
            return;
        }

        if (value instanceof String) {
            map.putString(key, (String) value);
            return;
        }

        if (value instanceof Map) {
            WritableMap nested = mapToWritableMap((Map<String, Object>) value);
            map.putMap(key, nested);
            if ("dicData".equals(key)) {
                map.putMap("data", mapToWritableMap((Map<String, Object>) value));
            }
            return;
        }

        if (value instanceof List) {
            map.putArray(key, listToWritableArray((List<Object>) value));
            return;
        }

        map.putString(key, String.valueOf(value));
    }

    private WritableArray listToWritableArray(List<Object> list) {
        WritableArray array = Arguments.createArray();
        if (list == null) {
            return array;
        }

        for (Object value : list) {
            if (value == null) {
                array.pushNull();
            } else if (value instanceof Boolean) {
                array.pushBoolean((Boolean) value);
            } else if (value instanceof Integer) {
                array.pushInt((Integer) value);
            } else if (value instanceof Long) {
                long longValue = (Long) value;
                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                    array.pushInt((int) longValue);
                } else {
                    array.pushDouble((double) longValue);
                }
            } else if (value instanceof Float) {
                array.pushDouble(((Float) value).doubleValue());
            } else if (value instanceof Double) {
                array.pushDouble((Double) value);
            } else if (value instanceof Number) {
                array.pushDouble(((Number) value).doubleValue());
            } else if (value instanceof String) {
                array.pushString((String) value);
            } else if (value instanceof Map) {
                array.pushMap(mapToWritableMap((Map<String, Object>) value));
            } else if (value instanceof List) {
                array.pushArray(listToWritableArray((List<Object>) value));
            } else {
                array.pushString(String.valueOf(value));
            }
        }

        return array;
    }

    private String safeDate(String dateOfLastData) {
        return dateOfLastData == null ? "" : dateOfLastData;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device == null) {
                return;
            }

            String address = device.getAddress();
            if (address == null || seenScanDevices.containsKey(address)) {
                logNative("scanResult", "Duplicate or missing address ignored");
                return;
            }

            String name = null;
            try {
                name = device.getName();
            } catch (SecurityException ignored) {
                // Ignore and fall back to scan record.
            }
            if ((name == null || name.isEmpty()) && result.getScanRecord() != null) {
                ScanRecord record = result.getScanRecord();
                name = record.getDeviceName();
            }
            if (name == null || name.isEmpty()) {
                name = "Unknown";
            }

            seenScanDevices.put(address, System.currentTimeMillis());
            logNative("scanResult", "Found device address=" + address + ", name=" + name + ", rssi=" + result.getRssi());

            WritableMap payload = Arguments.createMap();
            payload.putString("address", address);
            payload.putString("name", name);
            payload.putInt("rssi", result.getRssi());
            emitEvent("RNBleSdkV8.scanResult", payload);
        }

        @Override
        public void onScanFailed(int errorCode) {
            logNative("scanFailed", "Scan failed with code=" + errorCode);
            emitError("Scan failed: " + errorCode);
            stopScanInternal(true);
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                logNative("gatt", "Connection failed status=" + status);
                closeGatt(true);
                emitError("Bluetooth connection failed: " + status);
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                logNative("gatt", "Connected, discovering services");
                try {
                    gatt.discoverServices();
                } catch (SecurityException exception) {
                    emitError("Missing Bluetooth connect permission");
                    closeGatt(true);
                }
                return;
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                logNative("gatt", "Disconnected");
                closeGatt(true);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logNative("gatt", "Service discovery failed status=" + status);
                emitError("Service discovery failed: " + status);
                closeGatt(true);
                return;
            }

            prepareNotification(gatt);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (CCCD_UUID.equals(descriptor.getUuid()) && status == BluetoothGatt.GATT_SUCCESS) {
                notificationsReady = true;
                logNative("gatt", "Notifications enabled");
                emitConnected();
                return;
            }

            if (CCCD_UUID.equals(descriptor.getUuid())) {
                logNative("gatt", "Notification setup failed status=" + status);
                emitError("Notification setup failed: " + status);
                closeGatt(true);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            if (value == null) {
                return;
            }

            logNative("gatt", "Characteristic changed len=" + value.length);
            BleSDK.DataParsingWithData(value, parserListener);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            writing = false;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logNative("gatt", "Write failed status=" + status);
                emitError("Write failed: " + status);
            }

            flushCommandQueue();
        }
    };
}
