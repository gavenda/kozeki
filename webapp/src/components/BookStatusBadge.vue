<script setup lang="ts">
import { computed } from 'vue';
import { overallProgression, ownership, stateOf } from '@/lib/bookUi';
import type { Book } from '@/types';
import AppIcon from './AppIcon.vue';
import ReadingRing from './ReadingRing.vue';

/**
 * Marks a book the user has wherever it is listed, as the phone's `OwnedBadge`: how far along one
 * being read is, or else how it is owned (on the wishlist, bought, or an EPUB).
 */
const props = defineProps<{ book: Book }>();
const owned = computed(() => ownership(props.book));
</script>

<template>
	<ReadingRing v-if="stateOf(book) === 'READING'" :progress="overallProgression(book)" />
	<span
		v-else
		class="flex size-8 shrink-0 items-center justify-center rounded-full bg-secondary-container text-on-secondary-container"
		:title="owned.label"
	>
		<AppIcon :name="owned.icon" :size="18" />
		<span class="sr-only">{{ owned.label }}</span>
	</span>
</template>
