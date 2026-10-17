// SPDX-FileCopyrightText: 2026 redrixDev
// SPDX-License-Identifier: GPL-3.0-or-later

package com.redrixdev.tikstik.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.redrixdev.tikstik.core.data.db.PackWithStickers
import com.redrixdev.tikstik.packs.PackRepository
import com.redrixdev.tikstik.packs.WhatsAppCopies
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/** The official WhatsApp contract (StickerContentProvider.java in the stickers repository), read-only. */
class StickerContentProvider : ContentProvider() {

    // Created before Application.onCreate: the Hilt graph is requested at query time.
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun packs(): PackRepository
        fun copies(): WhatsAppCopies
    }

    private val matcher = UriMatcher(UriMatcher.NO_MATCH)
    private lateinit var authority: String

    private val dependencies: Dependencies by lazy {
        EntryPointAccessors.fromApplication(requireNotNull(context).applicationContext, Dependencies::class.java)
    }

    override fun onCreate(): Boolean {
        authority = authority(requireNotNull(context).packageName)
        matcher.addURI(authority, METADATA, METADATA_ALL)
        matcher.addURI(authority, "$METADATA/*", METADATA_ONE)
        matcher.addURI(authority, "$STICKERS/*", STICKERS_OF)
        matcher.addURI(authority, "$STICKERS_ASSET/*/*", ASSET)
        return true
    }

    // Queries arrive over binder, off the main thread: waiting for Room here does not block the UI.
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor =
        when (matcher.match(uri)) {
            // WhatsApp accepts at most 10 packs per app: only the offered ones are listed.
            METADATA_ALL -> metadata(runBlocking { dependencies.packs().offered() }.filter(::servable).take(PackRules.MAX_PACKS))
            METADATA_ONE -> metadata(listOfNotNull(pack(uri.lastPathSegment)))
            STICKERS_OF -> stickers(pack(uri.lastPathSegment))
            else -> throw IllegalArgumentException("Unsupported URI")
        }.apply { setNotificationUri(requireNotNull(context).contentResolver, uri) }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        if (mode != "r" || matcher.match(uri) != ASSET) throw FileNotFoundException("Read only")
        val (packId, name) = uri.pathSegments.let { it[1] to it[2] }
        val pack = pack(packId) ?: throw FileNotFoundException()
        val file = when {
            name == TRAY_FILE -> dependencies.packs().tray(pack.pack.id)
            name.removeSuffix(STICKER_EXT) in pack.stickerIds -> dependencies.copies().file(name.removeSuffix(STICKER_EXT))
            else -> throw FileNotFoundException()
        }
        return AssetFileDescriptor(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun getType(uri: Uri): String = when (matcher.match(uri)) {
        METADATA_ALL -> "vnd.android.cursor.dir/vnd.$authority.$METADATA"
        METADATA_ONE -> "vnd.android.cursor.item/vnd.$authority.$METADATA"
        STICKERS_OF -> "vnd.android.cursor.dir/vnd.$authority.$STICKERS"
        ASSET -> if (uri.lastPathSegment == TRAY_FILE) "image/png" else "image/webp"
        else -> throw IllegalArgumentException("Unsupported URI")
    }

    // Individually, any valid pack is served even when not listed: WhatsApp may ask for one it already had.
    private fun pack(id: String?): PackWithStickers? =
        id?.let { runBlocking { dependencies.packs().get(it) } }?.takeIf(::servable)

    private fun servable(pack: PackWithStickers) = pack.items.size in PackRules.MIN_STICKERS..PackRules.MAX_STICKERS

    private fun metadata(packs: List<PackWithStickers>) = MatrixCursor(METADATA_COLUMNS).apply {
        for (pack in packs) {
            val p = pack.pack
            // No links or email (they are optional); whatsapp_will_not_cache_stickers is unused since WhatsApp 2.25.9.78.
            addRow(arrayOf<Any>(p.id, p.name, PUBLISHER, TRAY_FILE, "", "", "", "", "", "", p.imageDataVersion.toString(), 0, if (p.animated) 1 else 0))
        }
    }

    private fun stickers(pack: PackWithStickers?) = MatrixCursor(STICKER_COLUMNS).apply {
        // The accessibility text is optional, and a number would describe nothing.
        pack?.items?.sortedBy { it.position }?.forEach { item -> addRow(arrayOf<Any?>(item.stickerId + STICKER_EXT, item.emoji, null)) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()

    companion object {
        const val TRAY_FILE = "tray.png"
        private const val STICKER_EXT = ".webp"
        private const val PUBLISHER = "TikStik"
        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val STICKERS_ASSET = "stickers_asset"
        private const val METADATA_ALL = 1
        private const val METADATA_ONE = 2
        private const val STICKERS_OF = 3
        private const val ASSET = 4

        // Contract names: they cannot change without breaking communication with WhatsApp.
        private val METADATA_COLUMNS = arrayOf(
            "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher", "sticker_pack_icon",
            "android_play_store_link", "ios_app_download_link", "sticker_pack_publisher_email",
            "sticker_pack_publisher_website", "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website",
            "image_data_version", "whatsapp_will_not_cache_stickers", "animated_sticker_pack",
        )
        private val STICKER_COLUMNS = arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text")

        fun authority(packageName: String) = "$packageName.stickercontentprovider"
    }
}
