package com.example.complementary_filter

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.complementary_filter.ui.theme.Complementary_filterTheme
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Complementary_filterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FilterComparisonScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun FilterComparisonScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    var filteredAccel by remember { mutableStateOf(floatArrayOf(0f, 0f, 9.81f)) }
    var systemGravity by remember { mutableStateOf(floatArrayOf(0f, 0f, 9.81f)) }
    var gyroValues by remember { mutableStateOf(floatArrayOf(0f, 0f, 0f)) }

    val kalmanRoll = remember { KalmanFilter() }
    val kalmanPitch = remember { KalmanFilter() }
    val compRoll = remember { ComplementaryFilter(0.96f) }
    val compPitch = remember { ComplementaryFilter(0.96f) }

    var kalmanAngles by remember { mutableStateOf(Pair(0f, 0f)) }
    var compAngles by remember { mutableStateOf(Pair(0f, 0f)) }
    var refAngles by remember { mutableStateOf(Pair(0f, 0f)) }

    var lastTimestamp by remember { mutableLongStateOf(0L) }
    var scale by remember { mutableFloatStateOf(1f) }
    val transformState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 3f)
    }

    val sensorEventListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        val alpha = 0.15f
                        filteredAccel = floatArrayOf(
                            filteredAccel[0] + alpha * (event.values[0] - filteredAccel[0]),
                            filteredAccel[1] + alpha * (event.values[1] - filteredAccel[1]),
                            filteredAccel[2] + alpha * (event.values[2] - filteredAccel[2])
                        )
                    }
                    Sensor.TYPE_GRAVITY -> {
                        systemGravity = event.values.clone()
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        gyroValues = event.values.clone()
                        if (lastTimestamp != 0L) {
                            val dt = (event.timestamp - lastTimestamp) * 1e-9f
                            if (dt > 0.5f) { lastTimestamp = event.timestamp; return }

                            // Using robust angle calculation from Filters.kt
                            val (accRoll, accPitch) = calculateAnglesFromAccel(systemGravity[0], systemGravity[1], systemGravity[2])
                            refAngles = Pair(accRoll, accPitch)

                            // Gyro rates: X is Pitch Rate, Y is Roll Rate
                            val gr = gyroValues[1] * 180f / PI.toFloat()
                            val gp = gyroValues[0] * 180f / PI.toFloat()

                            kalmanAngles = Pair(
                                kalmanRoll.update(accRoll, gr, dt),
                                kalmanPitch.update(accPitch, gp, dt)
                            )
                            compAngles = Pair(
                                compRoll.update(accRoll, gr, dt),
                                compPitch.update(accPitch, gp, dt)
                            )
                            
                            // Debug log to verify stability
                            if (abs(gr) > 50 || abs(gp) > 50) {
                                Log.d("SensorDebug", "Stable R: $accRoll, P: $accPitch | Gyro: $gr, $gp")
                            }
                        }
                        lastTimestamp = event.timestamp
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    DisposableEffect(Unit) {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val grav = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        sensorManager.registerListener(sensorEventListener, accel, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(sensorEventListener, gyro, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(sensorEventListener, grav, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(sensorEventListener) }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("3D Filter Comparison", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
        
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            DataBox("Roll", kalmanAngles.first, compAngles.first)
            DataBox("Pitch", kalmanAngles.second, compAngles.second)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).transformable(transformState),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { FilterCard("LPF (Raw)", filteredAccel, refAngles, Color(0xFFE3F2FD), scale) }
            item { FilterCard("System Gravity", systemGravity, refAngles, Color(0xFFF1F8E9), scale) }
            item { FilterCard("Complementary", systemGravity, compAngles, Color(0xFFFFF3E0), scale) }
            item { FilterCard("Kalman", systemGravity, kalmanAngles, Color(0xFFF3E5F5), scale) }
        }
    }
}

@Composable
fun DataBox(label: String, kalman: Float, comp: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp)
        Text("K: ${"%.1f".format(kalman)}°", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
        Text("C: ${"%.1f".format(comp)}°", fontSize = 16.sp, color = Color.DarkGray)
    }
}

@Composable
fun FilterCard(title: String, gData: FloatArray, angles: Pair<Float, Float>, bgColor: Color, scale: Float) {
    Card(colors = CardDefaults.cardColors(containerColor = bgColor), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.Black)
            Text("R: ${"%.1f".format(angles.first)}° P: ${"%.1f".format(angles.second)}°", fontSize = 14.sp, color = Color.Black)
            
            Box(modifier = Modifier.height(150.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)) {
                    ThreeDView(angles.first, angles.second, gData)
                }
            }
        }
    }
}

@Composable
fun ThreeDView(roll: Float, pitch: Float, accel: FloatArray) {
    Canvas(modifier = Modifier.size(110.dp, 140.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val viewScale = size.width / 130f
        
        fun project(x: Float, y: Float, z: Float): Offset {
            val rRoll = Math.toRadians(roll.toDouble()).toFloat()
            val rPitch = Math.toRadians(pitch.toDouble()).toFloat()
            val y1 = y * cos(rPitch) - z * sin(rPitch)
            val z1 = y * sin(rPitch) + z * cos(rPitch)
            val x2 = x * cos(rRoll) + z1 * sin(rRoll)
            val z2 = -x * sin(rRoll) + z1 * cos(rRoll)
            val pFactor = 400f / (400f + z2)
            return center + Offset(x2 * pFactor * viewScale, -y1 * pFactor * viewScale)
        }

        val w = 45f; val h = 80f; val d = 10f
        val corners = listOf(
            project(-w, -h, -d), project(w, -h, -d), project(w, h, -d), project(-w, h, -d),
            project(-w, -h, d),  project(w, -h, d),  project(w, h, d),  project(-w, h, d)
        )
        
        val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
        edges.forEach { (i, j) -> drawLine(Color.Black.copy(alpha = 0.4f), corners[i], corners[j], strokeWidth = 2f) }
        
        val frontPath = Path().apply {
            moveTo(corners[4].x, corners[4].y); lineTo(corners[5].x, corners[5].y)
            lineTo(corners[6].x, corners[6].y); lineTo(corners[7].x, corners[7].y); close()
        }
        drawPath(frontPath, Color.Gray.copy(alpha = 0.2f))

        val axisLen = 70f
        val origin = project(0f, 0f, 0f)
        val ax = accel[0]; val ay = accel[1]; val az = accel[2]
        val mag = sqrt(ax*ax + ay*ay + az*az).coerceAtLeast(0.1f)
        
        val gEnd = project(-ax/mag * axisLen, -ay/mag * axisLen, az/mag * axisLen)
        drawLine(Color.Black, origin, gEnd, strokeWidth = 10f, cap = StrokeCap.Round)
        
        val dx = gEnd.x - origin.x; val dy = gEnd.y - origin.y
        val ang = atan2(dy, dx)
        drawLine(Color.Black, gEnd, gEnd - Offset(cos(ang-0.5f)*15f, sin(ang-0.5f)*15f), strokeWidth = 10f)
        drawLine(Color.Black, gEnd, gEnd - Offset(cos(ang+0.5f)*15f, sin(ang+0.5f)*15f), strokeWidth = 10f)

        drawContext.canvas.nativeCanvas.apply {
            val p = android.graphics.Paint().apply {
                this.color = android.graphics.Color.BLACK; this.textSize = 28f; this.isFakeBoldText = true; this.textAlign = android.graphics.Paint.Align.CENTER
            }
            drawText("중력", gEnd.x, gEnd.y + 40f, p)
        }
    }
}
