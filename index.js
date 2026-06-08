const native = require('./src/native');
const BleScreen = require('./src/BleScreen');

module.exports = {
  ...native,
  BleScreen,
  default: BleScreen,
};
