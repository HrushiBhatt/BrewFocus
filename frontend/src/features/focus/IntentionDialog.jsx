import { useState } from 'react';
import { Modal } from '../../components/Modal';
import { brew, useBrew, useModes, useSettings } from '../../state/services';

/** Before each block: a micro-goal, an optional task, and whether to breathe first. */
export function IntentionDialog() {
  const tasks = useBrew((s) => s.tasks);
  const { mode } = useModes();
  const settings = useSettings();
  const [intention, setIntention] = useState('');
  const [taskId, setTaskId] = useState(() => brew.getState().taskId);
  const [breathe, setBreathe] = useState(settings.breathe);

  const openTasks = tasks.filter((t) => !t.done);
  const taskTitle = tasks.find((t) => t.id === brew.getState().taskId)?.title ?? '';

  function start(e) {
    e.preventDefault();
    brew.getState().beginFocus(intention.trim(), taskId, breathe);
  }

  return (
    <div className="intention-dialog">
      <Modal title="Set an intention" onClose={() => brew.getState().setDialog(null)}>
        <form className="modal-form" onSubmit={start}>
          <label className="field">
            What&apos;s the one thing this {mode.focus}-minute brew is for?
            <input
              className="input px"
              data-autofocus
              value={intention}
              onChange={(e) => setIntention(e.target.value)}
              maxLength={140}
              placeholder={taskTitle ? `Defaults to: ${taskTitle}` : 'e.g. Draft the intro section'}
            />
          </label>
          {openTasks.length > 0 && (
            <label className="field">
              Task
              <select className="input px" value={taskId ?? ''} onChange={(e) => setTaskId(e.target.value ? Number(e.target.value) : null)}>
                <option value="">No task</option>
                {openTasks.map((task) => (
                  <option key={task.id} value={task.id}>
                    {task.title} ({task.brews}/{task.estimate})
                  </option>
                ))}
              </select>
            </label>
          )}
          <label className="check">
            <input type="checkbox" checked={breathe} onChange={(e) => setBreathe(e.target.checked)} />
            Two rounds of box breathing first (~30s)
          </label>
          <div className="modal-actions">
            <button className="btn btn-primary px">Start brewing</button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
