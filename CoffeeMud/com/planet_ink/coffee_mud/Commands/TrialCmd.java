package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
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
 * G04: TRIAL shows the escalating trial here (its round, what was chosen and
 * what is offered); TRIAL CHOOSE [n] takes an offered modifier and goes on;
 * TRIAL STOP banks what was won and ends the trial.
 */
public class TrialCmd extends StdCommand
{
	public TrialCmd()
	{
	}

	private final String[] access = I(new String[] { "TRIAL" });

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
		final Behavior B = (R == null) ? null : R.fetchBehavior("EncounterDirector");
		if(!(B instanceof EncounterDirectors.Director))
		{
			mob.tell(L("There is no trial here."));
			return false;
		}
		mob.tell(((EncounterDirectors.Director)B).trial(mob, CMParms.combine(commands, 1)));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}
