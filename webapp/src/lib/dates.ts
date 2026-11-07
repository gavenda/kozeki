const DAY_MS = 86_400_000;

/** The epoch day of a moment in this browser's zone, as the phone stores days in its own. */
export function epochDayOf(millis: number): number {
	const date = new Date(millis);
	return Math.floor((millis - date.getTimezoneOffset() * 60_000) / DAY_MS);
}

export const today = (): number => epochDayOf(Date.now());

/** The local calendar date an epoch day stands for. */
export function dateOfEpochDay(day: number): Date {
	const utc = new Date(day * DAY_MS);
	return new Date(utc.getUTCFullYear(), utc.getUTCMonth(), utc.getUTCDate());
}

export function epochDayOfDate(year: number, month: number, day: number): number {
	return Math.floor(Date.UTC(year, month, day) / DAY_MS);
}

export function yearOfEpochDay(day: number): number {
	return new Date(day * DAY_MS).getUTCFullYear();
}

/** 0 for January. */
export function monthOfEpochDay(day: number): number {
	return new Date(day * DAY_MS).getUTCMonth();
}
