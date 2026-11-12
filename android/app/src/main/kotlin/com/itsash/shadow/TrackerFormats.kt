package com.itsash.shadow

/**
 * The tracker format table. Everything that decides "is this advertisement an item tracker" lives here.
 *
 * Confidence notes (from public documentation and open research such as the AirGuard / Apple Find My
 * accessory write-ups; there is no device in this environment to confirm any of them):
 *  - Apple Find My: manufacturer 0x004C, payload type 0x12, length 0x19. Well documented. The payload
 *    carries 22 bytes of the rotating public key, which we use as the matching key.
 *  - Apple type 0x07 (also used by AirPods "proximity pairing" with length 0x19): UNCERTAIN whether an
 *    unregistered AirTag uses it. Listed with low confidence and never alerts.
 *  - Google Find My Device network (FMDN): Eddystone service 0xFEAA, frame type 0x40 (0x41 when the tag is in
 *    "unwanted tracking protection" mode), then a 20-byte ephemeral id. Matches Google's published spec; the
 *    exact frame layout is read from memory, treat as likely-but-unverified.
 *  - Samsung SmartTag: service data UUID 0xFD5A. The UUID is well known; the internal byte layout is UNCERTAIN,
 *    so the "key" taken from the data is only a hint and matching falls back to the address.
 *  - Tile: service 0xFEED (and 0xFEEC). Well known. No key (matched by address).
 *  - Chipolo: service 0xFE33. Likely (Chipolo's assigned 16-bit UUID). Chipolo ONE Spot/Card Spot use Apple Find My
 *    or Google FMDN formats instead and are matched by those rows.
 *  - Google Fast Pair 0xFE2C: also used by earbuds and speakers, so low confidence and never alerts.
 */
enum class TrackerType(val wire: String, val label: String, val alerts: Boolean, val certain: Boolean) {
    APPLE_FIND_MY("apple_find_my", "Apple Find My (AirTag or accessory)", true, true),
    APPLE_SETUP("apple_setup", "Apple device in setup mode (maybe AirPods)", false, false),
    GOOGLE_FMDN("google_fmdn", "Google Find My Device tracker", true, true),
    SAMSUNG_SMARTTAG("samsung_smarttag", "Samsung SmartTag", true, true),
    TILE("tile", "Tile", true, true),
    CHIPOLO("chipolo", "Chipolo", true, true),
    GOOGLE_FAST_PAIR("google_fast_pair", "Fast Pair device (maybe a tracker)", false, false),
    GENERIC("generic", "Unknown device seen repeatedly", false, false),
    ;

    companion object {
        fun fromWire(w: String): TrackerType? = entries.firstOrNull { it.wire == w }
    }
}

/** [key] is an identifier carried inside the payload that outlives the address, when the format has one. */
data class Classification(val type: TrackerType, val key: String?)

object TrackerFormats {
    const val APPLE = 0x004C
    const val UUID_SAMSUNG = 0xFD5A
    const val UUID_TILE = 0xFEED
    const val UUID_TILE_2 = 0xFEEC
    const val UUID_CHIPOLO = 0xFE33
    const val UUID_EDDYSTONE = 0xFEAA
    const val UUID_FAST_PAIR = 0xFE2C

    private const val APPLE_OFFLINE_FINDING = 0x12
    private const val APPLE_SETUP_TYPE = 0x07
    private const val FMDN_FRAME = 0x40
    private const val FMDN_FRAME_UTP = 0x41

    /** Returns null when the advertisement is not a known tracker format. */
    fun classify(adv: Adv): Classification? {
        adv.manufacturer[APPLE]?.let { d ->
            if (d.isNotEmpty()) {
                val t = d[0].toInt() and 0xFF
                if (t == APPLE_OFFLINE_FINDING && d.size >= 4) {
                    // type, length(0x19), status, 22 key bytes, 2 high bits of the key, hint.
                    val key = if (d.size >= 25) AdParser.hex(d, 3, 25) else null
                    return Classification(TrackerType.APPLE_FIND_MY, key)
                }
                if (t == APPLE_SETUP_TYPE) return Classification(TrackerType.APPLE_SETUP, null)
            }
        }
        adv.serviceData[UUID_EDDYSTONE]?.let { d ->
            if (d.isNotEmpty()) {
                val frame = d[0].toInt() and 0xFF
                if (frame == FMDN_FRAME || frame == FMDN_FRAME_UTP) {
                    val key = if (d.size >= 21) AdParser.hex(d, 1, 21) else null
                    return Classification(TrackerType.GOOGLE_FMDN, key)
                }
            }
            // Other Eddystone frames (UID, URL, TLM, EID) are ordinary beacons, not trackers.
        }
        if (UUID_SAMSUNG in adv.serviceData || UUID_SAMSUNG in adv.serviceUuids) {
            val d = adv.serviceData[UUID_SAMSUNG]
            val key = if (d != null && d.size >= 9) AdParser.hex(d, 1, 9) else null
            return Classification(TrackerType.SAMSUNG_SMARTTAG, key)
        }
        if (hasAny(adv, UUID_TILE, UUID_TILE_2)) return Classification(TrackerType.TILE, null)
        if (hasAny(adv, UUID_CHIPOLO)) return Classification(TrackerType.CHIPOLO, null)
        if (UUID_FAST_PAIR in adv.serviceData) return Classification(TrackerType.GOOGLE_FAST_PAIR, null)
        return null
    }

    private fun hasAny(adv: Adv, vararg ids: Int) = ids.any { it in adv.serviceData || it in adv.serviceUuids }

    /** 16-bit UUID from a full UUID string on the Bluetooth base, else null. */
    fun uuid16(uuid: String): Int? {
        val s = uuid.lowercase()
        if (s.length != 36 || !s.startsWith("0000") || !s.endsWith("-0000-1000-8000-00805f9b34fb")) return null
        return s.substring(4, 8).toIntOrNull(16)
    }

    // --- generic "seen repeatedly" rule (scan screen only; it never raises an alert) -------------

    const val GENERIC_MIN_COUNT = 8
    const val GENERIC_MIN_RSSI = -80

    /**
     * An unrecognised advertiser that has no name, carries manufacturer or service data and keeps showing
     * up. Apple devices are left out: iPhones and Macs advertise constantly and would flood the list.
     */
    fun looksGeneric(adv: Adv, count: Int, avgRssi: Double): Boolean {
        if (count < GENERIC_MIN_COUNT || avgRssi < GENERIC_MIN_RSSI) return false
        if (adv.name != null || APPLE in adv.manufacturer) return false
        return adv.manufacturer.isNotEmpty() || adv.serviceData.isNotEmpty()
    }
}
