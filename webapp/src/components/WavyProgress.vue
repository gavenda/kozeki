<script setup lang="ts">
import { LinearProgressIndicator } from 'tailwind-material3';

/**
 * Material's LinearWavyProgressIndicator. [tone] picks the colour: primary for the EPUB, tertiary for
 * paper, each on its own container for the track, as the phone draws them. The indicator draws in
 * primary on the secondary container, so those two roles are pointed at the tone's.
 */
withDefaults(defineProps<{ progress: number; tone?: 'primary' | 'tertiary' }>(), { tone: 'primary' });
</script>

<template>
	<LinearProgressIndicator
		:progress="progress"
		wavy
		:class="
			tone === 'primary'
				? '[--color-secondary-container:var(--color-primary-container)]'
				: '[--color-primary:var(--color-tertiary)] [--color-secondary-container:var(--color-tertiary-container)]'
		"
	/>
</template>
