# Polar transport proof-of-concept

Two debug APK variants are configured:

- `standardDebug`: generic BLE HR receiver, minSdk 26 (Android 8+).
- `polarDebug`: Polar SDK test screen, minSdk 33 (Android 13+), app ID suffix `.polar`.

The Polar flavor adds a separate launcher entry named `Polar 设备连接测试`. It validates SDK discovery, per-sensor connection, HR callback, battery callback and RSSI before merging the transport into the athlete dashboard.

Pinned dependency: `com.github.polarofficial:polar-ble-sdk:8.4.0` from JitPack.

Polar's current Android quickstart specifies minSdk 33. The standard flavor retains API 26. One SDK instance is used per process.

Polar documents that the SDK can interact with multiple devices, but parallel operations across many separate devices have not been extensively tested. Validate incrementally with 1, 2, then 5+ physical sensors. This proof-of-concept does not infer swim distance, laps or pace from HR.

See `app/src/polar/assets/THIRD_PARTY_NOTICES.txt` for attribution. The full license is available in the official Polar SDK repository.
