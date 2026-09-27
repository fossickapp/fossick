package com.fossick.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fossick.app.data.local.ScreenshotEntity
import com.fossick.app.data.model.MemoryCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MemoryThumbnail(item: ScreenshotEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = produceState<Bitmap?>(initialValue = null, item.uri, item.category) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadThumbnail(context.contentResolver, Uri.parse(item.uri), item.category) }.getOrNull()
        }
    }.value

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = when (item.category) {
                    MemoryCategory.VIDEO -> "VIDEO"
                    else -> "IMAGE"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun loadThumbnail(
    resolver: android.content.ContentResolver,
    uri: Uri,
    category: String
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return resolver.loadThumbnail(uri, android.util.Size(320, 320), null)
    }
    if (uri.authority == "media") {
        val mediaId = runCatching { android.content.ContentUris.parseId(uri) }.getOrNull()
        if (mediaId != null && mediaId > 0L) {
            return if (category == MemoryCategory.VIDEO) {
                MediaStore.Video.Thumbnails.getThumbnail(resolver, mediaId, MediaStore.Video.Thumbnails.MINI_KIND, null)
            } else {
                MediaStore.Images.Thumbnails.getThumbnail(resolver, mediaId, MediaStore.Images.Thumbnails.MINI_KIND, null)
            }
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 640) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}