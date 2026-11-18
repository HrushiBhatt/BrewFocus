import { useState } from 'react';
import { Sprite } from '../../components/Sprite';
import { ICONS } from '../../components/sprites';
import { cx } from '../../lib/cx';
import { formatMinutes } from '../../lib/time';
import { brew, useBrew, useTimerView } from '../../state/services';
import './TaskList.css';

/** One coffee bean per estimated brew, filled as they're completed (overruns shown too). */
const beans = (task) => Array.from({ length: Math.max(task.estimate, task.brews) }, (_, i) => i < task.brews);

/** What you're working on: add a task with an estimate of focus blocks, brew it, tick it off. */
export function TaskList({ className }) {
  const tasks = useBrew((s) => s.tasks);
  const taskId = useBrew((s) => s.taskId);
  const { busy } = useTimerView();
  const [title, setTitle] = useState('');
  const [estimate, setEstimate] = useState(1);
  const { addTask, toggleTask, removeTask, focusOn } = brew.getState();

  const open = tasks.filter((t) => !t.done);
  const done = tasks.filter((t) => t.done);
  const valid = title.trim().length > 0 && title.length <= 120 && Number.isInteger(estimate) && estimate >= 1 && estimate <= 20;

  async function add(e) {
    e.preventDefault();
    if (!valid) return;
    await addTask(title.trim(), estimate);
    setTitle('');
    setEstimate(1);
  }

  return (
    <div className={cx('task-panel', className)}>
      <section className="panel px" aria-labelledby="tasks-title">
        <h2 className="panel-title" id="tasks-title">
          Tasks
          <span className="count">{open.length} open</span>
        </h2>
        <form className="task-form" onSubmit={add} noValidate>
          <input className="input px" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={120} placeholder="New task…" aria-label="New task" />
          <input
            className="input px estimate"
            type="number"
            value={Number.isNaN(estimate) ? '' : estimate}
            onChange={(e) => setEstimate(e.target.valueAsNumber)}
            min={1}
            max={20}
            aria-label="Estimated brews"
            title="How many focus blocks will it take?"
          />
          <button className="btn btn-primary btn-sm px" disabled={!valid}>
            Add
          </button>
        </form>
        <ul className="task-list">
          {open.map((task) => (
            <li key={task.id} className={cx('task', task.id === taskId && 'selected')}>
              <input type="checkbox" className="check-box" onChange={() => toggleTask(task)} aria-label={`Mark ${task.title} done`} />
              <div className="task-body">
                <span className="task-title">{task.title}</span>
                <span className="task-meta">
                  <span className="beans" aria-label={`${task.brews} of ${task.estimate} brews`}>
                    {beans(task).map((full, i) => (
                      <i key={i} className={cx(full && 'full', i >= task.estimate && 'over')} />
                    ))}
                  </span>
                  {task.minutes > 0 && <span>{formatMinutes(task.minutes)}</span>}
                </span>
              </div>
              <button className="brew-btn px" onClick={() => focusOn(task)} disabled={busy} aria-label={`Focus on ${task.title}`} title="Brew this task">
                <Sprite className="icon-sm" rows={ICONS.play} />
              </button>
              <button className="icon-btn" onClick={() => removeTask(task)} aria-label={`Delete ${task.title}`}>
                <Sprite className="icon-sm" rows={ICONS.close} />
              </button>
            </li>
          ))}
          {open.length === 0 && <li className="empty">Nothing planned yet. Add what you&apos;ll focus on, then press ▶ to brew it.</li>}
        </ul>
        {done.length > 0 && (
          <details className="done">
            <summary>Done ({done.length})</summary>
            <ul className="task-list">
              {done.map((task) => (
                <li key={task.id} className="task is-done">
                  <input type="checkbox" className="check-box" defaultChecked onChange={() => toggleTask(task)} aria-label={`Reopen ${task.title}`} />
                  <div className="task-body">
                    <span className="task-title">{task.title}</span>
                    <span className="task-meta">
                      {task.brews} brews · {formatMinutes(task.minutes)}
                    </span>
                  </div>
                  <button className="icon-btn" onClick={() => removeTask(task)} aria-label={`Delete ${task.title}`}>
                    <Sprite className="icon-sm" rows={ICONS.close} />
                  </button>
                </li>
              ))}
            </ul>
          </details>
        )}
      </section>
    </div>
  );
}
