/**
 * A minimal publish/subscribe channel: `on` returns the function that unsubscribes. It carries live
 * events from the server stream (or the in-browser backend) to whoever is listening.
 */
export function createEmitter() {
  const listeners = new Set();
  return {
    on(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    emit(event) {
      for (const listener of [...listeners]) listener(event);
    },
  };
}
