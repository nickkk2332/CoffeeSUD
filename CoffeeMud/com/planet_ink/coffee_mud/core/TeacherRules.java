package com.planet_ink.coffee_mud.core;

import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
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
 * P16: a teacher's rules, in MOBTeacher's parameters or Prop_Trainer's text:
 * STUDENTMASK="..." (who it teaches at all) and REFUSEMSG="...",
 * SKILLMASK.[ability ID]="..." (who it teaches that skill), PRICE="5 gold per
 * level" (added to the usual cost), HIDDEN=[skill IDs] (taught, not listed).
 * They gate PRACTICE, TEACH and GAIN with it, and TRAIN.
 */
public class TeacherRules
{
	private TeacherRules()
	{
	}

	private static final Pattern KEYED = Pattern.compile(
		"(?i)\\b(STUDENTMASK|REFUSEMSG|PRICE|HIDDEN|SKILLMASK\\.[A-Za-z0-9_]+)\\s*=\\s*(\"[^\"]*\"|'[^']*'|\\S+)");

	/**
	 * @param parms a teacher's parameters
	 * @return them without the rules, for the words of skills and classes
	 */
	public static String withoutRules(final String parms)
	{
		return (parms == null) ? "" : KEYED.matcher(parms).replaceAll(" ").trim();
	}

