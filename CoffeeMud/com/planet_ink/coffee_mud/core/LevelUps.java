package com.planet_ink.coffee_mud.core;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
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
 * P2: exact level-ups and level-downs from scripts and JavaScript (MPLEVELUP,
 * MPLEVELDOWN, MPEXP $n NEXTLEVEL, EXPTONEXT()), whatever the character's
 * experience within its level.
 */
public class LevelUps
{
	private LevelUps()
	{
	}

	/**
	 * @param M a character
	 * @return the experience it needs for its next level, 0 when it has none
	 */
	public static int toNext(final MOB M)
	{
		if(M == null)
			return 0;
		final int n = M.getExpNeededLevel();
		return (n == Integer.MAX_VALUE) ? 0 : Math.max(0, n);
	}

	/**
	 * Raises a character exactly n levels: the experience each level needs, or
	 * a plain level for one with no experience.
	 * @param M the character
	 * @param n how many
	 * @param source what the experience is for, as SCRIPT:quest
	 * @return the levels gained
	 */
	public static int up(final MOB M, final int n, final String source)
	{
		if(M == null)
			return 0;
		final int was = M.basePhyStats().level();
		for(int i = 0; i < n; i++)
		{
			final int before = M.basePhyStats().level();
			if(M.getExpNeededLevel() == Integer.MAX_VALUE)
				CMLib.leveler().level(M);
			else
				CMLib.leveler().postExperience(M, (source == null) ? "SCRIPT:LEVELUP" : source, null, null, M.getExpNeededLevel() + 1, false);
			M.recoverPhyStats();
			M.recoverCharStats();
			M.recoverMaxState();
			if(M.basePhyStats().level() <= before)
				break; // capped, or refused
		}
		return M.basePhyStats().level() - was;
	}

	/**
	 * Lowers a character n levels, its experience set back with them.
	 * @param M the character
	 * @param n how many
	 * @return the levels lost
	 */
	public static int down(final MOB M, final int n)
	{
		if(M == null)
			return 0;
		final int was = M.basePhyStats().level();
		for(int i = 0; i < n; i++)
		{
			final int before = M.basePhyStats().level();
			CMLib.leveler().unLevel(M, true);
			M.recoverPhyStats();
			M.recoverCharStats();
			M.recoverMaxState();
			if(M.basePhyStats().level() >= before)
				break;
		}
		return was - M.basePhyStats().level();
	}
}
