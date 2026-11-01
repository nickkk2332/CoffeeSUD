package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.AchievementLibrary;
import com.planet_ink.coffee_mud.Libraries.interfaces.AbilityMapper.AbilityMapping;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * DT36, for builders: PROGRESSION class [race] [to level] lists the level
 * curve: for each level the XP it takes, the hit points, mana and moves, the
 * practices and trains, and the skills gained (given, or to learn).
 * PROGRESSION XP [area] adds up the experience the content gives, area by
 * area: achievements, literal MPEXP amounts in scripts, and Prop_EnterAdjuster
 * experience, with the level a character reaches doing it all, in order.
 */
public class ProgressionCmd extends StdCommand
{
	public ProgressionCmd()
	{
	}

	private final String[] access = I(new String[] { "PROGRESSION" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * The level curve of a class and race.
	 * @param C the class
	 * @param Rc the race
	 * @param to the last level
	 * @return the table
	 */
	public static String curve(final CharClass C, final Race Rc, final int to)
	{
		final StringBuilder str = new StringBuilder(C.name() + " (" + C.ID() + "), " + Rc.name() + ", to level " + to + ":");
		str.append("\n\r  ").append(CMStrings.padRight("Lvl", 4)).append(CMStrings.padRight("XP", 10)).append(CMStrings.padRight("HP", 6))
			.append(CMStrings.padRight("Mana", 6)).append(CMStrings.padRight("Move", 6)).append(CMStrings.padRight("Prac", 5))
			.append(CMStrings.padRight("Trn", 4)).append("Skills (* given)");
		for(int level = 1; level <= to; level++)
		{
			final MOB M = CMLib.leveler().fillOutMOB(C, Rc, level);
			if(M == null)
				break;
			final List<String> skills = new ArrayList<String>();
			for(final String id : CMLib.ableMapper().getLevelListings(C.ID(), true, level))
			{
				final Ability A = CMClass.getAbility(id);
				if(A == null)
					continue;
				skills.add((CMLib.ableMapper().getDefaultGain(C.ID(), true, id) ? "*" : "") + A.name());
			}
			str.append("\n\r  ").append(CMStrings.padRight("" + level, 4))
				.append(CMStrings.padRight("" + CMLib.leveler().getLevelExperience(M, level - 1), 10))
				.append(CMStrings.padRight("" + M.baseState().getHitPoints(), 6))
				.append(CMStrings.padRight("" + M.baseState().getMana(), 6))
				.append(CMStrings.padRight("" + M.baseState().getMovement(), 6))
				.append(CMStrings.padRight("" + M.getPractices(), 5))
				.append(CMStrings.padRight("" + M.getTrains(), 4))
				.append(String.join(", ", skills));
			M.destroy();
		}
		return str.toString();
	}

	private static final Pattern MPEXP = Pattern.compile("(?im)^\\s*mpexp\\s+\\S+\\s+(\\d+)");
	private static final Pattern EXPE  = Pattern.compile("(?i)\\bexpe?\\s*\\+\\s*(\\d+)");

	private static int scriptXP(final PhysicalAgent P)
	{
		int xp = 0;
		for(final Enumeration<ScriptingEngine> e = P.scripts(); e.hasMoreElements();)
			xp += textXP(e.nextElement().getScript());
		for(final Enumeration<Behavior> b = P.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if(B instanceof ScriptingEngine)
				xp += textXP(((ScriptingEngine)B).getScript());
		}
		final Ability A = P.fetchEffect("Prop_EnterAdjuster");
		if(A != null)
		{
			final Matcher m = EXPE.matcher(A.text());
			while(m.find())
				xp += CMath.s_int(m.group(1));
		}
		return xp;
	}

	private static int textXP(final String script)
	{
		if(script == null)
			return 0;
		String text = script;
		final int x = text.toUpperCase().indexOf("LOAD=");
		if(x >= 0)
		{
			String f = text.substring(x + 5).trim().split("[\\s;~]")[0];
			if(f.startsWith("/"))
				f = f.substring(1);
			final CMFile F = new CMFile(f.toLowerCase().startsWith("resources/") ? ("/" + f) : ("/resources/" + f), null, 0);
			if(F.exists())
				text = F.text().toString();
		}
		int xp = 0;
		final Matcher m = MPEXP.matcher(text.replace(';', '\n').replace('~', '\n'));
		while(m.find())
			xp += CMath.s_int(m.group(1));
		return xp;
	}

	/**
	 * The experience an area's content gives: its scripts' MPEXP amounts and
	 * Prop_EnterAdjuster experience, on the area, its rooms and what is in them.
	 * @param A the area
	 * @return the experience
	 */
	public static int areaXP(final Area A)
	{
		int xp = scriptXP(A);
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			xp += scriptXP(R);
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && M.isMonster())
					xp += scriptXP(M);
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
				xp += scriptXP(i.nextElement());
		}
		return xp;
	}

