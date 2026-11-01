package com.planet_ink.coffee_mud.core.save;

import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.io.*;
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
 * Tools for looking at saved games: what one holds (SAVES INFO), what
 * differs between two (SAVES DIFF), copying one, and the rotating saves of
 * CHECKPOINT and AUTOSAVE.
 */
public final class SaveTools
{
	/** the saves CHECKPOINT writes in turn, and ROLLBACK restores the newest of */
	public static final String	CHECKPOINT_SLOT	= "checkpoint";
	public static final int		CHECKPOINTS		= 3;

	private SaveTools()
	{
	}

	private static String L(final String msg, final String... xs)
	{
		return CMLib.lang().L(msg, xs);
	}

	/**
	 * Returns the save to write next of a set that take turns: SLOT1 to
	 * SLOTn, the one never written or written longest ago.
	 * @param slot the set's name
	 * @param n how many in the set
	 * @return the save's name
	 */
	public static String nextInTurn(final String slot, final int n)
	{
		String oldest = slot + "1";
		long oldestTime = Long.MAX_VALUE;
		for(int i = 1; i <= Math.max(1, n); i++)
		{
			final long when = SaveGame.savedAt(slot + i);
			if(when < oldestTime)
			{
				oldestTime = when;
				oldest = slot + i;
			}
		}
		return oldest;
	}

	/**
	 * Returns the newest of a set of saves that take turns.
	 * @param slot the set's name
	 * @param n how many in the set
	 * @return the save's name, or null if none was written
	 */
	public static String newestInTurn(final String slot, final int n)
	{
		String newest = null;
		long newestTime = 0;
		for(int i = 1; i <= Math.max(1, n); i++)
		{
			final long when = SaveGame.savedAt(slot + i);
			if(when > newestTime)
			{
				newestTime = when;
				newest = slot + i;
			}
		}
		return newest;
	}

	/**
	 * Copies a saved game to another name.
	 * @param from the save
	 * @param to its new name
	 * @return null if copied, or why not
	 */
	public static String copy(final String from, final String to)
	{
		final File saveDir = SaveGame.saveDir();
		if(!SaveSlots.isValidName(to))
			return L("A save's name is letters, digits, dashes and underscores, at most 32 of them.");
		final File src = new File(saveDir, from);
		final File dst = new File(saveDir, to);
		if(!new File(src, "header.properties").isFile())
			return L("There is no saved game called '@x1'.", from);
		if(dst.exists())
			return L("There is already a save called '@x1'.", to);
		try
		{
			copyTree(src, dst);
		}
		catch(final IOException e)
		{
			return L("The save could not be copied: @x1", e.getMessage());
		}
		return null;
	}

	private static void copyTree(final File from, final File to) throws IOException
	{
		if(from.isDirectory())
		{
			if(!to.mkdirs())
				throw new IOException("cannot make " + to.getPath());
			final File[] list = from.listFiles();
			if(list != null)
			{
				for(final File F : list)
					copyTree(F, new File(to, F.getName()));
			}
		}
		else
			java.nio.file.Files.copy(from.toPath(), to.toPath());
	}

	private static List<XMLTag> world(final String slot)
	{
		final String text = SaveSlots.readFile(SaveGame.saveDir(), slot, WorldState.FILE);
		if(text == null)
			return new ArrayList<XMLTag>();
		final List<XMLTag> all = CMLib.xml().parseAllXML(text);
		if((all.size() > 0) && all.get(0).tag().equalsIgnoreCase("WORLD"))
			return all.get(0).contents();
		return new ArrayList<XMLTag>();
	}

	private static String nameIn(final XMLTag thing)
	{
		final String text = CMLib.xml().restoreAngleBrackets(thing.getValFromPieces("TEXT"));
		final int x = text.indexOf("<NAME>");
		final int y = (x < 0) ? -1 : text.indexOf("</NAME>", x);
		if((x >= 0) && (y > x))
			return CMLib.xml().restoreAngleBrackets(text.substring(x + 6, y));
		return thing.getValFromPieces("CLASS");
	}

