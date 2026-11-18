import { cx } from '../../lib/cx';
import { tick } from '../../sound/audio';
import { ambience, brew, useAmbience, useSettings } from '../../state/services';
import './AmbienceMixer.css';

const LAYERS = [
  { id: 'rain', label: 'Rain', icon: '🌧️' },
  { id: 'cafe', label: 'Café', icon: '☕' },
  { id: 'fire', label: 'Fireplace', icon: '🔥' },
  { id: 'lofi', label: 'Lo-fi keys', icon: '🎹' },
];

const TICKS = [
  { id: 'off', label: 'Off' },
  { id: 'clock', label: 'Clock' },
  { id: 'ominous', label: 'Ominous' },
];

/** A range slider whose filled part follows its value (the CSS reads --val). */
function Volume({ layer, label, value, disabled }) {
  return (
    <input
      className="range"
      type="range"
      min="0"
      max="1"
      step="0.05"
      value={value}
      style={{ '--val': value }}
      disabled={disabled}
      aria-label={`${label} volume`}
      onChange={(e) => ambience.getState().setVolume(layer, Number(e.target.value))}
    />
  );
}

/**
 * Background sound: rain, café, fireplace and lo-fi layers mixed together, plus your own audio file and
 * an optional tick. The sound is synthesized in sound/audio.js, so there are no audio files.
 */
export function AmbienceMixer() {
  const volumes = useAmbience((s) => s.volumes);
  const playing = useAmbience((s) => s.playing);
  const track = useAmbience((s) => s.track);
  const settings = useSettings();

  /** Plays a file from the user's own disk. It stays in the browser and is never uploaded. */
  const pickTrack = (e) => {
    const file = e.target.files?.[0];
    if (file) ambience.getState().loadTrack(file);
  };

  const chooseTick = (style) => {
    void brew.getState().updateSettings({ tick: style });
    if (style !== 'off') tick(style, 1, false); // a preview
  };

  return (
    <div className="ambience-mixer">
      <section className="panel px" aria-labelledby="mixer-title">
        <h2 className="panel-title" id="mixer-title">
          Ambience
          <button className={cx('btn btn-sm px', playing && 'btn-primary')} aria-pressed={playing} onClick={() => ambience.getState().toggle()}>
            {playing ? '■ Stop' : '▶ Play'}
          </button>
        </h2>
        <p className="panel-note">Layers blend. Try rain under lo-fi keys.</p>

        <div className="layers">
          {LAYERS.map((layer) => (
            <div className="layer" key={layer.id}>
              <span aria-hidden="true">{layer.icon}</span>
              <span className="layer-name">{layer.label}</span>
              <Volume layer={layer.id} label={layer.label} value={volumes[layer.id]} />
            </div>
          ))}
          <div className="layer">
            <span aria-hidden="true">🎧</span>
            <label className="layer-name file-pick" title={track ?? 'Load an audio file from your computer'}>
              {track ?? 'Your audio…'}
              <input className="sr-only" type="file" accept="audio/*" onChange={pickTrack} />
            </label>
            <Volume layer="custom" label="Your audio" value={volumes.custom} disabled={!track} />
          </div>
        </div>

        <h3 className="mixer-sub" id="tick-title">
          Ticking
        </h3>
        <div className="seg-group" role="radiogroup" aria-labelledby="tick-title">
          {TICKS.map((t) => (
            <button key={t.id} className="seg px" role="radio" aria-checked={settings.tick === t.id} onClick={() => chooseTick(t.id)}>
              {t.label}
            </button>
          ))}
        </div>
        <p className="panel-note">Some people focus better with a little pressure. The final minute ticks louder.</p>
      </section>
    </div>
  );
}