	/**
	 * The experience the achievements give.
	 * @return the experience
	 */
	public static int achievementXP()
	{
		int xp = 0;
		for(final AccountStats.Agent agent : AccountStats.Agent.values())
		{
			for(final Enumeration<AchievementLibrary.Achievement> a = CMLib.achievements().achievements(agent); a.hasMoreElements();)
			{
				for(final AchievementLibrary.Award W : a.nextElement().getRewards())
				{
					if((W.getType() == AchievementLibrary.AwardType.XP) && (W instanceof AchievementLibrary.AmountAward))
						xp += ((AchievementLibrary.AmountAward)W).getAmount();
				}
			}
		}
		return xp;
	}

	private static int levelFor(final MOB sample, final long xp)
	{
		int level = 1;
		while((level < 300) && (CMLib.leveler().getLevelExperience(sample, level) <= xp))
			level++;
		return level;
	}

	/**
	 * PROGRESSION XP: the experience the content gives, area by area.
	 * @param only one area, or null for all, in the map's order
	 * @return the lines
	 */
	public static String xpReport(final Area only)
	{
		final MOB sample = CMLib.leveler().fillOutMOB(CMClass.getCharClass("StdCharClass"), CMClass.getRace("Human"), 1);
		final StringBuilder str = new StringBuilder("Experience the content gives (achievements, MPEXP in scripts, Prop_EnterAdjuster):");
		long total = 0;
		final int ach = achievementXP();
		str.append("\n\r  achievements: ").append(ach);
		total += ach;
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
		{
			final Area A = a.nextElement();
			if((A == null) || CMath.bset(A.flags(), Area.FLAG_INSTANCE_CHILD) || ((only != null) && (A != only)))
				continue;
			final int xp = areaXP(A);
			total += xp;
			str.append("\n\r  ").append(A.Name()).append(": ").append(xp).append(" (").append(total).append(" in all, level ")
				.append((sample == null) ? 0 : levelFor(sample, total)).append(")");
		}
		if(sample != null)
			sample.destroy();
		return str.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 2)
		{
			mob.tell(L("PROGRESSION [CLASS] ([RACE]) ([TO LEVEL]), PROGRESSION XP ([AREA])"));
			return false;
		}
		if(commands.get(1).equalsIgnoreCase("XP"))
		{
			Area A = null;
			if(commands.size() > 2)
			{
				A = CMLib.map().getArea(CMParms.combine(commands, 2));
				if(A == null)
				{
					mob.tell(L("There is no area '@x1'.", CMParms.combine(commands, 2)));
					return false;
				}
			}
			mob.tell(xpReport(A));
			return false;
		}
		CharClass C = CMClass.getCharClass(commands.get(1));
		if(C == null)
			C = CMClass.findCharClass(commands.get(1));
		if(C == null)
		{
			mob.tell(L("There is no class '@x1'.", commands.get(1)));
			return false;
		}
		Race Rc = CMClass.getRace("Human");
		int to = 10;
		for(int i = 2; i < commands.size(); i++)
		{
			final String w = commands.get(i);
			if(CMath.isInteger(w))
				to = Math.max(1, Math.min(100, CMath.s_int(w)));
			else
			if(!w.equalsIgnoreCase("TO"))
			{
				Race R2 = CMClass.getRace(w);
				if(R2 == null)
					R2 = CMClass.findRace(w);
				if(R2 == null)
				{
					mob.tell(L("There is no race '@x1'.", w));
					return false;
				}
				Rc = R2;
			}
		}
		mob.tell(curve(C, Rc, to));
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
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDCLASSES);
	}
}
