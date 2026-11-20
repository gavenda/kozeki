<script setup lang="ts">
import { onKeyStroke, useDocumentVisibility, useIntervalFn, useWakeLock, useWindowSize } from '@vueuse/core';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import BookCover from '@/components/BookCover.vue';
import WavyProgress from '@/components/WavyProgress.vue';
import { physicalPageCount } from '@/lib/bookUi';
import { coverSrc } from '@/lib/covers';
import { PhysicalSitting, type SittingPart } from '@/lib/sitting';
import { s } from '@/strings';
import type { Book } from '@/types';
import { Button, IconButton } from 'tailwind-material3';

/**
 * The whole window given to a paper copy being read, as the phone's `PhysicalReadingMode`: one wide
 * button that moves the page on, a short narrow one beside it that takes back a tap made by
 * mistake, and a timer whose time counts as reading. Each tap is saved as it is made, and the pages
 * of one sitting are kept as one reading (`sittingSince`). The arrow keys and the space bar turn the
 * page too.
 *
 * In a tall window the cover fills it behind a translucent card; in a wide one it stands to the
 * left. Either way the card and the buttons sit together in the middle.
 */
const props = defineProps<{ book: Book }>();
const emit = defineEmits<{ turn: [page: number, sittingSince: number]; session: [part: SittingPart]; close: [] }>();

// The page is kept here while the screen is open, so quick taps never wait on the library.
const page = ref(props.book.physicalPage ?? 0);
const sittingSince = Date.now();
const total = computed(() => physicalPageCount(props.book));
const cover = computed(() => coverSrc(props.book));
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

// ---- The timer ------------------------------------------------------------------------------

