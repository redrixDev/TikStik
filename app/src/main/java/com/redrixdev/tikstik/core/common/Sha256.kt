// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.common

import java.security.MessageDigest

/** The id of an original: its SHA-256 in hex. */
fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).toHexString()
