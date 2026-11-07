package com.github.bilibiliboomboom.heartratemonitormulti

import android.Manifest
import android.app.Activity
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
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class MainActivity : Activity() {
    private val hrService = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val hrMeasurement = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    private val main = Handler(Looper.getMainLooper())
    private lateinit var adapter: BluetoothAdapter
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private val devices = linkedMapOf<String, DeviceRow>()
    private val gattMap = linkedMapOf<String, BluetoothGatt>()
    private var scanning = false

    private data class DeviceRow(
        val device: BluetoothDevice,
        var name: String,
        var bpm: Int = 0,
        var connected: Boolean = false,
        var updatedAt: Long = 0L,
        var card: LinearLayout? = null,
        var value: TextView? = null,
        var action: Button? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        adapter = manager.adapter
        buildUi()
        requestNeededPermissions()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 12) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "多人心率监测"; textSize = 24f; setTextColor(0xff172033.toInt()) },
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(Button(this).apply { text = "扫描设备"; setOnClickListener { if (scanning) stopScan() else startScan() } })
        header.addView(Button(this).apply { text = "全部断开"; setOnClickListener { disconnectAll() } })
        root.addView(header)
        status = TextView(this).apply { text = "准备就绪 · 请先在每只手环上开启心率广播"; textSize = 14f; setPadding(0, 8, 0, 12) }
        root.addView(status)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
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
            val name = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull() ?: "未知设备"
            main.post {
                val row = devices[id] ?: DeviceRow(device, name).also { devices[id] = it; addDeviceCard(id, it) }
                if (name.isNotBlank()) row.name = name
                refreshRow(row)
            }
        }
        override fun onScanFailed(errorCode: Int) {
            main.post { scanning = false; status.text = "扫描失败：$errorCode" }
        }
    }

    private fun startScan() {
        if (!hasPermissions()) { requestNeededPermissions(); return }
        if (!adapter.isEnabled) { status.text = "请先打开手机蓝牙"; return }
        try {
            val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(hrService)).build())
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            adapter.bluetoothLeScanner.startScan(filters, settings, scanCallback)
            scanning = true
            status.text = "正在扫描心率广播设备…"
            main.postDelayed({ if (scanning) stopScan() }, 20_000)
        } catch (e: SecurityException) { status.text = "缺少蓝牙权限" }
    }

    private fun stopScan() {
        if (!hasPermissions()) return
        runCatching { adapter.bluetoothLeScanner.stopScan(scanCallback) }
        scanning = false
        status.text = "扫描结束 · 已发现 ${devices.size} 台设备"
    }

    private fun addDeviceCard(id: String, row: DeviceRow) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14, 8, 14, 8)
            setBackgroundColor(0xfff1f5f9.toInt())
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(TextView(this).apply { text = row.name; textSize = 17f; setTextColor(0xff172033.toInt()) })
        val value = TextView(this).apply { text = "未连接"; textSize = 14f; setTextColor(0xff475569.toInt()) }
        info.addView(value)
        card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        val action = Button(this).apply { text = "连接"; setOnClickListener { if (row.connected) disconnect(id) else connect(id) } }
        card.addView(action)
        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
        row.card = card; row.value = value; row.action = action
    }

    private fun refreshRow(row: DeviceRow) {
        row.card?.let { card ->
            val title = (card.getChildAt(0) as LinearLayout).getChildAt(0) as TextView
            title.text = row.name
        }
        row.value?.text = when {
            row.connected && row.bpm > 0 -> "${row.bpm} BPM · 已连接"
            row.connected -> "已连接 · 等待心率数据"
            else -> "未连接"
        }
        row.action?.text = if (row.connected) "断开" else "连接"
    }

    private fun connect(id: String) {
        if (!hasPermissions()) { requestNeededPermissions(); return }
        val row = devices[id] ?: return
        if (gattMap.containsKey(id)) return
        try {
            status.text = "正在连接：${row.name}"
            val gatt = if (Build.VERSION.SDK_INT >= 23)
                row.device.connectGatt(this, false, callback(id), BluetoothDevice.TRANSPORT_LE)
            else row.device.connectGatt(this, false, callback(id))
            gattMap[id] = gatt
        } catch (e: SecurityException) { status.text = "蓝牙连接权限不足" }
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
                main.post { status.text = "设备不支持标准心率特征：${devices[id]?.name}" }
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
        main.post {
            devices[id]?.let { it.bpm = bpm; it.updatedAt = System.currentTimeMillis(); refreshRow(it) }
            status.text = "正在接收心率 · 已连接 ${gattMap.size} 台设备"
        }
    }

    private fun disconnect(id: String) {
        val gatt = gattMap.remove(id)
        if (gatt != null) {
            if (hasPermissions()) runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
        devices[id]?.let { it.connected = false; it.bpm = 0; refreshRow(it) }
    }

    private fun disconnectAll() { gattMap.keys.toList().forEach { disconnect(it) }; status.text = "已断开所有设备" }

    override fun onDestroy() {
        stopScan()
        disconnectAll()
        super.onDestroy()
    }
}
