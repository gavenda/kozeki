<script setup lang="ts">
export type Face = 'neutral' | 'curious' | 'studious' | 'worried' | 'happy' | 'asleep' | 'content' | 'downcast' | 'dismayed';

/** Ui with what to say about a screen that has nothing on it, as the phone's empty states. */
defineProps<{ face: Face; title: string; message?: string }>();
</script>

<template>
	<div class="mx-auto flex max-w-md flex-col items-center gap-3 px-8 py-12 text-center">
		<img :src="`/ui/${face}.webp`" alt="" class="w-32 select-none" draggable="false" />
		<h2 class="text-xl font-medium">{{ title }}</h2>
		<p v-if="message" class="text-sm text-on-surface-variant">{{ message }}</p>
		<slot />
	</div>
</template>
