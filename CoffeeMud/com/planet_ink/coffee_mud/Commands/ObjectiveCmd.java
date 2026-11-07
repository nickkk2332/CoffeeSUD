package com.planet_ink.coffee_mud.Commands;

import java.util.List;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.*;

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
/** G03: OBJECTIVE, the encounter in this room: its phase, warning, objective and foes, in plain text.  It takes no game time. */
public class ObjectiveCmd extends StdCommand
{
	private final String[] access = I(new String[] { "OBJECTIVE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public double actionsCost(final MOB mob, final List<String> words)
	{
		return 0;
	}

	@Override
	public double combatActionsCost(final MOB mob, final List<String> words)
	{
		return 0;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> words, final int flags) throws java.io.IOException
	{
		if(!EncounterDirectors.enabled())
		{
			mob.tell(L("Objective: the encounter director (@x1) is not active in this campaign.", EncounterDirectors.MODULE));
			return false;
		}
		final EncounterDirectors.Director d = EncounterDirectors.in(mob.location());
		mob.tell((d == null) ? L("There is no encounter here.") : d.describe(mob, false));
		return false;
	}
}
