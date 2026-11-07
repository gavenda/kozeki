<script setup lang="ts">
import { computed } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import DropdownMenu from '@/components/DropdownMenu.vue';
import { canChangeState, canFavorite, READING_STATE_ORDER, STATE_ICON, stateOf } from '@/lib/bookUi';
import { inLibrary } from '@/lib/library';
import { s } from '@/strings';
import type { Book, ReadingState } from '@/types';

/**
 * Floats over the grid while books are picked, as the phone's `SelectionToolbar`: how many, and
 * what can be done to them together. Making them favourites is the button beside it; the rest is in
 * its menu.
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
		<div class="flex items-center gap-2">
			<div class="flex h-16 items-center rounded-full bg-surface-container px-2 shadow-lg">
				<button class="icon-btn" :aria-label="s.selection.clear" :title="s.selection.clear" @click="emit('clear')">
					<AppIcon name="close" />
				</button>
				<span class="ps-1 pe-3 type-title-medium" :aria-label="s.selection.count(books.length)">{{ books.length }}</span>
				<button
					class="icon-btn"
					:disabled="allSelected"
					:aria-label="s.selection.selectAll"
					:title="s.selection.selectAll"
					@click="emit('selectAll')"
				>
					<AppIcon name="selectAll" />
				</button>
				<button class="icon-btn" :aria-label="s.shelves.addTo" :title="s.shelves.addTo" @click="emit('shelves')">
					<AppIcon name="shelves" />
				</button>
				<DropdownMenu>
					<template #trigger="{ toggle }">
						<button class="icon-btn" :aria-label="s.book.moreActions" :title="s.book.moreActions" @click="toggle">
							<AppIcon name="moreVert" />
						</button>
					</template>
					<template #default="{ close }">
						<template v-if="changeable.length">
							<button
								v-for="state in READING_STATE_ORDER.filter((state) => state !== shared)"
								:key="state"
								class="menu-item"
								@click="(emit('state', state), close())"
							>
								<AppIcon :name="STATE_ICON[state]" />{{ s.book.markAs(s.state[state]) }}
							</button>
							<hr class="my-2 border-outline-variant" />
						</template>
						<!-- One book gets the full details; several are simply marked as bought today. -->
						<button v-if="single || notPurchased.length" class="menu-item" @click="(emit('purchase'), close())">
							<AppIcon name="purchasedBookFill" />
							{{ single?.acquisition === 'PURCHASED' ? s.purchase.dialogTitle : s.purchase.markPurchased }}
						</button>
						<button v-if="purchased.length" class="menu-item" @click="(emit('unpurchase'), close())">
							<AppIcon :name="toWishlist ? 'kidStar' : 'purchasedBookOffFill'" />
							{{ toWishlist ? s.purchase.moveToWishlist : s.purchase.markNotPurchased }}
						</button>
						<hr class="my-2 border-outline-variant" />
						<button class="menu-item" @click="(emit('remove'), close())"><AppIcon name="deleteFill" />{{ s.action.remove }}</button>
					</template>
				</DropdownMenu>
			</div>
			<button
				v-if="favoritable.length"
				class="flex size-14 cursor-pointer items-center justify-center rounded-2xl bg-primary-container text-on-primary-container shadow-lg hover:brightness-110"
				:aria-label="allFavorite ? s.favorite.remove : s.favorite.add"
				:title="allFavorite ? s.favorite.remove : s.favorite.add"
				@click="emit('favorite', !allFavorite)"
			>
				<AppIcon :name="allFavorite ? 'favoriteFill' : 'favorite'" />
			</button>
		</div>
	</div>
</template>

<style scoped>
/* The toolbar sits at the foot of the window, so its menu opens upwards. */
:deep(.menu) {
	top: auto;
	bottom: 100%;
	margin-bottom: 4px;
}
</style>
