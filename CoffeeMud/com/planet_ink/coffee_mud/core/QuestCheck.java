package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.util.*;

import com.planet_ink.coffee_mud.Common.interfaces.Quest;
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
 * QUESTCHECK (DT32): a quest's script read without starting it or making
 * anything, each problem with its line: commands, SET variables and GIVE,
 * TAKE, LOAD and IMPORT kinds the quest engine does not know; no NAME or
 * DURATION; a WAIT with no INTERVAL; LOAD= and IMPORT files that are neither
 * the quest's own (FILE) nor in the resources; and each embedded script,
 * checked by SCRIPTCHECK (DT11).
 */
public final class QuestCheck
{
	private QuestCheck()
	{
	}

	/** the commands a quest's script line may start with */
	public static final Set<String> COMMANDS = set("SET", "LOAD", "GIVE", "TAKE", "STEP", "RESET", "IMPORT", "OPTIONAL", "QUIET", "<SCRIPT>", "</SCRIPT>");
	/** what SET takes besides the quest's own stats */
	public static final Set<String> SET_VARS = set("ABILITY", "ABILITYGROUP", "ACTION", "ACTIONGROUP", "AGENT", "AGENTGROUP", "AREA", "AREAGROUP",
		"FACTION", "FACTIONGROUP", "ITEM", "ITEMGROUP", "ITEMTYPE", "LOCALE", "LOCALEGROUP", "LOCALEGROUPAROUND", "MOB", "MOBGROUP", "MOBTYPE",
		"MOTIVE", "MOTIVEGROUP", "PCMOB", "PCMOBGROUP", "PRESERVE", "ROOM", "ROOMGROUP", "ROOMGROUPAROUND", "TARGET", "TARGETGROUP", "TOOL",
		"TOOLGROUP", "WHENAT", "WHENATGROUP", "WHENHAPPENED", "WHENHAPPENEDGROUP", "WHEREAT", "WHEREATGROUP", "WHEREHAPPENED", "WHEREHAPPENEDGROUP");
	/** what LOAD, GIVE, TAKE and IMPORT take */
	public static final Set<String> LOAD_KINDS = set("MOB", "MOBGROUP", "ITEM", "ITEMGROUP", "ABILITY", "ABILITYGROUP");
	public static final Set<String> GIVE_KINDS = set("FOLLOWER", "ITEM", "ITEMS", "ABILITY", "BEHAVIOR", "AFFECT", "STAT", "SCRIPT");
	public static final Set<String> TAKE_KINDS = set("ABILITY", "BEHAVIOR", "AFFECT");
	public static final Set<String> IMPORT_KINDS = set("MOBS", "ITEMS", "ABILITIES");

	private static Set<String> set(final String... s)
	{
		return new HashSet<String>(Arrays.asList(s));
	}

	/**
	 * The quest stats SET may set (NAME, DURATION, WAIT, INTERVAL and the
	 * rest), from the quest engine itself.
	 * @return the names
	 */
	public static Set<String> questStats()
	{
		final Set<String> stats = new HashSet<String>();
		final Quest Q = (Quest)CMClass.getCommon("DefaultQuest");
		if(Q != null)
		{
			for(final String s : Q.getStatCodes())
				stats.add(s.toUpperCase());
		}
		return stats;
	}

	/**
	 * The files a quest's text holds after its script: each
	 * &lt;FILE&gt;&lt;NAME&gt;x&lt;/NAME&gt;&lt;DATA&gt;...&lt;/DATA&gt;&lt;/FILE&gt;, by name.
	 * @param text the quest's text
	 * @return the files, by name, upper case
	 */
	public static Map<String, String> ownFiles(final String text)
	{
		final Map<String, String> files = new LinkedHashMap<String, String>();
		final String u = text.toUpperCase();
		int x = u.indexOf("<FILE>");
		while(x >= 0)
		{
			final int n1 = u.indexOf("<NAME>", x);
			final int n2 = (n1 < 0) ? -1 : u.indexOf("</NAME>", n1);
			final int d1 = (n2 < 0) ? -1 : u.indexOf("<DATA>", n2);
			final int d2 = (d1 < 0) ? -1 : u.indexOf("</DATA>", d1);
			if(d2 < 0)
				break;
			files.put(text.substring(n1 + 6, n2).trim().toUpperCase(), text.substring(d1 + 6, d2));
			x = u.indexOf("<FILE>", d2);
		}
		return files;
	}

