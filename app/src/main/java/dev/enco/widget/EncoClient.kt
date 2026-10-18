package dev.enco.widget

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object EncoClient {
    private const val TAG = "Enco"
    private const val CONNECT_TIMEOUT_MS = 2_500L
    private const val READ_TIMEOUT_MS = 1_500L
    private val VENDOR_SERVICE: UUID = UUID.fromString("0000079a-d102-11e1-9b23-00025b00a5a5")

    fun query(device: BluetoothDevice): Map<Int, Level> {
        val started = System.currentTimeMillis()
        var levels = queryOnce(device, allowUuidFallback = true)
        if (levels.isEmpty()) {
            Thread.sleep(400)
            levels = queryOnce(device, allowUuidFallback = false)
        }
        val elapsed = System.currentTimeMillis() - started
        if (levels.isNotEmpty()) {
            Log.i(TAG, "battery in ${elapsed}ms parts=${levels.keys}")
            return levels
        }
        Log.w(TAG, "battery unanswered after ${elapsed}ms")
        throw IOException("no battery response")
    }

    private fun queryOnce(device: BluetoothDevice, allowUuidFallback: Boolean): Map<Int, Level> {
        val socket = open(device, allowUuidFallback) ?: return emptyMap()
        val deadline = AtomicLong(System.currentTimeMillis() + READ_TIMEOUT_MS)
        val finished = AtomicBoolean(false)
        val watchdog = Thread {
            while (!finished.get()) {
                if (System.currentTimeMillis() > deadline.get()) {
                    runCatching { socket.close() }
                    return@Thread
                }
                Thread.sleep(40)
            }
        }
        watchdog.isDaemon = true
        watchdog.start()
        try {
            socket.outputStream.write(EncoProtocol.BATTERY_QUERY)
            socket.outputStream.flush()
            val levels = mutableMapOf<Int, Level>()
            while (System.currentTimeMillis() < deadline.get()) {
                val frame = try {
                    EncoProtocol.readFrame(socket.inputStream)
                } catch (error: IOException) {
                    if (System.currentTimeMillis() >= deadline.get() || levels.isNotEmpty()) break
                    throw error
                }
                val (command, payload) = EncoProtocol.parseFrame(frame)
                if (command !in EncoProtocol.BATTERY_COMMANDS) continue
                levels.putAll(EncoProtocol.parseBattery(payload))
                if (levels.isNotEmpty()) {
                    deadline.set(minOf(deadline.get(), System.currentTimeMillis() + 200))
                }
            }
            return levels
        } catch (error: Exception) {
            Log.w(TAG, "battery read failed", error)
            return emptyMap()
        } finally {
            finished.set(true)
            runCatching { socket.close() }
        }
    }

    private fun open(device: BluetoothDevice, allowUuidFallback: Boolean): BluetoothSocket? {
        val started = System.currentTimeMillis()
        val channel = runCatching { channelSocket(device) }.getOrNull()
        if (channel != null) {
            connectWithin(channel, CONNECT_TIMEOUT_MS)?.let {
                Log.i(TAG, "connected on channel 12 in ${System.currentTimeMillis() - started}ms")
                return it
            }
        }
        if (!allowUuidFallback) {
            Log.w(TAG, "channel 12 did not connect")
            return null
        }
        val fallbacks = listOf(
            { device.createRfcommSocketToServiceRecord(VENDOR_SERVICE) },
            { device.createInsecureRfcommSocketToServiceRecord(VENDOR_SERVICE) },
        )
        for (open in fallbacks) {
            val socket = runCatching { open() }.getOrNull() ?: continue
            connectWithin(socket, CONNECT_TIMEOUT_MS)?.let {
                Log.i(TAG, "connected on vendor uuid in ${System.currentTimeMillis() - started}ms")
                return it
            }
        }
        Log.w(TAG, "could not connect")
        return null
    }

    private fun connectWithin(socket: BluetoothSocket, timeoutMs: Long): BluetoothSocket? {
        val connected = AtomicBoolean(false)
        val worker = Thread {
            try {
                socket.connect()
                connected.set(true)
            } catch (_: IOException) {
            }
        }
        worker.isDaemon = true
        worker.name = "enco-connect"
        worker.start()
        worker.join(timeoutMs)
        if (connected.get() && socket.isConnected) return socket
        runCatching { socket.close() }
        worker.join(400)
        return null
    }

    private fun channelSocket(device: BluetoothDevice): BluetoothSocket {
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return method.invoke(device, 12) as BluetoothSocket
    }
}
