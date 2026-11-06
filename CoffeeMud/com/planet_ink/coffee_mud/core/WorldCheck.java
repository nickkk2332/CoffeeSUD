package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Exits.interfaces.Exit;
import com.planet_ink.coffee_mud.Items.interfaces.Container;
import com.planet_ink.coffee_mud.Items.interfaces.DoorKey;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.Environmental;
import com.planet_ink.coffee_mud.core.interfaces.ShopKeeper;
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
 * WORLD CHECK (DT13): one lint of the whole world, by severity.  Its parts
 * (ONLY= picks some): exits (exits to nowhere; doors with no way back),
 * reach (the start rooms exist; rooms nothing leads to from them), keys
 * (locked doors and containers whose key is nowhere: rooms, mobs, shops,
 * catalog), text (rooms with their class's default text or the new room's,
 * generic mobs and items with their class's),
 * shops (shopkeepers with nothing to sell), data (what the world files
 * could not load, DT3), scripts (SCRIPTCHECK, DT11) and props (PROPCHECK,
 * DT12).  sud.sh CHECK=1 runs it without a player and exits 1 on errors.
 */
public final class WorldCheck
{
	private WorldCheck()
	{
	}

	/** how bad a finding is */
	public enum Severity
	{
		ERROR, WARNING
	}

	/** the parts of the check */
	public static final String[] PARTS = { "EXITS", "REACH", "KEYS", "TEXT", "SHOPS", "DATA", "SCRIPTS", "PROPS", "JS", "VOCAB", "LORE", "TEMPLATES", "ROUTES", "IDS", "MODULES", "COMPOSITIONS", "COMBAT" };

	/** one thing found */
	public static final class Finding
	{
		/** how bad */
		public final Severity	severity;
		/** the part that found it */
		public final String		part;
		/** what, and where */
		public final String		what;

		Finding(final Severity severity, final String part, final String what)
		{
			this.severity = severity;
			this.part = part;
			this.what = what;
		}

		@Override
		public String toString()
		{
			return "[" + part.toLowerCase() + "] " + what;
		}
	}

	/** how many rooms of an area that nothing reaches are named, before "and n more" */
	private static final int	UNREACHED_NAMED	= 5;

	private static List<Room> roomsOf(final Area only)
	{
		final List<Room> rooms = new ArrayList<Room>();
		final Enumeration<Room> e = (only != null) ? only.getProperMap() : CMLib.map().rooms();
		while(e.hasMoreElements())
		{
			final Room R = e.nextElement();
			if((R != null) && (R.roomID().length() > 0))
				rooms.add(R);
		}
		return rooms;
	}

	private static String dir(final int d)
	{
		return CMLib.directions().getDirectionName(d).toLowerCase();
	}

	private static void exits(final List<Room> rooms, final List<Finding> out)
	{
		for(final Room R : rooms)
		{
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Exit E = R.getRawExit(d);
				final Room R2 = R.rawDoors()[d];
				if((E != null) && (R2 == null))
					out.add(new Finding(Severity.ERROR, "EXITS", R.roomID() + " " + dir(d) + ": an exit to nowhere"));
				else
				if((R2 != null) && WorldFiles.isDevArea(R2.getArea()) && (!WorldFiles.isDevArea(R.getArea())))
					out.add(new Finding(Severity.WARNING, "EXITS", R.roomID() + " " + dir(d) + ": an exit into " + R2.roomID()
						+ ", in the dev area " + R2.getArea().Name() + ", which a normal game does not load"));
				else
				if((R2 != null) && (E != null) && E.hasADoor())
				{
					final int op = Directions.getOpDirectionCode(d);
					if((op >= 0) && (R2.rawDoors()[op] != R))
						out.add(new Finding(Severity.WARNING, "EXITS", R.roomID() + " " + dir(d) + ": a door into " + R2.roomID() + ", which has no way back"));
				}
			}
		}
	}

	/** the rooms players start in: START, and START_ by race, class and the rest */
	public static List<String> startRoomIDs()
	{
		final Set<String> ids = new TreeSet<String>();
		final CMProps page = CMProps.instance();
		final String start = page.getStr("START", "").trim();
		if(start.length() > 0)
			ids.add(start);
		for(final Enumeration<Object> k = page.keys(); k.hasMoreElements();)
		{
			final String key = (String)k.nextElement();
			if(!key.startsWith("START_"))
				continue;
			String v = page.getProperty(key);
			if(v == null)
				continue;
			if(key.substring(6).startsWith("MASK"))
			{
				final int x = v.indexOf('=');
				v = (x > 0) ? v.substring(0, x) : "";
			}
			if(v.trim().length() > 0)
				ids.add(v.trim());
		}
		return new ArrayList<String>(ids);
	}

	private static void reach(final Area only, final List<Room> rooms, final List<Finding> out)
	{
		final LinkedList<Room> todo = new LinkedList<Room>();
		final Set<Room> reached = new HashSet<Room>();
		for(final String id : startRoomIDs())
		{
			final Room R = CMLib.map().getRoom(id);
			if(R == null)
				out.add(new Finding(Severity.ERROR, "REACH", "the start room " + id + " does not exist"));
			else
			if(reached.add(R))
				todo.add(R);
		}
		if(reached.isEmpty())
			return;
		while(!todo.isEmpty())
		{
			final Room R = todo.removeFirst();
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Room R2 = R.rawDoors()[d];
				if((R2 != null) && reached.add(R2))
					todo.add(R2);
			}
		}
		// by area, so that an area apart (an instance's, a dev area) is one line
		final java.util.Map<Area, List<String>> unreached = new java.util.LinkedHashMap<Area, List<String>>();
		for(final Room R : rooms)
		{
			if(reached.contains(R) || (R.getArea() == null))
				continue;
			if(!unreached.containsKey(R.getArea()))
				unreached.put(R.getArea(), new ArrayList<String>());
			unreached.get(R.getArea()).add(R.roomID());
		}
		for(final Map.Entry<Area, List<String>> e : unreached.entrySet())
		{
			final List<String> ids = e.getValue();
			final List<String> named = ids.subList(0, Math.min(UNREACHED_NAMED, ids.size()));
			out.add(new Finding(Severity.WARNING, "REACH", e.getKey().Name() + ": " + ids.size() + " rooms nothing leads to from the start: "
				+ String.join(", ", named) + ((ids.size() > named.size()) ? (" and " + (ids.size() - named.size()) + " more") : "")));
		}
	}

	private static void addKeys(final Item I, final Set<String> keys)
	{
		if(I instanceof DoorKey)
			keys.add(((DoorKey)I).getKey().trim().toUpperCase());
	}

	/** every key in the world: in rooms, carried, in shops, in the catalog */
	private static Set<String> allKeys()
	{
		final Set<String> keys = new HashSet<String>();
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
				addKeys(i.nextElement(), keys);
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if(M == null)
					continue;
				for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
					addKeys(i.nextElement(), keys);
				if(M instanceof ShopKeeper)
				{
					for(final Iterator<Environmental> s = ((ShopKeeper)M).getShop().getStoreInventory(); s.hasNext();)
					{
						final Environmental E = s.next();
						if(E instanceof Item)
							addKeys((Item)E, keys);
					}
				}
			}
		}
		for(final String name : CMLib.catalog().getCatalogItemNames())
			addKeys(CMLib.catalog().getCatalogItem(name), keys);
		return keys;
	}

	private static void keys(final List<Room> rooms, final List<Finding> out)
	{
		final Set<String> keys = allKeys();
		for(final Room R : rooms)
		{
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Exit E = R.getRawExit(d);
				if((E != null) && E.hasALock() && (E.keyName().trim().length() > 0) && (!keys.contains(E.keyName().trim().toUpperCase())))
					out.add(new Finding(Severity.ERROR, "KEYS", R.roomID() + " " + dir(d) + ": locked with key '" + E.keyName() + "', which exists nowhere"));
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				if((I instanceof Container) && ((Container)I).hasALock() && (((Container)I).keyName().trim().length() > 0)
				&& (!keys.contains(((Container)I).keyName().trim().toUpperCase())))
					out.add(new Finding(Severity.ERROR, "KEYS", R.roomID() + " " + I.Name() + ": locked with key '" + ((Container)I).keyName() + "', which exists nowhere"));
			}
		}
	}

	private static boolean sameText(final String a, final String b)
	{
		return (a != null) && (b != null) && (a.trim().length() > 0) && a.trim().equals(b.trim());
	}

	private static void text(final List<Room> rooms, final List<Finding> out)
	{
		for(final Room R : rooms)
		{
			final Room proto = CMClass.getLocalePrototype(R.ID());
			if((R.description() != null) && R.description().contains("Brand new database room!"))
				out.add(new Finding(Severity.WARNING, "TEXT", R.roomID() + ": the new room's description"));
			else
			if((proto != null) && sameText(R.description(), proto.description()))
				out.add(new Finding(Severity.WARNING, "TEXT", R.roomID() + ": " + R.ID() + "'s default description"));
			if((proto != null) && sameText(R.displayText(), proto.displayText()))
				out.add(new Finding(Severity.WARNING, "TEXT", R.roomID() + ": " + R.ID() + "'s default title"));
			if(R.hasTag(DigMode.TODO_TAG))
				out.add(new Finding(Severity.WARNING, "TEXT", R.roomID() + ": tagged TODO, as WORLD DIG or PATH made it; EDIT DESC gives it its own text"));
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M == null) || (!M.isMonster()))
					continue;
				// a generic mob a builder made: its class's own text is a stand-in
				final MOB p = M.ID().startsWith("Gen") ? CMClass.getMOBPrototype(M.ID()) : null;
				if((p != null) && (sameText(M.Name(), p.Name()) || sameText(M.displayText(), p.displayText())))
					out.add(new Finding(Severity.WARNING, "TEXT", R.roomID() + " " + M.Name() + ": " + M.ID() + "'s default "
						+ (sameText(M.Name(), p.Name()) ? "name" : "look")));
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				final Item p = I.ID().startsWith("Gen") ? CMClass.getItemPrototype(I.ID()) : null;
				if((p != null) && (sameText(I.Name(), p.Name()) || sameText(I.displayText(), p.displayText())))
					out.add(new Finding(Severity.WARNING, "TEXT", R.roomID() + " " + I.Name() + ": " + I.ID() + "'s default "
						+ (sameText(I.Name(), p.Name()) ? "name" : "look")));
			}
		}
	}

	private static void shops(final List<Room> rooms, final List<Finding> out)
	{
		for(final Room R : rooms)
		{
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M instanceof ShopKeeper) && M.isMonster() && (!((ShopKeeper)M).getShop().getStoreInventory().hasNext()))
					out.add(new Finding(Severity.WARNING, "SHOPS", R.roomID() + " " + M.Name() + ": a shop with nothing to sell"));
			}
		}
	}

	private static void data(final List<Finding> out)
	{
		for(final Map.Entry<String, List<String>> e : WorldFiles.problems().entrySet())
		{
			for(final String p : e.getValue())
				out.add(new Finding(Severity.ERROR, "DATA", e.getKey() + ": " + p));
		}
	}

	/**
	 * Runs the check.
	 * @param only the area to check, or null for the world
	 * @param parts the parts to run (PARTS), or null for all
	 * @return what was found, errors first
	 */
	public static List<Finding> run(final Area only, final Set<String> parts)
	{
		final List<Finding> out = new ArrayList<Finding>();
		final List<Room> rooms = roomsOf(only);
		final boolean all = (parts == null) || parts.isEmpty();
		if(all || parts.contains("COMBAT"))
		{
			final CombatRules.Library profiles = CombatProfiles.check();
			for(final String error : profiles.errors) out.add(new Finding(Severity.ERROR, "COMBAT", error));
			final String selection = CombatProfiles.selectionProblem(profiles);
			if(!selection.isEmpty()) out.add(new Finding(Severity.ERROR, "COMBAT", selection));
			for(final String error : CombatProfiles.checkProperties(rooms)) out.add(new Finding(Severity.ERROR, "COMBAT", error));
		}
		if(all || parts.contains("COMPOSITIONS"))
			for(final String error : AbilityComposition.check().errors)
				out.add(new Finding(Severity.ERROR, "COMPOSITIONS", error));
		if(all || parts.contains("MODULES"))
		{
			final ModuleDefinitions.Plan modules = OptionalModules.configuredPlan();
			for(final String error : modules.errors)
				out.add(new Finding(Severity.ERROR, "MODULES", error));
			for(final String id : modules.enabled)
			{
				final String clock = GameTime.isTurnBased() ? "TURNS" : "REALTIME";
				if(!modules.definitions.get(id).modes.contains(clock))
					out.add(new Finding(Severity.ERROR, "MODULES", modules.definitions.get(id).path + ": does not support " + clock));
			}
		}
		if(all || parts.contains("EXITS"))
			exits(rooms, out);
		if(all || parts.contains("REACH"))
			reach(only, rooms, out);
		if(all || parts.contains("KEYS"))
			keys(rooms, out);
		if(all || parts.contains("TEXT"))
			text(rooms, out);
		if(all || parts.contains("SHOPS"))
			shops(rooms, out);
		if((all || parts.contains("DATA")) && (only == null))
			data(out);
		if(all || parts.contains("SCRIPTS"))
		{
			for(final String p : (only == null) ? ScriptCheck.checkAll() : ScriptCheck.checkArea(only, null))
				out.add(new Finding(Severity.ERROR, "SCRIPTS", p));
		}
		if((all || parts.contains("JS")) && (only == null))
		{
			// JS5: the world's JavaScript classes: their //extends lines, and whether they compile
			for(final String p : JSScope.checkWorldClasses())
				out.add(new Finding(Severity.ERROR, "JS", p));
		}
		if(all || parts.contains("PROPS"))
		{
			for(final String p : (only == null) ? PropCheck.checkAll() : PropCheck.checkArea(only))
				out.add(new Finding(Severity.WARNING, "PROPS", p));
		}
        for(final String part:new String[]{"VOCAB","LORE","TEMPLATES","ROUTES","IDS"})
            if(all||parts.contains(part))out.addAll(AuthoringCheck.run(part,only));
		final List<Finding> sorted = new ArrayList<Finding>();
		for(final Severity S : Severity.values())
		{
			for(final Finding F : out)
			{
				if(F.severity == S)
					sorted.add(F);
			}
		}
		return sorted;
	}

	/**
	 * The report, by severity.
	 * @param found what run found
	 * @return the report's lines
	 */
	public static List<String> report(final List<Finding> found)
	{
		final List<String> lines = new ArrayList<String>();
		int errors = 0;
		int warnings = 0;
		for(final Finding F : found)
		{
			if(F.severity == Severity.ERROR)
				errors++;
			else
				warnings++;
		}
		lines.add("WORLD CHECK: " + errors + " errors, " + warnings + " warnings.");
		Severity last = null;
		for(final Finding F : found)
		{
			if(F.severity != last)
			{
				lines.add((F.severity == Severity.ERROR) ? "Errors:" : "Warnings:");
				last = F.severity;
			}
			lines.add("  " + F);
		}
		return lines;
	}

	/**
	 * How many errors were found.
	 * @param found what run found
	 * @return the errors
	 */
	public static int errors(final List<Finding> found)
	{
		int n = 0;
		for(final Finding F : found)
		{
			if(F.severity == Severity.ERROR)
				n++;
		}
		return n;
	}

	/** CHECK=1: the check runs once the world is up, and the game ends with its result */
	private static volatile boolean	headless	= false;
	private static volatile int		exitCode	= 0;

	/**
	 * Asks for the check to run without a player (sud.sh CHECK=1).
	 * @param on true to ask
	 */
	public static void setHeadless(final boolean on)
	{
		headless = on;
	}

	/**
	 * Whether the check runs without a player.
	 * @return true if it does
	 */
	public static boolean isHeadless()
	{
		return headless;
	}

	/**
	 * Runs the whole check for CHECK=1, printing the report.
	 * @param printer where the lines go
	 * @return the exit code: 1 if there are errors, else 0
	 */
	public static int runHeadless(final java.io.PrintStream printer)
	{
		final List<Finding> found = run(null, null);
		for(final String line : report(found))
			printer.println(line);
		exitCode = (errors(found) > 0) ? 1 : 0;
		return exitCode;
	}

	/**
	 * The exit code of the headless check.
	 * @return 1 if it found errors, else 0
	 */
	public static int exitCode()
	{
		return exitCode;
	}
}
