package com.openbubbles.wear.bridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.provider.ContactsContract
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

/** Small app-private cache of normalized contact thumbnails. */
object AvatarCache {
    private const val DIR = "openbubbles_wear_avatars"
    private const val MAX_SOURCE = 4 * 1024 * 1024
    private const val MAX_FILES = 200
    private const val MAX_BYTES = 6L * 1024 * 1024
    private val lock = Any()

    fun cache(context: Context, source: Uri?): String? {
        source ?: return null
        val app = context.applicationContext
        return runCatching {
            val bytes = when (source.scheme) {
                "file" -> {
                    val file = File(source.path ?: return null).canonicalFile
                    val roots = listOf(app.filesDir, File(app.getDir("flutter", Context.MODE_PRIVATE), "avatars"))
                    if (!file.isFile || roots.none { file.toPath().startsWith(it.canonicalFile.toPath()) }) return null
                    file.inputStream().use(::readBounded)
                }
                "content" -> {
                    if (source.authority != ContactsContract.AUTHORITY || !hasContacts(app)) return null
                    app.contentResolver.openInputStream(source)?.use(::readBounded) ?: return null
                }
                else -> return null
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val longest = max(bitmap.width, bitmap.height)
            val scaled = if (longest > 128) {
                val ratio = 128f / longest
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                    (bitmap.height * ratio).roundToInt().coerceAtLeast(1), true).also { if (it !== bitmap) bitmap.recycle() }
            } else bitmap
            val prepared = try {
                val output = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
                output.toByteArray()
            } finally {
                scaled.recycle()
            }
            store(app, prepared)
        }.getOrNull()
    }

    data class Member(val name: String, val avatarId: String?)

    fun group(context: Context, members: List<Member>): String? {
        if (members.isEmpty()) return null
        return runCatching {
            val sorted = members.sortedByDescending { it.avatarId != null }.take(4)
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.rgb(48, 50, 55))
                val count = sorted.size
                val diameter = (1f - count * 0.07f) * 128f * 0.65f
                val orbit = (128f - (1f - count * 0.07f) * 64f - 20.48f) / 2f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                sorted.forEachIndexed { index, member ->
                    val angle = index.toDouble() / count * Math.PI * 2 + Math.PI * 1.25
                    val x = 64f - orbit * cos(angle).toFloat()
                    val y = 64f + orbit * sin(angle).toFloat()
                    val extra = index == 3 && members.size > 4
                    val photo = if (extra) null else member.avatarId?.let { info(context, it) }
                        ?.let { BitmapFactory.decodeFile(it.file.path) }
                    if (photo != null) {
                        try {
                            val scale = diameter / minOf(photo.width, photo.height)
                            val matrix = Matrix().apply {
                                setScale(scale, scale)
                                postTranslate(x - photo.width * scale / 2, y - photo.height * scale / 2)
                            }
                            paint.shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                                setLocalMatrix(matrix)
                            }
                            canvas.drawCircle(x, y, diameter / 2, paint)
                            paint.shader = null
                        } finally { photo.recycle() }
                    } else {
                        paint.color = Color.rgb(82, 86, 94)
                        canvas.drawCircle(x, y, diameter / 2, paint)
                        paint.color = Color.WHITE
                        paint.textSize = diameter * 0.36f
                        paint.textAlign = Paint.Align.CENTER
                        val initials = member.name.trim().split(Regex("\\s+"))
                            .filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
                        canvas.drawText(if (extra) "+${members.size - 3}" else initials.ifEmpty { "?" },
                            x, y - (paint.ascent() + paint.descent()) / 2, paint)
                    }
                }
                val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, this) }
                store(context, bytes.toByteArray())
            } finally { bitmap.recycle() }
        }.getOrNull()
    }

    private fun store(context: Context, prepared: ByteArray): String? {
        val id = "avatar-" + MessageDigest.getInstance("SHA-256").digest(prepared).joinToString("") { "%02x".format(it) }
        val dir = directory(context)
        synchronized(lock) {
            dir.mkdirs()
            val target = File(dir, "$id.jpg")
            if (!target.isFile) {
                val temp = File(dir, "$id.tmp")
                temp.writeBytes(prepared)
                if (!temp.renameTo(target)) { temp.delete(); return null }
            }
            target.setLastModified(System.currentTimeMillis())
            prune(dir, target)
        }
        return id
    }

    fun forAddress(context: Context, address: String?): String? {
        if (!hasContacts(context)) return null
        val normalized = com.openbubbles.wear.protocol.Protocol.normalizeAddress(address) ?: return null
        val uri: Uri
        val projection = arrayOf(ContactsContract.Contacts.PHOTO_URI)
        val selection: String?
        val args: Array<String>?
        if (normalized.contains('@')) {
            uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_LOOKUP_URI, Uri.encode(normalized))
            selection = null
            args = null
        } else {
            uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(normalized))
            selection = null
            args = null
        }
        return runCatching {
            context.contentResolver.query(uri, projection, selection, args, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.let { raw -> cache(context, Uri.parse(raw)) } else null
            }
        }.getOrNull()
    }

    fun info(context: Context, avatarId: String): MediaInfo? = synchronized(lock) {
        if (!avatarId.matches(Regex("avatar-[0-9a-f]{64}"))) return@synchronized null
        val file = File(directory(context.applicationContext), "$avatarId.jpg")
        if (!file.isFile || file.length() <= 0) return@synchronized null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth !in 1..128 || bounds.outHeight !in 1..128) return@synchronized null
        file.setLastModified(System.currentTimeMillis())
        MediaInfo(avatarId, file, "image/jpeg", bounds.outWidth, bounds.outHeight, file.length(), file.lastModified())
    }

    fun ids(context: Context): Set<String> = synchronized(lock) {
        directory(context.applicationContext).listFiles()?.mapNotNull { f ->
            val id = f.name.removeSuffix(".jpg")
            id.takeIf { f.isFile && f.length() > 0 && f.name == "$id.jpg" && it.matches(Regex("avatar-[0-9a-f]{64}")) }
        }?.toSet().orEmpty()
    }

    fun clear(context: Context) = synchronized(lock) {
        directory(context.applicationContext).listFiles()?.forEach { it.delete() }
        Unit
    }

    private fun hasContacts(context: Context) = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    private fun directory(context: Context) = File(context.filesDir, DIR)
    private fun readBounded(input: java.io.InputStream): ByteArray {
        val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); var total = 0
        while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= MAX_SOURCE); out.write(buffer, 0, n) }
        return out.toByteArray()
    }
    private fun prune(dir: File, newest: File) {
        val files = dir.listFiles()?.filter { it.isFile && it.name.matches(Regex("avatar-[0-9a-f]{64}\\.jpg")) }
            ?.sortedWith(compareByDescending<File> { it == newest }.thenByDescending { it.lastModified() }).orEmpty()
        var bytes = 0L
        var kept = 0
        files.forEach { file ->
            if (kept < MAX_FILES && bytes + file.length() <= MAX_BYTES) {
                bytes += file.length()
                kept++
            } else file.delete()
        }
    }
}
