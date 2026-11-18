import { useRef, useState } from 'react';
import { errorMessage } from '../../api/http';
import { Sprite } from '../../components/Sprite';
import { ICONS } from '../../components/sprites';
import { brew, useModes, useTimerView } from '../../state/services';
import './ModePicker.css';

const EMPTY = { name: '', focus: 45, rest: 10 };

/** Choose a timing rhythm (Pomodoro 25/5 and friends), or save your own. */
export function ModePicker() {
  const { modes, mode } = useModes();
  const { busy } = useTimerView();
  const [form, setForm] = useState(EMPTY);
  const [error, setError] = useState('');
  const details = useRef(null);
  const { selectMode, deleteMode, createMode } = brew.getState();

  const field = (key, number) => (e) => setForm({ ...form, [key]: number ? e.target.valueAsNumber : e.target.value });

  /** Saves a custom mode to the account, then folds the form away. */
  async function save(e) {
    e.preventDefault();
    const name = form.name.trim();
    const valid = Number.isInteger(form.focus) && form.focus >= 1 && form.focus <= 180
      && Number.isInteger(form.rest) && form.rest >= 0 && form.rest <= 60;
    if (!name || name.length > 32 || !valid) {
      setError('Give it a name, 1–180 focus minutes and 0–60 break minutes.');
      return;
    }
    try {
      await createMode(name, form.focus, form.rest);
      setForm(EMPTY);
      setError('');
      details.current.open = false;
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <div className="mode-picker">
      <section className="panel px" aria-labelledby="modes-title">
        <h2 className="panel-title" id="modes-title">
          Brew mode
        </h2>
        <ul className="mode-list">
          {modes.map((m) => (
            <li className="mode-item" key={m.key}>
              <button className="mode px" aria-pressed={m.key === mode.key} disabled={busy} onClick={() => selectMode(m)}>
                <span className="mode-time">
                  {m.focus}
                  <small>/{m.rest}</small>
                </span>
                <span className="mode-name">{m.name}</span>
                <span className="mode-blurb">{m.blurb}</span>
              </button>
              {m.presetId !== undefined && (
                <button className="mode-delete" aria-label={`Delete ${m.name}`} disabled={busy} onClick={() => deleteMode(m)}>
                  <Sprite className="icon-sm" rows={ICONS.close} />
                </button>
              )}
            </li>
          ))}
        </ul>
        {busy && <p className="panel-note">Modes unlock when this brew ends.</p>}

        <details className="custom" ref={details}>
          <summary>Custom brew</summary>
          <form className="custom-form" onSubmit={save} noValidate>
            <label className="field wide">
              Name
              <input className="input px" value={form.name} onChange={field('name')} maxLength={32} placeholder="e.g. Thesis sprint" />
            </label>
            <label className="field">
              Focus (min)
              <input className="input px" type="number" value={Number.isNaN(form.focus) ? '' : form.focus} onChange={field('focus', true)} min={1} max={180} />
            </label>
            <label className="field">
              Break (min)
              <input className="input px" type="number" value={Number.isNaN(form.rest) ? '' : form.rest} onChange={field('rest', true)} min={0} max={60} />
            </label>
            {error && <p className="form-error wide">{error}</p>}
            <button className="btn btn-primary px wide">Save mode</button>
          </form>
        </details>
      </section>
    </div>
  );
}
