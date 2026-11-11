package com.example.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * App-wide CompositionLocal providing the hoisted [SingleFlightNoticeDispatcher] from [com.example.ui.PaisaTrackApp].
 */
val LocalNoticeDispatcher = compositionLocalOf<SingleFlightNoticeDispatcher?> { null }

/**
 * Single-flight, visibility-gated snackbar notice dispatcher wrapping a [SnackbarHostState].
 *
 * Rules:
 * 1. Synchronous in-flight key is set BEFORE launching the show coroutine.
 * 2. Same key while live -> dropped immediately (no queue, no replace, no timer reset).
 * 3. Different key while live -> replaces current notice immediately (cancels and dismisses active, shows new).
 * 4. try/finally uses compare-and-clear: clears [inFlightKey] ONLY if it still equals its own key (and token),
 *    so a cancelled or replaced notice never wipes its successor's gate.
 */
class SingleFlightNoticeDispatcher(
    val snackbarHostState: SnackbarHostState,
    private val scope: CoroutineScope
) {
    companion object {
        const val PRIVACY_NOTICE_KEY = "PRIVACY_MODE_MEDIA_NOTICE"
        const val PRIVACY_NOTICE_MESSAGE =
            "Turn off Privacy Mode in Settings to view photos and voice notes."
    }

    private val lock = Any()

    @Volatile
    var inFlightKey: String? = null
        private set

    private var inFlightToken: Long = 0L
    private var inFlightJob: Job? = null

    @Volatile
    var dispatchedCount: Int = 0
        private set

    @Volatile
    var droppedSameKeyCount: Int = 0
        private set

    fun showPrivacyNotice(): Boolean {
        return showNotice(
            key = PRIVACY_NOTICE_KEY,
            message = PRIVACY_NOTICE_MESSAGE,
            duration = SnackbarDuration.Short
        )
    }

    fun showNotice(
        key: String,
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        duration: SnackbarDuration = SnackbarDuration.Short,
        onActionPerformed: (() -> Unit)? = null,
        onDismissed: (() -> Unit)? = null
    ): Boolean {
        val previousJob: Job?
        val token: Long
        synchronized(lock) {
            // Same key while live -> drop (complete no-op: no queue, no replace, no timer reset)
            if (inFlightKey == key) {
                droppedSameKeyCount++
                return false
            }
            // Synchronous in-flight key set BEFORE launching the show coroutine
            previousJob = inFlightJob
            inFlightKey = key
            token = ++inFlightToken
            dispatchedCount++
        }

        // Different key while live -> replace current (cancel & dismiss active, never queue)
        previousJob?.cancel()
        snackbarHostState.currentSnackbarData?.dismiss()

        val job = scope.launch {
            try {
                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = actionLabel,
                    withDismissAction = withDismissAction,
                    duration = duration
                )
                when (result) {
                    SnackbarResult.ActionPerformed -> onActionPerformed?.invoke()
                    SnackbarResult.Dismissed -> onDismissed?.invoke()
                }
            } finally {
                // Compare-and-clear: clears the in-flight key ONLY if it still equals its own key.
                // A replaced or cancelled show must never wipe its successor's gate.
                synchronized(lock) {
                    if (inFlightKey == key && inFlightToken == token) {
                        inFlightKey = null
                        inFlightJob = null
                    }
                }
            }
        }

        synchronized(lock) {
            if (inFlightKey == key && inFlightToken == token) {
                inFlightJob = job
            }
        }
        return true
    }

    fun dismissCurrent() {
        snackbarHostState.currentSnackbarData?.dismiss()
    }
}

@Composable
fun rememberSingleFlightNoticeDispatcher(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    scope: CoroutineScope = rememberCoroutineScope()
): SingleFlightNoticeDispatcher {
    return remember(snackbarHostState, scope) {
        SingleFlightNoticeDispatcher(snackbarHostState, scope)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleFlightSnackbarHost(
    dispatcher: SingleFlightNoticeDispatcher,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = dispatcher.snackbarHostState,
        modifier = modifier
            .semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
            .testTag("single_flight_snackbar_host")
    ) { snackbarData ->
        key(snackbarData) {
            val dismissState = rememberSwipeToDismissBoxState(
                positionalThreshold = { totalDistance -> totalDistance * 0.25f },
                confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) {
                        snackbarData.dismiss()
                        true
                    } else {
                        false
                    }
                }
            )
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {},
                modifier = Modifier.testTag("single_flight_snackbar_swipe_box")
            ) {
                Snackbar(snackbarData = snackbarData)
            }
        }
    }
}
