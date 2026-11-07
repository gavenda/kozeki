<script setup lang="ts">
/** Something Ui says, in a bubble with its tail on its start side, pointing back at her Portrait. */
</script>

<template>
	<div class="relative ps-2">
		<svg class="absolute start-0 top-1/2 -mt-2 size-4 rtl:-scale-x-100" viewBox="0 0 16 16" aria-hidden="true">
			<path d="M8 0 0 8 8 16Z" class="fill-surface-container-high" />
		</svg>
		<p class="rounded-[20px] bg-surface-container-high px-4 py-3 type-body-medium text-on-surface">
			<slot />
		</p>
	</div>
</template>
