<script setup lang="ts">
import { useElementSize } from '@vueuse/core';
import { computed, ref, watch } from 'vue';
import { niceCeiling, textWidth, ticksFor } from '@/lib/chart';

export interface ChartBar {
	/** Shown under the column; empty to skip it where columns are dense. */
	label: string;
	value: number;
	/** The value as the reader should see it, such as "24m". */
	valueText: string;
}

/**
 * A single-series column chart, as the phone's `ColumnChart`. Every column is the same colour,
 * since length already carries the value. Only one value is written out: the selected column's, or
 * the tallest when none is selected. Clicking anywhere in a column's slot selects it.
 */
const props = withDefaults(
	defineProps<{
		bars: ChartBar[];
		description: string;
		axisText: (value: number) => string;
		reference?: { value: number; label: string };
		wholeNumbers?: boolean;
		height?: number;
	}>(),
	{ height: 180 },
);

const box = ref<HTMLElement>();
const { width } = useElementSize(box);
const selected = ref<number | null>(null);
watch(
	() => props.bars,
	() => (selected.value = null),
);

const FONT = '500 11px "Google Sans Flex Variable", sans-serif';
const VALUE_FONT = '500 12px "Google Sans Flex Variable", sans-serif';
const LABEL_HEIGHT = 16;

const layout = computed(() => {
	const bars = props.bars;
	const top = niceCeiling(Math.max(0, ...bars.map((bar) => bar.value), props.reference?.value ?? 0));
	const ticks = ticksFor(top, !!props.wholeNumbers).map((value) => ({ value, text: props.axisText(value) }));
	const gutter = Math.max(...ticks.map((tick) => textWidth(tick.text, FONT))) + 8;
	const hasLabels = bars.some((bar) => bar.label);
	const labelBand = (hasLabels ? LABEL_HEIGHT : 0) + 6;
	const headroom = LABEL_HEIGHT + 6;
	const plot = { left: gutter, top: headroom, right: width.value, bottom: props.height - labelBand };
	const slot = bars.length ? (plot.right - plot.left) / bars.length : 0;
	// Capped so wide charts get air between columns instead of fat blocks.
	const barWidth = Math.max(2, Math.min(24, slot - 2));
	const y = (value: number) => plot.bottom - (top > 0 ? value / top : 0) * (plot.bottom - plot.top);
	const highlighted =
		selected.value ?? (bars.length ? bars.reduce((best, bar, index) => (bar.value > bars[best].value ? index : best), 0) : null);
	const shownValue = highlighted != null && (selected.value != null || bars[highlighted].value > 0) ? highlighted : null;
	return {
		ticks: ticks.map((tick) => ({ ...tick, y: y(tick.value) })),
		plot,
		gutter,
		columns: bars.map((bar, index) => {
			const centre = plot.left + slot * (index + 0.5);
			const barTop = y(bar.value);
			const radius = Math.min(4, barWidth / 2, plot.bottom - barTop);
			const left = centre - barWidth / 2;
			const right = centre + barWidth / 2;
			// Rounded where the data ends, square where it meets the baseline.
			const path =
				bar.value > 0
					? `M${left} ${plot.bottom}V${barTop + radius}Q${left} ${barTop} ${left + radius} ${barTop}H${right - radius}Q${right} ${barTop} ${right} ${barTop + radius}V${plot.bottom}Z`
					: '';
			return { centre, path, label: bar.label, dimmed: selected.value != null && selected.value !== index };
		}),
		reference: props.reference && props.reference.value > 0 ? { y: y(props.reference.value), label: props.reference.label } : null,
		value:
			shownValue != null
				? (() => {
						const text = props.bars[shownValue].valueText;
						const textW = textWidth(text, VALUE_FONT);
						const centre = plot.left + slot * (shownValue + 0.5);
						const x = Math.min(Math.max(centre - textW / 2, plot.left), Math.max(plot.right - textW, plot.left));
						return { text, x, y: y(props.bars[shownValue].value) - 4 };
					})()
				: null,
		slot,
	};
});

function pick(event: MouseEvent) {
	const { gutter, slot } = layout.value;
	const x = event.offsetX;
	if (!props.bars.length || x < gutter || slot <= 0) return;
	const index = Math.min(Math.max(Math.floor((x - gutter) / slot), 0), props.bars.length - 1);
	selected.value = selected.value === index ? null : index;
}
</script>

<template>
	<div ref="box" class="w-full" role="img" :aria-label="description">
		<svg v-if="width" :width="width" :height="height" class="block cursor-pointer select-none" @click="pick">
			<g v-for="tick in layout.ticks" :key="tick.value">
				<line :x1="layout.plot.left" :x2="layout.plot.right" :y1="tick.y" :y2="tick.y" class="stroke-outline-variant" stroke-width="1" />
				<text
					:x="layout.gutter - 8"
					:y="tick.y"
					text-anchor="end"
					dominant-baseline="central"
					class="fill-on-surface-variant type-label-small"
				>
					{{ tick.text }}
				</text>
			</g>
			<g v-for="(column, index) in layout.columns" :key="index">
				<path v-if="column.path" :d="column.path" class="fill-tertiary transition-opacity" :fill-opacity="column.dimmed ? 0.45 : 1" />
				<text
					v-if="column.label"
					:x="column.centre"
					:y="layout.plot.bottom + 6"
					text-anchor="middle"
					dominant-baseline="hanging"
					class="fill-on-surface-variant type-label-small"
				>
					{{ column.label }}
				</text>
			</g>
			<g v-if="layout.reference">
				<line
					:x1="layout.plot.left"
					:x2="layout.plot.right"
					:y1="layout.reference.y"
					:y2="layout.reference.y"
					class="stroke-on-surface-variant"
					stroke-width="1"
				/>
				<text :x="layout.plot.right" :y="layout.reference.y - 2" text-anchor="end" class="fill-on-surface-variant type-label-small">
					{{ layout.reference.label }}
				</text>
			</g>
			<text v-if="layout.value" :x="layout.value.x" :y="layout.value.y" class="fill-on-surface type-label-medium">
				{{ layout.value.text }}
			</text>
		</svg>
	</div>
</template>
