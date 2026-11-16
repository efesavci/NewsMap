import { useState } from 'react';
import { previewMode } from '../previewApi';
import './SavedLibrary.css';

export default function SavedLibrary({ items, onClose, onOpen, onArchive, onRemove }) {
  const [archived, setArchived] = useState(false);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const act = async (id, action) => {
    setBusy(id); setError('');
    try { await action(); } catch { setError('Could not update your library. Please try again.'); }
    finally { setBusy(''); }
  };
  const visible = items.filter(item => item.archived === archived);
  return <aside className="saved-library" aria-label="Saved news library">
    <header><div><small>YOUR LIBRARY · NO EXPIRY</small><h2>Saved news</h2></div><button onClick={onClose} aria-label="Close saved news">×</button></header>
    <p>Keep stories after they leave the 48-hour map. Saved copies preserve the reports available when you saved them.</p>
    {previewMode && <p className="library-note">Preview: stored in this browser only. Clearing browser data removes these saves.</p>}
    <nav aria-label="Library folders">
      <button className={!archived ? 'active' : ''} onClick={() => setArchived(false)}>Saved · {items.filter(i => !i.archived).length}</button>
      <button className={archived ? 'active' : ''} onClick={() => setArchived(true)}>Archive · {items.filter(i => i.archived).length}</button>
    </nav>
    {error && <p role="alert">{error}</p>}
    {!visible.length && <div className="library-empty">{archived ? 'Your archive is empty.' : 'Open an event on the globe and choose Save story.'}</div>}
    {visible.map(item => <article key={item.eventId}>
      <small>{item.event.category} · {item.event.location}</small>
      <button className="library-title" onClick={() => onOpen(item.event)}>{item.event.title}</button>
      <p>{item.event.articles?.length || 0} reports · Saved {new Date(item.savedAt).toLocaleDateString()}</p>
      <div className="library-actions">
        <button disabled={busy === item.eventId} onClick={() => act(item.eventId, () => onArchive(item.eventId, !archived))}>{archived ? 'Restore to saved' : 'Archive'}</button>
        <button disabled={busy === item.eventId} onClick={() => act(item.eventId, () => onRemove(item.eventId))}>Unsave</button>
      </div>
    </article>)}
  </aside>;
}
