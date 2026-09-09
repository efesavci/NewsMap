import { useCallback, useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import './EnginePanel.css';

const shortSource = source => {
  try { return new URL(source).hostname.replace(/^www\./, ''); } catch { return source; }
};

const elapsed = milliseconds => {
  if (!milliseconds) return '—';
  if (milliseconds < 1000) return `${milliseconds}ms`;
  const seconds = Math.round(milliseconds / 1000);
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
};

const timeLabel = value => value
  ? new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date(value))
  : 'Running';

export default function EnginePanel({ open, onClose }) {
  const [runs, setRuns] = useState([]);
  const [error, setError] = useState('');
  const [expanded, setExpanded] = useState('');

  const load = useCallback(async () => {
    try {
      const response = await axios.get('/api/pipeline/runs?limit=30');
      setRuns(response.data || []);
      setError('');
    } catch {
      setError('Pipeline history is temporarily unavailable.');
    }
  }, []);

  useEffect(() => {
    if (!open) return undefined;
    const initial = window.setTimeout(load, 0);
    const interval = window.setInterval(load, 15_000);
    return () => {
      window.clearTimeout(initial);
      window.clearInterval(interval);
    };
  }, [load, open]);

  const totals = useMemo(() => runs.reduce((sum, run) => ({
    fresh: sum.fresh + run.newArticles,
    skipped: sum.skipped + run.knownSkipped,
    failed: sum.failed + run.failedArticles,
  }), { fresh: 0, skipped: 0, failed: 0 }), [runs]);

  if (!open) return null;

  return (
    <aside className="engine-panel" aria-label="Pipeline health">
      <header className="engine-heading">
        <div>
          <span>ENGINE / LIVE</span>
          <h2>Pipeline health</h2>
        </div>
        <button type="button" onClick={event => { event.stopPropagation(); onClose(); }} aria-label="Close pipeline health">×</button>
      </header>

      <div className="engine-summary">
        <div><span>New</span><strong>{totals.fresh}</strong></div>
        <div><span>Deduped</span><strong>{totals.skipped}</strong></div>
        <div><span>Failed</span><strong className={totals.failed ? 'warn' : ''}>{totals.failed}</strong></div>
      </div>

      <div className="engine-legend"><span>Recent scheduled runs</span><i /> Updates every 15 seconds</div>
      {error && <div className="engine-error">{error}</div>}
      {!error && runs.length === 0 && <div className="engine-empty">The next fetch will create the first measured run.</div>}

      <div className="engine-runs">
        {runs.map(run => {
          const isExpanded = expanded === run.runId;
          return (
            <article className={`engine-run ${run.status.toLowerCase()}`} key={run.runId}>
              <button className="engine-run-main" onClick={() => setExpanded(isExpanded ? '' : run.runId)}>
                <i />
                <span className="engine-run-time">{timeLabel(run.startedAt)}</span>
                <span className="engine-run-result">
                  <strong>{run.status.replace('_', ' ')}</strong>
                  <small>{run.newArticles} new · {run.knownSkipped} known · {elapsed(run.durationMs)}</small>
                </span>
                <b>{isExpanded ? '−' : '+'}</b>
              </button>
              {isExpanded && (
                <div className="engine-run-detail">
                  <div className="engine-event-delta">
                    <span>Events <b>{run.eventsBefore} → {run.eventsAfter}</b></span>
                    <span>Reports <b>{run.eventArticlesBefore} → {run.eventArticlesAfter}</b></span>
                    <span>Ledger <b>{run.ledgerTotal}</b></span>
                  </div>
                  {run.error && <p>{run.error}</p>}
                  <div className="engine-sources">
                    {(run.sources || []).map(source => (
                      <div key={source.source}>
                        <strong>{shortSource(source.source)}</strong>
                        <span>{source.newArticles} new</span>
                        <span>{source.knownSkipped} skipped</span>
                        <span className={source.failedArticles ? 'warn' : ''}>{source.failedArticles} failed</span>
                        <small>{elapsed(source.durationMs)}</small>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </article>
          );
        })}
      </div>
    </aside>
  );
}
