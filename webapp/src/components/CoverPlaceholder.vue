<script setup lang="ts">
import { useElementSize } from '@vueuse/core';
import { computed, ref } from 'vue';
import { bindingOf, FRAME_INSET, placeholderTone, SPINE, TITLE_GAP } from '@/lib/placeholder';

/** A book's cover when it has none: bound, with its title on the board, as the phone draws it. */
const props = withDefaults(defineProps<{ title: string; showTitle?: boolean }>(), { showTitle: true });

const box = ref<HTMLElement>();
const { width, height } = useElementSize(box);
const binding = computed(() => (width.value > 0 ? bindingOf(width.value, height.value) : null));

const TONES = {
	primary: 'bg-primary-container text-on-primary-container',
	secondary: 'bg-secondary-container text-on-secondary-container',
	tertiary: 'bg-tertiary-container text-on-tertiary-container',
};
const tone = computed(() => TONES[placeholderTone(props.title)]);
// Shares of the width, as the spine and the frame are, so the title keeps inside the frame.
const titleStart = `${(SPINE + FRAME_INSET + TITLE_GAP) * 100}%`;
const titleEnd = `${(FRAME_INSET + TITLE_GAP) * 100}%`;
</script>

<template>
	<div ref="box" class="relative size-full" :class="tone">
		<svg v-if="binding" class="absolute inset-0 size-full" aria-hidden="true">
			<rect :width="binding.spine" :height="height" class="fill-scrim" fill-opacity="0.1" />
			<line
				:x1="binding.spine"
				:x2="binding.spine"
				y1="0"
				:y2="height"
				class="stroke-scrim"
				stroke-opacity="0.22"
				:stroke-width="binding.hairline"
			/>
			<g fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round">
				<rect
					v-if="!binding.scrolls"
					:x="binding.frame.left"
					:y="binding.frame.top"
					:width="binding.frame.right - binding.frame.left"
					:height="binding.frame.bottom - binding.frame.top"
					:rx="binding.frameCorner"
					:stroke-width="binding.line"
				/>
				<template v-else>
					<path :d="binding.sides" :stroke-width="binding.line" />
					<path :d="binding.scrolls" :stroke-width="binding.scrollStroke" />
				</template>
			</g>
		</svg>
		<div class="absolute inset-y-0 flex items-center justify-center text-center" :style="{ left: titleStart, right: titleEnd }">
			<span v-if="showTitle" class="line-clamp-4 type-label-medium-em break-words">{{ title }}</span>
			<span v-else class="type-title-medium-em">{{ title.slice(0, 1).toUpperCase() }}</span>
		</div>
	</div>
</template>
