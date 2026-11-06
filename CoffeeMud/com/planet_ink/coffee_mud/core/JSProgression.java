package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.Common.interfaces.Faction;
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
 * JS23: world/scripts/progression.js, if there is one, and its functions,
 * each called if the file defines it: onCreated(mob) when a character is
 * made; onCreationStep(mob, step, answer) after each step of making one
 * (race, gender, stats, class, factions, deity; CharCreation also names the
 * steps traits, template, origin and kit, and each creation.ini question by
 * its name in lower case); xpGain(mob, source,
 * amount), which returns the experience a gain gives instead (the source is
 * COMBAT:[level][flag], ABILITY:[id], CLASS:[id], MISC:[id], COMMAND:Teach,
 * SCRIPT:, DEATH:, FACTION:[id], SIEGE:[id], LIEGE:[name], COST:, HOMAGE[name],
 * QUEST:, DISCOVERY:, ACHIEVE:, SERVICE:, REMORT: or an ability or behavior's
 * own ID); onLevel(mob,
 * level) after a level's gains; practicesForLevel(mob) and
 * trainsForLevel(mob), which return the practices and trains a level gives
 * instead of the usual; onLearn(mob, abilityId) when a character gets a
 * skill; onTrain(mob, what, amount) after TRAIN; and statRoll(mob, bonus)
 * for STARTSTAT=SCRIPT, which sets the new character's stats or returns
 * them.  The file runs in the usual scope (cm, require, include, sud), with
 * the usual time limit, and is read again when it changes.
 */
public final class JSProgression
{
	private JSProgression()
	{
	}

	private static Scriptable	scope	= null;
	/** what the scope was read from: the file's path, time and size */
	private static String		readAs	= null;

	/** the creation steps onCreationStep is told of, in order */
	public static final String[]	STEPS	= { "race", "gender", "stats", "class", "factions", "deity" };

	/**
	 * The file.
	 * @return world/scripts/progression.js, or null if there is no world folder
	 */
	public static File file()
	{
		final File world = WorldFiles.worldDir();
		return (world == null) ? null : new File(new File(world, WorldHost.DIR), "progression.js");
	}

	/**
	 * Forgets the file as read, for a restart of the game, which reads it
	 * again.
	 */
	public static synchronized void reset()
	{
		scope = null;
		readAs = null;
	}

