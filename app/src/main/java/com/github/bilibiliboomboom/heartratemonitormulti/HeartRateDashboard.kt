package com.github.bilibiliboomboom.heartratemonitormulti

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HeartRateDeviceUi(
    val id: String, val alias: String, val name: String, val bpm: Int,
    val connected: Boolean, val detail: String, val history: List<Int>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateDashboard(
    revision: Int, status: String, session: String, scanning: Boolean, recording: Boolean,
    devices: List<HeartRateDeviceUi>, onScan: () -> Unit, onRecord: () -> Unit,
    onExport: () -> Unit, onDisconnectAll: () -> Unit, onConnect: (String) -> Unit,
    onRename: (String) -> Unit, onRemove: (String) -> Unit, onOrientation: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Scaffold(
        containerColor = cs.surface,
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("多人心率监测", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("MULTI HEART RATE MONITOR", fontSize = 10.sp, color = cs.onSurfaceVariant, letterSpacing = 1.2.sp)
                } },
                actions = {
                    IconButton(onClick = onOrientation) { Icon(Icons.Default.Tune, "横竖屏设置") }
                    FilledTonalIconButton(onClick = onScan) {
                        Icon(if (scanning) Icons.Default.Stop else Icons.Default.Refresh, if (scanning) "停止扫描" else "扫描设备")
                    }
                    Spacer(Modifier.width(8.dp))
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(22.dp), color = cs.primaryContainer.copy(alpha = .55f)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, null, tint = cs.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (scanning) "正在搜索心率设备" else "设备连接状态", fontWeight = FontWeight.SemiBold)
                            Text(status, fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                        Text("${devices.count { it.connected }} / ${devices.size}", color = cs.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                ElevatedCard(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, null, tint = cs.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("训练记录", fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text(if (recording) "● 记录中" else "未开始", color = if (recording) cs.error else cs.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(session, fontSize = 13.sp, color = cs.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onRecord, modifier = Modifier.weight(1f)) {
                                Icon(if (recording) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(6.dp)); Text(if (recording) "停止记录" else "开始记录")
                            }
                            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Download, null); Spacer(Modifier.width(5.dp)); Text("导出 CSV")
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("监测设备", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = cs.secondaryContainer) {
                        Text("${devices.size}", Modifier.padding(horizontal = 9.dp, vertical = 3.dp), fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDisconnectAll) { Text("全部断开") }
                }
            }
            if (devices.isEmpty()) item {
                Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 34.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(42.dp), tint = cs.outline)
                        Spacer(Modifier.height(12.dp))
                        Text("还没有发现设备", fontWeight = FontWeight.SemiBold)
                        Text("点击右上角扫描，确保手环或手表已开启心率广播", color = cs.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }
            items(devices, key = { it.id }) { device ->
                ElevatedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = cs.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = if (device.connected) cs.primaryContainer else cs.surfaceContainerHighest, modifier = Modifier.size(42.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, null, tint = if (device.connected) cs.primary else cs.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(device.alias, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(device.detail, fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 15.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (device.connected && device.bpm > 0) "${device.bpm}" else "--", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                                Text("BPM", fontSize = 10.sp, color = cs.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        HeartRateMiniChart(device.history, Modifier.fillMaxWidth().height(42.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onRename(device.id) }) { Text("改名称") }
                            TextButton(onClick = { onRemove(device.id) }) { Text("移除", color = cs.error) }
                            Spacer(Modifier.weight(1f))
                            if (device.connected) OutlinedButton(onClick = { onConnect(device.id) }) { Text("断开") }
                            else Button(onClick = { onConnect(device.id) }) { Text("连接设备") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartRateMiniChart(values: List<Int>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val low = (values.minOrNull() ?: 0).toFloat() - 5f
        val range = ((values.maxOrNull() ?: 1).toFloat() + 5f - low).coerceAtLeast(1f)
        val points = values.mapIndexed { i, value ->
            Offset(i.toFloat() / (values.size - 1) * size.width, size.height - (value - low) / range * size.height)
        }
        for (i in 0 until points.lastIndex) drawLine(color, points[i], points[i + 1], strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}
