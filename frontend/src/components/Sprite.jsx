import { useMemo } from 'react';
import './Sprite.css';

/**
 * Merges each row's same-colored neighbouring pixels into single rects, so a 12×12 sprite is a dozen
 * rectangles rather than 144.
 *
 * @param {string[]} rows             one string per row, one character per pixel, '.' transparent
 * @param {Record<string, string>} palette  character → color
 * @param {string} [fill]             paint every pixel this color instead (a locked item's silhouette)
 */
export function runs(rows, palette, fill) {
  const color = (c) => (c === undefined || c === '.' ? null : (fill ?? palette[c]));
  const out = [];
  rows.forEach((row, y) => {
    for (let x = 0; x < row.length; ) {
      const paint = color(row[x]);
      let w = 1;
      while (x + w < row.length && color(row[x + w]) === paint) w++;
      if (paint) out.push({ x, y, w, fill: paint });
      x += w;
    }
  });
  return out;
}

const ICON_PALETTE = { x: 'currentColor' };

/** Text pixel art rendered as crisp SVG. Size it with a class. A title makes it an image for screen readers. */
export function Sprite({ rows, palette = ICON_PALETTE, fill, title, className = '' }) {
  const rects = useMemo(() => runs(rows, palette, fill), [rows, palette, fill]);
  return (
    <span className={`sprite ${className}`}>
      <svg
        viewBox={`0 0 ${rows[0].length} ${rows.length}`}
        shapeRendering="crispEdges"
        role={title ? 'img' : undefined}
        aria-label={title || undefined}
        aria-hidden={title ? undefined : 'true'}
      >
        {rects.map((r, i) => (
          <rect key={i} x={r.x} y={r.y} width={r.w} height="1" fill={r.fill} />
        ))}
      </svg>
    </span>
  );
}
