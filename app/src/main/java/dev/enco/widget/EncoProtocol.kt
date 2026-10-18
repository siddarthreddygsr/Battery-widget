package dev.enco.widget

import java.io.IOException
import java.io.InputStream

data class Level(val percent: Int, val charging: Boolean)

object EncoProtocol {
    val BATTERY_QUERY = byteArrayOf(
        0xAA.toByte(), 0x07, 0x00, 0x00, 0x06, 0x01, 0x00, 0x00, 0x00,
    )
    val BATTERY_COMMANDS = setOf(0x8106, 0x0204)
    const val LEFT = 1
    const val RIGHT = 2
    const val CASE = 3

    fun readFrame(input: InputStream): ByteArray {
        val header = readExact(input, 2)
        if (header[0].toUByte().toInt() != 0xAA) {
            throw IOException("unexpected header ${header.toHex()}")
        }
        val rest = readExact(input, header[1].toUByte().toInt())
        return header + rest
    }

    fun parseFrame(frame: ByteArray): Pair<Int, ByteArray> {
        if (frame.size < 9 || frame[0].toUByte().toInt() != 0xAA) {
            throw IllegalArgumentException("short frame ${frame.toHex()}")
        }
        val command = u16le(frame, 4)
        val length = u16le(frame, 7)
        if (frame.size < 9 + length) {
            throw IllegalArgumentException("short frame ${frame.toHex()}")
        }
        return command to frame.copyOfRange(9, 9 + length)
    }

    fun parseBattery(payload: ByteArray): Map<Int, Level> {
        if (payload.size < 2) {
            throw IllegalArgumentException("short battery payload ${payload.toHex()}")
        }
        val count = payload[1].toUByte().toInt()
        if (count > 8) {
            throw IllegalArgumentException("bad battery count $count")
        }
        val levels = mutableMapOf<Int, Level>()
        for (index in 0 until count) {
            val offset = 2 + index * 2
            if (offset + 1 >= payload.size) break
            val raw = payload[offset + 1].toUByte().toInt()
            val percent = raw and 0x7F
            if (percent > 100) continue
            levels[payload[offset].toUByte().toInt()] = Level(
                percent = percent,
                charging = raw and 0x80 != 0,
            )
        }
        return levels
    }

    private fun readExact(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = input.read(buffer, offset, size - offset)
            if (count < 0) throw IOException("earbuds closed the connection")
            offset += count
        }
        return buffer
    }

    private fun u16le(frame: ByteArray, offset: Int): Int {
        return frame[offset].toUByte().toInt() or (frame[offset + 1].toUByte().toInt() shl 8)
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
