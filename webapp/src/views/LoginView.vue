<script setup lang="ts">
import { useAuth0 } from '@auth0/auth0-vue';
import { useRoute } from 'vue-router';
import EmptyState from '@/components/EmptyState.vue';
import { s } from '@/strings';

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
			<button class="btn-filled mt-2" @click="signIn">{{ s.login.action }}</button>
		</EmptyState>
	</div>
</template>
