package com.itsash.shadow

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import java.text.DateFormat
import java.util.Date

/**
 * Foreground service behind the "Background protection" switch.
 *
 * Every [CYCLE_MS] it wakes (an inexact allow-while-idle alarm, so Doze can delay it), holds a short wake lock,
 * scans with tracker ScanFilters for [SCAN_MS], records what it saw in [ShadowState]'s ledger together with a
 * coarse location, and raises a notification when [Ledger.takeDue] says a tracker has travelled with the user.
 * Service type: connectedDevice (Bluetooth scanning) plus location when the permission is granted.
 */
class ProtectionService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: ShadowPrefs
    private lateinit var nm: NotificationManager
    private lateinit var alarms: AlarmManager
    private lateinit var scanner: BleScanner
    private var wakeLock: PowerManager.WakeLock? = null
    private var lm: LocationManager? = null

    private var scanning = false
    private var lastFix: Location? = null
    private var lastStatus = ""
    private val batch = LinkedHashMap<String, Pair<Adv, Int>>()

    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            runCycle()
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastFix = location
        }

        @Deprecated("Required on API < 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ShadowState.init(this)
        prefs = ShadowPrefs(this)
        nm = getSystemService(NotificationManager::class.java)
        alarms = getSystemService(AlarmManager::class.java)
        scanner = BleScanner(this)
        createChannels()
        if (!enterForeground()) {
            stopSelf()
            return
        }
        val filter = IntentFilter(ACTION_TICK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tickReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(tickReceiver, filter)
        }
        startLocationUpdates()
        running = true
        handler.post { runCycle() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.protection) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(tickReceiver) }
        runCatching { lm?.removeUpdates(locationListener) }
        alarms.cancel(tickIntent())
        scanner.stop()
        releaseWakeLock()
        super.onDestroy()
    }

    // --- one scan cycle --------------------------------------------------------------------

    @SuppressLint("WakelockTimeout")
    private fun runCycle() {
        if (!prefs.protection) {
            stopSelf()
            return
        }
        if (scanning) return
        scheduleNext()
        if (!Perms.bluetooth(this)) {
            setStatus("Bluetooth permission missing. Open Shadow to allow it.")
            return
        }
        batch.clear()
        val started = scanner.start(BleScanner.trackerFilters(), lowLatency = false) { addr, adv, rssi ->
            batch[addr] = adv to rssi
        }
        if (!started) {
            setStatus("Bluetooth is off. Turn it on to keep watching.")
            return
        }
        scanning = true
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "shadow:scan")
            .apply {
                setReferenceCounted(false)
                acquire(SCAN_MS + 15_000L)
            }
        handler.postDelayed({ finishCycle() }, SCAN_MS)
    }

    private fun finishCycle() {
        scanner.stop()
        scanning = false
        val now = System.currentTimeMillis()
        val fix = currentLocation(now)
        var nearby = 0
        val due = ShadowState.withLedger { ledger ->
            for ((addr, v) in batch) {
                val cls = TrackerFormats.classify(v.first) ?: continue
                if (!cls.type.alerts) continue
                nearby++
                ledger.record(Sighting(addr, cls.key, cls.type, v.second, now, fix?.latitude, fix?.longitude))
            }
            ledger.prune(now)
            ledger.takeDue().map {
                AlertRecord(now, it.id, it.type, it.durationMs(), it.places.size, it.lastRssi, it.ignoreIds().toList(), false)
            }
        }
        batch.clear()
        for (a in due) {
            ShadowState.addAlert(a)
            notifyAlert(a)
        }
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now))
        val loc = if (fix == null) " No location." else ""
        setStatus("Last check $time: $nearby tracker${if (nearby == 1) "" else "s"} nearby.$loc")
        releaseWakeLock()
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    private fun tickIntent(): PendingIntent = PendingIntent.getBroadcast(
        this,
        0,
        Intent(ACTION_TICK).setPackage(packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleNext() {
        runCatching {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + CYCLE_MS, tickIntent())
        }
    }

    // --- location ---------------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!Perms.location(this)) return
        val m = getSystemService(LocationManager::class.java) ?: return
        lm = m
        for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            runCatching {
                if (m.allProviders.contains(p)) m.requestLocationUpdates(p, LOCATION_INTERVAL_MS, 0f, locationListener, Looper.getMainLooper())
            }
        }
    }

    /** The newest fix younger than [MAX_FIX_AGE_MS], from our own updates or the system's last known. */
    @SuppressLint("MissingPermission")
    private fun currentLocation(now: Long): Location? {
        if (!Perms.location(this)) return null
        val candidates = ArrayList<Location>()
        lastFix?.let { candidates.add(it) }
        val m = lm ?: getSystemService(LocationManager::class.java)
        for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            runCatching { m?.getLastKnownLocation(p)?.let { candidates.add(it) } }
        }
        return candidates.filter { now - it.time <= MAX_FIX_AGE_MS }.maxByOrNull { it.time }
    }

    // --- notifications -----------------------------------------------------------------------

    private fun createChannels() {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Protection running", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing notification while Shadow watches for trackers."
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Tracker alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "An unknown tracker seems to travel with you."
            },
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun statusNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_shadow)
            .setContentTitle("Shadow is watching for trackers")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()

    private fun setStatus(text: String) {
        if (text == lastStatus) return
        lastStatus = text
        nm.notify(NOTIFICATION_STATUS, statusNotification(text))
    }

    private fun notifyAlert(a: AlertRecord) {
        val mins = a.durationMs / 60_000
        val where = if (a.places >= 2) " and ${a.places} different places" else ""
        val n = Notification.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_shadow)
            .setContentTitle("A tracker may be following you")
            .setContentText("${a.type.label} has been near you for $mins min$where.")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "${a.type.label} has been near you for $mins min$where. If it is yours or a family member's, " +
                        "open Shadow and tap Ignore. Otherwise use Scan now to find it.",
                ),
            )
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .build()
        nm.notify(NOTIFICATION_ALERT_BASE + (a.entryId.hashCode() and 0xFFFF), n)
    }

    @SuppressLint("InlinedApi")
    private fun enterForeground(): Boolean {
        val n = statusNotification("Starting...")
        lastStatus = "Starting..."
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return try {
                startForeground(NOTIFICATION_STATUS, n)
                true
            } catch (e: Exception) {
                false
            }
        }
        val full = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
            (if (Perms.location(this)) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        // Starting a location service from the background (for example after a reboot) can be refused;
        // then run as a plain connectedDevice service, without location.
        for (type in intArrayOf(full, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)) {
            try {
                startForeground(NOTIFICATION_STATUS, n, type)
                return true
            } catch (e: Exception) {
                // try the next type
            }
        }
        return false
    }

    companion object {
        private const val ACTION_TICK = "com.itsash.shadow.SCAN_TICK"
        private const val CHANNEL_STATUS = "shadow_status"
        private const val CHANNEL_ALERT = "shadow_alert"
        private const val NOTIFICATION_STATUS = 21
        private const val NOTIFICATION_ALERT_BASE = 1000

        /** Scan 20 s every 3 minutes. */
        const val SCAN_MS = 20_000L
        const val CYCLE_MS = 3 * 60_000L
        private const val LOCATION_INTERVAL_MS = 2 * 60_000L
        private const val MAX_FIX_AGE_MS = 15 * 60_000L

        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ProtectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProtectionService::class.java))
        }

        fun sync(context: Context) {
            if (ShadowPrefs(context).protection) start(context) else stop(context)
        }
    }
}

/** Restarts protection after a reboot or an app update, if it is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (ShadowPrefs(context).protection && Perms.bluetooth(context)) {
                    runCatching { ProtectionService.start(context) }
                }
            }
        }
    }
}
