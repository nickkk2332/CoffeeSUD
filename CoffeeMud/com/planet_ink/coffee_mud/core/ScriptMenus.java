package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine.MPContext;
import com.planet_ink.coffee_mud.Common.interfaces.Session;
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
 * N2: MPMENU who FUNCTION "prompt" key:"label"[IF cond] ... [default]: a
 * numbered menu that does not block.  The script goes on at once; the
 * character's next line answers it, by number or key, and that
 * FUNCTION_PROG of the same script runs with the key as $0 (and $g), with
 * $n the character and $i the host.  An empty line takes the default; a
 * line that answers nothing takes the default too, or, with none, asks
 * again.  The answer is not a command, so it takes no time in turn-based
 * play.  One menu at a time per character: a new one replaces the old.
 */
public final class ScriptMenus
{
	private ScriptMenus()
	{
	}

	/** one choice */
	public static final class Option
	{
		/** what the function gets as $0 */
		public String	key		= "";
		/** what the character sees */
		public String	label	= "";
		/** an IF condition, unevaluated, or "" */
		public String	cond	= "";
		/** N9: in a menu of commands (no script), what the choice runs for the character */
		public String	command	= "";
	}

	/** an open menu */
	public static final class Menu
	{
		/** the prompt, before the choices */
		public String				prompt	= "";
		/** the choices shown */
		public final List<Option>	options	= new ArrayList<Option>();
		/** the default key, or "" */
		public String				dflt	= "";
		/** the character answering */
		public MOB					who		= null;
		/** the script that asked */
		public ScriptingEngine		engine	= null;
		/** where it asked from */
		public MPContext			ctx		= null;
		/** the FUNCTION_PROG to run with the answer */
		public String				func	= "";
	}

	private static final Map<String, Menu>	open	= new ConcurrentHashMap<String, Menu>();

	private static String key(final MOB mob)
	{
		return mob.Name().toUpperCase();
	}

	/**
	 * Reads what follows MPMENU who FUNCTION.
	 * @param rest "prompt" key:"label"[IF cond] ... [default]
	 * @return the menu, its conditions and labels as written
	 * @throws IllegalArgumentException what is wrong with it
	 */
	public static Menu parse(final String rest)
	{
		final Menu M = new Menu();
		final String s = (rest == null) ? "" : rest;
		int i = skip(s, 0);
		if((i < s.length()) && (s.charAt(i) == '"'))
		{
			final int e = s.indexOf('"', i + 1);
			if(e < 0)
				throw new IllegalArgumentException("the prompt has no closing quote");
			M.prompt = s.substring(i + 1, e);
			i = e + 1;
		}
		while((i = skip(s, i)) < s.length())
		{
			int e = i;
			while((e < s.length()) && (s.charAt(e) != ':') && (!Character.isWhitespace(s.charAt(e))))
				e++;
			final String k = s.substring(i, e);
			if((e >= s.length()) || (s.charAt(e) != ':'))
			{
				M.dflt = k; // a bare word: the default
				i = e;
				continue;
			}
			final Option O = new Option();
			O.key = k;
			i = e + 1;
			if((i < s.length()) && (s.charAt(i) == '"'))
			{
				final int q = s.indexOf('"', i + 1);
				if(q < 0)
					throw new IllegalArgumentException("the label of " + k + " has no closing quote");
				O.label = s.substring(i + 1, q);
				i = q + 1;
			}
			else
			{
				e = i;
				while((e < s.length()) && (!Character.isWhitespace(s.charAt(e))) && (s.charAt(e) != '['))
					e++;
				O.label = s.substring(i, e);
				i = e;
			}
			if((i < s.length()) && (s.charAt(i) == '['))
			{
				int depth = 0;
				e = i;
				for(; e < s.length(); e++)
				{
					if(s.charAt(e) == '[')
						depth++;
					else
					if((s.charAt(e) == ']') && ((--depth) == 0))
						break;
				}
				if(e >= s.length())
					throw new IllegalArgumentException("the condition of " + k + " has no closing ]");
				String c = s.substring(i + 1, e).trim();
				if(c.toUpperCase().startsWith("IF "))
					c = c.substring(3).trim();
				O.cond = c;
				i = e + 1;
			}
			if(O.key.length() == 0)
				throw new IllegalArgumentException("a choice with no key");
			if(O.label.length() == 0)
				O.label = O.key;
			M.options.add(O);
		}
		return M;
	}

