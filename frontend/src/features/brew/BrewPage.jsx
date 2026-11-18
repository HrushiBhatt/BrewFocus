import { useEffect } from 'react';
import { brew } from '../../state/services';
import { TaskList } from '../tasks/TaskList';
import { AmbienceMixer } from './AmbienceMixer';
import { ModePicker } from './ModePicker';
import { TimerStage } from './TimerStage';
import './BrewPage.css';

/**
 * The main page: the timer in the middle, tasks on one side, modes and ambience on the other.
 * Space, R and S work anywhere on this page (see the brew store's onKey).
 */
export default function BrewPage() {
  useEffect(() => {
    const onKey = (e) => brew.getState().onKey(e);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  return (
    <div className="brew-page">
      <TimerStage className="stage" />
      <TaskList className="tasks" />
      <div className="side">
        <ModePicker />
        <AmbienceMixer />
      </div>
    </div>
  );
}
