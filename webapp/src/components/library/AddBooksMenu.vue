<script setup lang="ts">
import { onClickOutside, onKeyStroke, useWindowScroll } from '@vueuse/core';
import { computed, ref, watch } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import { s } from '@/strings';

/**
 * The library's floating button, as the phone's `AddBooksMenu`: it opens into the ways to add
 * books, and a new shelf on the shelves. Once scrolled more than a window down and moving back up,
 * it takes the reader back to the top instead.
 */
defineProps<{ shelves: boolean }>();
const emit = defineEmits<{ find: []; manual: []; newShelf: [] }>();

const open = ref(false);
const root = ref<HTMLElement>();
onClickOutside(root, () => (open.value = false));
onKeyStroke('Escape', () => (open.value = false));

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

function main() {
	if (toTop.value) window.scrollTo({ top: 0, behavior: 'smooth' });
	else open.value = !open.value;
}
function pick(action: 'find' | 'manual' | 'newShelf') {
	open.value = false;
	if (action === 'find') emit('find');
	else if (action === 'manual') emit('manual');
	else emit('newShelf');
}
</script>

<template>
	<div ref="root" class="fixed end-4 bottom-24 z-30 flex flex-col items-end gap-1 md:end-6 md:bottom-6">
		<template v-if="open">
			<button v-if="shelves" class="fab-item bg-primary text-on-primary" @click="pick('newShelf')">
				<AppIcon name="shelvesFill" />{{ s.shelves.new }}
			</button>
			<button class="fab-item bg-secondary-container text-on-secondary-container" @click="pick('find')">
				<AppIcon name="travelExplore" />{{ s.library.addFind }}
			</button>
			<button class="fab-item mb-2 bg-tertiary-container text-on-tertiary-container" @click="pick('manual')">
				<AppIcon name="editNoteFill" />{{ s.library.addManual }}
			</button>
		</template>
		<button
			class="flex size-14 cursor-pointer items-center justify-center shadow-lg transition-[border-radius,background-color] duration-200 hover:brightness-110"
			:class="open ? 'rounded-full bg-primary text-on-primary' : 'rounded-2xl bg-primary-container text-on-primary-container'"
			:aria-label="toTop ? s.library.scrollToTop : s.library.add"
			:aria-expanded="toTop ? undefined : open"
			:title="toTop ? s.library.scrollToTop : s.library.add"
			@click="main"
		>
			<AppIcon :name="toTop ? 'keyboardArrowUp' : open ? 'close' : 'add'" />
		</button>
	</div>
</template>

<style scoped>
@reference "../../style.css";
.fab-item {
	@apply flex h-14 cursor-pointer items-center gap-3 rounded-full px-6 type-title-medium shadow-md hover:brightness-110;
}
</style>
