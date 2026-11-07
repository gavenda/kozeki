<script setup lang="ts">
import ModalDialog from '@/components/ModalDialog.vue';
import { s } from '@/strings';

/** Asks before removing [count] books with everything recorded of them (the phone's `DeleteBooksDialog`). */
defineProps<{ count: number }>();
const emit = defineEmits<{ confirm: []; close: [] }>();
</script>

<template>
	<ModalDialog :title="s.deleteBooks.title(count)" icon="deleteFill" icon-class="text-error" @close="emit('close')">
		<p>{{ s.deleteBooks.message(count) }}</p>
		<template #actions="{ close }">
			<button class="btn-text" @click="close">{{ s.deleteBooks.keep(count) }}</button>
			<button class="btn-danger" @click="(emit('confirm'), close())">{{ s.deleteBooks.confirm(count) }}</button>
		</template>
	</ModalDialog>
</template>
