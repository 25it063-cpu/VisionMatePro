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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID

/**
 * Optimized Bluetooth Manager for the Smart Cane.
 * Implements fast-fail timeouts, retries, and high-precision logging.
 */
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
        connectJob?.cancel()
        connectJob = scope.launch {
            val totalStartTime = System.currentTimeMillis()
            var attempt = 0
            val maxAttempts = 3

            while (attempt < maxAttempts && _connectionStatus.value != ConnectionStatus.CONNECTED) {
                attempt++
                Log.i("BTManager", "connect start - attempt $attempt")
                _connectionStatus.value = ConnectionStatus.CONNECTING

                try {
                    val adapter = bluetoothAdapter ?: throw IOException("Bluetooth hardware missing")
                    if (!adapter.isEnabled) throw IOException("Bluetooth disabled")

                    if (adapter.isDiscovering) {
                        Log.d("BTManager", "Force-cancelling discovery")
                        adapter.cancelDiscovery()
                    }

                    val device = adapter.getRemoteDevice(deviceAddress)
                    
                    // withTimeout prevents the connect call from hanging for 20+ seconds
                    withTimeout(8000L) { 
                        socket = try {
                            device.createRfcommSocketToServiceRecord(SPP_UUID).also {
                                Log.d("BTManager", "socket created (standard)")
                            }
                        } catch (e: Exception) {
                            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            (method.invoke(device, 1) as BluetoothSocket).also {
                                Log.d("BTManager", "socket created (fallback)")
                            }
                        }
                        
                        socket?.connect()
                    }

                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    Log.i("BTManager", "connected")
                    
                    val connectTime = System.currentTimeMillis() - totalStartTime
                    Log.i("BTManager", "total time to establish link: ${connectTime}ms")

                    Log.d("BTManager", "listening started")
                    startListening()

                } catch (e: Exception) {
                    Log.e("BTManager", "Attempt $attempt failed: ${e.message}")
                    socket?.close()
                    socket = null
                    
                    if (attempt < maxAttempts) {
                        delay(1000) // Quick 1s retry interval
                    } else {
                        _lastError.value = e.message
                        _connectionStatus.value = ConnectionStatus.ERROR
                        delay(2000)
                        _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    }
                }
            }
        }
    }

    private fun startListening() {
        receiveJob?.cancel()
        receiveJob = scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket?.inputStream))
                while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) {
                        val updatedData = DataParser.parseSensorString(line)
                        _sensorDataFlow.value = updatedData
                        if (updatedData.isPhysicalSosPressed) _sosSignalEvents.tryEmit(Unit)
                    }
                }
            } catch (e: IOException) {
                Log.e("BTManager", "Data stream error: ${e.message}")
            } finally {
                disconnect()
            }
        }
    }

    override fun disconnect() {
        if (_connectionStatus.value == ConnectionStatus.DISCONNECTED && socket == null) return
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        receiveJob?.cancel()
        try { socket?.close() } catch (e: Exception) { }
        socket = null
        _sensorDataFlow.value = SensorData()
    }

    override fun sendCommand(command: String) {
        scope.launch {
            try {
                socket?.outputStream?.write(command.toByteArray())
                socket?.outputStream?.flush()
            } catch (e: Exception) {
                Log.e("BTManager", "Command failed: $command")
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }
}
