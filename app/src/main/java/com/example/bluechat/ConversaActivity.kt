package com.example.bluechat

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bluechat.adapter.MessageAdapter
import com.example.bluechat.model.ChatMessage
import com.example.bluechat.service.BluetoothEventListenerAdapter
import com.example.bluechat.service.BluetoothService
import com.example.bluechat.service.ConnectionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversaActivity : AppCompatActivity() {

    private lateinit var txtNomeContacto: TextView
    private lateinit var txtStatusContacto: TextView
    private lateinit var txtStatusLigacao: TextView
    private lateinit var rvMensagens: RecyclerView
    private lateinit var containerMensagensExemplo: LinearLayout
    private lateinit var etMensagem: EditText
    private lateinit var btnEnviar: ImageButton

    private val messageAdapter = MessageAdapter()
    private var myNickname: String = "Eu"

    private val bluetoothListener = object : BluetoothEventListenerAdapter() {
        override fun onMessageReceived(message: ChatMessage) {
            containerMensagensExemplo.visibility = View.GONE
            messageAdapter.addMessage(message)
            rvMensagens.smoothScrollToPosition(messageAdapter.itemCount - 1)
        }

        override fun onStateChanged(state: ConnectionState, connectedDeviceName: String?) {
            if (state == ConnectionState.CONNECTED) {
                txtStatusContacto.text = "ligado"
                txtStatusLigacao.text = "Ligado a ${connectedDeviceName ?: "Remoto"} · ${getCurrentTime()}"
            } else if (state == ConnectionState.LISTEN || state == ConnectionState.NONE) {
                txtStatusContacto.text = "desligado"
                Toast.makeText(this@ConversaActivity, "Ligação terminada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_conversa)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        myNickname = prefs.getString(MainActivity.KEY_ALCUNHA, "Eu") ?: "Eu"

        val nomeContacto = intent.getStringExtra("NOME_CONTACTO")
            ?: BluetoothService.connectedDeviceName
            ?: "Ana"

        initViews(nomeContacto)
    }

    private fun initViews(nomeContacto: String) {
        txtNomeContacto = findViewById(R.id.txtNomeContacto)
        txtStatusContacto = findViewById(R.id.txtStatusContacto)
        txtStatusLigacao = findViewById(R.id.txtStatusLigacao)
        rvMensagens = findViewById(R.id.rvMensagens)
        containerMensagensExemplo = findViewById(R.id.containerMensagensExemplo)
        etMensagem = findViewById(R.id.etMensagem)
        btnEnviar = findViewById(R.id.btnEnviar)

        txtNomeContacto.text = nomeContacto
        txtStatusLigacao.text = "Ligado a $nomeContacto · ${getCurrentTime()}"

        rvMensagens.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMensagens.adapter = messageAdapter

        btnEnviar.setOnClickListener {
            val texto = etMensagem.text.toString().trim()
            if (texto.isNotEmpty()) {
                val sentMsg = BluetoothService.sendMessage(texto, myNickname)
                if (sentMsg != null) {
                    containerMensagensExemplo.visibility = View.GONE
                    messageAdapter.addMessage(sentMsg)
                    rvMensagens.smoothScrollToPosition(messageAdapter.itemCount - 1)
                    etMensagem.text.clear()
                } else {
                    // Fallback for simulation / preview if not connected via real hardware
                    containerMensagensExemplo.visibility = View.GONE
                    val fallbackMsg = ChatMessage(
                        sender = myNickname,
                        text = texto,
                        timestamp = getCurrentTime(),
                        isSent = true,
                        status = "✓"
                    )
                    messageAdapter.addMessage(fallbackMsg)
                    rvMensagens.smoothScrollToPosition(messageAdapter.itemCount - 1)
                    etMensagem.text.clear()
                }
            }
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    override fun onResume() {
        super.onResume()
        BluetoothService.registerListener(bluetoothListener)
    }

    override fun onPause() {
        super.onPause()
        BluetoothService.unregisterListener(bluetoothListener)
    }
}
