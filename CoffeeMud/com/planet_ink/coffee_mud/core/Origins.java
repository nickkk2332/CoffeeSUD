package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Common.interfaces.Faction;
import com.planet_ink.coffee_mud.Common.interfaces.PlayerAccount;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.Tickable;
import com.planet_ink.coffee_mud.core.save.WorldFiles;

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
 * P6: character origins as data, world/origins.ini.  Each origin is an [ID]
 * section with NAME, DESCRIPTION, MASK (who may take it), UNLOCK (a profile or
 * character tattoo it needs), START (a room ID), ITEMS (catalog names or item
 * classes, a count before each if more than one), ABILITIES (ID and
 * proficiency), FACTIONS (ID and amount), TATTOOS, MONEY and SCRIPT (a
 * ONCE_PROG, lines between semicolons, or LOAD=file).  When there are origins,
 * character creation asks for one after the class; the choice is kept as the
 * tattoo ORIGIN_[ID], and the origin is given where the start room is set.
 * CULTURE names a culture (world/cultures.ini) the character is of from the
 * start: adopted by the same rules as later (core/Cultures), so it can be
 * left again.
 */
public class Origins
{
	private Origins()
	{
	}

	/** one origin: its ID and its fields, by upper-case key */
	public static class Origin
	{
		public final String				id;
		public final Map<String, String>	vals	= new TreeMap<String, String>();

		public Origin(final String id)
		{
			this.id = id;
		}

		/**
		 * @param key a field
		 * @return its value, or ""
		 */
		public String get(final String key)
		{
			final String v = vals.get(key.toUpperCase());
			return (v == null) ? "" : v;
		}

		/** @return its NAME, or its ID */
		public String name()
		{
			final String n = get("NAME");
			return (n.length() > 0) ? n : id;
		}

		/** @return the tattoo its choice is kept as */
		public String tattoo()
		{
			return "ORIGIN_" + id.toUpperCase();
		}
	}

	/** @return world/origins.ini, or null with no world folder */
	public static File file()
	{
		final File W = WorldFiles.worldDir();
		return (W == null) ? null : new File(W, "origins.ini");
	}

