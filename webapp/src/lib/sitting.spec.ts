import { describe, expect, it } from 'vitest';
import { PhysicalSitting } from './sitting';

const minute = 60_000;

function clock() {
	const time = { now: 1_000_000 };
	return { time, sitting: new PhysicalSitting(() => time.now) };
}

describe('PhysicalSitting', () => {
	it('is kept when it closes, less the time it was paused', () => {
		const { time, sitting } = clock();
		sitting.start();
		time.now += 10 * minute;
		sitting.pause();
		time.now += 5 * minute;
		sitting.resume();
		time.now += 2 * minute;
		expect(sitting.close()).toEqual({ startedAt: 1_000_000, endedAt: time.now, durationMs: 12 * minute });
	});

	it('keeps what was read when the tab goes out of sight, and carries on in a new part', () => {
		const { time, sitting } = clock();
		sitting.start();
		time.now += 20 * minute;
		expect(sitting.hide()?.durationMs).toBe(20 * minute);
		time.now += 60 * minute;
		sitting.show();
		time.now += 5 * minute;
		expect(sitting.clock).toEqual({ baseMs: 20 * minute, runningSince: time.now - 5 * minute });
		expect(sitting.close()).toEqual({ startedAt: time.now - 5 * minute, endedAt: time.now, durationMs: 5 * minute });
	});

	it('stays paused when the tab comes back paused', () => {
		const { time, sitting } = clock();
		sitting.start();
		time.now += minute;
		sitting.pause();
		sitting.hide();
		sitting.show();
		expect(sitting.clock.runningSince).toBeNull();
	});

	it('drops a moment too short for a session on closing', () => {
		const { time, sitting } = clock();
		sitting.start();
		time.now += 5_000;
		expect(sitting.close()).toBeNull();
	});
});
