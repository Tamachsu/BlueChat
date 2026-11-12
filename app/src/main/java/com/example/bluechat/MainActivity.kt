package com.example.bluechat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "BlueChatPrefs"
        const val KEY_ALCUNHA = "ALCUNHA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alcunhaSalva = prefs.getString(KEY_ALCUNHA, null)

        val btnContinuar = findViewById<Button>(R.id.btnContinuar)
        val etAlcunha = findViewById<EditText>(R.id.etAlcunha)

        if (!alcunhaSalva.isNullOrEmpty()) {
            etAlcunha?.setText(alcunhaSalva)
        }

        btnContinuar?.setOnClickListener {
            val alcunha = etAlcunha?.text?.toString()?.trim()
            if (alcunha.isNullOrEmpty()) {
                Toast.makeText(this, "Por favor insira uma alcunha", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putString(KEY_ALCUNHA, alcunha).apply()
                val intent = Intent(this, DispositivosActivity::class.java)
                intent.putExtra(KEY_ALCUNHA, alcunha)
                startActivity(intent)
            }
        }
    }
}
