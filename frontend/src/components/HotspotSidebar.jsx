import { useState } from 'react';

const percent = value => `${Math.round((value || 0) * 100)}%`;

function cleanSource(source = '') {
  return source.replace(/^www\./, '');
}

function confidenceLabel(value = 0) {
  if (value >= 0.9) return 'High confidence';
  if (value >= 0.82) return 'Good confidence';
  return 'Review suggested';
}

function EventGroup({ group, stacked, onFollow, onQualityFeedback }) {
  const reports = [...(group.articles || [])].sort((a, b) => a.timestamp - b.timestamp);
  const sources = [...new Set(reports.map(article => cleanSource(article.source)))];
  const [savedFollow, setSavedFollow] = useState('');
  const [reviewed, setReviewed] = useState({});
  const recentChanges = [...(group.updates || [])].reverse().slice(0, 4);

  const follow = async (type, value) => {
    await onFollow(type, value);
    setSavedFollow(`${type}:${value}`);
  };

  const review = async (articleId, verdict) => {
    await onQualityFeedback(group.eventId, articleId, verdict);
    setReviewed(current => ({ ...current, [articleId]: verdict }));
  };

  return (
    <section className="hotspot-group">
      {stacked && <h3 className="event-title">{group.eventTitle}</h3>}

      {group.radarReasons?.length > 0 && (
        <div className="radar-reasons">
          {group.radarReasons.map(reason => <span key={reason}>{reason}</span>)}
        </div>
      )}

      <div className="event-overview">
        <div className="event-stat">
          <strong>{reports.length}</strong>
          <span>{reports.length === 1 ? 'report' : 'reports'}</span>
        </div>
        <div className="event-stat">
          <strong>{sources.length}</strong>
          <span>{sources.length === 1 ? 'source' : 'sources'}</span>
        </div>
        <div className="event-stat event-confidence">
          <strong>{percent(group.confidence)}</strong>
          <span>{confidenceLabel(group.confidence)}</span>
        </div>
      </div>

      <div className="event-section-label">Source comparison</div>
      <div className="source-chips">
        {sources.map(source => <span className="source-chip" key={source}>{source}</span>)}
      </div>

      <div className="event-actions" aria-label="Follow event interests">
        <button onClick={() => follow('EVENT', group.eventId)}>
          {savedFollow === `EVENT:${group.eventId}` ? 'Following event' : 'Follow event'}
        </button>
        <button onClick={() => follow('CATEGORY', group.category)}>
          {savedFollow === `CATEGORY:${group.category}` ? `Following ${group.category}` : `+ ${group.category}`}
        </button>
        <button onClick={() => follow('LOCATION', group.location)}>
          {savedFollow === `LOCATION:${group.location}` ? `Following ${group.location}` : `+ ${group.location}`}
        </button>
      </div>

      {recentChanges.length > 0 && (
        <>
          <div className="event-section-label change-heading">What changed?</div>
          <div className="event-changes">
            {recentChanges.map(change => (
              <div key={change.updateId}>
                <i />
                <span>
                  <strong>{change.summary}</strong>
                  <small>{new Date(change.createdAt).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</small>
                </span>
              </div>
            ))}
          </div>
        </>
      )}

      <div className="event-section-label timeline-heading">Event timeline</div>
      <div className="event-timeline">
        {reports.map((article, index) => {
          const signals = article.matchSignals || {};
          const isSeed = signals.seed === 1;
          return (
            <article className="timeline-item" key={article.articleId || `${article.url}-${index}`}>
              <span className="timeline-dot" aria-hidden="true" />
              <div className="timeline-card">
                <div className="timeline-topline">
                  <span>{index === 0 ? 'FIRST REPORT' : 'UPDATE'}</span>
                  <time dateTime={article.publishTime}>
                    {new Date(article.timestamp).toLocaleString([], {
                      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
                    })}
                  </time>
                </div>
                <h4 className="article-title">
                  <a href={article.url} target="_blank" rel="noopener noreferrer">
                    {article.title}
                  </a>
                </h4>
                <div className="article-meta">
                  <span className="article-source">{cleanSource(article.source)}</span>
                  <span>{isSeed ? 'Event origin' : `${percent(article.matchScore)} event match`}</span>
                </div>
                {!isSeed && (
                  <>
                    <div className="match-evidence" aria-label="Event match evidence">
                      <span>Semantic {percent(signals.semantic)}</span>
                      {signals.lexical > 0 && <span>Wording {percent(signals.lexical)}</span>}
                      {signals.entities > 0 && <span>Entities {percent(signals.entities)}</span>}
                      <span>Recency {percent(signals.time)}</span>
                    </div>
                    <div className="match-review" aria-label="Review event match">
                      <span>Same event?</span>
                      <button className={reviewed[article.articleId] === 'SAME_EVENT' ? 'active' : ''} onClick={() => review(article.articleId, 'SAME_EVENT')}>Yes</button>
                      <button className={reviewed[article.articleId] === 'WRONG_EVENT' ? 'active warn' : ''} onClick={() => review(article.articleId, 'WRONG_EVENT')}>No</button>
                    </div>
                  </>
                )}
              </div>
            </article>
          );
        })}
      </div>

      {group.eventId && <div className="event-id">Stable ID · {group.eventId}</div>}
    </section>
  );
}

export default function HotspotSidebar({ hotspot, onClose, onFollow, onQualityFeedback }) {
  const isOpen = !!hotspot;
  const eventGroups = hotspot?.containedHotspots || [hotspot];
  const stacked = !!hotspot?.locationStack;

  return (
    <aside className={`sidebar ${isOpen ? 'open' : ''}`} aria-label="Event details">
      {hotspot && (
        <>
          <div className="sidebar-header">
            <div>
              <h2 className="sidebar-title">{stacked ? hotspot.location : hotspot.eventTitle}</h2>
              <div className="sidebar-category">
                {stacked ? `${hotspot.point_count} EVENTS HERE` : hotspot.category}
              </div>
              {!stacked && (
                <div className="event-location">
                  {hotspot.location} · {Math.round(hotspot.locationConfidence * 100)}% location confidence
                </div>
              )}
            </div>
            <button className="close-btn" onClick={onClose} aria-label="Close sidebar">
              <span aria-hidden="true">×</span>
            </button>
          </div>

          <div className="sidebar-content">
            {eventGroups.map(group => (
              <EventGroup group={group} stacked={stacked} onFollow={onFollow} onQualityFeedback={onQualityFeedback} key={group.eventId || group.eventTitle} />
            ))}
          </div>
        </>
      )}
    </aside>
  );
}
