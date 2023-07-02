package com.example.aplikacja_ekg

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.util.UUID


const val MESSAGE_READ: Int = 0

class MyBluetoothService(private val handler: Handler) {

    private var mConnectedThread: ConnectedThread? = null
    private var mHandler: Handler? = handler

    val DEVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")


    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {

        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmBuffer: ByteArray = ByteArray(1024)

        override fun run() {
            var numBytes: Int

            while (true) {
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    Log.d(DEBUG_TAG, "Input stream was disconnected", e)
                    break
                }

                val readMsg = mHandler?.obtainMessage(
                    MESSAGE_READ, numBytes, -1,
                    mmBuffer)
                readMsg?.sendToTarget()
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
    private inner class ConnectThread(device: BluetoothDevice, context: Context) : Thread() {

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

}