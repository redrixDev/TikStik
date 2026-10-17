// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

/** Why the TikTok cache could not be read; each case has its own user-facing message. */
enum class AccessError {
    NOT_CONFIGURED,
    NO_WIFI,
    WIRELESS_DEBUGGING_OFF,
    NETWORK_NOT_ALLOWED,
    CONNECTION_FAILED,
    PAIRING_LOST,

    // The shell cannot enter the cache (some Android 11 builds do not give it `ext_data_rw`).
    CACHE_DENIED,
}

class AccessException(val error: AccessError, cause: Throwable? = null) : Exception(error.name, cause)
