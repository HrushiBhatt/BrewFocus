import { Sprite } from '../../components/Sprite';
import { CAFE } from '../../components/sprites';
import { cx } from '../../lib/cx';
import { plural } from '../../lib/time';
import './CafeShelf.css';

/** Progress toward the next decoration, from 0 to 1, or null once the café is full. */
export function nextUnlock(completed) {
  const next = CAFE.find((item) => completed < item.at);
  if (!next) return { next: null, progress: 1 };
  const previousAt = CAFE.filter((item) => item.at <= completed).at(-1)?.at ?? 0;
  return { next, progress: (completed - previousAt) / (next.at - previousAt) };
}

/** Every full brew furnishes a cozy pixel café. Locked items show as silhouettes. */
export function CafeShelf({ completed }) {
  const { next, progress } = nextUnlock(completed);
  return (
    <div className="cafe-shelf">
      <ul className="shelf">
        {CAFE.map((item) => {
          const unlocked = completed >= item.at;
          return (
            <li key={item.id} className={cx(unlocked && 'unlocked')}>
              <Sprite
                rows={item.rows}
                palette={item.palette}
                fill={unlocked ? undefined : '#242424'}
                title={unlocked ? item.name : `Locked: ${item.at} full brews`}
              />
              <span className="name">{unlocked ? item.name : plural(item.at, 'brew')}</span>
            </li>
          );
        })}
      </ul>
      <div className="next">
        <p>{next ? `${plural(next.at - completed, 'more full brew')} to unlock the ${next.name}.` : 'Your café is fully furnished. ☕'}</p>
        <div className="meter">
          <span style={{ width: `${progress * 100}%` }} />
        </div>
      </div>
    </div>
  );
}
