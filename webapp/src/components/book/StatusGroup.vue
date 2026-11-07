<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { READING_STATE_ORDER, STATE_ICON } from '@/lib/bookUi';
import { s } from '@/strings';
import type { ReadingState } from '@/types';

/** Where the user is with a book, as the phone's `StateSelector`: one connected group that wraps where the states do not fit. */
defineProps<{ state: ReadingState }>();
const emit = defineEmits<{ change: [state: ReadingState] }>();
</script>

<template>
	<section class="flex flex-col gap-1">
		<h2 class="section-title">{{ s.book.status }}</h2>
		<div class="flex flex-wrap gap-0.5" role="radiogroup" :aria-label="s.book.status">
			<button
				v-for="(option, index) in READING_STATE_ORDER"
				:key="option"
				role="radio"
				:aria-checked="option === state"
				class="flex h-10 cursor-pointer items-center gap-2 px-4 type-label-large transition-[border-radius,filter] duration-200 hover:brightness-110"
				:class="[
					option === state ? 'rounded-full bg-primary text-on-primary' : 'bg-surface-container text-on-surface-variant',
					option === state
						? ''
						: [
								index === 0 ? 'rounded-s-full' : 'rounded-s-sm',
								index === READING_STATE_ORDER.length - 1 ? 'rounded-e-full' : 'rounded-e-sm',
							],
				]"
				@click="emit('change', option)"
			>
				<AppIcon :name="option === state ? 'check' : STATE_ICON[option]" :size="18" />
				{{ s.state[option] }}
			</button>
		</div>
	</section>
</template>
