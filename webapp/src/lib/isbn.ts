/** ISBN validation, as the phone's `Isbn.kt`. Results are bare digits (plus a trailing X for ISBN-10). */

function checkDigit13(firstTwelve: string): string {
	const sum = [...firstTwelve].reduce((total, c, index) => total + Number(c) * (index % 2 === 0 ? 1 : 3), 0);
	return String((10 - (sum % 10)) % 10);
}

export function isValid13(isbn: string): boolean {
	return /^97[89]\d{10}$/.test(isbn) && checkDigit13(isbn.slice(0, 12)) === isbn[12];
}

export function isValid10(isbn: string): boolean {
	if (!/^\d{9}[\dX]$/.test(isbn)) return false;
	const sum = [...isbn].reduce((total, c, index) => total + (10 - index) * (c === 'X' ? 10 : Number(c)), 0);
	return sum % 11 === 0;
}

/** A valid ISBN-10 or ISBN-13 as an ISBN-13, or null. */
export function toIsbn13(isbn: string): string | null {
	const clean = isbn.replace(/[^\dXx]/g, '').toUpperCase();
	if (isValid13(clean)) return clean;
	if (isValid10(clean)) {
		const body = '978' + clean.slice(0, 9);
		return body + checkDigit13(body);
	}
	return null;
}

/** The ISBN-10 form of a 978-prefixed ISBN-13, or null when there is none. */
export function toIsbn10(isbn13: string): string | null {
	if (!isValid13(isbn13) || !isbn13.startsWith('978')) return null;
	const body = isbn13.slice(3, 12);
	const sum = [...body].reduce((total, c, index) => total + (10 - index) * Number(c), 0);
	const check = (11 - (sum % 11)) % 11;
	return body + (check === 10 ? 'X' : String(check));
}
