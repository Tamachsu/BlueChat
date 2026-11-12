package com.example.bluechat.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.bluechat.model.ChatMessage
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ConnectionState {
    NONE,
    LISTEN,
    CONNECTING,
    CONNECTED
}

interface BluetoothServiceListener {
    fun onStateChanged(state: ConnectionState, connectedDeviceName: String?)
    fun onMessageReceived(message: ChatMessage)
    fun onError(errorMessage: String)
}

abstract class BluetoothEventListenerAdapter : BluetoothServiceListener {
    override fun onStateChanged(state: ConnectionState, connectedDeviceName: String?) {}
    override fun onMessageReceived(message: ChatMessage) {}
    override fun onError(errorMessage: String) {}
}

@SuppressLint("MissingPermission")
object BluetoothService {
    private const val TAG = "BlueChat_Service"
    private const val NAME_SECURE = "BlueChat"
    private val MY_UUID_SECURE: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var adapter: BluetoothAdapter? = try {
        BluetoothAdapter.getDefaultAdapter()
    } catch (e: Exception) {
        null
    }

    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    var currentState: ConnectionState = ConnectionState.NONE
        private set

    var connectedDeviceName: String? = null
        private set

    private val listeners = mutableSetOf<BluetoothServiceListener>()
    private val handler = Handler(Looper.getMainLooper())

    fun registerListener(listener: BluetoothServiceListener) {
        listeners.add(listener)
        listener.onStateChanged(currentState, connectedDeviceName)
    }

    fun unregisterListener(listener: BluetoothServiceListener) {
        listeners.remove(listener)
    }

    private fun updateState(state: ConnectionState, deviceName: String? = connectedDeviceName) {
        currentState = state
        connectedDeviceName = deviceName
        handler.post {
            listeners.forEach { it.onStateChanged(state, deviceName) }
        }
    }

