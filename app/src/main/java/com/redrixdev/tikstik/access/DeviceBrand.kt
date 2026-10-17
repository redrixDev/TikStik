// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

/** Brands with their own help in the wizard (docs/FABRICANTES.md); the rest use the AOSP one. */
enum class DeviceBrand {
    SAMSUNG,
    XIAOMI,
    OPPO,
    ;

    companion object {
        // Redmi and POCO report themselves as Xiaomi; realme and OnePlus share the ColorOS base.
        fun of(manufacturer: String): DeviceBrand? = when (manufacturer.trim().lowercase()) {
            "samsung" -> SAMSUNG
            "xiaomi", "redmi", "poco" -> XIAOMI
            "oppo", "realme", "oneplus" -> OPPO
            else -> null
        }
    }
}
