// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.update

/** A release number such as `1.2.3` or `v1.2.3`; a missing patch counts as 0 and anything after the numbers is ignored. */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {

    override fun compareTo(other: Version): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        // Never from the middle of a number and always each number whole: a cut one would read as another version.
        private val NUMBERS = Regex("""(?<![\d.])v?(\d+)\.(\d+)(?:\.(\d+))?""")

        fun parse(text: String): Version? {
            val match = NUMBERS.find(text) ?: return null
            val (major, minor, patch) = match.groupValues.drop(1).map { it.ifEmpty { "0" }.toIntOrNull() ?: return null }
            return Version(major, minor, patch)
        }
    }
}
