<script setup lang="ts">
/**
 * The names in [authors], separated by commas, as the phone's `AuthorLinks`. Those in [refs], the
 * ones the catalogue keeps a page for, are links to it; a click elsewhere is left to what is behind.
 */
withDefaults(defineProps<{ authors: string[]; refs?: { id: string; name: string }[] }>(), { refs: () => [] });
</script>

<template>
	<span>
		<template v-for="(name, index) in authors" :key="index"
			><template v-if="index > 0">, </template
			><RouterLink
				v-if="refs.find((ref) => ref.name === name)"
				:to="{ name: 'author', params: { id: refs.find((ref) => ref.name === name)!.id }, query: { name } }"
				class="text-primary hover:underline"
				@click.stop
				>{{ name }}</RouterLink
			><template v-else>{{ name }}</template></template
		>
	</span>
</template>
