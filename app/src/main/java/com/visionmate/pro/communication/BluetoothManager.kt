package com.visionmate.pro.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.visionmate.pro.model.ConnectionStatus
import com.visionmate.pro.model.SensorData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.io.InputStream
import java.util.UUID

interface BluetoothCaneManager {
    val connectionStatus: StateFlow<ConnectionStatus>
    val sensorDataFlow: StateFlow<SensorData>
    val sosSignalEvents: SharedFlow<Unit>
    val lastError: StateFlow<String?>

    fun connect(deviceAddress: String)
    fun disconnect()
    fun sendCommand(command: String)
    fun getPairedDevices(): List<BluetoothDevice>
}

class RealBluetoothManager(private val context: Context) : BluetoothCaneManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectJob: Job? = null
    private var receiveJob: Job? = null
    private var socket: BluetoothSocket? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus = _connectionStatus.asStateFlow()

    private val _sensorDataFlow = MutableStateFlow(SensorData())
    override val sensorDataFlow = _sensorDataFlow.asStateFlow()

    private val _sosSignalEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val sosSignalEvents = _sosSignalEvents.asSharedFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError = _lastError.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    override fun connect(deviceAddress: String) {
        if (_connectionStatus.value == ConnectionStatus.CONNECTED) return
        
        _lastError.value = null
        _connectionStatus.value = ConnectionStatus.CONNECTING
        
        connectJob?.cancel()
        connectJob = scope.launch {
            try {
                val adapter = bluetoothAdapter ?: throw IOException("BT not supported")
                if (!adapter.isEnabled) throw IOException("Bluetooth is off")

                if (adapter.isDiscovering) adapter.cancelDiscovery()

                val device = adapter.getRemoteDevice(deviceAddress)
                
                socket = try {
                    device.createRfcommSocketToServiceRecord(SPP_UUID).apply { connect() }
                } catch (e: Exception) {
                    val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    val fallbackSocket = method.invoke(device, 1) as BluetoothSocket
                    fallbackSocket.apply { connect() }
                }
                
                _connectionStatus.value = ConnectionStatus.CONNECTED
                startListening()
            } catch (e: Exception) {
                _lastError.value = e.message
                _connectionStatus.value = ConnectionStatus.ERROR
                delay(2000)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    private fun startListening() {
        receiveJob?.cancel()
        receiveJob = scope.launch {
            val buffer = ByteArray(1024)
            var accumulator = ""
            val inputStream: InputStream = socket?.inputStream ?: return@launch
            
            while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                try {
                    val bytes = inputStream.read(buffer)
                    if (bytes == -1) {
                        Log.w("BTManager", "Bluetooth socket reached EOF (remote device disconnected)")
                        break
                    }
                    if (bytes > 0) {
                        val chunk = String(buffer, 0, bytes)
                        accumulator += chunk
                        
                        // Check if the accumulator contains a line ending
                        if (accumulator.contains("\n") || accumulator.contains("\r")) {
                            val lines = accumulator.split(Regex("[\r\n]+"))
                            // Process all lines that are followed by a newline
                            for (i in 0 until lines.size - 1) {
                                processIncomingLine(lines[i])
                            }
                            // Keep the last part (it might be a partial line or empty)
                            accumulator = lines.last()
                        }
                    }
                } catch (e: IOException) {
                    Log.w("BTManager", "Bluetooth read error: ${e.message}")
                    break
                }
            }
            disconnect()
        }
    }

    private fun processIncomingLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return
        try {
            val updatedData = DataParser.parseSensorString(trimmed)
            _sensorDataFlow.value = updatedData
            if (updatedData.isPhysicalSosPressed) _sosSignalEvents.tryEmit(Unit)
        } catch (e: Exception) {
            Log.e("BTManager", "Parse error: $trimmed")
        }
    }

    override fun disconnect() {
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        receiveJob?.cancel()
        try { socket?.close() } catch (e: Exception) { }
        socket = null
        _sensorDataFlow.value = SensorData.invalid()
    }

    override fun sendCommand(command: String) {
        scope.launch {
            try { socket?.outputStream?.write(command.toByteArray()) } catch (e: Exception) { }
        }
    }

    @SuppressLint("MissingPermission")
    override fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }
}
