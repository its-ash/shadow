package com.itsash.shadow

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Runtime permission checks shared by the activity and the service. */
object Perms {
    private fun granted(c: Context, p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Android 12+: BLUETOOTH_SCAN. Older: location is what allows BLE scans. */
    fun bluetooth(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(c, Manifest.permission.BLUETOOTH_SCAN) && granted(c, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(c, Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun location(c: Context): Boolean =
        granted(c, Manifest.permission.ACCESS_FINE_LOCATION) || granted(c, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun notifications(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(c, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        }

    /** What to ask in one go. */
    fun toRequest(c: Context): Array<String> {
        val list = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list.add(Manifest.permission.POST_NOTIFICATIONS)
        return list.filter { !granted(c, it) }.toTypedArray()
    }
}
