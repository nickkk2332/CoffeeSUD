package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.Faction;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
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
 * P7: starting kits, in DT16's kit files (world/kits/NAME.kit).  A kit with a
 * "starting" line (all, or a zapper mask) is given at character creation to
 * those its mask lets in; kits that share a "choose" group are a menu, one of
 * them taken, in the KIT creation step after the origin.  "title" names it in
 * the menu, "wear no" keeps its items unworn.  Items, gold, abilities,
 * factions and vars are given as KIT gives them, but gold is added.
 */
public class StartingKits
{
	private StartingKits()
	{
	}

	/** a starting kit, as read */
	public static class StartKit
	{
		/** its file's name, without .kit */
		public String				name	= "";
		/** who gets it: all, or a zapper mask */
		public String				mask	= "";
		/** its menu group, or "" */
		public String				group	= "";
		/** its name in the menu */
		public String				title	= "";
		/** whether its items are put on */
		public boolean				wear	= true;
		/** each setting: word, value */
		public final List<String[]>	lines	= new ArrayList<String[]>();
	}

	/** @return world/kits, or null with no world folder */
	public static File dir()
	{
		final File W = WorldFiles.worldDir();
		return (W == null) ? null : new File(W, "kits");
	}

	/**
	 * Every starting kit, by file name.
	 * @return the kits with a "starting" line
	 */
	public static List<StartKit> all()
	{
		final List<StartKit> list = new ArrayList<StartKit>();
		final File D = dir();
		final File[] files = ((D == null) || (!D.isDirectory())) ? null : D.listFiles();
		if(files == null)
			return list;
		Arrays.sort(files);
		for(final File F : files)
		{
			if(F.isFile() && F.getName().toLowerCase().endsWith(".kit"))
			{
				final StartKit K = read(F);
				if(K != null)
					list.add(K);
			}
		}
		return list;
	}

