<script setup lang="ts" generic="T extends string | number">
import { computed } from 'vue';
import { Button, ButtonGroup, IconButton } from 'tailwind-material3';
import { optionColorClasses, type OptionColor } from '@/lib/optionColor';
import type { IconName } from '@/lib/icons';
import AppIcon from './AppIcon.vue';

export interface ButtonOption<V> {
	value: V;
	label: string;
	icon?: IconName;
	/** The icon alone, as wide as it takes, its label read out instead. */
	iconOnly?: boolean;
	color?: OptionColor;
}

/**
 * A single-choice row of toggle buttons, as the phone's `ConnectedButtonGroup` and
 * `StandardButtonGroup`, on Material's ButtonGroup. Connected buttons join, 2px apart; standard
 * ones stand apart and turn square when chosen. The options share the width. A [color] (or an
 * option's own) replaces Material's toggle colours, as the phone's `OptionColor` does.
 */
const props = withDefaults(
	defineProps<{ options: ButtonOption<T>[]; modelValue: T; connected?: boolean; color?: OptionColor; disabled?: boolean }>(),
	{ connected: false },
);
const emit = defineEmits<{ 'update:modelValue': [value: T] }>();

const model = computed({
	get: () => props.modelValue as string | number,
	set: (value) => {
		if (value != null) emit('update:modelValue', value as T);
	},
});

/** The option's colours, made to win over the button's own. */
function colorClasses(option: ButtonOption<T>): string | undefined {
	const color = option.color ?? props.color;
	if (!color) return undefined;
	return optionColorClasses(color, option.value === props.modelValue)
		.split(' ')
		.map((name) => `${name}!`)
		.join(' ');
}
</script>

<template>
	<ButtonGroup v-model="model" :variant="connected ? 'connected' : 'standard'" selection="single" required class="w-full">
		<template v-for="option in options" :key="String(option.value)">
			<IconButton
				v-if="option.iconOnly"
				:value="option.value"
				:label="option.label"
				:disabled="disabled"
				width="wide"
				:class="colorClasses(option)"
			>
				<AppIcon :name="option.icon!" />
			</IconButton>
			<Button v-else :value="option.value" :disabled="disabled" class="min-w-0 flex-1 shrink!" :class="colorClasses(option)">
				<template v-if="option.icon" #icon><AppIcon :name="option.icon" /></template>
				<span class="truncate">{{ option.label }}</span>
			</Button>
		</template>
	</ButtonGroup>
</template>
