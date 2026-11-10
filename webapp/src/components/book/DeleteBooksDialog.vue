<script setup lang="ts">
import ModalDialog from '@/components/ModalDialog.vue';
import { s } from '@/strings';
import { Button } from 'tailwind-material3';

/** Asks before removing [count] books with everything recorded of them (the phone's `DeleteBooksDialog`). */
defineProps<{ count: number }>();
const emit = defineEmits<{ confirm: []; close: [] }>();
</script>

<template>
	<ModalDialog :title="s.deleteBooks.title(count)" icon="deleteFill" icon-class="text-error" @close="emit('close')">
		<p>{{ s.deleteBooks.message(count) }}</p>
		<template #actions="{ close }">
			<Button variant="text" @click="close">{{ s.deleteBooks.keep(count) }}</Button>
			<Button class="bg-error! text-on-error!" @click="(emit('confirm'), close())">{{ s.deleteBooks.confirm(count) }}</Button>
		</template>
	</ModalDialog>
</template>
