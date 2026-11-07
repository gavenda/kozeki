<script setup lang="ts">
import { computed, ref, useId } from 'vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { epochDayOf } from '@/lib/dates';
import { isCurrency, parsePrice, priceToInput } from '@/lib/format';
import type { Purchase } from '@/lib/library';
import { s } from '@/strings';
import type { Book } from '@/types';

/**
 * The purchase details of [book], as the phone's `PurchaseDialog`: a price in a currency, the day
 * it was bought, where and a memo. The places bought at before are offered as it is typed.
 */
const props = defineProps<{ book: Book; lastCurrency: string; locations: string[] }>();
const emit = defineEmits<{ save: [purchase: Purchase]; close: [] }>();

const currency0 = props.book.purchaseCurrency ?? props.lastCurrency;
const price = ref(props.book.purchasePriceMinor != null ? priceToInput(props.book.purchasePriceMinor, currency0) : '');
const currency = ref(currency0);
const date = ref(new Date((props.book.purchasedOn ?? epochDayOf(Date.now())) * 86_400_000).toISOString().slice(0, 10));
const location = ref(props.book.purchaseLocation ?? '');
const memo = ref(props.book.purchaseMemo ?? '');
const listId = useId();

const code = computed(() => currency.value.trim().toUpperCase());
const priceMinor = computed(() => (price.value.trim() ? parsePrice(price.value, code.value) : null));
const priceInvalid = computed(() => price.value.trim() !== '' && priceMinor.value == null);
const currencyInvalid = computed(() => !isCurrency(code.value));

function save(close: () => void) {
	const [y, m, d] = date.value.split('-').map(Number);
	emit('save', {
		acquisition: 'PURCHASED',
		priceMinor: priceMinor.value,
		currency: code.value,
		purchasedOn: y ? Math.floor(Date.UTC(y, m - 1, d) / 86_400_000) : null,
		location: location.value,
		memo: memo.value,
	});
	close();
}
</script>

<template>
	<ModalDialog :title="s.purchase.dialogTitle" persistent @close="emit('close')">
		<div class="flex flex-col gap-3 text-on-surface">
			<div class="flex gap-2">
				<label class="flex-1">
					<span class="label">{{ s.purchase.price }}</span>
					<input v-model="price" class="field" :class="{ 'field-error': priceInvalid }" inputmode="decimal" autofocus />
				</label>
				<label class="w-26">
					<span class="label">{{ s.purchase.currency }}</span>
					<input v-model="currency" class="field uppercase" :class="{ 'field-error': currencyInvalid }" maxlength="3" />
				</label>
			</div>
			<label>
				<span class="label">{{ s.purchase.date }}</span>
				<input v-model="date" type="date" class="field" />
			</label>
			<label>
				<span class="label">{{ s.purchase.location }}</span>
				<input v-model="location" class="field" :list="listId" />
				<datalist :id="listId"><option v-for="place in locations" :key="place" :value="place" /></datalist>
			</label>
			<label>
				<span class="label">{{ s.purchase.memo }}</span>
				<textarea v-model="memo" rows="2" class="field" />
			</label>
		</div>
		<template #actions="{ close }">
			<button class="btn-text" @click="close">{{ s.action.cancel }}</button>
			<button class="btn-filled" :disabled="priceInvalid || currencyInvalid" @click="save(close)">{{ s.purchase.save }}</button>
		</template>
	</ModalDialog>
</template>
