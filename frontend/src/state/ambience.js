import { createStore } from 'zustand/vanilla';
import { load, persistField } from '../lib/persisted';
import { audio, loadCustomTrack, mix } from '../sound/audio';

const MIX_KEY = 'brew:mix';
const DEFAULT_MIX = { rain: 0.5, cafe: 0, fire: 0, lofi: 0.3, custom: 0 };

/**
 * The ambience mixer. Audio is inherently per-device, so it lives in the browser, in a store so the
 * mix keeps playing when you switch pages.
 */
export function createAmbienceStore() {
  const store = createStore((set, get) => ({
    volumes: load(MIX_KEY, DEFAULT_MIX),
    playing: false,
    /** The name of the user's own audio file, once picked. */
    track: null,

    toggle() {
      audio(); // create the AudioContext inside the click, so the browser allows it
      set({ playing: !get().playing });
    },

    setVolume(layer, value) {
      set({ volumes: { ...get().volumes, [layer]: value } });
    },

    /** Plays a file from the user's own disk. It stays in the browser and is never uploaded. */
    loadTrack(file) {
      loadCustomTrack(file);
      const volumes = get().volumes;
      set({ track: file.name, volumes: { ...volumes, custom: volumes.custom || 0.6 } });
    },
  }));
  store.subscribe(({ volumes, playing }) => mix(volumes, playing));
  persistField(store, 'volumes', MIX_KEY);
  return store;
}
