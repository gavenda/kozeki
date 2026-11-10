<script setup lang="ts">
import { computed, ref, useId } from 'vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { epochDayOf } from '@/lib/dates';
import { isCurrency, parsePrice, priceToInput } from '@/lib/format';
import type { Purchase } from '@/lib/library';
import { s } from '@/strings';
import type { Book } from '@/types';
import { Button, DateField, TextField } from 'tailwind-material3';

/**
 * The purchase details of [book], as the phone's `PurchaseDialog`: a price in a currency, the day
 * it was bought, where and a memo. The places bought at before are offered as it is typed.
 */
const props = defineProps<{ book: Book; lastCurrency: string; locations: string[] }>();
const emit = defineEmits<{ save: [purchase: Purchase]; close: [] }>();

const currency0 = props.book.purchaseCurrency ?? props.lastCurrency;
const price = ref(props.book.purchasePriceMinor != null ? priceToInput(props.book.purchasePriceMinor, currency0) : '');
const currency = ref(currency0);
// An epoch day is a calendar date (lib/dates.ts); the picker takes it as that date at local midnight.
const day0 = new Date((props.book.purchasedOn ?? epochDayOf(Date.now())) * 86_400_000);
const date = ref<Date | null>(new Date(day0.getUTCFullYear(), day0.getUTCMonth(), day0.getUTCDate()));
const location = ref(props.book.purchaseLocation ?? '');
const memo = ref(props.book.purchaseMemo ?? '');
const listId = useId();

const code = computed(() => currency.value.trim().toUpperCase());
const priceMinor = computed(() => (price.value.trim() ? parsePrice(price.value, code.value) : null));
const priceInvalid = computed(() => price.value.trim() !== '' && priceMinor.value == null);
const currencyInvalid = computed(() => !isCurrency(code.value));

function save(close: () => void) {
	const day = date.value;
	emit('save', {
		acquisition: 'PURCHASED',
		priceMinor: priceMinor.value,
		currency: code.value,
		purchasedOn: day ? Math.floor(Date.UTC(day.getFullYear(), day.getMonth(), day.getDate()) / 86_400_000) : null,
		location: location.value,
		memo: memo.value,
	});
	close();
}
</script>

<template>
	<ModalDialog :title="s.purchase.dialogTitle" persistent @close="emit('close')">
		<div class="flex flex-col gap-3 pt-1">
			<div class="flex gap-2">
				<TextField
					v-model="price"
					variant="outlined"
					:label="s.purchase.price"
					:error="priceInvalid"
					class="min-w-0 flex-1"
					inputmode="decimal"
					autofocus
				/>
				<TextField
					v-model="currency"
					variant="outlined"
					:label="s.purchase.currency"
					:error="currencyInvalid"
					class="w-26 uppercase"
					maxlength="3"
				/>
			</div>
			<DateField v-model="date" :label="s.purchase.date" class="w-full" />
			<TextField v-model="location" variant="outlined" :label="s.purchase.location" class="w-full" :list="listId" />
			<datalist :id="listId"><option v-for="place in locations" :key="place" :value="place" /></datalist>
			<TextField v-model="memo" variant="outlined" :label="s.purchase.memo" multiline :rows="2" class="w-full" />
		</div>
		<template #actions="{ close }">
			<Button variant="text" @click="close">{{ s.action.cancel }}</Button>
			<Button :disabled="priceInvalid || currencyInvalid" @click="save(close)">{{ s.purchase.save }}</Button>
		</template>
	</ModalDialog>
</template>
