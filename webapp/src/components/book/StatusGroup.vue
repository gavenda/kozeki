<script setup lang="ts">
import { Button, ButtonGroup } from 'tailwind-material3';
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
		<ButtonGroup
			:model-value="state"
			variant="connected"
			selection="single"
			required
			class="flex-wrap"
			:aria-label="s.book.status"
			@update:model-value="(option) => option && emit('change', option as ReadingState)"
		>
			<Button v-for="option in READING_STATE_ORDER" :key="option" :value="option">
				<template #icon><AppIcon :name="option === state ? 'check' : STATE_ICON[option]" /></template>
				{{ s.state[option] }}
			</Button>
		</ButtonGroup>
	</section>
</template>
