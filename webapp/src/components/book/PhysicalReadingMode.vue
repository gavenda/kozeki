<script setup lang="ts">
import { onKeyStroke, useWakeLock } from '@vueuse/core';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import BookCover from '@/components/BookCover.vue';
import WavyProgress from '@/components/WavyProgress.vue';
import { physicalPageCount } from '@/lib/bookUi';
import { s } from '@/strings';
import type { Book } from '@/types';
import { Button, IconButton } from 'tailwind-material3';

/**
 * The whole window given to a paper copy being read, as the phone's `PhysicalReadingMode`: one wide
 * button that moves the page on, and a narrow one beside it that takes back a tap made by mistake.
 * Each tap is saved as it is made, and the pages of one sitting are kept as one reading
 * (`sittingSince`). The arrow keys and the space bar turn the page too.
 */
const props = defineProps<{ book: Book }>();
const emit = defineEmits<{ turn: [page: number, sittingSince: number]; close: [] }>();

// The page is kept here while the screen is open, so quick taps never wait on the library.
const page = ref(props.book.physicalPage ?? 0);
const sittingSince = Date.now();
const total = computed(() => physicalPageCount(props.book));
const hasCover = computed(() => !!(props.book.coverFile || props.book.coverUrl));
const canNext = computed(() => total.value == null || page.value < total.value);

function turn(to: number) {
	page.value = to;
	emit('turn', to, sittingSince);
}
const next = () => canNext.value && turn(page.value + 1);
const previous = () => page.value > 0 && turn(page.value - 1);

onKeyStroke(['ArrowRight', ' ', 'PageDown'], (event) => (event.preventDefault(), next()));
onKeyStroke(['ArrowLeft', 'PageUp'], (event) => (event.preventDefault(), previous()));
onKeyStroke('Escape', () => emit('close'));

// The screen stays on while the window is given to the book, as the phone keeps it on.
const wakeLock = useWakeLock();
onMounted(() => wakeLock.isSupported.value && void wakeLock.request('screen'));
onBeforeUnmount(() => void wakeLock.release());
</script>

<template>
	<Teleport to="body">
		<div class="fixed inset-0 z-50 flex justify-center bg-surface text-on-surface" role="dialog" aria-modal="true" :aria-label="book.title">
			<div class="flex size-full max-w-[600px] flex-col p-4">
				<IconButton variant="standard" :label="s.action.close" :title="s.action.close" class="self-start" @click="emit('close')">
					<AppIcon name="close" />
				</IconButton>
				<div class="flex min-h-0 flex-1 flex-col items-center justify-center gap-3 px-2 text-center">
					<!-- As tall as 300px when there is room, shorter when there is not. -->
					<div v-if="hasCover" class="flex min-h-0 w-full flex-[0_1_300px] justify-center">
						<div class="aspect-[2/3] h-full"><BookCover :book="book" rounded="rounded-2xl" /></div>
					</div>
					<h1 class="line-clamp-3 type-headline-small-em">{{ book.title }}</h1>
					<p v-if="book.authors?.length" class="line-clamp-2 type-title-medium text-on-surface-variant">{{ book.authors.join(', ') }}</p>
					<p class="mt-2 type-label-large text-tertiary">{{ s.book.page }}</p>
					<p class="type-display-medium-em" aria-live="polite">{{ total ? s.xOfY(page, total) : String(page) }}</p>
					<WavyProgress v-if="total" class="w-full" :progress="Math.min(page / total, 1)" tone="tertiary" />
				</div>
				<div class="mt-4 flex items-center gap-2">
					<!-- Narrow, so that a thumb reaching for the next page does not land on it. -->
					<IconButton
						variant="tonal"
						size="xl"
						width="narrow"
						:label="s.physical.previousPage"
						:title="s.physical.previousPage"
						:disabled="page <= 0"
						:class="page > 0 && 'bg-tertiary-container! text-on-tertiary-container!'"
						@click="previous"
					>
						<AppIcon name="keyboardArrowLeft" :size="40" />
					</IconButton>
					<Button
						size="xl"
						class="min-w-0 flex-1 px-6!"
						:class="canNext && 'bg-tertiary! text-on-tertiary!'"
						:disabled="!canNext"
						@click="next"
					>
						{{ s.physical.nextPage }}
						<AppIcon name="keyboardArrowRight" :size="40" />
					</Button>
				</div>
			</div>
		</div>
	</Teleport>
</template>
