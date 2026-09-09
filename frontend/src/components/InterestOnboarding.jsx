import { useState } from 'react';
import './InterestOnboarding.css';

const INTERESTS = [
  { value: 'POLITICS', index: '01', title: 'Politics', note: 'Elections, policy and diplomacy' },
  { value: 'WAR', index: '02', title: 'Conflict', note: 'Wars, security and displacement' },
  { value: 'BUSINESS', index: '03', title: 'Business', note: 'Markets, industry and trade' },
  { value: 'TECHNOLOGY', index: '04', title: 'Technology', note: 'AI, science and digital power' },
  { value: 'HEALTH', index: '05', title: 'Health', note: 'Outbreaks, medicine and wellbeing' },
];

export default function InterestOnboarding({ displayName, onComplete }) {
  const [selected, setSelected] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const toggle = value => setSelected(current => current.includes(value)
    ? current.filter(item => item !== value)
    : [...current, value]);

  const complete = async categories => {
    setBusy(true);
    setError('');
    try {
      await onComplete(categories);
    } catch {
      setError('Could not save your radar. Try once more.');
      setBusy(false);
    }
  };

  return (
    <main className="interest-shell">
      <header><span>NEWSMAP</span><b>RADAR SETUP / 01</b></header>
      <section>
        <p className="interest-kicker"><i /> Welcome, {displayName}</p>
        <h1>What deserves<br />your attention?</h1>
        <p className="interest-copy">Choose a few starting signals. Your radar will learn from the events, places, and sources you follow later.</p>

        <div className="interest-grid">
          {INTERESTS.map(interest => {
            const active = selected.includes(interest.value);
            return (
              <button className={active ? 'active' : ''} onClick={() => toggle(interest.value)} key={interest.value}>
                <span>{interest.index}</span>
                <strong>{interest.title}</strong>
                <small>{interest.note}</small>
                <b>{active ? '✓' : '+'}</b>
              </button>
            );
          })}
        </div>

        {error && <div className="interest-error">{error}</div>}
        <div className="interest-actions">
          <button className="interest-skip" disabled={busy} onClick={() => complete([])}>Explore the world first</button>
          <button className="interest-continue" disabled={busy || selected.length === 0} onClick={() => complete(selected)}>
            {busy ? 'Building radar…' : `Build my radar${selected.length ? ` · ${selected.length}` : ''}`} <span>→</span>
          </button>
        </div>
      </section>
    </main>
  );
}
