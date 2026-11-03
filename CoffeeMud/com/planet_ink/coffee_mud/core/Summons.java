package com.planet_ink.coffee_mud.core;

import java.util.List;

import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
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

/**
 * P19: what a generic ability's SUMMONFLAGS take, and sending a summoned mob
 * away (Prop_Summoned, GenAbility).
 */
public class Summons
{
	private Summons()
	{
	}

	/** the SUMMONFLAGS words */
	public static final String[] FLAGS={"FOLLOW","ASSIST","NOSAVE","DISMISSONEXPIRE","BIND"};

	/**
	 * SUMMONFLAGS as given, kept to the known words, upper case, in their order.
	 * @param val the stat value
	 * @return the flags
	 */
	public static String flags(final String val)
	{
		final List<String> words=CMParms.parseAny((val==null)?"":val.toUpperCase().replace(',', ' '), ' ', true);
		final StringBuilder s=new StringBuilder("");
		for(final String f : FLAGS)
		{
			if(words.contains(f))
				s.append(f).append(' ');
		}
		return s.toString().trim();
	}

	/**
	 * Sends a summoned mob away: it departs, stops following and is gone.
	 * @param M the summoned mob
	 */
	public static void dismiss(final MOB M)
	{
		if((M==null)||M.amDestroyed())
			return;
		final Room R=M.location();
		if((R!=null)&&(!M.amDead()))
			R.show(M, null, CMMsg.MSG_OK_VISUAL, CMLib.lang().L("<S-NAME> depart(s)."));
		if(M.amFollowing()!=null)
			M.setFollowing(null);
		M.destroy();
	}

	/**
	 * @param M a follower
	 * @return true if it was summoned with NOSAVE, so a save leaves it out
	 */
	public static boolean leftOutOfSaves(final MOB M)
	{
		return (M!=null)&&(!CMLib.flags().isSavable(M))&&(M.fetchEffect("Prop_Summoned")!=null);
	}
}
