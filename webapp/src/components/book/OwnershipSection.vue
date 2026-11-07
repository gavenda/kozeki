<script setup lang="ts">
import ButtonGroup from '@/components/ButtonGroup.vue';
import { formatDay, formatPrice } from '@/lib/format';
import { inLibrary } from '@/lib/library';
import { s } from '@/strings';
import type { Book } from '@/types';

/**
 * Whether the book is bought, as the phone's `OwnershipSection`: wished for (or just not bought,
 * for one with its EPUB) or purchased, and what is on record of the purchase.
 */
defineProps<{ book: Book }>();
const emit = defineEmits<{ purchased: [purchased: boolean]; edit: [] }>();
</script>

<template>
	<section class="flex flex-col gap-3">
		<h2 class="section-title">{{ s.purchase.title }}</h2>
		<ButtonGroup
			connected
			:model-value="book.acquisition === 'PURCHASED' ? 'yes' : 'no'"
			:options="[
				{ value: 'no', label: inLibrary(book) ? s.purchase.notPurchased : s.purchase.wishlist },
				{ value: 'yes', label: s.purchase.purchased },
			]"
			@update:model-value="(value) => emit('purchased', value === 'yes')"
		/>
		<div v-if="book.acquisition === 'PURCHASED'" class="flex items-start gap-2">
			<div class="min-w-0 flex-1">
				<p class="type-title-medium-em">
					{{ book.purchasePriceMinor != null ? formatPrice(book.purchasePriceMinor, book.purchaseCurrency) : s.purchase.noPrice }}
				</p>
				<p v-if="book.purchasedOn != null" class="type-body-medium text-on-surface-variant">
					{{ s.purchase.boughtOn(formatDay(book.purchasedOn)) }}
				</p>
				<p v-if="book.purchaseLocation" class="type-body-medium text-on-surface-variant">{{ book.purchaseLocation }}</p>
				<p v-if="book.purchaseMemo" class="type-body-medium whitespace-pre-line text-on-surface-variant">{{ book.purchaseMemo }}</p>
			</div>
			<button class="btn-text" @click="emit('edit')">{{ s.action.edit }}</button>
		</div>
	</section>
</template>
