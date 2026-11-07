<script setup lang="ts">
/**
 * The halo that is the app's mark: two rings tied together at the four points of the compass,
 * around a dial with its hands at twelve and three, as the phone's `HaloMark`. Its colour is its
 * own whatever the theme. With [loading] it is the app's loading indicator, run like clockwork:
 * the rings drift one way while the hands go round the other, the second five times as fast.
 */
withDefaults(defineProps<{ size?: number; dial?: boolean; loading?: boolean }>(), { size: 48, dial: true });

// Measured off the artwork, as fractions of the halo's radius. A ring's is to the middle of its line.
const R = 50;
const OUTER = 0.954 * R;
const OUTER_STROKE = 0.092 * R;
const INNER = 0.805 * R;
const INNER_STROKE = 0.038 * R;
const DIAL = 0.546 * R;
const DIAL_STROKE = 0.05 * R;
const HUB = 0.103 * R;
const HUB_STROKE = 0.053 * R;
const SPOKE = 0.061 * R;
</script>

<template>
	<svg
		:width="size"
		:height="size"
		viewBox="0 0 100 100"
		class="shrink-0 text-halo"
		:role="loading ? 'progressbar' : undefined"
		:aria-hidden="loading && $attrs['aria-label'] ? undefined : 'true'"
	>
		<g fill="none" stroke="currentColor">
			<g :class="{ 'halo-rings': loading }">
				<circle cx="50" cy="50" :r="OUTER" :stroke-width="OUTER_STROKE" />
				<circle cx="50" cy="50" :r="INNER" :stroke-width="INNER_STROKE" />
				<line
					v-for="turn in 4"
					:key="turn"
					x1="50"
					:y1="50 - INNER"
					x2="50"
					:y2="50 - OUTER"
					:stroke-width="SPOKE"
					:transform="`rotate(${90 * turn} 50 50)`"
				/>
			</g>
			<template v-if="dial">
				<circle cx="50" cy="50" :r="DIAL" :stroke-width="DIAL_STROKE" />
				<circle cx="50" cy="50" :r="HUB" :stroke-width="HUB_STROKE" />
				<line x1="50" :y1="50 - HUB" x2="50" :y2="50 - DIAL" :stroke-width="SPOKE" :class="{ 'halo-first': loading }" />
				<line
					x1="50"
					:y1="50 - HUB"
					x2="50"
					:y2="50 - DIAL"
					:stroke-width="SPOKE"
					transform="rotate(90 50 50)"
					:class="{ 'halo-second': loading }"
				/>
			</template>
		</g>
	</svg>
</template>

<style scoped>
.halo-rings,
.halo-first,
.halo-second {
	transform-origin: 50px 50px;
	animation-timing-function: linear;
	animation-iteration-count: infinite;
	animation-duration: 5.6s;
}
.halo-rings {
	animation-name: halo-back;
}
.halo-first {
	animation-name: halo-forward;
}
.halo-second {
	animation-name: halo-forward-fast;
}
@keyframes halo-back {
	to {
		transform: rotate(-360deg);
	}
}
@keyframes halo-forward {
	to {
		transform: rotate(360deg);
	}
}
@keyframes halo-forward-fast {
	from {
		transform: rotate(90deg);
	}
	to {
		transform: rotate(1890deg);
	}
}
@media (prefers-reduced-motion: reduce) {
	.halo-rings,
	.halo-first,
	.halo-second {
		animation-duration: 30s;
	}
}
</style>
