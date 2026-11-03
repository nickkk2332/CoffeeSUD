package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

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
 * W28: a zone locked to one time mode: MODE=TURNS or REALTIME, while one
 * played is in its room or area (ONLYCOMBAT=TRUE: only while they fight
 * there), the mode before put back as they leave (RESTORE=TRUE, the
 * default); MSG= said as it switches.  core/TimeLock does it.
 */
public class Prop_TimeMode extends Property
{
	@Override
	public String ID()
	{
		return "Prop_TimeMode";
	}

	@Override
	public String name()
	{
		return "A zone of its own time";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS | Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "time held " + (CMParms.getParmStr(text(), "MODE", "TURNS").toUpperCase().startsWith("REAL") ? "running" : "turn by turn");
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		// ONLYCOMBAT: a fight starting or ending here
		if(msg.source().isPlayer()
		&& ((msg.targetMinor() == CMMsg.TYP_DAMAGE) || (msg.targetMinor() == CMMsg.TYP_WEAPONATTACK) || (msg.sourceMinor() == CMMsg.TYP_DEATH))
		&& CMParms.getParmStr(text(), "ONLYCOMBAT", "false").equalsIgnoreCase("TRUE"))
		{
			final MOB M = msg.source();
			msg.addTrailerRunnable(new Runnable()
			{
				@Override
				public void run()
				{
					TimeLock.check(M);
				}
			});
		}
	}
}
