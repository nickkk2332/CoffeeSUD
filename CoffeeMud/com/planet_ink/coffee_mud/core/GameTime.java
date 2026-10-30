package com.planet_ink.coffee_mud.core;

/*
   Copyright 2026 CoffeeSUD contributors

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

	   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
import java.util.function.BooleanSupplier;

/**
 * The game clock, in milliseconds.  Everything that times something in the
 * world (cooldowns, durations, contracts, decay, command pacing) reads this
 * instead of System.currentTimeMillis().  It runs with the system clock,
 * except that it stands still while the game is paused or turn-based, can be
 * moved forward on request, and can be set (loading a saved game).
 *
 * While the clock is stopped, it moves only when something asks it to: in
 * turn-based play, the player's actions ask for as much time as they take,
 * and WAIT asks for whole ticks.  The scheduler takes that time a step at a
 * time (see {@link #takeAdvance(long)}), so that everything due on the way
 * happens, in order.
 *
 * Things that are about the real world stay on the system clock: logs,
 * network and input timeouts, idle detection, thread health checks, caches,
 * and dates shown to the player as real dates, such as the last login.
 */
public final class GameTime
{
	/** the clock's state; replaced whole, so that a reader never sees half a change */
	private static final class State
	{
		/** how far the game clock is behind the system clock */
		final long	behind;
		/** the system time at which the clock stopped, or 0 while it runs */
		final long	stoppedAt;

		State(final long behind, final long stoppedAt)
		{
			this.behind = behind;
			this.stoppedAt = stoppedAt;
		}
	}

	private static volatile State	state		= new State(0, 0);
	/** true while the player has paused the game */
	private static volatile boolean	paused		= false;
	/** true while time moves only when the player acts */
	private static volatile boolean	turnBased	= false;
	/** time asked for and not yet taken by the scheduler */
	private static volatile long	pending		= 0;
	/** when this says so, the time still pending is dropped */
	private static volatile BooleanSupplier stopWhen = null;

	private GameTime()
	{
	}

	/**
	 * Returns the current game time in milliseconds.  While the clock runs,
	 * this has the same scale and starting point as System.currentTimeMillis().
	 * @return the current game time
	 */
	public static long now()
	{
		final State s = state;
		return ((s.stoppedAt != 0) ? s.stoppedAt : System.currentTimeMillis()) - s.behind;
	}

	/**
	 * Returns whether the player has paused the game.  The clock is then
	 * stopped, and actions that take time wait until the game resumes.
	 * @return true if paused
	 */
	public static boolean isPaused()
	{
		return paused;
	}

	/**
	 * Returns whether time moves only when the player acts.
	 * @return true if turn-based
	 */
	public static boolean isTurnBased()
	{
		return turnBased;
	}

	/**
	 * Returns whether the clock is stopped, because the game is paused or
	 * turn-based.  It then moves only when asked to.
	 * @return true if stopped
	 */
	public static boolean isStopped()
	{
		return state.stoppedAt != 0;
	}

	/**
	 * Pauses the game, stopping the clock.  Does nothing if the game is
	 * turn-based, since the clock is already stopped.
	 */
	public static synchronized void pause()
	{
		if(turnBased)
			return;
		paused = true;
		stop();
	}

	/**
	 * Unpauses the game, starting the clock again from where it stopped.
	 */
	public static synchronized void resume()
	{
		paused = false;
		if(!turnBased)
			start();
	}

	/**
	 * Makes time move only when the player acts, or run on its own again.
	 * Either way, the game is no longer paused.
	 * @param on true for turn-based, false for real time
	 */
	public static synchronized void setTurnBased(final boolean on)
	{
		turnBased = on;
		paused = false;
		if(on)
			stop();
		else
			start();
	}

	private static void stop()
	{
		final State s = state;
		if(s.stoppedAt == 0)
			state = new State(s.behind, System.currentTimeMillis());
	}

	private static void start()
	{
		final State s = state;
		if(s.stoppedAt != 0)
			state = new State(s.behind + (System.currentTimeMillis() - s.stoppedAt), 0);
		pending = 0;
		stopWhen = null;
	}

	/**
	 * Moves the game clock forward at once, whether it is running or stopped.
	 * Nothing that comes due on the way is run first; to move a stopped clock
	 * through time, use {@link #requestAdvance(long)}.
	 * @param ms how many milliseconds to move it
	 */
	public static synchronized void advance(final long ms)
	{
		if(ms <= 0)
			return;
		final State s = state;
		if(s.stoppedAt != 0)
			state = new State(s.behind, s.stoppedAt + ms);
		else
			state = new State(s.behind - ms, 0);
	}

	/**
	 * Asks for the stopped clock to move forward.  The scheduler moves it
	 * soon after, running whatever comes due on the way.  Does nothing while
	 * the clock runs.
	 * @param ms how many milliseconds to move it
	 */
	public static void requestAdvance(final long ms)
	{
		requestAdvance(ms, null);
	}

	/**
	 * Asks for the stopped clock to move forward, and to stop moving early
	 * once the given test says so.  The scheduler checks it after each step.
	 * Does nothing while the clock runs.
	 * @param ms how many milliseconds to move it
	 * @param until the test, or null to take all of the time
	 */
	public static synchronized void requestAdvance(final long ms, final BooleanSupplier until)
	{
		if((ms <= 0) || (state.stoppedAt == 0))
			return;
		pending += ms;
		if(until != null)
			stopWhen = until;
	}

	/**
	 * Returns whether time asked for has not all been taken yet.
	 * @return true while the clock is still being moved
	 */
	public static boolean isAdvancing()
	{
		return pending > 0;
	}

	/**
	 * Drops whatever time is still asked for.
	 */
	public static synchronized void cancelAdvance()
	{
		pending = 0;
		stopWhen = null;
	}

	/**
	 * Moves the stopped clock forward by some of the time asked for, but no
	 * more than the given step.  The scheduler calls this with the time until
	 * the next thing due, runs what is due, then calls it again.
	 * @param maxStep the most to move it
	 * @return how far it moved
	 */
	public static synchronized long takeAdvance(final long maxStep)
	{
		final long step = Math.min(pending, Math.max(0, maxStep));
		pending -= step;
		advance(step);
		if(pending == 0)
			stopWhen = null;
		return step;
	}

	/**
	 * Called by the scheduler after each step: drops the time still asked for
	 * if the test given with the request says to stop.
	 */
	public static void checkStop()
	{
		final BooleanSupplier until = stopWhen;
		if((until != null) && (pending > 0) && until.getAsBoolean())
			cancelAdvance();
	}

	/**
	 * Sets the game clock, keeping it running or stopped as it was.
	 * @param gameMs the game time it should now read
	 */
	public static synchronized void set(final long gameMs)
	{
		final State s = state;
		if(s.stoppedAt != 0)
			state = new State(s.stoppedAt - gameMs, s.stoppedAt);
		else
			state = new State(System.currentTimeMillis() - gameMs, 0);
	}
}
