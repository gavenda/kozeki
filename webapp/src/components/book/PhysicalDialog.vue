<script setup lang="ts">
import { computed, ref } from 'vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { physicalPageCount } from '@/lib/bookUi';
import { s } from '@/strings';
import type { Book, PhysicalReading } from '@/types';
import { Button, TextField } from 'tailwind-material3';

/**
 * The page reached in a paper copy and how many pages it has, as the phone's
 * `PhysicalProgressDialog`. Going back to an earlier page unreads the readings on record past it,
 * so it asks first when there are any.
 */
const props = defineProps<{ book: Book; readings: PhysicalReading[] }>();
const emit = defineEmits<{ save: [page: number, pageCount: number | null]; close: [] }>();

const page = ref(props.book.physicalPage?.toString() ?? '');
const pageCount = ref(physicalPageCount(props.book)?.toString() ?? '');
const confirming = ref(false);

const pageNumber = computed(() => (/^\d{1,5}$/.test(page.value) ? Number(page.value) : null));
const total = computed(() => (/^\d{1,5}$/.test(pageCount.value) ? Number(pageCount.value) : null));
const countValid = computed(() => pageCount.value === '' || (total.value != null && total.value > 0));
const pageValid = computed(() => pageNumber.value != null && (total.value == null || pageNumber.value <= total.value));
// The page as it will be kept: a copy with no count of its own ends where the book does.
const target = computed(() =>
	pageNumber.value == null
		? null
		: Math.min(pageNumber.value, total.value ?? (props.book.pageCount && props.book.pageCount > 0 ? props.book.pageCount : Infinity)),
);
const unread = computed(() => pagesUnread(props.readings, props.book.physicalPage, target.value));

function save(close: () => void) {
	if (unread.value > 0 && !confirming.value) {
		confirming.value = true;
		return;
	}
	emit('save', pageNumber.value!, total.value);
	close();
}
</script>

<script lang="ts">
/** How many pages of [readings] taking a copy from [current] to [next] unreads: those on record past an earlier page. */
export function pagesUnread(readings: PhysicalReading[], current: number | undefined, next: number | null): number {
	const page = next ?? 0;
	if (page >= (current ?? 0)) return 0;
	return readings.reduce((sum, reading) => sum + Math.max(reading.endPage - Math.max(reading.startPage, page), 0), 0);
}
</script>

<template>
	<ModalDialog v-if="!confirming" :title="s.physical.dialogTitle" persistent @close="emit('close')">
		<form id="physical" class="flex gap-2 pt-1" @submit.prevent>
			<TextField
				v-model="page"
				variant="outlined"
				:label="s.physical.page"
				:error="page !== '' && !pageValid"
				class="min-w-0 flex-1"
				inputmode="numeric"
				maxlength="5"
				autofocus
				@focus="($event.target as HTMLInputElement).select()"
			/>
			<TextField
				v-model="pageCount"
				variant="outlined"
				:label="s.physical.pageCount"
				:error="!countValid"
				class="min-w-0 flex-1"
				inputmode="numeric"
				maxlength="5"
			/>
		</form>
		<template #actions="{ close }">
			<Button variant="text" @click="close">{{ s.action.cancel }}</Button>
			<Button :disabled="!pageValid || !countValid" @click="save(close)">{{ s.physical.save }}</Button>
		</template>
	</ModalDialog>
	<ModalDialog v-else :title="s.physical.rewindTitle(target!)" icon="history" icon-class="text-error" @close="emit('close')">
		<p>{{ s.physical.rewindMessage(unread, target!) }}</p>
		<template #actions="{ close }">
			<Button variant="text" @click="confirming = false">{{ s.action.cancel }}</Button>
			<Button variant="text" class="text-error!" @click="save(close)">{{ s.physical.rewindConfirm }}</Button>
		</template>
	</ModalDialog>
</template>
