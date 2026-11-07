import { ref, watch, type Ref } from 'vue';

/**
 * A value kept in this browser's storage, as the phone keeps its view settings per device: the
 * library's filter, sort and layout, which groups are folded away. A value that cannot be read
 * back, or that [valid] refuses, is the default instead.
 */
export function persisted<T>(key: string, fallback: T, valid: (value: unknown) => value is T = (_): _ is T => true): Ref<T> {
	let initial = fallback;
	try {
		const raw = localStorage.getItem(`kozeki.${key}`);
		if (raw != null) {
			const value: unknown = JSON.parse(raw);
			if (valid(value)) initial = value;
		}
	} catch {
		// The default, then.
	}
	const state = ref(initial) as Ref<T>;
	watch(
		state,
		(value) => {
			try {
				localStorage.setItem(`kozeki.${key}`, JSON.stringify(value));
			} catch {
				// A remembered view is a convenience.
			}
		},
		{ deep: true },
	);
	return state;
}

/** A validator for one of [options]. */
export const oneOf =
	<T extends string>(options: readonly T[]) =>
	(value: unknown): value is T =>
		typeof value === 'string' && (options as readonly string[]).includes(value);
