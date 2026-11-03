package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.CharClasses.interfaces.CharClass;
import com.planet_ink.coffee_mud.Libraries.interfaces.AbilityMapper;
import com.planet_ink.coffee_mud.Libraries.interfaces.AbilityMapper.AbilityMapping;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.collections.DVector;
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
 * P24: SKILLTREE: a class's skills by their REQUIRES chains, each learned
 * (with its proficiency), learnable now (with its cost), locked (the level,
 * the skill still needed, or the mask) or hidden; and hints from
 * skills/skillhints.ini (the world's own copy first):
 * [ABILITY ID] or [CLASS ID].[ABILITY ID] = HINT="..." (SHOW=HINT).
 */
public class SkillTree
{
	private SkillTree()
	{
	}

	private static Map<String, String> hints = new HashMap<String, String>();
	private static int hintsHash = 0;

	private static Map<String, String> hints()
	{
		final CMFile F = new CMFile(Resources.buildResourcePath("skills") + "skillhints.ini", null, CMFile.FLAG_FORCEALLOW);
		final String text = (F.exists() && F.canRead()) ? F.text().toString() : "";
		if(text.hashCode() != hintsHash)
		{
			final Map<String, String> m = new HashMap<String, String>();
			for(final String line : Resources.getFileLineVector(new StringBuffer(text)))
			{
				final String l = line.trim();
				final int x = l.indexOf('=');
				if(l.startsWith("#") || (x <= 0))
					continue;
				m.put(l.substring(0, x).trim().toUpperCase(), l.substring(x + 1).trim());
			}
			hints = m;
			hintsHash = text.hashCode();
		}
		return hints;
	}

	private static String hintLine(final String classID, final String abilityID)
	{
		final Map<String, String> h = hints();
		final String c = h.get((classID + "." + abilityID).toUpperCase());
		return (c != null) ? c : h.get(abilityID.toUpperCase());
	}

	/**
	 * @param classID a class, or null
	 * @param abilityID a skill
	 * @return its HINT, or ""
	 */
	public static String hint(final String classID, final String abilityID)
	{
		final String l = hintLine((classID == null) ? "" : classID, abilityID);
		if(l == null)
			return "";
		final String h = CMParms.getParmStr(l, "HINT", "");
		return (h.length() > 0) ? h : ((l.toUpperCase().indexOf("HINT=") < 0) ? l : "");
	}

	/**
	 * @param classID a class
	 * @param abilityID a skill
	 * @return true for SHOW=HINT: a masked skill shown by name, with its hint
	 */
	public static boolean showHint(final String classID, final String abilityID)
	{
		final String l = hintLine((classID == null) ? "" : classID, abilityID);
		return (l != null) && CMParms.getParmStr(l, "SHOW", "").equalsIgnoreCase("HINT");
	}

	/**
	 * A skill's line: its name and state, or null when it is hidden.
	 * @param mob who looks
	 * @param C the class
	 * @param m its mapping
	 * @return the line
	 */
	public static String line(final MOB mob, final CharClass C, final AbilityMapping m)
	{
		final Ability A = CMClass.getAbility(m.abilityID());
		if(A == null)
			return null;
		final AbilityMapper AM = CMLib.ableMapper();
		final Ability mine = mob.fetchAbility(A.ID());
		if(mine != null)
			return CMLib.lang().L("^H@x1^N (learned, @x2%)", A.name(), "" + mine.proficiency());
		final String hint = hint(C.ID(), A.ID());
		final String mask = AM.getApplicableMask(mob, A);
		final boolean maskOK = (mask == null) || (mask.trim().length() == 0) || CMLib.masking().maskCheck(mask, mob, true);
		final AbilityMapper.SecretFlag secret = m.secretFlag();
		if((secret == AbilityMapper.SecretFlag.SECRET) || ((secret == AbilityMapper.SecretFlag.MASKED) && (!maskOK)))
		{
			if(showHint(C.ID(), A.ID()))
				return CMLib.lang().L("@x1 (locked: @x2)", A.name(), (hint.length() > 0) ? hint : CMLib.lang().L("not yet"));
			if(hint.length() > 0)
				return CMLib.lang().L("??? - @x1", hint);
			return null;
		}
		final int lvl = m.qualLevel();
		final int myLevel = Math.max(mob.charStats().getClassLevel(C), 0);
		if(lvl > myLevel)
			return CMLib.lang().L("@x1 (locked: level @x2)@x3", A.name(), "" + lvl, (hint.length() > 0) ? (" - " + hint) : "");
		final DVector unmet = AM.getUnmetPreRequisites(mob, A);
		if((unmet != null) && (unmet.size() > 0))
			return CMLib.lang().L("@x1 (locked: needs @x2)@x3", A.name(), AM.formatPreRequisites(unmet), (hint.length() > 0) ? (" - " + hint) : "");
		if(!maskOK)
			return CMLib.lang().L("@x1 (locked: @x2)", A.name(), (hint.length() > 0) ? hint : CMLib.lang().L("you do not qualify"));
		return CMLib.lang().L("^w@x1^N (learnable: @x2)@x3", A.name(), A.requirements(mob), (hint.length() > 0) ? (" - " + hint) : "");
	}

	private static String parentOf(final AbilityMapping m, final Map<String, AbilityMapping> all)
	{
		final DVector pre = m.skillPreReqs();
		if(pre == null)
			return null;
		for(int i = 0; i < pre.size(); i++)
		{
			final Object o = pre.elementAt(i, 1);
			final String id = (o == null) ? "" : o.toString().replace("*", "").trim();
			for(final String k : all.keySet())
			{
				if(k.equalsIgnoreCase(id) && (!k.equalsIgnoreCase(m.abilityID())))
					return k;
			}
		}
		return null;
	}

	/**
	 * SKILLTREE [class]: the class's skills by their REQUIRES chains.
	 * @param mob who looks
	 * @param C the class
	 * @return the text
	 */
	public static String tree(final MOB mob, final CharClass C)
	{
		final Map<String, AbilityMapping> all = new LinkedHashMap<String, AbilityMapping>();
		final List<AbilityMapping> L = new ArrayList<AbilityMapping>();
		for(final Enumeration<AbilityMapping> e = CMLib.ableMapper().getClassAbles(C.ID(), false); e.hasMoreElements();)
			L.add(e.nextElement());
		L.sort((a, b) -> Integer.compare(a.qualLevel(), b.qualLevel()));
		for(final AbilityMapping m : L)
		{
			if(CMClass.getAbility(m.abilityID()) != null)
				all.put(m.abilityID(), m);
		}
		final Map<String, List<AbilityMapping>> kids = new HashMap<String, List<AbilityMapping>>();
		final List<AbilityMapping> roots = new ArrayList<AbilityMapping>();
		for(final AbilityMapping m : all.values())
		{
			final String p = parentOf(m, all);
			if(p == null)
				roots.add(m);
			else
			{
				if(!kids.containsKey(p))
					kids.put(p, new ArrayList<AbilityMapping>());
				kids.get(p).add(m);
			}
		}
		final StringBuilder s = new StringBuilder(CMLib.lang().L("^HThe skills of @x1:^N\n\r", C.name()));
		int shown = 0;
		for(final AbilityMapping m : roots)
			shown += branch(mob, C, m, kids, s, 0, 0);
		if(shown == 0)
			s.append(CMLib.lang().L("  (none you can see)\n\r"));
		return s.toString();
	}

	private static int branch(final MOB mob, final CharClass C, final AbilityMapping m, final Map<String, List<AbilityMapping>> kids,
							  final StringBuilder s, final int depth, final int guard)
	{
		if(guard > 20)
			return 0;
		final String l = line(mob, C, m);
		int n = 0;
		if(l != null)
		{
			s.append("  ");
			for(int i = 0; i < depth; i++)
				s.append("  ");
			s.append((depth > 0) ? "> " : "").append(l).append("\n\r");
			n++;
		}
		final List<AbilityMapping> K = kids.get(m.abilityID());
		if(K != null)
		{
			for(final AbilityMapping k : K)
				n += branch(mob, C, k, kids, s, (l != null) ? depth + 1 : depth, guard + 1);
		}
		return n;
	}

	/**
	 * SKILLTREE [skill]: what leads to it, and its state.
	 * @param mob who looks
	 * @param A the skill
	 * @return the text
	 */
	public static String chain(final MOB mob, final Ability A)
	{
		final CharClass C = mob.charStats().getCurrentClass();
		final Map<String, AbilityMapping> all = new LinkedHashMap<String, AbilityMapping>();
		for(final Enumeration<AbilityMapping> e = CMLib.ableMapper().getClassAbles(C.ID(), true); e.hasMoreElements();)
		{
			final AbilityMapping m = e.nextElement();
			all.put(m.abilityID(), m);
		}
		AbilityMapping m = all.get(A.ID());
		if(m == null)
			return CMLib.lang().L("@x1 is not a skill of @x2.", A.name(), C.name());
		final List<String> lines = new ArrayList<String>();
		for(int guard = 0; (m != null) && (guard < 20); guard++)
		{
			final String l = line(mob, C, m);
			lines.add(0, (l == null) ? "???" : l);
			final String p = parentOf(m, all);
			m = (p == null) ? null : all.get(p);
		}
		final StringBuilder s = new StringBuilder("");
		for(int i = 0; i < lines.size(); i++)
		{
			s.append("  ");
			for(int d = 0; d < i; d++)
				s.append("  ");
			s.append((i > 0) ? "> " : "").append(lines.get(i)).append("\n\r");
		}
		return s.toString();
	}
}
