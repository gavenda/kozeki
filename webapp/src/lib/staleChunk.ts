const KEY = 'kozeki.chunk-reload';

/** How long after one reload another is refused, so a chunk that is really gone cannot loop the page. */
export const RELOAD_WINDOW_MS = 10_000;

type Store = Pick<Storage, 'getItem' | 'setItem'>;

/**
 * Whether a route chunk that failed to load should be answered with a page reload.
 *
 * Every deploy replaces the hashed files under `/assets/`, and Static Assets answers a
 * missing one with the SPA shell (200, `text/html`) rather than a 404. A tab opened
 * before the deploy that then navigates to a route it has not visited yet asks for a
 * chunk that no longer exists and receives HTML in its place, which the browser rejects
 * as a module. Reloading picks up the new `index.html` and its current chunk names.
 *
 * It says yes once, and no again inside [RELOAD_WINDOW_MS]: if the fresh page cannot load
 * the chunk either, reloading again would only loop. Without usable storage there is no
 * way to tell a first failure from a repeat, so it declines rather than risk the loop.
 */
export function shouldReloadForStaleChunk(now: number = Date.now(), store: Store = sessionStorage): boolean {
	try {
		const last = Number(store.getItem(KEY));
		if (last && now - last < RELOAD_WINDOW_MS) return false;

		store.setItem(KEY, String(now));
		return true;
	} catch {
		return false;
	}
}
