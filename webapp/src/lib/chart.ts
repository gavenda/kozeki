/** Rounds up to 1, 2 or 5 times a power of ten, so axis ticks land on clean numbers (`niceCeiling`). */
export function niceCeiling(value: number): number {
	if (value <= 0) return 1;
	const magnitude = 10 ** Math.floor(Math.log10(value));
	const fraction = value / magnitude;
	const nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
	return Math.max(nice * magnitude, Math.ceil(value));
}

/** The axis ticks: nought, the middle and the top, or just the ends when the middle of a count is not whole. */
export function ticksFor(top: number, wholeNumbers: boolean): number[] {
	return !wholeNumbers || (top / 2) % 1 === 0 ? [0, top / 2, top] : [0, top];
}

let context: CanvasRenderingContext2D | null | undefined;

/** How wide [text] is drawn in [font], for laying out a chart's labels. A rough guess where there is no canvas. */
export function textWidth(text: string, font: string): number {
	if (context === undefined) context = typeof document === 'undefined' ? null : document.createElement('canvas').getContext('2d');
	if (!context) return text.length * 6.5;
	context.font = font;
	return context.measureText(text).width;
}
