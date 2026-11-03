package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.CharClasses.interfaces.CharClass;
import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.Common.interfaces.PlayerAccount;
import com.planet_ink.coffee_mud.Common.interfaces.Tattoo;
import com.planet_ink.coffee_mud.Items.interfaces.Coins;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Items.interfaces.Wearable;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Races.interfaces.Race;
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
 * P8: ready-made characters.  Catalog mobs in the category PLAYERTEMPLATE are
 * offered by NEW (CREATIONTEMPLATES=OFFER, the default, next to making your
 * own; ONLY, the only way; NO, never).  A template's Prop_PlayerTemplate holds
 * its PITCH, LOCK (NAME and GENDER: kept as the template's) and NAME=RANDOM
 * (a drawn name).  Picking one gives the new character its race, gender,
 * class and level (by real level-ups), stats, abilities and their
 * proficiency, expertises, factions, tattoos, items, money and description.
 */
public class PlayerTemplates
{
	private PlayerTemplates()
	{
	}

	/** the catalog category of player templates */
	public static final String CATEGORY = "PLAYERTEMPLATE";

	/** @return OFFER, ONLY or NO, from CREATIONTEMPLATES */
	public static String mode()
	{
		final CMProps P = CMProps.instance();
		final String m = (P == null) ? "OFFER" : P.getStr("CREATIONTEMPLATES", "OFFER").trim().toUpperCase();
		if(m.startsWith("ONLY"))
			return "ONLY";
		if(m.startsWith("NO") || m.equals("FALSE") || m.equals("OFF"))
			return "NO";
		return "OFFER";
	}

	/**
	 * The player templates, as the catalog has them.
	 * @return the template mobs, not copies
	 */
	public static List<MOB> all()
	{
		final List<MOB> list = new ArrayList<MOB>();
		final String[] names = CMLib.catalog().getCatalogMobNames(CATEGORY);
		if(names == null)
			return list;
		for(final String n : names)
		{
			final MOB T = CMLib.catalog().getCatalogMob(n);
			if(T != null)
				list.add(T);
		}
		return list;
	}

	/** @return the templates NEW offers: none under CREATIONTEMPLATES=NO */
	public static List<MOB> offered()
	{
		if(mode().equals("NO"))
			return new ArrayList<MOB>(0);
		return all();
	}

	private static String parm(final MOB T, final String key)
	{
		final Ability A = T.fetchEffect("Prop_PlayerTemplate");
		return (A == null) ? "" : CMParms.getParmStr(A.text(), key, "").trim();
	}

	/**
	 * @param T a template
	 * @return its one-line pitch
	 */
	public static String pitch(final MOB T)
	{
		return parm(T, "PITCH");
	}

	/**
	 * @param T a template
	 * @param field NAME or GENDER
	 * @return true if the template keeps it fixed
	 */
	public static boolean locked(final MOB T, final String field)
	{
		for(final String f : CMParms.parseCommas(parm(T, "LOCK").toUpperCase(), true))
		{
			if(f.trim().equals(field.toUpperCase()))
				return true;
		}
		return false;
	}

	/**
	 * @param T a template
	 * @return true if it draws a name (NAME=RANDOM)
	 */
	public static boolean randomName(final MOB T)
	{
		return parm(T, "NAME").equalsIgnoreCase("RANDOM");
	}

	/**
	 * The menu of templates.
	 * @param list the templates
	 * @param only true when making one's own is not offered
	 * @return the text
	 */
	public static String menu(final List<MOB> list, final boolean only)
	{
		final StringBuilder s = new StringBuilder(CMLib.lang().L("\n\r^!Ready-made characters:^N\n\r"));
		for(int i = 0; i < list.size(); i++)
		{
			final MOB T = list.get(i);
			s.append("^H").append(i + 1).append(") ").append(T.Name()).append("^N");
			final String p = pitch(T);
			if(p.length() > 0)
				s.append(": ").append(p);
			s.append("\n\r");
		}
		if(!only)
			s.append("^H0) ").append(CMLib.lang().L("Make your own")).append("^N\n\r");
		return s.toString();
	}

	/**
	 * Finds what was typed among the templates: its number, or its name or
	 * the start of it.
	 * @param list the templates
	 * @param input what was typed
	 * @return the template, or null
	 */
	public static MOB find(final List<MOB> list, final String input)
	{
		final String in = (input == null) ? "" : input.trim();
		if(in.length() == 0)
			return null;
		if(CMath.isInteger(in))
		{
			final int n = CMath.s_int(in);
			return ((n >= 1) && (n <= list.size())) ? list.get(n - 1) : null;
		}
		for(final MOB T : list)
		{
			if(T.Name().equalsIgnoreCase(in))
				return T;
		}
		for(final MOB T : list)
		{
			if(T.Name().toLowerCase().startsWith(in.toLowerCase()))
				return T;
		}
		return null;
	}

	/**
	 * @param name a character name
	 * @return true if no character has it
	 */
	public static boolean nameFree(final String name)
	{
		return (name.length() > 0)
			&& (CMLib.players().getLoadPlayer(name) == null)
			&& (CMLib.database().DBUserSearch(name) == null);
	}

	/**
	 * A name drawn for a character, in the style of their race, not taken.
	 * @param mob the character
	 * @return the name, or null after twenty tries
	 */
	public static String drawName(final MOB mob)
	{
		final char g = (char)mob.baseCharStats().getStat(CharStats.STAT_GENDER);
		for(int i = 0; i < 20; i++)
		{
			final String n = CMStrings.capitalizeAndLower(NameStyles.make(NameStyles.styleFor(mob), 0, 0, g, false).trim());
			if((n.indexOf(' ') < 0) && nameFree(n))
				return n;
		}
		return null;
	}

	/**
	 * Renames a character being made, in their profile too.
	 * @param mob the character
	 * @param name the new name
	 */
	public static void rename(final MOB mob, final String name)
	{
		final String old = mob.Name();
		final PlayerAccount acct = (mob.playerStats() == null) ? null : mob.playerStats().getAccount();
		final boolean listed = (acct != null) && (acct.findPlayer(old) != null);
		if(listed)
			acct.delPlayer(old);
		mob.setName(name);
		if(listed)
			acct.addNewPlayer(mob);
	}

	/**
	 * Levels a character up to a level by the experience each level needs, as
	 * CHARGEN and TESTCHAR do.
	 * @param M the character
	 * @param level the level
	 */
	public static void levelTo(final MOB M, final int level)
	{
		int guard = 0;
		while((M.basePhyStats().level() < level) && (guard++ < 1000))
		{
			if(M.getExpNeededLevel() == Integer.MAX_VALUE)
				CMLib.leveler().level(M);
			else
				CMLib.leveler().postExperience(M, "COMMAND:TEMPLATE", null, null, M.getExpNeededLevel() + 1, true);
			M.recoverPhyStats();
			M.recoverCharStats();
			M.recoverMaxState();
		}
	}

	/**
	 * Makes a character being made the template's: race, gender, class and
	 * level, stats, abilities, expertises, factions, tattoos, items, money and
	 * description.  Its name is the caller's to settle.
	 * @param mob the character
	 * @param T the template
	 */
	public static void apply(final MOB mob, final MOB T)
	{
		final Race R = T.baseCharStats().getMyRace();
		mob.baseCharStats().setMyRace(R);
		mob.baseCharStats().setStat(CharStats.STAT_GENDER, T.baseCharStats().getStat(CharStats.STAT_GENDER));
		for(final int i : CharStats.CODES.BASECODES())
			mob.baseCharStats().setStat(i, T.baseCharStats().getStat(i));
		R.startRacing(mob, false);
		CharClass C = T.baseCharStats().getCurrentClass();
		if((C == null) || C.ID().equals("StdCharClass"))
			C = mob.baseCharStats().getCurrentClass();
		mob.baseCharStats().setCurrentClass(C);
		if(mob.baseCharStats().getClassLevel(C) < 1)
			mob.baseCharStats().setClassLevel(C, 1);
		mob.basePhyStats().setLevel(Math.max(1, mob.basePhyStats().level()));
		C.startCharacter(mob, false, false);
		mob.recoverCharStats();
		mob.recoverPhyStats();
		mob.recoverMaxState();
		levelTo(mob, T.basePhyStats().level());
		for(final Enumeration<Ability> a = T.abilities(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if(A == null)
				continue;
			final Ability mine = mob.fetchAbility(A.ID());
			if(mine != null)
				mine.setProficiency(Math.max(mine.proficiency(), A.proficiency()));
			else
			{
				final Ability N = (Ability)A.copyOf();
				mob.addAbility(N);
				N.autoInvocation(mob, false);
			}
		}
		for(final Enumeration<String> e = T.expertises(); e.hasMoreElements();)
			mob.addExpertise(e.nextElement());
		for(final Enumeration<String> f = T.factions(); f.hasMoreElements();)
		{
			final String id = f.nextElement();
			mob.addFaction(id, T.fetchFaction(id));
		}
		for(final Enumeration<Tattoo> t = T.tattoos(); t.hasMoreElements();)
			mob.addTattoo(t.nextElement().getTattooName());
		for(int i = 0; i < T.numItems(); i++)
		{
			final Item I = T.getItem(i);
			if((I == null) || (I.container() != null) || (I instanceof Coins))
				continue; // what is in a container goes with it; money is given below
			final Item N = (Item)I.copyOf();
			mob.addItem(N);
			if(!I.amWearingAt(Wearable.IN_INVENTORY))
				N.wearIfPossible(mob);
		}
		final double money = CMLib.beanCounter().getTotalAbsoluteNativeValue(T);
		if(money > 0)
			CMLib.beanCounter().giveSomeoneMoney(mob, money);
		if(T.description().length() > 0)
			mob.setDescription(T.description());
		mob.recoverCharStats();
		mob.recoverPhyStats();
		mob.recoverMaxState();
		mob.resetToMaxState();
	}
}
