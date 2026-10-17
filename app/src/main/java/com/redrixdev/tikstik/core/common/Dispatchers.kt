// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.core.common

import javax.inject.Qualifier

/** Dispatcher for disk and network. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** For computation while the user waits (reviewing what was read from TikTok). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Background-priority threads for converting without competing with the UI. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ConversionDispatcher
