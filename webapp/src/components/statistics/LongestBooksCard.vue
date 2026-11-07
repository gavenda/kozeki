<script setup lang="ts">
import { computed, ref } from 'vue';
import ButtonGroup from '@/components/ButtonGroup.vue';
import ChartCard from '@/components/ChartCard.vue';
import Portrait from '@/components/Portrait.vue';
import RankedBars from '@/components/RankedBars.vue';
import SpeechBubble from '@/components/SpeechBubble.vue';
import { longestFinished, type CompletedBook, type LongestKind } from '@/lib/stats';
import { s } from '@/strings';

/**
 * The longest books finished, a tab for paper and one for EPUBs, as the phone's `LongestBooksCard`.
 * Until a tab is picked, the card opens on the first that has any.
 */
const props = defineProps<{ completed: CompletedBook[] }>();
const longest = computed(() => ({
	PHYSICAL: longestFinished(props.completed, 'PHYSICAL'),
	EPUB: longestFinished(props.completed, 'EPUB'),
}));
const picked = ref<LongestKind | null>(null);
const selected = computed<LongestKind>(() => picked.value ?? (longest.value.PHYSICAL.length ? 'PHYSICAL' : 'EPUB'));
</script>

<template>
	<ChartCard :title="s.stats.longestBooks">
		<ButtonGroup
			:model-value="selected"
			:options="[
				{ value: 'PHYSICAL', label: s.stats.longestPhysical, icon: 'autoStories', color: 'leather' },
				{ value: 'EPUB', label: s.stats.longestEpub, icon: 'libraryBooksFill', color: 'violet' },
			]"
			@update:model-value="picked = $event as LongestKind"
		/>
		<RankedBars
			v-if="longest[selected].length"
			:bars="longest[selected].map(({ book, length }) => ({ label: book.title, value: length, valueText: s.stats.lengthPages(length) }))"
		/>
		<div v-else class="flex items-center gap-2">
			<Portrait face="neutral" :width="96" />
			<SpeechBubble>{{ selected === 'PHYSICAL' ? s.stats.longestPhysicalEmpty : s.stats.longestEpubEmpty }}</SpeechBubble>
		</div>
	</ChartCard>
</template>
