/**
 * The theme's three colours as the phone gives them to its button groups (`OptionColor`). An
 * unselected button is in its colour's container and a selected one in the solid colour, so the
 * colours can be told apart in the light theme, where gold and leather are solid browns much alike.
 * Without a colour, a button is Material's own toggle: surface container, primary when selected.
 */
export type OptionColor = 'violet' | 'gold' | 'leather';

export function optionColorClasses(color: OptionColor | undefined, selected: boolean): string {
	switch (color) {
		case 'violet':
			return selected ? 'bg-primary text-on-primary' : 'bg-primary-container text-on-primary-container';
		case 'gold':
			return selected ? 'bg-secondary text-on-secondary' : 'bg-secondary-container text-on-secondary-container';
		case 'leather':
			return selected ? 'bg-tertiary text-on-tertiary' : 'bg-tertiary-container text-on-tertiary-container';
		default:
			return selected ? 'bg-primary text-on-primary' : 'bg-surface-container text-on-surface-variant';
	}
}

/** The colour's container, as a tonal icon button takes it (`iconButtonColors`). */
export function optionIconButtonClasses(color: OptionColor): string {
	return optionColorClasses(color, false);
}
