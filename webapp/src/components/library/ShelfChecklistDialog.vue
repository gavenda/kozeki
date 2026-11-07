<script setup lang="ts">
import { computed, ref } from 'vue';
import AppIcon from '@/components/AppIcon.vue';
import ModalDialog from '@/components/ModalDialog.vue';
import { s } from '@/strings';
import type { Shelf } from '@/types';

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
		<ul class="-mx-2 flex max-h-[50vh] flex-col overflow-y-auto text-on-surface">
			<li v-for="shelf in shelves" :key="shelf.id">
				<button
					class="flex w-full cursor-pointer items-center gap-4 rounded-lg px-2 py-3 text-start hover:bg-on-surface/8"
					role="checkbox"
					:aria-checked="chosen.get(shelf.id) === 'on' ? 'true' : chosen.get(shelf.id) === 'some' ? 'mixed' : 'false'"
					@click="tap(shelf.id)"
				>
					<span
						class="flex size-5 items-center justify-center rounded-sm border-2"
						:class="chosen.get(shelf.id) === 'off' ? 'border-on-surface-variant' : 'border-primary bg-primary text-on-primary'"
					>
						<AppIcon v-if="chosen.get(shelf.id) === 'on'" name="check" :size="16" />
						<AppIcon v-else-if="chosen.get(shelf.id) === 'some'" name="remove" :size="16" />
					</span>
					<span class="type-body-large">{{ shelf.name }}</span>
				</button>
			</li>
			<li v-for="name in created" :key="name" class="flex items-center gap-4 px-2 py-3">
				<span class="flex size-5 items-center justify-center rounded-sm border-2 border-primary bg-primary text-on-primary"
					><AppIcon name="check" :size="16"
				/></span>
				<span class="type-body-large">{{ name }}</span>
			</li>
		</ul>
		<form v-if="naming !== null" class="mt-2 flex gap-2" @submit.prevent="naming.trim() && (created.push(naming.trim()), (naming = null))">
			<input v-model="naming" class="field text-on-surface" :placeholder="s.shelves.name" autofocus />
			<button class="btn-text" :disabled="!naming.trim()">{{ s.shelves.create }}</button>
		</form>
		<button v-else class="btn-text mt-2 -ms-3" @click="naming = ''"><AppIcon name="add" :size="18" />{{ s.shelves.new }}</button>
		<template #actions="{ close }">
			<button class="btn-filled" @click="done(close)">{{ s.shelves.done }}</button>
		</template>
	</ModalDialog>
</template>
