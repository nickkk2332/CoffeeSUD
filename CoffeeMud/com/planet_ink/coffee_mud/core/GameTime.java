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
/**
 * The game clock, in milliseconds.  Everything that times something in the
 * world (cooldowns, durations, contracts, decay, command pacing) reads this
 * instead of System.currentTimeMillis().  It runs with the system clock,
 * except that it stands still while the game is paused, can be moved forward
 * by hand (turn-based play), and can be set (loading a saved game).
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

	private static volatile State state = new State(0, 0);

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
	 * Returns whether the game clock is stopped.
	 * @return true if paused
	 */
	public static boolean isPaused()
	{
		return state.stoppedAt != 0;
	}

	/**
	 * Stops the game clock.  Does nothing if it is already stopped.
	 */
	public static synchronized void pause()
	{
		final State s = state;
		if(s.stoppedAt == 0)
			state = new State(s.behind, System.currentTimeMillis());
	}

	/**
	 * Starts the game clock again from where it stopped.  Does nothing if it
	 * is running.
	 */
	public static synchronized void resume()
	{
		final State s = state;
		if(s.stoppedAt != 0)
			state = new State(s.behind + (System.currentTimeMillis() - s.stoppedAt), 0);
	}

	/**
	 * Moves the game clock forward, whether it is running or stopped.
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
