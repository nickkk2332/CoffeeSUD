package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
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
 * DT33, in builder mode: PUZZLE CHECK [AREA name] checks the places, doors
 * and things the world's scripts name ({@link PuzzleCheck}).  The listing and
 * the console (PUZZLE, PUZZLE SET, SIGNAL, RESET) come with the switch,
 * plate and controller properties (I12).
 */
public class PuzzleCmd extends StdCommand
{
	public PuzzleCmd()
	{
	}

	private final String[] access = I(new String[] { "PUZZLE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * PUZZLE CHECK's report.
	 * @param A the area, or null for the world
	 * @return the report
	 */
	public static String report(final Area A)
	{
		final List<String> problems = (A == null) ? PuzzleCheck.checkAll() : PuzzleCheck.checkArea(A);
		final StringBuilder str = new StringBuilder("PUZZLE CHECK " + ((A == null) ? "the world" : A.Name()) + ": " + problems.size() + " problems.");
		for(final String p : problems)
			str.append("\n\r  ").append(p);
		return str.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if((commands.size() < 2) || (!commands.get(1).equalsIgnoreCase("CHECK")))
		{
			mob.tell(L("PUZZLE CHECK (AREA [NAME]) checks the rooms, doors and things scripts name.  "
				+ "The listing and the console (PUZZLE, PUZZLE SET, SIGNAL and RESET) come with the switch, plate and controller properties."));
			return false;
		}
		Area A = null;
		if((commands.size() > 2) && commands.get(2).equalsIgnoreCase("AREA"))
		{
			final String name = CMParms.combine(commands, 3);
			A = (name.length() == 0) ? ((mob.location() == null) ? null : mob.location().getArea()) : CMLib.map().getArea(name);
			if(A == null)
			{
				mob.tell(L("There is no area '@x1'.", name));
				return false;
			}
		}
		mob.tell(report(A));
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
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS);
	}
}
