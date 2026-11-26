package com.itsash.shadow

/** What the "Scan now" screen shows for one device. */
data class ScanDevice(
    val id: String,
    val address: String,
    val type: TrackerType,
    val rssi: Int,
    val avgRssi: Double,
    val count: Int,
    val lastSeenMs: Long,
)

/** Aggregates the advertisements of one foreground scan (pure; tested in ScanSessionTest). */
class ScanSession(ignored: Set<String> = emptySet()) {
    private class Acc(val address: String, var adv: Adv) {
        var cls: Classification? = null
        var rssi = 0
        var avg = 0.0
        var count = 0
        var last = 0L
    }

    private val ignored = ignored.toMutableSet()
    private val devices = LinkedHashMap<String, Acc>()

    @Synchronized
    fun ignore(ids: Set<String>) {
        ignored.addAll(ids)
        devices.entries.removeAll { (id, a) -> id in ids || "a:${a.address}" in ids }
    }

    @Synchronized
    fun add(address: String, adv: Adv, rssi: Int, nowMs: Long) {
        val cls = TrackerFormats.classify(adv)
        val key = cls?.key
        if ("a:$address" in ignored || (key != null && "k:$key" in ignored)) return
        // Same payload key under a new address is the same tracker.
        val id = if (key != null) "k:$key" else "a:$address"
        val a = devices.getOrPut(id) { Acc(address, adv) }
        a.adv = adv
        a.cls = cls ?: a.cls
        a.rssi = rssi
        a.avg = if (a.count == 0) rssi.toDouble() else a.avg * 0.8 + rssi * 0.2
        a.count++
        a.last = nowMs
    }

    /** Trackers first (strongest signal first), then at most [maxGeneric] generic devices. */
    @Synchronized
    fun snapshot(maxGeneric: Int = 5): List<ScanDevice> {
        val out = ArrayList<ScanDevice>()
        val generic = ArrayList<ScanDevice>()
        for ((id, a) in devices) {
            val cls = a.cls
            if (cls != null) {
                out.add(ScanDevice(id, a.address, cls.type, a.rssi, a.avg, a.count, a.last))
            } else if (TrackerFormats.looksGeneric(a.adv, a.count, a.avg)) {
                generic.add(ScanDevice(id, a.address, TrackerType.GENERIC, a.rssi, a.avg, a.count, a.last))
            }
        }
        out.sortByDescending { it.avgRssi }
        generic.sortByDescending { it.avgRssi }
        return out + generic.take(maxGeneric)
    }
}
