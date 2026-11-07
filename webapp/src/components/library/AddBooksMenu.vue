<script setup lang="ts">
import { onClickOutside, onKeyStroke, useMediaQuery, useWindowScroll } from '@vueuse/core';
import { computed, ref, watch } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import { railExpanded } from '@/lib/rail';
import { s } from '@/strings';

/**
 * The library's floating button, as the phone's `AddBooksMenu`: it opens into the ways to add
 * books, and a new shelf on the shelves. Once scrolled more than a window down and moving back up,
 * it takes the reader back to the top instead. On a wide window it sits at the top of the rail,
 * and its menu drops down from it.
 */
defineProps<{ shelves: boolean }>();
const emit = defineEmits<{ find: []; manual: []; newShelf: [] }>();

const onRail = useMediaQuery('(min-width: 48rem)');
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
	<Teleport to="#rail-action" :disabled="!onRail">
		<div
			ref="root"
			:class="
				onRail
					? [
							'relative ms-5 self-start transition-[margin] duration-300 ease-out motion-reduce:transition-none',
							railExpanded ? 'mb-10' : 'mb-5',
						]
					: 'fixed end-4 bottom-24 z-30 flex flex-col items-end gap-1'
			"
		>
			<!-- On the rail the options are a menu under the button, as the phone's dropdown. -->
			<Transition name="pop">
				<div
					v-if="open && onRail"
					class="absolute start-0 top-full z-50 mt-1 flex min-w-56 flex-col rounded-xl bg-surface-container py-2 shadow-lg"
					role="menu"
				>
					<button v-if="shelves" class="menu-item" role="menuitem" @click="pick('newShelf')">
						<AppIcon name="shelvesFill" />{{ s.shelves.new }}
					</button>
					<button class="menu-item" role="menuitem" @click="pick('find')"><AppIcon name="travelExplore" />{{ s.library.addFind }}</button>
					<button class="menu-item" role="menuitem" @click="pick('manual')">
						<AppIcon name="editNoteFill" />{{ s.library.addManual }}
					</button>
				</div>
			</Transition>
			<div v-if="open && !onRail" class="contents">
				<button v-if="shelves" class="fab-item bg-primary text-on-primary" @click="pick('newShelf')">
					<AppIcon name="shelvesFill" />{{ s.shelves.new }}
				</button>
				<button class="fab-item bg-secondary-container text-on-secondary-container" @click="pick('find')">
					<AppIcon name="travelExplore" />{{ s.library.addFind }}
				</button>
				<button class="fab-item bg-tertiary-container text-on-tertiary-container mb-2" @click="pick('manual')">
					<AppIcon name="editNoteFill" />{{ s.library.addManual }}
				</button>
			</div>
			<button
				class="flex h-14 cursor-pointer items-center justify-center px-4 transition-[border-radius,background-color,filter] duration-200 hover:brightness-110"
				:class="[
					open ? 'rounded-full bg-primary text-on-primary' : 'rounded-2xl bg-primary-container text-on-primary-container',
					onRail ? '' : 'shadow-lg',
				]"
				:aria-label="toTop ? s.library.scrollToTop : s.library.add"
				:aria-expanded="toTop ? undefined : open"
				:title="toTop ? s.library.scrollToTop : s.library.add"
				@click="main"
			>
				<AppIcon :name="toTop ? 'keyboardArrowUp' : open ? 'close' : 'add'" />
				<span
					class="grid transition-[grid-template-columns,opacity] duration-300 ease-out motion-reduce:transition-none"
					:class="onRail && railExpanded ? 'grid-cols-[1fr] opacity-100' : 'grid-cols-[0fr] opacity-0'"
					aria-hidden="true"
				>
					<span class="overflow-hidden ps-3 pe-1 type-label-large whitespace-nowrap">{{
						toTop ? s.library.scrollToTop : s.library.add
					}}</span>
				</span>
			</button>
		</div>
	</Teleport>
</template>

<style scoped>
.pop-enter-active,
.pop-leave-active {
	transform-origin: top left;
	transition:
		opacity 150ms ease-out,
		transform 150ms ease-out;
}
.pop-enter-from,
.pop-leave-to {
	opacity: 0;
	transform: scale(0.92);
}
@media (prefers-reduced-motion: reduce) {
	.pop-enter-active,
	.pop-leave-active {
		transition: none;
	}
}

@reference "../../style.css";
.menu-item {
	@apply flex h-14 cursor-pointer items-center gap-3 px-4 type-label-large whitespace-nowrap text-on-surface hover:bg-on-surface/10;
}
.fab-item {
	@apply flex h-14 cursor-pointer items-center gap-3 rounded-full px-6 type-title-medium whitespace-nowrap shadow-md hover:brightness-110;
}
</style>
