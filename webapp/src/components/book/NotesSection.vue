<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import { formatDate } from '@/lib/format';
import { s } from '@/strings';
import type { Note } from '@/types';
import { Button, Card } from 'tailwind-material3';

/** The user's notes on a book, newest first, as on the phone: a card each, which opens to edit. */
defineProps<{ notes: Note[] }>();
const emit = defineEmits<{ add: []; open: [note: Note] }>();
</script>

<template>
	<section class="flex flex-col gap-3">
		<div class="flex items-center justify-between">
			<h2 class="section-title">{{ s.notes.title }}</h2>
			<Button variant="text" @click="emit('add')"
				><template #icon><AppIcon name="add" /></template>{{ s.notes.add }}</Button
			>
		</div>
		<p v-if="notes.length === 0" class="type-body-medium text-on-surface-variant">{{ s.notes.empty }}</p>
		<Card
			v-for="note in notes"
			:key="note.id"
			as="button"
			interactive
			class="flex! flex-col gap-1.5 bg-surface-container!"
			@click="emit('open', note)"
		>
			<p class="type-body-medium whitespace-pre-wrap">{{ note.text }}</p>
			<p class="type-body-medium text-on-surface-variant">
				{{ [note.chapterTitle, formatDate(note.sync.createdAt)].filter(Boolean).join(' · ') }}
			</p>
		</Card>
	</section>
</template>
