package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * I10: COMBINE [ITEM] WITH [ITEM]: COMBINE_PROG on either, then the table
 * of world/combinations.txt (core/Combinations).
 */
public class Combine extends StdCommand
{
	public Combine()
	{
	}

	private final String[] access = I(new String[] { "COMBINE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(mob.location() == null)
			return false;
		final String rest = CMParms.combine(commands, 1).trim();
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^(.+?)\\s+(?:with|and)\\s+(.+)$").matcher(rest);
		if(!m.matches())
		{
			mob.tell(L("Combine what with what?"));
			return false;
		}
		final Environmental A = Combinations.thing(mob, m.group(1).trim());
		if(!(A instanceof Item))
		{
			mob.tell(L("You don't have '@x1'.", m.group(1).trim()));
			return false;
		}
		Environmental B = null;
		// the second: another one of the same name, if it is
		final String bn = m.group(2).trim();
		if(CMLib.english().containsString(A.Name(), bn) || A.Name().equalsIgnoreCase(bn))
		{
			for(final Enumeration<Item> i = mob.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				if((I != A) && (I.container() == null) && (CMLib.english().containsString(I.Name(), bn) || I.Name().equalsIgnoreCase(bn)))
				{
					B = I;
					break;
				}
			}
		}
		if(B == null)
			B = Combinations.thing(mob, bn);
		if((!(B instanceof Item)) || (B == A))
		{
			mob.tell(L("You don't have another '@x1'.", bn));
			return false;
		}
		return Combinations.act(mob, "COMBINE", A, B);
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
