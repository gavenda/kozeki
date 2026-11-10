<script setup lang="ts">
import { s } from '@/strings';
import AppIcon from './AppIcon.vue';
import { Button, IconButton, Toolbar } from 'tailwind-material3';

/** Floats over a catalogue's results while some are picked, to add them all at once (the phone's `AddSelectedToolbar`). */
defineProps<{ count: number }>();
const emit = defineEmits<{ clear: []; add: [acquisition: 'WISHLIST' | 'PURCHASED'] }>();
</script>

<template>
	<div class="fixed inset-x-0 bottom-6 z-40 flex justify-center px-4">
		<Toolbar variant="floating" :label="s.selection.count(count)">
			<IconButton variant="standard" :label="s.selection.clear" :title="s.selection.clear" @click="emit('clear')">
				<AppIcon name="close" />
			</IconButton>
			<span class="ps-1 pe-3 type-title-medium" :aria-label="s.selection.count(count)">{{ count }}</span>
			<Button variant="text" @click="emit('add', 'WISHLIST')"
				><template #icon><AppIcon name="kidStar" /></template>{{ s.selection.addWishlist }}</Button
			>
			<Button variant="tonal" @click="emit('add', 'PURCHASED')"
				><template #icon><AppIcon name="purchasedBookFill" /></template>{{ s.selection.addPurchased }}
			</Button>
		</Toolbar>
	</div>
</template>
