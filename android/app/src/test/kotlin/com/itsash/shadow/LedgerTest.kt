package com.itsash.shadow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {
    private val min = 60_000L
    private val cfg = AlertConfig()

    // 0.01 degrees of latitude is about 1.11 km; 0.002 is about 222 m.
    private val home = 48.0 to 11.0
    private val far = 48.01 to 11.0
    private val near = 48.002 to 11.0

    private fun sight(
        l: Ledger,
        t: Long,
        addr: String = "AA:01",
        key: String? = null,
        at: Pair<Double, Double>? = home,
        type: TrackerType = TrackerType.APPLE_FIND_MY,
    ) = l.record(Sighting(addr, key, type, -60, t, at?.first, at?.second))

    @Test
    fun distanceIsAboutRight() {
        val d = Geo.distanceM(48.0, 11.0, 48.01, 11.0)
        assertTrue(d in 1100.0..1120.0)
        assertEquals(0.0, Geo.distanceM(1.0, 2.0, 1.0, 2.0), 0.001)
    }

    @Test
    fun defaultThresholdsAreInTheDocumentedRange() {
        assertTrue(cfg.minDurationMs in 20 * min..30 * min)
        assertTrue(cfg.minPlaces in 2..3)
        assertTrue(cfg.minDistanceM in 300.0..500.0)
    }

    @Test
    fun alertsAfterDurationAndTwoFarPlaces() {
        val l = Ledger(cfg)
        sight(l, 0)
        sight(l, 10 * min, at = near)
        assertTrue(l.takeDue().isEmpty())
        sight(l, 20 * min, at = far)
        assertTrue("20 min is not enough", l.takeDue().isEmpty())
        sight(l, 31 * min, at = far)
        val due = l.takeDue()
        assertEquals(1, due.size)
        assertEquals(2, due[0].places.size)
        assertTrue("reported once", l.takeDue().isEmpty())
        sight(l, 40 * min, at = far)
        assertTrue(l.takeDue().isEmpty())
    }

    @Test
    fun staysQuietWhenItNeverLeavesOnePlace() {
        val l = Ledger(cfg)
        for (i in 0..20) sight(l, i * 10 * min, at = if (i % 2 == 0) home else near)
        assertTrue(l.takeDue().isEmpty())
        assertEquals(1, l.entries[0].places.size)
    }

    @Test
    fun staysQuietWhenSeenTooShortWhileMoving() {
        val l = Ledger(cfg)
        sight(l, 0)
        sight(l, 5 * min, at = far)
        sight(l, 10 * min, at = far)
        assertTrue(l.takeDue().isEmpty())
    }

    @Test
    fun placesAreFarFromEachOtherNotJustFromTheFirst() {
        val l = Ledger(cfg)
        sight(l, 0, at = home)
        sight(l, 1 * min, at = 48.0036 to 11.0) // ~400 m
        sight(l, 2 * min, at = 48.0045 to 11.0) // ~500 m from home but ~100 m from the second
        assertEquals(2, l.entries[0].places.size)
    }

    @Test
    fun withoutLocationAlertsOnlyAfterTheLongerLimit() {
        val l = Ledger(cfg)
        sight(l, 0, at = null)
        sight(l, 40 * min, at = null)
        sight(l, 80 * min, at = null)
        sight(l, 110 * min, at = null)
        assertTrue("under 2 h", l.takeDue().isEmpty())
        sight(l, 125 * min, at = null)
        assertEquals(1, l.takeDue().size)
    }

    @Test
    fun aGapStartsANewEpisode() {
        val l = Ledger(cfg)
        sight(l, 0)
        sight(l, 20 * min, at = far)
        sight(l, 50 * min + 1, at = far) // 30 min later, still the same episode
        assertEquals(1, l.takeDue().size)
        sight(l, 3 * 60 * min, at = home) // hours later: new episode, can alert again
        val e = l.entries[0]
        assertEquals(1, l.entries.size)
        assertFalse(e.alerted)
        assertEquals(1, e.sightings)
        assertEquals(1, e.places.size)
    }

    @Test
    fun rotatingAddressWithSameKeyIsOneTracker() {
        val l = Ledger(cfg)
        sight(l, 0, addr = "A1", key = "kk")
        sight(l, 10 * min, addr = "A2", key = "kk", at = far)
        sight(l, 35 * min, addr = "A3", key = "kk", at = far)
        assertEquals(1, l.entries.size)
        assertEquals(setOf("A1", "A2", "A3"), l.entries[0].addresses)
        assertEquals(1, l.takeDue().size)
    }

    @Test
    fun sameAddressWithChangedKeyIsStillOneTracker() {
        val l = Ledger(cfg)
        sight(l, 0, addr = "A1", key = "k1")
        sight(l, 10 * min, addr = "A1", key = "k2")
        assertEquals(1, l.entries.size)
        assertEquals(setOf("k1", "k2"), l.entries[0].keys)
    }

    @Test
    fun differentTrackersStaySeparate() {
        val l = Ledger(cfg)
        sight(l, 0, addr = "A1", key = "k1")
        sight(l, 0, addr = "B1", key = "k2")
        sight(l, 0, addr = "C1", type = TrackerType.TILE)
        assertEquals(3, l.entries.size)
    }

    @Test
    fun ignoredTrackersAreDroppedAndForgotten() {
        val l = Ledger(cfg)
        val e = sight(l, 0, addr = "A1", key = "k1")!!
        l.ignore(e.ignoreIds())
        assertTrue(l.entries.isEmpty())
        assertNull(sight(l, 1 * min, addr = "A1", key = "k1"))
        assertNull("new address but same key", sight(l, 2 * min, addr = "A9", key = "k1"))
        assertNotNull(sight(l, 3 * min, addr = "B1", key = "k2"))
    }

    @Test
    fun onlyAlertableTypesAlert() {
        val l = Ledger(cfg)
        sight(l, 0, type = TrackerType.GOOGLE_FAST_PAIR)
        sight(l, 20 * min, at = far, type = TrackerType.GOOGLE_FAST_PAIR)
        sight(l, 40 * min, at = far, type = TrackerType.GOOGLE_FAST_PAIR)
        assertTrue(l.takeDue().isEmpty())
    }

    @Test
    fun needsAFewSightings() {
        val l = Ledger(cfg)
        sight(l, 0)
        sight(l, 40 * min, at = far)
        assertTrue("two sightings are not enough", l.takeDue().isEmpty())
    }

    @Test
    fun pruneDropsOldEntries() {
        val l = Ledger(cfg)
        sight(l, 0)
        sight(l, 40 * min, addr = "B1")
        l.prune(cfg.retentionMs + 10 * min)
        assertEquals(1, l.entries.size)
        assertEquals(setOf("B1"), l.entries[0].addresses)
    }

    @Test
    fun clearRemovesEntriesButKeepsIgnoreList() {
        val l = Ledger(cfg)
        l.ignore(listOf("a:X"))
        sight(l, 0)
        l.clear()
        assertTrue(l.entries.isEmpty())
        assertEquals(setOf("a:X"), l.ignored)
    }
}