	private static synchronized Scriptable scope()
	{
		final File F = file();
		final String as = ((F == null) || (!F.isFile())) ? "" : (F.getAbsolutePath() + "|" + F.lastModified() + "|" + F.length());
		if(as.equals(readAs))
			return scope;
		readAs = as;
		scope = null;
		if(as.length() == 0)
			return null;
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final ScriptableObject S = new ImporterTopLevel(cx);
			JSScope.prepare(cx, S, null);
			cx.evaluateString(S, new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8), "world/scripts/progression.js", 1, null);
			scope = S;
		}
		catch(final Exception e)
		{
			JSRuntime.report("world/scripts/progression.js", e);
		}
		finally
		{
			Context.exit();
		}
		return scope;
	}

	/**
	 * Whether the file defines a function.
	 * @param fn its name
	 * @return true if it does
	 */
	public static boolean has(final String fn)
	{
		final Scriptable S = scope();
		return (S != null) && (ScriptableObject.getProperty(S, fn) instanceof Function);
	}

	/**
	 * Calls one of the file's functions, if it defines it.
	 * @param fn the function's name
	 * @param args its arguments, Java objects
	 * @return what it returned, as a Java object, or null (undefined too)
	 */
	public static Object call(final String fn, final Object... args)
	{
		final Scriptable S = scope();
		if(S == null)
			return null;
		final Object F = ScriptableObject.getProperty(S, fn);
		if(!(F instanceof Function))
			return null;
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Object[] jsArgs = new Object[args.length];
			for(int i = 0; i < args.length; i++)
				jsArgs[i] = Context.javaToJS(args[i], S);
			final Object result = ((Function)F).call(cx, S, S, jsArgs);
			if((result == null) || (result instanceof Undefined))
				return null;
			return Context.jsToJava(result, Object.class);
		}
		catch(final Exception e)
		{
			JSRuntime.report("world/scripts/progression.js " + fn + "()", e);
			return null;
		}
		finally
		{
			Context.exit();
		}
	}

	private static Integer whole(final Object o)
	{
		if(o instanceof Number)
		{
			final double d = ((Number)o).doubleValue();
			if(!Double.isNaN(d) && !Double.isInfinite(d))
				return Integer.valueOf((int)Math.round(d));
		}
		else
		if((o != null) && CMath.isNumber(o.toString().trim()))
			return Integer.valueOf((int)Math.round(CMath.s_double(o.toString().trim())));
		return null;
	}

	/**
	 * From CoffeeLevels.postExperience: what xpGain makes of a gain.
	 * @param mob who gains
	 * @param source the gain's source, as COMBAT:5 or ABILITY:Skill_Track
	 * @param amount the experience
	 * @return what xpGain returned, not under 0; or the amount, if it is
	 *         not a gain, or xpGain is not defined or returns no number
	 */
	public static int xpGain(final MOB mob, final String source, final int amount)
	{
		if((amount <= 0) || (mob == null) || !has("xpGain"))
			return amount;
		final Integer n = whole(call("xpGain", mob, (source == null) ? "" : source, Integer.valueOf(amount)));
		return (n == null) ? amount : Math.max(0, n.intValue());
	}

	/**
	 * From CoffeeLevels.level: practicesForLevel's or trainsForLevel's
	 * number, in place of the usual.
	 * @param fn practicesForLevel or trainsForLevel
	 * @param mob who levels
	 * @param usual the usual number
	 * @return the function's number, not under 0, or the usual
	 */
	public static int perLevel(final String fn, final MOB mob, final int usual)
	{
		if((mob == null) || !has(fn))
			return usual;
		final Integer n = whole(call(fn, mob));
		return (n == null) ? usual : Math.max(0, n.intValue());
	}

	/**
	 * From CharCreation: whether stats come from statRoll, as
	 * STARTSTAT=SCRIPT says, when the file defines it.
	 * @return true if they do
	 */
	public static boolean scriptedStats()
	{
		return CMProps.instance().getStr("STARTSTAT").trim().equalsIgnoreCase("SCRIPT") && has("statRoll");
	}

	/**
	 * From CharCreation: statRoll(mob, bonus) for a new character, which sets
	 * the stats itself or returns them, as { strength: 14, dexterity: 12 }
	 * (stats by name, abbreviation or the start of a name) or a list in the order of the base
	 * stats; a stat it leaves out is at STARTSTAT's usual, BASEMINSTAT.
	 * @param mob the new character
	 * @param bonus the bonus points the theme gives
	 */
	public static void statRoll(final MOB mob, final int bonus)
	{
		if(mob == null)
			return;
		mob.baseCharStats().setAllBaseValues(CMProps.getIntVar(CMProps.Int.BASEMINSTAT));
		final Object o = call("statRoll", mob, Integer.valueOf(bonus));
		final int[] base = CharStats.CODES.BASECODES();
		if(o instanceof Map)
		{
			for(final Map.Entry<?, ?> e : ((Map<?, ?>)o).entrySet())
			{
				final String key = String.valueOf(e.getKey()).trim();
				int code = CharStats.CODES.findWhole(key, true);
				if(code < 0)
					code = CharStats.CODES.findWhole(key.toLowerCase(), false);
				final Integer n = whole(e.getValue());
				if((code < 0) || (n == null))
					Log.errOut("JSProgression", "statRoll: no stat '" + e.getKey() + "' = " + e.getValue());
				else
					mob.baseCharStats().setStat(code, n.intValue());
			}
		}
		else
		if(o instanceof List)
		{
			final List<?> l = (List<?>)o;
			for(int i = 0; (i < l.size()) && (i < base.length); i++)
			{
				final Integer n = whole(l.get(i));
				if(n != null)
					mob.baseCharStats().setStat(base[i], n.intValue());
			}
		}
		mob.recoverCharStats();
	}

	/**
	 * From CharCreation: onCreationStep, after a step of making a character.
	 * @param mob the new character
	 * @param step race, gender, stats, class, factions, deity, traits, template, origin, kit,
	 *             or a creation.ini question's name in lower case
	 */
	public static void creationStep(final MOB mob, final String step)
	{
		if((mob == null) || !has("onCreationStep"))
			return;
		final String answer;
		switch(step)
		{
		case "race":
			answer = mob.baseCharStats().getMyRace().name();
			break;
		case "gender":
			answer = "" + (char)mob.baseCharStats().getStat(CharStats.STAT_GENDER);
			break;
		case "stats":
		{
			final StringBuilder s = new StringBuilder();
			for(final int code : CharStats.CODES.BASECODES())
				s.append((s.length() > 0) ? " " : "").append(CharStats.CODES.ABBR(code)).append(" ").append(mob.baseCharStats().getStat(code));
			answer = s.toString();
			break;
		}
		case "class":
			answer = mob.baseCharStats().getCurrentClass().name();
			break;
		case "factions":
		{
			final StringBuilder s = new StringBuilder();
			for(final Enumeration<String> f = mob.factions(); f.hasMoreElements();)
			{
				final String id = f.nextElement();
				final Faction F = CMLib.factions().getFaction(id);
				if(F != null)
					s.append((s.length() > 0) ? ", " : "").append(F.name()).append(" ").append(mob.fetchFaction(id));
			}
			answer = s.toString();
			break;
		}
		case "deity":
			answer = (mob.baseCharStats().getWorshipCharID() == null) ? "" : mob.baseCharStats().getWorshipCharID();
			break;
		default:
			answer = "";
			break;
		}
		call("onCreationStep", mob, step, answer);
	}

	/**
	 * What the file defines, for JS HELP and the guide.
	 * @return the names of the functions it defines
	 */
	public static List<String> defined()
	{
		final List<String> out = new ArrayList<String>();
		for(final String fn : new String[] { "onCreated", "onCreationStep", "xpGain", "onLevel", "practicesForLevel", "trainsForLevel", "onLearn", "onTrain", "statRoll" })
		{
			if(has(fn))
				out.add(fn);
		}
		return out;
	}
}
