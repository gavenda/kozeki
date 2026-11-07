import { reactive } from 'vue';
import { apiFetch } from './http';

/**
 * Cover pictures the account keeps, which need the access token and so cannot be a plain `<img src>`.
 * Each is fetched once and held as an object URL; a name never changes what it shows, so it is never
 * fetched again.
 */
const urls = reactive(new Map<string, string | null>());
const pending = new Map<string, Promise<void>>();

/**
 * Whether the account keeps this book's cover picture: one the user chose, one from its EPUB, or
 * one with nowhere else to come from. A catalogue picture is not uploaded; it is shown from its
 * `coverUrl`. The phone decides the same in `SyncDao.coverRefs`.
 */
export function coverInAccount(book: { coverFile?: string; coverUrl?: string; customCover?: boolean; fileHash?: string }): boolean {
	return !!book.coverFile && (!!book.customCover || !!book.fileHash || !book.coverUrl);
}

export function coverUrl(name: string | undefined): string | null | undefined {
	if (!name) return null;
	if (!urls.has(name) && !pending.has(name)) {
		pending.set(
			name,
			apiFetch(`/covers/${encodeURIComponent(name)}`)
				.then(async (response) => {
					urls.set(name, URL.createObjectURL(await response.blob()));
				})
				.catch(() => {
					urls.set(name, null);
				})
				.finally(() => pending.delete(name)),
		);
	}
	// Undefined while it loads, null when the account does not have it.
	return urls.get(name);
}

const MAX_EDGE = 1200;

/** Scales a picked picture to the phone's cover size, as a JPEG, and uploads it. Returns the file name to give the book. */
export async function uploadCover(bookId: string, file: File): Promise<string> {
	const bitmap = await createImageBitmap(file);
	const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height));
	const canvas = document.createElement('canvas');
	canvas.width = Math.round(bitmap.width * scale);
	canvas.height = Math.round(bitmap.height * scale);
	canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
	const blob = await new Promise<Blob>((resolve, reject) =>
		canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('No picture'))), 'image/jpeg', 0.9),
	);

	// Named as the phone names its covers, so every saved cover is a new file.
	const name = `${bookId}-${Date.now()}.jpg`;
	await apiFetch(`/covers/${name}`, { method: 'PUT', headers: { 'content-type': 'image/jpeg' }, body: blob });
	urls.set(name, URL.createObjectURL(blob));
	return name;
}
