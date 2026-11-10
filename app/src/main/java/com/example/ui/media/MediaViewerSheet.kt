package com.example.ui.media

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.example.data.model.MediaAttachment
import com.example.data.repository.MediaRepository
import com.example.ui.common.SingleFlightNoticeDispatcher
import com.example.ui.common.SingleFlightSnackbarHost
import com.example.ui.common.SwipeRevealHelper
import com.example.ui.common.rememberSingleFlightNoticeDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

val PhotoScaleKey = SemanticsPropertyKey<Float>("PhotoScale")
val PhotoTranslationXKey = SemanticsPropertyKey<Float>("PhotoTranslationX")
val PhotoTranslationYKey = SemanticsPropertyKey<Float>("PhotoTranslationY")

data class MediaViewerItem(
    val id: String,
    val kind: String, // "PHOTO" or "VOICE"
    val file: File,
    val durationMs: Long = 0L,
    val rawAttachment: MediaAttachment? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerSheet(
    txnId: Long? = null,
    attachments: List<MediaAttachment> = emptyList(),
    draftItems: List<MediaViewerItem>? = null,
    initialTab: String = "IMAGES",
    hideBalances: Boolean = false,
    autoPlayFirstVoice: Boolean = false,
    mediaRepository: MediaRepository,
    onDismiss: () -> Unit,
    onDeleteAttachment: ((MediaAttachment) -> Unit)? = null,
    onDeleteDraftItem: ((MediaViewerItem) -> Unit)? = null,
    noticeDispatcher: SingleFlightNoticeDispatcher? = null
) {
    val fallbackDispatcher = rememberSingleFlightNoticeDispatcher()
    val effectiveNoticeDispatcher = noticeDispatcher ?: fallbackDispatcher

    BackHandler { onDismiss() }

    DisposableEffect(Unit) {
        onDispose {
            MediaRepository.activePlayingTxnId.value = null
            MediaRepository.activePlayingVoiceKey.value = null
        }
    }

    val isDraftMode = draftItems != null
    val scope = rememberCoroutineScope()
    var deletedItemIds by remember { mutableStateOf(setOf<String>()) }

    val allItems = remember(attachments, draftItems, txnId, deletedItemIds) {
        val rawList = draftItems ?: attachments.map { att ->
            val file = File(mediaRepository.getVaultDirForTxn(att.txnId), att.fileName)
            val resolvedDur = if (att.kind == "VOICE") {
                MediaRepository.resolveVoiceDurationMs(file)
            } else {
                0L
            }
            MediaViewerItem(
                id = att.id.toString(),
                kind = att.kind,
                file = file,
                durationMs = resolvedDur,
                rawAttachment = att
            )
        }
        rawList.filter { it.id !in deletedItemIds }
    }

    val deleteMediaItem: (MediaViewerItem) -> Unit = { item ->
        deletedItemIds = deletedItemIds + item.id
        if (isDraftMode) {
            onDeleteDraftItem?.invoke(item)
        } else {
            val att = item.rawAttachment
            if (att != null) {
                mediaRepository.getExistingMediaFile(att)?.let { f ->
                    if (f.exists()) f.delete()
                }
                if (item.file.exists()) {
                    item.file.delete()
                }
                com.example.data.repository.MutationTracker.bump()
                scope.launch {
                    mediaRepository.deleteAttachment(att)
                }
                onDeleteAttachment?.invoke(att)
            }
        }
    }

    val photos = remember(allItems) { allItems.filter { it.kind == "PHOTO" } }
    val voiceNotes = remember(allItems) { allItems.filter { it.kind == "VOICE" } }

    var selectedTab by remember(initialTab) {
        mutableStateOf(
            if (initialTab == "VOICE") {
                "VOICE"
            } else if (photos.isEmpty() && voiceNotes.isNotEmpty()) {
                "VOICE"
            } else {
                "IMAGES"
            }
        )
    }

    LaunchedEffect(initialTab, photos.isEmpty(), voiceNotes.isEmpty()) {
        if (initialTab == "VOICE" && voiceNotes.isNotEmpty()) {
            selectedTab = "VOICE"
        } else if (initialTab == "IMAGES" && photos.isEmpty() && voiceNotes.isNotEmpty() && deletedItemIds.isEmpty()) {
            selectedTab = "VOICE"
        }
    }

    LaunchedEffect(photos.isEmpty(), voiceNotes.isEmpty(), deletedItemIds.isNotEmpty()) {
        if (photos.isEmpty() && voiceNotes.isEmpty() && deletedItemIds.isNotEmpty()) {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag("media_viewer_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attached Media",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("viewer_close_btn")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (hideBalances) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { effectiveNoticeDispatcher.showPrivacyNotice() }
                        .padding(horizontal = 24.dp, vertical = 36.dp)
                        .testTag("privacy_media_gate_box"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = SingleFlightNoticeDispatcher.PRIVACY_NOTICE_MESSAGE,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("privacy_media_gate_message")
                        )
                    }
                }
                if (noticeDispatcher == null) {
                    SingleFlightSnackbarHost(dispatcher = effectiveNoticeDispatcher)
                }
            } else {
                // Two-tab row: [ Images | Voice ]
                TabRow(
                    selectedTabIndex = if (selectedTab == "IMAGES") 0 else 1,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Tab(
                        selected = selectedTab == "IMAGES",
                        onClick = { selectedTab = "IMAGES" },
                        modifier = Modifier.testTag("media_sheet_tab_images"),
                        text = { Text("Images (${photos.size})") }
                    )
                    Tab(
                        selected = selectedTab == "VOICE",
                        onClick = { selectedTab = "VOICE" },
                        modifier = Modifier.testTag("media_sheet_tab_voice"),
                        text = { Text("Voice (${voiceNotes.size})") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == "IMAGES") {
                    if (photos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No images attached",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val pagerState = rememberPagerState(pageCount = { photos.size })
                        val currentIdx = pagerState.currentPage.coerceIn(0, photos.size - 1)
                        val currentPhotoItem = photos[currentIdx]
                        val currentFile = currentPhotoItem.file

                        val currentDimensions = remember(currentFile) {
                            if (currentFile.exists()) {
                                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(currentFile.absolutePath, opts)
                                "${opts.outWidth}x${opts.outHeight}"
                            } else ""
                        }

                        val currentSizeFormatted = remember(currentFile) {
                            if (currentFile.exists()) {
                                MediaRepository.formatFileSize(currentFile.length())
                            } else "0 KB"
                        }

                        var isCurrentPageZoomed by remember { mutableStateOf(false) }

                        LaunchedEffect(pagerState.currentPage) {
                            isCurrentPageZoomed = false
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${currentIdx + 1} / ${photos.size}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("viewer_photo_counter")
                                    )
                                    Text(
                                        text = if (currentDimensions.isNotBlank()) "$currentDimensions • $currentSizeFormatted" else currentSizeFormatted,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("viewer_media_subtitle")
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val itemToDelete = photos.getOrNull(currentIdx)
                                        if (itemToDelete != null) {
                                            deleteMediaItem(itemToDelete)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("viewer_header_delete_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete photo",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = !isCurrentPageZoomed,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(320.dp)
                                    .testTag("viewer_photo_pager")
                            ) { page ->
                                val photoItem = photos.getOrNull(page)
                                if (photoItem != null) {
                                    PhotoViewerZoomablePage(
                                        index = page,
                                        photoItem = photoItem,
                                        currentPage = pagerState.currentPage,
                                        onZoomChanged = { zoomed ->
                                            if (page == pagerState.currentPage) {
                                                isCurrentPageZoomed = zoomed
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // VOICE TAB
                    if (voiceNotes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No voice notes attached",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            voiceNotes.forEachIndexed { index, voiceItem ->
                                VoiceViewerRow(
                                    index = index,
                                    txnId = txnId,
                                    voiceItem = voiceItem,
                                    autoStart = autoPlayFirstVoice && index == 0,
                                    onDelete = { deleteMediaItem(voiceItem) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoViewerZoomablePage(
    index: Int,
    photoItem: MediaViewerItem,
    currentPage: Int,
    onZoomChanged: (Boolean) -> Unit = {}
) {
    var scale by remember(photoItem.id) { mutableFloatStateOf(1f) }
    var offset by remember(photoItem.id) { mutableStateOf(Offset.Zero) }
    val file = photoItem.file

    // A1: Any HorizontalPager page change resets scale to 1x and translation to (0,0)
    LaunchedEffect(currentPage) {
        if (scale != 1f || offset != Offset.Zero) {
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("viewer_photo_card_$index")
            .semantics {
                set(PhotoScaleKey, scale)
                set(PhotoTranslationXKey, offset.x)
                set(PhotoTranslationYKey, offset.y)
                customActions = listOf(
                    CustomAccessibilityAction(if (scale > 1.05f) "Reset zoom" else "Zoom in") {
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged(false)
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                            onZoomChanged(true)
                        }
                        true
                    }
                )
            }
            .pointerInput(photoItem.id) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged(false)
                        } else {
                            val targetScale = 2.5f
                            val maxPanX = (size.width * (targetScale - 1f)) / 2f
                            val maxPanY = (size.height * (targetScale - 1f)) / 2f
                            val targetX = (size.width / 2f - tapOffset.x) * (targetScale - 1f)
                            val targetY = (size.height / 2f - tapOffset.y) * (targetScale - 1f)
                            scale = targetScale
                            offset = Offset(
                                targetX.coerceIn(-maxPanX, maxPanX),
                                targetY.coerceIn(-maxPanY, maxPanY)
                            )
                            onZoomChanged(true)
                        }
                    }
                )
            }
            .pointerInput(photoItem.id) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.filter { it.pressed }
                        val pointerCount = pressedPointers.size

                        if (pointerCount >= 2) {
                            // Pinch-to-zoom
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                if (newScale <= 1.001f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                    onZoomChanged(false)
                                } else {
                                    val maxPanX = (size.width * (newScale - 1f)) / 2f
                                    val maxPanY = (size.height * (newScale - 1f)) / 2f
                                    val nextX = (offset.x + pan.x).coerceIn(-maxPanX, maxPanX)
                                    val nextY = (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                    scale = newScale
                                    offset = Offset(nextX, nextY)
                                    onZoomChanged(true)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } else if (pointerCount == 1 && scale > 1.001f) {
                            // Pan while zoomed (> 1x)
                            val pan = event.calculatePan()
                            if (pan != Offset.Zero) {
                                val maxPanX = (size.width * (scale - 1f)) / 2f
                                val maxPanY = (size.height * (scale - 1f)) / 2f
                                val nextX = (offset.x + pan.x).coerceIn(-maxPanX, maxPanX)
                                val nextY = (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                offset = Offset(nextX, nextY)
                                event.changes.forEach { it.consume() }
                            }
                        }
                        // PointerCount == 1 and scale <= 1.001f: do not consume, allow HorizontalPager to page!
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (file.exists()) {
            val bitmap = remember(file.absolutePath) {
                BitmapFactory.decodeFile(file.absolutePath)
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Photo ${index + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .testTag("viewer_photo_image_$index")
                )
            } else {
                Text("Unable to load image", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text("Image file not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val PauseVectorIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Pause",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 19f)
            horizontalLineTo(10f)
            verticalLineTo(5f)
            horizontalLineTo(6f)
            verticalLineTo(19f)
            close()
            moveTo(14f, 5f)
            verticalLineTo(19f)
            horizontalLineTo(18f)
            verticalLineTo(5f)
            horizontalLineTo(14f)
            close()
        }
    }.build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceViewerRow(
    index: Int,
    txnId: Long? = null,
    voiceItem: MediaViewerItem,
    autoStart: Boolean = false,
    onDelete: () -> Unit
) {
    val voiceFile = voiceItem.file
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var totalDurationMs by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    val resolvedDurationMs = remember(voiceFile.absolutePath, voiceItem.durationMs, totalDurationMs) {
        val fromRepo = MediaRepository.resolveVoiceDurationMs(voiceFile, voiceItem.durationMs)
        if (fromRepo > 0L) fromRepo else totalDurationMs.toLong().coerceAtLeast(0L)
    }

    DisposableEffect(voiceFile.absolutePath) {
        val mp = MediaPlayer()
        try {
            if (voiceFile.exists()) {
                mp.setDataSource(voiceFile.absolutePath)
                mp.prepare()
                if (mp.duration > 0) {
                    totalDurationMs = mp.duration
                }
            }
        } catch (_: Exception) {}
        player = mp

        mp.setOnCompletionListener {
            isPlaying = false
            currentPositionMs = 0
            MediaRepository.activePlayingTxnId.value = null
            MediaRepository.activePlayingVoiceKey.value = null
        }

        onDispose {
            if (isPlaying) {
                MediaRepository.activePlayingTxnId.value = null
                MediaRepository.activePlayingVoiceKey.value = null
            }
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                val p = player
                if (p != null && p.isPlaying) {
                    currentPositionMs = p.currentPosition
                }
            } catch (_: Exception) {}
            delay(250)
        }
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetX = remember(voiceItem.id) { Animatable(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("viewer_voice_swipe_container_$index")
    ) {
        val rowWidthPx = with(density) { maxWidth.toPx() }
        val maxRevealPx = SwipeRevealHelper.computeMaxRevealPx(rowWidthPx, density)
        val maxRevealDp = with(density) { maxRevealPx.toDp() }

        // Capped red delete reveal band at right end (<= 30% of row width, max 120.dp)
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(maxRevealDp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error)
                    .testTag("voice_delete_reveal_band_$index"),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        scope.launch { offsetX.snapTo(0f) }
                        onDelete()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_delete_bin_$index")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete voice memo",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Delete voice memo") {
                            onDelete()
                            true
                        }
                    )
                }
                .pointerInput(maxRevealPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val nextOffset = (offsetX.value + dragAmount).coerceIn(-maxRevealPx, 0f)
                            scope.launch { offsetX.snapTo(nextOffset) }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -maxRevealPx * 0.4f) {
                                    offsetX.animateTo(-maxRevealPx, tween(150))
                                } else {
                                    offsetX.animateTo(0f, tween(150))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, tween(150)) }
                        }
                    )
                }
                .testTag("viewer_voice_card_$index"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val nextPlaying = !isPlaying
                        isPlaying = nextPlaying
                        if (nextPlaying) {
                            MediaRepository.activePlayingTxnId.value = txnId ?: -1L
                            MediaRepository.activePlayingVoiceKey.value = voiceItem.id
                            try {
                                player?.start()
                            } catch (_: Exception) {}
                        } else {
                            MediaRepository.activePlayingTxnId.value = null
                            MediaRepository.activePlayingVoiceKey.value = null
                            try {
                                player?.pause()
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .semantics { contentDescription = if (isPlaying) "Pause" else "Play" }
                        .testTag("viewer_play_pause_btn_$index")
                ) {
                    if (isPlaying) {
                        Icon(
                            imageVector = PauseVectorIcon,
                            contentDescription = "Pause",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("viewer_pause_symbol_$index")
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Voice note #${index + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val currentPosFormatted = MediaRepository.formatDuration(currentPositionMs.toLong())
                    val totalDurFormatted = MediaRepository.formatDuration(resolvedDurationMs)
                    val sizeFormatted = MediaRepository.formatFileSize(voiceFile.length())
                    Text(
                        text = "$currentPosFormatted / $totalDurFormatted • $sizeFormatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("viewer_voice_subtitle_$index")
                    )
                }
            }
        }
    }
}
