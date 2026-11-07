/**
 * The cover of a book that has none, bound like the one Ui carries: a spine down one side, and on
 * the board a line that frames the title, with a scroll in each corner. Ported from the phone's
 * `CoverPlaceholder` (BookCover.kt), whose measures are fractions of the cover's width.
 */

/** Java's `String.hashCode`, so a title gets the same colour on the web as on the phone. */
export function javaHash(text: string): number {
	let hash = 0;
	for (let index = 0; index < text.length; index++) hash = (Math.imul(31, hash) + text.charCodeAt(index)) | 0;
	return hash;
}

export type PlaceholderTone = 'primary' | 'secondary' | 'tertiary';

export function placeholderTone(title: string): PlaceholderTone {
	const index = Math.abs(javaHash(title)) % 3;
	return index === 0 ? 'primary' : index === 1 ? 'secondary' : 'tertiary';
}

export const SPINE = 0.075;
export const FRAME_INSET = 0.09;
export const TITLE_GAP = 0.03;
const FRAME_STROKE = 0.014;
const SCROLL_STROKE = 0.0115;
const SCROLLS_ACROSS = 0.17;
const SCROLLS_DOWN = 0.22;
const FRAME_CORNER = 0.03;
const EYE = 0.3;
const STEM_REACH = 0.45;
const STEPS_PER_TURN = 24;
/** A narrower cover's scrolls would close up into blots. */
const SCROLLS_FROM_WIDTH = 80;

interface Spiral {
	x: number;
	y: number;
	radius: number;
	turns: number;
}

// The scroll along the top is the larger. The one down the side starts under it with a smaller curl.
const ACROSS_FIRST: Spiral = { x: 0.034, y: 0.034, radius: 0.034, turns: 1.2 };
const ACROSS_SECOND: Spiral = { x: 0.131, y: 0.033, radius: 0.03, turns: 1.2 };
const DOWN_FIRST: Spiral = { x: 0.101, y: 0.021, radius: 0.021, turns: 0.85 };
const DOWN_SECOND: Spiral = { x: 0.178, y: 0.034, radius: 0.034, turns: 1.2 };

type Point = [number, number];

/** From the eye outwards, ending on the side of the spiral that [endAngle] points to. */
function spiralPoints(spiral: Spiral, endAngle: number): Point[] {
	const steps = Math.round(spiral.turns * STEPS_PER_TURN);
	return Array.from({ length: steps + 1 }, (_, step) => {
		const progress = step / steps;
		const angle = endAngle + (1 - progress) * spiral.turns * 2 * Math.PI;
		const distance = spiral.radius * (EYE + (1 - EYE) * progress);
		return [spiral.x + distance * Math.cos(angle), spiral.y + distance * Math.sin(angle)];
	});
}

const heading = (from: Point, to: Point): Point => {
	const length = Math.hypot(to[0] - from[0], to[1] - from[1]);
	return [(to[0] - from[0]) / length, (to[1] - from[1]) / length];
};

/** Two spirals joined by a stem into an S, laid out along a line of the frame; [place] puts a point on the cover. */
function scroll(first: Spiral, second: Spiral, place: (point: Point) => Point): string {
	const unwinding = spiralPoints(first, Math.PI / 2);
	const winding = spiralPoints(second, -Math.PI / 2).reverse();
	const start = winding[0];
	const end = unwinding[unwinding.length - 1];
	const reach = STEM_REACH * Math.hypot(start[0] - end[0], start[1] - end[1]);
	const out = heading(unwinding[unwinding.length - 2], end);
	const into = heading(winding[1], start);
	const leaving = place([end[0] + out[0] * reach, end[1] + out[1] * reach]);
	const joining = place([start[0] + into[0] * reach, start[1] + into[1] * reach]);
	const f = (p: Point) => `${p[0].toFixed(2)} ${p[1].toFixed(2)}`;
	return [
		`M${f(place(unwinding[0]))}`,
		...unwinding.slice(1).map((p) => `L${f(place(p))}`),
		`C${f(leaving)} ${f(joining)} ${f(place(start))}`,
		...winding.slice(1).map((p) => `L${f(place(p))}`),
	].join('');
}

export interface Binding {
	spine: number;
	hairline: number;
	line: number;
	frame: { left: number; top: number; right: number; bottom: number };
	/** The frame's line all the way round, on a cover too small for scrolls. */
	frameCorner: number;
	/** The four sides, stopping short of the corners, and the scrolls in them. */
	sides?: string;
	scrolls?: string;
	scrollStroke: number;
}

/** The binding drawn on a cover [width] by [height] pixels. */
export function bindingOf(width: number, height: number): Binding {
	const hairline = 1;
	const inset = width * FRAME_INSET;
	const frame = { left: width * SPINE + inset, top: inset, right: width - inset, bottom: height - inset };
	const binding: Binding = {
		spine: width * SPINE,
		hairline,
		line: Math.max(width * FRAME_STROKE, hairline),
		frame,
		frameCorner: width * FRAME_CORNER,
		scrollStroke: Math.max(width * SCROLL_STROKE, hairline),
	};
	if (width < SCROLLS_FROM_WIDTH) return binding;

	const across = width * SCROLLS_ACROSS;
	const down = width * SCROLLS_DOWN;
	binding.sides = [
		`M${frame.left + across} ${frame.top}H${frame.right - across}`,
		`M${frame.left + across} ${frame.bottom}H${frame.right - across}`,
		`M${frame.left} ${frame.top + down}V${frame.bottom - down}`,
		`M${frame.right} ${frame.top + down}V${frame.bottom - down}`,
	].join('');
	const parts: string[] = [];
	for (const [x, inX] of [
		[frame.left, 1],
		[frame.right, -1],
	]) {
		for (const [y, inY] of [
			[frame.top, 1],
			[frame.bottom, -1],
		]) {
			parts.push(scroll(ACROSS_FIRST, ACROSS_SECOND, ([px, py]) => [x + inX * px * width, y + inY * py * width]));
			parts.push(scroll(DOWN_FIRST, DOWN_SECOND, ([px, py]) => [x + inX * py * width, y + inY * px * width]));
		}
	}
	binding.scrolls = parts.join('');
	return binding;
}
