package com.example.textoapp.presentation

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.textoapp.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val button: Button = findViewById(R.id.boton)
        button.setOnClickListener {
            Toast.makeText(this, "¡Botón presionado!", Toast.LENGTH_SHORT).show()
        }
    }
}
