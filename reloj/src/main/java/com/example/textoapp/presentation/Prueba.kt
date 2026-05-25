package com.example.textoapp.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.textoapp.R
import android.media.MediaPlayer
import android.widget.Button

class Prueba : ComponentActivity() {

    private lateinit var mediaPlayer: MediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.prueba)

        mediaPlayer = MediaPlayer.create(this, R.raw.wiwiwi)

        val boton: Button = findViewById(R.id.boton);

        boton.setOnClickListener {
            if (!mediaPlayer.isPlaying) {
                mediaPlayer.start()
            }
        }
    }
}