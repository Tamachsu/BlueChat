package com.example.bluechat.model

import android.bluetooth.BluetoothDevice

data class BluetoothDeviceModel(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val isPaired: Boolean,
    val statusText: String
)
