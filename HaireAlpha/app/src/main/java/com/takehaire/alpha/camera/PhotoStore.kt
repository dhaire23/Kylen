package com.takehaire.alpha.camera

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PhotoItem(val uri: Uri, val name: String, val dateTaken: Long, val mime: String, val favorite: Boolean, val width: Int, val height: Int)

/**
 * MediaStore storage in DCIM/HAIRE Alpha (shows up as its own album in Samsung Gallery).
 * Writes are transactional: an item is created IS_PENDING, fully written, then published; any
 * failure deletes the pending row so a broken file never appears as a photograph.
 */
class PhotoStore(private val context: Context) {
    private val resolver = context.contentResolver
    private val collection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private var lastName = ""
    private var counter = 0

    @Synchronized
    fun newBaseName(timeMs: Long): String {
        val base = "HAIRE_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(timeMs))
        if (base == lastName) counter++ else { counter = 0; lastName = base }
        return if (counter == 0) base else "${base}_$counter"
    }

    /** Bytes free on primary storage; capture is refused below ~150 MB to avoid partial files. */
    fun freeBytes(): Long = try {
        StatFs(Environment.getExternalStorageDirectory().path).availableBytes
    } catch (_: Exception) {
        Long.MAX_VALUE
    }

    fun hasSpaceForCapture(): Boolean = freeBytes() > 150L * 1024 * 1024

    fun save(displayName: String, mime: String, timeMs: Long, width: Int, height: Int, writer: (OutputStream) -> Unit): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$ALBUM")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.Images.Media.DATE_TAKEN, timeMs)
            if (width > 0) put(MediaStore.MediaColumns.WIDTH, width)
            if (height > 0) put(MediaStore.MediaColumns.HEIGHT, height)
        }
        val uri = resolver.insert(collection, values) ?: throw IllegalStateException("Could not create the photo in the gallery")
        try {
            resolver.openOutputStream(uri, "w")?.use { writer(it) } ?: throw IllegalStateException("Could not open the photo for writing")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri
        } catch (e: Throwable) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            throw e
        }
    }

    fun saveFile(displayName: String, mime: String, timeMs: Long, width: Int, height: Int, file: File): Uri =
        save(displayName, mime, timeMs, width, height) { out -> file.inputStream().use { it.copyTo(out, 1 shl 16) } }

    fun list(limit: Int = 500): List<PhotoItem> {
        val out = ArrayList<PhotoItem>()
        val proj = mutableListOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
        )
        if (Build.VERSION.SDK_INT >= 30) proj.add(MediaStore.MediaColumns.IS_FAVORITE)
        val sel = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        try {
            resolver.query(collection, proj.toTypedArray(), sel, arrayOf("%$ALBUM%"), "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val iMime = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val iFav = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE) else -1
                while (c.moveToNext() && out.size < limit) {
                    out.add(
                        PhotoItem(
                            ContentUris.withAppendedId(collection, c.getLong(iId)), c.getString(iName) ?: "", c.getLong(iDate),
                            c.getString(iMime) ?: "image/jpeg", iFav >= 0 && c.getInt(iFav) == 1, c.getInt(iW), c.getInt(iH),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Own items can be favourited directly; returns false if the system requires a consent request. */
    fun setFavorite(uri: Uri, fav: Boolean): Boolean = try {
        if (Build.VERSION.SDK_INT < 30) false
        else resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_FAVORITE, if (fav) 1 else 0) }, null, null) > 0
    } catch (_: Exception) {
        false
    }

    /** Deletes an item this app created. Throws RecoverableSecurityException-style errors for others. */
    fun delete(uri: Uri): Boolean = resolver.delete(uri, null, null) > 0

    companion object {
        const val ALBUM = "HAIRE Alpha"
    }
}
