// SPDX-License-Identifier: MIT OR Apache-2.0
// Ported from Rigby2D's core/state.js.
//
// Track 0 is the base layer; higher tracks are applied over it and only change what their
// animations key. Each track plays one entry at a time, crossfading from the previous one, and
// can have more entries queued.
package dev.gavenda.rigby

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A playing animation on a track. `alpha` scales the track's influence (layers), `timeScale` its
 * speed. `delay` (queued entries) is seconds after the previous entry started, or null to start
 * when the previous one ends.
 */
class TrackEntry internal constructor(
    val track: Int,
    val animation: Animation,
    val looping: Boolean,
    val mixDuration: Double,
    internal val delay: Double?,
) {
    val name: String get() = animation.name
    var time = 0.0
        internal set
    internal var mixTime = 0.0

    /** Time before the last update, for event firing (negative so time-0 events fire). */
    internal var lastTime = -1.0

    /** True until the first update, so events at time 0 fire once. */
    internal var fresh = true
    var alpha = 1.0
    var timeScale = 1.0

    /** The entry being faded out. */
    internal var previous: TrackEntry? = null
    internal var queue = ArrayList<TrackEntry>()

    /** Mix progress in [0, 1]: how much this entry has replaced the previous one. */
    internal fun mix(): Double = if (mixDuration > 0) min(1.0, mixTime / mixDuration) else 1.0
}

/** An event key merged with its definition (keys override the definition's values). */
class Event(
    val name: String,
    val time: Double,
    val loop: Int,
    val int: Int,
    val float: Double,
    val string: String?,
    val audio: String?,
    val volume: Double,
    val balance: Double,
)

/** Playback callbacks. Every method has a default that does nothing. */
interface AnimationStateListener {
    fun start(entry: TrackEntry) {}

    /** The entry was replaced or cleared. */
    fun end(entry: TrackEntry) {}

    /** The entry reached its end; every loop for a looping entry. */
    fun complete(entry: TrackEntry) {}

    fun event(entry: TrackEntry, event: Event) {}
}

/** Plays animations on a skeleton: tracks, crossfades, queues and events. */
class AnimationState(val data: SkeletonData) {
    private val tracks = ArrayList<TrackEntry?>()
    private val listeners = ArrayList<AnimationStateListener>()

    /** Playback speed multiplier for every track. */
    var speed = 1.0

    fun addListener(listener: AnimationStateListener) {
        listeners += listener
    }

    fun removeListener(listener: AnimationStateListener) {
        listeners -= listener
    }

    private inline fun notify(block: AnimationStateListener.() -> Unit) {
        for (l in listeners.toList()) l.block()
    }

    /** The entry playing on `track`, or null. */
    fun current(track: Int): TrackEntry? = tracks.getOrNull(track)

    /**
     * Starts `name` on `track` now, crossfading from what it played over `mix` seconds and
     * dropping anything queued. Returns the entry, or null if no animation has that name.
     */
    fun setAnimation(track: Int, name: String, looping: Boolean, mix: Double = 0.0): TrackEntry? {
        val animation = data.findAnimation(name) ?: return null
        val entry = TrackEntry(track, animation, looping, mix, null)
        val current = tracks.getOrNull(track)
        if (current != null) {
            for (q in current.queue) notify { end(q) }
            current.queue = ArrayList()
            replace(current, entry)
        } else {
            setTrack(track, entry)
            notify { start(entry) }
        }
        return entry
    }

    /**
     * Queues `name` on `track` after the current entry (or the last queued one): when it ends, or
     * `delay` seconds after it starts. Starts right away on an empty track.
     */
    fun addAnimation(track: Int, name: String, looping: Boolean, mix: Double = 0.0, delay: Double? = null): TrackEntry? {
        val current = tracks.getOrNull(track) ?: return setAnimation(track, name, looping, mix)
        val animation = data.findAnimation(name) ?: return null
        val entry = TrackEntry(track, animation, looping, mix, delay)
        current.queue += entry
        return entry
    }

    /** Stops a track (its animation stops affecting the pose). */
    fun clearTrack(track: Int) {
        val current = tracks.getOrNull(track) ?: return
        for (e in listOfNotNull(current.previous, current) + current.queue) notify { end(e) }
        tracks[track] = null
    }

    fun clearTracks() {
        for (i in tracks.indices) clearTrack(i)
        tracks.clear()
    }

