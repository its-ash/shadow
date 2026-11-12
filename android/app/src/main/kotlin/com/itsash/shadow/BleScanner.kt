package com.itsash.shadow

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid

/** Thin wrapper over the platform BLE scanner. Every result is parsed from the raw advertisement bytes. */
class BleScanner(context: Context) {
    private val adapter: BluetoothAdapter? =
        context.applicationContext.getSystemService(BluetoothManager::class.java)?.adapter
    private var callback: ScanCallback? = null
    private var scanner: BluetoothLeScanner? = null

    val available: Boolean get() = adapter?.isEnabled == true

    /** Starts a scan. Returns false when Bluetooth is off or a permission is missing. */
    @SuppressLint("MissingPermission")
    fun start(filters: List<ScanFilter>, lowLatency: Boolean, onAdv: (String, Adv, Int) -> Unit): Boolean {
        stop()
        val s = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return false
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = deliver(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { deliver(it) }
            private fun deliver(r: ScanResult) {
                val raw = r.scanRecord?.bytes ?: return
                onAdv(r.device.address, AdParser.parse(raw), r.rssi)
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(if (lowLatency) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_LOW_POWER)
            .build()
        return try {
            s.startScan(filters, settings, cb)
            scanner = s
            callback = cb
            true
        } catch (e: SecurityException) {
            false
        } catch (e: RuntimeException) {
            // IllegalStateException / IllegalArgumentException (a filter the stack rejects)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val cb = callback ?: return
        runCatching { scanner?.stopScan(cb) }
        callback = null
        scanner = null
    }

    companion object {
        private fun uuid(id: Int) = ParcelUuid.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", id))

        /**
         * Filters for background scans. Android 8.1+ ignores unfiltered scans while the screen is off, so
         * the service must filter. Each entry matches one row of [TrackerFormats]. UNCERTAIN: whether every
         * vendor's tag advertises the UUID as a service UUID, as service data, or both, so both forms are listed.
         */
        fun trackerFilters(): List<ScanFilter> {
            val list = ArrayList<ScanFilter>()
            // Apple Find My: manufacturer 0x004C, data starts 0x12 0x19.
            list.add(
                ScanFilter.Builder()
                    .setManufacturerData(TrackerFormats.APPLE, byteArrayOf(0x12, 0x19), byteArrayOf(0xFF.toByte(), 0xFF.toByte()))
                    .build(),
            )
            // Google FMDN: Eddystone service data, frame 0x40 or 0x41 (mask 0xFE matches both).
            list.add(
                ScanFilter.Builder()
                    .setServiceData(uuid(TrackerFormats.UUID_EDDYSTONE), byteArrayOf(0x40), byteArrayOf(0xFE.toByte()))
                    .build(),
            )
            for (id in intArrayOf(TrackerFormats.UUID_SAMSUNG, TrackerFormats.UUID_TILE, TrackerFormats.UUID_TILE_2, TrackerFormats.UUID_CHIPOLO)) {
                list.add(ScanFilter.Builder().setServiceUuid(uuid(id)).build())
                list.add(ScanFilter.Builder().setServiceData(uuid(id), byteArrayOf(), byteArrayOf()).build())
            }
            return list
        }
    }
}
