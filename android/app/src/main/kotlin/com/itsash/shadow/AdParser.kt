package com.itsash.shadow

/**
 * One parsed BLE advertisement (no Android types, so it runs in JVM tests).
 *
 * [manufacturer]: company id -> data AFTER the 2-byte company id.
 * [serviceData]: 16-bit service UUID -> data after the UUID.
 * [serviceUuids]: 16-bit UUIDs from the "complete / incomplete list of 16-bit UUIDs" fields.
 */
class Adv(
    val manufacturer: Map<Int, ByteArray> = emptyMap(),
    val serviceData: Map<Int, ByteArray> = emptyMap(),
    val serviceUuids: Set<Int> = emptySet(),
    val name: String? = null,
)

/** Parses raw advertisement bytes (a sequence of `len, type, data` structures, Core Spec Supplement Part A). */
object AdParser {
    private const val AD_UUID16_INCOMPLETE = 0x02
    private const val AD_UUID16_COMPLETE = 0x03
    private const val AD_NAME_SHORT = 0x08
    private const val AD_NAME_COMPLETE = 0x09
    private const val AD_SERVICE_DATA_16 = 0x16
    private const val AD_MANUFACTURER = 0xFF

    fun parse(raw: ByteArray): Adv {
        val manufacturer = LinkedHashMap<Int, ByteArray>()
        val serviceData = LinkedHashMap<Int, ByteArray>()
        val uuids = LinkedHashSet<Int>()
        var name: String? = null
        var i = 0
        while (i < raw.size) {
            val len = raw[i].toInt() and 0xFF
            if (len == 0) break // padding: the rest is zeros
            val end = i + 1 + len
            if (end > raw.size) break // truncated structure
            val type = raw[i + 1].toInt() and 0xFF
            val data = raw.copyOfRange(i + 2, end)
            when (type) {
                AD_UUID16_INCOMPLETE, AD_UUID16_COMPLETE -> {
                    var j = 0
                    while (j + 1 < data.size) {
                        uuids.add(u16(data, j))
                        j += 2
                    }
                }
                AD_SERVICE_DATA_16 -> if (data.size >= 2) serviceData[u16(data, 0)] = data.copyOfRange(2, data.size)
                AD_MANUFACTURER -> if (data.size >= 2) manufacturer[u16(data, 0)] = data.copyOfRange(2, data.size)
                AD_NAME_SHORT, AD_NAME_COMPLETE -> name = String(data, Charsets.UTF_8)
            }
            i = end
        }
        return Adv(manufacturer, serviceData, uuids, name)
    }

    private fun u16(b: ByteArray, at: Int): Int = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    fun hex(b: ByteArray, from: Int = 0, to: Int = b.size): String {
        val sb = StringBuilder()
        for (k in from until to) sb.append(String.format("%02x", b[k].toInt() and 0xFF))
        return sb.toString()
    }
}
