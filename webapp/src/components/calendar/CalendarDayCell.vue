<script setup lang="ts">
import { computed } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import BookCover from '@/components/BookCover.vue';
import HaloMark from '@/components/HaloMark.vue';
import RatingBar from '@/components/RatingBar.vue';
import type { CalendarBook } from '@/lib/stats';
import { s } from '@/strings';

/**
 * A day of the book calendar, as the phone's `DayCell`: its number, then the cover of what was
 * read, or two covers one over the other when there was more, and under them a mark for a book
 * finished that day: its stars, or a check when it has none. The slot for the mark is always kept,
 * so the rows line up. Ui's halo marks her birthday.
 */
const props = defineProps<{ day: number; books: CalendarBook[]; today: boolean; birthday: boolean; description: string }>();
const emit = defineEmits<{ open: [] }>();
const finished = computed(() => props.books.find((entry) => entry.completed));
</script>

<template>
	<component
		:is="books.length ? 'button' : 'div'"
		class="flex flex-col items-center gap-0.5 rounded-lg p-0.5"
		:class="{ 'cursor-pointer transition-colors hover:bg-on-surface/8': books.length }"
		:aria-label="books.length ? description : undefined"
		@click="books.length && emit('open')"
	>
		<span :class="today ? 'type-label-medium-em text-primary' : 'type-label-medium text-on-surface-variant'">{{ day }}</span>
		<div class="relative aspect-[2/3] w-full">
			<HaloMark v-if="birthday" class="absolute inset-x-0 top-1/2 mx-auto -translate-y-1/2" :size="44" :dial="false" />
			<BookCover v-if="books.length === 1" :book="books[0].book" :show-title="false" rounded="rounded-sm" class="absolute inset-0" />
			<template v-else-if="books.length > 1">
				<div class="absolute end-0 bottom-0 w-[78%]"><BookCover :book="books[1].book" :show-title="false" rounded="rounded-sm" /></div>
				<div class="absolute start-0 top-0 w-[78%] shadow-sm">
					<BookCover :book="books[0].book" :show-title="false" rounded="rounded-sm" />
				</div>
				<span
					v-if="books.length > 2"
					class="absolute end-0 bottom-0 rounded-sm bg-inverse-surface px-[3px] type-label-small text-inverse-on-surface"
				>
					{{ s.calendar.moreBooks(books.length - 2) }}
				</span>
			</template>
		</div>
		<span class="flex h-2.5 w-10 items-center justify-center">
			<template v-if="finished">
				<RatingBar v-if="finished.book.rating != null" :rating="finished.book.rating" :size="8" />
				<AppIcon v-else name="checkCircleFill" :size="10" class="text-primary" />
			</template>
		</span>
	</component>
</template>
