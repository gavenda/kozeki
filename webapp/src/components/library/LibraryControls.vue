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
			<button
				class="btn-text h-10 gap-2 px-3"
				:class="filter === 'ALL' ? 'text-on-surface' : 'text-primary'"
				:aria-expanded="filtersOpen"
				:aria-label="s.library.filterBooks"
				@click="filtersOpen = !filtersOpen"
			>
				<AppIcon name="filterAltFill" />
				<span class="type-title-small">{{ s.library.filter[filter] }}</span>
			</button>
			<div class="flex items-center">
				<DropdownMenu>
					<template #trigger="{ toggle }">
						<button
							class="icon-btn text-primary"
							:aria-label="s.library.kindChoose(s.library.kind[kind])"
							:title="s.library.kind[kind]"
							@click="toggle"
						>
							<AppIcon :name="kindIcon()" />
						</button>
					</template>
					<template #default="{ close }">
						<button
							v-for="entry in KINDS"
							:key="entry.kind"
							class="menu-item"
							:class="{ 'text-primary': entry.kind === kind }"
							role="menuitemradio"
							:aria-checked="entry.kind === kind"
							@click="(emit('update:kind', entry.kind), close())"
						>
							<AppIcon :name="entry.icon" /><span class="flex-1">{{ s.library.kind[entry.kind] }}</span>
							<AppIcon v-if="entry.kind === kind" name="check" />
						</button>
					</template>
				</DropdownMenu>
				<button
					class="icon-btn"
					:aria-label="display === 'COVERS' ? s.library.displayAsList : s.library.displayAsCovers"
					:title="display === 'COVERS' ? s.library.displayAsList : s.library.displayAsCovers"
					@click="emit('update:display', display === 'COVERS' ? 'LIST' : 'COVERS')"
				>
					<AppIcon :name="display === 'COVERS' ? 'viewListFill' : 'gridViewFill'" />
				</button>
				<DropdownMenu>
					<template #trigger="{ toggle }">
						<button class="icon-btn" :aria-label="s.library.sortBooks" :title="s.library.sortBooks" @click="toggle">
							<AppIcon name="sort" />
						</button>
					</template>
					<template #default="{ close }">
						<button
							v-for="option in LIBRARY_SORTS"
							:key="option"
							class="menu-item"
							role="menuitemradio"
							:aria-checked="option === sort"
							:aria-description="option === sort ? (isDescending(sort, reversed) ? s.library.descending : s.library.ascending) : undefined"
							@click="(emit('sort', option), close())"
						>
							<span class="size-6">
								<AppIcon v-if="option === sort" :name="isDescending(sort, reversed) ? 'arrowDownward' : 'arrowUpward'" />
							</span>
							{{ s.library.sort[option] }}
						</button>
					</template>
				</DropdownMenu>
			</div>
		</div>
		<div v-show="filtersOpen" class="flex flex-wrap gap-2 px-4 pb-1">
			<button
				v-for="option in LIBRARY_FILTERS"
				:key="option"
				class="chip"
				:class="{ 'chip-on': option === filter }"
				:aria-pressed="option === filter"
				@click="emit('update:filter', option)"
			>
				<AppIcon v-if="option === filter" name="check" :size="18" />
				{{ s.library.filter[option] }}
				<span class="type-label-small">{{ counts[option] }}</span>
			</button>
		</div>
	</div>
</template>
