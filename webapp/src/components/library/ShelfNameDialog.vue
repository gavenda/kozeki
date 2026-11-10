<script setup lang="ts">
import { ref } from 'vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { s } from '@/strings';
import { Button, TextField } from 'tailwind-material3';

/** Names a new shelf, or renames one (the phone's shelf prompt). */
const props = defineProps<{ name?: string }>();
const emit = defineEmits<{ save: [name: string]; close: [] }>();
const draft = ref(props.name ?? '');
</script>

<template>
	<ModalDialog :title="name == null ? s.shelves.new : s.shelves.rename" persistent @close="emit('close')">
		<form id="shelf-name" @submit.prevent="draft.trim() && emit('save', draft.trim())">
			<TextField v-model="draft" variant="outlined" :label="s.shelves.name" class="w-full" autofocus maxlength="100" />
		</form>
		<template #actions="{ close }">
			<Button variant="text" type="button" @click="close">{{ s.action.cancel }}</Button>
			<Button variant="text" type="submit" form="shelf-name" :disabled="!draft.trim()">{{
				name == null ? s.shelves.create : s.shelves.save
			}}</Button>
		</template>
	</ModalDialog>
</template>
