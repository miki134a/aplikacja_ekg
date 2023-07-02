package com.example.aplikacja_ekg

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.ACTION_PAIRING_REQUEST
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.util.UUID


class PlotData : AppCompatActivity() {

    lateinit var btAdapter: BluetoothAdapter
    lateinit var btReceiver: BroadcastReceiver

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.plot_data)
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


        //
        val permissions = arrayOf(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADMIN
        )

        ActivityCompat.requestPermissions(this, permissions, 42)

        btAdapter = BluetoothAdapter.getDefaultAdapter()
        lateinit var finalDevice: BluetoothDevice

        if (btAdapter.isEnabled) {
            val devices = btAdapter.bondedDevices
            var paired = false
            for (device in devices) {
                if (device.name == "MODUL EKG") {
                    paired = true
                    finalDevice = device
                    break
                }
            }
            if (paired) {
                val context: Context = applicationContext // Aktualny kontekst aplikacji
                connectBluetooth(finalDevice, context)
            } else {

                Log.v("START", "Skanowanie")
                val context: Context = applicationContext // Aktualny kontekst aplikacji
                val scanner = BluetoothScanner(this)
                scanner.startScan { device ->
                    Log.v("MEANWHILE", "ZNALEZIONO ${device.name}")
                    if (device.name == "MODUL EKG") {
                        Log.v("UPDATE", "ZNALEZIONO URZĄDZENIE $device.name")
                        pairDevice(device, context)
                        Log.v("UPDATE", "POMYŚLNIE SPAROWANO!")
                        connectBluetooth(device, context)
                    }
                }
            }
        }
    }

    private fun pairDevice(device: BluetoothDevice, context: Context) {
        val helper = BluetoothPairingHelper()
        helper.pairWithBluetoothDevice(device, context)
    }

    private fun connectBluetooth(device: BluetoothDevice, context: Context) {

        val intent = Intent(this,Test::class.java)
        intent.putExtra("device",device)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            RequestPermission()
        }
        intent.putExtra("device",device)
        startActivity(intent)
    }

    class BluetoothScanner(private val context: Context) {
        private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        private var scanReceiver: BroadcastReceiver? = null
        private var scanCallback: ((BluetoothDevice) -> Unit)? = null

        fun startScan(callback: (BluetoothDevice) -> Unit) {
            scanCallback = callback

            // Sprawdź, czy urządzenie obsługuje Bluetooth
            if (bluetoothAdapter == null) {
                println("Urządzenie nie obsługuje Bluetooth.")
                return
            }

            // Sprawdź, czy Bluetooth jest włączone
            if (!bluetoothAdapter.isEnabled) {
                println("Bluetooth jest wyłączony. Włącz Bluetooth i spróbuj ponownie.")
                return
            }

            // Rozpocznij skanowanie urządzeń Bluetooth
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                RequestPermission()
            }
            bluetoothAdapter.startDiscovery()

            // Zarejestruj odbiornik zdarzeń skanowania urządzeń Bluetooth
            scanReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val action = intent.action
                    if (BluetoothDevice.ACTION_FOUND == action) {
                        val device =
                            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null) {
                            scanCallback?.invoke(device)
                        }
                    }
                }
            }

            // Zarejestruj odbiornik zdarzeń
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(scanReceiver, filter)
        }

        fun stopScan() {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                RequestPermission()
            }
            bluetoothAdapter?.cancelDiscovery()
            scanCallback = null
            scanReceiver?.let { context.unregisterReceiver(it) }
        }
    }
    class BluetoothPairingHelper {
        private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        fun pairWithBluetoothDevice(device: BluetoothDevice, context: Context) {
            // Sprawdź, czy urządzenie obsługuje Bluetooth
            if (bluetoothAdapter == null) {
                Log.v("WARN!", "Urządzenie nie obsługuje Bluetooth.")
                return
            }

            // Sprawdź, czy Bluetooth jest włączone
            if (!bluetoothAdapter.isEnabled) {
                Log.v("WARN!", "Bluetooth jest wyłączony. Włącz Bluetooth i spróbuj ponownie.")
                return
            }

            // Sprawdź, czy urządzenie jest już sparowane
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                RequestPermission()
            }
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                Log.v("UPDATE", "Urządzenie jest już sparowane.")
                return
            }

            // Parowanie z urządzeniem
            try {
                // Wywołaj metodę do parowania urządzenia Bluetooth
                device.let {
                    device.createBond()
                }
                Log.v("UPDATE", "UDAŁO SIĘ SPAROWAĆ")
            } catch (e: Exception) {
                Log.v("UPS", "NIE SPAROWANO")
            }
        }
    }
}



