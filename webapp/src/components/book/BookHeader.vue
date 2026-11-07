<script setup lang="ts">
import { onKeyStroke } from '@vueuse/core';
import { ref } from 'vue';
import AuthorLinks from '@/components/AuthorLinks.vue';
import BookCover from '@/components/BookCover.vue';
import RatingBar from '@/components/RatingBar.vue';
import { s } from '@/strings';

export interface HeaderBook {
	title: string;
	subtitle?: string;
	authors?: string[];
	authorRefs?: { id: string; name: string }[];
	series?: string;
	seriesNumber?: number;
	coverFile?: string;
	coverUrl?: string;
	rating?: number;
}

/**
 * The top of a book's page, as the phone's `Header`: the cover, which opens full size, then the
 * title, the series as a way to its page, the authors as ways to theirs, and the user's rating
 * when the book is theirs ([rateable]).
 */
defineProps<{ book: HeaderBook; rateable?: boolean }>();
const emit = defineEmits<{ rate: [rating: number | null] }>();
const viewing = ref(false);
onKeyStroke('Escape', () => (viewing.value = false));
</script>

<template>
	<header class="flex gap-4">
		<button class="w-32 shrink-0 cursor-zoom-in self-start" :aria-label="book.title" @click="viewing = true">
			<BookCover :book="book" rounded="rounded-2xl" />
		</button>
		<div class="flex min-w-0 flex-1 flex-col gap-1.5">
			<h1 class="type-headline-small-em break-words">{{ book.title }}</h1>
			<p v-if="book.subtitle" class="type-title-small text-on-surface-variant">{{ book.subtitle }}</p>
			<RouterLink
				v-if="book.series"
				:to="{ name: 'series', params: { name: book.series } }"
				class="self-start type-body-medium text-primary hover:underline"
				:aria-label="s.book.openSeries"
			>
				{{ book.seriesNumber != null ? s.book.seriesWithNumber(book.series, book.seriesNumber) : book.series }}
			</RouterLink>
			<p v-if="book.authors?.length" class="type-title-medium text-on-surface-variant">
				<AuthorLinks :authors="book.authors" :refs="book.authorRefs" />
			</p>
			<template v-if="rateable">
				<div class="mt-1 flex flex-wrap items-center gap-2">
					<RatingBar :rating="book.rating" editable @change="emit('rate', $event)" />
					<button v-if="book.rating != null" class="btn-text h-9" @click="emit('rate', null)">{{ s.rating.clear }}</button>
				</div>
			</template>
		</div>
	</header>
	<Teleport to="body">
		<div
			v-if="viewing"
			class="fixed inset-0 z-50 flex cursor-zoom-out items-center justify-center bg-scrim/92 p-6"
			@click="viewing = false"
		>
			<div class="w-full max-w-[560px]"><BookCover :book="book" rounded="rounded-2xl" /></div>
		</div>
	</Teleport>
</template>
