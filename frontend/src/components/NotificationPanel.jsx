import { useState } from 'react';

export default function NotificationPanel({
  open,
  notifications,
  unreadCount,
  preferences,
  follows,
  onOpen,
  onMarkAllRead,
  onSavePreferences,
  onUpdateFollowLevel,
}) {
  const [showSettings, setShowSettings] = useState(false);
  const [draft, setDraft] = useState(preferences);
  const [saveState, setSaveState] = useState('idle');
  const [savingFollow, setSavingFollow] = useState('');

  const activeDraft = draft || preferences;

  if (!open) return null;

  const updateDraft = (field, value) => setDraft(current => ({ ...(current || preferences), [field]: value }));

  const savePreferences = async event => {
    event.preventDefault();
    setSaveState('saving');
    try {
      const saved = await onSavePreferences(activeDraft);
      setDraft(saved);
      setSaveState('saved');
      window.setTimeout(() => setSaveState('idle'), 1500);
    } catch (error) {
      console.error('Could not save notification preferences:', error);
      setSaveState('error');
    }
  };

  const updateFollowLevel = async (follow, notificationLevel) => {
    const key = `${follow.type}:${follow.value}`;
    setSavingFollow(key);
    try {
      await onUpdateFollowLevel(follow.type, follow.value, notificationLevel);
    } finally {
      setSavingFollow('');
    }
  };

  if (showSettings) {
    return (
      <section className="notification-panel" aria-label="Notification settings">
        <div className="notification-panel-header">
          <div>
            <strong>Notification settings</strong>
            <span>Controls future push and email alerts</span>
          </div>
          <button onClick={() => setShowSettings(false)}>← Inbox</button>
        </div>
        {activeDraft ? (
          <form className="notification-settings" onSubmit={savePreferences}>
            <label className="notification-setting-toggle">
              <span>
                <strong>Delivery alerts</strong>
                <small>The in-app inbox stays complete when alerts are off.</small>
              </span>
              <input
                type="checkbox"
                checked={activeDraft.deliveryEnabled}
                onChange={event => updateDraft('deliveryEnabled', event.target.checked)}
              />
            </label>

            <label className="notification-setting-field">
              <span>Minimum priority</span>
              <select
                value={activeDraft.minimumPriority}
                onChange={event => updateDraft('minimumPriority', event.target.value)}
              >
                <option value="NORMAL">All meaningful updates</option>
                <option value="HIGH">Important changes only</option>
              </select>
            </label>

            <label className="notification-setting-toggle">
              <span>
                <strong>Quiet hours</strong>
                <small>Hold non-urgent delivery during this window.</small>
              </span>
              <input
                type="checkbox"
                checked={activeDraft.quietHoursEnabled}
                onChange={event => updateDraft('quietHoursEnabled', event.target.checked)}
              />
            </label>

            <div className="notification-time-fields">
              <label>
                From
                <input
                  type="time"
                  value={activeDraft.quietStart}
                  disabled={!activeDraft.quietHoursEnabled}
                  onChange={event => updateDraft('quietStart', event.target.value)}
                />
              </label>
              <label>
                Until
                <input
                  type="time"
                  value={activeDraft.quietEnd}
                  disabled={!activeDraft.quietHoursEnabled}
                  onChange={event => updateDraft('quietEnd', event.target.value)}
                />
              </label>
            </div>

            <span className="notification-timezone">Timezone: {activeDraft.timezone}</span>

            <div className="follow-alerts">
              <div className="follow-alerts-heading">
                <strong>Followed signals</strong>
                <span>Choose how much attention each follow receives.</span>
              </div>
              {follows.length === 0 ? (
                <span className="follow-alerts-empty">Follow an event, place, or category to tune it here.</span>
              ) : follows.map(follow => {
                const key = `${follow.type}:${follow.value}`;
                return (
                  <label className="follow-alert-row" key={key}>
                    <span>
                      <small>{follow.type}</small>
                      <strong>{follow.value}</strong>
                    </span>
                    <select
                      value={follow.notificationLevel || 'ALL'}
                      disabled={savingFollow === key}
                      onChange={event => updateFollowLevel(follow, event.target.value)}
                    >
                      <option value="ALL">All updates</option>
                      <option value="IMPORTANT">Important only</option>
                      <option value="MUTED">Muted</option>
                    </select>
                  </label>
                );
              })}
            </div>

            <button className="notification-save" type="submit" disabled={saveState === 'saving'}>
              {saveState === 'saving' ? 'Saving…' : saveState === 'saved' ? 'Saved' : 'Save preferences'}
            </button>
            {saveState === 'error' && <span className="notification-save-error">Could not save. Try again.</span>}
          </form>
        ) : (
          <div className="notification-empty"><span>Loading settings…</span></div>
        )}
      </section>
    );
  }

  return (
    <section className="notification-panel" aria-label="Notifications">
      <div className="notification-panel-header">
        <div>
          <strong>Event updates</strong>
          <span>{unreadCount} unread</span>
        </div>
        <div className="notification-header-actions">
          {unreadCount > 0 && (
            <button onClick={onMarkAllRead}>Read all</button>
          )}
          <button onClick={() => setShowSettings(true)}>Settings</button>
        </div>
      </div>

      <div className="notification-list">
        {notifications.length === 0 ? (
          <div className="notification-empty">
            <strong>No meaningful changes yet</strong>
            <span>Follow an event, place, or category. New event changes will appear here.</span>
          </div>
        ) : notifications.map(notification => (
          <button
            className={`notification-item ${notification.readAt ? '' : 'unread'}`}
            key={notification.notificationId}
            onClick={() => onOpen(notification)}
          >
            <span className="notification-unread-dot" aria-hidden="true" />
            <span className="notification-copy">
              <span className="notification-title-row">
                <strong>{notification.title}</strong>
                <em className={`notification-priority ${(notification.priority || 'NORMAL').toLowerCase()}`}>
                  {notification.priority === 'HIGH' ? 'Priority' : 'Standard'}
                </em>
              </span>
              <span>{notification.body}</span>
              <small>
                {new Date(notification.eventUpdatedAt).toLocaleString([], {
                  month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
                })}
              </small>
              <span className="notification-reason">{notification.reasons.join(' · ')}</span>
            </span>
          </button>
        ))}
      </div>
    </section>
  );
}
