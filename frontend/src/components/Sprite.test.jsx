import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Sprite, runs } from './Sprite';
import { CAFE } from './sprites';

describe('Sprite', () => {
  it('merges same-colored neighbours into one rect per run', () => {
    expect(runs(['xx.x', '....'], { x: 'red' })).toEqual([
      { x: 0, y: 0, w: 2, fill: 'red' },
      { x: 3, y: 0, w: 1, fill: 'red' },
    ]);
  });

  it('paints a silhouette when given a fill', () => {
    expect(runs(['ab'], { a: 'red', b: 'blue' }, '#222').map((r) => r.fill)).toEqual(['#222']);
  });

  it('renders every café item as crisp SVG', () => {
    for (const item of CAFE) {
      const { container, unmount } = render(<Sprite rows={item.rows} palette={item.palette} />);
      const svg = container.querySelector('svg');
      expect(svg.getAttribute('viewBox')).toBe('0 0 12 12');
      expect(svg.querySelectorAll('rect').length).toBeGreaterThan(5);
      unmount();
    }
  });

  it('is hidden from screen readers unless it has a title', () => {
    const { container, rerender } = render(<Sprite rows={['x']} />);
    expect(container.querySelector('svg').getAttribute('aria-hidden')).toBe('true');
    rerender(<Sprite rows={['x']} title="Sprout" />);
    expect(container.querySelector('svg').getAttribute('role')).toBe('img');
  });
});
