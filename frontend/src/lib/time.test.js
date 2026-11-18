import { describe, expect, it } from 'vitest';
import { formatClock, formatMinutes, localDay, plural } from './time';

describe('time formatting', () => {
  it('rounds the clock up, so 00:00 means time is really up', () => {
    expect(formatClock(1500)).toBe('25:00');
    expect(formatClock(1499.2)).toBe('25:00');
    expect(formatClock(0.1)).toBe('00:01');
    expect(formatClock(0)).toBe('00:00');
  });

  it('writes minutes as hours and minutes', () => {
    expect(formatMinutes(45)).toBe('45m');
    expect(formatMinutes(135)).toBe('2h 15m');
  });

  it('uses the local calendar day, not UTC', () => {
    expect(localDay(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
  });

  it('pluralizes', () => {
    expect(plural(1, 'brew')).toBe('1 brew');
    expect(plural(0, 'brew')).toBe('0 brews');
  });
});
