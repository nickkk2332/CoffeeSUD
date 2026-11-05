package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.planet_ink.coffee_mud.Common.interfaces.Faction;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
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
 * P12: creation steps and questions as data, world/creation.ini.  STEPS= gives
 * the steps: RACE, GENDER, STATS, CLASS, FACTIONS and DEITY keep the engine's
 * order (each needs the one before), and a standard step left out is not
 * asked (its pick is random, as the CHARCR* DISABLE flags make it); ORIGIN,
 * KIT and the world's own questions ([NAME] sections) come where STEPS puts
 * them.  RACE=RANDOM (or SKIP), and so on, for a step listed but not asked.
 * A question has PROMPT, INTRO (a text file), SHOWIF (a zapper mask) and
 * CHOICE lines (NAME, HELP, MASK, EFFECTS in achievement reward syntax,
 * FACTION, START, SCRIPT); its answer is the tattoo CHARCR_[STEP]_[CHOICE],
 * and what the answer gives comes at the end of creation.
 */
public class CreationSteps
{
	private CreationSteps()
	{
	}

	/** the engine's steps, in its order */
	public static final String[]	FIXED		= { "RACE", "GENDER", "STATS", "CLASS", "FACTIONS", "DEITY" };
	/** the standard steps that go where STEPS puts them */
	public static final String[]	MOVABLE		= { "ORIGIN", "KIT", "TRAITS" };
	/** the default order */
	public static final String[]	DEFAULT		= { "RACE", "GENDER", "STATS", "CLASS", "ORIGIN", "KIT", "TRAITS", "FACTIONS", "DEITY" };

	/** one answer to a question */
	public static class Choice
	{
		public String	name	= "";
		public String	help	= "";
		public String	mask	= "";
		public String	effects	= "";
		public String	faction	= "";
		public String	start	= "";
		public String	script	= "";
	}

	/** a question of the world's own */
	public static class Question
	{
		public final String			name;
		public String				prompt	= "";
		public String				intro	= "";
		public String				showIf	= "";
		public final List<Choice>	choices	= new ArrayList<Choice>();

		public Question(final String name)
		{
			this.name = name;
		}
	}

	/** creation.ini, as read */
	public static class Plan
	{
		public final List<String>			steps		= new ArrayList<String>();
		public final Map<String, String>	modes		= new HashMap<String, String>();
		public final Map<String, Question>	questions	= new HashMap<String, Question>();
		public boolean						listed		= false;
	}

	private static final ThreadLocal<Boolean> quick = new ThreadLocal<Boolean>();

	/**
	 * P27: whether the creation on this thread is a QUICK NEW, every step
	 * taken at random.
	 * @param on true for QUICK
	 */
	public static void setQuick(final boolean on)
	{
		quick.set(Boolean.valueOf(on));
	}

	/** @return true while a QUICK NEW is made on this thread */
	public static boolean isQuick()
	{
		return Boolean.TRUE.equals(quick.get());
	}

	/**
	 * P27: NEW [name] QUICK ([template]): the words after QUICK.
	 * @param parms NEW's words
	 * @return null without QUICK, else the template's name ("" for none)
	 */
	public static String quickArgs(final String[] parms)
	{
		for(int p = 2; p < parms.length; p++)
		{
			if(parms[p].equalsIgnoreCase("QUICK"))
			{
				final StringBuilder t = new StringBuilder("");
				for(int q = p + 1; q < parms.length; q++)
				{
					if(!parms[q].equalsIgnoreCase("<CONFIRMED>"))
						t.append(parms[q]).append(' ');
				}
				return t.toString().trim();
			}
		}
		return null;
	}

	private static Plan		cached		= null;
	private static long		cachedWhen	= -1;
	private static String	cachedPath	= null;

	/** @return world/creation.ini, or null with no world folder */
	public static File file()
	{
		final File W = WorldFiles.worldDir();
		return (W == null) ? null : new File(W, "creation.ini");
	}

	/**
	 * creation.ini as it is now; the default steps without it.
	 * @return the plan
	 */
	public static synchronized void clear() { cached = null; cachedWhen = -1; cachedPath = null; }

