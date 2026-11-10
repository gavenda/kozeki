<script setup lang="ts">
import { ref } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { s } from '@/strings';
import type { Note } from '@/types';
import { Button, IconButton, TextField } from 'tailwind-material3';

/** Writes a new note or edits [note], which can be removed from here too (the phone's `NoteDialog`). */
const props = defineProps<{ note?: Note }>();
const emit = defineEmits<{ save: [text: string]; remove: []; close: [] }>();
const text = ref(props.note?.text ?? '');
</script>

<template>
	<ModalDialog :title="note ? s.notes.edit : s.notes.add" persistent @close="emit('close')">
		<TextField v-model="text" variant="outlined" :label="s.notes.hint" multiline :rows="6" class="w-full" autofocus />
		<template #actions="{ close }">
			<IconButton
				variant="standard"
				:label="s.notes.remove"
				class="me-auto text-error!"
				v-if="note"
				:title="s.notes.remove"
				@click="(emit('remove'), close())"
			>
				<AppIcon name="deleteFill" />
			</IconButton>
			<Button variant="text" @click="close">{{ s.action.cancel }}</Button>
			<Button :disabled="!text.trim()" @click="(emit('save', text), close())">{{ s.notes.save }}</Button>
		</template>
	</ModalDialog>
</template>
