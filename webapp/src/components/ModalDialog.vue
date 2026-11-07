<script setup lang="ts">
import { onMounted, ref } from 'vue';

/** A modal on the native <dialog>, which takes care of focus and the Escape key. */
defineProps<{ title: string }>();
const emit = defineEmits<{ close: [] }>();
const dialog = ref<HTMLDialogElement>();

onMounted(() => dialog.value?.showModal());
</script>

<template>
	<dialog
		ref="dialog"
		class="m-auto w-[min(32rem,calc(100vw-2rem))] rounded-3xl bg-surface-container-high p-6 text-on-surface backdrop:bg-scrim/40"
		@close="emit('close')"
		@click.self="dialog?.close()"
	>
		<h2 class="mb-4 text-xl font-medium">{{ title }}</h2>
		<slot />
		<div class="mt-6 flex justify-end gap-2">
			<slot name="actions" :close="() => dialog?.close()" />
		</div>
	</dialog>
</template>
