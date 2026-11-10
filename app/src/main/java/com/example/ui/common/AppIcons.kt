package com.example.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    // Core icon mappings
    val CalendarToday = Icons.Default.DateRange
    val DeleteForever = Icons.Default.Delete
    val Security = Icons.Default.Lock
    val Fingerprint: ImageVector = ImageVector.Builder(
        name = "Fingerprint",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(17.81f, 4.47f)
            curveToRelative(-0.08f, 0f, -0.16f, -0.02f, -0.23f, -0.06f)
            curveTo(15.66f, 3.42f, 14f, 3f, 12.01f, 3f)
            curveToRelative(-1.98f, 0f, -3.86f, 0.47f, -5.57f, 1.41f)
            curveToRelative(-0.24f, 0.13f, -0.54f, 0.04f, -0.68f, -0.2f)
            curveToRelative(-0.13f, -0.24f, -0.04f, -0.55f, 0.2f, -0.68f)
            curveTo(7.82f, 2.52f, 9.86f, 2f, 12.01f, 2f)
            curveToRelative(2.13f, 0f, 3.99f, 0.47f, 6.03f, 1.52f)
            curveToRelative(0.25f, 0.13f, 0.34f, 0.43f, 0.21f, 0.67f)
            curveToRelative(-0.09f, 0.18f, -0.26f, 0.28f, -0.44f, 0.28f)
            close()
            moveTo(3.5f, 9.72f)
            curveToRelative(-0.25f, 0f, -0.48f, -0.19f, -0.5f, -0.44f)
            curveToRelative(-0.1f, -1.14f, 0.13f, -2.28f, 0.69f, -3.39f)
            curveToRelative(0.12f, -0.25f, 0.42f, -0.34f, 0.67f, -0.22f)
            curveToRelative(0.25f, 0.12f, 0.34f, 0.42f, 0.22f, 0.67f)
            curveToRelative(-0.51f, 1.02f, -0.72f, 2.06f, -0.63f, 3.09f)
            curveToRelative(0.02f, 0.28f, -0.18f, 0.52f, -0.45f, 0.54f)
            close()
            moveTo(9.5f, 10.5f)
            curveToRelative(0f, -1.38f, 1.12f, -2.5f, 2.5f, -2.5f)
            reflectiveCurveToRelative(2.5f, 1.12f, 2.5f, 2.5f)
            verticalLineToRelative(4.0f)
            curveToRelative(0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
            reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
            verticalLineToRelative(-4.0f)
            curveToRelative(0f, -2.48f, -2.02f, -4.5f, -4.5f, -4.5f)
            reflectiveCurveTo(7.5f, 8.02f, 7.5f, 10.5f)
            verticalLineToRelative(5.0f)
            curveToRelative(0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
            curveToRelative(0.28f, 0f, 0.5f, -0.22f, 0.5f, -0.5f)
            reflectiveCurveToRelative(-0.22f, -0.5f, -0.5f, -0.5f)
            curveToRelative(-1.1f, 0f, -2.0f, -0.9f, -2.0f, -2.0f)
            verticalLineToRelative(-5.0f)
            close()
            moveTo(12.01f, 22f)
            curveToRelative(-0.28f, 0f, -0.5f, -0.22f, -0.5f, -0.5f)
            verticalLineToRelative(-3.43f)
            curveToRelative(0f, -0.83f, 0.71f, -1.5f, 1.58f, -1.5f)
            reflectiveCurveToRelative(1.58f, 0.67f, 1.58f, 1.5f)
            verticalLineToRelative(3.43f)
            curveToRelative(0f, 0.28f, -0.22f, 0.5f, -0.5f, 0.5f)
            close()
        }
    }.build()
    val Restore = Icons.Default.Refresh
    val Lightbulb = Icons.Default.Info
    val PushPin = Icons.Default.Star
    val AttachFile = Icons.Default.Share
    val VolumeUp = Icons.Default.Notifications
    val VisibilityOff = Icons.Default.Clear
    val TableChart = Icons.AutoMirrored.Filled.List
    val FormatListBulleted = Icons.AutoMirrored.Filled.List
    val ReceiptLong = Icons.AutoMirrored.Filled.List
    val Dashboard = Icons.Default.Home
    val Tune = Icons.Default.Settings
    val ShoppingBag = Icons.Default.Star
    val Restaurant = Icons.Default.Favorite
    val DirectionsCar = Icons.Default.Place
    val Flight = Icons.Default.PlayArrow
    val Mic = Icons.Default.Call
    val Archive = Icons.Default.Delete
    val Unarchive = Icons.Default.Refresh
    val RequestQuote = Icons.AutoMirrored.Filled.List

    val Wallet: ImageVector = ImageVector.Builder(
        name = "Wallet",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(21f, 18f)
            verticalLineTo(19f)
            curveTo(21f, 20.1f, 20.1f, 21f, 19f, 21f)
            horizontalLineTo(5f)
            curveTo(3.89f, 21f, 3f, 20.1f, 3f, 19f)
            verticalLineTo(5f)
            curveTo(3f, 3.9f, 3.89f, 3f, 5f, 3f)
            horizontalLineTo(19f)
            curveTo(20.1f, 3f, 21f, 3.9f, 21f, 5f)
            verticalLineTo(6f)
            horizontalLineTo(12f)
            curveTo(10.89f, 6f, 10f, 6.9f, 10f, 8f)
            verticalLineTo(16f)
            curveTo(10f, 17.1f, 10.89f, 18f, 12f, 18f)
            horizontalLineTo(21f)
            close()
            moveTo(12f, 16f)
            horizontalLineTo(22f)
            verticalLineTo(8f)
            horizontalLineTo(12f)
            verticalLineTo(16f)
            close()
            moveTo(16f, 13.5f)
            curveTo(15.17f, 13.5f, 14.5f, 12.83f, 14.5f, 12f)
            curveTo(14.5f, 11.17f, 15.17f, 10.5f, 16f, 10.5f)
            curveTo(16.83f, 10.5f, 17.5f, 11.17f, 17.5f, 12f)
            curveTo(17.5f, 12.83f, 16.83f, 13.5f, 16f, 13.5f)
            close()
        }
    }.build()

    val History: ImageVector = ImageVector.Builder(
        name = "History",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(13f, 3f)
            curveTo(8.03f, 3f, 4f, 7.03f, 4f, 12f)
            horizontalLineTo(1f)
            lineTo(4.89f, 15.89f)
            lineTo(4.96f, 16.03f)
            lineTo(9f, 12f)
            horizontalLineTo(6f)
            curveTo(6f, 8.13f, 9.13f, 5f, 13f, 5f)
            curveTo(16.87f, 5f, 20f, 8.13f, 20f, 12f)
            curveTo(20f, 15.87f, 16.87f, 19f, 13f, 19f)
            curveTo(11.07f, 19f, 9.32f, 18.21f, 8.06f, 16.94f)
            lineTo(6.64f, 18.36f)
            curveTo(8.27f, 19.99f, 10.51f, 21f, 13f, 21f)
            curveTo(17.97f, 21f, 22f, 16.97f, 22f, 12f)
            curveTo(22f, 7.03f, 17.97f, 3f, 13f, 3f)
            close()
            moveTo(12f, 8f)
            verticalLineTo(13f)
            lineTo(16.28f, 15.54f)
            lineTo(17f, 14.33f)
            lineTo(13.5f, 12.25f)
            verticalLineTo(8f)
            horizontalLineTo(12f)
            close()
        }
    }.build()

    val FileDownload: ImageVector = ImageVector.Builder(
        name = "FileDownload",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 9f)
            horizontalLineTo(15f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            verticalLineTo(9f)
            horizontalLineTo(5f)
            lineTo(12f, 16f)
            lineTo(19f, 9f)
            close()
            moveTo(5f, 18f)
            verticalLineTo(20f)
            horizontalLineTo(19f)
            verticalLineTo(18f)
            horizontalLineTo(5f)
            close()
        }
    }.build()

    val CloudDownload: ImageVector = ImageVector.Builder(
        name = "CloudDownload",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19.35f, 10.04f)
            curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
            curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
            curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
            curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
            horizontalLineTo(19f)
            curveTo(21.76f, 20f, 24f, 17.76f, 24f, 15f)
            curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f)
            close()
            moveTo(17f, 13f)
            lineTo(12f, 18f)
            lineTo(7f, 13f)
            horizontalLineTo(10f)
            verticalLineTo(9f)
            horizontalLineTo(14f)
            verticalLineTo(13f)
            horizontalLineTo(17f)
            close()
        }
    }.build()

    val UploadFile: ImageVector = ImageVector.Builder(
        name = "UploadFile",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(14f, 2f)
            horizontalLineTo(6f)
            curveTo(4.9f, 2f, 4.01f, 2.9f, 4.01f, 4f)
            lineTo(4f, 20f)
            curveTo(4f, 21.1f, 4.89f, 22f, 5.99f, 22f)
            horizontalLineTo(18f)
            curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
            verticalLineTo(8f)
            lineTo(14f, 2f)
            close()
            moveTo(18f, 20f)
            horizontalLineTo(6f)
            verticalLineTo(4f)
            horizontalLineTo(13f)
            verticalLineTo(9f)
            horizontalLineTo(18f)
            verticalLineTo(20f)
            close()
            moveTo(13.28f, 12.28f)
            lineTo(12f, 11f)
            verticalLineTo(17f)
            horizontalLineTo(10f)
            verticalLineTo(11f)
            lineTo(8.72f, 12.28f)
            lineTo(7.28f, 10.87f)
            lineTo(11f, 7.15f)
            lineTo(14.72f, 10.87f)
            lineTo(13.28f, 12.28f)
            close()
        }
    }.build()

    val Sync: ImageVector = ImageVector.Builder(
        name = "Sync",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 6f)
            verticalLineTo(9f)
            lineTo(16f, 5f)
            lineTo(12f, 1f)
            verticalLineTo(4f)
            curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
            curveTo(4f, 13.57f, 4.46f, 15.03f, 5.24f, 16.26f)
            lineTo(6.7f, 14.8f)
            curveTo(6.25f, 13.97f, 6f, 13.01f, 6f, 12f)
            curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
            close()
            moveTo(18.76f, 7.74f)
            lineTo(17.3f, 9.2f)
            curveTo(17.74f, 10.04f, 18f, 10.99f, 18f, 12f)
            curveTo(18f, 15.31f, 15.31f, 18f, 12f, 18f)
            verticalLineTo(15f)
            lineTo(8f, 19f)
            lineTo(12f, 23f)
            verticalLineTo(20f)
            curveTo(16.42f, 20f, 20f, 16.42f, 20f, 12f)
            curveTo(20f, 10.43f, 19.54f, 8.97f, 18.76f, 7.74f)
            close()
        }
    }.build()

    val Storage: ImageVector = ImageVector.Builder(
        name = "Storage",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(2f, 20f)
            horizontalLineTo(22f)
            verticalLineTo(16f)
            horizontalLineTo(2f)
            verticalLineTo(20f)
            close()
            moveTo(4f, 17f)
            horizontalLineTo(6f)
            verticalLineTo(19f)
            horizontalLineTo(4f)
            verticalLineTo(17f)
            close()
            moveTo(2f, 4f)
            verticalLineTo(8f)
            horizontalLineTo(22f)
            verticalLineTo(4f)
            horizontalLineTo(2f)
            close()
            moveTo(6f, 7f)
            horizontalLineTo(4f)
            verticalLineTo(5f)
            horizontalLineTo(6f)
            verticalLineTo(7f)
            close()
            moveTo(2f, 14f)
            horizontalLineTo(22f)
            verticalLineTo(10f)
            horizontalLineTo(2f)
            verticalLineTo(14f)
            close()
            moveTo(4f, 11f)
            horizontalLineTo(6f)
            verticalLineTo(13f)
            horizontalLineTo(4f)
            verticalLineTo(11f)
            close()
        }
    }.build()

    // Aliases to proper Material glyphs
    val Payments = Wallet
    val Widgets = Storage
    val Download = FileDownload
    val Upload = UploadFile
    val CloudUpload = Sync

    // Custom vector paths for icons without direct core visual match
    val ChevronRight: ImageVector = ImageVector.Builder(
        name = "ChevronRight",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 6f)
            lineTo(8.59f, 7.41f)
            lineTo(13.17f, 12f)
            lineTo(8.59f, 16.59f)
            lineTo(10f, 18f)
            lineTo(16f, 12f)
            close()
        }
    }.build()

    val ExpandMore: ImageVector = ImageVector.Builder(
        name = "ExpandMore",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(16.59f, 8.59f)
            lineTo(12f, 13.17f)
            lineTo(7.41f, 8.59f)
            lineTo(6f, 10f)
            lineTo(12f, 16f)
            lineTo(18f, 10f)
            close()
        }
    }.build()

    val ExpandLess: ImageVector = ImageVector.Builder(
        name = "ExpandLess",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 8f)
            lineTo(6f, 14f)
            lineTo(7.41f, 15.41f)
            lineTo(12f, 10.83f)
            lineTo(16.59f, 15.41f)
            lineTo(18f, 14f)
            close()
        }
    }.build()

    val ArrowUpward: ImageVector = ImageVector.Builder(
        name = "ArrowUpward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 12f)
            lineTo(5.41f, 13.41f)
            lineTo(11f, 7.83f)
            lineTo(11f, 20f)
            lineTo(13f, 20f)
            lineTo(13f, 7.83f)
            lineTo(18.59f, 13.41f)
            lineTo(20f, 12f)
            lineTo(12f, 4f)
            close()
        }
    }.build()

    val ArrowDownward: ImageVector = ImageVector.Builder(
        name = "ArrowDownward",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 12f)
            lineTo(18.59f, 10.59f)
            lineTo(13f, 16.17f)
            lineTo(13f, 4f)
            lineTo(11f, 4f)
            lineTo(11f, 16.17f)
            lineTo(5.41f, 10.59f)
            lineTo(4f, 12f)
            lineTo(12f, 20f)
            close()
        }
    }.build()

    val TrendingUp: ImageVector = ImageVector.Builder(
        name = "TrendingUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(16f, 6f)
            lineTo(18.29f, 8.29f)
            lineTo(13.41f, 13.17f)
            lineTo(9.41f, 9.17f)
            lineTo(2f, 16.59f)
            lineTo(3.41f, 18f)
            lineTo(9.41f, 12f)
            lineTo(13.41f, 16f)
            lineTo(19.71f, 9.71f)
            lineTo(22f, 12f)
            lineTo(22f, 6f)
            close()
        }
    }.build()

    val CreditCard: ImageVector = ImageVector.Builder(
        name = "CreditCard",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 4f)
            lineTo(4f, 4f)
            curveTo(2.89f, 4f, 2.01f, 4.89f, 2.01f, 6f)
            lineTo(2f, 18f)
            curveTo(2f, 19.1f, 2.89f, 20f, 4f, 20f)
            lineTo(20f, 20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
            lineTo(22f, 6f)
            curveTo(22f, 4.89f, 21.1f, 4f, 20f, 4f)
            close()
            moveTo(20f, 18f)
            lineTo(4f, 18f)
            lineTo(4f, 12f)
            lineTo(20f, 12f)
            lineTo(20f, 18f)
            close()
            moveTo(20f, 8f)
            lineTo(4f, 8f)
            lineTo(4f, 6f)
            lineTo(20f, 6f)
            lineTo(20f, 8f)
            close()
        }
    }.build()

    val AccountBalance: ImageVector = ImageVector.Builder(
        name = "AccountBalance",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 10f)
            lineTo(6.5f, 10f)
            lineTo(6.5f, 17f)
            lineTo(4f, 17f)
            close()
            moveTo(10.75f, 10f)
            lineTo(13.25f, 10f)
            lineTo(13.25f, 17f)
            lineTo(10.75f, 17f)
            close()
            moveTo(2f, 22f)
            lineTo(22f, 22f)
            lineTo(22f, 19f)
            lineTo(2f, 19f)
            close()
            moveTo(17.5f, 10f)
            lineTo(20f, 10f)
            lineTo(20f, 17f)
            lineTo(17.5f, 17f)
            close()
            moveTo(12f, 1f)
            lineTo(2f, 6f)
            lineTo(2f, 8f)
            lineTo(22f, 8f)
            lineTo(22f, 6f)
            close()
        }
    }.build()

    val Backspace: ImageVector = ImageVector.Builder(
        name = "Backspace",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(22f, 3f)
            lineTo(7f, 3f)
            curveTo(6.31f, 3f, 5.67f, 3.36f, 5.3f, 3.94f)
            lineTo(0f, 12f)
            lineTo(5.3f, 20.06f)
            curveTo(5.67f, 20.64f, 6.31f, 21f, 7f, 21f)
            lineTo(22f, 21f)
            curveTo(23.1f, 21f, 24f, 20.1f, 24f, 19f)
            lineTo(24f, 5f)
            curveTo(24f, 3.9f, 23.1f, 3f, 22f, 3f)
            close()
            moveTo(19f, 15.59f)
            lineTo(17.59f, 17f)
            lineTo(14f, 13.41f)
            lineTo(10.41f, 17f)
            lineTo(9f, 15.59f)
            lineTo(12.59f, 12f)
            lineTo(9f, 8.41f)
            lineTo(10.41f, 7f)
            lineTo(14f, 10.59f)
            lineTo(17.59f, 7f)
            lineTo(19f, 8.41f)
            lineTo(15.41f, 12f)
            close()
        }
    }.build()

    val Calculate: ImageVector = ImageVector.Builder(
        name = "Calculate",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 3f)
            lineTo(5f, 3f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            lineTo(3f, 19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            lineTo(19f, 21f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            lineTo(21f, 5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            close()
            moveTo(19f, 19f)
            lineTo(5f, 19f)
            lineTo(5f, 5f)
            lineTo(19f, 5f)
            lineTo(19f, 19f)
            close()
            moveTo(6.25f, 7.75f)
            horizontalLineTo(9.75f)
            verticalLineTo(9.25f)
            horizontalLineTo(6.25f)
            close()
            moveTo(14.25f, 7.75f)
            horizontalLineTo(17.75f)
            verticalLineTo(9.25f)
            horizontalLineTo(14.25f)
            close()
            moveTo(6.25f, 13.25f)
            horizontalLineTo(9.75f)
            verticalLineTo(14.75f)
            horizontalLineTo(6.25f)
            close()
            moveTo(14.25f, 12.5f)
            horizontalLineTo(17.75f)
            verticalLineTo(14f)
            horizontalLineTo(14.25f)
            close()
            moveTo(14.25f, 15f)
            horizontalLineTo(17.75f)
            verticalLineTo(16.5f)
            horizontalLineTo(14.25f)
            close()
        }
    }.build()

    val ContentCopy: ImageVector = ImageVector.Builder(
        name = "ContentCopy",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(16f, 1f)
            lineTo(4f, 1f)
            curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f)
            lineTo(2f, 17f)
            horizontalLineTo(4f)
            lineTo(4f, 3f)
            horizontalLineTo(16f)
            close()
            moveTo(19f, 5f)
            lineTo(8f, 5f)
            curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f)
            lineTo(6f, 21f)
            curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f)
            lineTo(19f, 23f)
            curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f)
            lineTo(21f, 7f)
            curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
            close()
            moveTo(19f, 21f)
            lineTo(8f, 21f)
            lineTo(8f, 7f)
            lineTo(19f, 7f)
            close()
        }
    }.build()

    val CurrencyRupee: ImageVector = ImageVector.Builder(
        name = "CurrencyRupee",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(13.66f, 7f)
            curveTo(13.1f, 5.82f, 11.9f, 5f, 10f, 5f)
            lineTo(6f, 5f)
            verticalLineTo(3f)
            horizontalLineTo(18f)
            verticalLineTo(5f)
            horizontalLineTo(14.8f)
            curveTo(15.54f, 5.86f, 16f, 6.98f, 16f, 8.2f)
            horizontalLineTo(18f)
            verticalLineTo(10.2f)
            horizontalLineTo(16f)
            curveTo(15.7f, 12.3f, 14f, 14.1f, 11.5f, 14.4f)
            lineTo(16.5f, 21f)
            horizontalLineTo(13.5f)
            lineTo(8.5f, 14.4f)
            verticalLineTo(12.5f)
            horizontalLineTo(10f)
            curveTo(12f, 12.5f, 13.5f, 11.2f, 13.66f, 9.2f)
            horizontalLineTo(6f)
            verticalLineTo(7.2f)
            horizontalLineTo(13.66f)
            close()
        }
    }.build()

    val LightMode: ImageVector = ImageVector.Builder(
        name = "LightMode",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 7f)
            curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
            curveTo(7f, 14.76f, 9.24f, 17f, 12f, 17f)
            curveTo(14.76f, 17f, 17f, 14.76f, 17f, 12f)
            curveTo(17f, 9.24f, 14.76f, 7f, 12f, 7f)
            close()
            moveTo(13f, 2f)
            horizontalLineTo(11f)
            verticalLineTo(4f)
            horizontalLineTo(13f)
            close()
            moveTo(13f, 20f)
            horizontalLineTo(11f)
            verticalLineTo(22f)
            horizontalLineTo(13f)
            close()
            moveTo(22f, 13f)
            verticalLineTo(11f)
            horizontalLineTo(20f)
            verticalLineTo(13f)
            close()
            moveTo(4f, 13f)
            verticalLineTo(11f)
            horizontalLineTo(2f)
            verticalLineTo(13f)
            close()
        }
    }.build()

    val DarkMode: ImageVector = ImageVector.Builder(
        name = "DarkMode",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12.3f, 2f)
            curveTo(6.36f, 2.37f, 2f, 7.39f, 2f, 13f)
            curveTo(2f, 18.52f, 6.48f, 23f, 12f, 23f)
            curveTo(17.61f, 23f, 22.63f, 18.64f, 23f, 12.7f)
            curveTo(21.43f, 13.52f, 19.64f, 14f, 17.75f, 14f)
            curveTo(12.78f, 14f, 8.75f, 9.97f, 8.75f, 5f)
            curveTo(8.75f, 3.11f, 9.23f, 1.32f, 10.05f, 0.5f)
            curveTo(10.8f, 1.25f, 11.55f, 2f, 12.3f, 2f)
            close()
        }
    }.build()
}
