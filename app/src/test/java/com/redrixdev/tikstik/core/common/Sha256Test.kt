// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Sha256Test {

    // Every saved original is named by this exact text: lowercase hex with no separators.
    @Test
    fun matchesTheStandardTestVector() {
        assertThat("abc".encodeToByteArray().sha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
}
