package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * DT29, for builders: VALIDATE RECIPES [skill|ALL] checks every row of the
 * chosen skills' recipe files and their extension files (each value as
 * MODIFY RECIPE would take it, and the column counts), the gathering tables
 * (hunting.txt's resources, places and mobs, fishing.txt's) and
 * components.txt, and reports every problem with its file and row.
 */
public class ValidateCmd extends StdCommand
{
	public ValidateCmd()
	{
	}

	private final String[] access = I(new String[] { "VALIDATE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * A skill's recipe problems.
	 * @param R the skill
	 * @return the problems, or none if it has no recipe file
	 */
	public static List<String> recipeProblems(final RecipeDriven R)
	{
		if((R.getRecipeFormat() == null) || (R.getRecipeFormat().length() == 0)
		|| (R.getRecipeFilename() == null) || (R.getRecipeFilename().length() == 0))
			return new ArrayList<String>(0);
		return CMLib.ableParms().validateRecipes(R.getRecipeFilename(), R.getRecipeFormat());
	}

	private static List<String> rows(final String file)
	{
		final List<String> rows = new ArrayList<String>();
		final StringBuffer text = new CMFile(Resources.buildResourcePath("skills") + file, null, CMFile.FLAG_LOGERRORS).text();
		if(text == null)
			return rows;
		for(final String line : text.toString().replace('\r', '\n').split("\n"))
		{
			if((line.trim().length() > 0) && (!line.startsWith("#")))
				rows.add(line);
		}
		return rows;
	}

	private static boolean isDomain(final String d)
	{
		if(d.equals("*"))
			return true;
		for(final String s : Room.DOMAIN_OUTDOOR_DESCS)
		{
			if(s.equalsIgnoreCase(d))
				return true;
		}
		for(final String s : Room.DOMAIN_INDOORS_DESCS)
		{
			if(s.equalsIgnoreCase(d))
				return true;
		}
		return (CMClass.getLocale(d) != null) || (CMLib.map().getRoom(d) != null);
	}

	/**
	 * The gathering tables' problems: hunting.txt (resource, place,
	 * frequency, mob or race) and fishing.txt (resource, frequency, name,
	 * value).
	 * @return the problems
	 */
	public static List<String> gatheringProblems()
	{
		final List<String> problems = new ArrayList<String>();
		int n = 0;
		for(final String line : rows("hunting.txt"))
		{
			n++;
			final String[] c = line.split("\t", -1);
			if(c.length < 4)
			{
				problems.add("hunting.txt row " + n + ": " + c.length + " columns, and it needs 4");
				continue;
			}
			if(CMLib.materials().findResourceCode(c[0].trim(), true) < 0)
				problems.add("hunting.txt row " + n + ": resource '" + c[0].trim() + "' unknown");
			if(!isDomain(c[1].trim()))
				problems.add("hunting.txt row " + n + ": place '" + c[1].trim() + "' is no domain, locale or room");
			if(!CMath.isInteger(c[2].trim()))
				problems.add("hunting.txt row " + n + ": frequency '" + c[2].trim() + "' is not a number");
			final String mob = c[3].trim();
			if((CMClass.getMOB(mob) == null) && (CMClass.getRace(mob) == null))
				problems.add("hunting.txt row " + n + ": mob or race '" + mob + "' unknown");
		}
		n = 0;
		for(final String line : rows("fishing.txt"))
		{
			n++;
			final String[] c = line.split("\t", -1);
			if(c.length < 2)
			{
				problems.add("fishing.txt row " + n + ": " + c.length + " columns, and it needs at least 2");
				continue;
			}
			if(CMLib.materials().findResourceCode(c[0].trim(), true) < 0)
				problems.add("fishing.txt row " + n + ": resource '" + c[0].trim() + "' unknown");
			if(!CMath.isInteger(c[1].trim()))
				problems.add("fishing.txt row " + n + ": frequency '" + c[1].trim() + "' is not a number");
			if((c.length > 3) && (c[3].trim().length() > 0) && (!CMath.isInteger(c[3].trim())))
				problems.add("fishing.txt row " + n + ": value '" + c[3].trim() + "' is not a number");
		}
		return problems;
	}

	/**
	 * components.txt's problems: each entry parsed as the game parses it, and
	 * its ability looked for.
	 * @return the problems
	 */
	public static List<String> componentProblems()
	{
		final List<String> problems = new ArrayList<String>();
		int n = 0;
		final Map<String, List<AbilityComponent>> scratch = new HashMap<String, List<AbilityComponent>>();
		for(final String line : rows("components.txt"))
		{
			n++;
			final int x = line.indexOf('=');
			if(x < 0)
				continue; // a social's component line, as the game takes them
			final String id = line.substring(0, x).trim();
			if((CMClass.getAbility(id) == null) && (CMClass.findAbility(id) == null))
				problems.add("components.txt row " + n + ": ability '" + id + "' unknown");
			final String err = CMLib.ableComponents().addAbilityComponent(line, scratch);
			if(err != null)
				problems.add("components.txt row " + n + ": " + err);
		}
		return problems;
	}

	/**
	 * VALIDATE RECIPES: the report for one skill or all.
	 * @param what a skill's ID or name, or ALL
	 * @return the report
	 */
	public static String report(final String what)
	{
		final List<String> problems = new ArrayList<String>();
		int skills = 0;
		if(what.equalsIgnoreCase("ALL"))
		{
			for(final Enumeration<Ability> e = CMClass.abilities(); e.hasMoreElements();)
			{
				final Ability A = e.nextElement();
				if(A instanceof RecipeDriven)
				{
					final List<String> got = recipeProblems((RecipeDriven)A);
					skills++;
					for(final String p : got)
					{
						if(!problems.contains(p)) // skills that share a file report it once
							problems.add(p);
					}
				}
			}
			problems.addAll(gatheringProblems());
			problems.addAll(componentProblems());
		}
		else
		{
			Ability A = CMClass.getAbility(what);
			if(A == null)
				A = CMClass.findAbility(what, Ability.ACODE_COMMON_SKILL, -1, false);
			if(A == null)
				return "There is no skill '" + what + "'.";
			skills = 1;
			if(A instanceof RecipeDriven)
				problems.addAll(recipeProblems((RecipeDriven)A));
			if(A.ID().equals("Hunting") || A.ID().equals("Fishing"))
			{
				final String file = A.ID().toLowerCase() + ".txt";
				for(final String p : gatheringProblems())
				{
					if(p.startsWith(file))
						problems.add(p);
				}
			}
			else
			if(!(A instanceof RecipeDriven))
				return A.ID() + " has no recipes.";
		}
		final StringBuilder str = new StringBuilder("VALIDATE RECIPES: " + problems.size() + " problems in " + skills + " skills.");
		for(final String p : problems)
			str.append("\n\r  ").append(p);
		return str.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if((commands.size() < 2) || (!commands.get(1).equalsIgnoreCase("RECIPES")))
		{
			mob.tell(L("VALIDATE RECIPES ([SKILL]/ALL)"));
			return false;
		}
		final String what = (commands.size() > 2) ? CMParms.combine(commands, 2) : "ALL";
		mob.tell(report(what));
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
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDRECIPES);
	}
}
