<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { formatDate } from '@/lib/format';
import { s } from '@/strings';
import type { Note } from '@/types';

/** The user's notes on a book, newest first, as on the phone: a card each, which opens to edit. */
defineProps<{ notes: Note[] }>();
const emit = defineEmits<{ add: []; open: [note: Note] }>();
</script>

<template>
	<section class="flex flex-col gap-3">
		<div class="flex items-center justify-between">
			<h2 class="section-title">{{ s.notes.title }}</h2>
			<button class="btn-text" @click="emit('add')"><AppIcon name="add" :size="18" />{{ s.notes.add }}</button>
		</div>
		<p v-if="notes.length === 0" class="type-body-medium text-on-surface-variant">{{ s.notes.empty }}</p>
		<button
			v-for="note in notes"
			:key="note.id"
			class="card flex cursor-pointer flex-col gap-1.5 p-4 text-start transition-colors hover:bg-surface-container-high"
			@click="emit('open', note)"
		>
			<p class="type-body-medium whitespace-pre-wrap">{{ note.text }}</p>
			<p class="type-body-medium text-on-surface-variant">
				{{ [note.chapterTitle, formatDate(note.sync.createdAt)].filter(Boolean).join(' · ') }}
			</p>
		</button>
	</section>
</template>
