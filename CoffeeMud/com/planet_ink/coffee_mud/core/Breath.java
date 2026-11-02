package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.util.*;

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
 * W18: a breath count for everyone.  Where a mob cannot breathe, each tick
 * spends a tick of breath before it chokes or drowns: BREATHTICKS (a
 * formula in coffeemud.ini or world.ini, @x1 constitution, @x2 level; 0 for
 * none) and the TICKS= of each Prop_AirSupply it carries.  It is warned at
 * half and at a quarter; air it can breathe fills it again.
 */
public final class Breath
{
	private Breath()
	{
	}

	/** the breath each mob has left, while it is short of air */
	private static final Map<MOB, int[]>	left	= Collections.synchronizedMap(new WeakHashMap<MOB, int[]>());

	/**
	 * A mob's whole breath.
	 * @param M the mob
	 * @return the ticks it can go without air
	 */
	public static int max(final MOB M)
	{
		if(M == null)
			return 0;
		String formula = CMProps.instance().getProperty("BREATHTICKS");
		if(formula == null)
			formula = "@x1/3";
		int base = 0;
		if(formula.trim().length() > 0)
		{
			try
			{
				base = (int)Math.floor(CMath.parseMathExpression(formula.replace("@x1", "" + M.charStats().getStat(CharStats.STAT_CONSTITUTION))
					.replace("@x2", "" + M.phyStats().level())));
			}
			catch(final Exception e)
			{
				base = 0;
			}
		}
		for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
		{
			final Item I = i.nextElement();
			final Ability A = (I == null) ? null : I.fetchEffect("Prop_AirSupply");
			if(A != null)
				base += CMParms.getParmInt(A.text(), "TICKS", 0);
		}
		return Math.max(0, base);
	}

	/**
	 * @param M a mob
	 * @return the ticks of breath it has left (all of it where it can breathe)
	 */
	public static int remaining(final MOB M)
	{
		final int[] l = left.get(M);
		return (l == null) ? max(M) : l[0];
	}

	/** the air it can breathe: its breath is whole again */
	public static void refill(final MOB M)
	{
		if(left.remove(M) != null)
			M.tell(CMLib.lang().L("You gasp in a breath of air."));
	}

	/**
	 * A tick without air: a tick of breath spent, and a warning at half and
	 * at a quarter.
	 * @param M the mob
	 * @return true if it still had breath (no harm this tick), false once it is gone
	 */
	public static boolean hold(final MOB M)
	{
		int[] l = left.get(M);
		final int max = max(M);
		if(l == null)
		{
			l = new int[] { max };
			left.put(M, l);
		}
		if(l[0] <= 0)
			return false;
		l[0]--;
		if((max >= 2) && (l[0] == (max / 2)))
			M.tell(CMLib.lang().L("^ZYou have half your breath left.^?"));
		else
		if((max >= 4) && (l[0] == (max / 4)))
			M.tell(CMLib.lang().L("^ZYour lungs burn: you are nearly out of breath!^?"));
		else
		if(l[0] == 0)
			M.tell(CMLib.lang().L("^ZYour breath is gone!^?"));
		return true;
	}
}
