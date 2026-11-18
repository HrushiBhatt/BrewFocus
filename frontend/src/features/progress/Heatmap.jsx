import { useLayoutEffect, useMemo, useRef } from 'react';
import { formatMinutes, localDay, plural } from '../../lib/time';
import './Heatmap.css';

const CELL = 11;
const STEP = CELL + 3;
const LEFT = 30;
const TOP = 18;
const WEEKS = 53;
const WIDTH = LEFT + WEEKS * STEP + 14;
const HEIGHT = TOP + 7 * STEP;
const LEGEND = ['No focus', 'Under 25m', '25–59m', '1–2h', '2h+'];
const DAY_LABELS = [
  { row: 1, label: 'Mon' },
  { row: 3, label: 'Wed' },
  { row: 5, label: 'Fri' },
];

const level = (min) => (min <= 0 ? 0 : min < 25 ? 1 : min < 60 ? 2 : min < 120 ? 3 : 4);

/** One column per week and one cell per day for the last 53 weeks, ending today. */
export function buildGrid(minutesByDay, today = new Date()) {
  const end = new Date(today);
  end.setHours(12, 0, 0, 0); // noon, so a daylight-saving change can't skip or repeat a day
  const start = new Date(end);
  start.setDate(start.getDate() - (WEEKS - 1) * 7 - end.getDay());

  const cells = [];
  const months = [];
  for (let d = new Date(start), i = 0; d <= end; d.setDate(d.getDate() + 1), i++) {
    const col = Math.floor(i / 7);
    const row = i % 7;
    const key = localDay(d);
    if (row === 0 && d.getDate() <= 7) months.push({ col, label: d.toLocaleString(undefined, { month: 'short' }) });
    const min = minutesByDay[key] ?? 0;
    const when = d.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
    cells.push({ key, col, row, level: level(min), min, label: `${when}: ${min ? formatMinutes(min) : 'no focus'}` });
  }
  return { cells, months };
}

/** A GitHub-style consistency grid, shaded by minutes focused each day. */
export function Heatmap({ minutesByDay }) {
  const grid = useMemo(() => buildGrid(minutesByDay), [minutesByDay]);
  const scroll = useRef(null);

  const active = grid.cells.filter((c) => c.min > 0);
  const summary = `${plural(active.length, 'active day')} · ${formatMinutes(active.reduce((sum, c) => sum + c.min, 0))} this year`;

  // On narrow screens, start scrolled to the most recent weeks.
  useLayoutEffect(() => {
    const el = scroll.current;
    el.scrollLeft = el.scrollWidth;
  }, []);

  return (
    <div className="heatmap">
      <div className="scroll" ref={scroll}>
        <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label={summary}>
          {grid.months.map((m) => (
            <text key={m.col} x={LEFT + m.col * STEP} y="10">
              {m.label}
            </text>
          ))}
          {DAY_LABELS.map((d) => (
            <text key={d.row} x="0" y={TOP + d.row * STEP + CELL - 2}>
              {d.label}
            </text>
          ))}
          {grid.cells.map((c) => (
            <rect key={c.key} className={`heat-${c.level}`} x={LEFT + c.col * STEP} y={TOP + c.row * STEP} width={CELL} height={CELL}>
              <title>{c.label}</title>
            </rect>
          ))}
        </svg>
      </div>
      <div className="footer">
        <span>{summary}</span>
        <span className="legend" aria-hidden="true">
          Less
          {LEGEND.map((label, i) => (
            <span key={label} className={`swatch heat-${i}`} title={label} />
          ))}
          More
        </span>
      </div>
    </div>
  );
}