    private fun setTrack(track: Int, entry: TrackEntry) {
        while (tracks.size <= track) tracks += null
        tracks[track] = entry
    }

    private fun replace(from: TrackEntry, to: TrackEntry) {
        // A fade that has not finished keeps fading out from the newest pose.
        from.previous?.let { p -> notify { end(p) } }
        from.previous = null
        to.previous = if (to.mixDuration > 0) from else null
        if (to.previous == null) notify { end(from) }
        setTrack(to.track, to)
        notify { start(to) }
    }

    /** Advances time by `dt` seconds, starting queued entries and firing events. */
    fun update(dt: Double) {
        val step = dt * speed
        for (entry in tracks.toList()) {
            if (entry == null) continue
            advance(entry, step)
            // Start the next queued entry once its time comes.
            val next = entry.queue.firstOrNull() ?: continue
            val start = next.delay ?: max(0.0, entry.animation.duration - next.mixDuration)
            if (entry.time >= start) {
                entry.queue.removeAt(0)
                next.queue = entry.queue
                entry.queue = ArrayList()
                // Carry over the time past the switch point.
                next.time = max(0.0, entry.time - start)
                next.mixTime = next.time
                next.lastTime = -1.0
                next.fresh = false
                replace(entry, next)
                fireEvents(next)
            }
        }
    }

    private fun advance(entry: TrackEntry, dt: Double) {
        val step = dt * entry.timeScale
        entry.lastTime = if (entry.fresh) -1.0 else entry.time
        entry.fresh = false
        entry.time += step
        entry.mixTime += step
        fireEvents(entry)
        val previous = entry.previous ?: return
        // The entry fading out keeps playing, firing its events, until the mix ends.
        previous.lastTime = previous.time
        previous.time += dt * previous.timeScale
        fireEvents(previous)
        if (entry.mixTime >= entry.mixDuration) {
            notify { end(previous) }
            entry.previous = null
        }
    }

    /** Fires the events and completions between an entry's last and current time. */
    private fun fireEvents(entry: TrackEntry) {
        val anim = entry.animation
        val d = anim.duration
        val from = entry.lastTime
        val to = entry.time
        if (to < from) return
        fun fire(t0: Double, t1: Double, loop: Int) {
            for (e in anim.events) if (e.time > t0 && e.time <= t1) notify { event(entry, eventData(e, loop)) }
        }
        if (!entry.looping || d <= 0) {
            fire(from, min(to, d), 0)
            if (from < d && to >= d) notify { complete(entry) }
            return
        }
        // Looping: fire per loop crossed.
        val startLoop = floor(max(from, 0.0) / d).toInt()
        val endLoop = floor(to / d).toInt()
        for (loop in startLoop..endLoop) {
            val t0 = if (loop == startLoop) from - loop * d else -1.0
            val t1 = if (loop == endLoop) to - loop * d else d
            fire(t0, t1, loop)
            if (loop < endLoop) notify { complete(entry) }
        }
    }

    private fun eventData(key: EventKey, loop: Int): Event {
        val def = data.events.find { it.name == key.name }
        return Event(
            name = key.name,
            time = key.time,
            loop = loop,
            int = key.int ?: def?.int ?: 0,
            float = key.float ?: def?.float ?: 0.0,
            string = key.string ?: def?.string,
            audio = def?.audio,
            volume = key.volume ?: def?.volume ?: 1.0,
            balance = key.balance ?: def?.balance ?: 0.0,
        )
    }

    /** Resets `pose` to setup, applies every track in order and updates world transforms. */
    fun apply(pose: Pose) {
        pose.reset()
        for (entry in tracks) {
            if (entry == null) continue
            val layer = if (entry.track == 0) 1.0 else entry.alpha
            entry.previous?.let { applyEntry(pose, it, layer * it.alpha) }
            applyEntry(pose, entry, (if (entry.previous != null) entry.mix() else 1.0) * if (entry.track == 0) entry.alpha else layer)
        }
        pose.updateWorld()
    }

    private fun applyEntry(pose: Pose, entry: TrackEntry, alpha: Double) {
        val anim = entry.animation
        if (alpha <= 0) return
        val time = if (entry.looping && anim.duration > 0) {
            ((entry.time % anim.duration) + anim.duration) % anim.duration
        } else {
            min(entry.time, anim.duration)
        }
        pose.apply(anim, time, min(1.0, alpha))
    }
}
