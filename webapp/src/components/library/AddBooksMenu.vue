<script setup lang="ts">
import { useMediaQuery, useWindowScroll } from '@vueuse/core';
import { computed, ref, watch } from 'vue';
import { Fab, FabMenu, FabMenuItem, Menu, MenuItem } from 'tailwind-material3';
import AppIcon from '@/components/AppIcon.vue';
import { railExpanded } from '@/lib/rail';
import { s } from '@/strings';

/**
 * The library's floating button, as the phone's `AddBooksMenu`: a FAB menu that opens into the ways
 * to add books, and a new shelf on the shelves. Once scrolled more than a window down and moving back
 * up, it takes the reader back to the top instead. On a wide window it sits at the top of the rail,
 * extended while the rail is, and its menu drops down from it.
 */
defineProps<{ shelves: boolean }>();
const emit = defineEmits<{ find: []; manual: []; newShelf: [] }>();

const onRail = useMediaQuery('(min-width: 48rem)');
const open = ref(false);

const { y } = useWindowScroll();
const upward = ref(false);
let turnedAt = 0;
watch(y, (now, before) => {
	if (now > before) {
		upward.value = false;
		turnedAt = now;
	} else if (turnedAt - now >= 24) upward.value = true;
});
const toTop = computed(() => !open.value && upward.value && y.value > window.innerHeight);
const label = computed(() => (toTop.value ? s.library.scrollToTop : s.library.add));

const scrollUp = () => window.scrollTo({ top: 0, behavior: 'smooth' });
function main(toggle: () => void) {
	if (toTop.value) scrollUp();
	else toggle();
}
</script>

<template>
	<Teleport v-if="onRail" to="#rail-action">
		<Menu v-model:open="open" placement="bottom-start" :label="s.library.add">
			<template #anchor="{ toggle, props }">
				<Fab v-bind="toTop ? {} : props" :label="label" :expanded="railExpanded" lowered @click="main(toggle)">
					<template #icon><AppIcon :name="toTop ? 'keyboardArrowUp' : 'add'" /></template>
					{{ label }}
				</Fab>
			</template>
			<MenuItem v-if="shelves" @click="emit('newShelf')">
				<template #icon><AppIcon name="shelvesFill" /></template>
				{{ s.shelves.new }}
			</MenuItem>
			<MenuItem @click="emit('find')">
				<template #icon><AppIcon name="travelExplore" /></template>
				{{ s.library.addFind }}
			</MenuItem>
			<MenuItem @click="emit('manual')">
				<template #icon><AppIcon name="editNoteFill" /></template>
				{{ s.library.addManual }}
			</MenuItem>
		</Menu>
	</Teleport>
	<div v-else class="fixed end-4 bottom-24 z-30">
		<Fab v-if="toTop" :label="label" @click="scrollUp">
			<template #icon><AppIcon name="keyboardArrowUp" /></template>
		</Fab>
		<FabMenu v-else v-model:open="open" :label="s.library.add" :close-label="s.action.close">
			<template #icon><AppIcon name="add" /></template>
			<FabMenuItem v-if="shelves" @click="emit('newShelf')">
				<template #icon><AppIcon name="shelvesFill" /></template>
				{{ s.shelves.new }}
			</FabMenuItem>
			<FabMenuItem @click="emit('find')">
				<template #icon><AppIcon name="travelExplore" /></template>
				{{ s.library.addFind }}
			</FabMenuItem>
			<FabMenuItem @click="emit('manual')">
				<template #icon><AppIcon name="editNoteFill" /></template>
				{{ s.library.addManual }}
			</FabMenuItem>
		</FabMenu>
	</div>
</template>
