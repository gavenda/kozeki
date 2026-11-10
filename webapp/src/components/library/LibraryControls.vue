<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue';
import DropdownMenu from '@/components/DropdownMenu.vue';
import {
	isDescending,
	LIBRARY_FILTERS,
	LIBRARY_SORTS,
	type BookDisplay,
	type LibraryFilter,
	type LibraryKind,
	type LibrarySort,
} from '@/lib/libraryView';
import { s } from '@/strings';
import { Button, Chip, IconButton, MenuItem } from 'tailwind-material3';

/**
 * The row over the library, as the phone's: the filter on the left, its chips folded under it, and
 * on the right what to show, how to lay it out and what order to put it in. Picking the sort that
 * is already chosen turns it the other way round.
 */
const props = defineProps<{
	filter: LibraryFilter;
	counts: Record<LibraryFilter, number>;
	kind: LibraryKind;
	display: BookDisplay;
	sort: LibrarySort;
	reversed: boolean;
}>();
const filtersOpen = defineModel<boolean>('filtersOpen', { required: true });
const emit = defineEmits<{
	'update:filter': [filter: LibraryFilter];
	'update:kind': [kind: LibraryKind];
	'update:display': [display: BookDisplay];
	sort: [sort: LibrarySort];
}>();

const KINDS = [
	{ kind: 'BOOKS' as const, icon: 'libraryBooksFill' as const },
	{ kind: 'SHELVES' as const, icon: 'shelvesFill' as const },
];
const kindIcon = () => KINDS.find((entry) => entry.kind === props.kind)!.icon;
</script>

<template>
	<div class="flex flex-col gap-2">
		<div class="flex items-center justify-between px-1">
			<Button
				variant="text"
				:class="filter === 'ALL' ? 'text-on-surface' : 'text-primary'"
				:aria-expanded="filtersOpen"
				:aria-label="s.library.filterBooks"
				@click="filtersOpen = !filtersOpen"
				><template #icon><AppIcon name="filterAltFill" /></template>
				<span class="type-title-small">{{ s.library.filter[filter] }}</span>
			</Button>
			<div class="flex items-center">
				<DropdownMenu>
					<template #trigger="{ toggle, props }">
						<IconButton
							v-bind="props"
							variant="standard"
							:label="s.library.kindChoose(s.library.kind[kind])"
							class="text-primary!"
							:title="s.library.kind[kind]"
							@click="toggle"
						>
							<AppIcon :name="kindIcon()" />
						</IconButton>
					</template>
					<MenuItem v-for="entry in KINDS" :key="entry.kind" :selected="entry.kind === kind" @click="emit('update:kind', entry.kind)">
						<template #icon><AppIcon :name="entry.icon" /></template>
						{{ s.library.kind[entry.kind] }}
						<template v-if="entry.kind === kind" #trailing-icon><AppIcon name="check" /></template>
					</MenuItem>
				</DropdownMenu>
				<IconButton
					variant="standard"
					:label="display === 'COVERS' ? s.library.displayAsList : s.library.displayAsCovers"
					:title="display === 'COVERS' ? s.library.displayAsList : s.library.displayAsCovers"
					@click="emit('update:display', display === 'COVERS' ? 'LIST' : 'COVERS')"
				>
					<AppIcon :name="display === 'COVERS' ? 'viewListFill' : 'gridViewFill'" />
				</IconButton>
				<DropdownMenu>
					<template #trigger="{ toggle, props }">
						<IconButton v-bind="props" variant="standard" :label="s.library.sortBooks" :title="s.library.sortBooks" @click="toggle">
							<AppIcon name="sort" />
						</IconButton>
					</template>
					<MenuItem
						v-for="option in LIBRARY_SORTS"
						:key="option"
						:selected="option === sort"
						:aria-description="option === sort ? (isDescending(sort, reversed) ? s.library.descending : s.library.ascending) : undefined"
						@click="emit('sort', option)"
					>
						<template #icon>
							<AppIcon v-if="option === sort" :name="isDescending(sort, reversed) ? 'arrowDownward' : 'arrowUpward'" />
							<span v-else></span>
						</template>
						{{ s.library.sort[option] }}
					</MenuItem>
				</DropdownMenu>
			</div>
		</div>
		<div v-show="filtersOpen" class="flex flex-wrap gap-2 px-4 pb-1">
			<Chip
				v-for="option in LIBRARY_FILTERS"
				:key="option"
				type="filter"
				:selected="option === filter"
				@click="emit('update:filter', option)"
			>
				{{ s.library.filter[option] }}
				<span class="ms-1.5 type-label-small">{{ counts[option] }}</span>
			</Chip>
		</div>
	</div>
</template>
