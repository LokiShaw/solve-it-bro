package com.solveitbro.app.review

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import com.solveitbro.app.BuildConfig
import com.solveitbro.app.R
import com.solveitbro.app.monetization.NoSubscription
import com.solveitbro.app.monetization.PrefsQuotaStore
import com.solveitbro.app.monetization.SolveQuota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows the captured photo so the student can retake it or send it to be solved. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(image: Uri, onRetake: () -> Unit) {
    val context = LocalContext.current
    val quota = remember {
        SolveQuota(PrefsQuotaStore(context), NoSubscription, BuildConfig.FREE_DAILY_SOLVES)
    }
    var quotaStatus by remember { mutableStateOf(quota.status()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val limitReached = stringResource(R.string.review_limit_reached)
    val comingSoon = stringResource(R.string.review_solve_coming)

    val bitmap by produceState<Result<Bitmap>?>(initialValue = null, image) {
        value = withContext(Dispatchers.IO) { runCatching { context.loadUpright(image) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_title)) },
                navigationIcon = {
                    IconButton(onClick = onRetake) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (val result = bitmap) {
                    null -> CircularProgressIndicator()
                    else -> result.fold(
                        onSuccess = {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        onFailure = { Text(stringResource(R.string.review_load_failed)) },
                    )
                }
            }

            Text(
                text = when (val status = quotaStatus) {
                    SolveQuota.Status.Unlimited -> stringResource(R.string.review_unlimited)
                    is SolveQuota.Status.Remaining -> stringResource(R.string.review_solves_left, status.count)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_retake))
                }
                Button(
                    onClick = {
                        // TODO(next PR): crop, run ML Kit OCR, then call the backend /v1/solve.
                        val message = if (quota.tryConsume()) comingSoon else limitReached
                        quotaStatus = quota.status()
                        scope.launch { snackbar.showSnackbar(message) }
                    },
                    enabled = bitmap?.isSuccess == true,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.review_solve))
                }
            }
        }
    }
}

private const val MAX_PREVIEW_EDGE = 2048

/** Decodes [uri] downsampled to at most [MAX_PREVIEW_EDGE] px and rotated per its EXIF tag. */
private fun Context.loadUpright(uri: Uri): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_PREVIEW_EDGE) sample *= 2

    val decoded = contentResolver.openInputStream(uri).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Unreadable image: $uri")

    val degrees = contentResolver.openInputStream(uri).use { stream ->
        if (stream == null) 0 else ExifInterface(stream).rotationDegrees
    }
    if (degrees == 0) return decoded
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}
