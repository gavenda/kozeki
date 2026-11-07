<script setup lang="ts" generic="T extends string | number">
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
 * `StandardButtonGroup`. Connected buttons join with 2px between them, square on the inside and round
 * on the outside, in one colour; standard ones stand apart, round, square when chosen, and take a
 * colour each. The options share the width.
 */
withDefaults(defineProps<{ options: ButtonOption<T>[]; modelValue: T; connected?: boolean; color?: OptionColor; disabled?: boolean }>(), {
	connected: false,
});
const emit = defineEmits<{ 'update:modelValue': [value: T] }>();
</script>

<template>
	<div class="flex" :class="connected ? 'gap-0.5' : 'gap-2'" role="radiogroup">
		<button
			v-for="(option, index) in options"
			:key="String(option.value)"
			type="button"
			role="radio"
			:aria-checked="option.value === modelValue"
			:aria-label="option.iconOnly ? option.label : undefined"
			:disabled="disabled"
			class="flex h-10 min-w-0 cursor-pointer items-center justify-center gap-2 type-label-large transition-[border-radius,filter] duration-200 hover:brightness-110 disabled:cursor-default disabled:opacity-40 disabled:hover:brightness-100"
			:class="[
				option.iconOnly ? 'px-4' : 'flex-1 px-1',
				optionColorClasses(option.color ?? color, option.value === modelValue),
				connected
					? option.value === modelValue
						? 'rounded-full'
						: [index === 0 ? 'rounded-s-full' : 'rounded-s-sm', index === options.length - 1 ? 'rounded-e-full' : 'rounded-e-sm']
					: option.value === modelValue
						? 'rounded-xl'
						: 'rounded-full',
			]"
			@click="emit('update:modelValue', option.value)"
		>
			<AppIcon v-if="option.icon" :name="option.icon" :size="20" />
			<span v-if="!option.iconOnly" class="truncate">{{ option.label }}</span>
		</button>
	</div>
</template>
