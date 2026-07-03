package com.example.textoapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity(),
    CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    var activityContext: Context? = null

    private val SERVER_URL   = "http://192.168.3.102:3000/datos"
    private val RESUMEN_URL  = "http://192.168.3.102:3000/datos/resumen"
    private val PAYLOAD_PATH = "/SENSOR_DATA"

    private lateinit var tvEstadoCelular: TextView
    private lateinit var tvRitmoCelular: TextView
    private lateinit var tvPasosCelular: TextView
    private lateinit var tvGiroCelular: TextView
    private lateinit var btnGet: TextView
    private lateinit var textRespuesta: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        activityContext    = this
        tvEstadoCelular    = findViewById(R.id.tvEstadoCelular)
        tvRitmoCelular     = findViewById(R.id.tvRitmoCelular)
        tvPasosCelular     = findViewById(R.id.tvPasosCelular)
        tvGiroCelular      = findViewById(R.id.tvGiroCelular)
        btnGet             = findViewById(R.id.btnGet)
        textRespuesta      = findViewById(R.id.textRespuesta)

        // Ver resumen del día desde la BD
        btnGet.setOnClickListener {
            get(RESUMEN_URL)
        }
    }

    // ── Recibir datos del reloj ──

    override fun onMessageReceived(ME: MessageEvent) {
        if (ME.path != PAYLOAD_PATH) return

        val json = String(ME.data, StandardCharsets.UTF_8)
        Log.d("SENSOR_DATA", "Recibido: $json")

        try {
            val obj      = JSONObject(json)
            val hr       = obj.getDouble("heartRate")
            val steps    = obj.getInt("steps")
            val gyro     = obj.getDouble("gyro")

            runOnUiThread {
                tvEstadoCelular.text = "Conectado al reloj"
                tvRitmoCelular.text  = if (hr > 0) "${hr.toInt()}" else "--"
                tvPasosCelular.text  = "$steps"
                tvGiroCelular.text   = "${"%.2f".format(gyro)}"
                Toast.makeText(activityContext, "Datos nuevos recibidos del reloj", Toast.LENGTH_SHORT).show()
            }

            // Guardar en MongoDB automáticamente
            post(SERVER_URL, json)

        } catch (e: Exception) {
            Log.e("SENSOR_DATA", "Error al parsear: ${e.message}")
        }
    }

    // ── HTTP ──

    fun get(url: String) {
        val client  = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread { textRespuesta.text = "Error: ${e.message}" }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val data = response.body?.string()
                    runOnUiThread {
                        textRespuesta.text = if (response.isSuccessful) formatResumen(data) else "Error ${response.code}"
                    }
                }
            }
        })
    }

    fun post(url: String, jsonBody: String) {
        val client  = OkHttpClient()
        val body    = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("POST", "Error: ${e.message}")
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use { Log.d("POST", "Guardado: ${response.code}") }
            }
        })
    }

    // Formatea la respuesta del resumen para mostrarla legible
    private fun formatResumen(data: String?): String {
        if (data == null) return "Sin datos"
        return try {
            val obj = JSONObject(data)
            if (obj.has("mensaje")) return obj.getString("mensaje")
            buildString {
                appendLine("Resumen del dia")
                appendLine("─────────────────")
                if (obj.has("promedioHR"))    appendLine("Promedio HR:  ${obj.getString("promedioHR")} bpm")
                if (obj.has("maxHR"))         appendLine("Maximo HR:    ${obj.getInt("maxHR")} bpm")
                if (obj.has("minHR"))         appendLine("Minimo HR:    ${obj.getInt("minHR")} bpm")
                if (obj.has("pasos"))         appendLine("Pasos:        ${obj.getInt("pasos")}")
                if (obj.has("totalLecturas")) appendLine("Lecturas:     ${obj.getInt("totalLecturas")}")
            }
        } catch (e: Exception) {
            data
        }
    }

    override fun onDataChanged(p0: DataEventBuffer) {}
    override fun onCapabilityChanged(p0: CapabilityInfo) {}

    override fun onPause() {
        super.onPause()
        try {
            Wearable.getDataClient(activityContext!!).removeListener(this)
            Wearable.getMessageClient(activityContext!!).removeListener(this)
            Wearable.getCapabilityClient(activityContext!!).removeListener(this)
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onResume() {
        super.onResume()
        try {
            Wearable.getDataClient(activityContext!!).addListener(this)
            Wearable.getMessageClient(activityContext!!).addListener(this)
            Wearable.getCapabilityClient(activityContext!!)
                .addListener(this, android.net.Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
        } catch (e: Exception) { e.printStackTrace() }
    }
}
