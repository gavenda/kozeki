import { createAuth0 } from '@auth0/auth0-vue';
import { watch } from 'vue';

const domain = import.meta.env.VITE_AUTH0_DOMAIN;
const clientId = import.meta.env.VITE_AUTH0_CLIENT_ID;
const audience = import.meta.env.VITE_AUTH0_AUDIENCE;

export const isConfigured = Boolean(domain && clientId && audience);

/**
 * The Auth0 plugin, which is also the client. Install it after the router: on its return from
 * Auth0 it pushes to the route the user first asked for.
 *
 * Tokens live in localStorage with refresh-token rotation, since browsers block the third-party
 * cookies silent renewal relied on. The application's rotation has a leeway above zero, so a retry
 * after a lost response does not look like a stolen token (see Yuuka's notes on the same setup).
 */
export const auth0 = createAuth0({
	domain,
	clientId,
	authorizationParams: { audience, redirect_uri: window.location.origin },
	cacheLocation: 'localstorage',
	useRefreshTokens: true,
});

/** Resolves once the plugin has restored any existing session. Guards wait on it. */
export function whenAuthReady(): Promise<void> {
	if (!auth0.isLoading.value) return Promise.resolve();
	return new Promise((resolve) => {
		const stop = watch(auth0.isLoading, (loading) => {
			if (loading) return;
			stop();
			resolve();
		});
	});
}
