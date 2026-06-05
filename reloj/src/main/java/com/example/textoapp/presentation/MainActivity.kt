package com.example.textoapp.presentation

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity(),
    CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    var activityContext: Context? = null
    private var deviceConnected: Boolean = false
    private val PAYLOAD_PATH = "/APP_OPEN"
    private var nodeID: String = ""

    private lateinit var textoMensaje: TextView
    private lateinit var editMensaje: EditText
    private lateinit var btnEnviar: Button
    private lateinit var boton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        activityContext = this
        textoMensaje = findViewById(R.id.textoMensaje)
        editMensaje = findViewById(R.id.editMensaje)
        btnEnviar = findViewById(R.id.btnEnviar)
        boton = findViewById(R.id.boton)

        getNodes()

        btnEnviar.setOnClickListener {
            if (deviceConnected) {
                sendMessage()
            } else {
                // Si aún no está conectado, intenta de nuevo y luego envía
                getNodesYEnviar()
            }
        }

        boton.setOnClickListener {
            val intent = Intent(this@MainActivity, Prueba::class.java)
            startActivity(intent)
        }
    }

    private fun getNodes() {
        launch(Dispatchers.Default) {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(activityContext!!).connectedNodes)
                if (nodes.isNotEmpty()) {
                    nodeID = nodes[0].id
                    deviceConnected = true
                    Log.d("NODO", "Conectado al nodo: $nodeID")
                    withContext(Dispatchers.Main) {
                        textoMensaje.text = "Conectado al celular"
                    }
                } else {
                    Log.d("NODO", "No se encontraron nodos")
                    withContext(Dispatchers.Main) {
                        textoMensaje.text = "Sin conexión"
                    }
                }
            } catch (e: Exception) {
                Log.d("NODO", "Error: ${e.message}")
            }
        }
    }

    private fun getNodesYEnviar() {
        launch(Dispatchers.Default) {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(activityContext!!).connectedNodes)
                if (nodes.isNotEmpty()) {
                    nodeID = nodes[0].id
                    deviceConnected = true
                    withContext(Dispatchers.Main) {
                        sendMessage()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activityContext, "No conectado al celular", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.d("NODO", "Error: ${e.message}")
            }
        }
    }

    private fun sendMessage() {
        val mensaje = editMensaje.text.toString()
        if (mensaje.isEmpty()) return
        Wearable.getMessageClient(activityContext!!)
            .sendMessage(nodeID, PAYLOAD_PATH, mensaje.toByteArray())
            .addOnSuccessListener {
                Log.d("sendMessage", "Mensaje enviado correctamente")
                runOnUiThread {
                    Toast.makeText(activityContext, "Enviado", Toast.LENGTH_SHORT).show()
                    editMensaje.setText("")
                }
            }
            .addOnFailureListener { e ->
                Log.d("sendMessage", "Error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(activityContext, "Error al enviar", Toast.LENGTH_SHORT).show()
                }
            }
    }

    override fun onMessageReceived(ME: MessageEvent) {
        val message = String(ME.data, StandardCharsets.UTF_8)
        Log.d("onMessageReceived", "Mensaje recibido: $message")
        runOnUiThread {
            textoMensaje.text = "Celular: $message"
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
