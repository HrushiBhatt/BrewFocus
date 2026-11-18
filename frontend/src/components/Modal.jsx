import { useEffect, useRef } from 'react';
import { Sprite } from './Sprite';
import { ICONS } from './sprites';

/**
 * A native <dialog>: focus trapping, Esc to close and a backdrop for free. It opens as it mounts and
 * focuses the element marked `data-autofocus`. Closing it (the button, Esc, or a click on the
 * backdrop) calls `onClose`; the parent decides what that means and unmounts it.
 */
export function Modal({ title, onClose, children }) {
  const ref = useRef(null);

  useEffect(() => {
    const el = ref.current;
    if (!el.open) el.showModal?.();
    el.querySelector('[data-autofocus]')?.focus();
  }, []);

  return (
    <dialog
      ref={ref}
      className="modal"
      aria-label={title}
      onClose={onClose}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose(); // a click outside the card
      }}
    >
      <div className="modal-card px">
        <header className="modal-header">
          <h2>{title}</h2>
          <button className="icon-btn" type="button" onClick={onClose} aria-label="Close">
            <Sprite className="icon-sm" rows={ICONS.close} />
          </button>
        </header>
        <div className="modal-body">{children}</div>
      </div>
    </dialog>
  );
}
