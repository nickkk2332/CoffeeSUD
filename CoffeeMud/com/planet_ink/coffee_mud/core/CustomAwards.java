package com.planet_ink.coffee_mud.core;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Common.interfaces.Faction;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Libraries.interfaces.AchievementLibrary.AwardType;
import com.planet_ink.coffee_mud.Libraries.interfaces.AchievementLibrary.CustomAward;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.Tickable;
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
 * P5: achievement rewards beyond the stock thirteen, each written as a number
 * and a word with its settings in brackets: SCRIPT(LOAD=file) or an inline
 * script (lines between semicolons), run once on the character (ONCE_PROG, or
 * FUNCTION_PROG ONACHIEVE); EFFECT(ID= PARMS= TICKS=), lasting with TICKS=0;
 * FACTION(ID= AMOUNT=) (the number, without AMOUNT); VAR(NAME= VALUE= WHO=);
 * RECIPE(SKILL= NAME=).
 */
public class CustomAwards
{
	private CustomAwards()
	{
	}

	private static final String[] KINDS = { "SCRIPT", "EFFECT", "FACTION", "VAR", "RECIPE" };

	/**
	 * @param thing an award's word, upper case, as SCRIPT(...)
	 * @return true if it is one of these
	 */
	public static boolean isCustom(final String thing)
	{
		final String t = thing.trim().toUpperCase();
		for(final String k : KINDS)
		{
			if(t.startsWith(k + "(") && t.endsWith(")"))
				return true;
		}
		return false;
	}

	private static String parm(final String parms, final String key)
	{
		return CMStrings.deEscape(CMParms.getParmStr(parms, key, "")).trim();
	}

	/**
	 * Reads one of these awards.
	 * @param number the number written before it
	 * @param raw the award as written, its case kept, as SCRIPT(LOAD=progs/x.script)
	 * @return the award
	 * @throws IllegalArgumentException when it is not right, with why
	 */
	public static CustomAward parse(final int number, final String raw)
	{
		final String text = raw.trim();
		final int y = text.indexOf('(');
		final String kind = text.substring(0, y).trim().toUpperCase();
		final String parms = text.substring(y + 1, text.length() - 1).trim();
		final String source = number + " " + text;
		if(kind.equals("SCRIPT"))
		{
			if(parms.length() == 0)
				throw new IllegalArgumentException("SCRIPT() needs LOAD=file or a script");
			return award(source, CMLib.lang().L("a script of the story"), new Giver()
			{
				@Override
				public String give(final MOB mob)
				{
					runScript(mob, parms);
					return "";
				}
			});
		}
		if(kind.equals("EFFECT"))
		{
			final String id = parm(parms, "ID");
			final Ability proto = CMClass.getAbility(id);
			if(proto == null)
				throw new IllegalArgumentException("EFFECT(ID=) names no ability: " + id);
			final String ablParms = parm(parms, "PARMS");
			final int ticks = CMParms.getParmInt(parms, "TICKS", 0);
			return award(source, CMLib.lang().L("@x1@x2", proto.name(), (ticks > 0) ? CMLib.lang().L(" for @x1 ticks", "" + ticks) : ""), new Giver()
			{
				@Override
				public String give(final MOB mob)
				{
					final Ability A = CMClass.getAbility(id);
					if(A == null)
						return "";
					A.setMiscText(ablParms);
					if(ticks <= 0)
					{
						final Ability had = mob.fetchEffect(A.ID());
						if(had != null)
							mob.delEffect(had);
						A.setSavable(true);
						mob.addNonUninvokableEffect(A);
					}
					else
						A.startTickDown(mob, mob, ticks);
					mob.recoverCharStats();
					mob.recoverPhyStats();
					mob.recoverMaxState();
					return CMLib.lang().L("^HYou are given @x1!\n\r^?", A.name());
				}
			});
		}
		if(kind.equals("FACTION"))
		{
			final String id = parm(parms, "ID");
			Faction F = CMLib.factions().getFaction(id);
			if(F == null)
				F = CMLib.factions().getFactionByName(id);
			if(F == null)
				throw new IllegalArgumentException("FACTION(ID=) names no faction: " + id);
			final String fid = F.factionID();
			final String fname = F.name();
			final int amount = (parm(parms, "AMOUNT").length() > 0) ? CMath.s_int(parm(parms, "AMOUNT")) : number;
			return award(source, CMLib.lang().L("@x1@x2 @x3", (amount >= 0) ? "+" : "", "" + amount, fname), new Giver()
			{
				@Override
				public String give(final MOB mob)
				{
					final int now = mob.fetchFaction(fid);
					mob.addFaction(fid, (now == Integer.MAX_VALUE) ? amount : (now + amount));
					return CMLib.lang().L("^HYour standing with @x1 changes by @x2!\n\r^?", fname, "" + amount);
				}
			});
		}
		if(kind.equals("VAR"))
		{
			final String name = parm(parms, "NAME").toUpperCase();
			final String value = parm(parms, "VALUE");
			String who = parm(parms, "WHO");
			if(who.length() == 0)
				who = parm(parms, "SCOPE");
			final String fwho = who;
			if(name.length() == 0)
				throw new IllegalArgumentException("VAR() needs NAME=");
			return award(source, CMLib.lang().L("a mark in the story (@x1)", name.toLowerCase()), new Giver()
			{
				@Override
				public String give(final MOB mob)
				{
					final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
					S.setVar((fwho.length() == 0) ? mob.Name() : fwho, name, value);
					return "";
				}
			});
		}
		if(kind.equals("RECIPE"))
		{
			final String skill = parm(parms, "SKILL");
			final String name = parm(parms, "NAME");
			if((skill.length() == 0) || (name.length() == 0))
				throw new IllegalArgumentException("RECIPE() needs SKILL= and NAME=");
			return award(source, CMLib.lang().L("the recipe for @x1", name), new Giver()
			{
				@Override
				public String give(final MOB mob)
				{
					final String why = KnownRecipes.learn(mob, skill, name);
					if(why != null)
					{
						Log.errOut("CustomAwards", "RECIPE(SKILL=" + skill + " NAME=" + name + "): " + why);
						return "";
					}
					return CMLib.lang().L("^HYou learn how to make @x1!\n\r^?", name);
				}
			});
		}
		throw new IllegalArgumentException("not a known award: " + kind);
	}

