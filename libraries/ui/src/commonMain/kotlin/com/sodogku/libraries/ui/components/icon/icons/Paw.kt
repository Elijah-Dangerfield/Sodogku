package com.sodogku.libraries.ui.components.icon.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import com.sodogku.libraries.ui.components.game.Paw as PawGeometry
import com.sodogku.system.Dimension

/**
 * The paw print as an icon, for anything that wants one at type size and in a
 * theme colour: `Icon(icon = Icons.Paw("..."), color = ...)`.
 *
 * Built from [PawGeometry] rather than transcribed from the SVG, because the
 * same shape is also drawn straight onto a canvas by
 * `com.sodogku.libraries.ui.components.game.drawPaw` — the paw rating, the
 * streak mark, the score burst, the paywall bullets. Two hand-written copies of
 * five circles would look identical on the day they were written and diverge on
 * the first tweak.
 *
 * The ellipse and the circles are each two half-arcs, which is how a vector path
 * spells a closed round shape. `Color.Black` is a placeholder the tint replaces;
 * the source SVG uses `currentColor` for the same reason.
 */
val Paw: ImageVector
    get() {
        _paw?.let { return it }

        val built = ImageVector.Builder(
            name = "Paw",
            defaultWidth = Dimension.D900,
            defaultHeight = Dimension.D900,
            viewportWidth = PawGeometry.EXTENT,
            viewportHeight = PawGeometry.EXTENT,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                oval(
                    centreX = PawGeometry.EXTENT / 2f,
                    centreY = PawGeometry.PAD_CENTRE_Y,
                    radiusX = PawGeometry.PAD_RADIUS_X,
                    radiusY = PawGeometry.PAD_RADIUS_Y,
                )
                PawGeometry.TOES.forEach { (x, y) ->
                    oval(
                        centreX = x,
                        centreY = y,
                        radiusX = PawGeometry.TOE_RADIUS,
                        radiusY = PawGeometry.TOE_RADIUS,
                    )
                }
            }
        }.build()

        _paw = built
        return built
    }

private fun androidx.compose.ui.graphics.vector.PathBuilder.oval(
    centreX: Float,
    centreY: Float,
    radiusX: Float,
    radiusY: Float,
) {
    moveTo(centreX - radiusX, centreY)
    arcToRelative(radiusX, radiusY, 0f, true, true, radiusX * 2f, 0f)
    arcToRelative(radiusX, radiusY, 0f, true, true, -radiusX * 2f, 0f)
    close()
}

private var _paw: ImageVector? = null
