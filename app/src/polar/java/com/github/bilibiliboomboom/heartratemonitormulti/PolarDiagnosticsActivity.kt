package com.github.bilibiliboomboom.heartratemonitormulti

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Isolated SDK proof-of-concept: discovery, multi-connect, HR and battery callbacks. */
class PolarDiagnosticsActivity : Activity() {
    private lateinit var api: PolarBleApi
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var scanJob: Job? = null
    private val found = linkedMapOf<String, String>()
    private val connected = linkedSetOf<String>()
    private val latestHr = linkedMapOf<String, Int>()
    private val batteries = linkedMapOf<String, Int>()
    private val rssiValues = linkedMapOf<String, Int>()
    private val rows = linkedMapOf<String, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        api = PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO
            )
        )
        api.setPolarFilter(true)
        api.setAutomaticReconnection(true)
        api.setApiCallback(object : PolarBleApiCallback() {
            override fun deviceConnected(info: PolarDeviceInfo) {
                runOnUiThread {
                    connected.add(info.deviceId)
                    status.text = "已连接 " + connected.size + " 台 Polar 设备"
                    refreshDevice(info.deviceId)
                }
            }
            override fun deviceDisconnected(info: PolarDeviceInfo) {
                runOnUiThread {
                    connected.remove(info.deviceId)
                    refreshDevice(info.deviceId)
                    status.text = "设备断开：" + info.name
                }
            }
            override fun hrNotificationReceived(identifier: String, data: PolarHrData.PolarHrSample) {
                runOnUiThread {
                    latestHr[identifier] = data.hr
                    refreshDevice(identifier)
                }
            }
            override fun batteryLevelReceived(identifier: String, level: Int) {
                runOnUiThread {
                    batteries[identifier] = level
                    refreshDevice(identifier)
                }
            }
        })
        if (!hasBluetoothPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                410
            )
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 18)
        }
        root.addView(TextView(this).apply {
            text = "POLAR BLE · 多设备连接验证"
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        status = TextView(this).apply {
            text = "准备就绪。请让 Polar 设备处于开启状态。"
            textSize = 15f
            setPadding(0, 10, 0, 14)
        }
        root.addView(status)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(Button(this).apply {
            text = "扫描 Polar 设备"
            setOnClickListener { if (scanJob?.isActive == true) stopScan() else startScan() }
        })
        controls.addView(Button(this).apply {
            text = "全部断开"
            setOnClickListener { connected.toList().forEach { id -> runCatching { api.disconnectFromDevice(id) } } }
        })
        root.addView(controls)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun hasBluetoothPermissions(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
             checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)

    private fun startScan() {
        if (!hasBluetoothPermissions()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 410)
            status.text = "请授予附近设备权限后再次扫描"
            return
        }
        found.clear()
        rows.clear()
        content.removeAllViews()
        status.text = "正在搜索 Polar 设备…"
        scanJob?.cancel()
        scanJob = scope.launch {
            api.searchForDevice()
                .catch { error -> status.text = "扫描错误：" + (error.message ?: error.javaClass.simpleName) }
                .collect { info ->
                    found[info.deviceId] = info.name
                    addOrRefreshDevice(info.deviceId, info.name, info.rssi)
                }
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        status.text = "扫描已停止 · 发现 " + found.size + " 台 · 已连接 " + connected.size + " 台"
    }

    private fun addOrRefreshDevice(id: String, name: String, rssi: Int) {
        rssiValues[id] = rssi
        var row = rows[id]
        if (row == null) {
            row = TextView(this).apply {
                textSize = 16f
                setPadding(14, 14, 14, 8)
                setBackgroundColor(0xFFEAF0F7.toInt())
            }
            rows[id] = row
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
            content.addView(Button(this).apply {
                text = "连接 " + name
                gravity = Gravity.CENTER
                setOnClickListener {
                    runCatching { api.connectToDevice(id) }
                        .onFailure { status.text = "连接请求失败：" + it.message }
                    status.text = "正在连接：" + name
                }
            })
        }
        row.text = deviceSummary(id, name, rssi)
    }

    private fun refreshDevice(id: String) {
        val name = found[id] ?: runCatching { api.getDeviceName(id) }.getOrNull() ?: id
        rows[id]?.text = deviceSummary(id, name, rssiValues[id] ?: -1)
    }

    private fun deviceSummary(id: String, name: String, rssi: Int): String {
        val state = if (id in connected) "已连接" else "已发现"
        val hr = latestHr[id]?.let { it.toString() + " BPM" } ?: "-- BPM"
        val battery = batteries[id]?.let { it.toString() + "%" } ?: "未知"
        return name + " · " + state + "\nID: " + id + "\n心率: " + hr + "    电量: " + battery + "    RSSI: " + rssi + " dBm"
    }

    override fun onDestroy() {
        scanJob?.cancel()
        scope.cancel()
        if (::api.isInitialized) api.shutDown()
        super.onDestroy()
    }
}
