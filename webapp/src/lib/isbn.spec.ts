import { describe, expect, it } from 'vitest';
import { niceCeiling, ticksFor } from './chart';
import { toIsbn10, toIsbn13 } from './isbn';
import { javaHash, placeholderTone } from './placeholder';

describe('ISBN', () => {
	it('takes ISBN-13s and turns ISBN-10s into them', () => {
		expect(toIsbn13('978-0-306-40615-7')).toBe('9780306406157');
		expect(toIsbn13('0306406152')).toBe('9780306406157');
		expect(toIsbn13('080442957X')).toBe('9780804429573');
		expect(toIsbn13('9780306406158')).toBeNull();
		expect(toIsbn10('9780306406157')).toBe('0306406152');
		expect(toIsbn10('9791234567896')).toBeNull();
	});
});

describe('chart axis', () => {
	it('rounds up to 1, 2 or 5 times a power of ten', () => {
		expect(niceCeiling(0)).toBe(1);
		expect(niceCeiling(7)).toBe(10);
		expect(niceCeiling(130)).toBe(200);
		expect(niceCeiling(41)).toBe(50);
		expect(niceCeiling(1.5)).toBe(2);
	});

	it('leaves out a middle tick that is not a whole count', () => {
		expect(ticksFor(1, true)).toEqual([0, 1]);
		expect(ticksFor(1, false)).toEqual([0, 0.5, 1]);
		expect(ticksFor(10, true)).toEqual([0, 5, 10]);
	});
});

describe('placeholder colour', () => {
	it('hashes a title as Java does, so it gets the phone’s colour', () => {
		// Values Java gives: "hello".hashCode(), and one that overflows into the negatives.
		expect(javaHash('hello')).toBe(99162322);
		expect(javaHash('Aa')).toBe(javaHash('BB'));
		expect(javaHash('polygenelubricants')).toBe(-2147483648);
		// Kotlin's absoluteValue of the smallest Int is still negative, so its remainder picks the last colour.
		expect(placeholderTone('polygenelubricants')).toBe('tertiary');
		expect(placeholderTone('hello')).toBe(['primary', 'secondary', 'tertiary'][99162322 % 3]);
	});
});
