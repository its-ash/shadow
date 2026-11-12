package com.itsash.shadow

import android.bluetooth.BluetoothManager
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

/**
 * MethodChannel "com.itsash.shadow/control" (Dart: lib/shadow/shadow_platform.dart):
 *   - getConfig() -> {protection, running}
 *   - setProtection({value}): saves the switch and starts/stops [ProtectionService]. Needs the Bluetooth permission.
 *   - status() -> {bluetoothSupported, bluetoothOn, bluetoothPermission, locationPermission, notifications,
 *                  batteryOptimizationsIgnored}
 *   - requestPermissions() -> status, after the system dialog closes.
 *   - openPermission({name}): "app" | "bluetooth" | "battery".
 *   - alerts() -> [{ts, entryId, type, label, durationMs, places, rssi, ignored, ids}], newest first.
 *   - ignore({ids}), ignoredCount(), clearIgnored(), clearData({includeIgnored}).
 * EventChannel "com.itsash.shadow/scan": args {seconds}. One map per second:
 *   {elapsedMs, totalMs, done, devices: [{id, address, type, label, certain, rssi, avgRssi, count, ageMs}]}.
 *   Cancelling the stream stops the scan. A failed start is an error event "scan_failed".
 */
class MainActivity : FlutterActivity() {

    private val ui = Handler(Looper.getMainLooper())
    private var pendingPermissions: MethodChannel.Result? = null
    private var scanTick: Runnable? = null
    private var scanner: BleScanner? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        ShadowState.init(this)
        val messenger = flutterEngine.dartExecutor.binaryMessenger
        val prefs = ShadowPrefs(this)

        MethodChannel(messenger, "com.itsash.shadow/control").setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "getConfig" -> result.success(mapOf("protection" to prefs.protection, "running" to ProtectionService.running))
                    "setProtection" -> {
                        val on = call.argument<Boolean>("value") ?: false
                        if (on && !Perms.bluetooth(this)) {
                            result.error("permission", "Bluetooth permission is needed.", null)
                        } else {
                            prefs.protection = on
                            ProtectionService.sync(this)
                            result.success(null)
                        }
                    }
                    "status" -> result.success(status())
                    "requestPermissions" -> {
                        val missing = Perms.toRequest(this)
                        if (missing.isEmpty() || pendingPermissions != null) {
                            result.success(status())
                        } else {
                            pendingPermissions = result
                            requestPermissions(missing, REQUEST_PERMISSIONS)
                        }
                    }
                    "openPermission" -> {
                        openPermission(call.argument<String>("name"))
                        result.success(null)
                    }
                    "alerts" -> result.success(ShadowState.alertMaps())
                    "ignore" -> {
                        ShadowState.ignore(call.argument<List<String>>("ids") ?: emptyList())
                        result.success(null)
                    }
                    "ignoredCount" -> result.success(ShadowState.ignoredCount())
                    "clearIgnored" -> {
                        ShadowState.clearIgnored()
                        result.success(null)
                    }
                    "clearData" -> {
                        ShadowState.clearAll(call.argument<Boolean>("includeIgnored") ?: false)
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            } catch (e: Throwable) {
                result.error("failed", e.message, null)
            }
        }

        EventChannel(messenger, "com.itsash.shadow/scan").setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                val seconds = ((arguments as? Map<*, *>)?.get("seconds") as? Number)?.toInt() ?: 90
                startScan(seconds.coerceIn(10, 600), events)
            }

            override fun onCancel(arguments: Any?) = stopScan()
        })
    }

    private fun startScan(seconds: Int, events: EventChannel.EventSink) {
        stopScan()
        if (!Perms.bluetooth(this)) {
            events.error("scan_failed", "permission", null)
            return
        }
        val session = ScanSession(ShadowState.ignoredIds())
        val sc = BleScanner(this)
        scanner = sc
        val ok = sc.start(emptyList(), lowLatency = true) { addr, adv, rssi ->
            session.add(addr, adv, rssi, System.currentTimeMillis())
        }
        if (!ok) {
            scanner = null
            events.error("scan_failed", "bluetooth_off", null)
            return
        }
        val begin = System.currentTimeMillis()
        val total = seconds * 1000L
        val tick = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val done = now - begin >= total
                if (done) sc.stop()
                events.success(
                    mapOf(
                        "elapsedMs" to minOf(now - begin, total),
                        "totalMs" to total,
                        "done" to done,
                        "devices" to session.snapshot().map {
                            mapOf(
                                "id" to it.id,
                                "address" to it.address,
                                "type" to it.type.wire,
                                "label" to it.type.label,
                                "certain" to it.type.certain,
                                "rssi" to it.rssi,
                                "avgRssi" to it.avgRssi,
                                "count" to it.count,
                                "ageMs" to (now - it.lastSeenMs),
                            )
                        },
                    ),
                )
                if (!done) ui.postDelayed(this, 1000) else scanTick = null
            }
        }
        scanTick = tick
        tick.run()
    }

    private fun stopScan() {
        scanTick?.let { ui.removeCallbacks(it) }
        scanTick = null
        scanner?.stop()
        scanner = null
    }

    override fun onDestroy() {
        stopScan()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            pendingPermissions?.success(status())
            pendingPermissions = null
        }
    }

    private fun status(): Map<String, Any?> {
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        val power = getSystemService(PowerManager::class.java)
        return mapOf(
            "bluetoothSupported" to (adapter != null && packageManager.hasSystemFeature("android.hardware.bluetooth_le")),
            "bluetoothOn" to (adapter?.isEnabled == true),
            "bluetoothPermission" to Perms.bluetooth(this),
            "locationPermission" to Perms.location(this),
            "notifications" to Perms.notifications(this),
            "batteryOptimizationsIgnored" to power.isIgnoringBatteryOptimizations(packageName),
        )
    }

    private fun openPermission(name: String?) {
        when (name) {
            "app" -> startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            )
            "bluetooth" -> startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "battery" -> {
                @Suppress("BatteryLife")
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 42
    }
}
