package com.example.bluechat

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bluechat.adapter.DeviceAdapter
import com.example.bluechat.model.BluetoothDeviceModel
import com.example.bluechat.service.BluetoothEventListenerAdapter
import com.example.bluechat.service.BluetoothService
import com.example.bluechat.service.ConnectionState

@SuppressLint("MissingPermission")
class DispositivosActivity : AppCompatActivity() {

    private val bluetoothAdapter: BluetoothAdapter? = try {
        BluetoothAdapter.getDefaultAdapter()
    } catch (e: Exception) {
        null
    }

    private lateinit var txtStatusBluetooth: TextView
    private lateinit var btnProcurar: Button
    private lateinit var btnFicarVisivel: Button
    private lateinit var txtStatusProcura: TextView

    private lateinit var rvEmparelhados: RecyclerView
    private lateinit var rvEncontrados: RecyclerView
    private lateinit var containerEmparelhadosExemplo: LinearLayout
    private lateinit var containerEncontradosExemplo: LinearLayout

    private lateinit var adapterEmparelhados: DeviceAdapter
    private lateinit var adapterEncontrados: DeviceAdapter

    private val pairedList = mutableListOf<BluetoothDeviceModel>()
    private val discoveredList = mutableListOf<BluetoothDeviceModel>()

    private var pendingAction: (() -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            initBluetooth()
            pendingAction?.invoke()
            pendingAction = null
        } else {
            Toast.makeText(this, "Permissões de Bluetooth necessárias", Toast.LENGTH_SHORT).show()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let {
                        val name = try { it.name ?: "Sem nome" } catch (e: SecurityException) { "Sem nome" }
                        val address = it.address
                        val exists = discoveredList.any { d -> d.address == address }
                        if (!exists) {
                            val statusText = if (name == "Sem nome") "mostra o endereço MAC: $address" else "não emparelhado"
                            discoveredList.add(BluetoothDeviceModel(it, name, address, false, statusText))
                            containerEncontradosExemplo.visibility = View.GONE
                            adapterEncontrados.notifyDataSetChanged()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    txtStatusProcura.text = "concluído"
                }
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> {
                    val mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR)
                    if (mode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                        Toast.makeText(this@DispositivosActivity, "Dispositivo visível por 300s", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val bluetoothServiceListener = object : BluetoothEventListenerAdapter() {
        override fun onStateChanged(state: ConnectionState, connectedDeviceName: String?) {
            when (state) {
                ConnectionState.LISTEN -> {
                    txtStatusBluetooth.text = "Bluetooth ligado · à escuta"
                    txtStatusBluetooth.setBackgroundResource(R.drawable.bg_status_bluetooth_green)
                }
                ConnectionState.CONNECTING -> {
                    txtStatusBluetooth.text = "A ligar a ${connectedDeviceName ?: "dispositivo"}..."
                }
                ConnectionState.CONNECTED -> {
                    txtStatusBluetooth.text = "Ligado a ${connectedDeviceName ?: "dispositivo"}"
                    val intent = Intent(this@DispositivosActivity, ConversaActivity::class.java)
                    intent.putExtra("NOME_CONTACTO", connectedDeviceName ?: "Remoto")
                    startActivity(intent)
                }
                ConnectionState.NONE -> {
                    txtStatusBluetooth.text = "Bluetooth desligado"
                }
            }
        }

        override fun onError(errorMessage: String) {
            Toast.makeText(this@DispositivosActivity, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dispositivos)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        checkAndRequestPermissions()
    }

    private fun initViews() {
        txtStatusBluetooth = findViewById(R.id.txtStatusBluetooth)
        btnProcurar = findViewById(R.id.btnProcurar)
        btnFicarVisivel = findViewById(R.id.btnFicarVisivel)
        txtStatusProcura = findViewById(R.id.txtStatusProcura)

        rvEmparelhados = findViewById(R.id.rvEmparelhados)
        rvEncontrados = findViewById(R.id.rvEncontrados)
        containerEmparelhadosExemplo = findViewById(R.id.containerEmparelhadosExemplo)
        containerEncontradosExemplo = findViewById(R.id.containerEncontradosExemplo)

        rvEmparelhados.layoutManager = LinearLayoutManager(this)
        rvEncontrados.layoutManager = LinearLayoutManager(this)

        adapterEmparelhados = DeviceAdapter(pairedList) { deviceModel ->
            connectToDevice(deviceModel)
        }
        adapterEncontrados = DeviceAdapter(discoveredList) { deviceModel ->
            connectToDevice(deviceModel)
        }

        rvEmparelhados.adapter = adapterEmparelhados
        rvEncontrados.adapter = adapterEncontrados

        btnProcurar.setOnClickListener {
            startDiscoveryProcess()
        }

        btnFicarVisivel.setOnClickListener {
            makeDiscoverable()
        }

        // Keep preview clickable as fallback for emulator / testing
        containerEmparelhadosExemplo.setOnClickListener {
            val intent = Intent(this, ConversaActivity::class.java)
            intent.putExtra("NOME_CONTACTO", "Ana")
            startActivity(intent)
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            initBluetooth()
        }
    }

    private fun initBluetooth() {
        if (!hasBluetoothPermissions()) return

        try {
            if (bluetoothAdapter == null) {
                Toast.makeText(this, "Bluetooth não disponível neste dispositivo", Toast.LENGTH_SHORT).show()
                return
            }

            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivity(enableBtIntent)
            }

            loadPairedDevices()
            BluetoothService.startListening()

            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
            }
            try { unregisterReceiver(bluetoothReceiver) } catch (e: Exception) {}
            registerReceiver(bluetoothReceiver, filter)
        } catch (e: SecurityException) {
            Log.e("DispositivosActivity", "SecurityException in initBluetooth", e)
            Toast.makeText(this, "Permissão de Bluetooth necessária", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("DispositivosActivity", "Exception in initBluetooth", e)
        }
    }

    private fun loadPairedDevices() {
        if (!hasBluetoothPermissions()) return

        try {
            pairedList.clear()
            val bonded = bluetoothAdapter?.bondedDevices
            if (!bonded.isNullOrEmpty()) {
                containerEmparelhadosExemplo.visibility = View.GONE
                for (device in bonded) {
                    val name = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
                    pairedList.add(BluetoothDeviceModel(device, name, device.address, true, "emparelhado"))
                }
                adapterEmparelhados.notifyDataSetChanged()
            } else {
                containerEmparelhadosExemplo.visibility = View.VISIBLE
            }
        } catch (e: SecurityException) {
            containerEmparelhadosExemplo.visibility = View.VISIBLE
        } catch (e: Exception) {
            Log.e("DispositivosActivity", "Error loading paired devices", e)
        }
    }

    private fun startDiscoveryProcess() {
        if (!hasBluetoothPermissions()) {
            pendingAction = { performStartDiscovery() }
            checkAndRequestPermissions()
            return
        }
        performStartDiscovery()
    }

    private fun performStartDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
            discoveredList.clear()
            adapterEncontrados.notifyDataSetChanged()
            txtStatusProcura.text = "a procurar..."

            val success = bluetoothAdapter?.startDiscovery()
            if (success == true) {
                Toast.makeText(this, "A procurar dispositivos...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Não foi possível iniciar a procura", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permissão de Bluetooth necessária", Toast.LENGTH_SHORT).show()
        }
    }

    private fun makeDiscoverable() {
        if (!hasBluetoothPermissions()) {
            pendingAction = { performMakeDiscoverable() }
            checkAndRequestPermissions()
            return
        }
        performMakeDiscoverable()
    }

    private fun performMakeDiscoverable() {
        try {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
            startActivity(discoverableIntent)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permissão de Bluetooth necessária para ficar visível", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao alterar visibilidade", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToDevice(deviceModel: BluetoothDeviceModel) {
        if (!hasBluetoothPermissions()) {
            pendingAction = { connectToDevice(deviceModel) }
            checkAndRequestPermissions()
            return
        }

        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
            BluetoothService.connect(deviceModel.device)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permissão de Bluetooth necessária", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        BluetoothService.registerListener(bluetoothServiceListener)
        if (hasBluetoothPermissions()) {
            if (BluetoothService.currentState == ConnectionState.NONE) {
                BluetoothService.startListening()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        BluetoothService.unregisterListener(bluetoothServiceListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
}
