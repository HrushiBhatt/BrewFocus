import { cx } from '../lib/cx';
import './PixelMug.css';

// The original Brew Focus mug, drawn on an 8-unit pixel grid in a 200×220 viewBox.
// Each rect is [x, y, width, height, fill].
const INK = '#2A2A2A';

const BODY = [
  [32, 40, 120, 8, '#E8E8E8'],
  ...Array.from({ length: 13 }, (_, i) => [24, 48 + i * 8, 136, 8, i % 3 === 2 ? '#F5F5F5' : '#FFFFFF']),
  [24, 152, 136, 8, '#F0F0F0'],
  [24, 160, 136, 8, '#E8E8E8'],
  [32, 168, 120, 8, '#E0E0E0'],
  [40, 176, 104, 8, '#D8D8D8'],
];

const OUTLINE = [
  [32, 32, 120, 8], [24, 40, 8, 8], [152, 40, 8, 8], [16, 48, 8, 136], [160, 48, 8, 120],
  [16, 176, 8, 8], [24, 184, 8, 8], [32, 184, 120, 8], [152, 184, 8, 8], [160, 168, 8, 16],
  // handle
  [160, 56, 8, 8], [168, 48, 16, 8], [184, 56, 8, 16], [192, 72, 8, 56],
  [184, 128, 8, 16], [168, 144, 16, 8], [160, 136, 8, 8],
];

const HANDLE = [
  [168, 56, 16, 8, '#FFFFFF'], [176, 64, 8, 8, '#FFFFFF'], [184, 72, 8, 56, '#F5F5F5'],
  [176, 72, 8, 56, '#E8E8E8'], [176, 128, 8, 8, '#FFFFFF'], [168, 136, 8, 8, '#F0F0F0'],
];

const FRAME = [...OUTLINE.map(([x, y, w, h]) => [x, y, w, h, INK]), ...HANDLE];

const STEAM = [
  [[10, 30], [10, 24], [14, 18], [10, 12], [6, 6]],
  [[28, 32], [28, 26], [24, 20], [28, 14], [32, 8]],
  [[46, 30], [46, 24], [42, 18], [46, 12], [50, 6]],
];

const FULL = 120; // the coffee column's height when the mug is full

const rect = ([x, y, width, height, fill], key) => <rect key={key} x={x} y={y} width={width} height={height} fill={fill} />;

/**
 * The mug. `fill` runs from 0 (empty) to 1 (full), snapped to a 4-unit grid for a chunky, retro drain.
 * Children (the clock) sit on its face.
 */
export function PixelMug({ fill, running = false, celebrate = false, onBreak = false, className, children }) {
  const level = Math.round((FULL * Math.min(1, Math.max(0, fill))) / 4) * 4;
  const top = 56 + FULL - level;
  return (
    <div className={cx('pixel-mug', className, running && 'is-running', celebrate && 'is-celebrating', onBreak && 'is-break')}>
      <div className="mug-glow" />
      <svg className="steam" viewBox="0 0 60 40" aria-hidden="true">
        {STEAM.map((puff, i) => (
          <g key={i}>
            {puff.map(([x, y], j) => (
              <rect key={j} x={x} y={y} width="4" height="4" fill="currentColor" opacity={0.6 - j * 0.1} />
            ))}
          </g>
        ))}
      </svg>
      <svg className="mug-svg" viewBox="0 0 200 220" shapeRendering="crispEdges" aria-hidden="true">
        {BODY.map(rect)}
        <rect x="24" y={top} width="136" height={level} fill="#6F4E37" />
        {level >= 8 && (
          <>
            <rect x="24" y={top - 8} width="136" height="8" fill="#A67C52" />
            <rect x="32" y={top} width="8" height="8" fill="#8B6B4C" opacity="0.5" />
            <rect x="40" y={top} width="8" height="8" fill="#8B6B4C" opacity="0.3" />
          </>
        )}
        {FRAME.map(rect)}
      </svg>
      <div className="mug-readout">{children}</div>
    </div>
  );
}
