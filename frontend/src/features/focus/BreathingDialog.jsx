import { useEffect, useState } from 'react';
import { Modal } from '../../components/Modal';
import { brew } from '../../state/services';
import './BreathingDialog.css';

const STEPS = ['Breathe in', 'Hold', 'Breathe out', 'Hold'];
const ROUNDS = 2;

/** Box breathing (4-4-4-4) to ease into deep work. The dot traces the box in time with the count. */
export function BreathingDialog() {
  const [elapsed, setElapsed] = useState(0);
  const launch = () => void brew.getState().launch();

  useEffect(() => {
    const id = setInterval(() => setElapsed((s) => s + 1), 1000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    if (elapsed >= ROUNDS * 16) void brew.getState().launch();
  }, [elapsed]);

  return (
    <div className="breathing-dialog">
      <Modal title="Breathe" onClose={launch}>
        <div className="breath">
          <div className="breath-box">
            <div className="breath-fill" />
            <span className="breath-dot" />
          </div>
          <p className="breath-cue">{STEPS[Math.floor(elapsed / 4) % 4]}</p>
          <p className="breath-count">{4 - (elapsed % 4)}</p>
        </div>
        <div className="modal-actions">
          <button className="btn px" onClick={launch}>
            Skip to focus
          </button>
        </div>
      </Modal>
    </div>
  );
}