	private static int skip(final String s, int i)
	{
		while((i < s.length()) && Character.isWhitespace(s.charAt(i)))
			i++;
		return i;
	}

	/**
	 * @param M a menu
	 * @return it as the character sees it
	 */
	public static String text(final Menu M)
	{
		final StringBuilder str = new StringBuilder("");
		if(M.prompt.length() > 0)
			str.append(M.prompt).append("\n\r");
		for(int i = 0; i < M.options.size(); i++)
			str.append("  ^w").append(i + 1).append(".^N ").append(M.options.get(i).label).append("\n\r");
		String dfltLabel = "";
		for(final Option O : M.options)
		{
			if(O.key.equalsIgnoreCase(M.dflt))
				dfltLabel = O.label;
		}
		str.append(CMLib.lang().L("Choose 1-@x1", "" + M.options.size()));
		if(dfltLabel.length() > 0)
			str.append(CMLib.lang().L(" (Enter: @x1)", dfltLabel));
		str.append(": ");
		return str.toString();
	}

	/**
	 * Shows a menu and waits, without blocking, for its answer.
	 * @param M the menu, its options already those to show
	 * @param S the session to ask on, or null to tell the character (and
	 *            answer through {@link #answer(MOB, String)})
	 */
	public static void open(final Menu M, final Session S)
	{
		if((M.who == null) || M.options.isEmpty())
			return;
		open.put(key(M.who), M);
		if(S == null)
		{
			M.who.tell(text(M));
			return;
		}
		S.prompt(new Session.InputCallback(Session.InputCallback.Type.PROMPT, "", 0)
		{
			@Override
			public void showPrompt()
			{
				S.promptPrint(text(M));
			}

			@Override
			public void timedOut()
			{
			}

			@Override
			public void callBack()
			{
				answer(M.who, this.input, S);
			}
		});
	}

	/**
	 * @param mob a character
	 * @return true if a menu waits for their answer
	 */
	public static boolean waiting(final MOB mob)
	{
		return (mob != null) && open.containsKey(key(mob));
	}

	/**
	 * Takes a character's answer to their menu.
	 * @param mob the character
	 * @param input what they typed
	 * @return true if they had a menu
	 */
	public static boolean answer(final MOB mob, final String input)
	{
		return answer(mob, input, null);
	}

	private static boolean answer(final MOB mob, final String input, final Session S)
	{
		if(mob == null)
			return false;
		final Menu M = open.get(key(mob));
		if(M == null)
			return false;
		final String in = (input == null) ? "" : input.trim();
		Option chosen = null;
		if(CMath.isInteger(in))
		{
			final int n = CMath.s_int(in);
			if((n >= 1) && (n <= M.options.size()))
				chosen = M.options.get(n - 1);
		}
		else
		if(in.length() > 0)
		{
			for(final Option O : M.options)
			{
				if(O.key.equalsIgnoreCase(in))
					chosen = O;
			}
		}
		String k = (chosen != null) ? chosen.key : "";
		if((k.length() == 0) && (M.dflt.length() > 0))
			k = M.dflt;
		if(k.length() == 0)
		{
			mob.tell(CMLib.lang().L("Choose 1 to @x1.", "" + M.options.size()));
			open(M, S);
			return true;
		}
		open.remove(key(mob));
		if(M.engine == null) // N9: a menu of commands, TALK's services
		{
			for(final Option O : M.options)
			{
				if(O.key.equalsIgnoreCase(k) && (O.command.length() > 0))
				{
					mob.doCommand(CMParms.parse(O.command), 0);
					break;
				}
			}
			return true;
		}
		final MPContext c = new MPContext(M.ctx.scripted, M.ctx.monster, mob, M.ctx.scripted, null, null, k, new Object[] { k });
		M.engine.callFunc(M.func, k, c);
		return true;
	}

	/**
	 * Closes a character's menu without an answer.
	 * @param mob the character
	 */
	public static void cancel(final MOB mob)
	{
		if(mob != null)
			open.remove(key(mob));
	}
}
