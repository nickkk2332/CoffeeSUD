package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.CRC32;

import com.planet_ink.coffee_mud.Common.interfaces.Clan;
import com.planet_ink.coffee_mud.Common.interfaces.Tattoo;
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
 * Phase 2's rules files, in a save: a save keeps each one's sum, and a restore
 * names each that changed since, with what the change means for a campaign
 * already under way (each feature keeps what it recorded, and reads the new
 * rules from then on).
 */
public final class RulesFiles
{
	private RulesFiles()
	{
	}

	/** each file, and what a change to it means for a save already made */
	private static final String[][] FILES = {
		{ "cultures.ini", "cultures already taken up keep what they gave; leaving one takes away what was recorded, even if the file no longer has it; renames.ini carries a renamed one over" },
		{ "talents.ini", "talents already decided stay decided; the others are checked against the file as it is now" },
		{ "formations.ini", "members keep their roles by name; a role the file no longer has does nothing until it is given again; renames.ini carries a renamed one over" },
		{ "orgpools.ini", "pools keep their units; a kind the file no longer has keeps them, unseen, until it is back" },
		{ "challenges.ini", "personal records are kept; a book the file no longer has is not shown" },
		{ "affiliations.ini", "the rules as they are now apply from now on; a new CONFLICT does not end memberships already held, it refuses the next join" },
	};

	private static String sumOf(final File F)
	{
		if(!F.isFile())
			return "none";
		try
		{
			final CRC32 crc = new CRC32();
			crc.update(Files.readAllBytes(F.toPath()));
			return F.length() + "-" + Long.toHexString(crc.getValue());
		}
		catch(final java.io.IOException e)
		{
			return "?";
		}
	}

	/** the files' sums now, as a save keeps them: "file=sum;..." */
	public static String saved()
	{
		final StringBuilder s = new StringBuilder();
		for(final String[] f : FILES)
			s.append((s.length() > 0) ? ";" : "").append(f[0]).append('=').append(sumOf(new File(WorldFiles.worldDir(), f[0])));
		return s.toString();
	}

	/** the restore report's lines for the files that changed since a save (none for a save that kept no sums) */
	public static List<String> changes(final String savedSums)
	{
		final List<String> out = new ArrayList<String>();
		if((savedSums == null) || savedSums.trim().isEmpty())
			return out;
		final Map<String, String> was = new TreeMap<String, String>();
		for(final String pair : savedSums.split(";"))
		{
			final int x = pair.indexOf('=');
			if(x > 0)
				was.put(pair.substring(0, x), pair.substring(x + 1));
		}
		for(final String[] f : FILES)
		{
			final String before = was.get(f[0]);
			final String now = sumOf(new File(WorldFiles.worldDir(), f[0]));
			if((before == null) || before.equals(now))
				continue;
			final String how = before.equals("none") ? "New" : now.equals("none") ? "Gone" : "Changed";
			out.add(how + " rules file: " + f[0] + " (" + f[1] + ")."); // before boot: no language library yet
		}
		return out;
	}

	/** the rules files whose entries characters and organizations hold by name, so renames.ini can rename them */
	private static final String[] RENAMABLE = { "cultures.ini", "talents.ini", "formations.ini", "orgpools.ini", "challenges.ini" };

	/** world/renames.ini: a [file] section per rules file, and in it OLD=NEW for each entry given a new name */
	public static File renamesFile()
	{
		return new File(WorldFiles.worldDir(), "renames.ini");
	}