	/**
	 * Every origin in world/origins.ini, in its order.
	 * @return the origins, none without the file
	 */
	public static List<Origin> all()
	{
		final File F = file();
		if((F == null) || (!F.isFile()))
			return new ArrayList<Origin>(0);
		try
		{
			return parse(new String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8"));
		}
		catch(final Exception e)
		{
			Log.errOut("Origins", "origins.ini: " + e.getMessage());
			return new ArrayList<Origin>(0);
		}
	}

	/**
	 * Reads origins: [ID] starts one, KEY=VALUE lines follow (a line ending in
	 * \ goes on to the next), # and ; start comments.
	 * @param text the file's text
	 * @return the origins
	 */
	public static List<Origin> parse(final String text)
	{
		final List<Origin> all = new ArrayList<Origin>();
		Origin O = null;
		final String[] lines = text.replace("\r", "").split("\n");
		for(int l = 0; l < lines.length; l++)
		{
			String line = lines[l].trim();
			while(line.endsWith("\\") && (l + 1 < lines.length))
				line = line.substring(0, line.length() - 1).trim() + " " + lines[++l].trim();
			if((line.length() == 0) || line.startsWith("#") || line.startsWith(";"))
				continue;
			if(line.startsWith("[") && line.endsWith("]"))
			{
				final String id = line.substring(1, line.length() - 1).trim().replace(' ', '_');
				if(id.length() > 0)
				{
					O = new Origin(id);
					all.add(O);
				}
				continue;
			}
			final int x = line.indexOf('=');
			if((O == null) || (x <= 0))
			{
				Log.errOut("Origins", "origins.ini: line " + (l + 1) + " is not [ID] or KEY=VALUE: " + line);
				continue;
			}
			O.vals.put(line.substring(0, x).trim().toUpperCase(), line.substring(x + 1).trim());
		}
		return all;
	}

	/**
	 * @param mob a character being made
	 * @param tattoo an UNLOCK tattoo
	 * @return true if their profile, or they, have it
	 */
	public static boolean unlocked(final MOB mob, final String tattoo)
	{
		if(mob.findTattoo(tattoo) != null)
			return true;
		final PlayerAccount acct = (mob.playerStats() == null) ? null : mob.playerStats().getAccount();
		return (acct != null) && (acct.findTattoo(tattoo) != null);
	}

	/**
	 * The origins a character may take: its MASK passes, and its UNLOCK is had.
	 * @param mob the character being made
	 * @return the origins
	 */
	public static List<Origin> choices(final MOB mob)
	{
		final List<Origin> list = new ArrayList<Origin>();
		for(final Origin O : all())
		{
			final String mask = O.get("MASK");
			if((mask.length() > 0) && (!CMLib.masking().maskCheck(mask, mob, true)))
				continue;
			final String unlock = O.get("UNLOCK");
			if((unlock.length() > 0) && (!unlocked(mob, unlock)))
				continue;
			list.add(O);
		}
		return list;
	}

	/**
	 * The menu of a character's choices.
	 * @param choices their origins
	 * @return the text
	 */
	public static String menu(final List<Origin> choices)
	{
		final StringBuilder s = new StringBuilder(CMLib.lang().L("\n\r^!Please choose an origin:^N\n\r"));
		for(int i = 0; i < choices.size(); i++)
		{
			final Origin O = choices.get(i);
			s.append("^H").append(i + 1).append(") ").append(O.name()).append("^N");
			final String d = O.get("DESCRIPTION");
			if(d.length() > 0)
				s.append(": ").append(d);
			s.append("\n\r");
		}
		return s.toString();
	}

	/**
	 * Finds what was typed among the choices: its number, ID or name, or the
	 * start of its name.
	 * @param choices the origins
	 * @param input what was typed
	 * @return the origin, or null
	 */
	public static Origin find(final List<Origin> choices, final String input)
	{
		final String in = (input == null) ? "" : input.trim();
		if(in.length() == 0)
			return null;
		if(CMath.isInteger(in))
		{
			final int n = CMath.s_int(in);
			return ((n >= 1) && (n <= choices.size())) ? choices.get(n - 1) : null;
		}
		for(final Origin O : choices)
		{
			if(O.id.equalsIgnoreCase(in) || O.name().equalsIgnoreCase(in))
				return O;
		}
		for(final Origin O : choices)
		{
			if(O.name().toLowerCase().startsWith(in.toLowerCase()) || O.id.toLowerCase().startsWith(in.toLowerCase()))
				return O;
		}
		return null;
	}

	/**
	 * Keeps a character's choice, as the tattoo ORIGIN_[ID].
	 * @param mob the character being made
	 * @param O the origin
	 */
	public static void choose(final MOB mob, final Origin O)
	{
		for(final Origin other : all())
		{
			if(mob.findTattoo(other.tattoo()) != null)
				mob.delTattoo(other.tattoo());
		}
		mob.addTattoo(O.tattoo());
	}

	/**
	 * @param mob a character
	 * @return the origin they chose, or null
	 */
	public static Origin chosen(final MOB mob)
	{
		for(final Origin O : all())
		{
			if(mob.findTattoo(O.tattoo()) != null)
				return O;
		}
		return null;
	}

	/**
	 * Gives a character being made what their origin gives: its start room,
	 * items, abilities, faction standing, tattoos and money, then runs its
	 * script.
	 * @param mob the character
	 * @return the origin given, or null for none
	 */
	public static Origin apply(final MOB mob)
	{
		final Origin O = chosen(mob);
		if(O == null)
			return null;
		final String where = "origins.ini [" + O.id + "] ";
		final String start = O.get("START");
		if(start.length() > 0)
		{
			final Room R = CMLib.map().getRoom(start);
			if(R != null)
				mob.setStartRoom(R);
			else
				Log.errOut("Origins", where + "START: no room " + start);
		}
		for(final String entry : CMParms.parseCommas(O.get("ITEMS"), true))
		{
			int count = 1;
			String name = entry.trim();
			final int sp = name.indexOf(' ');
			if((sp > 0) && CMath.isInteger(name.substring(0, sp)))
			{
				count = Math.max(1, Math.min(100, CMath.s_int(name.substring(0, sp))));
				name = name.substring(sp + 1).trim();
			}
			for(int c = 0; c < count; c++)
			{
				final Item I = item(name);
				if(I == null)
				{
					Log.errOut("Origins", where + "ITEMS: no catalog item or item class " + name);
					break;
				}
				mob.addItem(I);
			}
		}
		for(final String entry : CMParms.parseCommas(O.get("ABILITIES"), true))
		{
			final List<String> words = CMParms.parse(entry);
			if(words.size() == 0)
				continue;
			final Ability A = CMClass.getAbility(words.get(0));
			if(A == null)
			{
				Log.errOut("Origins", where + "ABILITIES: no ability " + words.get(0));
				continue;
			}
			final int prof = (words.size() > 1) ? CMath.s_int(words.get(1)) : A.proficiency();
			final Ability had = mob.fetchAbility(A.ID());
			if(had != null)
				had.setProficiency(Math.max(had.proficiency(), prof));
			else
			{
				A.setProficiency(prof);
				mob.addAbility(A);
				A.autoInvocation(mob, false);
			}
		}
		for(final String entry : CMParms.parseCommas(O.get("FACTIONS"), true))
		{
			final List<String> words = CMParms.parse(entry);
			if(words.size() < 2)
				continue;
			final String fname = CMParms.combine(words.subList(0, words.size() - 1), 0);
			Faction F = CMLib.factions().getFaction(fname);
			if(F == null)
				F = CMLib.factions().getFactionByName(fname);
			if(F == null)
			{
				Log.errOut("Origins", where + "FACTIONS: no faction " + fname);
				continue;
			}
			final int amount = CMath.s_int(words.get(words.size() - 1));
			final int now = mob.fetchFaction(F.factionID());
			mob.addFaction(F.factionID(), (now == Integer.MAX_VALUE) ? amount : (now + amount));
		}
		for(final String t : CMParms.parseAny(O.get("TATTOOS").replace(',', ' '), ' ', true))
			mob.addTattoo(t.toUpperCase());
		final double money = CMath.s_double(O.get("MONEY"));
		if(money > 0)
			CMLib.beanCounter().giveSomeoneMoney(mob, money);
		final String culture = O.get("CULTURE");
		if(culture.length() > 0)
		{
			try
			{
				Cultures.adopt(mob, culture, "origin");
			}
			catch(final java.io.IOException e)
			{
				Log.errOut("Origins", where + "CULTURE: " + e.getMessage());
			}
		}
		final String script = O.get("SCRIPT");
		if(script.length() > 0)
			runScript(mob, script);
		return O;
	}

	private static Item item(final String name)
	{
		Item I = CMLib.catalog().getCatalogItem(name);
		if(I != null)
		{
			I = (Item)I.copyOf();
			try
			{
				CMLib.catalog().changeCatalogUsage(I, true);
			}
			catch(final Exception e)
			{
			}
			return I;
		}
		I = CMClass.getItem(name);
		return I;
	}

	/**
	 * Runs a creation script for a character, as CHARCREATIONSCRIPTS does.
	 * @param mob the character
	 * @param script the script
	 */
	public static void runScript(final MOB mob, final String script)
	{
		final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
		S.setSavable(false);
		S.setVarScope("*");
		S.setScript(script);
		final Room oldRoom = mob.location();
		if(oldRoom == null)
			mob.setLocation((mob.getStartRoom() != null) ? mob.getStartRoom() : CMLib.map().getRandomRoom());
		try
		{
			final CMMsg msg = CMClass.getMsg(mob, mob, null, CMMsg.MSG_OK_VISUAL, null, null, "CHARCREATION");
			S.executeMsg(mob, msg);
			S.dequeResponses(null);
			S.tick(mob, Tickable.TICKID_MOB);
		}
		finally
		{
			if(oldRoom == null)
				mob.setLocation(null);
		}
	}
}