	private static boolean resourceExists(final String name)
	{
		if(name.indexOf('$') >= 0)
			return true; // a template's variable: filled in when the quest is made
		String n = name.trim();
		if(n.startsWith("/"))
			n = n.substring(1);
		if(!n.toLowerCase().startsWith("resources/"))
			n = "resources/" + n;
		if(new File(n).isFile() || new File(WorldFiles.worldDir(), n).isFile())
			return true;
		return new CMFile("/" + n, null, 0).exists();
	}

	/**
	 * Checks a quest's text.
	 * @param name what to call it in the problems (its file, or name)
	 * @param text the text
	 * @return the problems, each "name line n: what"
	 */
	public static List<String> checkText(final String name, final String text)
	{
		return checkText(name, text, 0);
	}

	private static List<String> checkText(final String name, final String text, final int depth)
	{
		final List<String> out = new ArrayList<String>();
		// a quest whose text is LOAD=file is that file's
		final String first = firstLine(text);
		if(first.toUpperCase().startsWith("LOAD=") && (depth < 3))
		{
			String f = first.substring(5).trim();
			if(f.startsWith("/"))
				f = f.substring(1);
			if(!f.toLowerCase().startsWith("resources/"))
				f = "resources/" + f;
			final CMFile F = new CMFile("/" + f, null, 0);
			if(!F.exists())
			{
				out.add(name + " line 1: LOAD=" + first.substring(5).trim() + " not found");
				return out;
			}
			return checkText(name + " -> " + first.substring(5).trim(), F.text().toString(), depth + 1);
		}
		final Map<String, String> files = ownFiles(text);
		final Set<String> stats = questStats();
		final String[] lines = text.replace("\r\n", "\n").replace("\n\r", "\n").replace('\r', '\n').split("\n");
		boolean hasName = false;
		boolean hasDuration = false;
		boolean hasWait = false;
		boolean hasInterval = false;
		boolean inScript = false;
		for(int i = 0; i < lines.length; i++)
		{
			final String line = lines[i].trim();
			final String at = name + " line " + (i + 1) + ": ";
			if(line.startsWith("<?xml") || line.toUpperCase().startsWith("<FILE>"))
				break; // the quest's own files start here
			if(inScript)
			{
				if(line.toUpperCase().startsWith("</SCRIPT>"))
					inScript = false;
				continue;
			}
			if((line.length() == 0) || line.startsWith("#"))
				continue;
			final List<String> p = CMParms.parse(line);
			if(p.isEmpty())
				continue;
			String cmd = p.get(0).toUpperCase();
			while((cmd.equals("OPTIONAL") || cmd.equals("QUIET")) && (p.size() > 1))
			{
				p.remove(0);
				cmd = p.get(0).toUpperCase();
				if(cmd.equals("ON") || cmd.equals("OFF"))
				{
					p.remove(0);
					if(p.isEmpty())
						break;
					cmd = p.get(0).toUpperCase();
				}
			}
			if(p.isEmpty() || cmd.equals("ON") || cmd.equals("OFF"))
				continue;
			if(cmd.equals("<SCRIPT>"))
			{
				inScript = true;
				continue;
			}
			if(!COMMANDS.contains(cmd))
			{
				out.add(at + cmd + " is no quest command");
				continue;
			}
			final String kind = (p.size() > 1) ? p.get(1).toUpperCase() : "";
			if(cmd.equals("SET"))
			{
				if(kind.length() == 0)
					out.add(at + "SET with nothing to set");
				else
				if((!SET_VARS.contains(kind)) && (!stats.contains(kind)))
					out.add(at + "SET " + kind + " unknown");
				if(kind.equals("NAME"))
					hasName = true;
				if(kind.equals("DURATION"))
					hasDuration = true;
				if(kind.equals("WAIT"))
					hasWait = true;
				if(kind.equals("INTERVAL"))
					hasInterval = true;
			}
			else
			if(cmd.equals("LOAD") && (!LOAD_KINDS.contains(kind)))
				out.add(at + "LOAD " + kind + " unknown");
			else
			if(cmd.equals("GIVE") && (!GIVE_KINDS.contains(kind)))
				out.add(at + "GIVE " + kind + " unknown");
			else
			if(cmd.equals("TAKE") && (!TAKE_KINDS.contains(kind)))
				out.add(at + "TAKE " + kind + " unknown");
			else
			if(cmd.equals("IMPORT"))
			{
				if(!IMPORT_KINDS.contains(kind))
					out.add(at + "IMPORT " + kind + " unknown");
				else
				if(p.size() > 2)
				{
					final String f = p.get(2);
					if((!files.containsKey(f.toUpperCase())) && (!resourceExists(f)))
						out.add(at + "IMPORT file " + f + " not found");
				}
			}
			// LOAD= files, and the scripts given
			for(final String w : p)
			{
				if(w.toUpperCase().startsWith("LOAD="))
				{
					final String f = w.substring(5);
					if(files.containsKey(f.toUpperCase()))
					{
						for(final String prob : ScriptCheck.checkText(f, files.get(f.toUpperCase()), null))
							out.add(name + " (" + f + "): " + prob);
					}
					else
					if(!resourceExists(f))
						out.add(at + "LOAD=" + f + " not found");
				}
			}
			if(cmd.equals("GIVE") && kind.equals("SCRIPT") && (p.size() > 2) && (line.toUpperCase().indexOf("LOAD=") < 0))
			{
				// an inline script, its lines separated by ; or ~
				final String script = CMParms.combine(p, 2);
				for(final String prob : ScriptCheck.checkText(name + " line " + (i + 1), script, null))
					out.add(prob);
			}
		}
		if(!hasName)
			out.add(name + ": no SET NAME");
		if(!hasDuration)
			out.add(name + ": no SET DURATION");
		if(hasWait && (!hasInterval))
			out.add(name + ": SET WAIT with no SET INTERVAL, so the quest is deleted after its first stop");
		return out;
	}