	/**
	 * Reads a kit file as a starting kit.
	 * @param F the file
	 * @return the kit, or null if it has no "starting" line
	 */
	public static StartKit read(final File F)
	{
		final StartKit K = new StartKit();
		K.name = F.getName().substring(0, F.getName().length() - 4);
		boolean starting = false;
		try
		{
			for(final String raw : new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8).split("\\r?\\n"))
			{
				final String line = raw.trim();
				if((line.length() == 0) || line.startsWith("#"))
					continue;
				final int x = line.indexOf(' ');
				final String word = ((x < 0) ? line : line.substring(0, x)).toUpperCase();
				final String value = (x < 0) ? "" : line.substring(x + 1).trim();
				if(word.equals("STARTING"))
				{
					starting = true;
					K.mask = value;
				}
				else
				if(word.equals("CHOOSE"))
					K.group = value.toUpperCase();
				else
				if(word.equals("TITLE"))
					K.title = value;
				else
				if(word.equals("WEAR"))
					K.wear = !value.toUpperCase().startsWith("N");
				else
					K.lines.add(new String[] { word, value });
			}
		}
		catch(final java.io.IOException e)
		{
			Log.errOut("StartingKits", F.getName() + ": " + e.getMessage());
			return null;
		}
		if(!starting)
			return null;
		if(K.title.length() == 0)
			K.title = K.name;
		return K;
	}

	/**
	 * @param mob a character being made
	 * @param K a starting kit
	 * @return true if its mask lets them in
	 */
	public static boolean fits(final MOB mob, final StartKit K)
	{
		final String m = K.mask.trim();
		if((m.length() == 0) || m.equalsIgnoreCase("ALL"))
			return true;
		return CMLib.masking().maskCheck(m, mob, true);
	}

	/**
	 * The menu groups a character has kits in, in the order of the kits.
	 * @param mob the character being made
	 * @return the groups
	 */
	public static List<String> groups(final MOB mob)
	{
		final List<String> groups = new ArrayList<String>();
		for(final StartKit K : all())
		{
			if((K.group.length() > 0) && fits(mob, K) && (!groups.contains(K.group)))
				groups.add(K.group);
		}
		return groups;
	}

	/**
	 * @param mob the character being made
	 * @param group a menu group
	 * @return the kits of that group they may take
	 */
	public static List<StartKit> inGroup(final MOB mob, final String group)
	{
		final List<StartKit> list = new ArrayList<StartKit>();
		for(final StartKit K : all())
		{
			if(K.group.equalsIgnoreCase(group) && fits(mob, K))
				list.add(K);
		}
		return list;
	}

	/**
	 * The menu of a group's kits.
	 * @param kits the kits
	 * @return the text
	 */
	public static String menu(final List<StartKit> kits)
	{
		final StringBuilder s = new StringBuilder(CMLib.lang().L("\n\r^!Please choose a kit:^N\n\r"));
		for(int i = 0; i < kits.size(); i++)
			s.append("^H").append(i + 1).append(") ").append(kits.get(i).title).append("^N\n\r");
		return s.toString();
	}

	/**
	 * Finds what was typed among a group's kits: its number, file name or
	 * title, or the start of its title.
	 * @param kits the kits
	 * @param input what was typed
	 * @return the kit, or null
	 */
	public static StartKit find(final List<StartKit> kits, final String input)
	{
		final String in = (input == null) ? "" : input.trim();
		if(in.length() == 0)
			return null;
		if(CMath.isInteger(in))
		{
			final int n = CMath.s_int(in);
			return ((n >= 1) && (n <= kits.size())) ? kits.get(n - 1) : null;
		}
		for(final StartKit K : kits)
		{
			if(K.name.equalsIgnoreCase(in) || K.title.equalsIgnoreCase(in))
				return K;
		}
		for(final StartKit K : kits)
		{
			if(K.title.toLowerCase().startsWith(in.toLowerCase()))
				return K;
		}
		return null;
	}

	/**
	 * Gives a character being made their starting kits: every kit that fits
	 * them and is in no group, and those chosen.
	 * @param mob the character
	 * @param chosen the names of the kits chosen from menus
	 * @return what could not be given
	 */
	public static List<String> apply(final MOB mob, final Collection<String> chosen)
	{
		final List<String> said = new ArrayList<String>();
		for(final StartKit K : all())
		{
			if(!fits(mob, K))
				continue;
			if((K.group.length() > 0) && ((chosen == null) || (!chosen.contains(K.name))))
				continue;
			give(mob, K, said);
		}
		for(final String why : said)
			Log.errOut("StartingKits", why);
		return said;
	}

	/**
	 * An item named in a kit: catalog:NAME, an item class, or a catalog name.
	 * @param spec the name
	 * @return a new item, or null
	 */
	public static Item item(final String spec)
	{
		Item I;
		if(spec.toLowerCase().startsWith("catalog:"))
			I = CMLib.catalog().getCatalogItem(spec.substring(8).trim());
		else
		{
			I = CMClass.getItem(spec);
			if(I != null)
				return I;
			I = CMLib.catalog().getCatalogItem(spec);
		}
		if(I == null)
			return null;
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

	private static void give(final MOB M, final StartKit K, final List<String> said)
	{
		final String where = K.name + ".kit: ";
		for(final String[] l : K.lines)
		{
			final String word = l[0];
			final String v = l[1];
			if(word.equals("ITEM"))
			{
				int count = 1;
				String spec = v;
				final int sp = v.indexOf(' ');
				if((sp > 0) && CMath.isInteger(v.substring(0, sp)))
				{
					count = Math.max(1, Math.min(100, CMath.s_int(v.substring(0, sp))));
					spec = v.substring(sp + 1).trim();
				}
				for(int c = 0; c < count; c++)
				{
					final Item I = item(spec);
					if(I == null)
					{
						said.add(where + "no item '" + spec + "'");
						break;
					}
					M.addItem(I);
					if(K.wear)
						I.wearIfPossible(M);
				}
			}
			else
			if(word.equals("GOLD"))
			{
				if(CMath.isNumber(v) && (CMath.s_double(v) > 0))
					CMLib.beanCounter().giveSomeoneMoney(M, CMath.s_double(v));
			}
			else
			if(word.equals("ABILITY"))
			{
				final String[] p = v.split("\\s+");
				final Ability A = CMClass.getAbility(p[0]);
				if(A == null)
				{
					said.add(where + "no ability '" + p[0] + "'");
					continue;
				}
				Ability mine = M.fetchAbility(A.ID());
				if(mine == null)
				{
					mine = A;
					M.addAbility(mine);
				}
				mine.setProficiency((p.length > 1) && CMath.isInteger(p[1]) ? CMath.s_int(p[1]) : 100);
				mine.autoInvocation(M, false);
			}
			else
			if(word.equals("FACTION"))
			{
				final String[] p = v.split("\\s+");
				final Faction F = CMLib.factions().getFaction(p[0]);
				if((F == null) || (p.length < 2) || (!CMath.isInteger(p[1])))
					said.add(where + "faction '" + v + "'");
				else
					M.addFaction(F.factionID(), CMath.s_int(p[1]));
			}
			else
			if(word.equals("VAR"))
			{
				final int x = v.indexOf(' ');
				if(x > 0)
				{
					final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
					S.setVar(M.Name(), v.substring(0, x).trim().toUpperCase(), v.substring(x + 1).trim());
				}
			}
			// race, class, gender, alignment, level and room are for TESTCHAR and KIT, not new characters
		}
	}
}
