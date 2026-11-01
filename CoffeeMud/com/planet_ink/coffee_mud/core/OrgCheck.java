package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.ClanGovernment;
import com.planet_ink.coffee_mud.Common.interfaces.Faction;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
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
 * ORGCHECK (DT37): faction files read again from their raw lines, so that
 * what the property map hides shows: keys given twice (the later wins), and
 * bands that overlap or leave gaps; CHANGE triggers that match nothing;
 * REACTION and AFFBEHAV IDs that are no ability, behaviour, command or
 * social; REACTION ranges that are no band; RELATION factions that do not
 * exist.  Also the world files' mobs whose CLAN is no clan, and governments
 * whose ENTRYSCRIPT does not check.
 */
public final class OrgCheck
{
	private OrgCheck()
	{
	}

	private static boolean knownEvent(final String id)
	{
		final String u = id.trim().toUpperCase();
		for(final Faction.FactionChangeEvent.MiscTrigger T : Faction.FactionChangeEvent.MiscTrigger.values())
		{
			if(T.name().equals(u))
				return true;
		}
		for(final String s : Ability.ACODE.DESCS)
		{
			if(s.equalsIgnoreCase(u))
				return true;
		}
		for(final String s : Ability.DOMAIN.DESCS)
		{
			if(s.equalsIgnoreCase(u))
				return true;
		}
		for(final String s : Ability.FLAG_DESCS)
		{
			if(s.equalsIgnoreCase(u))
				return true;
		}
		if(CMClass.getAbility(id.trim()) != null)
			return true;
		if(CMLib.socials().fetchSocial(id.trim(), true) != null)
			return true;
		if(u.endsWith(" *") && (CMLib.socials().fetchSocial(u.substring(0, u.length() - 2).trim(), true) != null))
			return true;
		// a message type, MSG_ and its name, as the faction's own map has them
		for(final String s : CMMsg.TYPE_DESCS)
		{
			if(("MSG_" + s).equalsIgnoreCase(u))
				return true;
		}
		return false;
	}

	private static boolean knownObjectID(final String id)
	{
		final String s = id.trim();
		return (CMClass.getAbility(s) != null) || (CMClass.getBehavior(s) != null) || (CMClass.getCommand(s) != null)
			|| (CMLib.socials().fetchSocial(s, true) != null);
	}

	private static final Pattern KEY = Pattern.compile("^\\s*([A-Za-z_]+[0-9]*)\\s*=(.*)$");

	/**
	 * Checks one faction file's text.
	 * @param name what to call it
	 * @param text the file's text
	 * @return the problems, each "name line n: what"
	 */
	public static List<String> checkFaction(final String name, final String text)
	{
		final List<String> out = new ArrayList<String>();
		final Map<String, Integer> seen = new HashMap<String, Integer>();
		final List<int[]> bands = new ArrayList<int[]>();
		final List<String> bandNames = new ArrayList<String>();
		final Set<String> codes = new HashSet<String>();
		final List<String[]> reactions = new ArrayList<String[]>();
		final String[] lines = text.replace("\r\n", "\n").replace("\n\r", "\n").replace('\r', '\n').split("\n");
		for(int i = 0; i < lines.length; i++)
		{
			final String line = lines[i];
			if(line.trim().startsWith("#") || (line.trim().length() == 0))
				continue;
			final Matcher m = KEY.matcher(line);
			if(!m.matches())
				continue;
			final String key = m.group(1).toUpperCase();
			final String val = m.group(2).trim();
			final String at = name + " line " + (i + 1) + ": ";
			if(seen.containsKey(key))
				out.add(at + key + " is given again (line " + seen.get(key) + " too): only this one counts");
			seen.put(key, Integer.valueOf(i + 1));
			if(key.startsWith("RANGE"))
			{
				final List<String> p = CMParms.parseSemicolons(val, false);
				if((p.size() < 4) || (!CMath.isInteger(p.get(0).trim())) || (!CMath.isInteger(p.get(1).trim())))
				{
					out.add(at + key + " is not low;high;name;code");
					continue;
				}
				bands.add(new int[] { CMath.s_int(p.get(0).trim()), CMath.s_int(p.get(1).trim()), i + 1 });
				bandNames.add(key + " (" + p.get(2).trim() + ")");
				codes.add(p.get(3).trim().toUpperCase());
			}
			else
			if(key.startsWith("CHANGE"))
			{
				final List<String> p = CMParms.parseSemicolons(val, false);
				// a trigger's parameters, (WAIT=5) and the like, come off first, as the faction takes them
				String trigger = p.isEmpty() ? "" : p.get(0).trim();
				final int x = trigger.indexOf('(');
				if((x > 0) && trigger.endsWith(")"))
					trigger = trigger.substring(0, x).trim();
				if(!knownEvent(trigger))
					out.add(at + key + ": the trigger '" + trigger + "' matches nothing");
			}
			else
			if(key.startsWith("AFFBEHAV"))
			{
				final List<String> p = CMParms.parseSafeSemicolonList(val, false);
				if(p.size() != 3)
					out.add(at + key + " is not ID;parameters;mask, so it is skipped");
				else
				if(!knownObjectID(p.get(0)))
					out.add(at + key + ": '" + p.get(0).trim() + "' is no ability or behaviour");
			}
			else
			if(key.startsWith("REACTION"))
			{
				final List<String> p = CMParms.parseSemicolons(val, false);
				if(p.size() < 3)
					out.add(at + key + " is not range;mask;ID;parameters");
				else
				{
					if(!knownObjectID(p.get(2)))
						out.add(at + key + ": unknown ability, behaviour, command or social '" + p.get(2).trim() + "'");
					reactions.add(new String[] { at, key, p.get(0).trim().toUpperCase() });
				}
			}
			else
			if(key.startsWith("RELATION"))
			{
				final List<String> p = CMParms.parse(val);
				if((p.size() >= 1) && (CMLib.factions().getFaction(p.get(0)) == null))
					out.add(at + key + ": there is no faction '" + p.get(0) + "'");
			}
		}
		for(final String[] r : reactions)
		{
			if((r[2].length() > 0) && (!codes.contains(r[2])) && (!r[2].equals("*")))
				out.add(r[0] + r[1] + ": the range '" + r[2] + "' is no band's code");
		}
		// the bands, in order: overlaps and gaps
		final Integer[] order = new Integer[bands.size()];
		for(int i = 0; i < order.length; i++)
			order[i] = Integer.valueOf(i);
		Arrays.sort(order, new Comparator<Integer>()
		{
			@Override
			public int compare(final Integer a, final Integer b)
			{
				return Integer.compare(bands.get(a.intValue())[0], bands.get(b.intValue())[0]);
			}
		});
		for(int i = 1; i < order.length; i++)
		{
			final int[] prev = bands.get(order[i - 1].intValue());
			final int[] cur = bands.get(order[i].intValue());
			final String pn = bandNames.get(order[i - 1].intValue());
			final String cn = bandNames.get(order[i].intValue());
			if(cur[0] <= prev[1])
				out.add(name + " line " + cur[2] + ": " + cn + " and " + pn + " both hold " + cur[0] + " to " + Math.min(cur[1], prev[1]));
			else
			if(cur[0] > prev[1] + 1)
				out.add(name + " line " + cur[2] + ": nothing holds " + (prev[1] + 1) + " to " + (cur[0] - 1) + ", between " + pn + " and " + cn);
		}
		return out;
	}

