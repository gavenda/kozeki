<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import AuthorLinks from '@/components/AuthorLinks.vue';
import BookCover from '@/components/BookCover.vue';
import CoverBadges from '@/components/CoverBadges.vue';
import { press } from '@/lib/press';
import { s } from '@/strings';
import type { Book } from '@/types';
import SortDetailLine, { type SortDetail } from './SortDetailLine.vue';

/**
 * A book in the grid, as the phone's `LibraryBookItem`: its cover with the badges on it, the title,
 * and the authors or what the sort goes by. A tap opens it, a long press or a right-click picks it;
 * once picking, a tap picks too. A picked book is tinted, its cover drawn in, with a check.
 */
const props = defineProps<{ book: Book; detail: SortDetail | null; selected: boolean; selecting: boolean }>();
const emit = defineEmits<{ open: []; toggle: [] }>();
// Made once, so a long press is still known when the click that ends it arrives.
const handlers = press(
	() => (props.selecting ? emit('toggle') : emit('open')),
	() => emit('toggle'),
);
</script>

<template>
	<div
		role="button"
		tabindex="0"
		:aria-pressed="selecting ? selected : undefined"
		class="group relative cursor-pointer rounded-xl pb-1.5 transition-colors outline-none select-none focus-visible:ring-2 focus-visible:ring-primary"
		:class="selected ? 'bg-secondary-container text-on-secondary-container' : 'hover:bg-on-surface/5'"
		v-on="handlers"
	>
		<div class="relative transition-[padding] duration-200" :class="selected ? 'p-1.5' : ''">
			<BookCover :book="book" />
			<CoverBadges :book="book" />
			<span v-if="selected" class="absolute start-3 top-3 rounded-full bg-secondary p-[3px] text-on-secondary">
				<AppIcon name="check" :size="18" />
			</span>
			<!-- A pointer has no long press to speak of, so picking is offered on hover too. -->
			<button
				v-else-if="!selecting"
				class="absolute start-1.5 top-1.5 hidden rounded-full bg-surface-container-highest/80 p-[3px] text-on-surface opacity-0 transition-opacity group-hover:opacity-100 focus-visible:opacity-100 pointer-fine:block"
				:aria-label="s.selection.select"
				@click.stop="emit('toggle')"
				@pointerdown.stop
			>
				<AppIcon name="check" :size="18" />
			</button>
		</div>
		<p class="mt-1.5 line-clamp-2 px-1 type-label-large">{{ book.title }}</p>
		<SortDetailLine v-if="detail" :detail="detail" :star-size="12" text-class="px-1 type-body-small text-on-surface-variant" />
		<p v-else class="truncate px-1 type-body-small" :class="selected ? '' : 'text-on-surface-variant'">
			<AuthorLinks v-if="!selecting" :authors="book.authors ?? []" :refs="book.authorRefs" />
			<template v-else>{{ (book.authors ?? []).join(', ') }}</template>
		</p>
	</div>
</template>
