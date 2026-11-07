<script setup lang="ts">
import { s } from '@/strings';
import AppIcon from './AppIcon.vue';

/** Floats over a catalogue's results while some are picked, to add them all at once (the phone's `AddSelectedToolbar`). */
defineProps<{ count: number }>();
const emit = defineEmits<{ clear: []; add: [acquisition: 'WISHLIST' | 'PURCHASED'] }>();
</script>

<template>
	<div class="fixed inset-x-0 bottom-6 z-40 flex justify-center px-4">
		<div class="flex h-16 items-center gap-1 rounded-full bg-surface-container px-2 shadow-lg">
			<button class="icon-btn" :aria-label="s.selection.clear" :title="s.selection.clear" @click="emit('clear')">
				<AppIcon name="close" />
			</button>
			<span class="ps-1 pe-3 type-title-medium" :aria-label="s.selection.count(count)">{{ count }}</span>
			<button class="btn-text" @click="emit('add', 'WISHLIST')"><AppIcon name="kidStar" :size="18" />{{ s.selection.addWishlist }}</button>
			<button class="btn-tonal" @click="emit('add', 'PURCHASED')">
				<AppIcon name="purchasedBookFill" :size="18" />{{ s.selection.addPurchased }}
			</button>
		</div>
	</div>
</template>
