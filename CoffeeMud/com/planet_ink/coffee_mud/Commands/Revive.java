package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
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
 * C8: REVIVE [ally]: brings one who is down (PLAYERDEATH's DOWNED) back up,
 * with a tenth of their hit points.
 */
public class Revive extends StdCommand
{
	public Revive()
	{
	}

	private final String[] access = I(new String[] { "REVIVE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final Room R = mob.location();
		if(R == null)
			return false;
		if(Downed.isDown(mob))
		{
			mob.tell(L("You are down yourself."));
			return false;
		}
		MOB target = null;
		final String name = CMParms.combine(commands, 1).trim();
		if(name.length() > 0)
			target = R.fetchInhabitant(name);
		else
		{
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && (M != mob) && Downed.isDown(M))
				{
					target = M;
					break;
				}
			}
		}
		if((target == null) || (!Downed.isDown(target)))
		{
			mob.tell(L("There is no one down here to revive."));
			return false;
		}
		R.show(mob, target, CMMsg.MSG_OK_ACTION, L("<S-NAME> help(s) <T-NAME> up."));
		Downed.revive(target, Math.max(1, target.maxState().getHitPoints() / 10));
		return true;
	}

	@Override
	public double combatActionsCost(final MOB mob, final List<String> cmds)
	{
		return 1.0;
	}

	@Override
	public double actionsCost(final MOB mob, final List<String> cmds)
	{
		return 1.0;
	}

	@Override
	public boolean canBeOrdered()
	{
		return true;
	}
}
