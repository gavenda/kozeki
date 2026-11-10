<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Dialog } from 'tailwind-material3';
import type { IconName } from '@/lib/icons';
import AppIcon from './AppIcon.vue';

/**
 * The phone's AlertDialog, as Material's Dialog: it opens when it is shown and says `close` once it
 * has animated shut. With an [icon] the headline is centred under it. A [persistent] one, a form, does
 * not close on a click outside it or on Escape, so what was typed is not lost. A `portrait` (one of
 * Ui's faces) stands over the headline instead, larger than Material's icon allows. The content keeps
 * a fixed width, as the phone's dialogs do, so a form does not shrink to its fields.
 */
defineProps<{ title: string; icon?: IconName; iconClass?: string; persistent?: boolean }>();
const emit = defineEmits<{ close: [] }>();
const open = ref(false);

onMounted(() => (open.value = true));
const close = () => (open.value = false);
</script>

<template>
	<Dialog v-model:open="open" :persistent="persistent" :aria-label="$slots.portrait ? title : undefined" @closed="emit('close')">
		<template v-if="icon && !$slots.portrait" #icon><AppIcon :name="icon" :class="iconClass" /></template>
		<template v-if="!$slots.portrait" #headline>{{ title }}</template>
		<div class="w-[32rem] max-w-full">
			<div v-if="$slots.portrait" class="flex flex-col items-center">
				<slot name="portrait" />
				<h2 class="pt-4 pb-4 text-center type-headline-small text-on-surface">{{ title }}</h2>
			</div>
			<slot :close="close" />
		</div>
		<template v-if="$slots.actions" #actions>
			<slot name="actions" :close="close" />
		</template>
	</Dialog>
</template>
