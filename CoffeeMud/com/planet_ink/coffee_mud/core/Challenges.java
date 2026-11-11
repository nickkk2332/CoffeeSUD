package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import com.planet_ink.coffee_mud.Libraries.interfaces.AchievementLibrary.Achievement;
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
 * P13: challenge books.  world/challenges.ini names books of achievements,
 * one a line: [id]=TITLE="..." ENTRIES="ACH_A,ACH_B" and/or PREFIX="ACH_".
 * CHALLENGES lists each book with how many entries are done and how many are
 * still hidden (an entry whose VISIBLEMASK one does not pass, not yet done);
 * CHALLENGES [book] lists the entries one may see, and one's personal records
 * for the book.  A record (MPRECORD, cm.record) keeps its first value and its
 * best, each with when, in the character's story records, so the save keeps
 * them.
 */
public final class Challenges
{
	private Challenges()
	{
	}

	public static final String FAMILY = "challenges";

	/** a book as the file declares it */
	public static final class Book
	{
		public final String			id, title, prefix;
		public final List<String>	named;

		Book(final String id, final String parms)
		{
			this.id = id.trim().toUpperCase(Locale.ROOT);
			title = CMParms.getParmStr(parms, "TITLE", this.id.toLowerCase(Locale.ROOT));
			prefix = CMParms.getParmStr(parms, "PREFIX", "").trim().toUpperCase(Locale.ROOT);
			final List<String> n = new ArrayList<String>();
			for(final String e : CMParms.parseCommas(CMParms.getParmStr(parms, "ENTRIES", ""), true))
			{
				if(e.trim().length() > 0)
					n.add(e.trim().toUpperCase(Locale.ROOT));
			}
			named = Collections.unmodifiableList(n);
		}

		/** its achievements: those named, then those of its prefix, each once */
		public List<Achievement> entries()
		{
			final Map<String, Achievement> out = new LinkedHashMap<String, Achievement>();
			for(final String t : named)
			{
				final Achievement A = CMLib.achievements().getAchievement(t);
				if(A != null)
					out.put(A.getTattoo().toUpperCase(Locale.ROOT), A);
			}
			if(prefix.length() > 0)
			{
				final List<Achievement> more = new ArrayList<Achievement>();
				for(final Enumeration<Achievement> e = CMLib.achievements().achievements(null); e.hasMoreElements();)
				{
					final Achievement A = e.nextElement();
					if(A.getTattoo().toUpperCase(Locale.ROOT).startsWith(prefix))
						more.add(A);
				}
				more.sort(Comparator.comparing(a -> a.getTattoo()));
				for(final Achievement A : more)
					out.putIfAbsent(A.getTattoo().toUpperCase(Locale.ROOT), A);
			}
			return new ArrayList<Achievement>(out.values());
		}
	}