	/**
	 * The faction files: the game's and the world's.
	 * @return the files
	 */
	public static List<File> factionFiles()
	{
		final List<File> files = new ArrayList<File>();
		for(final File dir : new File[] { new File("resources/factions"), new File(new File(WorldFiles.worldDir(), "resources"), "factions") })
		{
			final File[] fs = dir.listFiles();
			if(fs == null)
				continue;
			Arrays.sort(fs);
			for(final File F : fs)
			{
				if(F.isFile() && F.getName().toLowerCase().endsWith(".ini"))
					files.add(F);
			}
		}
		return files;
	}

	/**
	 * The world files' mobs whose CLAN is no clan.
	 * @return the problems
	 */
	public static List<String> checkWorldClans()
	{
		final List<String> out = new ArrayList<String>();
		final Pattern P = Pattern.compile("<CLAN ROLE=[^>]*>([^<]*)</CLAN>");
		final File[] fs = new File(WorldFiles.worldDir(), "areas").listFiles();
		if(fs == null)
			return out;
		Arrays.sort(fs);
		for(final File F : fs)
		{
			if(!F.getName().endsWith(".cmare"))
				continue;
			final Map<String, int[]> missing = new TreeMap<String, int[]>();
			try
			{
				final Matcher m = P.matcher(new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8));
				while(m.find())
				{
					final String clan = CMLib.xml().restoreAngleBrackets(m.group(1)).trim();
					if((clan.length() > 0) && (CMLib.clans().getClan(clan) == null))
					{
						if(!missing.containsKey(clan))
							missing.put(clan, new int[1]);
						missing.get(clan)[0]++;
					}
				}
			}
			catch(final java.io.IOException e)
			{
				out.add(F.getName() + ": " + e.getMessage());
			}
			for(final Map.Entry<String, int[]> e : missing.entrySet())
				out.add(e.getValue()[0] + " mobs in " + F.getName() + " name clan " + e.getKey() + ", which does not exist");
		}
		return out;
	}

	/**
	 * The governments whose ENTRYSCRIPT does not check (SCRIPTCHECK).
	 * @return the problems
	 */
	public static List<String> checkGovernments()
	{
		final List<String> out = new ArrayList<String>();
		final ClanGovernment[] govs = CMLib.clans().getStockGovernments();
		if(govs == null)
			return out;
		for(final ClanGovernment G : govs)
		{
			final String script = G.getEntryScript();
			if((script == null) || (script.trim().length() == 0))
				continue;
			for(final String p : ScriptCheck.checkText(G.getName() + " ENTRYSCRIPT", script, null))
				out.add(p);
		}
		return out;
	}

	/**
	 * Everything: every faction file, the world's clans, the governments.
	 * @return the problems
	 */
	public static List<String> checkAll()
	{
		final List<String> out = new ArrayList<String>();
		for(final File F : factionFiles())
		{
			try
			{
				out.addAll(checkFaction(F.getName(), new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8)));
			}
			catch(final java.io.IOException e)
			{
				out.add(F.getName() + ": " + e.getMessage());
			}
		}
		out.addAll(checkWorldClans());
		out.addAll(checkGovernments());
		return out;
	}
}
