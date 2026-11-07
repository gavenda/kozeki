<script setup lang="ts">
import { computed } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import AuthorLinks from '@/components/AuthorLinks.vue';
import BookCover from '@/components/BookCover.vue';
import ReadingRing from '@/components/ReadingRing.vue';
import { overallProgression, ownership, STATE_ICON, stateOf } from '@/lib/bookUi';
import { formatPercent } from '@/lib/format';
import { inLibrary } from '@/lib/library';
import { press } from '@/lib/press';
import { s } from '@/strings';
import type { Book } from '@/types';
import SortDetailLine, { type SortDetail } from './SortDetailLine.vue';

/** A book in the list, as the phone's `LibraryBookRow`: a small cover, the title, the authors, its series and a line of its state. */
const props = defineProps<{ book: Book; detail: SortDetail | null; selected: boolean; selecting: boolean }>();
const emit = defineEmits<{ open: []; toggle: [] }>();
// Made once, so a long press is still known when the click that ends it arrives.
const handlers = press(
	() => (props.selecting ? emit('toggle') : emit('open')),
	() => emit('toggle'),
);
const owned = computed(() => ownership(props.book));
const state = computed(() => stateOf(props.book));
// A book that cannot be read here is told by how it is owned; one that can, by where the reader is.
const status = computed(() =>
	!inLibrary(props.book)
		? owned.value.label
		: state.value === 'READING'
			? formatPercent(overallProgression(props.book))
			: s.state[state.value],
);
</script>

<template>
	<div
		role="button"
		tabindex="0"
		:aria-pressed="selecting ? selected : undefined"
		class="flex cursor-pointer items-center gap-4 rounded-xl p-2 transition-colors outline-none select-none focus-visible:ring-2 focus-visible:ring-primary"
		:class="selected ? 'bg-secondary-container text-on-secondary-container' : 'hover:bg-on-surface/5'"
		v-on="handlers"
	>
		<div class="relative w-14 shrink-0">
			<BookCover :book="book" :show-title="false" rounded="rounded-lg" />
			<span v-if="selected" class="absolute inset-0 m-auto size-fit rounded-full bg-secondary p-[3px] text-on-secondary">
				<AppIcon name="check" :size="18" />
			</span>
		</div>
		<div class="flex min-w-0 flex-1 flex-col gap-0.5">
			<p class="line-clamp-2 type-title-medium">{{ book.title }}</p>
			<SortDetailLine
				v-if="detail"
				:detail="detail"
				:star-size="14"
				:text-class="`type-body-medium ${selected ? '' : 'text-on-surface-variant'}`"
			/>
			<p v-else-if="book.authors?.length" class="truncate type-body-medium" :class="selected ? '' : 'text-on-surface-variant'">
				<AuthorLinks v-if="!selecting" :authors="book.authors" :refs="book.authorRefs" />
				<template v-else>{{ book.authors.join(', ') }}</template>
			</p>
			<p v-if="book.series" class="truncate type-body-small" :class="selected ? '' : 'text-on-surface-variant'">
				{{ book.seriesNumber != null ? s.book.seriesWithNumber(book.series, book.seriesNumber) : book.series }}
			</p>
			<p class="flex items-center gap-1.5 type-body-small" :class="selected ? '' : 'text-on-surface-variant'">
				<AppIcon v-if="book.isFavorite" name="favoriteFill" :size="14" />
				<AppIcon :name="owned.icon" :size="14" />
				<AppIcon v-if="inLibrary(book)" :name="STATE_ICON[state]" :size="14" />
				<span class="truncate">{{ status }}</span>
			</p>
		</div>
		<ReadingRing v-if="state === 'READING'" :progress="overallProgression(book)" />
	</div>
</template>
