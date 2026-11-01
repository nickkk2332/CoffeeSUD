package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.core.interfaces.AbilityContainer;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Exits.interfaces.Exit;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.Physical;

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
 * PROPCHECK (DT12): reads the text of each effect in the world again and
 * reports what would do nothing or not what was meant: what the class logs
 * when it reads it (its own complaints), keys its help (AHELP PROP_...) does
 * not name, room IDs that do not resolve, unknown abilities in a spell list,
 * and effects on what they cannot affect.  Lines read "Area#12 / iron chest /
 * Prop_LimitedContents: unknown key MAXX".
 */
public final class PropCheck
{
	private PropCheck()
	{
	}

	private static final Pattern	KEY		= Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z][A-Za-z0-9_]*)\\s*=");
	private static final Pattern	ROOM_ID	= Pattern.compile("(?<![A-Za-z0-9_#])([A-Za-z][^\\s;,=()'\"#]*#[0-9]+)(?![0-9])");
	/** the words of a spell list that are not abilities */
	private static final Set<String> LIST_WORDS = new TreeSet<String>(java.util.Arrays.asList(new String[] {
		"NOUNINVOKE", "ONCLOSED" }));
	/** each class's keys, from its help; an empty set when the help names none */
	private static final Map<String, Set<String>> helpKeys = new HashMap<String, Set<String>>();

	/** the keys an effect's help names, as KEY= in its entry */
	private static Set<String> keysOf(final Ability A)
	{
		return keysOf(A.ID());
	}

	/**
	 * The keys a property's or behaviour's help names, as KEY= in its AHELP
	 * entry: what DT19's STAT BEHAVIORS also checks parameters against.
	 * @param ID the class's ID
	 * @return the keys, none if the help names none
	 */
	public static synchronized Set<String> keysOf(final String ID)
	{
		final String id = ID.toUpperCase();
		Set<String> keys = helpKeys.get(id);
		if(keys == null)
		{
			keys = new TreeSet<String>();
			String help = null;
			try
			{
				help = CMLib.help().getHelpText(id, null, true, true);
			}
			catch(final RuntimeException e)
			{
				help = null;
			}
			if(help != null)
			{
				final Matcher m = KEY.matcher(help);
				while(m.find())
					keys.add(m.group(1).toUpperCase());
			}
			helpKeys.put(id, keys);
		}
		return keys;
	}

	/**
	 * The KEY= words of a text that a class's help does not name.
	 * @param ID the class's ID
	 * @param text the text
	 * @return the keys, upper case, none if the help names no keys
	 */
	public static List<String> unknownKeys(final String ID, final String text)
	{
		final List<String> out = new ArrayList<String>();
		final Set<String> known = keysOf(ID);
		if(known.isEmpty() || (text == null))
			return out;
		final Matcher m = KEY.matcher(unquoted(text));
		while(m.find())
		{
			final String k = m.group(1).toUpperCase();
			if((!known.contains(k)) && (!out.contains(k)))
				out.add(k);
		}
		return out;
	}

	/** the text with what is quoted taken out, so that its words are not keys */
	private static String unquoted(final String text)
	{
		return text.replaceAll("\"[^\"]*\"|'[^']*'", "\"\"");
	}

	/** where a host is, for a problem line: room ID, or the catalog */
	private static String placeOf(final Physical host)
	{
		final Room R = CMLib.map().roomLocation(host);
		if(R != null)
			return CMLib.map().getExtendedRoomID(R);
		if(host instanceof Area)
			return host.Name();
		return "catalog";
	}

	/**
	 * Checks one effect on its host.
	 * @param host the host
	 * @param A the effect
	 * @return the problems, without where (the caller adds it)
	 */
	public static List<String> checkEffect(final Physical host, final Ability A)
	{
		final List<String> out = new ArrayList<String>();
		final String text = (A.text() == null) ? "" : A.text();
		// what the class itself says of the text
		final Ability fresh = CMClass.getAbility(A.ID());
		if(fresh != null)
		{
			Log.startCapture();
			try
			{
				fresh.setMiscText(text);
				if(fresh instanceof AbilityContainer)
					((AbilityContainer)fresh).numAbilities();
			}
			catch(final RuntimeException e)
			{
				out.add("reading its text failed: " + e);
			}
			finally
			{
				for(final String s : Log.stopCapture())
				{
					final String line = s.startsWith(A.ID() + ": ") ? s.substring(A.ID().length() + 2) : s;
					out.add(line.replace('\n', ' ').replace('\r', ' '));
				}
			}
		}
		// keys its help does not name
		for(final String k : unknownKeys(A.ID(), text))
			out.add("unknown key " + k + " (AHELP " + A.ID().toUpperCase() + ")");
		// room IDs
		final Matcher r = ROOM_ID.matcher(text);
		while(r.find())
		{
			final String id = r.group(1);
			if(CMLib.map().getRoom(id) == null)
				out.add("no room " + id);
		}
		// spell lists
		if((A instanceof AbilityContainer) && (A.ID().startsWith("Prop_")))
		{
			String list = text;
			final int mask = list.toUpperCase().indexOf("MASK=");
			if(mask > 0)
				list = list.substring(0, mask);
			for(String one : CMParms.parseSemicolons(list, true))
			{
				one = one.trim();
				if((one.length() == 0) || (one.indexOf('=') >= 0) || LIST_WORDS.contains(one.toUpperCase())
				|| one.toUpperCase().startsWith("LEVEL") || one.toUpperCase().startsWith("MAXTICKS") || one.toUpperCase().startsWith("TICKS"))
					continue;
				final int pct = one.indexOf("% ");
				if(pct > 0)
					one = one.substring(pct + 1).trim();
				if(one.endsWith(")") && (one.indexOf('(') > 0))
					one = one.substring(0, one.indexOf('(')).trim();
				if(CMath.isInteger(one) || one.endsWith("%"))
					continue;
				if(CMClass.getAbility(one) == null)
					out.add("unknown ability " + one);
			}
		}
		// on what it cannot affect
		try
		{
			if(!A.canAffect(host))
				out.add("cannot affect " + CMClass.classID(host));
		}
		catch(final RuntimeException e)
		{
			// a class that cannot tell is left be
		}
		return out;
	}

	/**
	 * Checks every effect on a host.
	 * @param host the host
	 * @return "place / host / effect: problem" lines
	 */
	public static List<String> checkHost(final Physical host)
	{
		final List<String> out = new ArrayList<String>();
		if(host == null)
			return out;
		for(final Enumeration<Ability> e = host.effects(); e.hasMoreElements();)
		{
			final Ability A = e.nextElement();
			if((A == null) || (!A.isSavable()) || A.canBeUninvoked())
				continue; // only what was set on it, not what is cast on it
			for(final String p : checkEffect(host, A))
				out.add(placeOf(host) + " / " + host.Name() + " / " + A.ID() + ": " + p);
		}
		return out;
	}

	/**
	 * Checks the effects of a room: its own, its exits', the mobs' in it and
	 * what they carry, and the items'.
	 * @param R the room
	 * @return the problems
	 */
	public static List<String> checkRoom(final Room R)
	{
		final Set<String> out = new LinkedHashSet<String>();
		if(R == null)
			return new ArrayList<String>();
		out.addAll(checkHost(R));
		for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
		{
			final Exit E = R.getRawExit(d);
			if(E != null)
				out.addAll(checkHost(E));
		}
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M == null) || (!M.isMonster()))
				continue;
			out.addAll(checkHost(M));
			for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
				out.addAll(checkHost(i.nextElement()));
		}
		for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
			out.addAll(checkHost(i.nextElement()));
		return new ArrayList<String>(out);
	}

	/**
	 * Checks the effects of an area and all in it.
	 * @param A the area
	 * @return the problems
	 */
	public static List<String> checkArea(final Area A)
	{
		final Set<String> out = new LinkedHashSet<String>();
		out.addAll(checkHost(A));
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
			out.addAll(checkRoom(r.nextElement()));
		return new ArrayList<String>(out);
	}

	/**
	 * Checks the catalog's mobs and items.
	 * @return the problems
	 */
	public static List<String> checkCatalog()
	{
		final Set<String> out = new LinkedHashSet<String>();
		for(final String name : CMLib.catalog().getCatalogMobNames())
		{
			final MOB M = CMLib.catalog().getCatalogMob(name);
			if(M != null)
				out.addAll(checkHost(M));
		}
		for(final String name : CMLib.catalog().getCatalogItemNames())
			out.addAll(checkHost(CMLib.catalog().getCatalogItem(name)));
		return new ArrayList<String>(out);
	}

	/**
	 * Checks the whole world and the catalog.
	 * @return the problems
	 */
	public static List<String> checkAll()
	{
		final Set<String> out = new LinkedHashSet<String>();
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
			out.addAll(checkArea(a.nextElement()));
		out.addAll(checkCatalog());
		return new ArrayList<String>(out);
	}
}
