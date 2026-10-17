// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.ui

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/** App-wide notices: the ViewModels post them and the root [NoticeHost] shows them. */
@Singleton
class Notices @Inject constructor() {
    // A buffered channel: what is posted while nobody listens (activity recreation) waits for the NoticeHost to come back.
    private val channel = Channel<Notice>(capacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val notices: Flow<Notice> = channel.receiveAsFlow()

    fun post(notice: Notice) {
        channel.trySend(notice)
    }
}
