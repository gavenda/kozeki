import { useStorage } from '@vueuse/core';

/** Whether the wide window's navigation rail shows its labels. One ref, shared by the rail and its action. */
export const railExpanded = useStorage('kozeki.rail.expanded', false);
