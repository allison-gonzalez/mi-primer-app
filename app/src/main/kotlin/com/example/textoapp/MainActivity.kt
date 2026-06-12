package com.example.textoapp

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity(),
    CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    lateinit var conectar: Button
    var activityContext: Context? = null

    private var deviceConnected: Boolean = false
    private val PAYLOAD_PATH = "/APP_OPEN"
    lateinit var nodeID: String

    private val SERVER_URL = "http://10.0.2.2:3000/datos"

    private lateinit var editText: EditText
    private lateinit var textView: TextView
    private lateinit var enviar: Button

    private lateinit var btnGet: Button
    private lateinit var btnPost: Button
    private lateinit var textRespuesta: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        activityContext = this
        editText = findViewById(R.id.editText)
        textView = findViewById(R.id.textView)
        conectar = findViewById(R.id.boton)
        enviar = findViewById(R.id.button)
        btnGet = findViewById(R.id.btnGet)
        btnPost = findViewById(R.id.btnPost)
        textRespuesta = findViewById(R.id.textRespuesta)

        // ── Chat con reloj ──
        conectar.setOnClickListener {
            if (!deviceConnected) {
                val tempAct: Activity = activityContext as MainActivity
                getNodes(tempAct)
            }
        }

        enviar.setOnClickListener {
            if (deviceConnected) {
                sendMessage()
            }
        }

        // ── HTTP ──
        btnGet.setOnClickListener {
            get(SERVER_URL)
        }

        btnPost.setOnClickListener {
            val jsonFijo = """{"mensaje": "Hola desde el celular", "origen": "celular"}"""
            post(SERVER_URL, jsonFijo)
        }
    }

    // ── Wearable ──

    private fun getNodes(context: Context) {
        launch(Dispatchers.Default) {
            val nodeList = Wearable.getNodeClient(context).connectedNodes
            try {
                val nodes = Tasks.await(nodeList)
                for (node in nodes) {
                    Log.d("NODO", node.toString())
                    Log.d("NODO", "El id del nodo es: ${node.id}")
                    nodeID = node.id
                    deviceConnected = true
                }
            } catch (exception: Exception) {
                Log.d("Error en el nodo", exception.toString())
            }
        }
    }

    private fun sendMessage() {
        val mensaje = editText.text.toString()
        Wearable.getMessageClient(activityContext!!)
            .sendMessage(nodeID, PAYLOAD_PATH, mensaje.toByteArray())
            .addOnSuccessListener {
                Log.d("sendMessage", "Mensaje enviado correctamente")
            }
            .addOnFailureListener { e ->
                Log.d("sendMessage", "Error al enviar mensaje ${e.message}")
            }
    }

    override fun onMessageReceived(ME: MessageEvent) {
        Log.d("onMessageReceived", ME.toString())
        Log.d("onMessageReceived", "ID del nodo ${ME.sourceNodeId}")
        Log.d("onMessageReceived", "Payload: ${ME.path}")
        val message = String(ME.data, StandardCharsets.UTF_8)
        Log.d("onMessageReceived", "Mensaje: ${message}")
        runOnUiThread {
            textView.text = "Mensaje del reloj: $message"
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

    // ── HTTP ──

    fun get(url: String) {
        // Crear un cliente de OkHttp
        val client = OkHttpClient()

        // Construir la petición
        val request = Request.Builder()
            .url(url)
            .build()

        // Ejecutar la petición en un hilo aparte
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                // Manejo de error
                Log.d("FETCH", "Error: ${e.message}")
                runOnUiThread {
                    textRespuesta.text = "Error: ${e.message}"
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.d("FETCH", "Error en la respuesta: ${response.code}")
                        runOnUiThread {
                            textRespuesta.text = "Error ${response.code}"
                        }
                    } else {
                        // Aquí se maneja la respuesta, por ejemplo, convertirla en String
                        val responseData = response.body?.string()
                        Log.d("FETCH", "Respuesta: $responseData")
                        runOnUiThread {
                            textRespuesta.text = responseData
                        }
                    }
                }
            }
        })
    }

    fun post(url: String, jsonBody: String) {
        val client = OkHttpClient()
        val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = jsonBody.toRequestBody(JSON)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.d("FETCH", "Error: ${e.message}")
                runOnUiThread {
                    textRespuesta.text = "Error: ${e.message}"
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Log.d("FETCH", "Error en la respuesta: ${response.code}")
                        runOnUiThread {
                            textRespuesta.text = "Error ${response.code}"
                        }
                    } else {
                        val responseData = response.body?.string()
                        Log.d("FETCH", "Respuesta: $responseData")
                        runOnUiThread {
                            textRespuesta.text = responseData
                        }
                    }
                }
            }
        })
    }
}
