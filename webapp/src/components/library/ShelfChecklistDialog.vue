<script setup lang="ts">
import { computed, ref } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { s } from '@/strings';
import type { Shelf } from '@/types';
import { Button, Checkbox, TextField } from 'tailwind-material3';

/**
 * Which shelves [count] picked books go on, as the phone's `ShelfChecklistSheet`. A shelf holds all
 * of them, some, or none; a tap on one with some gives it all of them. [onShelf] is how many of the
 * books each shelf holds. Done saves only the shelves that were changed.
 */
const props = defineProps<{ shelves: Shelf[]; onShelf: Map<string, number>; count: number }>();
const emit = defineEmits<{ save: [add: string[], remove: string[], create: string[]]; close: [] }>();

type Check = 'on' | 'off' | 'some';
const stateOf = (held: number): Check => (held <= 0 ? 'off' : held >= props.count ? 'on' : 'some');
const initial = new Map(props.shelves.map((shelf) => [shelf.id, stateOf(props.onShelf.get(shelf.id) ?? 0)]));
const chosen = ref(new Map(initial));
const created = ref<string[]>([]);
const naming = ref<string | null>(null);

function tap(id: string) {
	chosen.value.set(id, chosen.value.get(id) === 'on' ? 'off' : 'on');
}
const changed = computed(() => [...chosen.value].filter(([id, now]) => now !== initial.get(id)));

function done(close: () => void) {
	emit(
		'save',
		changed.value.filter(([, now]) => now === 'on').map(([id]) => id),
		changed.value.filter(([, now]) => now === 'off').map(([id]) => id),
		created.value,
	);
	close();
}
</script>

<template>
	<ModalDialog :title="s.shelves.addToTitle" @close="emit('close')">
		<ul class="flex max-h-[50vh] flex-col overflow-y-auto text-on-surface">
			<li v-for="shelf in shelves" :key="shelf.id">
				<Checkbox
					:model-value="chosen.get(shelf.id) === 'on'"
					:indeterminate="chosen.get(shelf.id) === 'some'"
					class="w-full py-1 type-body-large"
					@update:model-value="tap(shelf.id)"
				>
					{{ shelf.name }}
				</Checkbox>
			</li>
			<li v-for="name in created" :key="name">
				<Checkbox :model-value="true" disabled class="w-full py-1 type-body-large">{{ name }}</Checkbox>
			</li>
		</ul>
		<form
			v-if="naming !== null"
			class="mt-2 flex items-center gap-2"
			@submit.prevent="naming.trim() && (created.push(naming.trim()), (naming = null))"
		>
			<TextField v-model="naming" variant="outlined" :label="s.shelves.name" class="min-w-0 flex-1" autofocus />
			<Button variant="text" type="submit" :disabled="!naming.trim()">{{ s.shelves.create }}</Button>
		</form>
		<Button variant="text" class="mt-2 -ms-3" v-else @click="naming = ''"
			><template #icon><AppIcon name="add" /></template>{{ s.shelves.new }}</Button
		>
		<template #actions="{ close }">
			<Button @click="done(close)">{{ s.shelves.done }}</Button>
		</template>
	</ModalDialog>
</template>
