package com.example.textoapp.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import com.example.textoapp.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val boton: Button = findViewById(R.id.boton)

        boton.setOnClickListener {
            val intent = Intent(this@MainActivity, Prueba::class.java)
            startActivity(intent)
        }
    }
}
