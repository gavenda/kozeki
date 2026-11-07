import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { auth0, HARDCOVER_CALLBACK, whenAuthReady } from '@/lib/auth0';
import { s } from '@/strings';

declare module 'vue-router' {
	interface RouteMeta {
		/** The tab of the navigation rail this page is; pages without one get the whole window. */
		tab?: 'library' | 'calendar' | 'statistics';
		title?: string;
		public?: boolean;
	}
}

const BookForm = () => import('@/views/BookFormView.vue');

const routes: RouteRecordRaw[] = [
	{ path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
	{ path: '/', name: 'library', component: () => import('@/views/LibraryView.vue'), meta: { tab: 'library', title: s.nav.library } },
	{
		path: '/calendar',
		name: 'calendar',
		component: () => import('@/views/CalendarView.vue'),
		meta: { tab: 'calendar', title: s.calendar.title },
	},
	{
		path: '/statistics',
		name: 'statistics',
		component: () => import('@/views/StatisticsView.vue'),
		meta: { tab: 'statistics', title: s.nav.statistics },
	},
	// One day's statistics, reached from the calendar, as the phone's DayRoute.
	{
		path: '/day/:date',
		name: 'day',
		component: () => import('@/views/StatisticsView.vue'),
		props: true,
		meta: { title: s.nav.statistics },
	},
	{ path: '/settings', name: 'settings', component: () => import('@/views/SettingsView.vue'), meta: { title: s.settings.title } },
	{ path: '/books/:id', name: 'book', component: () => import('@/views/BookView.vue'), props: true },
	{ path: '/books/:id/edit', name: 'bookEdit', component: BookForm, props: true, meta: { title: s.form.editTitle } },
	{ path: '/add', name: 'add', component: () => import('@/views/AddBookView.vue'), meta: { title: s.add.title } },
	{ path: '/add/manual', name: 'addManual', component: BookForm, meta: { title: s.form.manualTitle } },
	{ path: '/found/:source/:sourceId', name: 'found', component: () => import('@/views/FoundBookView.vue'), props: true },
	{ path: '/authors/:id', name: 'author', component: () => import('@/views/AuthorView.vue'), props: true },
	{ path: '/series/:name', name: 'series', component: () => import('@/views/SeriesView.vue'), props: true },
	// Where Hardcover sends the browser back to after its sign-in.
	{ path: HARDCOVER_CALLBACK, name: 'hardcover', component: () => import('@/views/HardcoverCallbackView.vue') },
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
	document.title = to.meta.title ? `${to.meta.title} · ${s.appName}` : s.appName;
});
