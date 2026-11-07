import { dateOfEpochDay } from './dates';

export function formatDuration(ms: number): string {
	const minutes = Math.round(ms / 60_000);
	if (minutes < 60) return `${minutes} min`;
	const hours = Math.floor(minutes / 60);
	const rest = minutes % 60;
	return rest ? `${hours} h ${rest} min` : `${hours} h`;
}

export function formatPercent(fraction: number): string {
	return `${Math.round(Math.min(Math.max(fraction, 0), 1) * 100)}%`;
}

export function formatDay(day: number): string {
	return dateOfEpochDay(day).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatDateTime(millis: number): string {
	return new Date(millis).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

/** A price in minor units, in its currency's own format; the code alone when the browser does not know it. */
export function formatPrice(minor: number, currency: string | undefined): string {
	const code = currency ?? 'USD';
	try {
		const digits = new Intl.NumberFormat(undefined, { style: 'currency', currency: code }).resolvedOptions().maximumFractionDigits ?? 2;
		return new Intl.NumberFormat(undefined, { style: 'currency', currency: code }).format(minor / 10 ** digits);
	} catch {
		return `${(minor / 100).toFixed(2)} ${code}`;
	}
}

/** Minor units of a typed price, split on the decimal point rather than multiplied, so 45.99 is exactly 4599. */
export function parsePrice(text: string, currency: string): number | null {
	const cleaned = text.trim().replace(/,/g, '');
	if (!/^\d+(\.\d+)?$/.test(cleaned)) return null;
	let digits = 2;
	try {
		digits = new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2;
	} catch {
		// Two, as most currencies have.
	}
	const [whole, fraction = ''] = cleaned.split('.');
	return Number(whole) * 10 ** digits + Number((fraction + '0'.repeat(digits)).slice(0, digits) || '0');
}

export function priceToInput(minor: number, currency: string | undefined): string {
	let digits = 2;
	try {
		digits =
			new Intl.NumberFormat(undefined, { style: 'currency', currency: currency ?? 'USD' }).resolvedOptions().maximumFractionDigits ?? 2;
	} catch {
		// Two.
	}
	return digits === 0 ? String(minor) : (minor / 10 ** digits).toFixed(digits);
}
