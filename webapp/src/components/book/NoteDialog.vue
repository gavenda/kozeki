<script setup lang="ts">
import { ref } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { s } from '@/strings';
import type { Note } from '@/types';

/** Writes a new note or edits [note], which can be removed from here too (the phone's `NoteDialog`). */
const props = defineProps<{ note?: Note }>();
const emit = defineEmits<{ save: [text: string]; remove: []; close: [] }>();
const text = ref(props.note?.text ?? '');
</script>

<template>
	<ModalDialog :title="note ? s.notes.edit : s.notes.add" persistent @close="emit('close')">
		<label class="text-on-surface">
			<span class="label">{{ s.notes.hint }}</span>
			<textarea v-model="text" rows="6" class="field" autofocus />
		</label>
		<template #actions="{ close }">
			<button
				v-if="note"
				class="icon-btn me-auto text-error"
				:aria-label="s.notes.remove"
				:title="s.notes.remove"
				@click="(emit('remove'), close())"
			>
				<AppIcon name="deleteFill" />
			</button>
			<button class="btn-text" @click="close">{{ s.action.cancel }}</button>
			<button class="btn-filled" :disabled="!text.trim()" @click="(emit('save', text), close())">{{ s.notes.save }}</button>
		</template>
	</ModalDialog>
</template>
