package app.wiis.launcher.ui

import android.graphics.Bitmap
import androidx.compose.runtime.staticCompositionLocalOf
import app.wiis.emulator.Game
import app.wiis.launcher.AppGraph
import app.wiis.system.AppEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Where screens get pictures: box art and app icons. The app reads the art repository and the
 * package manager; screenshot tests and previews pass their own.
 */
interface ImageSource {
    /** Emits a game uri whenever its art arrives after the tile was drawn. */
    val artUpdates: Flow<String>

    /** Box art at roughly [sizePx] on its longest side, or null if there is none yet. */
    suspend fun art(game: Game, sizePx: Int): Bitmap?

    suspend fun icon(app: AppEntry): Bitmap?

    object None : ImageSource {
        override val artUpdates: Flow<String> = emptyFlow()
        override suspend fun art(game: Game, sizePx: Int): Bitmap? = null
        override suspend fun icon(app: AppEntry): Bitmap? = null
    }
}

class GraphImageSource(private val graph: AppGraph) : ImageSource {
    override val artUpdates: Flow<String> get() = graph.art.updates
    override suspend fun art(game: Game, sizePx: Int): Bitmap? = graph.art.load(game, sizePx)
    override suspend fun icon(app: AppEntry): Bitmap? = graph.apps.icon(app)
}

val LocalImageSource = staticCompositionLocalOf<ImageSource> { ImageSource.None }
