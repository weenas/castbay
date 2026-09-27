package com.weenas.castbay.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The music controls' icons, drawn here rather than taken from material-icons-extended (several
 * MB for a handful of shapes). Solid, rounded-corner-free glyphs in the SF Symbols spirit, on a 24 grid.
 */
object MediaIcons {
    val Play: ImageVector = icon("Play") {
        moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close()
    }

    val Pause: ImageVector = icon("Pause") {
        rect(6f, 4.5f, 4f, 15f)
        rect(14f, 4.5f, 4f, 15f)
    }

    val Next: ImageVector = icon("Next") {
        moveTo(5f, 5f); lineTo(15f, 12f); lineTo(5f, 19f); close()
        rect(16f, 5f, 3f, 14f)
    }

    val Previous: ImageVector = icon("Previous") {
        moveTo(19f, 5f); lineTo(9f, 12f); lineTo(19f, 19f); close()
        rect(5f, 5f, 3f, 14f)
    }

    /** Skip back 10 seconds: an arrow turning anticlockwise around "10" (SF Symbols' gobackward.10). */
    val Back10: ImageVector = skip10("Back10", forward = false)

    /** Skip ahead 10 seconds: an arrow turning clockwise around "10". */
    val Ahead10: ImageVector = skip10("Ahead10", forward = true)

    /** A speaker with sound waves, for the volume display. */
    val Speaker: ImageVector = ImageVector.Builder("Speaker", 24.dp, 24.dp, 24f, 24f)
        .path(fill = SolidColor(Color.White)) {
            moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 4.5f); lineTo(12f, 19.5f); lineTo(7f, 15f); lineTo(3f, 15f); close()
        }
        .path(stroke = SolidColor(Color.White), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round) {
            moveTo(15f, 9f); arcTo(4f, 4f, 0f, false, true, 15f, 15f)
            moveTo(17.5f, 6.5f); arcTo(7.5f, 7.5f, 0f, false, true, 17.5f, 17.5f)
        }
        .build()

    /**
     * A circular arrow, open at the top, with the arrowhead at its start and "10" inside; the
     * digits are drawn as strokes (a vector can't hold text).
     */
    private fun skip10(name: String, forward: Boolean): ImageVector {
        // The arrow is mirrored for back: x' = 24 - x.
        fun x(value: Float) = if (forward) value else 24f - value
        return ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(stroke = SolidColor(Color.White), strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round) {
                // From the top of a circle (centre 12,13, radius 8) round to its upper left.
                moveTo(12f, 5f)
                arcTo(8f, 8f, 0f, true, forward, x(5.07f), 9f)
            }
            .path(stroke = SolidColor(Color.White), strokeLineWidth = 1.4f, strokeLineCap = StrokeCap.Round) {
                // "10", well inside the circle; it reads the same both ways, so it isn't mirrored.
                moveTo(9.3f, 11.4f); lineTo(10.2f, 10.8f); lineTo(10.2f, 15.4f)
                moveTo(13.9f, 10.8f)
                arcTo(1.5f, 2.3f, 0f, true, true, 13.9f, 15.39f)
                arcTo(1.5f, 2.3f, 0f, true, true, 13.9f, 10.8f)
            }
            .path(fill = SolidColor(Color.White)) {
                // The arrowhead, pointing the way the arrow turns.
                moveTo(x(11f), 2.2f); lineTo(x(14.4f), 5f); lineTo(x(11f), 7.8f); close()
            }
            .build()
    }

    /** AirPlay: a screen with a triangle rising into it from below. */
    val AirPlay: ImageVector = icon("AirPlay", PathFillType.EvenOdd) {
        rect(2f, 3f, 20f, 13f)
        rect(4f, 5f, 16f, 9f)
        moveTo(12f, 13f); lineTo(18f, 21f); lineTo(6f, 21f); close()
    }

    /** DLNA: a TV on its stand. */
    val Tv: ImageVector = icon("Tv", PathFillType.EvenOdd) {
        rect(2f, 4f, 20f, 13f)
        rect(4f, 6f, 16f, 9f)
        rect(8f, 19f, 8f, 2f)
    }

    private fun PathBuilder.rect(x: Float, y: Float, width: Float, height: Float) {
        moveTo(x, y); lineTo(x + width, y); lineTo(x + width, y + height); lineTo(x, y + height); close()
    }

    private fun icon(name: String, fillType: PathFillType = PathFillType.NonZero, shape: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .path(fill = SolidColor(Color.White), pathFillType = fillType, pathBuilder = shape)
            .build()
}
