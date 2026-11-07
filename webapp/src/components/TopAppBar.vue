<script setup lang="ts">
import { useWindowScroll } from '@vueuse/core';
import { computed } from 'vue';
import { useRouter, type RouteLocationRaw } from 'vue-router';
import { s } from '@/strings';
import AppIcon from './AppIcon.vue';

/**
 * The bar across the top of a page, as the phone's top app bars. A top-level page's is large: its
 * title and subtitle sit under the bar and scroll away, and the bar takes the title once they have.
 * A page reached from another has a back arrow and keeps its title out of the bar until the page's
 * own heading has scrolled under it ([titleAfter] pixels). Either way the bar takes the surface
 * container's colour once anything is under it. Actions go in the slot.
 */
const props = withDefaults(
	defineProps<{ title: string; subtitle?: string; large?: boolean; back?: RouteLocationRaw; titleAfter?: number; alwaysTitle?: boolean }>(),
	{ titleAfter: 120 },
);

const router = useRouter();
const { y } = useWindowScroll();
const scrolled = computed(() => y.value > 0);
const showTitle = computed(() => props.alwaysTitle || y.value > (props.large ? 96 : props.titleAfter));

/** Back where the user came from, or to [back] when the page was opened directly. */
function goBack() {
	if (window.history.state?.back) router.back();
	else void router.push(props.back!);
}
</script>

<template>
	<header class="sticky top-0 z-30 transition-colors duration-200" :class="scrolled ? 'bg-surface-container' : 'bg-surface'">
		<div class="flex h-16 items-center gap-1 px-1">
			<button v-if="back" class="icon-btn size-12" :aria-label="s.action.back" @click="goBack">
				<AppIcon name="arrowBack" />
			</button>
			<h1
				class="min-w-0 flex-1 truncate type-title-large transition-[opacity,translate] duration-200"
				:class="[showTitle ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-2 opacity-0', back ? 'ps-1' : 'ps-3']"
				:aria-hidden="!showTitle && large"
			>
				{{ title }}
			</h1>
			<div class="flex items-center gap-1 pe-1"><slot /></div>
		</div>
	</header>
	<div v-if="large" class="px-4 pt-2 pb-5">
		<h2 class="type-display-small-em">{{ title }}</h2>
		<p v-if="subtitle" class="mt-1 type-title-medium text-on-surface-variant">{{ subtitle }}</p>
	</div>
</template>
