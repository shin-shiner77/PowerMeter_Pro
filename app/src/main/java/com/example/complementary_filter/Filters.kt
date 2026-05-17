package com.example.complementary_filter

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

class KalmanFilter {
    private var qAngle = 0.001f
    private var qBias = 0.003f
    private var rMeasure = 0.03f

    private var angle = 0f
    private var bias = 0f
    private var p = arrayOf(floatArrayOf(0f, 0f), floatArrayOf(0f, 0f))

    fun update(newAngle: Float, newRate: Float, dt: Float): Float {
        val rate = newRate - bias
        angle += dt * rate

        p[0][0] += dt * (dt * p[1][1] - p[0][1] - p[1][0] + qAngle)
        p[0][1] -= dt * p[1][1]
        p[1][0] -= dt * p[1][1]
        p[1][1] += qBias * dt

        val s = p[0][0] + rMeasure
        val k = floatArrayOf(p[0][0] / s, p[1][0] / s)

        val y = newAngle - angle
        angle += k[0] * y
        bias += k[1] * y

        val p00Temp = p[0][0]
        val p01Temp = p[0][1]

        p[0][0] -= k[0] * p00Temp
        p[0][1] -= k[0] * p01Temp
        p[1][0] -= k[1] * p00Temp
        p[1][1] -= k[1] * p01Temp

        return angle
    }
}

class ComplementaryFilter(private val alpha: Float = 0.96f) {
    private var angle = 0f

    fun update(accelAngle: Float, gyroRate: Float, dt: Float): Float {
        angle = alpha * (angle + gyroRate * dt) + (1 - alpha) * accelAngle
        return angle
    }
}

fun calculateAnglesFromAccel(ax: Float, ay: Float, az: Float): Pair<Float, Float> {
    // Robust Formulas to avoid Gimbal Lock jumps
    // Roll: Rotation around Y-axis. Stable even when Z is zero.
    val roll = atan2(ax, sqrt(ay * ay + az * az)) * 180 / PI.toFloat()
    
    // Pitch: Rotation around X-axis.
    val pitch = atan2(-ay, sqrt(ax * ax + az * az)) * 180 / PI.toFloat()

    return Pair(roll, pitch)
}
