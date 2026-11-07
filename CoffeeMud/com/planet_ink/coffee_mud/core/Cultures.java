package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.Origins.Origin;
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
 * P11: cultures, a source of their own.  world/cultures.ini has an [ID]
 * section for each, as origins.ini does: NAME, DESCRIPTION, MASK (who may
 * adopt it), ABILITIES (ID and proficiency, languages too), TATTOOS, ADOPT
 * (COMMAND: the CULTURE ADOPT command may adopt it; otherwise an origin's
 * CULTURE=, a script or a mentor), LEAVE (NEVER: it cannot be left but by a
 * script's FORCE) and KEEP (abilities that stay on leaving, as a mother
 * tongue).  A character holds one culture at a time, kept as the tattoo
 * CULTURE_[ID].  Adopting gives only what they did not have, and records
 * what it gave in their story records, so leaving takes away exactly that
 * (KEEP aside): what they had before or learned otherwise is theirs.  At
 * creation (an origin's CULTURE=) and later the same rules apply: the mask,
 * one at a time, what is given, what leaving takes.
 */
public final class Cultures
{
	private Cultures()
	{
	}

	public static final String	FAMILY	= "cultures";
	private static final String	RECORD	= "cultures";

	/** @return world/cultures.ini, or null with no world folder */
	public static File file()
	{
		final File W = WorldFiles.worldDir();
		return (W == null) ? null : new File(W, "cultures.ini");
	}

	/** every culture, in the file's order */
	public static List<Origin> all()
	{
		final File F = file();
		if((F == null) || !F.isFile())
			return new ArrayList<Origin>(0);
		try
		{
			return Origins.parse(new String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8"));
		}
		catch(final Exception e)
		{
			Log.errOut("Cultures", "cultures.ini: " + e.getMessage());
			return new ArrayList<Origin>(0);
		}
	}

	/** a culture by its ID or name, or null */
	public static Origin find(final String id)
	{
		final String in = (id == null) ? "" : id.trim();
		for(final Origin C : all())
		{
			if(C.id.equalsIgnoreCase(in) || C.name().equalsIgnoreCase(in))
				return C;
		}
		return null;
	}

	/** the tattoo a culture is held as */
	public static String tattoo(final Origin C)
	{
		return "CULTURE_" + C.id.toUpperCase(Locale.ROOT);
	}

	/** a character's culture, or null */
	public static Origin current(final MOB M)
	{
		if(M == null)
			return null;
		for(final Origin C : all())
		{
			if(M.findTattoo(tattoo(C)) != null)
				return C;
		}
		return null;
	}

	private static List<String> words(final String list)
	{
		final List<String> out = new ArrayList<String>();
		for(final String w : CMParms.parseCommas(list, true))
		{
			if(w.trim().length() > 0)
				out.add(w.trim());
		}
		return out;
	}

	private static List<String> strings(final Object o)
	{
		final List<String> out = new ArrayList<String>();
		if(o instanceof Object[])
		{
			for(final Object x : (Object[])o)
				out.add(String.valueOf(x));
		}
		return out;
	}

	/** why someone may not adopt a culture this way ("command", "origin", "script"), or "" */
	public static String why(final MOB M, final Origin C, final String how)
	{
		if(C == null)
			return CMLib.lang().L("there is no such culture");
		if(M.findTattoo(tattoo(C)) != null)
			return CMLib.lang().L("@x1 is already yours", C.name());
		final String mask = C.get("MASK");
		if((mask.length() > 0) && !CMLib.masking().maskCheck(mask, M, true))
			return CMLib.lang().L("@x1 is only for @x2", C.name(), CMLib.masking().maskDesc(mask));
		if("command".equalsIgnoreCase(how) && !C.get("ADOPT").equalsIgnoreCase("COMMAND"))
			return CMLib.lang().L("@x1 is not taken up by a word: it is learned in the world", C.name());
		final Origin now = current(M);
		if((now != null) && now.get("LEAVE").equalsIgnoreCase("NEVER") && !"force".equalsIgnoreCase(how))
			return CMLib.lang().L("you are of @x1, which cannot be left", now.name());
		return "";
	}

	/**
	 * Adopts a culture: leaves the one held first (under its rules), then
	 * gives what the new one gives that they do not have, and records it.
	 * @param how "command", "origin", "script" or "force" (a script's: past a LEAVE=NEVER)
	 * @return what happened, in words
	 */
	public static String adopt(final MOB M, final String id, final String how) throws IOException
	{
		final Origin C = find(id);
		final String why = why(M, C, how);
		if(!why.isEmpty())
			throw new IOException(why);
		final StringBuilder said = new StringBuilder();
		if(current(M) != null)
			said.append(leave(M, how)).append(' ');
		final List<String> gave = new ArrayList<String>(), tattoos = new ArrayList<String>(), names = new ArrayList<String>();
		for(final String entry : words(C.get("ABILITIES")))
		{
			final List<String> w = CMParms.parse(entry);
			final Ability A = CMClass.getAbility(w.get(0));
			if(A == null)
			{
				Log.errOut("Cultures", "cultures.ini [" + C.id + "] ABILITIES: no ability " + w.get(0));
				continue;
			}
			if(M.fetchAbility(A.ID()) != null)
				continue; // theirs already, from elsewhere: not the culture's to give or take
			A.setProficiency((w.size() > 1) ? CMath.s_int(w.get(1)) : A.proficiency());
			M.addAbility(A);
			A.autoInvocation(M, false);
			gave.add(A.ID());
			names.add(A.name());
		}
		for(final String t : CMParms.parseAny(C.get("TATTOOS").replace(',', ' '), ' ', true))
		{
			if(M.findTattoo(t.toUpperCase(Locale.ROOT)) == null)
			{
				M.addTattoo(t.toUpperCase(Locale.ROOT));
				tattoos.add(t.toUpperCase(Locale.ROOT));
			}
		}
		M.addTattoo(tattoo(C));
		final MiniJSON.JSONObject data = StoryRecords.read(M);
		final MiniJSON.JSONObject rec = StoryRecords.object(data, RECORD);
		rec.put("current", C.id.toUpperCase(Locale.ROOT));
		rec.put("gave", gave.toArray());
		rec.put("tattoos", tattoos.toArray());
		history(rec, C.id, how, "adopted");
		StoryRecords.write(M, data);
		ModuleStats.count(FAMILY, C.id.toLowerCase(Locale.ROOT), "adopted by " + how.toLowerCase(Locale.ROOT));
		said.append(CMLib.lang().L("You are now of @x1.", C.name()));
		if(!names.isEmpty())
			said.append(' ').append(CMLib.lang().L("It gives you: @x1.", String.join(", ", names)));
		return said.toString();
	}

	private static void history(final MiniJSON.JSONObject rec, final String id, final String how, final String what)
	{
		final List<Object> h = new ArrayList<Object>((rec.get("history") instanceof Object[]) ? Arrays.asList((Object[])rec.get("history")) : new ArrayList<Object>());
		final MiniJSON.JSONObject e = new MiniJSON.JSONObject();
		e.put("culture", id.toUpperCase(Locale.ROOT));
		e.put("what", what);
		e.put("how", how.toLowerCase(Locale.ROOT));
		e.put("at", Long.valueOf(GameTime.now()));
		h.add(e);
		while(h.size() > 20)
			h.remove(0);
		rec.put("history", h.toArray());
	}

	/**
	 * Leaves one's culture: takes away the abilities it gave (but KEEP) and
	 * the tattoos it gave; refused for LEAVE=NEVER, but by "force".
	 * @return what happened, in words
	 */
	public static String leave(final MOB M, final String how) throws IOException
	{
		final Origin C = current(M);
		if(C == null)
			throw new IOException(CMLib.lang().L("you hold no culture"));
		if(C.get("LEAVE").equalsIgnoreCase("NEVER") && !"force".equalsIgnoreCase(how))
			throw new IOException(CMLib.lang().L("@x1 cannot be left", C.name()));
		final MiniJSON.JSONObject data = StoryRecords.read(M);
		final MiniJSON.JSONObject rec = StoryRecords.object(data, RECORD);
		final Set<String> keep = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		keep.addAll(words(C.get("KEEP")));
		final List<String> gone = new ArrayList<String>(), kept = new ArrayList<String>();
		for(final String id : strings(rec.get("gave")))
		{
			final Ability A = M.fetchAbility(id);
			if(A == null)
				continue;
			if(keep.contains(A.ID()))
			{
				kept.add(A.name());
				continue;
			}
			final Ability E = M.fetchEffect(A.ID());
			if(E != null)
			{
				E.unInvoke();
				M.delEffect(E);
			}
			M.delAbility(A);
			gone.add(A.name());
		}
		for(final String t : strings(rec.get("tattoos")))
			M.delTattoo(t);
		M.delTattoo(tattoo(C));
		rec.put("current", "");
		rec.put("gave", new Object[0]);
		rec.put("tattoos", new Object[0]);
		history(rec, C.id, how, "left");
		StoryRecords.write(M, data);
		ModuleStats.count(FAMILY, C.id.toLowerCase(Locale.ROOT), "left by " + how.toLowerCase(Locale.ROOT));
		final StringBuilder s = new StringBuilder(CMLib.lang().L("You leave @x1.", C.name()));
		if(!gone.isEmpty())
			s.append(' ').append(CMLib.lang().L("It takes away: @x1.", String.join(", ", gone)));
		if(!kept.isEmpty())
			s.append(' ').append(CMLib.lang().L("You keep: @x1.", String.join(", ", kept)));
		return s.toString();
	}

	/** what a culture gave someone, by ability names */
	public static List<String> gave(final MOB M)
	{
		final List<String> out = new ArrayList<String>();
		for(final String id : strings(StoryRecords.object(StoryRecords.read(M), RECORD).get("gave")))
		{
			final Ability A = M.fetchAbility(id);
			if(A != null)
				out.add(A.name());
		}
		return out;
	}

	/** CULTURE: one's culture and what it gave; CULTURE LIST: all, and whether one may adopt each */
	public static String show(final MOB M, final boolean all)
	{
		final StringBuilder s = new StringBuilder();
		final Origin C = current(M);
		if(!all)
		{
			if(C == null)
				return CMLib.lang().L("You hold no culture.  CULTURE LIST names the cultures of this world.");
			s.append(CMLib.lang().L("You are of @x1.", C.name()));
			if(C.get("DESCRIPTION").length() > 0)
				s.append(' ').append(C.get("DESCRIPTION"));
			final List<String> gave = gave(M);
			if(!gave.isEmpty())
				s.append('\n').append(CMLib.lang().L("From it: @x1.", String.join(", ", gave)));
			s.append('\n').append(C.get("LEAVE").equalsIgnoreCase("NEVER") ? CMLib.lang().L("It cannot be left.") : CMLib.lang().L("Leaving it (CULTURE LEAVE) takes away what it gave@x1.",
				C.get("KEEP").isEmpty() ? "" : CMLib.lang().L(", but @x1", C.get("KEEP"))));
			return s.toString();
		}
		for(final Origin O : all())
		{
			final String why = why(M, O, "command");
			s.append(s.length() > 0 ? "\n" : "").append(O.name()).append(O.get("DESCRIPTION").isEmpty() ? "" : ": " + O.get("DESCRIPTION"));
			s.append(" - ").append((O == C) || ((C != null) && C.id.equals(O.id)) ? CMLib.lang().L("yours") : why.isEmpty() ? CMLib.lang().L("CULTURE ADOPT @x1", O.id.toLowerCase(Locale.ROOT)) : why);
		}
		return (s.length() == 0) ? CMLib.lang().L("This world has no cultures.") : s.toString();
	}

	/** what WORLD CHECK reports: unknown abilities, and an origin's CULTURE= that names none */
	public static List<String> problems()
	{
		final List<String> out = new ArrayList<String>();
		for(final Origin C : all())
		{
			for(final String entry : words(C.get("ABILITIES")))
			{
				final String id = CMParms.parse(entry).get(0);
				if(CMClass.getAbility(id) == null)
					out.add("cultures.ini [" + C.id + "]: no ability " + id);
			}
		}
		for(final Origin O : Origins.all())
		{
			final String c = O.get("CULTURE");
			if((c.length() > 0) && (find(c) == null))
				out.add("origins.ini [" + O.id + "] CULTURE: no culture " + c + " in cultures.ini");
		}
		return out;
	}
}
