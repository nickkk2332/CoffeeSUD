package com.planet_ink.coffee_mud.core;

import java.util.List;

import com.planet_ink.coffee_mud.Common.interfaces.PlayerAccount;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
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
 * P15: the level a character made later in the game starts at:
 * NEWCHARLEVEL=1|N|PARTYMIN|PARTYAVG|LEADER, each with an optional +n or -n
 * (PARTYMIN-1), reached by real level-ups at the end of creation;
 * NEWCHARXP=KEEP (the experience the level-ups leave) or FLOOR (the least the
 * level has).
 */
public class StartLevel
{
	private StartLevel()
	{
	}

	private static String str(final String key, final String dflt)
	{
		final CMProps P = CMProps.instance();
		return (P == null) ? dflt : P.getStr(key, dflt).trim();
	}

	/**
	 * The level a new character of a profile starts at.
	 * @param mob the character being made
	 * @param acct its profile
	 * @return the level, 1 at least
	 */
	public static int target(final MOB mob, final PlayerAccount acct)
	{
		String v = str("NEWCHARLEVEL", "1").toUpperCase().replace(" ", "");
		int offset = 0;
		final int x = Math.max(v.lastIndexOf('+'), v.lastIndexOf('-'));
		if((x > 0) && CMath.isInteger(v.substring(x + 1)))
		{
			offset = CMath.s_int(v.substring(x + 1)) * ((v.charAt(x) == '-') ? -1 : 1);
			v = v.substring(0, x);
		}
		int base;
		if(CMath.isInteger(v))
			base = CMath.s_int(v);
		else
		{
			final List<MOB> others = WorldRules.profileCharacters(acct);
			int min = Integer.MAX_VALUE;
			long sum = 0;
			int n = 0;
			for(final MOB M : others)
			{
				if((M == null) || (mob != null && M.Name().equalsIgnoreCase(mob.Name())))
					continue;
				final int L = M.basePhyStats().level();
				min = Math.min(min, L);
				sum += L;
				n++;
			}
			if(n == 0)
				return 1;
			if(v.startsWith("PARTYMIN"))
				base = min;
			else
			if(v.startsWith("PARTYAVG"))
				base = (int)Math.round((double)sum / n);
			else
			if(v.startsWith("LEADER"))
			{
				final MOB L = WorldRules.lastPlayed(acct);
				base = ((L == null) || ((mob != null) && L.Name().equalsIgnoreCase(mob.Name()))) ? min : L.basePhyStats().level();
			}
			else
			{
				Log.errOut("StartLevel", "NEWCHARLEVEL not understood: " + str("NEWCHARLEVEL", ""));
				base = 1;
			}
		}
		final int cap = CMProps.getIntVar(CMProps.Int.LASTPLAYERLEVEL);
		int level = Math.max(1, base + offset);
		if(cap > 0)
			level = Math.min(level, cap);
		return level;
	}

	/**
	 * Raises a character being made to its starting level by real level-ups
	 * (hit points, practices, trains and the skills levels bring).
	 * @param mob the character
	 * @param acct its profile
	 * @return the levels gained
	 */
	public static int apply(final MOB mob, final PlayerAccount acct)
	{
		if((mob == null) || (acct == null))
			return 0;
		final int to = target(mob, acct);
		final int now = mob.basePhyStats().level();
		if(to <= now)
			return 0;
		final Room was = mob.location();
		if(was == null)
			mob.setLocation(mob.getStartRoom());
		try
		{
			final int gained = LevelUps.up(mob, to - now, "CHARCREATION:NEWCHARLEVEL");
			if(str("NEWCHARXP", "KEEP").toUpperCase().startsWith("FLOOR") && (mob.basePhyStats().level() > 1))
				mob.setExperience(CMLib.leveler().getLevelExperience(mob, mob.basePhyStats().level() - 1));
			mob.recoverPhyStats();
			mob.recoverCharStats();
			mob.recoverMaxState();
			mob.resetToMaxState();
			return gained;
		}
		finally
		{
			if(was == null)
				mob.setLocation(null);
		}
	}
}
