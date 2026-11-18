import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { CafeShelf, nextUnlock } from './CafeShelf';
import { buildGrid } from './Heatmap';

describe('the café', () => {
  it('measures progress from the last unlock to the next', () => {
    expect(nextUnlock(0)).toMatchObject({ next: { id: 'sprout' }, progress: 0 });
    expect(nextUnlock(4)).toMatchObject({ next: { id: 'cat' }, progress: 0.5 }); // between 3 and 5
    expect(nextUnlock(100)).toEqual({ next: null, progress: 1 });
  });

  it('shows locked items as silhouettes and says what comes next', () => {
    render(<CafeShelf completed={3} />);
    expect(screen.getByRole('img', { name: 'Desk Lamp' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Locked: 5 full brews' })).toBeInTheDocument();
    expect(screen.getByText('2 more full brews to unlock the Café Cat.')).toBeInTheDocument();
  });
});

describe('the heatmap', () => {
  it('lays out 53 weeks ending today, shaded by minutes', () => {
    const today = new Date(2026, 10, 18); // a Wednesday
    const grid = buildGrid({ '2026-11-18': 130, '2026-11-17': 30 }, today);
    const last = grid.cells.at(-1);
    expect(last.key).toBe('2026-11-18');
    expect(last.level).toBe(4);
    expect(grid.cells.at(-2).level).toBe(2);
    expect(grid.cells[0].row).toBe(0); // the first column starts on a Sunday
    expect(Math.max(...grid.cells.map((c) => c.col))).toBe(52);
  });
});
