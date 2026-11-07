<script setup lang="ts">
import { computed, ref } from 'vue';
import ModalDialog from './ModalDialog.vue';
import { s } from '@/strings';

/** How many books to finish in [year] (the phone's yearly goal dialog). */
const props = defineProps<{ year: number; books: number | null }>();
const emit = defineEmits<{ save: [books: number]; close: [] }>();
const draft = ref(props.books?.toString() ?? '');
const value = computed(() => (/^\d{1,4}$/.test(draft.value) ? Number(draft.value) : 0));
</script>

<template>
	<ModalDialog :title="s.settings.yearlyGoal(year)" persistent @close="emit('close')">
		<form id="yearly-goal" @submit.prevent="value > 0 && emit('save', value)">
			<label class="text-on-surface">
				<span class="label">{{ s.settings.yearlyGoalBooks }}</span>
				<input
					:value="draft"
					class="field"
					inputmode="numeric"
					autofocus
					@input="draft = ($event.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 4)"
				/>
			</label>
		</form>
		<template #actions="{ close }">
			<button class="btn-text" type="button" @click="close">{{ s.action.cancel }}</button>
			<button class="btn-text" form="yearly-goal" :disabled="value <= 0">{{ s.action.save }}</button>
		</template>
	</ModalDialog>
</template>
