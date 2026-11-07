<script setup lang="ts">
import { computed } from 'vue';
import AppIcon from './AppIcon.vue';
import AuthorLinks from './AuthorLinks.vue';
import BookStatusBadge from './BookStatusBadge.vue';
import BookCover from './BookCover.vue';
import RatingBar from './RatingBar.vue';
import { ownership } from '@/lib/bookUi';
import type { Found } from '@/lib/library';
import { press } from '@/lib/press';
import type { Book } from '@/types';

/**
 * A book found in a catalogue, as the phone's `MetadataResultItem` (a row) and `MetadataResultCover`
 * (a cell of the covers grid). One the user has already is marked as theirs, as the library marks
 * it. A tap opens it, a long press or right-click picks it; once picking, a tap picks too.
 * [trailing] is written at the end of a row: a series page puts each book's number there.
 */
const props = defineProps<{
	found: Found;
	owned?: Book;
	layout: 'row' | 'cover';
	selected?: boolean;
	selecting?: boolean;
	trailing?: string;
}>();
const emit = defineEmits<{ open: []; toggle: [] }>();
// Made once, so a long press is still known when the click that ends it arrives.
const handlers = press(
	() => (props.selecting && !props.owned ? emit('toggle') : emit('open')),
	() => !props.owned && emit('toggle'),
);
const title = computed(() => (props.found.subtitle ? `${props.found.title}: ${props.found.subtitle}` : props.found.title));
const year = computed(() => props.found.publishedDate?.match(/\d{4}/)?.[0]);
const meta = computed(() => [year.value, props.found.publisher].filter(Boolean).join(' · '));
</script>

<template>
	<div
		role="button"
		tabindex="0"
		:aria-pressed="selecting ? selected : undefined"
		class="cursor-pointer rounded-xl transition-colors outline-none select-none focus-visible:ring-2 focus-visible:ring-primary"
		:class="[
			selected ? 'bg-secondary-container text-on-secondary-container' : 'hover:bg-on-surface/5',
			layout === 'row' ? 'flex items-center gap-3 px-4 py-2.5' : 'pb-1.5',
		]"
		v-on="handlers"
	>
		<template v-if="layout === 'row'">
			<div class="relative w-12 shrink-0">
				<BookCover :book="found" :show-title="false" rounded="rounded-lg" />
				<span v-if="selected" class="absolute inset-0 m-auto size-fit rounded-full bg-secondary p-[3px] text-on-secondary"
					><AppIcon name="check" :size="18"
				/></span>
			</div>
			<div class="flex min-w-0 flex-1 flex-col">
				<p class="line-clamp-2 type-body-large">{{ title }}</p>
				<p v-if="found.authors.length" class="truncate type-body-medium" :class="selected ? '' : 'text-on-surface-variant'">
					<AuthorLinks v-if="!selecting" :authors="found.authors" :refs="found.authorRefs" />
					<template v-else>{{ found.authors.join(', ') }}</template>
				</p>
				<p
					v-if="meta || found.averageRating"
					class="flex items-center gap-1.5 type-body-small"
					:class="selected ? '' : 'text-on-surface-variant'"
				>
					<span v-if="meta" class="truncate">{{ meta }}</span>
					<template v-if="found.averageRating">
						<RatingBar :rating="Math.round(found.averageRating * 2) / 2" :size="14" />
						<span>{{ found.averageRating.toFixed(1) }}</span>
					</template>
				</p>
			</div>
			<span v-if="trailing" class="type-label-large text-on-surface-variant">{{ trailing }}</span>
			<BookStatusBadge v-if="owned" :book="owned" />
		</template>
		<template v-else>
			<div class="relative transition-[padding]" :class="selected ? 'p-1.5' : ''">
				<BookCover :book="found" />
				<span v-if="selected" class="absolute start-3 top-3 rounded-full bg-secondary p-[3px] text-on-secondary"
					><AppIcon name="check" :size="18"
				/></span>
				<span
					v-if="owned"
					class="absolute end-1.5 top-1.5 rounded-full bg-surface-container-highest/80 p-[5px] text-on-surface"
					:title="ownership(owned).label"
				>
					<AppIcon :name="ownership(owned).icon" :size="18" />
				</span>
			</div>
			<p class="mt-1.5 line-clamp-2 px-1 type-label-large">{{ found.title }}</p>
			<p class="truncate px-1 type-body-small" :class="selected ? '' : 'text-on-surface-variant'">{{ found.authors.join(', ') }}</p>
		</template>
	</div>
</template>
