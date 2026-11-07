import type { AccessTokenClaims } from './auth0';

/** Shared Hono generics: Worker bindings plus per-request state. */
export interface AppEnv {
	Bindings: Env;
	Variables: {
		/** Verified claims from the Auth0 access token on this request. */
		claims: AccessTokenClaims;
		/** The Auth0 subject owning this request's data. Never client-supplied. */
		userId: string;
	};
}
