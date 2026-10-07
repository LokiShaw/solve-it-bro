package com.solveitbro.app.solve

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solveitbro.app.BuildConfig
import com.solveitbro.app.data.AppPrefs
import com.solveitbro.app.data.Language
import com.solveitbro.app.data.Solution
import com.solveitbro.app.data.SolveApi
import com.solveitbro.app.data.SolveRequestBody
import com.solveitbro.app.data.SolveResult
import com.solveitbro.app.monetization.NoSubscription
import com.solveitbro.app.monetization.PrefsQuotaStore
import com.solveitbro.app.monetization.SolveQuota
import com.solveitbro.app.ocr.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_OCR_CHARS = 4_000

/** Reads the cropped question, sends it to the backend and holds the answer. */
class SolveViewModel(
    app: Application,
    private val image: Uri,
    private val language: Language,
) : AndroidViewModel(app) {

    enum class Failure { LimitReached, CannotHelp, Network, Server }

    sealed interface UiState {
        data object Reading : UiState
        data object Solving : UiState
        data class Solved(
            val solution: Solution,
            val simplifying: Boolean = false,
            val isSimpler: Boolean = false,
            val simplerFailed: Boolean = false,
        ) : UiState
        data class Failed(val failure: Failure) : UiState
    }

    private val api = SolveApi(BuildConfig.API_BASE_URL, AppPrefs(app).deviceId)
    private val quota = SolveQuota(PrefsQuotaStore(app), NoSubscription, BuildConfig.FREE_DAILY_SOLVES)
    private var request: SolveRequestBody? = null

    private val _state = MutableStateFlow<UiState>(UiState.Reading)
    val state = _state.asStateFlow()

    init {
        solve()
    }

    fun solve() {
        viewModelScope.launch {
            val body = request ?: run {
                _state.value = UiState.Reading
                runCatching { buildRequest() }.getOrNull()?.also { request = it }
            }
            if (body == null) {
                _state.value = UiState.Failed(Failure.Server)
                return@launch
            }
            _state.value = UiState.Solving
            _state.value = when (val result = api.solve(body)) {
                is SolveResult.Success -> {
                    quota.tryConsume()
                    UiState.Solved(result.solution)
                }
                else -> UiState.Failed(result.toFailure())
            }
        }
    }

    fun explainSimpler() {
        val current = _state.value as? UiState.Solved ?: return
        val body = request ?: return
        _state.value = current.copy(simplifying = true, simplerFailed = false)
        viewModelScope.launch {
            _state.value = when (val result = api.solve(body.copy(mode = SolveRequestBody.MODE_SIMPLER))) {
                is SolveResult.Success -> {
                    quota.tryConsume()
                    UiState.Solved(result.solution, isSimpler = true)
                }
                else -> current.copy(simplifying = false, simplerFailed = true)
            }
        }
    }

    fun simplerErrorShown() {
        val current = _state.value as? UiState.Solved ?: return
        _state.value = current.copy(simplerFailed = false)
    }

    private suspend fun buildRequest(): SolveRequestBody {
        val bytes = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(image).use { requireNotNull(it).readBytes() }
        }
        val ocrText = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { readText(it, language) }
        }
        return SolveRequestBody(
            image = Base64.encodeToString(bytes, Base64.NO_WRAP),
            mediaType = "image/jpeg",
            language = language.code,
            ocrText = ocrText?.take(MAX_OCR_CHARS),
        )
    }

    private fun SolveResult.toFailure() = when (this) {
        SolveResult.LimitReached -> Failure.LimitReached
        SolveResult.CannotHelp -> Failure.CannotHelp
        is SolveResult.Failed -> if (network) Failure.Network else Failure.Server
        is SolveResult.Success -> error("not a failure")
    }
}
