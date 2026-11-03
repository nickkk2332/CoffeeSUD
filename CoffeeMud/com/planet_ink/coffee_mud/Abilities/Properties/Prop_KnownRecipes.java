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
 * CR4: the recipes a character knows, kept with them.  Each line of its
 * text is SKILLID|NAME (one of the skill's own recipes, as one marked
 * HIDDEN=1) or SKILLID|a whole recipe line (tabs and all).  The whole lines
 * are added to the skill's recipes, as a recipe book's are; the names make
 * HIDDEN=1 recipes known.
 */
public class Prop_KnownRecipes extends Property implements Recipes, KnownRecipes.Known
{
	@Override
	public String ID()
	{
		return "Prop_KnownRecipes";
	}

	@Override
	public String name()
	{
		return "Known recipes";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	protected final List<String[]>	entries	= new ArrayList<String[]>();

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		synchronized(entries)
		{
			entries.clear();
			for(final String line : newMiscText.split("\\n"))
			{
				final int x = line.indexOf('|');
				if((x > 0) && (x < line.length() - 1))
					entries.add(new String[] { line.substring(0, x).trim().toUpperCase(), line.substring(x + 1) });
			}
		}
	}

	@Override
	public String text()
	{
		final StringBuilder str = new StringBuilder("");
		synchronized(entries)
		{
			for(final String[] e : entries)
				str.append(e[0]).append('|').append(e[1]).append('\n');
		}
		return str.toString();
	}

	@Override
	public String accountForYourself()
	{
		return "Knows " + entries.size() + " recipe(s) of their own";
	}

	@Override
	public String[] linesFor(final String skillID)
	{
		final List<String> lines = new ArrayList<String>();
		synchronized(entries)
		{
			for(final String[] e : entries)
			{
				if(e[0].equalsIgnoreCase(skillID) && (e[1].indexOf('\t') > 0))
					lines.add(e[1]);
			}
		}
		return lines.toArray(new String[0]);
	}

	@Override
	public boolean knows(final String skillID, final String name)
	{
		synchronized(entries)
		{
			for(final String[] e : entries)
			{
				if(!e[0].equalsIgnoreCase(skillID))
					continue;
				final int t = e[1].indexOf('\t');
				if(KnownRecipes.same((t > 0) ? e[1].substring(0, t) : e[1], name))
					return true;
			}
		}
		return false;
	}

	@Override
	public boolean add(final String skillID, final String nameOrLine)
	{
		if((skillID == null) || (nameOrLine == null) || (nameOrLine.trim().length() == 0))
			return false;
		final String line = nameOrLine.replace('\n', ' ').replace('\r', ' ');
		final int t = line.indexOf('\t');
		final String name = (t > 0) ? line.substring(0, t) : line.trim();
		synchronized(entries)
		{
			for(final String[] e : entries)
			{
				if(e[0].equalsIgnoreCase(skillID) && (e[1].equals((t > 0) ? line : line.trim())))
					return false;
			}
			if((t <= 0) && knows(skillID, name))
				return false;
			entries.add(new String[] { skillID.trim().toUpperCase(), (t > 0) ? line : line.trim() });
		}
		super.setMiscText(text());
		return true;
	}

	// Recipes: the lines are given by linesFor, to the skill that asks
	@Override
	public String getCommonSkillID()
	{
		return "";
	}

	@Override
	public void setCommonSkillID(final String ID)
	{
	}

	@Override
	public String[] getRecipeCodeLines()
	{
		return new String[0];
	}

	@Override
	public void setRecipeCodeLines(final String[] lines)
	{
	}
}
