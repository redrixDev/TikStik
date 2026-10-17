// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VersionTest {

    @Test
    fun readsTagsAndNames() {
        assertThat(Version.parse("v1.2.3")).isEqualTo(Version(1, 2, 3))
        assertThat(Version.parse("1.2")).isEqualTo(Version(1, 2, 0))
        assertThat(Version.parse("TikStik 2.0.1")).isEqualTo(Version(2, 0, 1))
        assertThat(Version.parse("1.4.0-beta.2")).isEqualTo(Version(1, 4, 0))
        assertThat(Version.parse("latest")).isNull()
    }

    @Test
    fun takesTheWholeNumberWhereverItStarts() {
        assertThat(Version.parse("rc1.2.3")).isEqualTo(Version(1, 2, 3))
        assertThat(Version.parse("TikStikv1.2.3")).isEqualTo(Version(1, 2, 3))
        // Cutting a number short would read another version; one too big for an Int is no version.
        assertThat(Version.parse("1.2.34567890123")).isNull()
        assertThat(Version.parse("12345678901.0")).isNull()
    }

    @Test
    fun ordersByNumberNotByText() {
        assertThat(Version(1, 10, 0)).isGreaterThan(Version(1, 9, 9))
        assertThat(Version(2, 0, 0)).isGreaterThan(Version(1, 99, 99))
        assertThat(Version(1, 0, 1)).isGreaterThan(Version(1, 0, 0))
        assertThat(Version(1, 0, 0)).isEquivalentAccordingToCompareTo(Version(1, 0, 0))
        assertThat(Version(1, 0, 0).toString()).isEqualTo("1.0.0")
    }
}
