package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * ASSERT, for playtest files (tools/playtest.sh, DT15), in builder mode or a
 * DEV=1 game: answers "ASSERT ok: ..." or "ASSERT FAIL: ...", so that a
 * runner reads the answer instead of scraping the game's text.
 * <ul>
 * <li>ASSERT HERE roomid: the character is in that room.</li>
 * <li>ASSERT HAS item: the character carries (or wears) it.</li>
 * <li>ASSERT VAR host name op value: a global script variable compares so
 * (=, !=, &lt;, &gt;, &lt;=, &gt;=; numbers as numbers).</li>
 * <li>ASSERT QUEST name STEP n: the quest is running at that step.</li>
 * <li>ASSERT MARK n: says "PLAYSTEP n", the runner's step mark.</li>
 * </ul>
 */
public class AssertCmd extends StdCommand
{
	public AssertCmd()
	{
	}

	private final String[] access = I(new String[] { "ASSERT" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * Whether two values compare as asked.
	 * @param have the value there is
	 * @param op the comparison
	 * @param want the value asked for
	 * @return true if they do, null if the comparison is no comparison
	 */
	public static Boolean compares(final String have, final String op, final String want)
	{
		final boolean numbers = CMath.isNumber(have.trim()) && CMath.isNumber(want.trim());
		final int c = numbers ? Double.compare(CMath.s_double(have.trim()), CMath.s_double(want.trim())) : have.trim().compareToIgnoreCase(want.trim());
		switch(op)
		{
		case "=":
		case "==":
			return Boolean.valueOf(c == 0);
		case "!=":
		case "<>":
			return Boolean.valueOf(c != 0);
		case "<":
			return Boolean.valueOf(c < 0);
		case ">":
			return Boolean.valueOf(c > 0);
		case "<=":
			return Boolean.valueOf(c <= 0);
		case ">=":
			return Boolean.valueOf(c >= 0);
		default:
			return null;
		}
	}

	/**
	 * Answers an assertion.
	 * @param mob the character
	 * @param words the words after ASSERT
	 * @return "ok: ..." or "FAIL: ...", without ASSERT
	 */
	public static String answer(final MOB mob, final List<String> words)
	{
		if(words.isEmpty())
			return "FAIL: ASSERT HERE|HAS|VAR|QUEST|MARK ...";
		final String what = words.get(0).toUpperCase();
		final String rest = CMParms.combine(words, 1);
		switch(what)
		{
		case "HERE":
		{
			final Room R = mob.location();
			final String id = (R == null) ? "" : CMLib.map().getExtendedRoomID(R);
			if(id.equalsIgnoreCase(rest.trim()))
				return "ok: HERE " + rest.trim();
			return "FAIL: HERE " + rest.trim() + ": the character is in " + ((id.length() == 0) ? "no room" : id);
		}
		case "HAS":
		{
			final Item I = mob.findItem(null, rest.trim());
			if(I != null)
				return "ok: HAS " + rest.trim();
			return "FAIL: HAS " + rest.trim() + ": the character has no such thing";
		}
		case "VAR":
		{
			if(words.size() < 5)
				return "FAIL: ASSERT VAR [HOST] [NAME] [OP] [VALUE]";
			final String host = words.get(1);
			final String name = words.get(2);
			final String op = words.get(3);
			final String want = CMParms.combine(words, 4);
			final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
			final String have = S.getVar(host, name.toUpperCase());
			final Boolean ok = compares((have == null) ? "" : have, op, want);
			if(ok == null)
				return "FAIL: VAR " + host + " " + name + ": '" + op + "' is no comparison (=, !=, <, >, <=, >=)";
			if(ok.booleanValue())
				return "ok: VAR " + host + " " + name.toUpperCase() + " " + op + " " + want;
			return "FAIL: VAR " + host + " " + name.toUpperCase() + " " + op + " " + want + ": it is '" + have + "'";
		}
		case "QUEST":
		{
			final int stepAt = words.size() - 2;
			if((words.size() < 4) || (!words.get(stepAt).equalsIgnoreCase("STEP")) || (!CMath.isInteger(words.get(stepAt + 1))))
				return "FAIL: ASSERT QUEST [NAME] STEP [NUMBER]";
			final String name = CMParms.combine(words.subList(1, stepAt), 0);
			final Quest Q = QuestInfo.findQuest(name);
			if(Q == null)
				return "FAIL: QUEST " + name + ": there is no such quest";
			final int want = CMath.s_int(words.get(stepAt + 1));
			if(Q.running() && (Q.stepNumber() == want))
				return "ok: QUEST " + name + " STEP " + want;
			return "FAIL: QUEST " + name + " STEP " + want + ": it is " + (Q.running() ? ("at step " + Q.stepNumber()) : "not running");
		}
		case "MARK":
			return "MARK";
		default:
			return "FAIL: ASSERT HERE|HAS|VAR|QUEST ... ('" + what + "' is none of them)";
		}
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final List<String> words = new ArrayList<String>(commands.subList(1, commands.size()));
		final String a = answer(mob, words);
		if(a.equals("MARK"))
			mob.tell("PLAYSTEP " + CMParms.combine(words, 1));
		else
			mob.tell("ASSERT " + a);
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
		return SaveGame.isBuilderMode() || SaveGame.isDevMode();
	}
}
