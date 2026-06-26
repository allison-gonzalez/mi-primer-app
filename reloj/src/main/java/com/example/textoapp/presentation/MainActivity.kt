package com.example.textoapp.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.example.textoapp.R
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : ComponentActivity(),
    SensorEventListener,
    CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    // ── Sensores ──
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var stepCounterSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null

    // ── Valores actuales ──
    private var heartRate: Float = 0f
    private var rawStepCount: Int = 0
    private var baselineSteps: Int = -1
    private var lastResetDay: Int = -1
    private var gyroX: Float = 0f
    private var gyroY: Float = 0f
    private var gyroZ: Float = 0f

    // ── Wearable ──
    private var nodeID: String = ""
    private var deviceConnected: Boolean = false
    private val PAYLOAD_PATH = "/SENSOR_DATA"

    // ── UI ──
    private lateinit var tvEstado: TextView
    private lateinit var tvHora: TextView
    private lateinit var tvRitmo: TextView
    private lateinit var tvPasos: TextView
    private lateinit var tvGiro: TextView

    // ── Handler para actualización cada 5 segundos ──
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            actualizarPantalla()
            enviarDatosAlCelular()
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvEstado = findViewById(R.id.tvEstado)
        tvHora   = findViewById(R.id.tvHora)
        tvRitmo  = findViewById(R.id.tvRitmo)
        tvPasos  = findViewById(R.id.tvPasos)
        tvGiro   = findViewById(R.id.tvGiro)

        sensorManager    = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor  = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        gyroscopeSensor  = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Recuperar datos de pasos guardados
        val prefs = getSharedPreferences("sensor_prefs", Context.MODE_PRIVATE)
        baselineSteps = prefs.getInt("baseline_steps", -1)
        lastResetDay  = prefs.getInt("last_reset_day", -1)

        iniciarSensores()
        getNodes()
        handler.post(updateRunnable)
    }

    // ── Permisos y registro de sensores ──

    private fun iniciarSensores() {
        val permisos = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED) {
            permisos.add(Manifest.permission.BODY_SENSORS)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            permisos.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (permisos.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permisos.toTypedArray(), 1001)
        } else {
            registrarSensores()
        }
    }

    private fun registrarSensores() {
        heartRateSensor?.let  { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        stepCounterSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscopeSensor?.let  { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) registrarSensores()
    }

    // ── Actualización de pantalla ──

    private fun actualizarPantalla() {
        // Reset de pasos a medianoche
        val hoy = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (lastResetDay != hoy && rawStepCount > 0) {
            baselineSteps = rawStepCount
            lastResetDay = hoy
            getSharedPreferences("sensor_prefs", Context.MODE_PRIVATE).edit()
                .putInt("baseline_steps", baselineSteps)
                .putInt("last_reset_day", hoy)
                .apply()
        }

        val hora      = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val pasosHoy  = if (baselineSteps < 0) 0 else maxOf(0, rawStepCount - baselineSteps)
        val gyroMag   = sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ)

        tvHora.text  = hora
        tvRitmo.text = if (heartRate > 0) "❤  ${heartRate.toInt()} bpm" else "❤  Sin lectura"
        tvPasos.text = "👟  $pasosHoy pasos"
        tvGiro.text  = "🔄  ${"%.2f".format(gyroMag)} rad/s"
    }

    // ── Wearable: buscar nodo del celular ──

    private fun getNodes() {
        launch(Dispatchers.Default) {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(this@MainActivity).connectedNodes)
                if (nodes.isNotEmpty()) {
                    nodeID = nodes[0].id
                    deviceConnected = true
                    withContext(Dispatchers.Main) { tvEstado.text = "● Conectado al celular" }
                } else {
                    withContext(Dispatchers.Main) { tvEstado.text = "○ Sin conexión" }
                }
            } catch (e: Exception) {
                Log.e("NODO", "Error: ${e.message}")
            }
        }
    }

    // ── Enviar datos al celular ──

    private fun enviarDatosAlCelular() {
        if (!deviceConnected || nodeID.isEmpty()) {
            // Intentar reconectar en segundo plano
            launch(Dispatchers.Default) {
                try {
                    val nodes = Tasks.await(Wearable.getNodeClient(this@MainActivity).connectedNodes)
                    if (nodes.isNotEmpty()) {
                        nodeID = nodes[0].id
                        deviceConnected = true
                        withContext(Dispatchers.Main) { tvEstado.text = "● Conectado al celular" }
                    }
                } catch (_: Exception) {}
            }
            return
        }

        val pasosHoy = if (baselineSteps < 0) 0 else maxOf(0, rawStepCount - baselineSteps)
        val gyroMag  = sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ)
        val json = """{"heartRate":$heartRate,"steps":$pasosHoy,"gyro":${"%.4f".format(gyroMag)},"timestamp":${System.currentTimeMillis()}}"""

        Wearable.getMessageClient(this)
            .sendMessage(nodeID, PAYLOAD_PATH, json.toByteArray())
            .addOnSuccessListener { Log.d("SEND", "Enviado: $json") }
            .addOnFailureListener {
                deviceConnected = false
                tvEstado.text = "○ Sin conexión"
            }
    }

    // ── SensorEventListener ──

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_HEART_RATE -> {
                heartRate = if (event.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_LOW && event.values[0] > 0)
                    event.values[0] else 0f
            }
            Sensor.TYPE_STEP_COUNTER -> {
                rawStepCount = event.values[0].toInt()
                if (baselineSteps < 0) {
                    baselineSteps = rawStepCount
                    lastResetDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                    getSharedPreferences("sensor_prefs", Context.MODE_PRIVATE).edit()
                        .putInt("baseline_steps", baselineSteps)
                        .putInt("last_reset_day", lastResetDay)
                        .apply()
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroX = event.values[0]
                gyroY = event.values[1]
                gyroZ = event.values[2]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onMessageReceived(p0: MessageEvent) {}
    override fun onDataChanged(p0: DataEventBuffer) {}
    override fun onCapabilityChanged(p0: CapabilityInfo) {}

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
        sensorManager.unregisterListener(this)
        try {
            Wearable.getDataClient(this).removeListener(this)
            Wearable.getMessageClient(this).removeListener(this)
            Wearable.getCapabilityClient(this).removeListener(this)
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onResume() {
        super.onResume()
        registrarSensores()
        handler.post(updateRunnable)
        getNodes()
        try {
            Wearable.getDataClient(this).addListener(this)
            Wearable.getMessageClient(this).addListener(this)
            Wearable.getCapabilityClient(this)
                .addListener(this, android.net.Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
        } catch (e: Exception) { e.printStackTrace() }
    }
}
