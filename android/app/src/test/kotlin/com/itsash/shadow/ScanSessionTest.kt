package com.itsash.shadow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSessionTest {
    private fun tile() = Adv(serviceUuids = setOf(TrackerFormats.UUID_TILE))
    private fun mfr() = Adv(manufacturer = mapOf(0x0006 to byteArrayOf(1, 9)))
    private fun apple(key: Int): Adv {
        val d = ByteArray(27)
        d[0] = 0x12
        d[1] = 0x19
        for (i in 3 until 25) d[i] = key.toByte()
        return Adv(manufacturer = mapOf(TrackerFormats.APPLE to d))
    }

    @Test
    fun countsAndSortsByAverageSignal() {
        val s = ScanSession()
        repeat(3) { s.add("T1", tile(), -80, it * 1000L) }
        repeat(2) { s.add("T2", tile(), -50, it * 1000L) }
        val out = s.snapshot()
        assertEquals(listOf("a:T2", "a:T1"), out.map { it.id })
        assertEquals(3, out[1].count)
        assertEquals(TrackerType.TILE, out[0].type)
    }

    @Test
    fun appleKeyMergesRotatingAddresses() {
        val s = ScanSession()
        s.add("A1", apple(7), -60, 0)
        s.add("A2", apple(7), -62, 1000)
        s.add("A3", apple(9), -70, 2000)
        val out = s.snapshot()
        assertEquals(2, out.size)
        assertEquals(2, out.first { it.avgRssi > -65 }.count)
    }

    @Test
    fun genericAppearsOnlyAfterRepeatedSightings() {
        val s = ScanSession()
        repeat(7) { s.add("G", mfr(), -55, it * 1000L) }
        assertTrue(s.snapshot().isEmpty())
        s.add("G", mfr(), -55, 8000)
        val out = s.snapshot()
        assertEquals(1, out.size)
        assertEquals(TrackerType.GENERIC, out[0].type)
    }

    @Test
    fun genericListIsCapped() {
        val s = ScanSession()
        for (g in 0 until 9) repeat(8) { s.add("G$g", mfr(), -50 - g, it * 1000L) }
        assertEquals(5, s.snapshot().size)
    }

    @Test
    fun ignoredDevicesNeverShow() {
        val s = ScanSession(setOf("a:T1"))
        repeat(3) { s.add("T1", tile(), -50, it * 1000L) }
        assertTrue(s.snapshot().isEmpty())
    }
}