    @Synchronized
    fun startListening() {
        try {
            Log.d(TAG, "startListening: CURRENT = $currentState")

            // Cancel any thread attempting to make a connection
            connectThread?.cancel()
            connectThread = null

            // Cancel any thread currently running a connection
            connectedThread?.cancel()
            connectedThread = null

            if (acceptThread == null) {
                acceptThread = AcceptThread()
                acceptThread?.start()
            }
            updateState(ConnectionState.LISTEN, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in startListening", e)
            handler.post {
                listeners.forEach { it.onError("Permissão de Bluetooth necessária") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in startListening", e)
        }
    }

    @Synchronized
    fun connect(device: BluetoothDevice) {
        try {
            val deviceName = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
            Log.d(TAG, "connect to: $deviceName")

            if (currentState == ConnectionState.CONNECTING) {
                connectThread?.cancel()
                connectThread = null
            }

            connectedThread?.cancel()
            connectedThread = null

            connectThread = ConnectThread(device)
            connectThread?.start()
            updateState(ConnectionState.CONNECTING, deviceName)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in connect", e)
            handler.post {
                listeners.forEach { it.onError("Permissão de Bluetooth necessária") }
            }
        }
    }

    @Synchronized
    fun connected(socket: BluetoothSocket, device: BluetoothDevice) {
        try {
            val deviceName = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
            Log.d(TAG, "connected to $deviceName")

            connectThread?.cancel()
            connectThread = null

            connectedThread?.cancel()
            connectedThread = null

            acceptThread?.cancel()
            acceptThread = null

            connectedThread = ConnectedThread(socket)
            connectedThread?.start()

            updateState(ConnectionState.CONNECTED, deviceName)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in connected", e)
        }
    }

    @Synchronized
    fun stop() {
        Log.d(TAG, "stop service")
        connectThread?.cancel()
        connectThread = null

        connectedThread?.cancel()
        connectedThread = null

        acceptThread?.cancel()
        acceptThread = null

        updateState(ConnectionState.NONE, null)
    }

    fun write(out: ByteArray) {
        var r: ConnectedThread?
        synchronized(this) {
            if (currentState != ConnectionState.CONNECTED) return
            r = connectedThread
        }
        r?.write(out)
    }

    fun sendMessage(text: String, senderName: String): ChatMessage? {
        if (currentState != ConnectionState.CONNECTED) return null
        val fullPayload = "$senderName::$text"
        val bytes = fullPayload.toByteArray(Charsets.UTF_8)
        write(bytes)

        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        return ChatMessage(
            sender = senderName,
            text = text,
            timestamp = time,
            isSent = true,
            status = "✓"
        )
    }

    private fun connectionFailed() {
        handler.post {
            listeners.forEach { it.onError("Não foi possível ligar ao dispositivo") }
        }
        startListening()
    }

    private fun connectionLost() {
        handler.post {
            listeners.forEach { it.onError("Ligação perdida") }
        }
        startListening()
    }

    private class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            try {
                adapter?.listenUsingRfcommWithServiceRecord(NAME_SECURE, MY_UUID_SECURE)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException creating server socket", e)
                null
            } catch (e: IOException) {
                Log.e(TAG, "Socket listen() failed", e)
                null
            }
        }

        override fun run() {
            var socket: BluetoothSocket?
            while (currentState != ConnectionState.CONNECTED) {
                socket = try {
                    mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket accept() failed", e)
                    break
                }

                if (socket != null) {
                    synchronized(BluetoothService) {
                        when (currentState) {
                            ConnectionState.LISTEN, ConnectionState.CONNECTING -> {
                                connected(socket, socket.remoteDevice)
                            }
                            ConnectionState.NONE, ConnectionState.CONNECTED -> {
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(TAG, "Could not close unwanted socket", e)
                                }
                            }
                        }
                    }
                }
            }
        }

        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Could not close server socket", e)
            }
        }
    }

    private class ConnectThread(private val mmDevice: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            try {
                mmDevice.createRfcommSocketToServiceRecord(MY_UUID_SECURE)
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException creating socket", e)
                null
            } catch (e: IOException) {
                Log.e(TAG, "Socket create() failed", e)
                null
            }
        }

        override fun run() {
            try {
                adapter?.cancelDiscovery()
            } catch (e: SecurityException) {
                Log.e(TAG, "cancelDiscovery failed", e)
            }

            val socket = mmSocket ?: return
            try {
                socket.connect()
            } catch (e: IOException) {
                try {
                    socket.close()
                } catch (e2: IOException) {
                    Log.e(TAG, "unable to close socket during connection failure", e2)
                }
                connectionFailed()
                return
            }

            synchronized(BluetoothService) {
                connectThread = null
            }

            connected(socket, mmDevice)
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }
    }

    private class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream? = try { mmSocket.inputStream } catch (e: IOException) { null }
        private val mmOutStream: OutputStream? = try { mmSocket.outputStream } catch (e: IOException) { null }

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int

            while (currentState == ConnectionState.CONNECTED) {
                try {
                    bytes = mmInStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val rawMsg = String(buffer, 0, bytes, Charsets.UTF_8)
                        val parts = rawMsg.split("::", limit = 2)
                        val sender = if (parts.size > 1) parts[0] else connectedDeviceName ?: "Remoto"
                        val content = if (parts.size > 1) parts[1] else rawMsg
                        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                        val chatMsg = ChatMessage(
                            sender = sender,
                            text = content,
                            timestamp = time,
                            isSent = false,
                            status = ""
                        )

                        handler.post {
                            listeners.forEach { it.onMessageReceived(chatMsg) }
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "disconnected", e)
                    connectionLost()
                    break
                }
            }
        }

        fun write(buffer: ByteArray) {
            try {
                mmOutStream?.write(buffer)
            } catch (e: IOException) {
                Log.e(TAG, "Exception during write", e)
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }
    }
}
