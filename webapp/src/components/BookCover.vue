<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { coverInAccount, coverUrl } from '@/lib/covers';
import CoverPlaceholder from './CoverPlaceholder.vue';

/**
 * A book's cover, falling back to a generated one that shows the title. The fallback is always
 * drawn underneath, so a cover that fails to load never leaves a blank box. The corners are the
 * caller's, as the phone's shape parameter: rounded-xl unless told otherwise.
 */
const props = withDefaults(
	defineProps<{
		book: { title: string; coverFile?: string; coverUrl?: string; customCover?: boolean; fileHash?: string };
		showTitle?: boolean;
		rounded?: string;
	}>(),
	{ showTitle: true, rounded: 'rounded-xl' },
);

// The account's own copy of the cover first, then the catalogue's, as the phone does. A catalogue
// picture is never in the account, so it is not asked for there.
const src = computed(() => {
	if (!coverInAccount(props.book)) return props.book.coverUrl ?? null;
	const own = coverUrl(props.book.coverFile);
	if (own) return own;
	if (own === undefined) return null;
	return props.book.coverUrl ?? null;
});
const failed = ref(false);
watch(src, () => (failed.value = false));
</script>

<template>
	<div class="relative aspect-[2/3] w-full overflow-hidden" :class="rounded">
		<CoverPlaceholder :title="book.title" :show-title="showTitle" />
		<img
			v-if="src && !failed"
			:src="src"
			alt=""
			class="absolute inset-0 size-full object-cover"
			loading="lazy"
			referrerpolicy="no-referrer"
			@error="failed = true"
		/>
	</div>
</template>
