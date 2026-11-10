<script setup lang="ts">
import { useAuth0 } from '@auth0/auth0-vue';
import { useRoute } from 'vue-router';
import EmptyState from '@/components/EmptyState.vue';
import { s } from '@/strings';
import { Button } from 'tailwind-material3';

const { loginWithRedirect } = useAuth0();
const route = useRoute();

function signIn() {
	const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/';
	void loginWithRedirect({ appState: { target } });
}
</script>

<template>
	<div class="flex min-h-dvh items-center justify-center">
		<EmptyState face="neutral" :title="s.login.title" :message="s.login.message">
			<Button class="mt-2" @click="signIn">{{ s.login.action }}</Button>
		</EmptyState>
	</div>
</template>
