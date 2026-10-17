// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.common

/** The current time in milliseconds; tests substitute a fixed one. */
fun interface Clock {
    fun now(): Long
}
