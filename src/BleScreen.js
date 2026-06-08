const React = require('react');
const {
  ActivityIndicator,
  Platform,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  ScrollView,
  View,
} = require('react-native');

const {
  BleEvents,
  addBleListener,
  connect,
  disconnect,
  requestBlePermissions,
  startScan,
  stopScan,
} = require('./native');

function BleScreen() {
  const pendingDevicesRef = React.useRef(new Map());
  const [devices, setDevices] = React.useState([]);
  const [isScanning, setIsScanning] = React.useState(false);
  const [status, setStatus] = React.useState('Ready');
  const [connectedDevice, setConnectedDevice] = React.useState(null);

  React.useEffect(() => {
    const flushDevices = () => {
      if (pendingDevicesRef.current.size === 0) {
        return;
      }

      const pending = pendingDevicesRef.current;
      pendingDevicesRef.current = new Map();

      setDevices(prev => {
        const byAddress = new Map(prev.map(item => [item.address, item]));
        const order = prev.map(item => item.address);
        let changed = false;

        pending.forEach(device => {
          const existing = byAddress.get(device.address);

          if (!existing) {
            order.push(device.address);
            byAddress.set(device.address, device);
            changed = true;
            return;
          }

          const smoothedRssi =
            typeof existing.rssi === 'number' && typeof device.rssi === 'number'
              ? Math.round(existing.rssi * 0.7 + device.rssi * 0.3)
              : device.rssi ?? existing.rssi;
          const nextRssi =
            typeof existing.rssi === 'number' &&
            typeof smoothedRssi === 'number' &&
            Math.abs(smoothedRssi - existing.rssi) < 3
              ? existing.rssi
              : smoothedRssi;
          const nextName = device.name || existing.name;

          if (nextRssi === existing.rssi && nextName === existing.name) {
            return;
          }

          byAddress.set(device.address, {
            ...existing,
            name: nextName,
            rssi: nextRssi,
          });
          changed = true;
        });

        return changed
          ? order.map(address => byAddress.get(address)).filter(Boolean).slice(0, 50)
          : prev;
      });
    };

    const flushTimer = setInterval(flushDevices, 300);
    const subscriptions = [
      addBleListener(BleEvents.SCAN_RESULT, device => {
        if (!device || !device.address) {
          return;
        }

        pendingDevicesRef.current.set(device.address, device);
      }),
      addBleListener(BleEvents.CONNECTED, device => {
        setConnectedDevice(device || null);
        setStatus(`Connected${device?.name ? `: ${device.name}` : ''}`);
        setIsScanning(false);
      }),
      addBleListener(BleEvents.DISCONNECTED, () => {
        setConnectedDevice(null);
        setStatus('Disconnected');
        setIsScanning(false);
      }),
      addBleListener(BleEvents.ERROR, error => {
        const message = error?.message || 'BLE error';
        setStatus(message);
        setIsScanning(false);
      }),
      addBleListener(BleEvents.STATE, state => {
        const nextState = state?.state;

        if (nextState === 'scanning') {
          setIsScanning(true);
          return;
        }

        if (nextState === 'scan_stopped' || nextState === 'disconnected') {
          setIsScanning(false);
          return;
        }
      }),
      addBleListener(BleEvents.LOG, log => {
        const stage = log?.stage || 'native';
        const message = log?.message || '';
        if (message) {
          console.log(`[BLE:${stage}] ${message}`);
        }
      }),
    ];

    return () => {
      clearInterval(flushTimer);
      stopScan();
      subscriptions.forEach(subscription => {
        if (subscription && typeof subscription.remove === 'function') {
          subscription.remove();
        }
      });
    };
  }, []);

  const ensurePermissions = React.useCallback(async () => {
    if (Platform.OS !== 'android') {
      return true;
    }

    const granted = await requestBlePermissions();

    if (!granted) {
      setStatus('Bluetooth permissions are required');
    }

    return granted;
  }, []);

  const handleStartScan = React.useCallback(async () => {
    const allowed = await ensurePermissions();
    if (!allowed) {
      return;
    }

    setDevices([]);
    pendingDevicesRef.current.clear();
    setStatus('Scanning');
    setIsScanning(true);
    startScan();
  }, [ensurePermissions]);

  const handleStopScan = React.useCallback(() => {
    stopScan();
    setIsScanning(false);
    setStatus('Scan stopped');
  }, []);

  const handleConnect = React.useCallback(
    async device => {
      const allowed = await ensurePermissions();
      if (!allowed || !device?.address) {
        return;
      }

      setStatus(`Connecting to ${device.name || device.address}`);
      connect(device.address, device.name || '');
    },
    [ensurePermissions],
  );

  const handleDisconnect = React.useCallback(() => {
    disconnect();
  }, []);

    return React.createElement(
      SafeAreaView,
      {style: styles.container},
    React.createElement(
      View,
      {style: styles.header},
      React.createElement(Text, {style: styles.title}, 'BLE SDK'),
      React.createElement(Text, {style: styles.subtitle}, status),
      connectedDevice
        ? React.createElement(
            Text,
            {style: styles.connectedLabel},
            `Connected: ${connectedDevice.name || connectedDevice.address}`,
          )
        : null,
    ),
    React.createElement(
      View,
      {style: styles.actions},
      React.createElement(
        Pressable,
        {style: styles.primaryButton, onPress: handleStartScan},
        React.createElement(Text, {style: styles.primaryButtonText}, 'Start Scan'),
      ),
      React.createElement(
        Pressable,
        {style: styles.secondaryButton, onPress: handleStopScan},
        React.createElement(Text, {style: styles.secondaryButtonText}, 'Stop Scan'),
      ),
      React.createElement(
        Pressable,
        {style: styles.secondaryButton, onPress: handleDisconnect},
        React.createElement(Text, {style: styles.secondaryButtonText}, 'Disconnect'),
      ),
    ),
    isScanning
      ? React.createElement(
          View,
          {style: styles.scanningRow},
          React.createElement(ActivityIndicator, {size: 'small'}),
          React.createElement(Text, {style: styles.scanningText}, 'Scanning nearby bands'),
        )
      : null,
    React.createElement(
      View,
      {style: styles.deviceListFrame},
      React.createElement(
        ScrollView,
        {
          nestedScrollEnabled: true,
          keyboardShouldPersistTaps: 'handled',
          showsVerticalScrollIndicator: false,
          contentContainerStyle: styles.listContent,
        },
        devices.length
          ? devices.map(item =>
              React.createElement(
                View,
                {key: item.address, style: styles.deviceCard},
                React.createElement(
                  View,
                  {style: styles.deviceInfo},
                  React.createElement(Text, {style: styles.deviceName}, item.name || 'Unknown device'),
                  React.createElement(Text, {style: styles.deviceMeta}, item.address),
                  item.rssi !== undefined
                    ? React.createElement(Text, {style: styles.deviceMeta}, `RSSI ${item.rssi}`)
                    : null,
                ),
                React.createElement(
                  Pressable,
                  {
                    style: styles.connectButton,
                    onPress: () => handleConnect(item),
                  },
                  React.createElement(Text, {style: styles.connectButtonText}, 'Connect'),
                ),
              ),
            )
          : React.createElement(
              Text,
              {style: styles.emptyText},
              isScanning ? 'Scanning for devices...' : 'No devices found yet.',
            ),
      ),
    ),
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F6F4EF',
    padding: 16,
  },
  header: {
    marginBottom: 16,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#102A43',
  },
  subtitle: {
    marginTop: 4,
    fontSize: 14,
    color: '#52616B',
  },
  connectedLabel: {
    marginTop: 8,
    fontSize: 14,
    color: '#0B6E4F',
    fontWeight: '600',
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 12,
  },
  primaryButton: {
    backgroundColor: '#102A43',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  primaryButtonText: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
  secondaryButton: {
    backgroundColor: '#E4E7EB',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  secondaryButtonText: {
    color: '#102A43',
    fontWeight: '600',
  },
  scanningRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginBottom: 12,
  },
  scanningText: {
    color: '#52616B',
  },
  listContent: {
    paddingBottom: 24,
  },
  deviceListFrame: {
    height: 280,
    overflow: 'hidden',
  },
  emptyText: {
    marginTop: 20,
    color: '#7B8794',
    textAlign: 'center',
  },
  deviceCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 14,
    marginBottom: 10,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowRadius: 10,
    shadowOffset: {width: 0, height: 4},
    elevation: 2,
  },
  deviceInfo: {
    flex: 1,
    paddingRight: 12,
  },
  deviceName: {
    fontSize: 16,
    fontWeight: '700',
    color: '#102A43',
  },
  deviceMeta: {
    marginTop: 2,
    fontSize: 12,
    color: '#52616B',
  },
  connectButton: {
    backgroundColor: '#0B6E4F',
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  connectButtonText: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
});

module.exports = BleScreen;
module.exports.default = BleScreen;
