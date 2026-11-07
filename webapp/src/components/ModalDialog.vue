<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { IconName } from '@/lib/icons';
import AppIcon from './AppIcon.vue';

/**
 * A modal on the native <dialog>, which takes care of focus and the Escape key, dressed as the
 * phone's AlertDialog: 28px round, the high surface container, the actions at the end. With an
 * [icon] the headline is centred under it. A [persistent] one, a form, does not close on a click
 * outside it, so what was typed is not lost.
 */
const props = defineProps<{ title: string; icon?: IconName; iconClass?: string; persistent?: boolean; wide?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>();

onMounted(() => dialog.value?.showModal());
/** Closes on a click on the backdrop, which lands on the dialog itself but outside its box. */
function outside(event: MouseEvent) {
	if (props.persistent || event.target !== dialog.value) return;
	const box = dialog.value.getBoundingClientRect();
	const inside = event.clientX >= box.left && event.clientX <= box.right && event.clientY >= box.top && event.clientY <= box.bottom;
	if (!inside) dialog.value.close();
}
</script>

<template>
	<dialog
		ref="dialog"
		class="m-auto max-h-[calc(100dvh-3rem)] rounded-[28px] bg-surface-container-high p-6 text-on-surface backdrop:bg-scrim/40"
		:class="wide ? 'w-[min(40rem,calc(100vw-2rem))]' : 'w-[min(35rem,calc(100vw-2rem))]'"
		@close="emit('close')"
		@click="outside"
	>
		<div v-if="icon || $slots.icon" class="mb-4 flex justify-center" :class="iconClass ?? 'text-secondary'">
			<slot name="icon"><AppIcon v-if="icon" :name="icon" /></slot>
		</div>
		<h2 class="mb-4 type-headline-small" :class="{ 'text-center': icon || $slots.icon }">{{ title }}</h2>
		<div class="type-body-medium text-on-surface-variant">
			<slot :close="() => dialog?.close()" />
		</div>
		<div class="mt-6 flex flex-wrap justify-end gap-2">
			<slot name="actions" :close="() => dialog?.close()" />
		</div>
	</dialog>
</template>
