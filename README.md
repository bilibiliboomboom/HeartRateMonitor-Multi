# HeartRateMonitor Multi

Android 多设备 BLE 心率监测器，面向游泳队/团体训练场景。

## 当前版本
- 横屏多人列表
- BLE 标准心率服务（0x180D）扫描
- 多个 GATT 连接独立维护，可同时接收多台设备的 0x2A37 心率通知
- 单设备连接/断开与全部断开
- Android 8.0+；Android 12+ 使用蓝牙扫描/连接权限

## 使用
1. 在每只手环/心率设备上开启「心率广播」。
2. 手机保持蓝牙开启并授予附近设备权限。
3. 点击「扫描设备」，再逐一点击「连接」。
4. 每台设备会显示独立 BPM。

注意：设备必须广播并提供 Bluetooth SIG 标准 Heart Rate Service。小米手环 10 需先开启心率广播。游泳时 BLE 2.4GHz 信号在水下衰减明显，接收手机应放在池边，不能保证人在水下持续收数。

## Build
GitHub Actions 会在 push 到 main 后自动构建 Debug APK。
