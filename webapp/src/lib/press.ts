/**
 * A tap, and a long press as the phone picks a book with. A mouse has right-click for the long
 * press too, and Enter or Space taps from the keyboard. Spread the result on the element: `v-on="press(…)"`.
 */
export function press(onTap: () => void, onLongPress: () => void) {
	let timer: ReturnType<typeof setTimeout> | undefined;
	let longPressed = false;
	let start: { x: number; y: number } | null = null;
	const cancel = () => {
		clearTimeout(timer);
		timer = undefined;
	};
	return {
		pointerdown(event: PointerEvent) {
			if (event.button !== 0) return;
			longPressed = false;
			start = { x: event.clientX, y: event.clientY };
			timer = setTimeout(() => {
				longPressed = true;
				onLongPress();
			}, 500);
		},
		pointermove(event: PointerEvent) {
			if (start && Math.hypot(event.clientX - start.x, event.clientY - start.y) > 10) cancel();
		},
		pointerup: cancel,
		pointercancel: cancel,
		pointerleave: cancel,
		click(event: MouseEvent) {
			if (longPressed) {
				longPressed = false;
				event.preventDefault();
				return;
			}
			onTap();
		},
		contextmenu(event: MouseEvent) {
			event.preventDefault();
			cancel();
			// A touch long press fires this too, as the timer does: it must pick the book once, not twice.
			if (longPressed) return;
			// Any click that ends this press is swallowed, as after the timer's.
			longPressed = true;
			onLongPress();
		},
		keydown(event: KeyboardEvent) {
			// A key on a link or a button inside the element is that control's own.
			if (event.target !== event.currentTarget) return;
			if (event.key !== 'Enter' && event.key !== ' ') return;
			event.preventDefault();
			onTap();
		},
	};
}
