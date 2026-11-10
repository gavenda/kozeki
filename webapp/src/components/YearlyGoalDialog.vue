<script setup lang="ts">
import { computed, ref } from 'vue';
import ModalDialog from './ModalDialog.vue';
import { s } from '@/strings';
import { Button, TextField } from 'tailwind-material3';

/** How many books to finish in [year] (the phone's yearly goal dialog). */
const props = defineProps<{ year: number; books: number | null }>();
const emit = defineEmits<{ save: [books: number]; close: [] }>();
const draft = ref(props.books?.toString() ?? '');
const value = computed(() => (/^\d{1,4}$/.test(draft.value) ? Number(draft.value) : 0));
</script>

<template>
	<ModalDialog :title="s.settings.yearlyGoal(year)" persistent @close="emit('close')">
		<form id="yearly-goal" @submit.prevent="value > 0 && emit('save', value)">
			<TextField
				:model-value="draft"
				variant="outlined"
				:label="s.settings.yearlyGoalBooks"
				class="w-full"
				inputmode="numeric"
				autofocus
				@update:model-value="
					draft = String($event ?? '')
						.replace(/\D/g, '')
						.slice(0, 4)
				"
			/>
		</form>
		<template #actions="{ close }">
			<Button variant="text" type="button" @click="close">{{ s.action.cancel }}</Button>
			<Button variant="text" type="submit" form="yearly-goal" :disabled="value <= 0">{{ s.action.save }}</Button>
		</template>
	</ModalDialog>
</template>
