/**
 * Timing modes: the built-in rhythms, plus the user's own saved presets.
 *
 * @typedef {{ key: string, name: string, focus: number, rest: number, blurb: string, presetId?: number }} Mode
 */

/** @type {Mode[]} */
export const BUILTIN_MODES = [
  { key: 'pomodoro', name: 'Pomodoro', focus: 25, rest: 5, blurb: 'The classic sprint' },
  { key: '52-17', name: '52 / 17', focus: 52, rest: 17, blurb: 'Long focus, real rest' },
  { key: 'animedoro', name: 'Animedoro', focus: 50, rest: 10, blurb: 'Deep block, then a reward' },
  { key: 'deep-work', name: 'Deep Work', focus: 90, rest: 20, blurb: 'One full ultradian cycle' },
];

/** A saved preset as a mode. */
export const fromPreset = (p) => ({
  key: `custom-${p.id}`,
  name: p.name,
  focus: p.focus,
  rest: p.rest,
  blurb: 'Your custom brew',
  presetId: p.id,
});

/** Every mode on offer: the built-ins first, then the user's. */
export const allModes = (presets) => [...BUILTIN_MODES, ...presets.map(fromPreset)];

/** The mode with this key, or the first built-in if it's gone (say, a deleted preset). */
export const findMode = (presets, key) => allModes(presets).find((m) => m.key === key) ?? BUILTIN_MODES[0];
