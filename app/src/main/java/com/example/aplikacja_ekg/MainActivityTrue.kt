package com.example.aplikacja_ekg

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.util.UUID

const val DEBUG_TAG = "DEBUG"
const val DEFAULT_UUID = "00001101-0000-1000-8000-00805F9B34FB"

class MainActivityTrue : AppCompatActivity() {

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var mDevice: BluetoothDevice? = null
    private var mConnectedThread: ConnectedThread? = null
    private var mConnectThread: ConnectThread? = null

    private val DEVICE_UUID: UUID = UUID.fromString(DEFAULT_UUID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.final_view)

        val rootView = window.decorView.findViewById<View>(android.R.id.content)

        // Ukryj pasek menu z dołu
        rootView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED) {
            getPermissions()
        } else {
            initializeBT()
        }
    }

    private fun getPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 2)
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        }
    }

    private var requestBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(DEBUG_TAG, "Granted")
            initializeBT()
        }else{
            Log.d(DEBUG_TAG, "Denied")
            Toast.makeText(this, "Permissions denied", Toast.LENGTH_LONG)
        }
    }


    @SuppressLint("MissingPermission")
    private fun initializeBT() {
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager?.adapter
        val DEVICE_MAC = intent.getStringExtra("deviceAddress")
        val DEVICE_NAME = intent.getStringExtra("deviceName")

        if (bluetoothAdapter == null) {
            return;
        }
        val pairedDevices = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            if(device.name.equals(DEVICE_NAME) && device.address.equals(DEVICE_MAC)) {
                mDevice = device;
                return@forEach
            }
        }
        if (mDevice != null) {
            connect()
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {

        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmBuffer: ByteArray = ByteArray(15)

        override fun run() {
            var numBytes: Int

            while (true) {
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    Log.d(DEBUG_TAG, "Input stream was disconnected", e)
                    break
                }
                Log.d(DEBUG_TAG, String(mmBuffer, 0, numBytes))
//
//               var start: Int? = null
//                var stop: Int? = null
//                for((i,v) in mmBuffer.withIndex()) {
//                    if(v == 'W'.code.toByte())
//                        start = i
//                    else if(v == '.'.code.toByte()) {
//                        stop = i
//                        if (start != null && start < stop) {
//                            Log.d(DEBUG_TAG, String(mmBuffer, start, stop-start))
//                        }
//                    }
 //               }
            }
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(DEBUG_TAG, "Could not close the connect socket", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(DEVICE_UUID)
        }

        override fun run() {
            mmSocket?.let { socket ->
                socket.connect()
                connected(socket)
            }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(DEBUG_TAG, "Could not close the client socket", e)
            }
        }
    }


    @Synchronized
    fun connected(socket: BluetoothSocket?) {
        mConnectedThread = ConnectedThread(socket!!)
        mConnectedThread!!.start()
    }

    @Synchronized
    fun connect() {
        mConnectThread = ConnectThread(mDevice!!)
        mConnectThread!!.start()
    }
}
