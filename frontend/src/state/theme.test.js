import { afterEach, describe, expect, it } from 'vitest';
import { THEME_KEY, createThemeStore } from './theme';

describe('theme', () => {
  const html = document.documentElement;

  /** A fresh store, as on page load, with this already saved. */
  const start = (stored) => {
    localStorage.clear();
    if (stored) localStorage.setItem(THEME_KEY, JSON.stringify(stored));
    return createThemeStore();
  };

  afterEach(() => localStorage.clear());

  it('starts dark, whatever the system prefers', () => {
    expect(start().getState().current).toBe('dark');
    expect(html.dataset.theme).toBe('dark');
  });

  it('keeps the choice across a reload', () => {
    start().getState().toggle();
    expect(html.dataset.theme).toBe('light');
    expect(JSON.parse(localStorage.getItem(THEME_KEY))).toBe('light');
    expect(createThemeStore().getState().current).toBe('light');
  });

  it('treats anything unrecognized in storage as dark', () => {
    expect(start('system').getState().current).toBe('dark');
    localStorage.setItem(THEME_KEY, '{not json');
    expect(createThemeStore().getState().current).toBe('dark');
  });
});
