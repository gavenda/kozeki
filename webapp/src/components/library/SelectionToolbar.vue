<script setup lang="ts">
import { computed } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import DropdownMenu from '@/components/DropdownMenu.vue';
import { canChangeState, canFavorite, READING_STATE_ORDER, STATE_ICON, stateOf } from '@/lib/bookUi';
import { inLibrary } from '@/lib/library';
import { s } from '@/strings';
import type { Book, ReadingState } from '@/types';
import { Fab, IconButton, MenuGroup, MenuItem, Toolbar } from 'tailwind-material3';

/**
 * Floats over the grid while books are picked, as the phone's `SelectionToolbar`: how many, and
 * what can be done to them together. Making them favourites is the button beside it; the rest is in
 * its menu. It is Material's floating toolbar with the favourite FAB beside it.
 */
const props = defineProps<{ books: Book[]; allSelected: boolean }>();
const emit = defineEmits<{
	clear: [];
	selectAll: [];
	favorite: [favorite: boolean];
	shelves: [];
	state: [state: ReadingState];
	purchase: [];
	unpurchase: [];
	remove: [];
}>();

const favoritable = computed(() => props.books.filter(canFavorite));
const allFavorite = computed(() => favoritable.value.length > 0 && favoritable.value.every((book) => book.isFavorite));
const changeable = computed(() => props.books.filter(canChangeState));
const shared = computed(() => {
	const states = new Set(changeable.value.map(stateOf));
	return states.size === 1 ? [...states][0] : null;
});
const single = computed(() => (props.books.length === 1 ? props.books[0] : null));
const purchased = computed(() => props.books.filter((book) => book.acquisition === 'PURCHASED'));
const notPurchased = computed(() => props.books.filter((book) => book.acquisition !== 'PURCHASED'));
// Dropping the purchase leaves a wish, or a download when the book has its EPUB.
const toWishlist = computed(() => purchased.value.every((book) => !inLibrary(book)));
</script>

<template>
	<div class="fixed inset-x-0 bottom-24 z-40 flex justify-center px-4 md:bottom-6">
		<Toolbar variant="floating" :label="s.selection.count(books.length)">
			<IconButton variant="standard" :label="s.selection.clear" :title="s.selection.clear" @click="emit('clear')">
				<AppIcon name="close" />
			</IconButton>
			<span class="ps-1 pe-3 type-title-medium" :aria-label="s.selection.count(books.length)">{{ books.length }}</span>
			<IconButton
				variant="standard"
				:label="s.selection.selectAll"
				:disabled="allSelected"
				:title="s.selection.selectAll"
				@click="emit('selectAll')"
			>
				<AppIcon name="selectAll" />
			</IconButton>
			<IconButton variant="standard" :label="s.shelves.addTo" :title="s.shelves.addTo" @click="emit('shelves')">
				<AppIcon name="shelves" />
			</IconButton>
			<DropdownMenu>
				<template #trigger="{ toggle, props }">
					<IconButton v-bind="props" variant="standard" :label="s.book.moreActions" :title="s.book.moreActions" @click="toggle">
						<AppIcon name="moreVert" />
					</IconButton>
				</template>
				<MenuGroup v-if="changeable.length">
					<MenuItem v-for="state in READING_STATE_ORDER.filter((state) => state !== shared)" :key="state" @click="emit('state', state)">
						<template #icon><AppIcon :name="STATE_ICON[state]" /></template>
						{{ s.book.markAs(s.state[state]) }}
					</MenuItem>
				</MenuGroup>
				<!-- One book gets the full details; several are simply marked as bought today. -->
				<MenuGroup v-if="single || notPurchased.length || purchased.length">
					<MenuItem v-if="single || notPurchased.length" @click="emit('purchase')">
						<template #icon><AppIcon name="purchasedBookFill" /></template>
						{{ single?.acquisition === 'PURCHASED' ? s.purchase.dialogTitle : s.purchase.markPurchased }}
					</MenuItem>
					<MenuItem v-if="purchased.length" @click="emit('unpurchase')">
						<template #icon><AppIcon :name="toWishlist ? 'kidStar' : 'purchasedBookOffFill'" /></template>
						{{ toWishlist ? s.purchase.moveToWishlist : s.purchase.markNotPurchased }}
					</MenuItem>
				</MenuGroup>
				<MenuGroup>
					<MenuItem @click="emit('remove')">
						<template #icon><AppIcon name="deleteFill" /></template>
						{{ s.action.remove }}
					</MenuItem>
				</MenuGroup>
			</DropdownMenu>
			<template v-if="favoritable.length" #fab>
				<Fab
					:label="allFavorite ? s.favorite.remove : s.favorite.add"
					:title="allFavorite ? s.favorite.remove : s.favorite.add"
					@click="emit('favorite', !allFavorite)"
				>
					<template #icon><AppIcon :name="allFavorite ? 'favoriteFill' : 'favorite'" /></template>
				</Fab>
			</template>
		</Toolbar>
	</div>
</template>
