import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { auth0, whenAuthReady } from '@/lib/auth0';
import { s } from '@/strings';

const routes: RouteRecordRaw[] = [
	{ path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
	{ path: '/', name: 'library', component: () => import('@/views/LibraryView.vue'), meta: { title: s.nav.library } },
	{ path: '/books/:id', name: 'book', component: () => import('@/views/BookView.vue'), props: true },
	{ path: '/add', name: 'add', component: () => import('@/views/AddBookView.vue'), meta: { title: s.add.title } },
	{ path: '/calendar', name: 'calendar', component: () => import('@/views/CalendarView.vue'), meta: { title: s.nav.calendar } },
	{ path: '/statistics', name: 'statistics', component: () => import('@/views/StatisticsView.vue'), meta: { title: s.nav.statistics } },
	{ path: '/settings', name: 'settings', component: () => import('@/views/SettingsView.vue'), meta: { title: s.nav.settings } },
	// Where Hardcover sends the browser back to after its sign-in.
	{ path: '/hardcover', name: 'hardcover', component: () => import('@/views/HardcoverCallbackView.vue') },
	{ path: '/:pathMatch(.*)*', redirect: '/' },
];

export const router = createRouter({
	history: createWebHistory(),
	routes,
	scrollBehavior: (_to, _from, saved) => saved ?? { top: 0 },
});

router.beforeEach(async (to) => {
	// Until the session is restored, and an arriving code exchanged, everyone looks signed out.
	await whenAuthReady();
	if (!to.meta.public && !auth0.isAuthenticated.value) {
		return { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } };
	}
	if (to.name === 'login' && auth0.isAuthenticated.value) return { name: 'library' };
});

router.afterEach((to) => {
	document.title = to.meta.title ? `${to.meta.title as string} · ${s.appName}` : s.appName;
});
