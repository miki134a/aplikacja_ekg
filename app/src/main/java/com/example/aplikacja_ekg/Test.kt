package com.example.aplikacja_ekg

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class Test : AppCompatActivity() {

    private lateinit var chart: LineChart
    private lateinit var dataSet: LineDataSet
    private lateinit var lineData: LineData

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isReadingData = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.acrivity_real_time_chart)

        // Pobierz referencję do widoku, np. RelativeLayout lub innego rodzaju widoku
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

        chart = findViewById(R.id.chart)
        setupChart()

        if (checkLocationPermission()) {
            startBluetoothConnection()
        } else {
            requestLocationPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBluetoothConnection()
    }

    private fun setupChart() {
        // Konfiguracja wykresu
        chart.setTouchEnabled(false)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)
        chart.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
        val legend = chart.legend
        legend.form = Legend.LegendForm.NONE

        val description = Description()
        description.text = ""
        chart.description = description

        // Konfiguracja danych
        dataSet = LineDataSet(null, null,   )
        //dataSet.axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT

        val emptyFormatter = object : ValueFormatter() {
            override fun getPointLabel(entry: Entry?): String {
                return "" // Pusty marker
            }
        }

        dataSet.color = Color.RED
        dataSet.lineWidth = 2f
        dataSet.isHighlightEnabled = false
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawValues(false)
        dataSet.setDrawCircles(false)

        lineData = LineData()
        lineData.addDataSet(dataSet)

        chart.data = lineData
        val minY = 1750F
        val maxY = 4000F
        chart.axisLeft.axisMinimum = minY
        chart.axisLeft.axisMaximum = maxY
        chart.xAxis.isEnabled = false // Wyłącz osie X
        chart.axisLeft.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART) // Ustaw pozycję osi Y na zewnątrz wykresu
        chart.axisRight.isEnabled = false // Wyłącz osie Y po prawej stronie
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        chart.axisLeft.setCenterAxisLabels(true) // Wyśrodkuj etykiety na osi Y

// Pobierz referencje do osi X i Y
        val xAxis = chart.xAxis
        val yAxis = chart.axisLeft

// Wyłącz widoczność osi X i Y
        xAxis.isEnabled = false
        yAxis.isEnabled = false
// Wyłącz widoczność etykiet osi Y
        yAxis.setDrawLabels(false)
// Ustaw rozmiar wykresu
        chart.layoutParams.width = screenHeight-100
        chart.layoutParams.height = screenWidth-5000
        chart.invalidate()

        val temp = chart.lineData.getDataSetByIndex(0) as LineDataSet
        temp.valueFormatter = emptyFormatter

    }

    private fun addEntry(value: Float) {
        if (value < 1500 || value > 4000) return;
        val dataSets: List<ILineDataSet>? = chart.data?.dataSets
        if (dataSets != null && dataSets.isNotEmpty()) {
            dataSet = dataSets[0] as LineDataSet
            val entry = Entry(dataSet.entryCount.toFloat(), value)

            lineData.addEntry(entry, 0)
            lineData.notifyDataChanged()
            chart.notifyDataSetChanged()
            chart.setVisibleXRangeMaximum(512f)
            chart.moveViewToX(lineData.entryCount.toFloat() - 512f)
        }
    }

    private fun checkLocationPermission(): Boolean {
        val permissionResult = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        return permissionResult == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun startBluetoothConnection() {
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        if (pairedDevices != null && pairedDevices.isNotEmpty()) {
            // Wybierz urządzenie Bluetooth z listy sparowanych urządzeń
            val receivedIntent = intent
            val device: BluetoothDevice? = receivedIntent.getParcelableExtra("device")
            // Utwórz socket Bluetooth
            try {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    RequestPermission()
                }
                if (device != null) {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                }
                bluetoothSocket?.connect()
                inputStream = bluetoothSocket?.inputStream

                isReadingData = true
                readBluetoothData()
            } catch (e: IOException) {
                Log.e(TAG, "Błąd podczas nawiązywania połączenia Bluetooth", e)
            }
        }
    }

    private fun stopBluetoothConnection() {
        isReadingData = false

        try {
            inputStream?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Błąd podczas zamykania strumienia wejściowego Bluetooth", e)
        }

        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Błąd podczas zamykania połączenia Bluetooth", e)
        }
    }

    private fun readBluetoothData() {
        val buffer = ByteArray(4)
        var bytes: Int

        Thread {
            while (isReadingData) {
                try {
                    bytes = inputStream?.read(buffer) ?: 0
                    val data = String(buffer, 0, bytes)
                    // Przetwarzanie otrzymanych danych Bluetooth
                    val value = data.toFloat()
                    handler.post { addEntry(value) }
                } catch (e: IOException) {
                    Log.e(TAG, "Błąd podczas czytania danych Bluetooth", e)
                    break
                }
            }
        }.start()
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val TAG = "RealTimeChartActivity"
        private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    override fun onBackPressed() {
        bluetoothSocket?.close()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }
}