const sitting = new PhysicalSitting();
const clock = ref(sitting.clock);
const now = ref(Date.now());
useIntervalFn(() => (now.value = Date.now()), 1000);
function keep(part: SittingPart | null) {
	clock.value = sitting.clock;
	if (part) emit('session', part);
}
const running = computed(() => clock.value.runningSince != null);
const elapsed = computed(
	() => clock.value.baseMs + (clock.value.runningSince != null ? Math.max(now.value - clock.value.runningSince, 0) : 0),
);
const clockText = computed(() => {
	const seconds = Math.floor(elapsed.value / 1000);
	const pad = (n: number) => String(n).padStart(2, '0');
	const hours = Math.floor(seconds / 3600);
	const minutes = Math.floor(seconds / 60) % 60;
	return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds % 60)}` : `${minutes}:${pad(seconds % 60)}`;
});
function toggleTimer() {
	if (running.value) sitting.pause();
	else sitting.resume();
	now.value = Date.now();
	clock.value = sitting.clock;
}

// Time out of sight is not reading, and what was read until then is kept in case the tab is never
// come back to.
const visibility = useDocumentVisibility();
watch(visibility, (state) => {
	if (state === 'hidden') {
		keep(sitting.hide());
	} else {
		sitting.show();
		now.value = Date.now();
		clock.value = sitting.clock;
	}
});
onMounted(() => {
	sitting.start();
	clock.value = sitting.clock;
});
onBeforeUnmount(() => keep(sitting.close()));

// ---- Size ------------------------------------------------------------------------------------

// The layout is made for a window about the size of a laptop's (a tall one about a phone's or
// tablet's) and zoomed up to fill a bigger one, up to twice over, so its controls grow with it.
const { width, height } = useWindowSize();
const scale = computed(() => {
	const [w, h] = [width.value, height.value];
	const fit = w > h ? Math.min(w / 1280, h / 800) : Math.min(w / 600, h / 960);
	return Math.min(Math.max(fit, 1), 2);
});
const scaled = computed(() => ({
	width: `${width.value / scale.value}px`,
	height: `${height.value / scale.value}px`,
	zoom: scale.value,
}));

// The screen stays on while the window is given to the book, as the phone keeps it on.
const wakeLock = useWakeLock();
onMounted(() => wakeLock.isSupported.value && void wakeLock.request('screen'));
onBeforeUnmount(() => void wakeLock.release());
</script>

<template>
	<Teleport to="body">
		<div class="fixed inset-0 z-50 bg-surface text-on-surface" role="dialog" aria-modal="true" :aria-label="book.title">
			<!-- In a tall window the cover is the background, darkened a little so the card stands out. -->
			<template v-if="cover">
				<img :src="cover" alt="" class="absolute inset-0 size-full object-cover landscape:hidden" referrerpolicy="no-referrer" />
				<div class="absolute inset-0 bg-black/30 landscape:hidden" />
			</template>

			<!-- Laid out for a laptop's window and zoomed to fill a bigger one, so a large monitor gets large controls. -->
			<div class="absolute top-0 left-0" :style="scaled">
				<div class="relative flex size-full items-center justify-center gap-12 px-6 py-18 landscape:px-12 landscape:py-16">
					<div v-if="cover" class="hidden h-full max-h-[600px] landscape:block">
						<div class="aspect-[2/3] h-full"><BookCover :book="book" rounded="rounded-2xl" /></div>
					</div>
					<div class="flex w-full max-w-[480px] flex-col items-center gap-6">
						<section
							class="flex w-full flex-col items-center gap-2 rounded-[28px] p-6 text-center landscape:bg-transparent! landscape:p-0"
							:class="cover ? 'bg-surface-container-high/82' : 'bg-surface-container-high'"
						>
							<h1 class="line-clamp-3 type-headline-small-em">{{ book.title }}</h1>
							<p v-if="book.authors?.length" class="line-clamp-2 type-title-medium text-on-surface-variant">
								{{ book.authors.join(', ') }}
							</p>
							<p class="mt-1 type-label-large text-tertiary">{{ s.book.page }}</p>
							<p class="type-display-medium-em" aria-live="polite">{{ total ? s.xOfY(page, total) : String(page) }}</p>
							<WavyProgress v-if="total" class="w-full" :progress="Math.min(page / total, 1)" tone="tertiary" />
						</section>
						<div class="flex w-full items-center gap-3 landscape:max-w-[360px]">
							<!-- Short and narrow, so that a thumb reaching for the next page does not land on it. -->
							<IconButton
								variant="tonal"
								size="m"
								width="narrow"
								:label="s.physical.previousPage"
								:title="s.physical.previousPage"
								:disabled="page <= 0"
								:class="page > 0 && 'bg-tertiary-container! text-on-tertiary-container!'"
								@click="previous"
							>
								<AppIcon name="undo" />
							</IconButton>
							<Button
								size="l"
								class="min-w-0 flex-1 px-6!"
								:class="canNext && 'bg-tertiary! text-on-tertiary!'"
								:disabled="!canNext"
								@click="next"
							>
								{{ s.physical.nextPage }}
								<AppIcon name="keyboardArrowRight" :size="32" />
							</Button>
						</div>
					</div>
				</div>

				<!-- The way out on one side, the sitting's timer on the other. Over a cover both sit on the surface colour. -->
				<div class="absolute inset-x-0 top-0 flex items-center p-2">
					<IconButton
						:variant="cover ? 'tonal' : 'standard'"
						:label="s.action.back"
						:title="s.action.back"
						:class="cover && 'bg-surface-container-high/82! text-on-surface! landscape:bg-transparent!'"
						@click="emit('close')"
					>
						<AppIcon name="arrowBack" />
					</IconButton>
					<div
						class="ml-auto flex items-center gap-1 rounded-full"
						:class="cover ? 'bg-surface-container-high/82 landscape:bg-surface-container-high' : 'bg-surface-container-high'"
					>
						<span class="flex size-10 items-center justify-center text-tertiary"><AppIcon name="timerFill" /></span>
						<span class="type-title-medium tabular-nums" role="timer">{{ clockText }}</span>
						<IconButton
							variant="standard"
							:label="running ? s.physical.timerPause : s.physical.timerResume"
							:title="running ? s.physical.timerPause : s.physical.timerResume"
							@click="toggleTimer"
						>
							<AppIcon :name="running ? 'pauseFill' : 'playArrowFill'" />
						</IconButton>
					</div>
				</div>
			</div>
		</div>
	</Teleport>
</template>
