<script setup lang="ts">
import { computed } from 'vue';
import { ownership, overallProgression, STATE_GLYPH, stateOf } from '@/lib/bookUi';
import type { Book } from '@/types';
import { s } from '@/strings';
import AppIcon from './AppIcon.vue';
import ReadingRing from './ReadingRing.vue';

/**
 * What the library grid lays over a cover, as the phone does: bottom-left, the heart of a favorite
 * over the ownership badge; top-right, how far along a book being read is, or its state's glyph
 * for one finished, paused or dropped. A planned book has none.
 */
const props = defineProps<{ book: Book }>();
const owned = computed(() => ownership(props.book));
const state = computed(() => stateOf(props.book));
</script>

<template>
	<div class="pointer-events-none absolute start-1.5 bottom-1.5 flex flex-col gap-1">
		<span v-if="book.isFavorite" class="rounded-full bg-surface-container-highest p-[5px] text-on-surface" :title="s.favorite.remove">
			<AppIcon name="favoriteFill" :size="14" />
		</span>
		<span class="rounded-full bg-surface-container-highest p-[5px] text-on-surface" :title="owned.label">
			<AppIcon :name="owned.icon" :size="14" />
		</span>
	</div>
	<span
		v-if="state !== 'PLANNED'"
		class="pointer-events-none absolute end-1.5 top-1.5 rounded-full bg-surface-container-highest/80 p-[3px] text-on-surface"
		:title="s.state[state]"
	>
		<ReadingRing v-if="state === 'READING'" :progress="overallProgression(book)" />
		<AppIcon v-else :name="STATE_GLYPH[state]" :size="32" />
	</span>
</template>
