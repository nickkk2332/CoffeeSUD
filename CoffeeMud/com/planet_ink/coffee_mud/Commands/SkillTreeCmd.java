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
 * P24: SKILLTREE ([CLASS]|[SKILL]): the skills by their REQUIRES chains.
 */
public class SkillTreeCmd extends StdCommand
{
	public SkillTreeCmd()
	{
	}

	private final String[] access = I(new String[] { "SKILLTREE", "STREE" });

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
			mob.tell(SkillTree.tree(mob, mob.charStats().getCurrentClass()));
			return false;
		}
		final String name = CMParms.combine(commands, 1);
		final CharClass C = CMClass.findCharClass(name);
		if(C != null)
		{
			mob.tell(SkillTree.tree(mob, C));
			return false;
		}
		Ability A = CMClass.getAbility(name);
		if(A == null)
			A = CMClass.findAbility(name);
		if(A == null)
		{
			mob.tell(L("There is no class or skill called '@x1'.", name));
			return false;
		}
		mob.tell(SkillTree.chain(mob, A));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}
