import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api/api';
import { cx } from '../../lib/cx';
import { formatMinutes, plural } from '../../lib/time';
import { brew, useBrew } from '../../state/services';
import { CafeShelf } from './CafeShelf';
import { Heatmap } from './Heatmap';
import './ProgressPage.css';

const WHEN = new Intl.DateTimeFormat(undefined, { weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' });

/**
 * The journal: the newest finished blocks. It reloads whenever the stats change, which is whenever a
 * session lands on any device.
 */
function useJournal(stats) {
  const [journal, setJournal] = useState({ sessions: null, failed: false });
  useEffect(() => {
    if (!stats) return undefined;
    let current = true;
    api.sessions().then(
      (sessions) => current && setJournal({ sessions, failed: false }),
      () => current && setJournal((j) => ({ ...j, failed: true })),
    );
    return () => {
      current = false;
    };
  }, [stats]);
  return journal;
}

/** The tracking page: totals, streaks, the year heatmap, the café and the journal. */
export default function ProgressPage() {
  const stats = useBrew((s) => s.stats);
  const tasks = useBrew((s) => s.tasks);
  const journal = useJournal(stats);

  useEffect(() => {
    void brew.getState().refresh('sessions');
  }, []);

  /** Task names by ID, so a journal entry can say what it was for. */
  const taskTitles = useMemo(() => new Map(tasks.map((t) => [t.id, t.title])), [tasks]);
  const taskTitle = (id) => (id === null ? undefined : taskTitles.get(id));

  if (!stats) {
    return (
      <div className="progress-page">
        <p className="empty">Loading your stats…</p>
      </div>
    );
  }

  return (
    <div className="progress-page">
      <div className="tiles">
        <div className="tile px">
          <span className="tile-label">
            <span aria-hidden="true">☕</span> Today
          </span>
          <span className="tile-value">{formatMinutes(stats.today.minutes)}</span>
          <span className="tile-sub">{plural(stats.today.sessions, 'brew')}</span>
        </div>
        <div className="tile px">
          <span className="tile-label">
            <span aria-hidden="true">🔥</span> Streak
          </span>
          <span className="tile-value">{plural(stats.streak, 'day')}</span>
          <span className="tile-sub">Best: {plural(stats.best_streak, 'day')}</span>
        </div>
        <div className="tile px">
          <span className="tile-label">
            <span aria-hidden="true">⏱️</span> All time
          </span>
          <span className="tile-value">{formatMinutes(stats.total.minutes)}</span>
          <span className="tile-sub">{plural(stats.total.sessions, 'session')}</span>
        </div>
        <div className="tile px">
          <span className="tile-label">
            <span aria-hidden="true">🏆</span> Full brews
          </span>
          <span className="tile-value">{stats.total.completed}</span>
          <span className="tile-sub">ran all the way to 00:00</span>
        </div>
      </div>

      <section className="panel px wide" aria-labelledby="heat-title">
        <h2 className="panel-title" id="heat-title">
          Consistency
        </h2>
        <Heatmap minutesByDay={stats.heatmap} />
      </section>

      <section className="panel px" aria-labelledby="cafe-title">
        <h2 className="panel-title" id="cafe-title">
          Your café
        </h2>
        <CafeShelf completed={stats.total.completed} />
      </section>

      <section className="panel px" aria-labelledby="journal-title">
        <h2 className="panel-title" id="journal-title">
          Brew journal
        </h2>
        {journal.sessions ? (
          <ol className="journal">
            {journal.sessions.map((entry) => {
              const title = taskTitle(entry.task_id);
              return (
                <li key={entry.id} className={cx('entry', !entry.completed && 'early')}>
                  <div className="meta">
                    <time dateTime={entry.created_at}>{WHEN.format(new Date(entry.created_at))}</time>
                    <span>{entry.mode}</span>
                    <span className="mins">{formatMinutes(Math.round(entry.focused / 60))}</span>
                    {!entry.completed && <span>ended early</span>}
                    {entry.distractions > 0 && <span>{plural(entry.distractions, 'distraction')}</span>}
                  </div>
                  {title && <p className="task">▣ {title}</p>}
                  {entry.intention && entry.intention !== title && <p>🎯 {entry.intention}</p>}
                  {entry.reflection && <p className="reflection">✎ {entry.reflection}</p>}
                </li>
              );
            })}
            {journal.sessions.length === 0 && <li className="empty">No brews yet. Finished sessions and your reflections land here.</li>}
          </ol>
        ) : journal.failed ? (
          <p className="empty">Couldn&apos;t load the journal.</p>
        ) : (
          <p className="empty">Loading…</p>
        )}
      </section>
    </div>
  );
}