	public static synchronized Plan plan()
	{
		final File F = file();
		if((F == null) || (!F.isFile()))
		{
			cached = null;
			final Plan P = new Plan();
			P.steps.addAll(Arrays.asList(DEFAULT));
			return P;
		}
		if((cached != null) && (cachedWhen == F.lastModified()) && F.getPath().equals(cachedPath))
			return cached;
		String text = "";
		try
		{
			text = new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8);
		}
		catch(final java.io.IOException e)
		{
			Log.errOut("CreationSteps", "creation.ini: " + e.getMessage());
		}
		cached = parse(text);
		cachedWhen = F.lastModified();
		cachedPath = F.getPath();
		return cached;
	}

	private static boolean isFixed(final String s)
	{
		return Arrays.asList(FIXED).contains(s);
	}

	private static boolean isStandard(final String s)
	{
		return isFixed(s) || Arrays.asList(MOVABLE).contains(s) || s.equals("REVIEW");
	}

	/**
	 * Reads creation.ini's text.
	 * @param text the text
	 * @return the plan
	 */
	public static Plan parse(final String text)
	{
		final Plan P = new Plan();
		Question Q = null;
		final String[] lines = text.replace("\r", "").split("\n");
		for(int l = 0; l < lines.length; l++)
		{
			String line = lines[l].trim();
			while(line.endsWith("\\") && (l + 1 < lines.length))
				line = line.substring(0, line.length() - 1).trim() + " " + lines[++l].trim();
			if((line.length() == 0) || line.startsWith("#") || line.startsWith(";"))
				continue;
			if(line.startsWith("[") && line.endsWith("]"))
			{
				final String name = line.substring(1, line.length() - 1).trim().toUpperCase().replace(' ', '_');
				if(isStandard(name))
				{
					Log.errOut("CreationSteps", "creation.ini: [" + name + "] is a standard step's name, not a question's.");
					Q = null;
				}
				else
				{
					Q = new Question(name);
					P.questions.put(name, Q);
				}
				continue;
			}
			final int x = line.indexOf('=');
			if(x <= 0)
			{
				Log.errOut("CreationSteps", "creation.ini: line " + (l + 1) + " is not [NAME] or KEY=VALUE: " + line);
				continue;
			}
			final String key = line.substring(0, x).trim().toUpperCase();
			final String val = line.substring(x + 1).trim();
			if(Q == null)
			{
				if(key.equals("STEPS"))
				{
					P.listed = true;
					P.steps.clear();
					for(final String s : CMParms.parseCommas(val.toUpperCase(), true))
					{
						final String st = s.trim().replace(' ', '_');
						if(!P.steps.contains(st))
							P.steps.add(st);
					}
				}
				else
					P.modes.put(key, val.toUpperCase());
				continue;
			}
			if(key.equals("PROMPT"))
				Q.prompt = val;
			else
			if(key.equals("INTRO"))
				Q.intro = val;
			else
			if(key.equals("SHOWIF"))
				Q.showIf = val;
			else
			if(key.equals("MODE"))
				P.modes.put(Q.name, val.toUpperCase());
			else
			if(key.equals("CHOICE"))
			{
				final Choice C = new Choice();
				C.name = CMParms.getParmStr(val, "NAME", "").trim();
				C.help = CMParms.getParmStr(val, "HELP", "").trim();
				C.mask = CMParms.getParmStr(val, "MASK", "").trim();
				C.effects = CMParms.getParmStr(val, "EFFECTS", "").trim();
				C.faction = CMParms.getParmStr(val, "FACTION", "").trim();
				C.start = CMParms.getParmStr(val, "START", "").trim();
				C.script = CMParms.getParmStr(val, "SCRIPT", "").trim();
				if(C.name.length() == 0)
					Log.errOut("CreationSteps", "creation.ini [" + Q.name + "]: a CHOICE with no NAME: " + val);
				else
					Q.choices.add(C);
			}
			else
				Log.errOut("CreationSteps", "creation.ini [" + Q.name + "]: unknown key " + key);
		}
		if(!P.listed)
			P.steps.addAll(Arrays.asList(DEFAULT));
		// the engine's steps go in its order; say so if STEPS has them otherwise
		int last = -1;
		for(final String s : P.steps)
		{
			final int i = Arrays.asList(FIXED).indexOf(s);
			if(i >= 0)
			{
				if(i < last)
					Log.errOut("CreationSteps", "creation.ini: " + s + " comes before " + FIXED[last] + " in creation, whatever STEPS says.");
				last = Math.max(last, i);
			}
			else
			if((!isStandard(s)) && (!P.questions.containsKey(s)))
				Log.errOut("CreationSteps", "creation.ini: STEPS names " + s + ", which is neither a standard step nor a [" + s + "] question.");
		}
		return P;
	}

	/**
	 * How a step is taken: ASK, SKIP or RANDOM.  A step STEPS leaves out is
	 * RANDOM for RACE, GENDER, STATS and CLASS, SKIP for the rest.
	 * @param step the step
	 * @return ASK, SKIP or RANDOM
	 */
	public static String mode(final String step)
	{
		final Plan P = plan();
		final String s = step.toUpperCase();
		if(isQuick())
			return s.equals("REVIEW") ? "SKIP" : "RANDOM"; // P27
		final String m = P.modes.get(s);
		final boolean four = s.equals("RACE") || s.equals("GENDER") || s.equals("STATS") || s.equals("CLASS");
		if(!P.steps.contains(s))
			return four ? "RANDOM" : "SKIP";
		if(m == null)
			return "ASK";
		if(m.startsWith("RAND"))
			return "RANDOM";
		if(m.startsWith("SKIP") || m.startsWith("NO"))
			return four ? "RANDOM" : "SKIP";
		return "ASK";
	}

	/**
	 * @param step a step
	 * @return true if it is not asked
	 */
	public static boolean auto(final String step)
	{
		return !mode(step).equals("ASK");
	}

	/**
	 * The steps STEPS puts after one of the engine's steps, before its next one.
	 * @param fixed a step of the engine's, or START for those before RACE
	 * @return ORIGIN, KIT and question names, in order
	 */
	public static List<String> after(final String fixed)
	{
		final List<String> list = new ArrayList<String>();
		final Plan P = plan();
		boolean in = fixed.equalsIgnoreCase("START");
		for(final String s : P.steps)
		{
			if(isFixed(s))
			{
				if(in)
					break;
				in = s.equalsIgnoreCase(fixed);
				continue;
			}
			if(in && (Arrays.asList(MOVABLE).contains(s) || P.questions.containsKey(s)))
				list.add(s);
		}
		return list;
	}

	/**
	 * @param name a question's name
	 * @return the question, or null
	 */
	public static Question question(final String name)
	{
		return (name == null) ? null : plan().questions.get(name.toUpperCase());
	}

	/**
	 * @param mob a character being made
	 * @param Q a question
	 * @return true if it is asked of them (SHOWIF)
	 */
	public static boolean shown(final MOB mob, final Question Q)
	{
		return (Q.showIf.length() == 0) || CMLib.masking().maskCheck(Q.showIf, mob, true);
	}

	/**
	 * @param mob a character being made
	 * @param Q a question
	 * @return the answers open to them
	 */
	public static List<Choice> choices(final MOB mob, final Question Q)
	{
		final List<Choice> list = new ArrayList<Choice>();
		for(final Choice C : Q.choices)
		{
			if((C.mask.length() == 0) || CMLib.masking().maskCheck(C.mask, mob, true))
				list.add(C);
		}
		return list;
	}

	/**
	 * @param Q a question
	 * @return its INTRO file's text, or ""
	 */
	public static String intro(final Question Q)
	{
		if(Q.intro.length() == 0)
			return "";
		final File W = WorldFiles.worldDir();
		final File F = (W == null) ? null : new File(W, Q.intro);
		try
		{
			if((F != null) && F.isFile())
				return new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8);
		}
		catch(final java.io.IOException e)
		{
		}
		final CMFile C = new CMFile(Resources.makeFileResourceName(Q.intro), null);
		return C.exists() ? C.text().toString() : "";
	}

	/**
	 * @param Q a question
	 * @param choices its answers open to the character
	 * @return the menu
	 */
	public static String menu(final Question Q, final List<Choice> choices)
	{
		final StringBuilder s = new StringBuilder("\n\r");
		for(int i = 0; i < choices.size(); i++)
		{
			final Choice C = choices.get(i);
			s.append("^H").append(i + 1).append(") ").append(C.name).append("^N");
			if(C.help.length() > 0)
				s.append(": ").append(C.help);
			s.append("\n\r");
		}
		return s.toString();
	}

	/**
	 * Finds what was typed among the answers: its number, its name, or the
	 * start of its name.
	 * @param choices the answers
	 * @param input what was typed
	 * @return the answer, or null
	 */
	public static Choice find(final List<Choice> choices, final String input)
	{
		final String in = (input == null) ? "" : input.trim();
		if(in.length() == 0)
			return null;
		if(CMath.isInteger(in))
		{
			final int n = CMath.s_int(in);
			return ((n >= 1) && (n <= choices.size())) ? choices.get(n - 1) : null;
		}
		for(final Choice C : choices)
		{
			if(C.name.equalsIgnoreCase(in))
				return C;
		}
		for(final Choice C : choices)
		{
			if(C.name.toLowerCase().startsWith(in.toLowerCase()))
				return C;
		}
		return null;
	}

	private static String word(final String s)
	{
		return s.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	/**
	 * @param Q a question
	 * @param C an answer
	 * @return the tattoo the answer is kept as
	 */
	public static String tattoo(final Question Q, final Choice C)
	{
		return "CHARCR_" + word(Q.name) + "_" + word(C.name);
	}

	/**
	 * Keeps a character's answer, as its tattoo.
	 * @param mob the character
	 * @param Q the question
	 * @param C the answer
	 */
	public static void choose(final MOB mob, final Question Q, final Choice C)
	{
		for(final Choice other : Q.choices)
		{
			if(mob.findTattoo(tattoo(Q, other)) != null)
				mob.delTattoo(tattoo(Q, other));
		}
		mob.addTattoo(tattoo(Q, C));
	}

	/**
	 * A random answer, for a question taken at random.
	 * @param choices the answers open
	 * @return one of them, or null for none
	 */
	public static Choice random(final List<Choice> choices)
	{
		return (choices.size() == 0) ? null : choices.get(CMLib.dice().roll(1, choices.size(), -1));
	}

	/**
	 * Gives a character made what their answers give: EFFECTS (achievement
	 * rewards), FACTION (an ID or name and an amount added), START (their start
	 * room) and SCRIPT (a ONCE_PROG, run in their start room).
	 * @param mob the character
	 * @return how many answers gave something
	 */
	public static int applyAnswers(final MOB mob)
	{
		int n = 0;
		final Plan P = plan();
		for(final String s : P.steps)
		{
			final Question Q = P.questions.get(s);
			if(Q == null)
				continue;
			for(final Choice C : Q.choices)
			{
				if(mob.findTattoo(tattoo(Q, C)) == null)
					continue;
				n++;
				final String where = "creation.ini [" + Q.name + "] " + C.name + ": ";
				if(C.start.length() > 0)
				{
					final Room R = CMLib.map().getRoom(C.start);
					if(R != null)
						mob.setStartRoom(R);
					else
						Log.errOut("CreationSteps", where + "no room " + C.start);
				}
				if(C.effects.length() > 0)
					CMLib.achievements().giveAwards(mob, C.effects);
				final List<String> fw = CMParms.parse(C.faction);
				if(fw.size() >= 2)
				{
					final String fname = CMParms.combine(fw.subList(0, fw.size() - 1), 0);
					Faction F = CMLib.factions().getFaction(fname);
					if(F == null)
						F = CMLib.factions().getFactionByName(fname);
					if(F == null)
						Log.errOut("CreationSteps", where + "no faction " + fname);
					else
					{
						final int now = mob.fetchFaction(F.factionID());
						final int amount = CMath.s_int(fw.get(fw.size() - 1));
						mob.addFaction(F.factionID(), (now == Integer.MAX_VALUE) ? amount : (now + amount));
					}
				}
				if(C.script.length() > 0)
					Origins.runScript(mob, C.script);
			}
		}
		return n;
	}
}
