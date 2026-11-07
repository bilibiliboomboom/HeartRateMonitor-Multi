package com.github.bilibiliboomboom.heartratemonitormulti

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.widget.EditText
import androidx.compose.ui.platform.ComposeView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : Activity() {
    private val hrService = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val hrMeasurement = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val main = Handler(Looper.getMainLooper())
    private lateinit var adapter: BluetoothAdapter
    private var statusText by androidx.compose.runtime.mutableStateOf("准备就绪 · 请在手环/手表上开启心率广播")
    private var sessionText by androidx.compose.runtime.mutableStateOf("尚未开始记录")
    private val devices = linkedMapOf<String, DeviceRow>()
    private val gattMap = linkedMapOf<String, BluetoothGatt>()
    private val sessionSamples = linkedMapOf<String, MutableList<HeartSample>>()
    private var scanning = false
    private var landscape = true
    private var recording = false
    private var recordingStartedAt = 0L
    private var uiRevision by androidx.compose.runtime.mutableIntStateOf(0)
    private val prefs by lazy { getSharedPreferences("athlete_names", MODE_PRIVATE) }
    private val clock = object : Runnable {
        override fun run() {
            devices.values.forEach { refreshRow(it) }
            if (recording) {
                val seconds = ((System.currentTimeMillis() - recordingStartedAt) / 1000).coerceAtLeast(0)
                sessionText = "记录中 · ${seconds / 60}分${seconds % 60}秒 · ${sessionSamples.values.sumOf { it.size }} 条数据"
                uiRevision++
            }
            main.postDelayed(this, 1000)
        }
    }

    private data class HeartSample(val time: Long, val bpm: Int)
    private data class DeviceRow(
        val device: BluetoothDevice,
        var name: String,
        var alias: String,
        var bpm: Int = 0,
        var connected: Boolean = false,
        var updatedAt: Long = 0L,
        var rssi: Int = 0,
        val history: MutableList<Int> = mutableListOf(),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        adapter = manager.adapter
        buildComposeUi()
        requestNeededPermissions()
        main.post(clock)
    }

    private fun buildComposeUi() {
        setContentView(ComposeView(this).apply {
            setContent {
                androidx.compose.material3.MaterialTheme {
                    HeartRateDashboard(
                        revision = uiRevision,
                        status = statusText,
                        session = sessionText,
                        scanning = scanning,
                        recording = recording,
                        devices = devices.map { (id, row) ->
                            val age = if (row.updatedAt == 0L) -1 else ((System.currentTimeMillis() - row.updatedAt) / 1000).toInt()
                            val live = when {
                                !row.connected -> "未连接"
                                age < 0 -> "已连接 · 等待心率数据"
                                age > 10 -> "数据中断 ${age}s · 检查广播/距离"
                                else -> "实时接收 · ${age}s 前更新"
                            }
                            HeartRateDeviceUi(id, row.alias, row.name, row.bpm, row.connected,
                                "$live · RSSI ${row.rssi} dBm", row.history.toList())
                        },
                        onScan = { if (scanning) stopScan() else startScan() },
                        onRecord = { toggleRecording() },
                        onExport = { exportCsv() },
                        onDisconnectAll = { disconnectAll() },
                        onConnect = { id -> if (gattMap.containsKey(id)) disconnect(id) else connect(id) },
                        onRename = { id -> editAlias(id) },
                        onRemove = { id -> disconnect(id); devices.remove(id); refreshAll(); statusText = "已移除设备" },
                        onOrientation = {
                            landscape = !landscape
                            requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    )
                }
            }
        })
    }

    private fun requestNeededPermissions() {
        val needed = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 100)
    }

    private fun hasPermissions(): Boolean = if (Build.VERSION.SDK_INT >= 31)
        checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    else checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val id = device.address
            val discoveredName = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull() ?: "未知设备"
            main.post {
                val row = devices[id] ?: DeviceRow(
                    device = device,
                    name = discoveredName,
                    alias = prefs.getString(id, null)?.takeIf { it.isNotBlank() } ?: discoveredName
                ).also { devices[id] = it }
                if (discoveredName.isNotBlank()) row.name = discoveredName
                row.rssi = result.rssi
                refreshRow(row)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            main.post { scanning = false; statusText = "扫描失败：$errorCode" }
        }
    }

    private fun startScan() {
        if (!hasPermissions()) { requestNeededPermissions(); return }
        if (!adapter.isEnabled) { statusText = "请先打开手机蓝牙"; return }
        try {
            val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(hrService)).build())
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            adapter.bluetoothLeScanner.startScan(filters, settings, scanCallback)
            scanning = true
            statusText = "正在扫描标准 BLE 心率广播设备…"
            main.postDelayed({ if (scanning) stopScan() }, 20_000)
        } catch (_: SecurityException) { statusText = "缺少蓝牙权限" }
    }

    private fun stopScan() {
        if (!hasPermissions()) return
        runCatching { adapter.bluetoothLeScanner.stopScan(scanCallback) }
        scanning = false
        statusText = "扫描结束 · 已发现 ${devices.size} 台设备"
    }

    private fun editAlias(id: String) {
        val row = devices[id] ?: return
        val input = EditText(this).apply { setSingleLine(true); setText(row.alias); hint = "例如：Andy、1号泳道" }
        AlertDialog.Builder(this)
            .setTitle("设置运动员名称")
            .setMessage("设备：${row.name}")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val alias = input.text.toString().trim().ifBlank { row.name }
                row.alias = alias
                prefs.edit().putString(id, alias).apply()
                refreshRow(row)
            }.show()
    }

    private fun refreshRow(row: DeviceRow) { uiRevision++ }

    private fun refreshAll() { uiRevision++ }

    private fun connect(id: String) {
        if (!hasPermissions()) { requestNeededPermissions(); return }
        val row = devices[id] ?: return
        if (gattMap.containsKey(id)) return
        try {
            statusText = "正在连接：${row.alias}"
            val gatt = if (Build.VERSION.SDK_INT >= 23)
                row.device.connectGatt(this, false, callback(id), BluetoothDevice.TRANSPORT_LE)
            else row.device.connectGatt(this, false, callback(id))
            gattMap[id] = gatt
        } catch (_: SecurityException) { statusText = "蓝牙连接权限不足" }
    }

    private fun callback(id: String) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, statusCode: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                try { gatt.discoverServices() } catch (_: SecurityException) {}
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                main.post {
                    gattMap.remove(id)
                    devices[id]?.let { it.connected = false; refreshRow(it) }
                    runCatching { gatt.close() }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, statusCode: Int) {
            val characteristic = gatt.getService(hrService)?.getCharacteristic(hrMeasurement)
            if (statusCode != BluetoothGatt.GATT_SUCCESS || characteristic == null) {
                main.post { statusText = "设备不支持标准心率特征：${devices[id]?.alias}" }
                return
            }
            try {
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(cccd)
                if (descriptor != null) {
                    if (Build.VERSION.SDK_INT >= 33) gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    else {
                        @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                }
                main.post { devices[id]?.let { it.connected = true; refreshRow(it) } }
            } catch (_: SecurityException) {}
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == hrMeasurement) parseHeartRate(id, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == hrMeasurement) parseHeartRate(id, value)
        }
    }

    private fun parseHeartRate(id: String, data: ByteArray) {
        if (data.size < 2) return
        val flags = data[0].toInt() and 0xff
        val bpm = if ((flags and 1) != 0) {
            if (data.size < 3) return
            (data[1].toInt() and 0xff) or ((data[2].toInt() and 0xff) shl 8)
        } else data[1].toInt() and 0xff
        if (bpm !in 1..250) return
        main.post {
            devices[id]?.let { row ->
                row.bpm = bpm
                row.updatedAt = System.currentTimeMillis()
                row.history.add(bpm)
                if (row.history.size > 60) row.history.removeAt(0)
                if (recording) {
                    val samples = sessionSamples.getOrPut(id) { mutableListOf() }
                    samples.add(HeartSample(row.updatedAt, bpm))
                    if (samples.size > 20_000) samples.removeAt(0)
                }
                refreshRow(row)
            }
            statusText = "正在接收心率 · 已连接 ${gattMap.size} 台设备"
        }
    }

    private fun toggleRecording() {
        if (!recording) {
            recording = true
            recordingStartedAt = System.currentTimeMillis()
            sessionSamples.clear()
            devices.keys.forEach { sessionSamples[it] = mutableListOf() }
            uiRevision++
            Toast.makeText(this, "已开始记录本次训练", Toast.LENGTH_SHORT).show()
        } else {
            recording = false
            uiRevision++
            Toast.makeText(this, "训练记录已暂存，可导出 CSV", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportCsv() {
        if (sessionSamples.values.all { it.isEmpty() }) {
            Toast.makeText(this, "暂无记录，请先开始记录并接收心率", Toast.LENGTH_SHORT).show()
            return
        }
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val csv = buildString {
            append("timestamp,device_name,device_address,heart_rate_bpm\n")
            sessionSamples.forEach { (id, samples) ->
                val name = devices[id]?.alias ?: prefs.getString(id, "Unknown") ?: "Unknown"
                samples.forEach { sample ->
                    append("\"${formatter.format(Date(sample.time))}\",\"${name.replace("\"", "\"\"")}\",\"$id\",${sample.bpm}\n")
                }
            }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "多人心率训练记录")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        startActivity(Intent.createChooser(send, "导出/分享训练 CSV"))
    }

    private fun disconnect(id: String) {
        val gatt = gattMap.remove(id)
        if (gatt != null) {
            if (hasPermissions()) runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
        devices[id]?.let { it.connected = false; it.bpm = 0; refreshRow(it) }
    }

    private fun disconnectAll() {
        gattMap.keys.toList().forEach { disconnect(it) }
        statusText = "已断开所有设备"; uiRevision++
    }


    override fun onDestroy() {
        main.removeCallbacks(clock)
        stopScan()
        disconnectAll()
        super.onDestroy()
    }

}