	/** the books the world declares; none without the file */
	public static Map<String, Book> books()
	{
		final Map<String, Book> out = new TreeMap<String, Book>();
		final File f = new File(WorldFiles.worldDir(), "challenges.ini");
		if(!f.isFile())
			return out;
		try(final Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8))
		{
			final Properties p = new Properties();
			p.load(r);
			for(final String key : p.stringPropertyNames())
				out.put(key.trim().toUpperCase(Locale.ROOT), new Book(key, p.getProperty(key)));
		}
		catch(final IOException | IllegalArgumentException e)
		{
			Log.errOut("Challenges", "challenges.ini: " + e.getMessage());
		}
		return out;
	}

	/** what is wrong with the file: entries that are no achievement, books with none */
	public static List<String> problems()
	{
		final List<String> out = new ArrayList<String>();
		for(final Book b : books().values())
		{
			if(b.entries().isEmpty())
				out.add("challenges.ini: " + b.id + ": the book has no entries");
		}
		return out;
	}

	private static boolean done(final MOB who, final Achievement A)
	{
		return who.findTattoo(A.getTattoo()) != null;
	}

	private static Book book(final String name) throws IOException
	{
		final String n = name.trim().toUpperCase(Locale.ROOT);
		for(final Book b : books().values())
		{
			if(b.id.equals(n) || b.title.equalsIgnoreCase(name.trim()))
				return b;
		}
		for(final Book b : books().values())
		{
			if(b.title.toUpperCase(Locale.ROOT).startsWith(n) || b.id.startsWith(n))
				return b;
		}
		throw new IOException("no challenge book '" + name.trim() + "'");
	}

	/** CHALLENGES: each book, what is done, and how many entries are still hidden */
	public static String list(final MOB who)
	{
		final Map<String, Book> all = books();
		if(all.isEmpty())
			return "This world has no challenge books.";
		final StringBuilder s = new StringBuilder("Challenge books:");
		for(final Book b : all.values())
		{
			int done = 0, hidden = 0;
			final List<Achievement> entries = b.entries();
			for(final Achievement A : entries)
			{
				if(done(who, A))
					done++;
				else
				if(!A.canBeSeenBy(who))
					hidden++;
			}
			s.append("\n  ").append(b.title).append(": ").append(done).append(" of ").append(entries.size()).append(" done")
				.append((hidden > 0) ? "; " + hidden + " hidden " + ((hidden == 1) ? "entry" : "entries") : "");
		}
		return s.append("\nCHALLENGES [book] shows one.").toString();
	}

	/** CHALLENGES [book]: its entries one may see, the count of the rest, and one's records */
	public static String show(final MOB who, final String name) throws IOException
	{
		final Book b = book(name);
		final StringBuilder s = new StringBuilder(b.title).append(':');
		int hidden = 0;
		for(final Achievement A : b.entries())
		{
			final boolean d = done(who, A);
			if(!d && !A.canBeSeenBy(who))
			{
				hidden++;
				continue;
			}
			s.append("\n  [").append(d ? "x" : " ").append("] ").append(A.getDisplayStr());
		}
		if(hidden > 0)
			s.append("\n  ...and ").append(hidden).append(" hidden ").append((hidden == 1) ? "entry" : "entries").append(", shown once you find the way to them");
		final MiniJSON.JSONObject recs = StoryRecords.object(StoryRecords.object(StoryRecords.read(who), "records"), b.id);
		if(!recs.isEmpty())
		{
			s.append("\nYour records:");
			for(final String k : new TreeSet<String>(recs.keySet()))
			{
				final MiniJSON.JSONObject r = (MiniJSON.JSONObject)recs.get(k);
				s.append("\n  ").append(k).append(": best ").append(num(r.get("best"))).append(" (").append(r.get("bestAt")).append("); first ")
					.append(num(r.get("first"))).append(" (").append(r.get("firstAt")).append("); ").append(r.get("count")).append(" time(s)");
			}
		}
		return s.toString();
	}

	private static String num(final Object o)
	{
		if(!(o instanceof Number))
			return String.valueOf(o);
		final double d = ((Number)o).doubleValue();
		return (d == Math.rint(d)) ? Long.toString((long)d) : String.format(Locale.ROOT, "%.2f", Double.valueOf(d));
	}

	private static String now()
	{
		try
		{
			return CMLib.time().globalClock().getShortTimeDescription();
		}
		catch(final RuntimeException e)
		{
			return "game time " + GameTime.now();
		}
	}

	/**
	 * Records a value for a book: the first one is kept, and the best (the
	 * highest, or with lower the lowest), each with when.
	 * @return "first", "best" (a new best), or "" (neither)
	 */
	public static String record(final MOB who, final String bookName, final String key, final double value, final boolean lower) throws IOException
	{
		if(who == null)
			throw new IOException("a record is someone's");
		final Book b = book(bookName);
		final String k = key.trim().toLowerCase(Locale.ROOT);
		if(!k.matches("[a-z0-9][a-z0-9 _.-]{0,39}"))
			throw new IOException("a record's name is 1 to 40 letters, digits, spaces, '.', '_' and '-'");
		final MiniJSON.JSONObject all = StoryRecords.read(who), records = StoryRecords.object(all, "records"), recs = StoryRecords.object(records, b.id);
		final MiniJSON.JSONObject r = recs.containsKey(k) ? (MiniJSON.JSONObject)recs.get(k) : new MiniJSON.JSONObject();
		String said = "";
		final String at = now();
		if(!r.containsKey("first"))
		{
			r.put("first", Double.valueOf(value));
			r.put("firstAt", at);
			r.put("best", Double.valueOf(value));
			r.put("bestAt", at);
			said = "first";
		}
		else
		{
			final double best = ((Number)r.get("best")).doubleValue();
			if(lower ? (value < best) : (value > best))
			{
				r.put("best", Double.valueOf(value));
				r.put("bestAt", at);
				said = "best";
			}
		}
		r.put("count", Long.valueOf(((r.get("count") instanceof Number) ? ((Number)r.get("count")).longValue() : 0) + 1));
		r.put("lower", Boolean.valueOf(lower));
		recs.put(k, r);
		records.put(b.id, recs);
		all.put("records", records);
		StoryRecords.write(who, all);
		if(said.length() > 0)
		{
			ModuleStats.count(FAMILY, b.id.toLowerCase(Locale.ROOT), said.equals("first") ? "first records" : "new bests");
			Provenance.event(who, FAMILY, "challenges.ini " + b.id, (said.equals("first") ? "a first record of " : "a new best at ") + key + ": " + value);
		}
		return said;
	}
}
