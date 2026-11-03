package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.Recipes;

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
 * CR4: recipes a character knows, kept with them in Prop_KnownRecipes: a
 * name (a skill's own recipe, as one marked HIDDEN=1) or a whole recipe line
 * (a new recipe, as a recipe book holds).  LEARN FROM [BOOK] copies a book's
 * recipes into it, MPLEARNRECIPE teaches one, KNOWSRECIPE() asks.  With
 * PROFILERECIPES=true in the ini, what one character of the profile knows,
 * all of them know.
 */
public final class KnownRecipes
{
	private KnownRecipes()
	{
	}

	/** what Prop_KnownRecipes offers (abilities are loaded by the game's own class loader) */
	public interface Known
	{
		/**
		 * @param skillID a skill
		 * @return the whole recipe lines known for it
		 */
		public String[] linesFor(String skillID);

		/**
		 * @param skillID a skill
		 * @param name a recipe's name
		 * @return whether it is known, by name or line
		 */
		public boolean knows(String skillID, String name);

		/**
		 * @param skillID a skill
		 * @param nameOrLine a recipe's name, or a whole recipe line
		 * @return true if it was not known before
		 */
		public boolean add(String skillID, String nameOrLine);
	}

	/**
	 * @param mob a character
	 * @param make true to give them one if they have none
	 * @return their known recipes, or null
	 */
	public static Known of(final MOB mob, final boolean make)
	{
		if(mob == null)
			return null;
		Ability A = mob.fetchEffect("Prop_KnownRecipes");
		if((A == null) && make)
		{
			A = CMClass.getAbility("Prop_KnownRecipes");
			if(A == null)
				return null;
			mob.addNonUninvokableEffect(A);
			A = mob.fetchEffect("Prop_KnownRecipes");
		}
		return (A instanceof Known) ? (Known)A : null;
	}

	/** @return whether the profile's characters share what they know (PROFILERECIPES=true) */
	public static boolean shared()
	{
		final CMProps P = CMProps.instance();
		return (P != null) && CMath.s_bool(P.getStr("PROFILERECIPES", "false").trim());
	}

	/**
	 * @param mob a character
	 * @return the other characters of their profile in the world, when the profile shares what it knows
	 */
	protected static List<MOB> sharers(final MOB mob)
	{
		if((!shared()) || (mob == null) || (Party.profileOf(mob) == null))
			return new ArrayList<MOB>(0);
		return Party.othersInWorld(mob);
	}

	/**
	 * @param mob a character
	 * @param skillID a skill
	 * @param name a recipe's name
	 * @return whether they know it (or, under PROFILERECIPES, another character of their profile does)
	 */
	public static boolean knows(final MOB mob, final String skillID, final String name)
	{
		final Known K = of(mob, false);
		if((K != null) && K.knows(skillID, name))
			return true;
		for(final MOB M : sharers(mob))
		{
			final Known K2 = of(M, false);
			if((K2 != null) && K2.knows(skillID, name))
				return true;
		}
		return false;
	}

	/**
	 * @param mob a character
	 * @param skillID a skill
	 * @return the whole recipe lines the others of their profile know, under PROFILERECIPES
	 */
	public static List<String> sharedLines(final MOB mob, final String skillID)
	{
		final List<String> lines = new ArrayList<String>();
		for(final MOB M : sharers(mob))
		{
			final Known K = of(M, false);
			if(K != null)
				lines.addAll(Arrays.asList(K.linesFor(skillID)));
		}
		return lines;
	}

	/**
	 * LEARN FROM [BOOK]: a recipe book's recipes, known for good.
	 * @param mob the reader
	 * @param book the book
	 * @return how many were new
	 */
	public static int study(final MOB mob, final Recipes book)
	{
		final Known K = of(mob, true);
		if((K == null) || (book == null))
			return 0;
		int n = 0;
		for(final String line : book.getRecipeCodeLines())
		{
			if((line != null) && (line.trim().length() > 0) && K.add(book.getCommonSkillID(), line))
				n++;
		}
		return n;
	}

	/**
	 * MPLEARNRECIPE: one recipe, known for good: a whole line, or the name of
	 * one of the skill's own recipes.
	 * @param mob who learns it
	 * @param skillID the skill
	 * @param what the recipe's name, or a whole recipe line
	 * @return null when done, else what is wrong
	 */
	public static String learn(final MOB mob, final String skillID, final String what)
	{
		final Ability skill = CMClass.getAbility(skillID);
		if(skill == null)
			return "no skill '" + skillID + "'";
		final String w = (what == null) ? "" : what.trim();
		if(w.length() == 0)
			return "no recipe given";
		final Known K = of(mob, true);
		if(K == null)
			return "cannot keep recipes";
		if(w.indexOf('\t') > 0)
		{
			K.add(skill.ID(), w);
			return null;
		}
		final List<String> row = baseRecipe(skill, w);
		if(row == null)
			return "no recipe '" + w + "' in " + skill.ID();
		K.add(skill.ID(), row.get(0));
		return null;
	}

	/**
	 * @param skill a crafting skill
	 * @param name a recipe's name
	 * @return its own recipe of that name, or null
	 */
	@SuppressWarnings("unchecked")
	public static List<String> baseRecipe(final Ability skill, final String name)
	{
		try
		{
			final Object o = skill.getClass().getMethod("fetchRecipes").invoke(skill);
			if(o instanceof List)
			{
				for(final Object r : (List<Object>)o)
				{
					if((r instanceof List) && (((List<String>)r).size() > 0) && same(((List<String>)r).get(0), name))
						return (List<String>)r;
				}
			}
		}
		catch(final Exception e)
		{
			// not a crafting skill
		}
		return null;
	}

	/**
	 * @param a a recipe's name, as written (with % for the material)
	 * @param b another
	 * @return whether they are the same recipe
	 */
	public static boolean same(final String a, final String b)
	{
		if((a == null) || (b == null))
			return false;
		return clean(a).equalsIgnoreCase(clean(b));
	}

	private static String clean(final String s)
	{
		return s.replace("%", " ").trim().replaceAll("\\s+", " ");
	}
}
