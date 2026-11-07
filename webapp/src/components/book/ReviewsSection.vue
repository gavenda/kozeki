<script setup lang="ts">
import { ref, watch } from 'vue';
import ExpandableText from '@/components/ExpandableText.vue';
import HaloMark from '@/components/HaloMark.vue';
import RatingBar from '@/components/RatingBar.vue';
import { lookupMessage, providerFor, type BookReviews } from '@/lib/catalogue';
import { formatDay } from '@/lib/format';
import { s } from '@/strings';

/**
 * What other readers wrote of the book on Hardcover, as on the phone: its average and counts, then
 * the most liked reviews, three at first. One with spoilers stays folded until asked for.
 */
const props = defineProps<{ sourceId: string }>();
const PREVIEW = 3;
const reviews = ref<BookReviews | null>(null);
const error = ref<string | null>(null);
const showingAll = ref(false);
const unfolded = ref(new Set<string>());

async function load() {
	reviews.value = null;
	error.value = null;
	try {
		reviews.value = await providerFor('HARDCOVER').reviews!(props.sourceId);
	} catch (e) {
		error.value = lookupMessage(e);
	}
}
watch(() => props.sourceId, load, { immediate: true });
const dayOf = (date: string) => Math.floor(Date.parse(`${date}T00:00:00Z`) / 86_400_000);
</script>

<template>
	<section class="flex flex-col gap-3">
		<div class="flex flex-col gap-1">
			<h2 class="section-title">{{ s.reviews.title }}</h2>
			<div
				v-if="reviews && (reviews.averageRating != null || reviews.ratingsCount)"
				class="flex flex-wrap items-center gap-2 type-body-medium text-on-surface-variant"
			>
				<template v-if="reviews.averageRating != null">
					<span class="type-title-medium-em text-on-surface">{{ s.reviews.average(reviews.averageRating) }}</span>
					<RatingBar :rating="Math.round(reviews.averageRating * 2) / 2" :size="18" />
				</template>
				<span>{{ [s.reviews.ratingsCount(reviews.ratingsCount), s.reviews.reviewsCount(reviews.reviewsCount)].join(' · ') }}</span>
			</div>
		</div>
		<div v-if="!reviews && !error" class="flex justify-center py-4"><HaloMark loading :size="32" /></div>
		<div v-else-if="error" class="flex items-center gap-2">
			<p class="flex-1 type-body-medium text-on-surface-variant">{{ error }}</p>
			<button class="btn-text" @click="load">{{ s.action.retry }}</button>
		</div>
		<p v-else-if="reviews!.reviews.length === 0" class="type-body-medium text-on-surface-variant">{{ s.reviews.empty }}</p>
		<template v-else>
			<article
				v-for="review in showingAll ? reviews!.reviews : reviews!.reviews.slice(0, PREVIEW)"
				:key="review.id"
				class="card flex flex-col gap-2 p-4"
			>
				<div class="flex items-center gap-2">
					<p class="min-w-0 flex-1 truncate type-title-small">{{ review.reviewer ?? s.reviews.anonymous }}</p>
					<RatingBar v-if="review.rating != null" :rating="review.rating" :size="16" />
				</div>
				<div v-if="review.hasSpoilers && !unfolded.has(review.id)" class="flex items-center gap-2">
					<p class="flex-1 type-body-medium text-on-surface-variant">{{ s.reviews.spoilers }}</p>
					<button class="btn-text" @click="unfolded = new Set([...unfolded, review.id])">{{ s.reviews.spoilersShow }}</button>
				</div>
				<ExpandableText v-else :text="review.text" :lines="6" />
				<p class="type-body-small text-on-surface-variant">
					{{
						[review.reviewedOn ? formatDay(dayOf(review.reviewedOn)) : null, review.likes ? s.reviews.likes(review.likes) : null]
							.filter(Boolean)
							.join(' · ')
					}}
				</p>
			</article>
			<button v-if="!showingAll && reviews!.reviews.length > PREVIEW" class="btn-text self-start" @click="showingAll = true">
				{{ s.reviews.showAll(reviews!.reviews.length) }}
			</button>
		</template>
	</section>
</template>
