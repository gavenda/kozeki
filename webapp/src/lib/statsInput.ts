import { computed } from 'vue';
import { useLibrary } from '@/stores/library';
import type { StatsInput } from './stats';

/** The rows the statistics are worked out from, kept current with the library. */
export function useStatsInput() {
	const library = useLibrary();
	return computed<StatsInput>(() => ({
		books: [...library.tables.books.values()],
		readThroughs: [...library.tables.readThroughs.values()],
		sessions: [...library.tables.readingSessions.values()],
		physical: [...library.tables.physicalReadings.values()],
	}));
}
