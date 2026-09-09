import { useState, useEffect, useMemo, useCallback } from 'react';
import axios from 'axios';
import NewsGlobe from './components/NewsGlobe';
import HotspotSidebar from './components/HotspotSidebar';
import TimelineSlider from './components/TimelineSlider';
import CategoryFilters from './components/CategoryFilters';
import NotificationPanel from './components/NotificationPanel';
import AuthScreen from './components/AuthScreen';
import EnginePanel from './components/EnginePanel';
import InterestOnboarding from './components/InterestOnboarding';

const API_BASE = '';
axios.defaults.withCredentials = true;

function eventToHotspot(event, radar = {}) {
  return {
    eventId: event.id,
    eventTitle: event.title,
    lat: event.lat,
    lon: event.lon,
    location: event.location,
    locationConfidence: event.locationConfidence,
    locationMethod: event.locationMethod,
    category: event.category || 'OTHER',
    confidence: event.confidence,
    sources: event.sources,
    updates: event.updates || [],
    radarScore: radar.score,
    radarReasons: radar.reasons || [],
    articles: (event.articles || []).map(article => ({
      ...article,
      timestamp: Date.parse(article.publishTime),
    })),
  };
}

function App() {
  const [account, setAccount] = useState(undefined);
  const [allHotspots, setAllHotspots] = useState([]);
  const [selectedHotspot, setSelectedHotspot] = useState(null);
  const [hoursRange, setHoursRange] = useState(48);
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [viewMode, setViewMode] = useState('WORLD');
  const [refreshKey, setRefreshKey] = useState(0);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [inboxOpen, setInboxOpen] = useState(false);
  const [notificationPreferences, setNotificationPreferences] = useState(null);
  const [profile, setProfile] = useState(null);
  const [engineOpen, setEngineOpen] = useState(false);
  const userId = account?.userId;

  useEffect(() => {
    axios.get(`${API_BASE}/api/auth/csrf`)
      .then(response => {
        axios.defaults.headers.common[response.data.headerName] = response.data.token;
        return axios.get(`${API_BASE}/api/auth/me`);
      })
      .then(response => setAccount(response.data))
      .catch(error => {
        if (error.response?.status !== 401) console.error('Error restoring account session:', error);
        setAccount(null);
      });
  }, []);

  const loadInbox = useCallback(async () => {
    if (!userId) return;
    try {
      const response = await axios.get(`${API_BASE}/api/notifications?userId=${encodeURIComponent(userId)}`);
      setNotifications(response.data.notifications || []);
      setUnreadCount(response.data.unreadCount || 0);
    } catch (error) {
      console.error('Error fetching notifications:', error);
    }
  }, [userId]);

  const loadNotificationPreferences = useCallback(async () => {
    if (!userId) return;
    try {
      const response = await axios.get(`${API_BASE}/api/users/${encodeURIComponent(userId)}/notification-preferences`);
      setNotificationPreferences(response.data);
    } catch (error) {
      console.error('Error fetching notification preferences:', error);
    }
  }, [userId]);

  const loadProfile = useCallback(async () => {
    if (!userId) return;
    try {
      const response = await axios.get(`${API_BASE}/api/users/${encodeURIComponent(userId)}`);
      setProfile(response.data);
    } catch (error) {
      console.error('Error fetching profile:', error);
    }
  }, [userId]);

  const loadEvents = useCallback(() => {
    // Persistent events are now the product model. Spatial clustering below is
    // display-only and never changes an event's stable identity.
    if (viewMode === 'RADAR' && !userId) return;
    const request = viewMode === 'RADAR'
      ? axios.get(`${API_BASE}/api/radar?userId=${encodeURIComponent(userId)}&limit=250`)
      : axios.get(`${API_BASE}/api/events?status=ACTIVE&minArticles=1`);
    request
      .then(response => {
        const events = viewMode === 'RADAR'
          ? (response.data.items || []).map(item => eventToHotspot(item.event, item))
          : (response.data.events || []).map(event => eventToHotspot(event));
        setAllHotspots(events);
      })
      .catch(error => {
        console.error('Error fetching persistent events:', error);
      });
  }, [viewMode, userId]);

  useEffect(() => loadEvents(), [loadEvents, refreshKey]);

  useEffect(() => {
    if (!userId) return undefined;
    const initialLoad = window.setTimeout(() => {
      loadInbox();
      loadNotificationPreferences();
      loadProfile();
    }, 0);
    const interval = window.setInterval(loadInbox, 60_000);
    return () => {
      window.clearTimeout(initialLoad);
      window.clearInterval(interval);
    };
  }, [loadInbox, loadNotificationPreferences, loadProfile, userId]);

  const saveNotificationPreferences = useCallback(async preferences => {
    const {
      deliveryEnabled,
      minimumPriority,
      quietHoursEnabled,
      quietStart,
      quietEnd,
      timezone,
    } = preferences;
    const response = await axios.put(
      `${API_BASE}/api/users/${encodeURIComponent(userId)}/notification-preferences`,
      { deliveryEnabled, minimumPriority, quietHoursEnabled, quietStart, quietEnd, timezone }
    );
    setNotificationPreferences(response.data);
    return response.data;
  }, [userId]);

  const follow = useCallback(async (type, value) => {
    const response = await axios.post(`${API_BASE}/api/users/${encodeURIComponent(userId)}/follows`, { type, value });
    setProfile(response.data);
    setViewMode('RADAR');
    setRefreshKey(key => key + 1);
    await loadInbox();
  }, [loadInbox, userId]);

  const updateFollowNotificationLevel = useCallback(async (type, value, notificationLevel) => {
    const response = await axios.put(`${API_BASE}/api/users/${encodeURIComponent(userId)}/follows/notification-level`, {
      type,
      value,
      notificationLevel,
    });
    setProfile(response.data);
    return response.data;
  }, [userId]);

  const submitQualityFeedback = useCallback(async (eventId, articleId, verdict) => {
    await axios.post(`${API_BASE}/api/event-quality/feedback`, { eventId, articleId, verdict });
  }, []);

  const openNotification = useCallback(async notification => {
    const response = await axios.post(
      `${API_BASE}/api/notifications/${notification.notificationId}/read?userId=${encodeURIComponent(userId)}`
    );
    setNotifications(response.data.notifications || []);
    setUnreadCount(response.data.unreadCount || 0);
    setInboxOpen(false);

    const visible = allHotspots.find(event => event.eventId === notification.eventId);
    if (visible) {
      setSelectedHotspot(visible);
      return;
    }
    try {
      const eventResponse = await axios.get(`${API_BASE}/api/events/${notification.eventId}`);
      setSelectedHotspot(eventToHotspot(eventResponse.data));
    } catch (error) {
      console.error('Could not open notification event:', error);
    }
  }, [allHotspots, userId]);

  const markAllRead = useCallback(async () => {
    const response = await axios.post(`${API_BASE}/api/notifications/read-all?userId=${encodeURIComponent(userId)}`);
    setNotifications(response.data.notifications || []);
    setUnreadCount(0);
  }, [userId]);

  const signOut = useCallback(async () => {
    await axios.post(`${API_BASE}/api/auth/logout`);
    const csrf = await axios.get(`${API_BASE}/api/auth/csrf`);
    axios.defaults.headers.common[csrf.data.headerName] = csrf.data.token;
    setAccount(null);
    setProfile(null);
    setNotifications([]);
    setUnreadCount(0);
  }, []);

  const completeOnboarding = useCallback(async categories => {
    const response = await axios.post(
      `${API_BASE}/api/users/${encodeURIComponent(userId)}/onboarding`,
      { categories }
    );
    setProfile(response.data);
    if (categories.length) setViewMode('RADAR');
    setRefreshKey(key => key + 1);
  }, [userId]);

  // Filter hotspots dynamically based on the slider and category
  const filteredHotspots = useMemo(() => {
    if (allHotspots.length === 0) return [];
    
    // Find the newest article timestamp to act as "Now"
    let latestTimestamp = 0;
    allHotspots.forEach(h => {
        h.articles.forEach(a => {
            if (a.timestamp > latestTimestamp) latestTimestamp = a.timestamp;
        });
    });
    
    const cutoffTime = latestTimestamp - (hoursRange * 60 * 60 * 1000);

    return allHotspots
      .map(hotspot => {
        // Filter articles inside the hotspot by time
        const validArticles = hotspot.articles.filter(a => a.timestamp >= cutoffTime);
        return { ...hotspot, articles: validArticles };
      })
      .filter(hotspot => {
        // Only keep hotspots that have at least one valid article
        if (hotspot.articles.length === 0) return false;
        // (0,0) is the backend sentinel for an unresolved/global story, not a
        // valid map position. Never render it in the Gulf of Guinea.
        if (hotspot.locationMethod === 'unresolved' || hotspot.lat == null || hotspot.lon == null ||
            (hotspot.lat === 0 && hotspot.lon === 0)) return false;
        // Filter by category
        if (selectedCategory !== 'ALL' && hotspot.category !== selectedCategory) return false;
        return true;
      });
  }, [allHotspots, hoursRange, selectedCategory]);

  if (account === undefined) {
    return <main className="auth-loading-screen"><i /><span>Resolving your NewsMap</span></main>;
  }

  if (account === null) {
    return <AuthScreen apiBase={API_BASE} onAuthenticated={setAccount} />;
  }

  if (profile === null) {
    return <main className="auth-loading-screen"><i /><span>Preparing your radar</span></main>;
  }

  if (!profile.onboardingComplete) {
    return <InterestOnboarding displayName={account.displayName} onComplete={completeOnboarding} />;
  }

  return (
    <div className="app-container">
      <div className="app-header">
        <div className="brand-block">
          <span className="brand-kicker"><i /> Live event atlas</span>
          <span className="app-logo">NEWSMAP</span>
          <span className="header-scope">
            {viewMode === 'RADAR' ? 'Personal radar' : 'World desk'} · {filteredHotspots.length} signals
          </span>
        </div>
        <div className="view-switch" aria-label="Map view">
          <button className={viewMode === 'WORLD' ? 'active' : ''} onClick={() => setViewMode('WORLD')}>
            <span>01</span> World
          </button>
          <button className={viewMode === 'RADAR' ? 'active' : ''} onClick={() => setViewMode('RADAR')}>
            <span>02</span> My Radar
          </button>
        </div>
        <button
          className={`notification-trigger ${inboxOpen ? 'active' : ''}`}
          onClick={() => setInboxOpen(open => !open)}
          aria-label={`${unreadCount} unread notifications`}
        >
          <span className="notification-label">Updates</span>
          <b>{unreadCount > 99 ? '99+' : unreadCount}</b>
        </button>
        <button className="account-trigger" onClick={signOut} title="Sign out">
          <span>{account.displayName}</span>
          <b>Sign out</b>
        </button>
        <button className="engine-trigger" onClick={() => setEngineOpen(open => !open)}>
          <span>Engine</span><b>Health →</b>
        </button>
      </div>

      <EnginePanel open={engineOpen} onClose={() => setEngineOpen(false)} />

      <NotificationPanel
        open={inboxOpen}
        notifications={notifications}
        unreadCount={unreadCount}
        preferences={notificationPreferences}
        follows={profile?.follows || []}
        onOpen={openNotification}
        onMarkAllRead={markAllRead}
        onSavePreferences={saveNotificationPreferences}
        onUpdateFollowLevel={updateFollowNotificationLevel}
      />

      <CategoryFilters 
        selectedCategory={selectedCategory} 
        setSelectedCategory={setSelectedCategory} 
      />

      <NewsGlobe 
        hotspots={filteredHotspots} 
        onSelectHotspot={(hotspot) => setSelectedHotspot(hotspot)} 
      />

      <TimelineSlider 
        hoursRange={hoursRange} 
        setHoursRange={setHoursRange} 
      />

      <HotspotSidebar 
        hotspot={selectedHotspot} 
        onClose={() => setSelectedHotspot(null)} 
        onFollow={follow}
        onQualityFeedback={submitQualityFeedback}
      />
    </div>
  );
}

export default App;
