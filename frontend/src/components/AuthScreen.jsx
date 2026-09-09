import { useState } from 'react';
import axios from 'axios';
import './AuthScreen.css';

export default function AuthScreen({ apiBase, onAuthenticated }) {
  const [mode, setMode] = useState('signup');
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = async event => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const submittedEmail = String(form.get('email') || '');
    const submittedPassword = String(form.get('password') || '');
    const submittedDisplayName = String(form.get('displayName') || '');
    setBusy(true);
    setError('');
    try {
      const endpoint = mode === 'signup' ? 'signup' : 'login';
      const payload = mode === 'signup'
        ? { displayName: submittedDisplayName, email: submittedEmail, password: submittedPassword }
        : { email: submittedEmail, password: submittedPassword };
      const response = await axios.post(`${apiBase}/api/auth/${endpoint}`, payload);
      onAuthenticated(response.data);
    } catch (requestError) {
      const status = requestError.response?.status;
      setError(
        requestError.response?.data?.message ||
        requestError.response?.data?.detail ||
        (status ? `Account request failed (${status}).` : 'Could not connect to your account.')
      );
    } finally {
      setBusy(false);
    }
  };

  const changeMode = nextMode => {
    setMode(nextMode);
    setError('');
  };

  return (
    <main className="auth-shell">
      <section className="auth-intro">
        <span className="auth-index">NM / 01</span>
        <div>
          <p className="auth-eyebrow"><i /> Your world, continuously resolved</p>
          <h1>Follow events.<br />Not the noise.</h1>
          <p className="auth-copy">
            NewsMap joins reporting into persistent events, then alerts you only when the story meaningfully changes.
          </p>
        </div>
        <div className="auth-principles">
          <span>Stable event identities</span>
          <span>Source comparison</span>
          <span>Personal radar</span>
        </div>
      </section>

      <section className="auth-entry">
        <div className="auth-wordmark">NEWSMAP</div>
        <div className="auth-card">
          <div className="auth-tabs" aria-label="Account mode">
            <button className={mode === 'signup' ? 'active' : ''} onClick={() => changeMode('signup')}>Create account</button>
            <button className={mode === 'login' ? 'active' : ''} onClick={() => changeMode('login')}>Sign in</button>
          </div>

          <div className="auth-heading">
            <span>{mode === 'signup' ? 'START YOUR RADAR' : 'WELCOME BACK'}</span>
            <h2>{mode === 'signup' ? 'Make the map yours.' : 'Return to your world.'}</h2>
            <p>{mode === 'signup' ? 'Choose the events, places, and subjects worth interrupting you for.' : 'Your follows and event updates are waiting.'}</p>
          </div>

          <form onSubmit={submit}>
            {mode === 'signup' && (
              <label>
                <span>Name</span>
                <input name="displayName" autoFocus required minLength="2" maxLength="60" autoComplete="name" value={displayName} onChange={event => setDisplayName(event.target.value)} placeholder="How should we address you?" />
              </label>
            )}
            <label>
              <span>Email</span>
              <input name="email" autoFocus={mode === 'login'} required type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} placeholder="you@example.com" />
            </label>
            <label>
              <span>Password</span>
              <input name="password" required minLength="10" maxLength="128" type="password" autoComplete={mode === 'signup' ? 'new-password' : 'current-password'} value={password} onChange={event => setPassword(event.target.value)} placeholder={mode === 'signup' ? 'At least 10 characters' : 'Your password'} />
            </label>
            {error && <div className="auth-error">{error}</div>}
            <button className="auth-submit" disabled={busy} type="submit">
              <span>{busy ? 'Connecting…' : mode === 'signup' ? 'Create my radar' : 'Open NewsMap'}</span>
              <b>→</b>
            </button>
          </form>
          <p className="auth-security">Secure server session · Passwords are one-way hashed</p>
        </div>
      </section>
    </main>
  );
}
