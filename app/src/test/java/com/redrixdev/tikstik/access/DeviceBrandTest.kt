// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.access

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeviceBrandTest {

    @Test
    fun manufacturersMapToTheirHelp() {
        assertThat(DeviceBrand.of("samsung")).isEqualTo(DeviceBrand.SAMSUNG)
        assertThat(DeviceBrand.of("Xiaomi")).isEqualTo(DeviceBrand.XIAOMI)
        assertThat(DeviceBrand.of("POCO")).isEqualTo(DeviceBrand.XIAOMI)
        assertThat(DeviceBrand.of("OnePlus")).isEqualTo(DeviceBrand.OPPO)
        assertThat(DeviceBrand.of(" realme ")).isEqualTo(DeviceBrand.OPPO)
        assertThat(DeviceBrand.of("Google")).isNull()
        assertThat(DeviceBrand.of("motorola")).isNull()
    }
}
