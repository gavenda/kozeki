# Kozeki on the web

The book side of Kozeki's library in a browser, and the API the Android app syncs through. One Cloudflare Worker serves both: the Vue app in `dist/` as static assets, and `/api/*` from `server/`. Data is in D1, cover pictures in R2. Sign-in is Auth0. Comics stay on the phone.

## Setup

```sh
bun install
cp .env.example .env   # public Auth0 values; the Hardcover client ID once it is registered
bun run db:migrate:local
bun run dev            # Worker on :8788, Vite on :5173
```

## Deploy

```sh
bun run db:migrate     # when migrations/ changed
bun run deploy
```

The Worker answers on `kozeki.gavenda.dev` (`routes` in `wrangler.jsonc`).

## Resources

| What                      | Name                                                                                                    |
| ------------------------- | ------------------------------------------------------------------------------------------------------- |
| D1 database               | `kozeki`                                                                                                |
| R2 bucket                 | `kozeki-covers`                                                                                         |
| KV namespace (Auth0 keys) | `kozeki-cache`                                                                                          |
| Auth0 API                 | `Kozeki API`, `https://kozeki.gavenda.dev/api`                                                          |
| Auth0 applications        | `Kozeki` (SPA, this app) and `Kozeki` (Native, the Android app)                                         |
| Hardcover OAuth app       | To register: redirect URIs `https://kozeki.gavenda.dev/hardcover` and `http://localhost:5173/hardcover` |

## API

| Route                                              | Does                                                       |
| -------------------------------------------------- | ---------------------------------------------------------- |
| `POST /api/sync`                                   | Pushes changed rows, returns what changed after the cursor |
| `POST /api/covers/missing`                         | Which of these cover files the server lacks                |
| `PUT /api/covers/:name`                            | Stores a cover                                             |
| `GET /api/covers/:name`                            | Returns a cover                                            |
| `POST /api/hardcover/token`, `/revoke`, `/graphql` | Passes the web app's Hardcover calls through               |
| `GET /api/health`                                  | Unauthenticated health check                               |
