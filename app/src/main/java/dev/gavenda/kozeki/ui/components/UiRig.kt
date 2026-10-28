package dev.gavenda.kozeki.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import dev.gavenda.rigby.AnimationState
import dev.gavenda.rigby.AnimationStateListener
import dev.gavenda.rigby.Atlas
import dev.gavenda.rigby.Pose
import dev.gavenda.rigby.SkeletonData
import dev.gavenda.rigby.SkeletonJson
import dev.gavenda.rigby.TrackEntry
import dev.gavenda.rigby.android.AtlasTextures
import dev.gavenda.rigby.android.Camera
import dev.gavenda.rigby.android.SkeletonRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlin.math.min
import kotlin.random.Random

/**
 * Ui's rig from the game, the one her portraits were drawn from, played by the `:rigby` module.
 * It is read once, in the background, and shared by every portrait on screen; each portrait plays
 * it with its own pose.
 */
internal class UiRig(val data: SkeletonData, val textures: AtlasTextures) {
    companion object {
        // Read in a scope of its own, so a portrait that leaves the screen mid-read doesn't
        // cancel it for the others.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var loading: Deferred<UiRig?>? = null

        /** The rig, or null if it could not be read (the portraits then stay as pictures). */
        suspend fun get(context: Context): UiRig? {
            val pending = loading ?: scope.async {
                runCatching { read(context) }.onFailure { Log.w("UiRig", "Ui's rig could not be read", it) }.getOrNull()
            }.also { loading = it }
            return pending.await()
        }

        private fun read(context: Context): UiRig {
            val assets = context.assets
            val data = SkeletonJson.read(assets.open("ui/ui.json").bufferedReader().use { it.readText() }, "ui")
            val atlas = Atlas.parse(assets.open("ui/ui.atlas").bufferedReader().use { it.readText() })
            val page = assets.open("ui/ui.webp").use(BitmapFactory::decodeStream)
            // Portraits draw her at about the atlas's own size, so the page is used as it is.
            return UiRig(data, AtlasTextures(atlas, mapOf(atlas.pages[0].name to page)))
        }
    }
}

/** Animation tracks, bottom to top. */
private const val TRACK_IDLE = 0
private const val TRACK_FACE = 1
private const val TRACK_BLINK = 2
private const val FACE_MIX = 0.2

// Where the cut portraits sit on her rig, measured by laying the rig over the source artwork:
// source pixels per rig unit, and the rig point at the source's top-left corner. A portrait is
// cut from the source PORTRAIT_LEFT pixels in, so its own top-left is that much further right.
private const val SOURCE_SCALE = 0.64774
private const val SOURCE_LEFT = -663.319
private const val SOURCE_TOP = 1484.576
private const val PORTRAIT_LEFT = 234

/** Whether the system lets things move: "Remove animations" holds her still. */
private fun motionAllowed() = ValueAnimator.areAnimatorsEnabled()

/**
 * Ui as [Portrait] shows her, alive: she breathes, her halo glows and fades, and she blinks
 * when her face has open eyes. A change of [expression] blends into the new face. Draws nothing
 * until her rig is read; returns false until then, so the still portrait can stand in.
 */
@Composable
internal fun LiveUi(expression: Expression, cutWidth: Float, modifier: Modifier = Modifier): Boolean {
    val context = LocalContext.current
    val rig by produceState<UiRig?>(null) { value = UiRig.get(context.applicationContext) }
    val loaded = rig ?: return false
    val moving = remember { motionAllowed() }
    val player = remember(loaded) { Player(loaded) }
    // Before the next draw, so a portrait held still shows the new face too.
    SideEffect { player.show(expression, if (moving) FACE_MIX else 0.0) }
    var frame by remember { mutableLongStateOf(0L) }
    if (moving) {
        LaunchedEffect(player) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    // At most a tenth of a second at once, so she doesn't jump after a pause.
                    player.advance(if (last == 0L) 0.0 else min(0.1, (now - last) / 1e9))
                    last = now
                    frame = now
                }
            }
        }
    }
    DisposableEffect(player) { onDispose { player.dispose() } }
    val renderer = remember { SkeletonRenderer() }
    Canvas(modifier) {
        frame // redraw every frame
        player.apply()
        // The canvas is the cut portrait: cutWidth source pixels across.
        val zoom = (SOURCE_SCALE * size.width / cutWidth).toFloat()
        val camera = Camera((SOURCE_LEFT + PORTRAIT_LEFT / SOURCE_SCALE).toFloat(), SOURCE_TOP.toFloat(), zoom)
        drawIntoCanvas { renderer.draw(it.nativeCanvas, player.pose, loaded.textures, camera) }
    }
    return true
}

/** One portrait's playback of the shared rig. */
private class Player(rig: UiRig) {
    val pose = Pose(rig.data)
    private val state = AnimationState(rig.data)
    private var blinks = false
    private var untilBlink = nextBlink()
    private var posed = false

    private val listener = object : AnimationStateListener {
        // A blink holds its last frame, which is the plain face, until its track is cleared.
        override fun complete(entry: TrackEntry) {
            if (entry.track == TRACK_BLINK) state.clearTrack(TRACK_BLINK)
        }
    }

    init {
        state.addListener(listener)
        state.setAnimation(TRACK_IDLE, "Idle_01", looping = true)
    }

    fun show(expression: Expression, mix: Double) {
        val current = state.current(TRACK_FACE)
        if (current?.name != expression.animation) {
            state.setAnimation(TRACK_FACE, expression.animation, looping = true, mix = if (current == null) 0.0 else mix)
        }
        blinks = expression.blinks
        if (!blinks) state.clearTrack(TRACK_BLINK)
        posed = false
    }

    fun advance(dt: Double) {
        if (blinks) {
            untilBlink -= dt
            if (untilBlink <= 0) {
                state.setAnimation(TRACK_BLINK, "Eye_Close_01", looping = false)
                untilBlink = nextBlink()
            }
        }
        state.update(dt)
        posed = false
    }

    /** Poses her for drawing, once per update. */
    fun apply() {
        if (posed) return
        state.apply(pose)
        posed = true
    }

    fun dispose() {
        state.removeListener(listener)
    }

    private fun nextBlink() = 2.5 + Random.nextDouble() * 3.5
}
