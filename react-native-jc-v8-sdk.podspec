require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-jc-v8-sdk"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = "https://github.com/MFC-myfitnesscoach/react-native-jc-v8-sdk"
  s.license      = "UNLICENSED"
  s.authors      = { "Muhammad Talha" => "m.talha8266@gmail.com" }

  s.platforms    = { :ios => "15.2" }
  s.source       = { :git => "https://github.com/MFC-myfitnesscoach/react-native-jc-v8-sdk.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m}"

  # Pre-compiled BleSDK static library
  s.vendored_libraries = "ios/BleSDK/libBleSDK.a"

  # Expose SDK headers
  s.preserve_paths = "ios/BleSDK/*.h"
  s.xcconfig = {
    "HEADER_SEARCH_PATHS" => "$(PODS_ROOT)/../node_modules/react-native-jc-v8-sdk/ios/BleSDK"
  }

  s.frameworks   = "CoreBluetooth", "Foundation"

  s.dependency "React-Core"
end