	/**
	 * Describes what a saved game holds: who, where, when, the party, the
	 * quests running, the variables and the world it was made with.
	 * @param slot the save
	 * @return the description, or null if there is no such save
	 */
	public static String info(final String slot)
	{
		final Properties h = SaveSlots.readHeader(SaveGame.saveDir(), slot);
		if(h == null)
			return null;
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder(L("Saved game '@x1':\n\r", slot));
		str.append(L("  Character : @x1, level @x2 @x3 @x4\n\r", h.getProperty("character", "?"), h.getProperty("level", "?"),
			h.getProperty("race", ""), h.getProperty("class", "")));
		str.append(L("  Where     : @x1 (@x2)\n\r", h.getProperty("place", "?"), h.getProperty("area", "?")));
		final long saved = CMath.s_long(h.getProperty("saved", "0"));
		if(saved > 0)
			str.append(L("  Saved     : @x1\n\r", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(saved))));
		if(h.getProperty("gamedate", "").length() > 0)
			str.append(L("  Game date : @x1\n\r", h.getProperty("gamedate")));
		str.append(L("  Time      : @x1@x2, save mode @x3\n\r", h.getProperty("timemode", "?"),
			"true".equalsIgnoreCase(h.getProperty("paused")) ? L(", paused") : "", h.getProperty("savemode", "ANYWHERE")));
		final List<XMLTag> world = world(slot);
		final XMLTag party = xml.getPieceFromPieces(world, "PARTY");
		if((party != null) && (party.contents().size() > 0))
		{
			str.append(L("  Party     :"));
			for(final XMLTag p : party.contents())
				str.append(' ').append(xml.restoreAngleBrackets(p.getValFromPieces("NAME")))
				   .append(" (").append(xml.restoreAngleBrackets(p.getValFromPieces("ROOM"))).append(')');
			str.append("\n\r");
		}
		final XMLTag quests = xml.getPieceFromPieces(world, "QUESTS");
		final List<String> running = new ArrayList<String>();
		if(quests != null)
		{
			for(final XMLTag q : quests.contents())
			{
				if(q.getBoolFromPieces("RUNNING", false))
					running.add(xml.restoreAngleBrackets(q.getValFromPieces("NAME")) + "#" + q.getValFromPieces("STEP"));
			}
		}
		str.append(L("  Quests    : @x1\n\r", (running.size() == 0) ? L("none running") : CMParms.toListString(running.iterator())));
		final XMLTag vars = xml.getPieceFromPieces(world, "SCRIPTVARS");
		str.append(L("  Variables : @x1\n\r", "" + ((vars == null) ? 0 : vars.contents().size())));
		final String sum = h.getProperty("worldsum", "");
		str.append(L("  World     : @x1@x2\n\r", (sum.length() == 0) ? "?" : sum,
			((sum.length() > 0) && (!sum.equals(WorldFiles.bootChecksum()))) ? L(" (older world)") : L(" (this world)")));
		return str.toString();
	}

	private static Map<String, Integer> countNames(final List<String> names)
	{
		final Map<String, Integer> counts = new TreeMap<String, Integer>();
		for(final String n : names)
			counts.put(n, Integer.valueOf(counts.containsKey(n) ? (counts.get(n).intValue() + 1) : 1));
		return counts;
	}

	private static void diffCounts(final List<String> out, final String what, final List<String> a, final List<String> b)
	{
		final Map<String, Integer> ca = countNames(a);
		final Map<String, Integer> cb = countNames(b);
		final Set<String> all = new TreeSet<String>(ca.keySet());
		all.addAll(cb.keySet());
		for(final String n : all)
		{
			final int na = ca.containsKey(n) ? ca.get(n).intValue() : 0;
			final int nb = cb.containsKey(n) ? cb.get(n).intValue() : 0;
			if(nb > na)
				out.add(what + ": +" + ((nb - na > 1) ? ((nb - na) + " ") : "") + n);
			else
			if(na > nb)
				out.add(what + ": -" + ((na - nb > 1) ? ((na - nb) + " ") : "") + n);
		}
	}

	private static Map<String, String> varsOf(final List<XMLTag> world)
	{
		final XMLLibrary xml = CMLib.xml();
		final Map<String, String> vars = new TreeMap<String, String>();
		final XMLTag tag = xml.getPieceFromPieces(world, "SCRIPTVARS");
		if(tag != null)
		{
			for(final XMLTag v : tag.contents())
			{
				vars.put(xml.restoreAngleBrackets(v.getValFromPieces("HOST")) + " " + xml.restoreAngleBrackets(v.getValFromPieces("NAME")),
					xml.restoreAngleBrackets(v.getValFromPieces("VALUE")));
			}
		}
		return vars;
	}

	private static Map<String, XMLTag> questsOf(final List<XMLTag> world)
	{
		final Map<String, XMLTag> quests = new TreeMap<String, XMLTag>();
		final XMLTag tag = CMLib.xml().getPieceFromPieces(world, "QUESTS");
		if(tag != null)
		{
			for(final XMLTag q : tag.contents())
				quests.put(CMLib.xml().restoreAngleBrackets(q.getValFromPieces("NAME")), q);
		}
		return quests;
	}

	private static Map<String, List<String>[]> roomsOf(final List<XMLTag> world)
	{
		final Map<String, List<String>[]> rooms = new TreeMap<String, List<String>[]>();
		final XMLTag tag = CMLib.xml().getPieceFromPieces(world, "ROOMS");
		if(tag != null)
		{
			for(final XMLTag r : tag.contents())
			{
				@SuppressWarnings("unchecked")
				final List<String>[] things = new List[] { new ArrayList<String>(), new ArrayList<String>() };
				for(final XMLTag piece : r.contents())
				{
					if(piece.tag().equalsIgnoreCase("MOB"))
						things[0].add(nameIn(piece));
					else
					if(piece.tag().equalsIgnoreCase("ITEM"))
						things[1].add(nameIn(piece));
				}
				rooms.put(CMLib.xml().restoreAngleBrackets(r.getValFromPieces("ID")), things);
			}
		}
		return rooms;
	}

	/**
	 * Lists what differs between two saved games: the character and where
	 * they are, their hit points and what they carry, the variables, the
	 * quests, and what is in each room.
	 * @param a the first save
	 * @param b the second
	 * @return the differences, one a line, or null if a save is missing
	 */
	public static List<String> diff(final String a, final String b)
	{
		final Properties ha = SaveSlots.readHeader(SaveGame.saveDir(), a);
		final Properties hb = SaveSlots.readHeader(SaveGame.saveDir(), b);
		if((ha == null) || (hb == null))
			return null;
		final XMLLibrary xml = CMLib.xml();
		final List<String> out = new ArrayList<String>();
		for(final String key : new String[] { "character", "level", "class", "place", "area", "timemode", "savemode", "party", "gamedate" })
		{
			final String va = ha.getProperty(key, "");
			final String vb = hb.getProperty(key, "");
			if(!va.equals(vb))
				out.add(key + ": " + va + " -> " + vb);
		}
		final List<XMLTag> wa = world(a);
		final List<XMLTag> wb = world(b);
		final XMLTag pa = xml.getPieceFromPieces(wa, "PLAYER");
		final XMLTag pb = xml.getPieceFromPieces(wb, "PLAYER");
		if((pa != null) && (pb != null))
		{
			for(final String key : new String[] { "ROOM", "HP", "MANA", "MOVE" })
			{
				final String va = xml.restoreAngleBrackets(pa.getValFromPieces(key));
				final String vb = xml.restoreAngleBrackets(pb.getValFromPieces(key));
				if(!va.equals(vb))
					out.add("player " + key.toLowerCase() + ": " + va + " -> " + vb);
			}
			final List<String> ia = new ArrayList<String>();
			final List<String> ib = new ArrayList<String>();
			final XMLTag inva = pa.getPieceFromPieces("INVENTORY");
			final XMLTag invb = pb.getPieceFromPieces("INVENTORY");
			if(inva != null)
				for(final XMLTag I : inva.contents())
					ia.add(nameIn(I));
			if(invb != null)
				for(final XMLTag I : invb.contents())
					ib.add(nameIn(I));
			diffCounts(out, "carried", ia, ib);
		}
		final Map<String, String> va = varsOf(wa);
		final Map<String, String> vb = varsOf(wb);
		final Set<String> allVars = new TreeSet<String>(va.keySet());
		allVars.addAll(vb.keySet());
		for(final String k : allVars)
		{
			final String x = va.get(k);
			final String y = vb.get(k);
			if(x == null)
				out.add("variable " + k + ": set to " + y);
			else
			if(y == null)
				out.add("variable " + k + ": gone (was " + x + ")");
			else
			if(!x.equals(y))
				out.add("variable " + k + ": " + x + " -> " + y);
		}
		final Map<String, XMLTag> qa = questsOf(wa);
		final Map<String, XMLTag> qb = questsOf(wb);
		final Set<String> allQuests = new TreeSet<String>(qa.keySet());
		allQuests.addAll(qb.keySet());
		for(final String q : allQuests)
		{
			final XMLTag x = qa.get(q);
			final XMLTag y = qb.get(q);
			final String sx = (x == null) ? "absent" : (x.getBoolFromPieces("RUNNING", false) ? ("running, step " + x.getValFromPieces("STEP")) : "not running");
			final String sy = (y == null) ? "absent" : (y.getBoolFromPieces("RUNNING", false) ? ("running, step " + y.getValFromPieces("STEP")) : "not running");
			if(!sx.equals(sy))
				out.add("quest " + q + ": " + sx + " -> " + sy);
			final String vx = (x == null) ? "" : String.valueOf(x.getValFromPieces("VARS"));
			final String vy = (y == null) ? "" : String.valueOf(y.getValFromPieces("VARS"));
			if((x != null) && (y != null) && (!vx.equals(vy)))
				out.add("quest " + q + ": its variables changed");
		}
		final Map<String, List<String>[]> ra = roomsOf(wa);
		final Map<String, List<String>[]> rb = roomsOf(wb);
		final Set<String> allRooms = new TreeSet<String>(ra.keySet());
		allRooms.addAll(rb.keySet());
		@SuppressWarnings("unchecked")
		final List<String>[] none = new List[] { new ArrayList<String>(), new ArrayList<String>() };
		for(final String r : allRooms)
		{
			final List<String>[] x = ra.containsKey(r) ? ra.get(r) : none;
			final List<String>[] y = rb.containsKey(r) ? rb.get(r) : none;
			diffCounts(out, r + " mobs", x[0], y[0]);
			diffCounts(out, r + " items", x[1], y[1]);
		}
		return out;
	}

	/**
	 * Saves the game now and then, as AUTOSAVE says: every so many game
	 * minutes, or when the player goes into another area, in saves that take
	 * turns (AUTOSAVEKEEP of them, autosave1 and on).  It follows the save
	 * rules for fights, as an autosave does.
	 */
	public static final class AutoSaver implements Tickable
	{
		private final MOB		player;
		private final long		everyMs;
		private final boolean	onAreaChange;
		private final int		keep;
		private long			lastAt	= GameTime.now();
		private Area			lastArea;
		private volatile boolean stopped = false;

		public AutoSaver(final MOB player, final String setting, final int keep)
		{
			this.player = player;
			this.onAreaChange = setting.trim().toUpperCase().startsWith("ON-AREA");
			this.everyMs = onAreaChange ? 0 : (CMath.s_long(setting.trim()) * CMProps.getMillisPerMudHour() / 60L);
			this.keep = Math.max(1, keep);
			this.lastArea = (player.location() == null) ? null : player.location().getArea();
		}

		public void stop()
		{
			stopped = true;
		}

		@Override
		public String ID()
		{
			return "AutoSaver";
		}

		@Override
		public String name()
		{
			return ID();
		}

		@Override
		public CMObject newInstance()
		{
			return this;
		}

		@Override
		public CMObject copyOf()
		{
			return this;
		}

		@Override
		public void initializeClass()
		{
		}

		@Override
		public int compareTo(final CMObject o)
		{
			return (o == this) ? 0 : 1;
		}

		@Override
		public int getTickStatus()
		{
			return Tickable.STATUS_NOT;
		}

		@Override
		public boolean tick(final Tickable ticking, final int tickID)
		{
			if(stopped || player.amDestroyed() || (player.session() == null))
				return false;
			final Area here = (player.location() == null) ? null : player.location().getArea();
			boolean due = false;
			if(onAreaChange)
				due = (here != null) && (lastArea != null) && (here != lastArea);
			else
				due = (everyMs > 0) && ((GameTime.now() - lastAt) >= everyMs);
			if(here != null)
				lastArea = here;
			if(!due)
				return true;
			final String slot = nextInTurn("autosave", keep);
			if(SaveGame.autoSave(player, slot) == null)
			{
				lastAt = GameTime.now();
				player.tell(CMLib.lang().L("[Autosaved as '@x1']", slot));
			}
			return true;
		}
	}
}
