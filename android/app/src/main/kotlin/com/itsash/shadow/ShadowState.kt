package com.itsash.shadow

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** An alert that was raised (kept for the History screen). */
data class AlertRecord(
    val tsMs: Long,
    val entryId: String,
    val type: TrackerType,
    val durationMs: Long,
    val places: Int,
    val rssi: Int,
    val ids: List<String>,
    val ignored: Boolean,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "ts" to tsMs,
        "entryId" to entryId,
        "type" to type.wire,
        "label" to type.label,
        "durationMs" to durationMs,
        "places" to places,
        "rssi" to rssi,
        "ignored" to ignored,
        "ids" to ids,
    )
}

/** Settings shared by the app and the service (`shadow_prefs`). */
class ShadowPrefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var protection: Boolean
        get() = sp.getBoolean(KEY_PROTECTION, false)
        set(v) = sp.edit().putBoolean(KEY_PROTECTION, v).apply()

    var askedNotifications: Boolean
        get() = sp.getBoolean(KEY_ASKED_NOTIFICATIONS, false)
        set(v) = sp.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, v).apply()

    companion object {
        const val FILE = "shadow_prefs"
        private const val KEY_PROTECTION = "protection"
        private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}

/**
 * The process-wide ledger and alert history, saved to `shadow_state.json` in the app's private files.
 * The service and the activity run in the same process and share this object; all access is synchronized.
 * Nothing leaves the phone.
 */
object ShadowState {
    private const val FILE = "shadow_state.json"
    private const val MAX_ALERTS = 200

    private var file: File? = null
    private var ledger = Ledger()
    private val alerts = ArrayList<AlertRecord>()

    @Synchronized
    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, FILE)
        file = f
        if (f.exists()) runCatching { load(JSONObject(f.readText())) }
    }

    @Synchronized
    fun <T> withLedger(block: (Ledger) -> T): T {
        val r = block(ledger)
        save()
        return r
    }

    @Synchronized
    fun ignoredIds(): Set<String> = HashSet(ledger.ignored)

    @Synchronized
    fun addAlert(r: AlertRecord) {
        alerts.add(0, r)
        while (alerts.size > MAX_ALERTS) alerts.removeAt(alerts.size - 1)
        save()
    }

    @Synchronized
    fun alertMaps(): List<Map<String, Any?>> = alerts.map { it.toMap() }

    /** Puts the ids on the ignore list and marks matching alerts. */
    @Synchronized
    fun ignore(ids: List<String>) {
        ledger.ignore(ids)
        val set = ids.toSet()
        for (i in alerts.indices) {
            if (alerts[i].ids.any { it in set }) alerts[i] = alerts[i].copy(ignored = true)
        }
        save()
    }

    @Synchronized
    fun ignoredCount(): Int = ledger.ignored.size

    @Synchronized
    fun clearIgnored() {
        ledger.ignored.clear()
        for (i in alerts.indices) alerts[i] = alerts[i].copy(ignored = false)
        save()
    }

    /** Deletes sightings and alert history. The ignore list stays unless [includeIgnored]. */
    @Synchronized
    fun clearAll(includeIgnored: Boolean) {
        ledger.clear()
        alerts.clear()
        if (includeIgnored) ledger.ignored.clear()
        save()
    }

    // --- JSON -------------------------------------------------------------------------------

    private fun save() {
        val f = file ?: return
        val root = JSONObject()
        root.put("ignored", JSONArray(ledger.ignored.toList()))
        root.put(
            "entries",
            JSONArray().also { arr ->
                for (e in ledger.entries) {
                    arr.put(
                        JSONObject()
                            .put("id", e.id)
                            .put("type", e.type.wire)
                            .put("keys", JSONArray(e.keys.toList()))
                            .put("addresses", JSONArray(e.addresses.toList()))
                            .put("start", e.episodeStartMs)
                            .put("last", e.lastSeenMs)
                            .put("n", e.sightings)
                            .put("rssi", e.lastRssi)
                            .put("loc", e.hasLocation)
                            .put("alerted", e.alerted)
                            .put(
                                "places",
                                JSONArray().also { p ->
                                    e.places.forEach { p.put(JSONArray(listOf(it.lat, it.lon, it.ts))) }
                                },
                            ),
                    )
                }
            },
        )
        root.put(
            "alerts",
            JSONArray().also { arr ->
                for (a in alerts) {
                    arr.put(
                        JSONObject()
                            .put("ts", a.tsMs).put("entry", a.entryId).put("type", a.type.wire)
                            .put("dur", a.durationMs).put("places", a.places).put("rssi", a.rssi)
                            .put("ids", JSONArray(a.ids)).put("ignored", a.ignored),
                    )
                }
            },
        )
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(f)) {
            f.writeText(root.toString())
            tmp.delete()
        }
    }

    private fun strings(a: JSONArray?): List<String> = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }

    private fun load(root: JSONObject) {
        val l = Ledger()
        l.ignored.addAll(strings(root.optJSONArray("ignored")))
        val es = root.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until es.length()) {
            val o = es.getJSONObject(i)
            val type = TrackerType.fromWire(o.getString("type")) ?: continue
            val e = TrackerEntry(o.getString("id"), type)
            e.keys.addAll(strings(o.optJSONArray("keys")))
            e.addresses.addAll(strings(o.optJSONArray("addresses")))
            e.episodeStartMs = o.optLong("start")
            e.lastSeenMs = o.optLong("last")
            e.sightings = o.optInt("n")
            e.lastRssi = o.optInt("rssi")
            e.hasLocation = o.optBoolean("loc")
            e.alerted = o.optBoolean("alerted")
            val ps = o.optJSONArray("places") ?: JSONArray()
            for (j in 0 until ps.length()) {
                val p = ps.getJSONArray(j)
                e.places.add(Place(p.getDouble(0), p.getDouble(1), p.getLong(2)))
            }
            l.entries.add(e)
        }
        ledger = l
        alerts.clear()
        val aa = root.optJSONArray("alerts") ?: JSONArray()
        for (i in 0 until aa.length()) {
            val o = aa.getJSONObject(i)
            val type = TrackerType.fromWire(o.getString("type")) ?: continue
            alerts.add(
                AlertRecord(
                    o.getLong("ts"), o.getString("entry"), type, o.optLong("dur"), o.optInt("places"),
                    o.optInt("rssi"), strings(o.optJSONArray("ids")), o.optBoolean("ignored"),
                ),
            )
        }
    }
}
