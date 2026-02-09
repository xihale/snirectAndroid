package com.xihale.snirect.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    private fun materialIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
    }

    val Shield: ImageVector
        get() = materialIcon(name = "Filled.Shield") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.0f, 1.0f)
                lineTo(3.0f, 5.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 5.55f, 3.84f, 10.74f, 9.0f, 12.0f)
                curveToRelative(5.16f, -1.26f, 9.0f, -6.45f, 9.0f, -12.0f)
                verticalLineTo(5.0f)
                lineTo(12.0f, 1.0f)
                close()
            }
        }

    val Speed: ImageVector
        get() = materialIcon(name = "Filled.Speed") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20.38f, 8.57f)
                lineToRelative(-1.23f, 1.85f)
                curveToRelative(0.0f, 0.0f, -0.01f, 0.0f, -0.01f, 0.0f)
                curveTo(20.22f, 11.0f, 21.0f, 11.97f, 21.0f, 13.0f)
                curveToRelative(0.0f, 2.76f, -2.24f, 5.0f, -5.0f, 5.0f)
                reflectiveCurveToRelative(-5.0f, -2.24f, -5.0f, -5.0f)
                curveToRelative(0.0f, -0.99f, 0.31f, -1.96f, 0.9f, -2.74f)
                lineToRelative(-1.23f, -1.84f)
                curveToRelative(0.0f, 0.0f, 0.0f, 0.01f, 0.0f, 0.01f)
                curveTo(9.0f, 9.69f, 8.0f, 11.22f, 8.0f, 13.0f)
                curveToRelative(0.0f, 4.42f, 3.58f, 8.0f, 8.0f, 8.0f)
                reflectiveCurveToRelative(8.0f, -3.58f, 8.0f, -8.0f)
                curveTo(24.0f, 11.22f, 23.0f, 9.69f, 20.38f, 8.57f)
                close()
                moveTo(12.0f, 16.0f)
                lineToRelative(3.5f, -6.5f)
                curveTo(16.21f, 10.3f, 16.9f, 11.6f, 16.9f, 13.0f)
                curveToRelative(0.0f, 2.76f, -2.24f, 5.0f, -5.0f, 5.0f)
                reflectiveCurveToRelative(-5.0f, -2.24f, -5.0f, -5.0f)
                curveToRelative(0.0f, -0.97f, 0.35f, -1.85f, 0.93f, -2.57f)
                lineTo(8.5f, 11.0f)
                lineTo(12.0f, 16.0f)
                close()
            }
        }

    val Terminal: ImageVector
        get() = materialIcon(name = "Filled.Terminal") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20.0f, 4.0f)
                horizontalLineTo(4.0f)
                curveTo(2.89f, 4.0f, 2.0f, 4.89f, 2.0f, 6.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.11f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.11f, 0.0f, 2.0f, -0.89f, 2.0f, -2.0f)
                verticalLineTo(6.0f)
                curveTo(22.0f, 4.89f, 21.11f, 4.0f, 20.0f, 4.0f)
                close()
                moveTo(20.0f, 18.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(8.0f)
                horizontalLineToRelative(16.0f)
                verticalLineTo(18.0f)
                close()
                moveTo(18.0f, 17.0f)
                horizontalLineToRelative(-6.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(17.0f)
                close()
                moveTo(6.0f, 13.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(6.0f)
                verticalLineTo(13.0f)
                close()
            }
        }

    val Rule: ImageVector
        get() = materialIcon(name = "Filled.Rule") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(16.54f, 11.0f)
                lineTo(13.0f, 7.46f)
                lineToRelative(1.6f, -1.6f)
                curveToRelative(0.8f, -0.8f, 2.05f, -0.8f, 2.85f, 0.0f)
                lineToRelative(0.7f, 0.7f)
                curveToRelative(0.8f, 0.8f, 0.8f, 2.05f, 0.0f, 2.85f)
                lineTo(16.54f, 11.0f)
                close()
                moveTo(19.24f, 8.32f)
                lineTo(18.53f, 7.61f)
                curveToRelative(-0.29f, -0.29f, -0.77f, -0.29f, -1.06f, 0.0f)
                lineToRelative(-0.47f, 0.47f)
                lineToRelative(1.77f, 1.77f)
                lineToRelative(0.47f, -0.47f)
                curveTo(19.53f, 9.09f, 19.53f, 8.61f, 19.24f, 8.32f)
                close()
                moveTo(3.0f, 19.0f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(3.0f)
                verticalLineTo(19.0f)
                close()
                moveTo(15.11f, 12.43f)
                lineTo(13.34f, 14.2f)
                curveTo(13.15f, 14.39f, 12.89f, 14.5f, 12.63f, 14.5f)
                curveToRelative(-0.26f, 0.0f, -0.51f, -0.1f, -0.71f, -0.29f)
                lineTo(3.29f, 5.59f)
                curveToRelative(-0.39f, -0.39f, -0.39f, -1.02f, 0.0f, -1.41f)
                lineTo(5.59f, 1.88f)
                curveToRelative(0.39f, -0.39f, 1.02f, -0.39f, 1.41f, 0.0f)
                lineTo(15.11f, 10.0f)
                curveTo(15.5f, 10.39f, 15.5f, 11.02f, 15.11f, 12.43f)
                close()
                moveTo(5.59f, 3.29f)
                lineToRelative(-1.29f, 1.29f)
                lineTo(7.5f, 7.79f)
                lineToRelative(1.29f, -1.29f)
                lineTo(5.59f, 3.29f)
                close()
            }
        }

    val BugReport: ImageVector
        get() = materialIcon(name = "Filled.BugReport") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20.0f, 8.0f)
                horizontalLineToRelative(-2.81f)
                curveToRelative(-0.45f, -0.78f, -1.07f, -1.45f, -1.82f, -1.96f)
                lineTo(17.0f, 4.41f)
                lineTo(15.59f, 3.0f)
                lineToRelative(-2.17f, 2.17f)
                curveTo(12.96f, 5.06f, 12.49f, 5.0f, 12.0f, 5.0f)
                curveToRelative(-0.49f, 0.0f, -0.96f, 0.06f, -1.41f, 0.17f)
                lineTo(8.41f, 3.0f)
                lineTo(7.0f, 4.41f)
                lineToRelative(1.62f, 1.63f)
                curveTo(7.88f, 6.55f, 7.26f, 7.22f, 6.81f, 8.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.09f)
                curveTo(6.04f, 10.33f, 6.0f, 10.66f, 6.0f, 11.0f)
                verticalLineToRelative(1.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(1.0f)
                curveToRelative(0.0f, 0.34f, 0.04f, 0.67f, 0.09f, 1.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.81f)
                curveToRelative(1.04f, 1.79f, 2.97f, 3.0f, 5.19f, 3.0f)
                reflectiveCurveToRelative(4.15f, -1.21f, 5.19f, -3.0f)
                horizontalLineTo(20.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.09f)
                curveToRelative(0.05f, -0.33f, 0.09f, -0.66f, 0.09f, -1.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-1.0f)
                curveToRelative(0.0f, -0.34f, -0.04f, -0.67f, -0.09f, -1.0f)
                horizontalLineTo(20.0f)
                verticalLineTo(8.0f)
                close()
                moveTo(14.0f, 16.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(16.0f)
                close()
                moveTo(14.0f, 12.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(12.0f)
                close()
            }
        }

    val Dns: ImageVector
        get() = materialIcon(name = "Filled.Dns") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20.0f, 13.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-6.0f)
                curveTo(21.0f, 13.45f, 20.55f, 13.0f, 20.0f, 13.0f)
                close()
                moveTo(7.0f, 19.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                reflectiveCurveTo(8.1f, 19.0f, 7.0f, 19.0f)
                close()
                moveTo(20.0f, 3.0f)
                horizontalLineTo(4.0f)
                curveTo(3.45f, 3.0f, 3.0f, 3.45f, 3.0f, 4.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(4.0f)
                curveTo(21.0f, 3.45f, 20.55f, 3.0f, 20.0f, 3.0f)
                close()
                moveTo(7.0f, 9.0f)
                curveTo(5.9f, 9.0f, 5.0f, 8.1f, 5.0f, 7.0f)
                reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                reflectiveCurveTo(8.1f, 9.0f, 7.0f, 9.0f)
                close()
            }
        }

    val Update: ImageVector
        get() = materialIcon(name = "Filled.Update") {
            path(fill = SolidColor(Color.Black)) {
                 moveTo(12.0f, 2.0f)
                 curveTo(6.5f, 2.0f, 2.0f, 6.5f, 2.0f, 12.0f)
                 reflectiveCurveToRelative(4.5f, 10.0f, 10.0f, 10.0f)
                 reflectiveCurveToRelative(10.0f, -4.5f, 10.0f, -10.0f)
                 horizontalLineToRelative(-2.0f)
                 curveToRelative(0.0f, 4.4f, -3.6f, 8.0f, -8.0f, 8.0f)
                 reflectiveCurveToRelative(-8.0f, -3.6f, -8.0f, -8.0f)
                 reflectiveCurveToRelative(3.6f, -8.0f, 8.0f, -8.0f)
                 curveToRelative(2.0f, 0.0f, 3.8f, 0.7f, 5.2f, 1.9f)
                 lineToRelative(-3.2f, 3.2f)
                 horizontalLineTo(22.0f)
                 verticalLineTo(2.1f)
                 lineToRelative(-3.5f, 3.5f)
                 curveTo(16.7f, 3.8f, 14.5f, 2.0f, 12.0f, 2.0f)
                 close()
            }
        }

    val NetworkCheck: ImageVector
        get() = materialIcon(name = "Filled.NetworkCheck") {
             path(fill = SolidColor(Color.Black)) {
                moveTo(1.0f, 9.0f)
                lineToRelative(2.0f, 2.0f)
                curveToRelative(4.97f, -4.97f, 13.03f, -4.97f, 18.0f, 0.0f)
                lineToRelative(2.0f, -2.0f)
                curveTo(16.93f, 2.93f, 7.08f, 2.93f, 1.0f, 9.0f)
                close()
                moveTo(9.0f, 17.0f)
                lineToRelative(3.0f, 3.0f)
                lineToRelative(3.0f, -3.0f)
                curveToRelative(-1.65f, -1.66f, -4.34f, -1.66f, -6.0f, 0.0f)
                close()
                moveTo(5.0f, 13.0f)
                lineToRelative(2.0f, 2.0f)
                curveToRelative(2.76f, -2.76f, 7.24f, -2.76f, 10.0f, 0.0f)
                lineToRelative(2.0f, -2.0f)
                curveTo(15.14f, 9.14f, 8.87f, 9.14f, 5.0f, 13.0f)
                close()
             }
        }

    val DeleteSweep: ImageVector
        get() = materialIcon(name = "Filled.DeleteSweep") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(15.0f, 16.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-4.0f)
                close()
                moveTo(15.0f, 8.0f)
                horizontalLineToRelative(7.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-7.0f)
                close()
                moveTo(15.0f, 12.0f)
                horizontalLineToRelative(7.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-7.0f)
                close()
                moveTo(3.0f, 18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(6.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(8.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(10.0f)
                close()
                moveTo(14.0f, 5.0f)
                horizontalLineToRelative(-3.0f)
                lineToRelative(-1.0f, -1.0f)
                horizontalLineTo(6.0f)
                lineTo(5.0f, 5.0f)
                horizontalLineTo(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(12.0f)
                close()
            }
        }
}