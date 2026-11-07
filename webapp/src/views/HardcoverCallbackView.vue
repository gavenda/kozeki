<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import EmptyState from '@/components/EmptyState.vue';
import { completeHardcoverSignIn } from '@/lib/hardcover';
import { s } from '@/strings';

const router = useRouter();

onMounted(async () => {
	const accepted = await completeHardcoverSignIn(new URLSearchParams(window.location.search));
	await router.replace({ name: 'settings', query: accepted ? {} : { hardcover: 'rejected' } });
});
</script>

<template>
	<div class="flex min-h-dvh items-center justify-center">
		<EmptyState face="studious" :title="s.settings.hardcover" />
	</div>
</template>
