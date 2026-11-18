import { useState } from 'react';
import { Modal } from '../../components/Modal';
import { Sprite } from '../../components/Sprite';
import { CAFE } from '../../components/sprites';
import { formatMinutes } from '../../lib/time';
import { brew, useBrew } from '../../state/services';
import './ReflectionDialog.css';

/** After each block: one sentence for the journal, and any café decoration it earned. */
export function ReflectionDialog() {
  const done = useBrew((s) => s.reflecting);
  const [note, setNote] = useState('');
  if (!done) return null;

  const { session } = done;
  const unlocked = CAFE.find((item) => item.id === done.unlocked);
  const finish = (text) => void brew.getState().finishReflection(text);

  function save(e) {
    e.preventDefault();
    finish(note.trim());
  }

  /** Enter saves; Shift+Enter adds a line break. */
  function submitOnEnter(e) {
    if (e.key !== 'Enter' || e.shiftKey) return;
    e.preventDefault();
    e.currentTarget.form?.requestSubmit();
  }

  return (
    <div className="reflection-dialog">
      <Modal title={session.completed ? 'Brew complete!' : 'Brew ended early'} onClose={() => finish('')}>
        <p className="summary">
          You focused for <b>{formatMinutes(Math.round(session.focused / 60))}</b>
          {session.intention && (
            <>
              {' '}
              on <q>{session.intention}</q>
            </>
          )}
          .
        </p>
        {unlocked && (
          <div className="unlock px">
            <Sprite className="unlock-sprite" rows={unlocked.rows} palette={unlocked.palette} />
            <p>
              New for your café: <b>{unlocked.name}</b>
            </p>
          </div>
        )}
        <form className="modal-form" onSubmit={save}>
          <label className="field">
            In one sentence, what did you get done?
            <textarea
              className="input px"
              data-autofocus
              rows={2}
              maxLength={280}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              onKeyDown={submitOnEnter}
            />
          </label>
          <div className="modal-actions">
            <button type="button" className="btn px" onClick={() => finish('')}>
              Skip
            </button>
            <button className="btn btn-primary px">Save to journal</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
