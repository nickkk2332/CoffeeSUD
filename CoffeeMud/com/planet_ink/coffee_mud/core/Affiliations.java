package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.Clan;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.collections.Pair;
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
 * P03: affiliation rules, world/affiliations.ini, one rule a line, its parts
 * between bars:
 * <ul>
 * <li>TAG|[organization]|[tag] (more tags after more bars): an organization's
 * tags; its name and its category are tags of it too;</li>
 * <li>CONFLICT|[tag]|[tag]: two tags that cannot be held together, either
 * way: joining an organization of one while in one of the other is refused,
 * however the joining comes about, so rejoining too;</li>
 * <li>KEEP|[organization]|[mask]: what a member must keep passing; checked
 * again whenever their class changes, and failing it they leave;</li>
 * <li>LEAVE|[organization]|TATTOOS=a,b ABILITIES=x,y: what leaving takes
 * away, however it comes about (the government's own grants follow
 * membership already).</li>
 * </ul>
 */
public final class Affiliations
{
	private Affiliations()
	{
	}

	public static final String FAMILY = "affiliations";

	/** the rules as the file has them */
	public static final class Rules
	{
		public final Map<String, Set<String>>	tags		= new TreeMap<String, Set<String>>(String.CASE_INSENSITIVE_ORDER);
		public final List<String[]>				conflicts	= new ArrayList<String[]>();
		public final Map<String, String>		keep		= new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		public final Map<String, String>		leave		= new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		public final List<String>				problems	= new ArrayList<String>();
	}

	public static Rules rules()
	{
		final Rules r = new Rules();
		final File f = new File(WorldFiles.worldDir(), "affiliations.ini");
		if(!f.isFile())
			return r;
		try
		{
			int n = 0;
			for(final String raw : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
			{
				n++;
				final String line = raw.trim();
				if(line.isEmpty() || line.startsWith("#"))
					continue;
				final String[] p = line.split("\\|");
				final String kind = p[0].trim().toUpperCase(Locale.ROOT);
				if(kind.equals("TAG") && (p.length >= 3))
				{
					final Set<String> t = r.tags.computeIfAbsent(p[1].trim(), k -> new TreeSet<String>(String.CASE_INSENSITIVE_ORDER));
					for(int i = 2; i < p.length; i++)
						t.add(p[i].trim());
				}
				else
				if(kind.equals("CONFLICT") && (p.length == 3))
					r.conflicts.add(new String[] { p[1].trim(), p[2].trim() });
				else
				if(kind.equals("KEEP") && (p.length == 3))
					r.keep.put(p[1].trim(), p[2].trim());
				else
				if(kind.equals("LEAVE") && (p.length == 3))
					r.leave.put(p[1].trim(), p[2].trim());
				else
					r.problems.add("affiliations.ini line " + n + ": not a rule (TAG|org|tag, CONFLICT|tag|tag, KEEP|org|mask or LEAVE|org|...): " + line);
			}
		}
		catch(final java.io.IOException e)
		{
			r.problems.add("affiliations.ini: " + e.getMessage());
		}
		return r;
	}

	/** an organization's tags: its own, its name and its category */
	public static Set<String> tagsOf(final Rules r, final Clan C)
	{
		final Set<String> out = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		out.add(C.getName());
		if((C.getCategory() != null) && (C.getCategory().length() > 0))
			out.add(C.getCategory());
		final Set<String> t = r.tags.get(C.getName());
		if(t != null)
			out.addAll(t);
		return out;
	}

	/** why someone may not join an organization while in the ones they are in, or "" */
	public static String refuses(final MOB M, final Clan joining)
	{
		final Rules r = rules();
		if(r.conflicts.isEmpty())
			return "";
		final Set<String> mine = tagsOf(r, joining);
		for(final Pair<Clan, Integer> p : M.clans())
		{
			final Clan C = p.first;
			if((C == null) || C.clanID().equals(joining.clanID()))
				continue;
			final Set<String> theirs = tagsOf(r, C);
			for(final String[] c : r.conflicts)
			{
				// each organization's tag in the order the organizations are named
				if(mine.contains(c[0]) && theirs.contains(c[1]))
					return CMLib.lang().L("@x1 and @x2 cannot be held together (@x3 and @x4)", joining.getName(), C.getName(), c[0], c[1]);
				if(mine.contains(c[1]) && theirs.contains(c[0]))
					return CMLib.lang().L("@x1 and @x2 cannot be held together (@x3 and @x4)", joining.getName(), C.getName(), c[1], c[0]);
			}
		}
		return "";
	}

	/** what leaving an organization takes away, by its LEAVE rule */
	public static void left(final MOB M, final Clan C)
	{
		final String rule = rules().leave.get(C.getName());
		if((rule == null) || (M == null))
			return;
		final List<String> gone = new ArrayList<String>();
		for(final String t : CMParms.parseCommas(CMParms.getParmStr(rule, "TATTOOS", ""), true))
		{
			if(M.findTattoo(t.trim()) != null)
			{
				M.delTattoo(t.trim());
				gone.add(t.trim());
			}
		}
		for(final String a : CMParms.parseCommas(CMParms.getParmStr(rule, "ABILITIES", ""), true))
		{
			final Ability A = M.fetchAbility(a.trim());
			if(A != null)
			{
				final Ability E = M.fetchEffect(A.ID());
				if(E != null)
				{
					E.unInvoke();
					M.delEffect(E);
				}
				M.delAbility(A);
				gone.add(A.name());
			}
		}
		if(!gone.isEmpty())
		{
			ModuleStats.count(FAMILY, C.getName(), "leavings that took something");
			M.tell(CMLib.lang().L("Leaving @x1 takes away: @x2.", C.getName(), String.join(", ", gone)));
		}
	}

	/** a member's class changed: each membership whose KEEP mask they no longer pass ends */
	public static List<String> recheck(final MOB M)
	{
		final List<String> out = new ArrayList<String>();
		if(M == null)
			return out;
		final Rules r = rules();
		if(r.keep.isEmpty())
			return out;
		for(final Pair<Clan, Integer> p : new ArrayList<Pair<Clan, Integer>>(toList(M.clans())))
		{
			final Clan C = p.first;
			final String mask = (C == null) ? null : r.keep.get(C.getName());
			if((mask == null) || CMLib.masking().maskCheck(mask, M, true))
				continue;
			out.add(C.getName());
			M.tell(CMLib.lang().L("You no longer meet what @x1 asks of its members (@x2), and leave it.", C.getName(), CMLib.masking().maskDesc(mask)));
			C.delMember(M);
			ModuleStats.count(FAMILY, C.getName(), "left on a class change");
		}
		return out;
	}

	private static List<Pair<Clan, Integer>> toList(final Iterable<Pair<Clan, Integer>> it)
	{
		final List<Pair<Clan, Integer>> l = new ArrayList<Pair<Clan, Integer>>();
		for(final Pair<Clan, Integer> p : it)
			l.add(p);
		return l;
	}
}
