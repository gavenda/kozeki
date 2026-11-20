/**
 * The clock of a sitting in the reading mode, as the phone's `PhysicalSitting`. It runs from when
 * the mode opens until it closes, except while paused or while the tab is out of sight, and hands
 * its time over in parts to be kept as sessions: when the mode closes, and when the tab goes out of
 * sight, so that time is not lost if the tab is never come back to.
 */

/** Time to keep as one session, from [startedAt] to [endedAt], of which [durationMs] was read. */
export interface SittingPart {
	startedAt: number;
	endedAt: number;
	durationMs: number;
}

/** What the timer shows: [baseMs] plus the time since [runningSince] while it runs. */
export interface SittingClock {
	baseMs: number;
	runningSince: number | null;
}

/** As short as a session in the reader may be. */
export const MIN_PART_MS = 15_000;

export class PhysicalSitting {
	private open = false;
	private partStartedAt = 0;
	private keptMs = 0;
	private pendingMs = 0;
	private runningSince: number | null = null;
	private wasRunning = false;

	private readonly now: () => number;

	constructor(now: () => number = Date.now) {
		this.now = now;
	}

	get isOpen(): boolean {
		return this.open;
	}

	get clock(): SittingClock {
		return { baseMs: this.keptMs + this.pendingMs, runningSince: this.runningSince };
	}

	start(): void {
		if (this.open) return;
		this.open = true;
		this.keptMs = 0;
		this.pendingMs = 0;
		this.partStartedAt = this.now();
		this.runningSince = this.partStartedAt;
	}

	pause(): void {
		if (this.runningSince == null) return;
		this.pendingMs += Math.max(this.now() - this.runningSince, 0);
		this.runningSince = null;
	}

	resume(): void {
		if (!this.open || this.runningSince != null) return;
		this.runningSince = this.now();
		if (this.pendingMs === 0) this.partStartedAt = this.runningSince;
	}

	/** The tab went out of sight: the clock stops, and what it has is handed over. */
	hide(): SittingPart | null {
		if (!this.open) return null;
		this.wasRunning = this.runningSince != null;
		this.pause();
		return this.take(true);
	}

	/** The tab came back: the clock runs again if it was running when it went. */
	show(): void {
		if (this.open && this.wasRunning) this.resume();
		this.wasRunning = false;
	}

	close(): SittingPart | null {
		if (!this.open) return null;
		this.pause();
		const part = this.take(false);
		this.open = false;
		return part;
	}

	/** The time not yet handed over. Too little for a session is kept for the next part when [keepShort], else dropped. */
	private take(keepShort: boolean): SittingPart | null {
		const duration = this.pendingMs;
		if (duration < MIN_PART_MS) {
			if (!keepShort) this.pendingMs = 0;
			return null;
		}
		const end = this.now();
		const part = { startedAt: this.partStartedAt, endedAt: end, durationMs: duration };
		this.keptMs += duration;
		this.pendingMs = 0;
		this.partStartedAt = end;
		return part;
	}
}
