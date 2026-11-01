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
 * PREVIEW (DT17): the conditions a room's &lt;VARIES&gt; text is read under,
 * for one thread, instead of the world's: the time of day, the season, the
 * weather, where the reader came from and whether they have been there.  The
 * text's parser asks here first; a condition not set is the world's.
 */
public final class TextPreview
{
	/** a condition not set: the world's is used */
	public static final int UNSET = Integer.MIN_VALUE;

	/** the time of day (TimeClock.TimeOfDay ordinal) */
	public int		timeOfDay	= UNSET;
	/** the season (TimeClock.Season ordinal) */
	public int		season		= UNSET;
	/** the weather (Climate.WEATHER_ code) */
	public int		weather		= UNSET;
	/** the direction come from (Directions code), -1 for unknown */
	public int		from		= UNSET;
	/** whether the room was visited: 1, 0, or UNSET */
	public int		visited		= UNSET;

	private static final ThreadLocal<TextPreview> current = new ThreadLocal<TextPreview>();

	/**
	 * The conditions in force on this thread, if PREVIEW set any.
	 * @return the conditions, or null
	 */
	public static TextPreview current()
	{
		return current.get();
	}

	/**
	 * Reads something under these conditions.
	 * @param r what reads the text
	 */
	public void run(final Runnable r)
	{
		final TextPreview was = current.get();
		current.set(this);
		try
		{
			r.run();
		}
		finally
		{
			if(was == null)
				current.remove();
			else
				current.set(was);
		}
	}
}
