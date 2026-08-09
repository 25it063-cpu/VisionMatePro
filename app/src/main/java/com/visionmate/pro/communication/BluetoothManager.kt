package com.visionmate.pro.communication

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import com.visionmate.pro.model.ConnectionStatus
import com.visionmate.pro.model.SensorData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

interface BluetoothManager {
    val connectionStatus: StateFlow<ConnectionStatus>
    val sensorDataFlow: StateFlow<SensorData>
    val sosSignalEvents: SharedFlow<Unit>

    fun connect(deviceAddress: String)
    fun disconnect()
    fun sendCommand(command: String)
}

class RealBluetoothManager : BluetoothManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var connectJob: Job? = null
    private var receiveJob: Job? = null
    private var socket: BluetoothSocket? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _sensorDataFlow = MutableStateFlow(SensorData())
    override val sensorDataFlow: StateFlow<SensorData> = _sensorDataFlow.asStateFlow()

    private val _sosSignalEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val sosSignalEvents: SharedFlow<Unit> = _sosSignalEvents.asSharedFlow()

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    override fun connect(deviceAddress: String) {
        if (_connectionStatus.value == ConnectionStatus.CONNECTED) return
        
        _connectionStatus.value = ConnectionStatus.CONNECTING
        connectJob?.cancel()
        connectJob = scope.launch {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val device = adapter.getRemoteDevice(deviceAddress)
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket?.connect()
                
                _connectionStatus.value = ConnectionStatus.CONNECTED
                startListening()
            } catch (e: IOException) {
                _connectionStatus.value = ConnectionStatus.ERROR
                e.printStackTrace()
                delay(2000)
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }
    }

    private fun startListening() {
        receiveJob?.cancel()
        receiveJob = scope.launch {
            val buffer = ByteArray(1024)
            while (_connectionStatus.value == ConnectionStatus.CONNECTED) {
                try {
                    val bytes = socket?.inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val rawString = String(buffer, 0, bytes)
                        val updatedData = DataParser.parseSensorString(rawString)
                        _sensorDataFlow.value = updatedData
                        
                        if (updatedData.isPhysicalSosPressed) {
                            _sosSignalEvents.emit(Unit)
                        }
                    }
                } catch (e: IOException) {
                    disconnect()
                    break
                }
            }
        }
    }

    override fun disconnect() {
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        try {
            socket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        socket = null
        receiveJob?.cancel()
        _sensorDataFlow.value = SensorData() // Reset data on disconnect
    }

    override fun sendCommand(command: String) {
        scope.launch {
            try {
                socket?.outputStream?.write(command.toByteArray())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
