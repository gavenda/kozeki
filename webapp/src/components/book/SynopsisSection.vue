<script setup lang="ts">
import { computed } from 'vue';
import ExpandableText from '@/components/ExpandableText.vue';
import { s } from '@/strings';

export interface SynopsisBook {
	description?: string;
	series?: string;
	seriesNumber?: number;
	publisher?: string;
	publishedDate?: string;
	pageCount?: number;
	language?: string;
	isbn13?: string;
	isbn10?: string;
	categories?: string[];
}

/** What the catalogue says of a book, as the phone's `AboutSection`: the description cut to five lines, then its details. */
const props = defineProps<{ book: SynopsisBook }>();
const rows = computed(() =>
	[
		[
			s.book.detail.series,
			props.book.series &&
				(props.book.seriesNumber != null ? s.book.seriesWithNumber(props.book.series, props.book.seriesNumber) : props.book.series),
		],
		[s.book.detail.publisher, props.book.publisher],
		[s.book.detail.published, props.book.publishedDate],
		[s.book.detail.pages, props.book.pageCount?.toString()],
		[s.book.detail.language, props.book.language?.toUpperCase()],
		[s.book.detail.isbn, props.book.isbn13 ?? props.book.isbn10],
		[s.book.detail.categories, props.book.categories?.length ? props.book.categories.join(', ') : undefined],
	].filter((row): row is [string, string] => !!row[1]),
);
</script>

<template>
	<section class="flex flex-col gap-3">
		<h2 class="section-title">{{ s.book.about }}</h2>
		<ExpandableText v-if="book.description" :text="book.description" :lines="5" />
		<dl v-if="rows.length" class="flex flex-col gap-1">
			<div v-for="[label, value] in rows" :key="label" class="flex type-body-medium">
				<dt class="w-28 shrink-0 text-on-surface-variant">{{ label }}</dt>
				<dd class="min-w-0 break-words">{{ value }}</dd>
			</div>
		</dl>
		<p v-if="!book.description && rows.length === 0" class="type-body-medium text-on-surface-variant">{{ s.book.noDetails }}</p>
	</section>
</template>