	private static String firstLine(final String text)
	{
		for(final String line : text.replace("\r\n", "\n").replace("\n\r", "\n").replace('\r', '\n').split("\n"))
		{
			final String s = line.trim();
			if((s.length() > 0) && (!s.startsWith("#")))
				return s;
		}
		return "";
	}

	/**
	 * Checks every quest file in the world's quests folder.
	 * @return the problems
	 */
	public static List<String> checkWorld()
	{
		final List<String> out = new ArrayList<String>();
		final File dir = new File(WorldFiles.worldDir(), "quests");
		final File[] fs = dir.listFiles();
		if(fs == null)
			return out;
		Arrays.sort(fs);
		for(final File F : fs)
		{
			if(F.isFile() && F.getName().toLowerCase().endsWith(".quest"))
				out.addAll(checkFile(F));
		}
		return out;
	}

	/**
	 * Checks one quest file.
	 * @param F the file
	 * @return the problems
	 */
	public static List<String> checkFile(final File F)
	{
		try
		{
			return checkText(F.getName(), new String(java.nio.file.Files.readAllBytes(F.toPath()), java.nio.charset.StandardCharsets.UTF_8));
		}
		catch(final java.io.IOException e)
		{
			return new ArrayList<String>(Arrays.asList(F.getName() + ": " + e.getMessage()));
		}
	}
}
