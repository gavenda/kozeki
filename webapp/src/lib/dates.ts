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

/** The day weeks start on in the browser's locale, 0 for Sunday, as the phone takes it from its own; Monday when it does not say. */
export function firstDayOfWeek(): number {
	try {
		const locale = new Intl.Locale(navigator.language) as Intl.Locale & {
			weekInfo?: { firstDay: number };
			getWeekInfo?: () => { firstDay: number };
		};
		return ((locale.getWeekInfo?.() ?? locale.weekInfo)?.firstDay ?? 1) % 7;
	} catch {
		return 1;
	}
}

/** The epoch day of a "yyyy-mm-dd" date, or null when it is not one. */
export function epochDayOfIso(text: string): number | null {
	const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text);
	return match ? epochDayOfDate(Number(match[1]), Number(match[2]) - 1, Number(match[3])) : null;
}

export function isoOfEpochDay(day: number): string {
	return new Date(day * DAY_MS).toISOString().slice(0, 10);
}
