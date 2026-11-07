import { dateOfEpochDay } from './dates';

/** "1h 22m", "24m" or "<1m", as the phone's `formatDuration`. */
export function formatDuration(ms: number): string {
	const total = Math.floor(ms / 60_000);
	const hours = Math.floor(total / 60);
	const minutes = total % 60;
	if (hours > 0 && minutes > 0) return `${hours}h ${minutes}m`;
	if (hours > 0) return `${hours}h`;
	if (total > 0) return `${minutes}m`;
	return ms > 0 ? '<1m' : '0m';
}

export function formatPercent(fraction: number): string {
	return `${Math.round(Math.min(Math.max(fraction, 0), 1) * 100)}%`;
}

/** An epoch day in the medium date style, such as "8 Nov 2026". */
export function formatDay(day: number): string {
	return dateOfEpochDay(day).toLocaleDateString(undefined, { dateStyle: 'medium' });
}

export function formatDate(millis: number): string {
	return new Date(millis).toLocaleDateString(undefined, { dateStyle: 'medium' });
}

export function formatTime(millis: number): string {
	return new Date(millis).toLocaleTimeString(undefined, { timeStyle: 'short' });
}

export function formatDateTime(millis: number): string {
	return new Date(millis).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

/** "March 2026", as the phone's `formatMonth`. */
export function formatMonth(year: number, month: number): string {
	return new Date(year, month, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
}

/** How long ago [millis] was, such as "Now", "1 minute ago" or "2 days ago", in the phone's steps. */
export function formatTimeAgo(millis: number, now: number = Date.now()): string {
	const minutes = Math.floor(Math.max(0, now - millis) / 60_000);
	const hours = Math.floor(minutes / 60);
	const days = Math.floor(hours / 24);
	const format = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
	if (minutes < 1) {
		const word = format.format(0, 'second');
		return word.charAt(0).toUpperCase() + word.slice(1);
	}
	const [amount, unit]: [number, Intl.RelativeTimeFormatUnit] =
		minutes < 60
			? [minutes, 'minute']
			: hours < 24
				? [hours, 'hour']
				: days < 7
					? [days, 'day']
					: days < 30
						? [Math.floor(days / 7), 'week']
						: days < 365
							? [Math.floor(days / 30), 'month']
							: [Math.floor(days / 365), 'year'];
	return new Intl.RelativeTimeFormat(undefined, { numeric: 'always' }).format(-amount, unit);
}

/** The currency's minor digits, two when the browser does not know it. */
function fractionDigits(currency: string): number {
	try {
		return new Intl.NumberFormat(undefined, { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2;
	} catch {
		return 2;
	}
}

/** A price in minor units, in its currency's own format; the code alone when the browser does not know it. */
export function formatPrice(minor: number, currency: string | undefined): string {
	const code = currency || 'USD';
	try {
		return new Intl.NumberFormat(undefined, { style: 'currency', currency: code }).format(minor / 10 ** fractionDigits(code));
	} catch {
		return `${(minor / 100).toFixed(2)} ${code}`;
	}
}

/** Minor units of a typed price, split on the decimal point rather than multiplied, so 45.99 is exactly 4599. */
export function parsePrice(text: string, currency: string): number | null {
	const cleaned = text.trim().replace(/,/g, '');
	if (!/^\d+(\.\d+)?$/.test(cleaned)) return null;
	const digits = fractionDigits(currency);
	const [whole, fraction = ''] = cleaned.split('.');
	return Number(whole) * 10 ** digits + Number((fraction + '0'.repeat(digits)).slice(0, digits) || '0');
}

export function priceToInput(minor: number, currency: string | undefined): string {
	const digits = fractionDigits(currency || 'USD');
	return digits === 0 ? String(minor) : (minor / 10 ** digits).toFixed(digits);
}

/** Whether a currency code is one the browser can format, as the phone checks it against `Currency`. */
export function isCurrency(code: string): boolean {
	if (!/^[A-Z]{3}$/.test(code)) return false;
	try {
		new Intl.NumberFormat(undefined, { style: 'currency', currency: code });
		return true;
	} catch {
		return false;
	}
}

/** Ui's birthday, 23 April, which is also World Book Day. */
export function isUiBirthday(day: number): boolean {
	const date = dateOfEpochDay(day);
	return date.getMonth() === 3 && date.getDate() === 23;
}
