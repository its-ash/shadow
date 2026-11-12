package com.itsash.shadow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sample advertisements are built byte by byte, the way they arrive from the controller. */
class TrackerFormatsTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    /** len, type, payload */
    private fun ad(type: Int, vararg payload: Int) = bytes(payload.size + 1, type, *payload)

    private fun cat(vararg parts: ByteArray) = parts.fold(ByteArray(0)) { a, b -> a + b }

    private fun classify(raw: ByteArray) = TrackerFormats.classify(AdParser.parse(raw))

    @Test
    fun parserReadsManufacturerServiceDataUuidsAndName() {
        val raw = cat(
            ad(0x01, 0x06),
            ad(0x03, 0xED, 0xFE, 0x33, 0xFE),
            ad(0x16, 0x5A, 0xFD, 0x01, 0x02),
            ad(0xFF, 0x4C, 0x00, 0x12, 0x19),
            ad(0x09, 'T'.code, 'a'.code, 'g'.code),
        )
        val adv = AdParser.parse(raw)
        assertEquals(setOf(0xFEED, 0xFE33), adv.serviceUuids)
        assertEquals(listOf(1, 2), adv.serviceData[0xFD5A]!!.map { it.toInt() })
        assertEquals(listOf(0x12, 0x19), adv.manufacturer[0x004C]!!.map { it.toInt() })
        assertEquals("Tag", adv.name)
    }

    @Test
    fun parserStopsAtPaddingAndTruncatedStructures() {
        assertTrue(AdParser.parse(bytes(0, 0, 0, 0)).manufacturer.isEmpty())
        // claims 9 bytes but only 3 follow
        assertTrue(AdParser.parse(bytes(9, 0xFF, 0x4C, 0x00)).manufacturer.isEmpty())
        assertTrue(AdParser.parse(ByteArray(0)).serviceData.isEmpty())
    }

    @Test
    fun appleFindMyOfflineFindingGetsKeyFromPayload() {
        // 0x12 0x19 status + 22 key bytes + 2 key bits + hint = 27 bytes after the company id.
        val key = IntArray(22) { 0xA0 + it }
        val payload = intArrayOf(0x12, 0x19, 0x10, *key, 0x01, 0x00)
        val c = classify(ad(0xFF, 0x4C, 0x00, *payload))!!
        assertEquals(TrackerType.APPLE_FIND_MY, c.type)
        val k = c.key!!
        assertEquals(44, k.length)
        assertTrue(k.startsWith("a0a1a2"))
        assertTrue(k.endsWith("b5")) // 0xA0 + 21
    }

    @Test
    fun appleFindMyStatusByteDoesNotChangeTheKey() {
        val key = IntArray(22) { it + 1 }
        fun frame(status: Int) = classify(ad(0xFF, 0x4C, 0x00, 0x12, 0x19, status, *key, 0, 0))!!
        assertEquals(frame(0x00).key, frame(0x24).key)
    }

    @Test
    fun appleShortOfflineFindingHasNoKey() {
        val c = classify(ad(0xFF, 0x4C, 0x00, 0x12, 0x19, 0x10, 0x01))!!
        assertEquals(TrackerType.APPLE_FIND_MY, c.type)
        assertNull(c.key)
    }

    @Test
    fun appleOtherMessagesAreNotTrackers() {
        // 0x10 Nearby Info (iPhones), 0x0C Handoff, 0x05 AirDrop
        assertNull(classify(ad(0xFF, 0x4C, 0x00, 0x10, 0x05, 0x01, 0x02, 0x03)))
        assertNull(classify(ad(0xFF, 0x4C, 0x00, 0x0C, 0x0E, 0x00)))
        assertNull(classify(ad(0xFF, 0x4C, 0x00, 0x05, 0x12)))
    }

    @Test
    fun appleSetupTypeIsLowConfidenceAndNeverAlerts() {
        val c = classify(ad(0xFF, 0x4C, 0x00, 0x07, 0x19, 0x01))!!
        assertEquals(TrackerType.APPLE_SETUP, c.type)
        assertFalse(c.type.alerts)
        assertFalse(c.type.certain)
    }

    @Test
    fun samsungSmartTagByServiceData() {
        val c = classify(ad(0x16, 0x5A, 0xFD, 0x41, 1, 2, 3, 4, 5, 6, 7, 8, 9))!!
        assertEquals(TrackerType.SAMSUNG_SMARTTAG, c.type)
        assertEquals("0102030405060708", c.key)
    }

    @Test
    fun samsungSmartTagByUuidListHasNoKey() {
        val c = classify(ad(0x03, 0x5A, 0xFD))!!
        assertEquals(TrackerType.SAMSUNG_SMARTTAG, c.type)
        assertNull(c.key)
    }

    @Test
    fun tileByEitherUuid() {
        assertEquals(TrackerType.TILE, classify(ad(0x03, 0xED, 0xFE))!!.type)
        assertEquals(TrackerType.TILE, classify(ad(0x16, 0xEC, 0xFE, 0x01))!!.type)
        assertEquals(TrackerType.TILE, classify(ad(0x16, 0xED, 0xFE, 0x02, 0x03))!!.type)
    }

    @Test
    fun chipolo() {
        assertEquals(TrackerType.CHIPOLO, classify(ad(0x03, 0x33, 0xFE))!!.type)
    }

    @Test
    fun googleFmdnFrameWithEphemeralId() {
        val eid = IntArray(20) { 0x30 + it }
        val c = classify(ad(0x16, 0xAA, 0xFE, 0x40, *eid, 0x80))!!
        assertEquals(TrackerType.GOOGLE_FMDN, c.type)
        val k = c.key!!
        assertEquals(40, k.length)
        assertTrue(k.startsWith("303132"))
        val utp = classify(ad(0x16, 0xAA, 0xFE, 0x41, *eid, 0x80))!!
        assertEquals(TrackerType.GOOGLE_FMDN, utp.type)
        assertEquals(c.key, utp.key)
    }

    @Test
    fun ordinaryEddystoneBeaconsAreIgnored() {
        // UID (0x00), URL (0x10), TLM (0x20), EID (0x30)
        for (frame in intArrayOf(0x00, 0x10, 0x20, 0x30)) {
            assertNull(classify(ad(0x16, 0xAA, 0xFE, frame, 0xEE, 1, 2, 3, 4)))
        }
    }

    @Test
    fun fastPairIsLowConfidenceAndNeverAlerts() {
        val c = classify(ad(0x16, 0x2C, 0xFE, 0x12, 0x34, 0x56))!!
        assertEquals(TrackerType.GOOGLE_FAST_PAIR, c.type)
        assertFalse(c.type.alerts)
    }

    @Test
    fun unknownDevicesAreNotClassified() {
        assertNull(classify(ad(0xFF, 0x06, 0x00, 0x01, 0x09)))
        assertNull(classify(ad(0x01, 0x06)))
        assertNotNull(TrackerType.fromWire("tile"))
        assertNull(TrackerType.fromWire("nope"))
    }

    @Test
    fun uuid16FromFullUuid() {
        assertEquals(0xFD5A, TrackerFormats.uuid16("0000fd5a-0000-1000-8000-00805f9b34fb"))
        assertEquals(0xFEAA, TrackerFormats.uuid16("0000FEAA-0000-1000-8000-00805F9B34FB"))
        assertNull(TrackerFormats.uuid16("12345678-1234-1234-1234-1234567890ab"))
        assertNull(TrackerFormats.uuid16("fd5a"))
    }

    @Test
    fun genericRule() {
        val adv = AdParser.parse(ad(0xFF, 0x06, 0x00, 0x01, 0x09))
        assertTrue(TrackerFormats.looksGeneric(adv, 8, -60.0))
        assertFalse(TrackerFormats.looksGeneric(adv, 7, -60.0)) // not often enough
        assertFalse(TrackerFormats.looksGeneric(adv, 20, -90.0)) // too far to be with you
        val named = AdParser.parse(cat(ad(0xFF, 0x06, 0x00, 1), ad(0x09, 'x'.code)))
        assertFalse(TrackerFormats.looksGeneric(named, 20, -50.0))
        val apple = AdParser.parse(ad(0xFF, 0x4C, 0x00, 0x10, 0x05))
        assertFalse(TrackerFormats.looksGeneric(apple, 20, -50.0))
        assertFalse(TrackerFormats.looksGeneric(AdParser.parse(ad(0x01, 0x06)), 20, -50.0))
    }
}
