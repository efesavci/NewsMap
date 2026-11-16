// A browser-local preview, isolated from the real account and pipeline APIs.
import axios from 'axios';
export const previewMode = import.meta.env.VITE_PREVIEW_MODE === 'true';
let feed;
const key = 'newsmap-preview-v1';
const fresh = () => ({ userId: 'preview', displayName: 'Guest', onboardingComplete: false, follows: [], savedEvents: [], reviews: [], preferences: { deliveryEnabled: false, minimumPriority: 'NORMAL', quietHoursEnabled: false, quietStart: '22:00', quietEnd: '08:00', timezone: 'Europe/Berlin' } });
let state;
try { state = JSON.parse(localStorage.getItem(key)) || fresh(); } catch { state = fresh(); }
state.savedEvents ??= [];
const snapshotFeed = () => {
  feed ??= fetch('/preview/events.json').then(response => {
    if (!response.ok) throw new Error('Preview snapshot unavailable');
    return response.json();
  });
  return feed;
};
const save = () => localStorage.setItem(key, JSON.stringify(state));
const account = () => ({ userId: 'preview', displayName: 'Guest' });
const matches = (event, follow) => follow.type === 'EVENT' ? event.id === follow.value
  : follow.type === 'CATEGORY' ? event.category === follow.value
  : follow.type === 'LOCATION' ? event.location === follow.value
  : follow.type === 'SOURCE' ? event.sources.includes(follow.value) : false;

if (previewMode) {
  const networkAdapter = axios.getAdapter(axios.defaults.adapter);
  axios.defaults.adapter = async config => {
    if (!config.url.startsWith('/api/')) return networkAdapter(config);
    const url = new URL(config.url, location.origin);
    const path = url.pathname;
    const method = config.method || 'get';
    const body = typeof config.data === 'string' ? JSON.parse(config.data) : config.data || {};
    let data;
    if (path === '/api/auth/csrf') data = { headerName: 'X-Preview', token: 'local-preview' };
    else if (path === '/api/auth/me') data = account();
    else if (path === '/api/auth/logout') { const savedEvents = state.savedEvents; state = { ...fresh(), savedEvents }; save(); data = {}; }
    else if (path === '/api/notifications' || path.startsWith('/api/notifications/')) data = { notifications: [], unreadCount: 0 };
    else if (path === '/api/event-quality/feedback') { state.reviews.push(body); save(); data = {}; }
    else if (/^\/api\/users\/preview\/saved-events(?:\/|$)/.test(path)) {
      const eventId = decodeURIComponent(path.split('/')[5] || '');
      if (method === 'put') {
        const snapshot = await snapshotFeed();
        const event = snapshot.events.find(item => item.id === eventId);
        if (!event) throw new Error('Event not found');
        if (!state.savedEvents.some(item => item.eventId === eventId))
          state.savedEvents.unshift({ eventId, savedAt: new Date().toISOString(), archived: false, event: structuredClone(event) });
      } else if (method === 'patch') {
        state.savedEvents = state.savedEvents.map(item => item.eventId === eventId ? { ...item, archived: body.archived } : item);
      } else if (method === 'delete') state.savedEvents = state.savedEvents.filter(item => item.eventId !== eventId);
      save(); data = state.savedEvents;
    }
    else if (path.startsWith('/api/users/')) {
      if (path.endsWith('/onboarding')) {
        state.onboardingComplete = true;
        state.follows = body.categories.map(value => ({ type: 'CATEGORY', value, notificationLevel: 'NORMAL' }));
      } else if (path.endsWith('/follows')) {
        if (!state.follows.some(f => f.type === body.type && f.value === body.value)) state.follows.push({ ...body, notificationLevel: 'NORMAL' });
      } else if (path.endsWith('/follows/notification-level')) {
        state.follows = state.follows.map(f => f.type === body.type && f.value === body.value ? { ...f, notificationLevel: body.notificationLevel } : f);
      } else if (path.endsWith('/notification-preferences')) {
        if (method === 'put') state.preferences = body;
        data = state.preferences;
      }
      save();
      data ??= state;
    } else if (path === '/api/events' || path.startsWith('/api/events/') || path === '/api/radar') {
      const snapshot = await snapshotFeed();
      if (path === '/api/radar') data = { generatedAt: snapshot.generatedAt, items: snapshot.events.flatMap(event => {
        const follows = state.follows.filter(f => matches(event, f));
        return follows.length ? [{ event, score: follows.length, reasons: follows.map(f => `Following ${f.value}`) }] : []; }) };
      else if (path === '/api/events') data = snapshot;
      else data = snapshot.events.find(event => event.id === decodeURIComponent(path.split('/').pop()));
    } else throw new Error(`Unsupported preview action: ${path}`);
    return { data, status: 200, statusText: 'OK', headers: {}, config };
  };
}
