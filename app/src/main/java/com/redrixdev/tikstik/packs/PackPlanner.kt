// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.packs

import com.redrixdev.tikstik.whatsapp.PackRules

/** Pure split of the selection into new packs: full ones of 30 and the last with at least 3 when there is enough. */
object PackPlanner {

    /** No duplicates and none lost; fewer than 3 gives a small pack, which WhatsApp rejects until it is filled. */
    fun <T> split(items: List<T>): List<List<T>> {
        val unique = items.distinct()
        if (unique.isEmpty()) return emptyList()
        val max = PackRules.MAX_STICKERS
        val min = PackRules.MIN_STICKERS
        val sizes = MutableList((unique.size + max - 1) / max) { max }
        sizes[sizes.lastIndex] = unique.size - max * (sizes.size - 1)
        // The last one borrows from the previous whatever it lacks to reach 3.
        if (sizes.size > 1 && sizes.last() < min) {
            sizes[sizes.lastIndex - 1] -= min - sizes.last()
            sizes[sizes.lastIndex] = min
        }
        var from = 0
        return sizes.map { size -> unique.subList(from, from + size).also { from += size } }
    }

    /** How many of [wanted] fit in a pack that already has [inside]: the ones it lacks, up to full. */
    fun <T> fitting(wanted: List<T>, inside: Collection<T>): List<T> {
        val room = (PackRules.MAX_STICKERS - inside.size).coerceAtLeast(0)
        return wanted.distinct().filterNot { it in inside }.take(room)
    }
}
