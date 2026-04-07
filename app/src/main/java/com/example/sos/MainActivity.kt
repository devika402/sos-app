package com.example.sosapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var btnSOS: Button
    private lateinit var btnDiscover: Button
    private var switchShake: Switch? = null
    private lateinit var tvStatus: TextView
    private lateinit var tvConnected: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvLastLocation: TextView
    private lateinit var tvReceivedDetails: TextView
    private lateinit var listDevices: ListView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var connectedSocket: BluetoothSocket? = null
    private var serverStarted = false

    private val deviceNames = ArrayList<String>()
    private val deviceAddresses = ArrayList<String>()
    private lateinit var deviceAdapter: ArrayAdapter<String>

    private var receivedLat: String? = null
    private var receivedLon: String? = null

    private lateinit var prefs: SharedPreferences

    private val appUUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastShakeTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("sos_prefs", Context.MODE_PRIVATE)

        btnDiscover = findViewById(R.id.btnDiscover)
        btnSOS = findViewById(R.id.btnSOS)
        tvStatus = findViewById(R.id.tvStatus)
        tvConnected = findViewById(R.id.tvConnected)
        tvLocation = findViewById(R.id.tvLocation)
        tvLastLocation = findViewById(R.id.tvLastLocation)
        tvReceivedDetails = findViewById(R.id.tvReceivedDetails)
        listDevices = findViewById(R.id.listDevices)

        // Safe lookup: works only if switch exists in XML
        switchShake = findViewById(R.id.switchShake)
        switchShake?.isChecked = true

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
        listDevices.adapter = deviceAdapter

        registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        checkPermissions()
        checkBluetooth()

        tvLastLocation.text = "Last Location: ${getLastLocation()}"

        btnDiscover.setOnClickListener {
            startDiscovery()
        }

        listDevices.setOnItemClickListener { _, _, position, _ ->
            val address = deviceAddresses[position]
            val device = bluetoothAdapter?.getRemoteDevice(address)
            if (device != null) {
                connectToDevice(device)
            }
        }

        btnSOS.setOnClickListener {
            val socket = connectedSocket
            if (socket != null && socket.isConnected) {
                sendSOSWithLocation(socket, "Manual SOS")
            } else {
                Toast.makeText(this, "Connect to device first", Toast.LENGTH_SHORT).show()
            }
        }

        tvReceivedDetails.setOnClickListener {
            val lat = receivedLat
            val lon = receivedLon
            if (lat != null && lon != null) {
                openLocationInGoogleMaps(lat, lon)
            } else {
                Toast.makeText(this, "No received location available", Toast.LENGTH_SHORT).show()
            }
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun getTime(): String {
        return SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
    }

    private fun getLastLocation(): String {
        return prefs.getString("last_location", "None") ?: "None"
    }

    private fun saveLastLocation(location: String) {
        prefs.edit().putString("last_location", location).apply()
    }

    private fun startDiscovery() {
        deviceNames.clear()
        deviceAddresses.clear()
        deviceAdapter.notifyDataSetChanged()

        bluetoothAdapter?.cancelDiscovery()
        bluetoothAdapter?.startDiscovery()

        tvStatus.text = "Discovering devices..."
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == BluetoothDevice.ACTION_FOUND) {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                if (device != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }

                    val name = try { device.name ?: "Unknown Device" } catch (_: Exception) { "Unknown Device" }
                    val address = device.address

                    if (!deviceAddresses.contains(address)) {
                        deviceNames.add("$name\n$address")
                        deviceAddresses.add(address)
                        deviceAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Thread {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    runOnUiThread {
                        tvStatus.text = "Bluetooth permission missing"
                    }
                    return@Thread
                }

                bluetoothAdapter?.cancelDiscovery()

                val socket = device.createRfcommSocketToServiceRecord(appUUID)
                socket.connect()
                connectedSocket = socket

                runOnUiThread {
                    val name = try { device.name ?: "Unknown" } catch (_: Exception) { "Unknown" }
                    tvStatus.text = "Connected successfully"
                    tvConnected.text = "Connected Device: $name"
                }

                listen(socket)
            } catch (_: Exception) {
                runOnUiThread {
                    tvStatus.text = "Connection failed"
                }
            }
        }.start()
    }

    private fun sendSOSWithLocation(socket: BluetoothSocket, trigger: String) {
        if (!hasLocationPermission()) {
            tvStatus.text = "Location permission missing"
            Toast.makeText(this, "Allow location permission", Toast.LENGTH_SHORT).show()
            return
        }

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(10000)
            .build()

        fusedLocationClient.getCurrentLocation(request, null)
            .addOnSuccessListener { location ->
                val time = getTime()
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude

                    val currentAddress = "$lat, $lon"
                    val lastLocation = getLastLocation()

                    tvStatus.text = "🚨 SOS SENT!"
                    tvLocation.text = "Received Location: $lat, $lon"
                    tvLastLocation.text = "Last Location: $lastLocation"

                    tvReceivedDetails.text = """
SOS Sent
Time: $time
Trigger: $trigger

Latitude: $lat
Longitude: $lon

Current Address:
$currentAddress

Last Location:
$lastLocation

Tap here to open in Google Maps
                    """.trimIndent()

                    receivedLat = lat.toString()
                    receivedLon = lon.toString()

                    Thread {
                        try {
                            val msg = "SOS|$time|$lat|$lon|$lastLocation\n"
                            socket.outputStream.write(msg.toByteArray())
                            socket.outputStream.flush()

                            saveLastLocation(currentAddress)

                            runOnUiThread {
                                Toast.makeText(this, "SOS sent with location", Toast.LENGTH_SHORT).show()
                            }
                        } catch (_: Exception) {
                            runOnUiThread {
                                tvStatus.text = "Send failed"
                            }
                        }
                    }.start()
                } else {
                    tvStatus.text = "Location unavailable"
                }
            }
            .addOnFailureListener {
                tvStatus.text = "Failed to get location"
            }
    }

    private fun listen(socket: BluetoothSocket) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                while (true) {
                    val msg = reader.readLine() ?: break
                    runOnUiThread {
                        handleMessage(msg)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!tvStatus.text.toString().contains("SOS RECEIVED")) {
                        tvStatus.text = "Connection closed"
                    }
                }
            }
        }.start()
    }

    private fun handleMessage(msg: String) {
        if (msg.startsWith("SOS|")) {
            val parts = msg.split("|")
            if (parts.size >= 5) {
                val time = parts[1]
                val lat = parts[2]
                val lon = parts[3]
                val lastLocation = parts[4]

                receivedLat = lat
                receivedLon = lon

                tvStatus.text = "🚨 SOS RECEIVED!"
                tvLocation.text = "Received Location: $lat, $lon"
                tvLastLocation.text = "Last Location: $lastLocation"

                tvReceivedDetails.text = """
SOS Received
Time: $time

Latitude: $lat
Longitude: $lon

Current Address:
$lat, $lon

Last Location:
$lastLocation

Tap here to open in Google Maps
                """.trimIndent()

                vibrate()
                playSound()

                Toast.makeText(this, "Emergency SOS received!", Toast.LENGTH_LONG).show()
            }
        } else {
            tvStatus.text = "Message: $msg"
        }
    }

    private fun openLocationInGoogleMaps(lat: String, lon: String) {
        try {
            val uri = Uri.parse("geo:0,0?q=$lat,$lon")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                val fallbackUri = Uri.parse("https://maps.google.com/?q=$lat,$lon")
                startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to open map", Toast.LENGTH_SHORT).show()
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 150, 500, 150, 700),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(1500)
        }
    }

    private fun playSound() {
        try {
            val mp = MediaPlayer.create(this, Settings.System.DEFAULT_ALARM_ALERT_URI)
            mp.setOnCompletionListener { it.release() }
            mp.start()
        } catch (_: Exception) {
        }
    }

    private fun startServer() {
        if (serverStarted) return
        serverStarted = true

        Thread {
            try {
                if (bluetoothAdapter == null) {
                    runOnUiThread {
                        tvStatus.text = "Bluetooth not supported"
                    }
                    serverStarted = false
                    return@Thread
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    serverStarted = false
                    return@Thread
                }

                val server: BluetoothServerSocket =
                    bluetoothAdapter!!.listenUsingRfcommWithServiceRecord("SOSApp", appUUID)

                runOnUiThread {
                    if (!tvStatus.text.toString().contains("SOS RECEIVED")) {
                        tvStatus.text = "Waiting for connection..."
                    }
                }

                while (true) {
                    val socket = server.accept()
                    connectedSocket = socket

                    runOnUiThread {
                        if (!tvStatus.text.toString().contains("SOS RECEIVED")) {
                            tvStatus.text = "Device connected"
                        }
                    }

                    listen(socket)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!tvStatus.text.toString().contains("SOS RECEIVED")) {
                        tvStatus.text = "Server stopped"
                    }
                }
            }
        }.start()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkBluetooth() {
        if (bluetoothAdapter == null) {
            tvStatus.text = "Bluetooth not supported"
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            return
        }

        if (bluetoothAdapter?.isEnabled == true) {
            startServer()
        } else {
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (switchShake?.isChecked == false) return
        if (event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val acceleration = sqrt((x * x + y * y + z * z).toDouble())
        val currentTime = System.currentTimeMillis()

        if (acceleration > 15 && currentTime - lastShakeTime > 2000) {
            lastShakeTime = currentTime

            Toast.makeText(this, "Shake detected! Sending SOS...", Toast.LENGTH_SHORT).show()

            val socket = connectedSocket
            if (socket != null && socket.isConnected) {
                sendSOSWithLocation(socket, "Shake Detection")
            } else {
                tvStatus.text = "Shake detected, but no device connected"
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }
}