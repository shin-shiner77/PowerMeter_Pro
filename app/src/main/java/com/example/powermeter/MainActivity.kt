package com.example.powermeter

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.powermeter.ui.theme.PowerMeterTheme
import kotlinx.coroutines.delay
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PowerMeterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PowerMeterProScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

data class HardwareUsage(val name: String, val currentMa: Float, val color: Color, val factor: Float)

@Composable
fun PowerMeterProScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val batteryManager = remember { context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager }
    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }

    // Measurements
    var voltageMv by remember { mutableFloatStateOf(0f) }
    var tempC by remember { mutableFloatStateOf(0f) }
    var stableCurrentMa by remember { mutableFloatStateOf(0f) }
    
    // Tracking
    var offsetMa by remember { mutableFloatStateOf(0f) }
    var isMonitoring by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }

    val sampleBuffer = remember { mutableStateListOf<Float>() }
    val graphData = remember { mutableStateListOf<Float>() }
    var hwBreakdown by remember { mutableStateOf(listOf<HardwareUsage>()) }

    // 1. High-speed Sampling (50ms)
    LaunchedEffect(Unit) {
        while (true) {
            val currentUa = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val currentMa = abs(currentUa / 1000f)
            
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)?.toFloat() ?: 0f
            tempC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f

            sampleBuffer.add(currentMa)
            if (sampleBuffer.size > 40) sampleBuffer.removeAt(0) // Keep last 2 seconds
            delay(50)
        }
    }

    // 2. Advanced Hardware Profiling & Stabilization (Every 1s)
    LaunchedEffect(Unit) {
        while (true) {
            if (sampleBuffer.size >= 10) {
                // Stabilize: Sort and remove top/bottom 20% peaks
                val sorted = sampleBuffer.sorted()
                val trimCount = (sorted.size * 0.2).toInt()
                val subList = sorted.subList(trimCount, sorted.size - trimCount)
                stableCurrentMa = subList.average().toFloat()

                // Update graph data
                if (isMonitoring) {
                    graphData.add((stableCurrentMa - offsetMa).coerceAtLeast(0f))
                    if (graphData.size > 50) graphData.removeAt(0)
                } else {
                    graphData.clear()
                }

                // GET SYSTEM STATES for better estimation
                val brightness = try {
                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                } catch (e: Exception) { 128 }
                
                val isWifiOn = try {
                    wifiManager.isWifiEnabled
                } catch (e: Exception) {
                    false
                }
                val tempFactor = (tempC - 25f).coerceAtLeast(0f) / 10f // Increase AP usage if hot

                // Calculate weights dynamically
                val displayWeight = (brightness / 255f) * 0.6f + 0.1f
                val wifiWeight = if (isWifiOn) 0.15f else 0.05f
                val apWeight = 0.2f + (tempFactor * 0.15f)
                val baseWeight = 1.0f - (displayWeight + wifiWeight + apWeight).coerceAtMost(0.9f)

                hwBreakdown = listOf(
                    HardwareUsage("Display (OLED)", stableCurrentMa * displayWeight, Color(0xFF2196F3), displayWeight),
                    HardwareUsage("AP (CPU/GPU)", stableCurrentMa * apWeight, Color(0xFFF44336), apWeight),
                    HardwareUsage("Radios (WiFi/LTE)", stableCurrentMa * wifiWeight, Color(0xFF4CAF50), wifiWeight),
                    HardwareUsage("System Core", stableCurrentMa * baseWeight, Color(0xFF9E9E9E), baseWeight)
                )
            }
            delay(1000)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(20.dp)) {
        Text("PowerMeter Pro", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.Black)
        
        // ADB Manual Grant Hint - Updated with correct package name
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))
        ) {
            Text(
                "정밀 모드: 'adb shell pm grant com.example.powermeter android.permission.BATTERY_STATS' 실행 권장",
                fontSize = 10.sp, color = Color.DarkGray, modifier = Modifier.padding(8.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            InfoBox("Voltage", "${"%.1f".format(voltageMv / 1000f)} V")
            InfoBox("Stable", "${"%.1f".format(stableCurrentMa)} mA")
            val powerMw = stableCurrentMa * (voltageMv / 1000f)
            InfoBox("Power", "${"%.0f".format(powerMw)} mW")
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Monitor Card
        val usbCurrent = if (isMonitoring) (stableCurrentMa - offsetMa).coerceAtLeast(0f) else 0f
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if(isMonitoring) Color.Black else Color(0xFFEEEEEE)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if(isMonitoring) "EXTERNAL USB CURRENT" else "READY TO MONITOR", color = if(isMonitoring) Color.Green else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(if(isMonitoring) "${"%.1f".format(usbCurrent)} mA" else "--", color = if(isMonitoring) Color.White else Color.LightGray, fontSize = 48.sp, fontWeight = FontWeight.Black)
                if (isMonitoring) {
                    Text("Power: ${"%.1f".format(usbCurrent * voltageMv / 1000f)} mW", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    RealTimeGraph(data = graphData, modifier = Modifier.fillMaxWidth().height(80.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Dynamic Hardware Breakdown", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(hwBreakdown) { hw ->
                HardwareLine(hw)
            }
        }

        // Buttons
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { offsetMa = stableCurrentMa },
                modifier = Modifier.weight(1f).height(60.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242))
            ) { Text("영점 조정", fontWeight = FontWeight.Bold) }
            
            Button(
                onClick = { isMonitoring = !isMonitoring },
                modifier = Modifier.weight(1f).height(60.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if(isMonitoring) Color.Red else Color(0xFF2E7D32))
            ) { Text(if(isMonitoring) "중지" else "모니터링", fontWeight = FontWeight.Bold) }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { showReport = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
        ) { Text("결과 리포트", fontWeight = FontWeight.Bold) }
    }

    if (showReport) {
        ReportDialog(stableCurrentMa, offsetMa, hwBreakdown, onDismiss = { showReport = false })
    }
}

@Composable
fun RealTimeGraph(data: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas

        val maxVal = (data.maxOrNull() ?: 100f).coerceAtLeast(100f) * 1.2f
        val width = size.width
        val height = size.height
        val stepX = width / 49f

        val path = Path().apply {
            val startY = height - (data[0] / maxVal * height)
            moveTo(0f, startY)
            data.forEachIndexed { index, value ->
                val x = index * stepX
                val y = height - (value / maxVal * height)
                lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Color.Green,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
fun InfoBox(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

@Composable
fun HardwareLine(hw: HardwareUsage) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(hw.color, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(10.dp))
            Text(hw.name, modifier = Modifier.weight(1f), fontSize = 14.sp, color = Color.Black)
            Text("${"%.1f".format(hw.currentMa)} mA", fontWeight = FontWeight.Black, color = Color.Black)
        }
        LinearProgressIndicator(
            progress = { hw.factor },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp),
            color = hw.color,
            trackColor = Color.LightGray.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun ReportDialog(total: Float, offset: Float, breakdown: List<HardwareUsage>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Power Analysis Report", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("Total Average: ${"%.1f".format(total)} mA", fontWeight = FontWeight.Bold)
                Text("System Baseline: ${"%.1f".format(offset)} mA")
                Text("USB Net Draw: ${"%.1f".format((total - offset).coerceAtLeast(0f))} mA", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                breakdown.forEach {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(it.name, fontSize = 13.sp)
                        Text("${"%.1f".format(it.currentMa)} mA", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } }
    )
}
