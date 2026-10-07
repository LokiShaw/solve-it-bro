package com.solveitbro.app.review

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.solveitbro.app.BuildConfig
import com.solveitbro.app.R
import com.solveitbro.app.data.AppPrefs
import com.solveitbro.app.data.Language
import com.solveitbro.app.image.crop
import com.solveitbro.app.image.loadUpright
import com.solveitbro.app.image.scaledToMaxEdge
import com.solveitbro.app.image.toJpeg
import com.solveitbro.app.monetization.NoSubscription
import com.solveitbro.app.monetization.PrefsQuotaStore
import com.solveitbro.app.monetization.SolveQuota
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Longest edge sent to the AI; larger images cost more without reading better. */
private const val UPLOAD_MAX_EDGE = 1568

/**
 * Shows the captured photo with a crop box and an answer-language choice.
 * "Solve it" saves the cropped question and hands its [Uri] to [onSolve].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(image: Uri, onRetake: () -> Unit, onSolve: (Uri, Language) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val quota = remember {
        SolveQuota(PrefsQuotaStore(context), NoSubscription, BuildConfig.FREE_DAILY_SOLVES)
    }
    val quotaStatus = quota.status()
    var language by remember { mutableStateOf(prefs.language) }
    var cropRect by remember { mutableStateOf(CropRect.Default) }
    var saving by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val limitReached = stringResource(R.string.review_limit_reached)

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
                            val imageBitmap = remember(it) { it.asImageBitmap() }
                            CropImage(
                                bitmap = imageBitmap,
                                crop = cropRect,
                                onCropChange = { cropRect = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                        onFailure = { Text(stringResource(R.string.review_load_failed)) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.review_crop_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 12.dp),
            ) {
                Text(stringResource(R.string.review_language), style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow {
                    Language.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = language == option,
                            onClick = {
                                language = option
                                prefs.language = option
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, Language.entries.size),
                        ) {
                            Text(stringResource(if (option == Language.HI) R.string.lang_hi else R.string.lang_en))
                        }
                    }
                }
            }

            Text(
                text = when (quotaStatus) {
                    SolveQuota.Status.Unlimited -> stringResource(R.string.review_unlimited)
                    is SolveQuota.Status.Remaining -> stringResource(R.string.review_solves_left, quotaStatus.count)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_retake))
                }
                Button(
                    onClick = {
                        val source = bitmap?.getOrNull() ?: return@Button
                        if (quotaStatus == SolveQuota.Status.Remaining(0)) {
                            scope.launch { snackbar.showSnackbar(limitReached) }
                            return@Button
                        }
                        saving = true
                        scope.launch {
                            val file = withContext(Dispatchers.Default) {
                                val jpeg = source.crop(cropRect).scaledToMaxEdge(UPLOAD_MAX_EDGE).toJpeg()
                                File(File(context.cacheDir, "crops").apply { mkdirs() }, "q_${System.currentTimeMillis()}.jpg")
                                    .apply { writeBytes(jpeg) }
                            }
                            saving = false
                            onSolve(Uri.fromFile(file), language)
                        }
                    },
                    enabled = bitmap?.isSuccess == true && !saving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.review_solve))
                }
            }
        }
    }
}
