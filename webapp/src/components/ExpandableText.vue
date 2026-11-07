<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue';
import { useResizeObserver } from '@vueuse/core';
import { s } from '@/strings';
import AppIcon from './AppIcon.vue';

/**
 * Text cut to [lines] until clicked, with a chevron centred under it that turns over as it opens,
 * as the phone's `ExpandableText`. The chevron shows only when there is more to see.
 */
const props = withDefaults(defineProps<{ text: string; lines?: number }>(), { lines: 5 });
const expanded = ref(false);
const overflows = ref(false);
const body = ref<HTMLElement>();

function measure() {
	if (!expanded.value && body.value) overflows.value = body.value.scrollHeight > body.value.clientHeight + 1;
}
onMounted(measure);
useResizeObserver(body, measure);
watch(
	() => props.text,
	() => {
		expanded.value = false;
		void nextTick(measure);
	},
);
</script>

<template>
	<div class="flex flex-col items-center">
		<p
			ref="body"
			class="w-full type-body-medium whitespace-pre-line"
			:class="{ 'cursor-pointer': overflows || expanded }"
			:style="expanded ? {} : { display: '-webkit-box', WebkitLineClamp: lines, WebkitBoxOrient: 'vertical', overflow: 'hidden' }"
			@click="(overflows || expanded) && (expanded = !expanded)"
		>
			{{ text }}
		</p>
		<button
			v-if="overflows || expanded"
			type="button"
			class="icon-btn size-8"
			:aria-label="expanded ? s.action.showLess : s.action.showMore"
			:aria-expanded="expanded"
			@click="expanded = !expanded"
		>
			<AppIcon name="keyboardArrowUp" :size="20" class="transition-transform" :class="{ 'rotate-180': !expanded }" />
		</button>
	</div>
</template>
