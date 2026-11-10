<script setup lang="ts">
import { useWindowScroll } from '@vueuse/core';
import { computed } from 'vue';
import { useRouter, type RouteLocationRaw } from 'vue-router';
import { AppBar, IconButton } from 'tailwind-material3';
import { s } from '@/strings';
import AppIcon from './AppIcon.vue';

/**
 * The bar across the top of a page, as the phone's top app bars. A top-level page's is large: its
 * title and subtitle sit under the bar and scroll away, and the bar takes the title once they have.
 * A page reached from another has a back arrow and keeps its title out of the bar until the page's
 * own heading has scrolled under it ([titleAfter] pixels). Either way it is Material's small AppBar,
 * which takes the surface container's colour once anything is under it. Actions go in the slot.
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
	<AppBar class="sticky top-0 z-30" :scrolled="scrolled">
		<template v-if="back" #leading>
			<IconButton variant="standard" :label="s.action.back" @click="goBack"><AppIcon name="arrowBack" /></IconButton>
		</template>
		<span
			class="block truncate transition-[opacity,translate] duration-200"
			:class="showTitle ? 'translate-y-0 opacity-100' : 'pointer-events-none translate-y-2 opacity-0'"
			:aria-hidden="!showTitle && large"
		>
			{{ title }}
		</span>
		<template v-if="$slots.default" #trailing>
			<div class="flex items-center gap-1 pe-1"><slot /></div>
		</template>
	</AppBar>
	<div v-if="large" class="px-4 pt-2 pb-5">
		<h2 class="type-display-small-em">{{ title }}</h2>
		<p v-if="subtitle" class="mt-1 type-title-medium text-on-surface-variant">{{ subtitle }}</p>
	</div>
</template>
