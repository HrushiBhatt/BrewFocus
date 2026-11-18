import { PixelMug } from '../../components/PixelMug';
import { Sprite } from '../../components/Sprite';
import { ICONS } from '../../components/sprites';
import { cx } from '../../lib/cx';
import { formatClock, formatMinutes, plural } from '../../lib/time';
import { brew, useBrew, useTimerView } from '../../state/services';
import './TimerStage.css';

/** How many blocks the progress bar under the mug is drawn with. */
const SEGMENTS = 24;
const segments = Array.from({ length: SEGMENTS }, (_, i) => i);

/**
 * The timer itself: the mug, whose coffee drains as a block runs, the clock on it, the progress bar
 * and the controls. It only displays; the brew store and the server run the timer.
 */
export function TimerStage({ className }) {
  const view = useTimerView();
  const reflecting = useBrew((s) => s.reflecting);
  const pendingBreak = useBrew((s) => s.pendingBreak);
  const tasks = useBrew((s) => s.tasks);
  const taskId = useBrew((s) => s.taskId);
  const stats = useBrew((s) => s.stats);
  const { primary, reset, skip } = brew.getState();

  const status = view.state.status;
  const onBreak = view.phase === 'break';
  const lit = Math.round(view.progress * SEGMENTS);
  const [minutes, seconds] = formatClock(view.display.remainingMs / 1000).split(':');

  // The word under the clock.
  let label = status === 'running' ? 'FOCUS!' : 'READY!';
  if (reflecting) label = 'NICE!';
  else if (status === 'paused') label = 'PAUSED';
  else if (onBreak) label = 'BREAK';

  // The running block's mode (possibly started in another tab), else the selected one.
  const modeLabel = view.state.mode ?? `${view.mode.name} · ${view.mode.focus}/${view.mode.rest}`;

  // The line under the mug: the intention while focusing, otherwise what happens next.
  let caption = 'Press start to set an intention for this brew.';
  const nextTask = tasks.find((t) => t.id === taskId);
  if (onBreak) caption = 'Step away from the screen. Stretch, sip, breathe.';
  else if (view.intention) caption = `🎯 ${view.intention}`;
  else if (nextTask) caption = `Up next: ${nextTask.title}`;

  return (
    <div className={cx('timer-stage', className)}>
      <section className={cx('stage', onBreak && 'is-break')} aria-label="Timer">
        <p className="phase-chip px">
          {onBreak ? 'Break' : 'Focus'} · {modeLabel}
        </p>

        <PixelMug fill={view.fill} running={status === 'running'} celebrate={label === 'NICE!'} onBreak={onBreak}>
          <div className="clock" role="timer" aria-live="off">
            {minutes}
            <span className="clock-sep">:</span>
            {seconds}
          </div>
          <div className="clock-label">{label}</div>
        </PixelMug>

        <p className={cx('intention', !view.intention && 'is-empty')}>{caption}</p>

        <div
          className="segments"
          role="progressbar"
          aria-label={`${view.phase} progress`}
          aria-valuenow={Number((view.progress * 100).toFixed(0))}
          aria-valuemin={0}
          aria-valuemax={100}
        >
          {segments.map((i) => (
            <span key={i} className={i < lit ? 'on' : undefined} />
          ))}
        </div>

        <div className="controls">
          <button className="ctrl px" onClick={reset} aria-label="Reset" title="Reset: abandon without logging (R)">
            <Sprite className="icon" rows={ICONS.reset} />
          </button>
          <button
            className="ctrl ctrl-play px"
            onClick={primary}
            aria-label={status === 'running' ? 'Pause' : 'Start'}
            title={status === 'running' ? 'Pause (Space)' : 'Start (Space)'}
          >
            <Sprite className="icon" rows={status === 'running' ? ICONS.pause : ICONS.play} />
          </button>
          <button
            className="ctrl px"
            onClick={skip}
            disabled={status === 'idle' && !pendingBreak}
            aria-label={onBreak ? 'Skip break' : 'Finish early'}
            title={onBreak ? 'Skip break (S)' : 'Finish early (S)'}
          >
            <Sprite className="icon" rows={ICONS.skip} />
          </button>
        </div>

        {stats && (
          <p className="today">
            <span>
              Today <b>{plural(stats.today.sessions, 'brew')}</b>
            </span>
            <span>
              <b>{formatMinutes(stats.today.minutes)}</b> focused
            </span>
            <span>
              🔥 <b>{plural(stats.streak, 'day')}</b>
            </span>
          </p>
        )}
      </section>
    </div>
  );
}
