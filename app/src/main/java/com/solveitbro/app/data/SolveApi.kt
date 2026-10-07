package com.solveitbro.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class SolveRequestBody(
    val image: String,
    val mediaType: String,
    val language: String,
    val mode: String = MODE_STANDARD,
    val ocrText: String? = null,
) {
    companion object {
        const val MODE_STANDARD = "standard"
        const val MODE_SIMPLER = "simpler"
    }
}

@Serializable
data class Step(val title: String, val explanation: String)

@Serializable
data class Solution(
    val status: String,
    val subject: String,
    val question: String,
    val steps: List<Step>,
    val finalAnswer: String,
    val tip: String? = null,
) {
    val isSolved get() = status == "solved"
    val isUnreadable get() = status == "unreadable"
}

sealed interface SolveResult {
    data class Success(val solution: Solution) : SolveResult
    data object LimitReached : SolveResult
    data object CannotHelp : SolveResult
    data class Failed(val network: Boolean) : SolveResult
}

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Maps a `/v1/solve` HTTP response onto a [SolveResult]. */
internal fun parseSolveResponse(code: Int, body: String): SolveResult = when (code) {
    200 -> runCatching { SolveResult.Success(json.decodeFromString(Solution.serializer(), body)) }
        .getOrElse { SolveResult.Failed(network = false) }
    429 -> SolveResult.LimitReached
    422 -> SolveResult.CannotHelp
    else -> SolveResult.Failed(network = false)
}

internal fun encodeSolveRequest(body: SolveRequestBody): String =
    json.encodeToString(SolveRequestBody.serializer(), body)

/** Client for the Solve It Bro backend proxy (see /backend). */
class SolveApi(
    private val baseUrl: String,
    private val deviceId: String,
    private val client: OkHttpClient = defaultClient,
) {
    suspend fun solve(body: SolveRequestBody): SolveResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/v1/solve")
            .header("X-Device-Id", deviceId)
            .post(encodeSolveRequest(body).toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { parseSolveResponse(it.code, it.body.string()) }
        } catch (e: IOException) {
            SolveResult.Failed(network = true)
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()

        // Step-by-step answers can take a while to generate.
        val defaultClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build()
    }
}
