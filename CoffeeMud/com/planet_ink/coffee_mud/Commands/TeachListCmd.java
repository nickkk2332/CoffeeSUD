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
 * P16: TEACHLIST ([teacher]): what a teacher here offers you.
 */
public class TeachListCmd extends StdCommand
{
	public TeachListCmd()
	{
	}

	private final String[] access = I(new String[] { "TEACHLIST" });

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
		MOB teacher = null;
		if(commands.size() > 1)
			teacher = R.fetchInhabitant(CMParms.combine(commands, 1));
		else
		{
			for(int i = 0; i < R.numInhabitants(); i++)
			{
				final MOB M = R.fetchInhabitant(i);
				if((M != null) && (M != mob) && ((M.fetchBehavior("MOBTeacher") != null) || (M.fetchEffect("Prop_Trainer") != null)))
				{
					teacher = M;
					break;
				}
			}
		}
		if((teacher == null) || (teacher == mob))
		{
			mob.tell(L("There is no teacher here."));
			return false;
		}
		mob.tell(TeacherRules.teachList(teacher, mob));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}
