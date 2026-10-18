package dev.enco.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class EncoProtocolTest {
    @Test
    fun parseBatteryReadsLeftRightAndCase() {
        val payload = byteArrayOf(0x00, 0x03, 0x01, 80, 0x02, 70, 0x03, (0x80 or 90).toByte())
        val frame = frame(0x8106, payload)
        val (command, body) = EncoProtocol.parseFrame(frame)

        assertEquals(0x8106, command)
        val levels = EncoProtocol.parseBattery(body)
        assertEquals(Level(80, false), levels[EncoProtocol.LEFT])
        assertEquals(Level(70, false), levels[EncoProtocol.RIGHT])
        assertEquals(Level(90, true), levels[EncoProtocol.CASE])
    }

    @Test
    fun parseBatteryDropsAMissingReading() {
        val payload = byteArrayOf(0x00, 0x03, 0x01, 0x7F, 0x02, 60, 0x03, 0xFF.toByte())
        val levels = EncoProtocol.parseBattery(payload)
        assertEquals(setOf(EncoProtocol.RIGHT), levels.keys)
        assertEquals(Level(60, false), levels[EncoProtocol.RIGHT])
    }

    @Test
    fun readFrameAcceptsTheBatteryQueryShape() {
        val frame = EncoProtocol.readFrame(ByteArrayInputStream(EncoProtocol.BATTERY_QUERY))
        val (command, payload) = EncoProtocol.parseFrame(frame)
        assertEquals(0x0106, command)
        assertEquals(0, payload.size)
        assertFalse(command in EncoProtocol.BATTERY_COMMANDS)
        assertTrue(0x8106 in EncoProtocol.BATTERY_COMMANDS)
    }

    private fun frame(command: Int, payload: ByteArray): ByteArray {
        val bytes = ByteArray(9 + payload.size)
        bytes[0] = 0xAA.toByte()
        bytes[1] = (bytes.size - 2).toByte()
        bytes[4] = (command and 0xFF).toByte()
        bytes[5] = (command shr 8 and 0xFF).toByte()
        bytes[7] = (payload.size and 0xFF).toByte()
        bytes[8] = (payload.size shr 8 and 0xFF).toByte()
        payload.copyInto(bytes, 9)
        return bytes
    }
}
