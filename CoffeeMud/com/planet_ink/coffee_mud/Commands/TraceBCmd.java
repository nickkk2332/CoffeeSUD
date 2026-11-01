package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
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
 * TRACEB target [ticks] (DT19): tells you the decisions of a host's timed
 * behaviours for a while ({@link BehaviorTrace}); TRACEB OFF stops.  STAT
 * BEHAVIORS target shows each behaviour's state ({@link #describeBehaviors}).
 */
public class TraceBCmd extends StdCommand
{
	public TraceBCmd()
	{
	}

	private final String[] access = I(new String[] { "TRACEB" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * STAT BEHAVIORS: each behaviour of a host, its state as getStat DEBUG
	 * tells it, and the keys of its parameters its help does not name.
	 * @param mob the one asking
	 * @param what a mob or item here, ROOM or HERE, or name@room
	 * @return the description
	 */
	public static String describeBehaviors(final MOB mob, final String what)
	{
		final String name = (what == null) || (what.trim().length() == 0) ? "HERE" : what.trim();
		final PhysicalAgent host = ScriptCmd.findTarget(mob, name);
		if(host == null)
			return CMLib.lang().L("There is no '@x1' here.", name);
		final StringBuilder str = new StringBuilder(CMLib.lang().L("Behaviours of @x1:", host.Name()));
		int n = 0;
		for(final Enumeration<Behavior> e = host.behaviors(); e.hasMoreElements();)
		{
			final Behavior B = e.nextElement();
			n++;
			str.append("\n\r").append(n).append(". ").append(B.ID()).append(": ").append(B.getStat("DEBUG"));
			// a behaviour's help is BEHAVIOR_ID, or else its ID
			final String helpID = PropCheck.keysOf("BEHAVIOR_" + B.ID()).isEmpty() ? B.ID() : ("BEHAVIOR_" + B.ID());
			for(final String k : PropCheck.unknownKeys(helpID, B.getParms()))
				str.append("\n\r   ").append(CMLib.lang().L("unknown key @x1 (AHELP @x2)", k, helpID.toUpperCase()));
		}
		if(n == 0)
			str.append(" ").append(CMLib.lang().L("none."));
		return str.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 2)
		{
			mob.tell(L("TRACEB [MOB/ITEM/HERE] ([TICKS]), or TRACEB OFF."));
			return false;
		}
		if(commands.get(1).equalsIgnoreCase("OFF"))
		{
			BehaviorTrace.stop();
			mob.tell(L("TRACEB is off."));
			return false;
		}
		int ticks = 20;
		final List<String> rest = new ArrayList<String>(commands.subList(1, commands.size()));
		if((rest.size() > 1) && CMath.isInteger(rest.get(rest.size() - 1)))
			ticks = CMath.s_int(rest.remove(rest.size() - 1));
		final String name = CMParms.combine(rest, 0);
		final PhysicalAgent host = ScriptCmd.findTarget(mob, name);
		if(host == null)
		{
			mob.tell(L("There is no '@x1' here.", name));
			return false;
		}
		BehaviorTrace.start(mob, host, ticks);
		mob.tell(L("TRACEB: the decisions of @x1's timed behaviours, for @x2 ticks.", host.Name(), "" + ticks));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDMOBS)
			|| CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS);
	}
}
