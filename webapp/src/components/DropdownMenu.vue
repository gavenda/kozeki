<script setup lang="ts">
import { ref } from 'vue';
import { Menu } from 'tailwind-material3';

/**
 * A button that opens a menu under it, as the phone's DropdownMenu, on Material's Menu. The trigger
 * is the `trigger` slot, given `toggle`, `open` and `props` (spread them on it); the items are
 * MenuItems in the default slot, given `close`.
 */
withDefaults(defineProps<{ align?: 'start' | 'end'; label?: string }>(), { align: 'end' });
const open = ref(false);
const close = () => (open.value = false);
</script>

<template>
	<span class="contents">
		<!-- One root, so a listener given to this component (@click.stop) holds the menu's clicks too. -->
		<Menu v-model:open="open" :placement="align === 'end' ? 'bottom-end' : 'bottom-start'" :label="label">
			<template #anchor="{ toggle, props }">
				<slot name="trigger" :toggle="toggle" :open="open" :props="props" />
			</template>
			<slot :close="close" />
		</Menu>
	</span>
</template>
