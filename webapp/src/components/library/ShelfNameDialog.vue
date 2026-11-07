<script setup lang="ts">
import { ref } from 'vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { s } from '@/strings';

/** Names a new shelf, or renames one (the phone's shelf prompt). */
const props = defineProps<{ name?: string }>();
const emit = defineEmits<{ save: [name: string]; close: [] }>();
const draft = ref(props.name ?? '');
</script>

<template>
	<ModalDialog :title="name == null ? s.shelves.new : s.shelves.rename" persistent @close="emit('close')">
		<form id="shelf-name" @submit.prevent="draft.trim() && emit('save', draft.trim())">
			<label>
				<span class="label">{{ s.shelves.name }}</span>
				<input v-model="draft" class="field text-on-surface" autofocus maxlength="100" />
			</label>
		</form>
		<template #actions="{ close }">
			<button class="btn-text" type="button" @click="close">{{ s.action.cancel }}</button>
			<button class="btn-text" form="shelf-name" :disabled="!draft.trim()">{{ name == null ? s.shelves.create : s.shelves.save }}</button>
		</template>
	</ModalDialog>
</template>
