// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The system settings that matter for access; replaced in tests. */
interface DeviceState {
    val devOptionsEnabled: Boolean
    val wirelessDebuggingOn: Boolean

    /** The shell already granted `WRITE_SECURE_SETTINGS`: TikStik can turn debugging on and off. */
    val canToggleWirelessDebugging: Boolean
    val onWifi: Boolean
    val packageName: String

    fun setWirelessDebugging(on: Boolean): Boolean
}

class AndroidDeviceState @Inject constructor(@param:ApplicationContext private val context: Context) : DeviceState {

    override val devOptionsEnabled: Boolean
        get() = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    override val wirelessDebuggingOn: Boolean
        get() = Settings.Global.getInt(context.contentResolver, ADB_WIFI_ENABLED, 0) == 1

    override val canToggleWirelessDebugging: Boolean
        get() = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    override val onWifi: Boolean
        get() {
            val connectivity = context.getSystemService<ConnectivityManager>() ?: return false
            return connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }

    override val packageName: String get() = context.packageName

    override fun setWirelessDebugging(on: Boolean): Boolean =
        runCatching { Settings.Global.putInt(context.contentResolver, ADB_WIFI_ENABLED, if (on) 1 else 0) }.getOrDefault(false)

    companion object {
        // The "Wireless debugging" key in Settings.Global (AOSP); there is no public constant.
        private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

        fun aboutPhoneIntent(): Intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)

        // Developer options with "Wireless debugging" highlighted, if the manufacturer honors the key.
        fun wirelessDebuggingIntent(): Intent {
            val key = "toggle_adb_wireless"
            return Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .putExtra(":settings:fragment_args_key", key)
                .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", key) })
        }
    }
}
