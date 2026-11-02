package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import java.io.File;
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
 * C9: ENCOUNTER LIST | SHOW table | ADD table line | DEL table n | ROLL table |
 * CHECK (table) | RESET table: the encounter tables of world/encounters.
 */
public class EncounterCmd extends StdCommand
{
	public EncounterCmd()
	{
	}

	private final String[] access = I(new String[] { "ENCOUNTER" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/** the lines of a table's file that are entries, by their number in the file */
	protected static List<Integer> entryLines(final List<String> lines)
	{
		final List<Integer> list = new ArrayList<Integer>();
		for(int i = 0; i < lines.size(); i++)
		{
			final String l = lines.get(i).trim();
			if((l.length() > 0) && (!l.startsWith("#")))
				list.add(Integer.valueOf(i));
		}
		return list;
	}

	protected static List<String> readLines(final File F) throws java.io.IOException
	{
		if(!F.isFile())
			return new ArrayList<String>();
		return new ArrayList<String>(java.nio.file.Files.readAllLines(F.toPath(), java.nio.charset.StandardCharsets.UTF_8));
	}

	protected static void writeLines(final File F, final List<String> lines) throws java.io.IOException
	{
		F.getParentFile().mkdirs();
		java.nio.file.Files.write(F.toPath(), lines, java.nio.charset.StandardCharsets.UTF_8);
		Encounters.forget();
	}

	/**
	 * The words of an entry put back together: a word with spaces quoted, as
	 * KEY="value" when it is a KEY=value.
	 * @param words the words
	 * @param from the first
	 * @return the entry's line
	 */
	protected static String entryText(final List<String> words, final int from)
	{
		final StringBuilder str = new StringBuilder("");
		for(int i = from; i < words.size(); i++)
		{
			final String w = words.get(i);
			if(str.length() > 0)
				str.append(' ');
			if((w.indexOf(' ') < 0) || (w.indexOf('"') >= 0))
				str.append(w);
			else
			{
				final int eq = w.indexOf('=');
				if((eq > 0) && (w.substring(0, eq).indexOf(' ') < 0))
					str.append(w, 0, eq + 1).append('"').append(w.substring(eq + 1)).append('"');
				else
					str.append('"').append(w).append('"');
			}
		}
		return str.toString().trim();
	}

	/**
	 * Does one ENCOUNTER command.
	 * @param mob who asks (ROLL rolls for them, where they are)
	 * @param words the words after ENCOUNTER
	 * @return what to tell them
	 */
	public static String run(final MOB mob, final List<String> words)
	{
		final String sub = (words.size() > 0) ? words.get(0).toUpperCase() : "LIST";
		final String name = (words.size() > 1) ? words.get(1).toLowerCase() : "";
		try
		{
			if(sub.equals("LIST"))
			{
				final StringBuilder str = new StringBuilder("Encounter tables (world/encounters):");
				final List<String> names = Encounters.names();
				if(names.isEmpty())
					str.append(" none.");
				for(final String n : names)
				{
					final Encounters.Table T = Encounters.table(n);
					str.append("\n\r  ").append(n).append(": ").append((T == null) ? 0 : T.entries.size()).append(" entries");
					if((T != null) && (T.problems.size() > 0))
						str.append(", ").append(T.problems.size()).append(" problems");
				}
				return str.toString();
			}
			if(name.length() == 0)
				return "ENCOUNTER LIST | SHOW [TABLE] | ADD [TABLE] [ENTRY] | DEL [TABLE] [N] | ROLL [TABLE] | CHECK ([TABLE]) | RESET [TABLE]";
			final File F = Encounters.fileOf(name);
			if(sub.equals("ADD"))
			{
				final String line = entryText(words, 2);
				final String err = Encounters.entryProblem(line);
				if(err != null)
					return "Not added: " + err + ".";
				final List<String> lines = readLines(F);
				lines.add(line);
				writeLines(F, lines);
				return "Added to " + name + " as entry " + entryLines(lines).size() + ": " + line;
			}
			final Encounters.Table T = Encounters.table(name);
			if(sub.equals("CHECK") && name.equalsIgnoreCase("ALL"))
			{
				final StringBuilder str = new StringBuilder("");
				for(final String n : Encounters.names())
				{
					final Encounters.Table T2 = Encounters.table(n);
					str.append(n).append(": ").append((T2 == null) ? "unreadable" : (T2.problems.size() + " problems")).append("\n\r");
					if(T2 != null)
					{
						for(final String p : T2.problems)
							str.append("  ").append(p).append("\n\r");
					}
				}
				return (str.length() == 0) ? "There are no encounter tables." : str.toString();
			}
			if(T == null)
				return "There is no encounter table '" + name + "' (world/encounters/" + name + ".enc).";
			if(sub.equals("SHOW"))
			{
				final StringBuilder str = new StringBuilder("Encounter table " + name + ":");
				for(final Encounters.Entry E : T.entries)
				{
					str.append("\n\r  ").append(E.index + 1).append(". ").append(E.line);
					if(E.once && Encounters.onceDone(T, E))
						str.append("  (once: done)");
					final int c = Encounters.coolingFor(T, E);
					if(c > 0)
						str.append("  (cooling: ").append(c).append(" more)");
				}
				for(final String p : T.problems)
					str.append("\n\r  problem at line ").append(p);
				return str.toString();
			}
			if(sub.equals("DEL"))
			{
				final int n = (words.size() > 2) ? CMath.s_int(words.get(2)) : 0;
				final List<String> lines = readLines(F);
				final List<Integer> entries = entryLines(lines);
				if((n < 1) || (n > entries.size()))
					return "Entry 1 to " + entries.size() + ", please.";
				final String gone = lines.remove(entries.get(n - 1).intValue());
				writeLines(F, lines);
				return "Removed from " + name + ": " + gone.trim();
			}
			if(sub.equals("CHECK"))
			{
				final StringBuilder str = new StringBuilder(name + ": " + T.problems.size() + " problems.");
				for(final String p : T.problems)
					str.append("\n\r  line ").append(p);
				return str.toString();
			}
			if(sub.equals("RESET"))
			{
				Encounters.reset(T);
				return "The ONCE and COOLDOWN entries of " + name + " may come again.";
			}
			if(sub.equals("ROLL"))
			{
				if((mob == null) || (mob.location() == null))
					return "Roll it where?";
				final Encounters.Result res = Encounters.roll(T, mob.location(), mob, null, 0);
				if(res.entry == null)
					return "Nothing came: no entry of " + name + " fits you, here and now.";
				return "Rolled " + name + ": " + res.entry.line + ((res.spawned.size() > 0) ? (" (" + res.spawned.size() + " brought)") : "");
			}
			return "ENCOUNTER LIST | SHOW [TABLE] | ADD [TABLE] [ENTRY] | DEL [TABLE] [N] | ROLL [TABLE] | CHECK ([TABLE]) | RESET [TABLE]";
		}
		catch(final java.io.IOException e)
		{
			return "Could not write " + name + ": " + e.getMessage();
		}
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		mob.tell(run(mob, new ArrayList<String>(commands.subList(1, commands.size()))));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDMOBS);
	}
}