	private static String key(final String s)
	{
		return s.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	/** each rules file's renames, OLD to NEW in upper case, as renames.ini has them now (followed to the last name) */
	public static Map<String, Map<String, String>> renames()
	{
		final Map<String, Map<String, String>> all = read(null);
		for(final Map<String, String> m : all.values())
		{
			for(final Map.Entry<String, String> e : m.entrySet())
			{
				String now = e.getValue();
				for(int i = 0; (i < 8) && m.containsKey(now) && !m.get(now).equals(e.getKey()); i++)
					now = m.get(now);
				e.setValue(now);
			}
		}
		return all;
	}

	private static Map<String, Map<String, String>> read(final List<String> problems)
	{
		final Map<String, Map<String, String>> all = new TreeMap<String, Map<String, String>>();
		final File F = renamesFile();
		if(!F.isFile())
			return all;
		final List<String> lines;
		try
		{
			lines = Files.readAllLines(F.toPath(), StandardCharsets.UTF_8);
		}
		catch(final IOException e)
		{
			if(problems != null)
				problems.add("renames.ini: " + e.getMessage());
			return all;
		}
		Map<String, String> section = null;
		boolean skipping = false;
		for(int i = 0; i < lines.size(); i++)
		{
			final String l = lines.get(i).trim();
			if(l.isEmpty() || l.startsWith("#") || l.startsWith(";"))
				continue;
			if(l.startsWith("[") && l.endsWith("]"))
			{
				final String file = l.substring(1, l.length() - 1).trim().toLowerCase(Locale.ROOT);
				section = null;
				skipping = !Arrays.asList(RENAMABLE).contains(file);
				if(skipping)
				{
					if(problems != null)
						problems.add("renames.ini: line " + (i + 1) + ": [" + file + "] is not a rules file whose entries are held by name (" + String.join(", ", RENAMABLE) + ")");
					continue;
				}
				section = all.get(file);
				if(section == null)
				{
					section = new LinkedHashMap<String, String>();
					all.put(file, section);
				}
				continue;
			}
			final int x = l.indexOf('=');
			if((x <= 0) || (x == l.length() - 1))
			{
				if(problems != null)
					problems.add("renames.ini: line " + (i + 1) + " is not OLD=NEW: " + l);
				continue;
			}
			if(section == null)
			{
				if((problems != null) && !skipping)
					problems.add("renames.ini: line " + (i + 1) + " comes before any [file] section: " + l);
				continue;
			}
			section.put(key(l.substring(0, x)), key(l.substring(x + 1)));
		}
		return all;
	}

	/** the entries a rules file has now, by name: [ID] sections, or ID=... lines in a file without sections */
	public static Set<String> entries(final String file)
	{
		final Set<String> ids = new TreeSet<String>();
		final Set<String> lined = new TreeSet<String>();
		final File F = new File(WorldFiles.worldDir(), file);
		if(!F.isFile())
			return ids;
		try
		{
			for(final String raw : Files.readAllLines(F.toPath(), StandardCharsets.UTF_8))
			{
				final String l = raw.trim();
				if(l.isEmpty() || l.startsWith("#") || l.startsWith(";"))
					continue;
				if(l.startsWith("[") && (l.indexOf(']') > 1))
					ids.add(key(l.substring(1, l.indexOf(']'))));
				else
				if(l.indexOf('=') > 0)
					lined.add(key(l.substring(0, l.indexOf('='))));
			}
		}
		catch(final IOException e)
		{
			return ids;
		}
		return ids.isEmpty() ? lined : ids;
	}

	/** WORLD CHECK: what is wrong in renames.ini */
	public static List<String> problems()
	{
		final List<String> out = new ArrayList<String>();
		final Map<String, Map<String, String>> all = read(out);
		for(final Map.Entry<String, Map<String, String>> f : all.entrySet())
		{
			final Set<String> has = entries(f.getKey());
			final Map<String, String> m = f.getValue();
			for(final Map.Entry<String, String> r : m.entrySet())
			{
				final String where = "renames.ini: [" + f.getKey() + "] " + r.getKey() + "=" + r.getValue();
				if(r.getKey().equals(r.getValue()))
				{
					out.add(where + ": a name renamed to itself");
					continue;
				}
				final Set<String> seen = new HashSet<String>();
				String now = r.getKey();
				while(m.containsKey(now) && seen.add(now))
					now = m.get(now);
				if(m.containsKey(now))
					out.add(where + ": the renames go round in a circle");
				else
				if(!has.contains(now))
					out.add(where + ": " + f.getKey() + " has no " + now);
				if(has.contains(r.getKey()))
					out.add(where + ": " + f.getKey() + " still has " + r.getKey() + ", so whoever holds it would be moved to " + now);
			}
		}
		return out;
	}

	/** renames a tattoo, keeping its time; @return true if it had the old one */
	static boolean renameTattoo(final MOB M, final String from, final String to)
	{
		final Tattoo T = M.findTattoo(from);
		if(T == null)
			return false;
		M.delTattoo(T);
		if(M.findTattoo(to) == null)
		{
			if(T.getTickDown() > 0)
				M.addTattoo(to, T.getTickDown());
			else
				M.addTattoo(to);
		}
		return true;
	}

	/**
	 * Puts renames.ini's new names on what a character holds by the old ones:
	 * its culture, its role, its talents and its personal records, each noted
	 * in its LEDGER.
	 * @param M the character (or a follower)
	 * @return what was renamed, in words
	 */
	public static List<String> migrate(final MOB M)
	{
		return migrate(M, renames());
	}

	private static List<String> migrate(final MOB M, final Map<String, Map<String, String>> all)
	{
		final List<String> did = new ArrayList<String>();
		if((M == null) || all.isEmpty())
			return did;
		final Map<String, String> none = Collections.emptyMap();
		note(M, "cultures.ini", Cultures.rename(M, all.getOrDefault("cultures.ini", none)), did);
		note(M, "formations.ini", FormationRoles.rename(M, all.getOrDefault("formations.ini", none)), did);
		note(M, "talents.ini", Talents.rename(M, all.getOrDefault("talents.ini", none)), did);
		note(M, "challenges.ini", Challenges.rename(M, all.getOrDefault("challenges.ini", none)), did);
		return did;
	}

	private static void note(final MOB M, final String file, final List<String> what, final List<String> did)
	{
		for(final String w : what)
		{
			Provenance.event(M, "rules", "renames.ini [" + file + "]", w);
			did.add(M.Name() + ": " + w + " (renames.ini)");
		}
	}

	/**
	 * Puts renames.ini's new names on a character, its followers, and the
	 * pools of its organizations, after a restore or when it comes into the
	 * world.
	 * @param M the character
	 * @return what was renamed, in words
	 */
	public static List<String> migrateAll(final MOB M)
	{
		final Map<String, Map<String, String>> all = renames();
		final List<String> did = new ArrayList<String>();
		if((M == null) || all.isEmpty())
			return did;
		did.addAll(migrate(M, all));
		for(final Enumeration<com.planet_ink.coffee_mud.core.collections.Pair<MOB, Short>> f = M.followers(); f.hasMoreElements();)
		{
			final com.planet_ink.coffee_mud.core.collections.Pair<MOB, Short> F = f.nextElement();
			if((F != null) && (F.first != null))
				did.addAll(migrate(F.first, all));
		}
		final Map<String, String> pools = all.get("orgpools.ini");
		if(pools != null)
		{
			for(final com.planet_ink.coffee_mud.core.collections.Pair<Clan, Integer> p : M.clans())
			{
				try
				{
					for(final String w : OrgPools.rename(p.first, pools))
					{
						Provenance.event(M, "rules", "renames.ini [orgpools.ini]", w);
						did.add(w + " (renames.ini)");
					}
				}
				catch(final IOException e)
				{
					did.add(p.first.getName() + ": " + e.getMessage());
				}
			}
		}
		return did;
	}

	/** WORLD MODULES RULES: the rules files, what each holds, and renames.ini */
	public static String describe()
	{
		final StringBuilder s = new StringBuilder("Rules files (world/, not modules: always on when present, kept by name in saves):");
		for(final String[] f : FILES)
		{
			final File F = new File(WorldFiles.worldDir(), f[0]);
			s.append("\n  ").append(f[0]).append(": ");
			if(!F.isFile())
				s.append("none");
			else
			{
				final Set<String> ids = entries(f[0]);
				s.append(f[0].equals("affiliations.ini") ? (F.length() + " bytes") : (ids.size() + " entr" + ((ids.size() == 1) ? "y" : "ies")));
				s.append(", sum ").append(sumOf(F));
			}
			s.append("\n    after a change: ").append(f[1]);
		}
		final Map<String, Map<String, String>> all = renames();
		if(all.isEmpty())
			s.append("\nrenames.ini: none. A [file] section of OLD=NEW lines renames an entry characters already hold.");
		else
		{
			s.append("\nrenames.ini:");
			for(final Map.Entry<String, Map<String, String>> f : all.entrySet())
				for(final Map.Entry<String, String> r : f.getValue().entrySet())
					s.append("\n  ").append(f.getKey()).append(": ").append(r.getKey()).append(" is ").append(r.getValue()).append(" now");
		}
		return s.toString();
	}
}
