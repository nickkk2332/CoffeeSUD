package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * C13: DIFFICULTY [name]: says the game's difficulty and the presets, or
 * changes it (not in a fight).  The save keeps it.  See core/GameDifficulty.
 */
public class Difficulty extends StdCommand
{
	public Difficulty()
	{
	}

	private final String[]	access	= I(new String[] { "DIFFICULTY" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	protected String describe(final GameDifficulty.Preset P)
	{
		return L("enemies deal @x1% damage and have @x2% hit points; your side deals @x3% damage and gains @x4% experience",
			"" + P.npcDamage, "" + P.npcHp, "" + P.playerDamage, "" + P.playerXp);
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final String what = CMParms.combine(commands, 1).trim();
		if(what.length() == 0)
		{
			final String cur = GameDifficulty.current();
			final StringBuilder str = new StringBuilder(L("The game is on @x1: @x2.\n\r",
				CMStrings.capitalizeAndLower(cur), describe(GameDifficulty.inForce())));
			str.append(L("DIFFICULTY <name> changes it: "));
			final List<String> names = new ArrayList<String>();
			for(final String n : GameDifficulty.names())
				names.add(CMStrings.capitalizeAndLower(n));
			str.append(CMLib.english().toEnglishStringList(names)).append(".\n\r");
			mob.tell(str.toString());
			return false;
		}
		final String n = GameDifficulty.find(what);
		if(n == null)
		{
			mob.tell(L("There is no difficulty called '@x1'.  DIFFICULTY lists them.", what));
			return false;
		}
		if(mob.isInCombat())
		{
			mob.tell(L("Not in the middle of a fight."));
			return false;
		}
		GameDifficulty.set(n, mob.location());
		mob.tell(L("The game is now on @x1: @x2.", CMStrings.capitalizeAndLower(n), describe(GameDifficulty.inForce())));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}
