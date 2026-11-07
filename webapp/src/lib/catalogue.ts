import type { Found } from './library';
import { hardcoverSearch, hardcoverSignedIn } from './hardcover';

/**
 * Looking books up: Hardcover when the browser is signed in to it, Google Books otherwise, which
 * needs no account. Either way the answer is what [addFound] takes.
 */
export async function search(query: string): Promise<Found[]> {
	if (!query.trim()) return [];
	return hardcoverSignedIn() ? hardcoverSearch(query) : googleBooksSearch(query);
}

interface Volume {
	id: string;
	volumeInfo: {
		title?: string;
		subtitle?: string;
		authors?: string[];
		description?: string;
		publisher?: string;
		publishedDate?: string;
		pageCount?: number;
		categories?: string[];
		industryIdentifiers?: { type: string; identifier: string }[];
		imageLinks?: { thumbnail?: string };
		infoLink?: string;
	};
}

async function googleBooksSearch(query: string): Promise<Found[]> {
	const url = `https://www.googleapis.com/books/v1/volumes?q=${encodeURIComponent(query)}&maxResults=20&printType=books`;
	const response = await fetch(url);
	if (!response.ok) throw new Error(`Google Books answered ${response.status}.`);
	const body = (await response.json()) as { items?: Volume[] };
	return (body.items ?? []).flatMap(({ id, volumeInfo: info }) =>
		info.title
			? [
					{
						source: 'GOOGLE_BOOKS' as const,
						sourceId: id,
						title: info.title,
						subtitle: info.subtitle,
						authors: info.authors ?? [],
						description: info.description,
						publisher: info.publisher,
						publishedDate: info.publishedDate,
						pageCount: info.pageCount || undefined,
						isbn13: info.industryIdentifiers?.find((entry) => entry.type === 'ISBN_13')?.identifier,
						categories: info.categories,
						coverUrl: info.imageLinks?.thumbnail?.replace(/^http:/, 'https:'),
						infoUrl: info.infoLink,
					},
				]
			: [],
	);
}