	/** what an award does when given */
	private interface Giver
	{
		String give(MOB mob);
	}

	private static CustomAward award(final String source, final String desc, final Giver giver)
	{
		return new CustomAward()
		{
			@Override
			public AwardType getType()
			{
				return AwardType.CUSTOM;
			}

			@Override
			public String getDescription()
			{
				return desc;
			}

			@Override
			public boolean isPreAwarded()
			{
				return false;
			}

			@Override
			public boolean isNotAwardedOnRemort()
			{
				return false;
			}

			@Override
			public String give(final MOB mob)
			{
				return giver.give(mob);
			}

			@Override
			public String getSource()
			{
				return source;
			}
		};
	}

	/**
	 * Runs a reward's script once on a character: LOAD=file or the script,
	 * its ONCE_PROG on a tick and then its FUNCTION_PROG ONACHIEVE, in the
	 * character's room (or start room).
	 * @param mob the character
	 * @param script the script
	 */
	public static void runScript(final MOB mob, final String script)
	{
		final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
		S.setSavable(false);
		S.setVarScope("*");
		S.setScript(script);
		final Room oldRoom = mob.location();
		if(oldRoom == null)
			mob.setLocation(mob.getStartRoom());
		try
		{
			final CMMsg msg = CMClass.getMsg(mob, mob, null, CMMsg.MSG_OK_VISUAL, null, null, "ACHIEVE");
			S.executeMsg(mob, msg);
			S.dequeResponses(null);
			S.tick(mob, Tickable.TICKID_MOB);
			if(ScriptHooks.has(S, "ONACHIEVE"))
				ScriptHooks.call(S, mob, "ONACHIEVE", mob, mob, "");
		}
		finally
		{
			if(oldRoom == null)
				mob.setLocation(null);
		}
	}
}
