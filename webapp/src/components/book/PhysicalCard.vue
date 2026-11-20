<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import WavyProgress from '@/components/WavyProgress.vue';
import { physicalPageCount, physicalProgression } from '@/lib/bookUi';
import { formatPercent } from '@/lib/format';
import { s } from '@/strings';
import type { Book } from '@/types';
import BookStat from './BookStat.vue';
import { Button, IconButton } from 'tailwind-material3';

/**
 * Where the user is in a paper copy, which only they can tell, as the phone's
 * `PhysicalProgressCard`. Until they do, it is a short invitation rather than an empty meter. The
 * paper copy is told from the EPUB by its colour: the theme's leather.
 */
defineProps<{ book: Book }>();
const emit = defineEmits<{ edit: []; read: []; stop: [] }>();
</script>

<template>
	<section v-if="book.physicalPage == null" class="flex items-center gap-2 rounded-xl border border-outline-variant p-4">
		<div class="flex flex-1 flex-col gap-1">
			<p class="type-label-large text-tertiary">{{ s.book.progressPhysical }}</p>
			<p class="type-body-medium text-on-surface-variant">{{ s.physical.prompt }}</p>
		</div>
		<Button variant="tonal" @click="emit('edit')">{{ s.physical.track }}</Button>
	</section>
	<section v-else class="flex flex-col gap-3 rounded-xl bg-surface-container-high p-4">
		<p class="type-label-large text-tertiary">{{ s.book.progressPhysical }}</p>
		<template v-if="physicalProgression(book) != null">
			<p class="type-display-small-em">{{ formatPercent(physicalProgression(book)!) }}</p>
			<WavyProgress :progress="physicalProgression(book)!" tone="tertiary" />
		</template>
		<div class="flex items-center gap-2">
			<BookStat
				class="flex-1"
				:label="s.book.page"
				:value="physicalPageCount(book) ? s.xOfY(book.physicalPage, physicalPageCount(book)!) : String(book.physicalPage)"
			/>
			<!-- Narrow, and in the error colours, beside the button it must not be mistaken for. -->
			<IconButton
				variant="tonal"
				:label="s.physical.stop"
				size="xs"
				width="narrow"
				class="bg-error-container! text-on-error-container!"
				:title="s.physical.stop"
				@click="emit('stop')"
			>
				<AppIcon name="doNotDisturbOn" :size="20" />
			</IconButton>
			<Button variant="tonal" @click="emit('edit')">{{ s.physical.update }}</Button>
			<!-- Wide, as the way into the screen that turns pages. -->
			<IconButton
				:label="s.physical.readingMode"
				width="wide"
				class="bg-tertiary! text-on-tertiary!"
				:title="s.physical.readingMode"
				@click="emit('read')"
			>
				<AppIcon name="autoStories" />
			</IconButton>
		</div>
	</section>
</template>
