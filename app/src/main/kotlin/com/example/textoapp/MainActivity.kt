package com.example.textoapp

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var editText: EditText
    private lateinit var textView: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editText = findViewById(R.id.editText)
        textView = findViewById(R.id.textView)
        button = findViewById(R.id.button)

        button.setOnClickListener {
            val animation = AnimationUtils.loadAnimation(this, R.anim.scale_button)
            button.startAnimation(animation)
            
            val texto = editText.text.toString()
            val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
            
            if (texto.isNotEmpty()) {
                textView.text = texto
                val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
                textView.startAnimation(fadeIn)
                
                AlertDialog.Builder(this)
                    .setTitle("Éxito")
                    .setMessage("El texto se procesó correctamente.")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
                    .window?.decorView?.startAnimation(slideUp)
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Aviso")
                    .setMessage("Ingresa un texto antes de continuar.")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
                    .window?.decorView?.startAnimation(slideUp)
            }
        }
    }
}
