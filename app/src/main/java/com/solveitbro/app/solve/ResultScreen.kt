package com.solveitbro.app.solve

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.solveitbro.app.R
import com.solveitbro.app.data.Language
import com.solveitbro.app.data.Solution
import com.solveitbro.app.solve.SolveViewModel.Failure
import com.solveitbro.app.solve.SolveViewModel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(image: Uri, language: Language, onBack: () -> Unit, onNewQuestion: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel = viewModel { SolveViewModel(app, image, language) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val simplerFailed = stringResource(R.string.error_simpler_failed)

    LaunchedEffect((state as? UiState.Solved)?.simplerFailed) {
        if ((state as? UiState.Solved)?.simplerFailed == true) {
            snackbar.showSnackbar(simplerFailed)
            viewModel.simplerErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.result_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val solved = state as? UiState.Solved
            if (solved != null && solved.solution.isSolved) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) {
                    OutlinedButton(onClick = onNewQuestion, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.result_new))
                    }
                    Button(
                        onClick = viewModel::explainSimpler,
                        enabled = !solved.simplifying && !solved.isSimpler,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(
                                if (solved.simplifying) R.string.result_simpler_loading else R.string.result_simpler,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (val s = state) {
            UiState.Reading -> Progress(stringResource(R.string.result_reading), modifier)
            UiState.Solving -> Progress(stringResource(R.string.result_solving), modifier)
            is UiState.Failed -> FailureMessage(s.failure, onRetry = viewModel::solve, onNewQuestion = onNewQuestion, modifier = modifier)
            is UiState.Solved -> when {
                s.solution.isSolved -> SolutionList(s.solution, modifier)
                else -> Message(
                    text = stringResource(
                        if (s.solution.isUnreadable) R.string.result_unreadable else R.string.result_not_question,
                    ),
                    action = stringResource(R.string.result_new),
                    onAction = onNewQuestion,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun SolutionList(solution: Solution, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                AssistChip(onClick = {}, label = { Text(solution.subject) })
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.result_question), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(solution.question, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        itemsIndexed(solution.steps) { index, step ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.result_step, index + 1) + " · " + step.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(step.explanation, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.result_answer), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(solution.finalAnswer, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        solution.tip?.takeIf { it.isNotBlank() }?.let { tip ->
            item {
                Text(
                    stringResource(R.string.result_tip) + ": " + tip,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Progress(text: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FailureMessage(failure: Failure, onRetry: () -> Unit, onNewQuestion: () -> Unit, modifier: Modifier) {
    val text = stringResource(
        when (failure) {
            Failure.LimitReached -> R.string.review_limit_reached
            Failure.CannotHelp -> R.string.error_cannot_help
            Failure.Network -> R.string.error_network
            Failure.Server -> R.string.error_server
        },
    )
    val retryable = failure == Failure.Network || failure == Failure.Server
    Message(
        text = text,
        action = stringResource(if (retryable) R.string.result_retry else R.string.result_new),
        onAction = if (retryable) onRetry else onNewQuestion,
        modifier = modifier,
    )
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) { Text(action) }
    }
}
