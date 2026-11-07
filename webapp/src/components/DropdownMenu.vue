<script setup lang="ts">
import { onClickOutside, onKeyStroke } from '@vueuse/core';
import { ref } from 'vue';

/**
 * A button that opens a menu under it, as the phone's DropdownMenu. The trigger is the `trigger`
 * slot, given `toggle` and `open`; the items are the default slot, given `close`.
 */
withDefaults(defineProps<{ align?: 'start' | 'end' }>(), { align: 'end' });
const open = ref(false);
const root = ref<HTMLElement>();
onClickOutside(root, () => (open.value = false));
onKeyStroke('Escape', () => (open.value = false));
const close = () => (open.value = false);
</script>

<template>
	<div ref="root" class="relative">
		<slot name="trigger" :toggle="() => (open = !open)" :open="open" />
		<div v-if="open" class="menu top-full mt-1" :class="align === 'end' ? 'end-0' : 'start-0'" role="menu">
			<slot :close="close" />
		</div>
	</div>
</template>
