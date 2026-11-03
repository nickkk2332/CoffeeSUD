package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * P9: RECRUIT [NPC]: an NPC here with Prop_Recruitable joins the profile and
 * the party.
 */
public class RecruitCmd extends StdCommand
{
	public RecruitCmd()
	{
	}

	private final String[] access = I(new String[] { "RECRUIT" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 2)
		{
			mob.tell(L("Recruit whom?"));
			return false;
		}
		final Room R = mob.location();
		final MOB npc = (R == null) ? null : R.fetchInhabitant(CMParms.combine(commands, 1));
		if((npc == null) || (npc == mob))
		{
			mob.tell(L("You see no '@x1' here.", CMParms.combine(commands, 1)));
			return false;
		}
		final String why = Recruits.recruitHere(mob, npc);
		if(why != null)
			mob.tell(why);
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}