	/**
	 * @param parms a teacher's parameters
	 * @return its rules, by upper-case key, quotes taken off
	 */
	public static Map<String, String> rules(final String parms)
	{
		final Map<String, String> m = new TreeMap<String, String>();
		if(parms == null)
			return m;
		final Matcher M = KEYED.matcher(parms);
		while(M.find())
		{
			String v = M.group(2);
			if((v.length() >= 2) && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))))
				v = v.substring(1, v.length() - 1);
			m.put(M.group(1).toUpperCase(), v);
		}
		return m;
	}

	/**
	 * @param teacher a mob
	 * @return the parameters of its MOBTeacher, else its Prop_Trainer's text, or ""
	 */
	public static String parms(final MOB teacher)
	{
		if(teacher == null)
			return "";
		final Behavior B = teacher.fetchBehavior("MOBTeacher");
		if(B != null)
			return B.getParms();
		final Ability A = teacher.fetchEffect("Prop_Trainer");
		if(A != null)
			return A.text();
		final Ability A2 = teacher.fetchEffect("Prop_StatTrainer");
		return (A2 == null) ? "" : A2.text();
	}

	/**
	 * Why a teacher will not teach a student (a skill).
	 * @param teacher the teacher
	 * @param student the student
	 * @param abilityID the skill, or null for anything
	 * @return what the teacher says, or null if it will
	 */
	public static String refusal(final MOB teacher, final MOB student, final String abilityID)
	{
		final Map<String, String> r = rules(parms(teacher));
		if(r.isEmpty())
			return null;
		final String mask = r.get("STUDENTMASK");
		if((mask != null) && (mask.trim().length() > 0) && (!CMLib.masking().maskCheck(mask, student, true)))
		{
			final String msg = r.get("REFUSEMSG");
			return ((msg != null) && (msg.length() > 0)) ? msg : CMLib.lang().L("I will not teach you.");
		}
		if(abilityID != null)
		{
			final String skillMask = r.get("SKILLMASK." + abilityID.toUpperCase());
			if((skillMask != null) && (skillMask.trim().length() > 0) && (!CMLib.masking().maskCheck(skillMask, student, true)))
			{
				final Ability A = CMClass.getAbility(abilityID);
				return CMLib.lang().L("I will not teach you @x1.", (A == null) ? abilityID : A.name());
			}
		}
		return null;
	}

	/**
	 * The teacher's own price: PRICE="N (currency) per level" (times the
	 * student's level) or "N".
	 * @param teacher the teacher
	 * @param student the student
	 * @return the price in base money, 0 for none
	 */
	public static double price(final MOB teacher, final MOB student)
	{
		final String p = rules(parms(teacher)).get("PRICE");
		if((p == null) || (p.trim().length() == 0))
			return 0;
		final String t = p.trim().toUpperCase();
		final int sp = t.indexOf(' ');
		final double n = CMath.s_double((sp < 0) ? t : t.substring(0, sp));
		if(t.endsWith("PER LEVEL") && (student != null))
			return n * Math.max(1, student.basePhyStats().level());
		return n;
	}

	/**
	 * Takes the teacher's price from the student, if they can pay it.
	 * @param teacher the teacher
	 * @param student the student
	 * @return null when paid (or free), else what to say
	 */
	public static String pay(final MOB teacher, final MOB student)
	{
		final String why = cannotPay(teacher, student);
		if(why == null)
			charge(teacher, student);
		return why;
	}

	/**
	 * @param teacher the teacher
	 * @param student the student
	 * @return null if they can pay the teacher's price (or there is none), else what to say
	 */
	public static String cannotPay(final MOB teacher, final MOB student)
	{
		final double cost = price(teacher, student);
		if((cost <= 0) || (CMLib.beanCounter().getTotalAbsoluteNativeValue(student) >= cost))
			return null;
		return CMLib.lang().L("My lessons cost @x1.", CMLib.beanCounter().nameCurrencyShort(student, cost));
	}

	/**
	 * Takes the teacher's price from the student (call after cannotPay).
	 * @param teacher the teacher
	 * @param student the student
	 */
	public static void charge(final MOB teacher, final MOB student)
	{
		final double cost = price(teacher, student);
		if(cost > 0)
			CMLib.beanCounter().subtractMoney(student, cost);
	}

	/**
	 * Both checks, for one place: what the teacher says if it will not
	 * teach the student (the skill) or they cannot pay.
	 * @param teacher the teacher
	 * @param student the student
	 * @param abilityID the skill, or null
	 * @return null if it will
	 */
	public static String gate(final MOB teacher, final MOB student, final String abilityID)
	{
		final String why = refusal(teacher, student, abilityID);
		return (why != null) ? why : cannotPay(teacher, student);
	}

	/**
	 * @param teacher a teacher
	 * @param abilityID a skill
	 * @return true if the teacher keeps it off its lists (HIDDEN=)
	 */
	public static boolean hidden(final MOB teacher, final String abilityID)
	{
		final String h = rules(parms(teacher)).get("HIDDEN");
		if(h == null)
			return false;
		for(final String s : CMParms.parseCommas(h.replace(' ', ','), true))
		{
			if(s.trim().equalsIgnoreCase(abilityID))
				return true;
		}
		return false;
	}

	/**
	 * TEACHLIST: what a teacher offers a student: the skills it knows that the
	 * student qualifies for and has not, what each costs, and those it will
	 * not teach them, with why.
	 * @param teacher the teacher
	 * @param student the student
	 * @return the text
	 */
	public static String teachList(final MOB teacher, final MOB student)
	{
		final StringBuilder can = new StringBuilder("");
		final StringBuilder not = new StringBuilder("");
		final String all = refusal(teacher, student, null);
		if(all != null)
			return CMLib.lang().L("@x1 says, '@x2'", teacher.name(student), all);
		final double cost = price(teacher, student);
		for(final Enumeration<Ability> a = teacher.abilities(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if((A == null) || hidden(teacher, A.ID()) || (student.fetchAbility(A.ID()) != null))
				continue;
			if(!CMLib.ableMapper().qualifiesByLevel(student, A))
				continue;
			final String why = refusal(teacher, student, A.ID());
			if(why != null)
				not.append("  ").append(A.name()).append(" (").append(CMLib.lang().L("not for you")).append(")\n\r");
			else
				can.append("  ^H").append(A.name()).append("^N").append((cost > 0) ? (" (" + CMLib.beanCounter().nameCurrencyShort(student, cost) + ")") : "").append("\n\r");
		}
		final StringBuilder s = new StringBuilder("");
		if(can.length() > 0)
			s.append(CMLib.lang().L("^H@x1 can teach you^N (PRACTICE, or say TEACH ME):\n\r", teacher.name(student))).append(can);
		if(not.length() > 0)
			s.append(CMLib.lang().L("^HNot for you^N:\n\r")).append(not);
		if(s.length() == 0)
			s.append(CMLib.lang().L("@x1 has nothing to teach you.", teacher.name(student)));
		return s.toString();
	}
}
