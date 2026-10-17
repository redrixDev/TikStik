// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.di

import com.redrixdev.tikstik.whatsapp.AndroidWhatsApp
import com.redrixdev.tikstik.whatsapp.WhatsAppGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WhatsAppModule {

    @Binds
    abstract fun gateway(impl: AndroidWhatsApp): WhatsAppGateway
}
