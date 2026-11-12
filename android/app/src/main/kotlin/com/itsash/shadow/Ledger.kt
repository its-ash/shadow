package com.itsash.shadow

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** All alert thresholds in one place. Unit-tested in LedgerTest. */
data class AlertConfig(
    /** The tracker must have been around this long (first to last sighting in one episode). */
    val minDurationMs: Long = 30 * 60_000L,
    /** ...and have been seen at this many places, each at least [minDistanceM] from every other. */
    val minPlaces: Int = 2,
    val minDistanceM: Double = 400.0,
    /** Without any location (permission missing or no fix), alert only after this long. */
    val noLocationMinDurationMs: Long = 2 * 3_600_000L,
    /** A silence longer than this ends an episode; the next sighting starts a new one. */
    val maxGapMs: Long = 45 * 60_000L,
    val minSightings: Int = 3,
    /** Entries not seen for this long are dropped. */
    val retentionMs: Long = 48 * 3_600_000L,
    /** Places stored per episode (they are 400 m apart, so a handful is plenty). */
    val maxPlaces: Int = 12,
)

data class Place(val lat: Double, val lon: Double, val ts: Long)

object Geo {
    fun distanceM(a: Place, b: Place): Double = distanceM(a.lat, a.lon, b.lat, b.lon)

    /** Haversine distance in meters. */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }
}

/** One physical tracker as far as we can tell: all addresses and payload keys that were matched to it. */
class TrackerEntry(val id: String, var type: TrackerType) {
    val keys = LinkedHashSet<String>()
    val addresses = LinkedHashSet<String>()
    var episodeStartMs = 0L
    var lastSeenMs = 0L
    var sightings = 0
    var lastRssi = 0
    var hasLocation = false
    var alerted = false
    val places = ArrayList<Place>()

    fun durationMs(): Long = lastSeenMs - episodeStartMs

    /** Ids used by the ignore list: `k:<key>` and `a:<address>`. */
    fun ignoreIds(): Set<String> = keys.map { "k:$it" }.toSet() + addresses.map { "a:$it" }.toSet()
}

class Sighting(
    val address: String,
    val key: String?,
    val type: TrackerType,
    val rssi: Int,
    val tsMs: Long,
    val lat: Double? = null,
    val lon: Double? = null,
)

/**
 * Remembers recent trackers and decides when one has "travelled with you".
 *
 * Matching: by payload key first, then by address. A tracker whose address rotates but whose key stays
 * (or the other way round) is still one entry. A tracker that changes both at once looks like a new one.
 */
class Ledger(val cfg: AlertConfig = AlertConfig()) {
    val entries = ArrayList<TrackerEntry>()
    val ignored = LinkedHashSet<String>()
    private var nextId = 1

    fun isIgnored(address: String, key: String?): Boolean =
        "a:$address" in ignored || (key != null && "k:$key" in ignored)

    /** Adds the sighting. Returns the entry, or null when the tracker is on the ignore list. */
    fun record(s: Sighting): TrackerEntry? {
        if (isIgnored(s.address, s.key)) return null
        var e = entries.firstOrNull { s.key != null && s.key in it.keys }
            ?: entries.firstOrNull { s.address in it.addresses }
        if (e == null) {
            e = TrackerEntry("t${s.tsMs}-${nextId++}", s.type)
            entries.add(e)
            startEpisode(e, s.tsMs)
        } else if (s.tsMs - e.lastSeenMs > cfg.maxGapMs) {
            startEpisode(e, s.tsMs)
        }
        e.type = s.type
        e.addresses.add(s.address)
        if (s.key != null) e.keys.add(s.key)
        e.lastSeenMs = s.tsMs
        e.lastRssi = s.rssi
        e.sightings++
        if (s.lat != null && s.lon != null) {
            e.hasLocation = true
            val p = Place(s.lat, s.lon, s.tsMs)
            if (e.places.size < cfg.maxPlaces && e.places.all { Geo.distanceM(it, p) >= cfg.minDistanceM }) {
                e.places.add(p)
            }
        }
        return e
    }

    private fun startEpisode(e: TrackerEntry, ts: Long) {
        e.episodeStartMs = ts
        e.lastSeenMs = ts
        e.sightings = 0
        e.hasLocation = false
        e.alerted = false
        e.places.clear()
    }

    fun shouldAlert(e: TrackerEntry): Boolean {
        if (e.alerted || !e.type.alerts || e.sightings < cfg.minSightings) return false
        val d = e.durationMs()
        if (e.places.size >= cfg.minPlaces) return d >= cfg.minDurationMs
        return !e.hasLocation && d >= cfg.noLocationMinDurationMs
    }

    /** Entries that just crossed the threshold; each is marked alerted so it is reported once per episode. */
    fun takeDue(): List<TrackerEntry> = entries.filter { shouldAlert(it) }.onEach { it.alerted = true }

    fun prune(nowMs: Long) {
        entries.removeAll { nowMs - it.lastSeenMs > cfg.retentionMs }
    }

    fun ignore(ids: Collection<String>) {
        ignored.addAll(ids)
        entries.removeAll { e -> e.ignoreIds().any { it in ignored } }
    }

    fun clear() {
        entries.clear()
    }
}
