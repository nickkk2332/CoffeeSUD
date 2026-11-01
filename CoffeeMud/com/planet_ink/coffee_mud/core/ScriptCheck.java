package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.RhinoException;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Exits.interfaces.Exit;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent;

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
 * SCRIPTCHECK (DT11): reads MOBPROG scripts, with LOAD= followed, without
 * running them, and reports by file:line what would go wrong: unknown
 * triggers, commands (no prefix matching) and functions; IF/ELSE/ENDIF,
 * SWITCH/CASE/ENDSWITCH and FOR/NEXT out of balance, MPWHILE parentheses;
 * &lt;SCRIPT&gt; without &lt;/SCRIPT&gt;, JavaScript that does not compile and,
 * where approval is needed, unapproved blocks; LOAD= files missing;
 * MPCALLFUNC to no FUNCTION_PROG; room IDs that do not exist in MPGOTO,
 * MPTRANSFER and MPOLOADROOM.  It splits a script as the engine's parser does
 * (; between commands, ~ between triggers, # comments).
 */
public final class ScriptCheck
{
	private ScriptCheck()
	{
	}

	/** a line of a script, with the file and line it is written at */
	private static final class Src
	{
		final String	file;
		final int		line;
		final String	text;

		Src(final String file, final int line, final String text)
		{
			this.file = file;
			this.line = line;
			this.text = text;
		}
	}

	/** what a check of one host's scripts gathers */
	private static final class Run
	{
		final List<String>					problems	= new ArrayList<String>();
		final Map<String, String>			files;
		final Set<String>					functions	= new HashSet<String>();
		final List<Src>						calls		= new ArrayList<Src>();

		Run(final Map<String, String> files)
		{
			this.files = files;
		}

		void problem(final String file, final int line, final String what)
		{
			problems.add(file + ":" + line + " " + what);
		}
	}

	private static final Pattern	CONDITION_FUNC	= Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
	private static final Pattern	VALUE_FUNC		= Pattern.compile("\\$%([A-Za-z_][A-Za-z0-9_]*)\\(");
	private static Set<String>		abilityWords	= null;

	private static Set<String> set(final String[] words)
	{
		final Set<String> s = new HashSet<String>();
		for(final String w : words)
			s.add(w.toUpperCase());
		return s;
	}

	private static final Set<String>	PROGS	= set(ScriptingEngine.progs);
	private static final Set<String>	METHODS	= set(ScriptingEngine.methods);
	private static final Set<String>	FUNCS	= set(ScriptingEngine.funcs);

	/** the words skills are used by (KICK, STEAL), which a script may use as commands */
	private static synchronized Set<String> abilityWords()
	{
		if(abilityWords == null)
		{
			final Set<String> s = new HashSet<String>();
			for(final Enumeration<Ability> e = CMClass.abilities(); e.hasMoreElements();)
			{
				final String[] t = e.nextElement().triggerStrings();
				if(t != null)
				{
					for(final String w : t)
						s.add(w.toUpperCase());
				}
			}
			abilityWords = s;
		}
		return abilityWords;
	}

	/** reads a resource file, once a check */
	private static String fileText(final Run run, final String name)
	{
		if(run.files.containsKey(name))
			return run.files.get(name);
		final CMFile F = new CMFile(Resources.makeFileResourceName(name), null, CMFile.FLAG_FORCEALLOW);
		final String text = (F.exists() && F.canRead()) ? F.text().toString() : null;
		run.files.put(name, text);
		return text;
	}

	/** the text's lines from a character on, as Src, numbered from the given line */
	private static void addLines(final List<Src> out, final String file, final int firstLine, final String text)
	{
		final String[] lines = text.split("\\r\\n|\\n\\r|\\n|\\r", -1);
		for(int i = 0; i < lines.length; i++)
			out.add(new Src(file, firstLine + i, lines[i]));
	}

	private static int lineAt(final String text, final int index)
	{
		int n = 1;
		for(int i = 0; (i < index) && (i < text.length()); i++)
		{
			final char c = text.charAt(i);
			if((c == '\n') || ((c == '\r') && ((i + 1 >= text.length()) || (text.charAt(i + 1) != '\n'))
				&& ((i == 0) || (text.charAt(i - 1) != '\n'))))
				n++;
		}
		return n;
	}

	/** expands LOAD= as the engine does: at the start, or after ; or ~ */
	private static void expand(final Run run, final String file, final String text, final int depth, final List<Src> out)
	{
		if(depth > 10)
		{
			run.problem(file, 1, "LOAD= nested more than 10 deep");
			return;
		}
		final String upper = text.toUpperCase();
		int pos = 0;
		int segmentStart = 0;
		while(pos < text.length())
		{
			final int y = upper.indexOf("LOAD=", pos);
			if(y < 0)
				break;
			final String before = text.substring(segmentStart, y).trim();
			if((before.length() > 0) && (!before.endsWith(";")) && (!before.endsWith("~")))
			{
				pos = y + 5;
				continue;
			}
			addLines(out, file, lineAt(text, segmentStart), text.substring(segmentStart, y));
			final int z = text.indexOf('~', y);
			final String name = ((z > 0) ? text.substring(y + 5, z) : text.substring(y + 5)).trim();
			final String loaded = fileText(run, name);
			if(loaded == null)
				run.problem(file, lineAt(text, y), "LOAD=" + name + ": no such file");
			else
				expand(run, name, loaded, depth + 1, out);
			if(z < 0)
				return;
			pos = z + 1;
			segmentStart = pos;
		}
		addLines(out, file, lineAt(text, segmentStart), text.substring(segmentStart));
	}

	/** a statement of a trigger: its text and where it starts */
	private static final class Stmt
	{
		final Src		at;
		final String	text;
		final boolean	script;

		Stmt(final Src at, final String text, final boolean script)
		{
			this.at = at;
			this.text = text;
			this.script = script;
		}
	}

	/** splits the lines into triggers of statements, as the engine's parser does */
	private static List<List<Stmt>> split(final Run run, final List<Src> lines)
	{
		final List<List<Stmt>> units = new ArrayList<List<Stmt>>();
		List<Stmt> unit = new ArrayList<Stmt>();
		units.add(unit);
		StringBuilder js = null;
		Src jsAt = null;
		for(final Src L : lines)
		{
			final String t = L.text;
			int last = 0;
			boolean comment = false;
			for(int i = 0; i < t.length(); i++)
			{
				final char c = t.charAt(i);
				final boolean escaped = (i > 0) && (t.charAt(i - 1) == '\\');
				if(js != null)
				{
					if((c == '<') && t.regionMatches(true, i, "</SCRIPT>", 0, 9) && (!escaped))
					{
						js.append(t, last, i);
						unit.add(new Stmt(jsAt, js.toString(), true));
						js = null;
						i += 8;
						last = i + 1;
					}
					continue;
				}
				if(comment)
					break;
				if((c == '#') && (!escaped) && (t.substring(last, i).trim().length() == 0))
				{
					comment = true;
					last = t.length();
					break;
				}
				if(((c == ';') || (c == '~')) && (!escaped))
				{
					add(unit, L, t.substring(last, i));
					last = i + 1;
					if(c == '~')
					{
						unit = new ArrayList<Stmt>();
						units.add(unit);
					}
					continue;
				}
				if((c == '<') && t.regionMatches(true, i, "<SCRIPT>", 0, 8) && (!escaped))
				{
					add(unit, L, t.substring(last, i));
					js = new StringBuilder("");
					jsAt = L;
					i += 7;
					last = i + 1;
				}
			}
			if(js != null)
				js.append(t.substring(Math.min(last, t.length()))).append('\n');
			else
			if(!comment)
				add(unit, L, t.substring(Math.min(last, t.length())));
		}
		if(js != null)
			run.problem(jsAt.file, jsAt.line, "<SCRIPT> without </SCRIPT>");
		return units;
	}

	private static void add(final List<Stmt> unit, final Src at, final String text)
	{
		final String s = text.trim();
		if(s.length() > 0)
			unit.add(new Stmt(at, CMStrings.deEscape(s, ";~<#\\"), false));
	}

	private static String firstWord(final String s)
	{
		final int x = s.indexOf(' ');
		return ((x < 0) ? s : s.substring(0, x)).trim().toUpperCase();
	}

	private static String rest(final String s)
	{
		final int x = s.indexOf(' ');
		return (x < 0) ? "" : s.substring(x + 1).trim();
	}

	/** checks the functions named in a condition or a $%FUNC()% value */
	private static void checkFunctions(final Run run, final Stmt S, final String text, final boolean condition)
	{
		if(condition)
		{
			final Matcher m = CONDITION_FUNC.matcher(text.replaceAll("'[^']*'|\"[^\"]*\"|`[^`]*`", "''"));
			while(m.find())
			{
				final String name = m.group(1).toUpperCase();
				if((!FUNCS.contains(name)) && (!name.equals("AND")) && (!name.equals("OR")) && (!name.equals("NOT")))
					run.problem(S.at.file, S.at.line, "unknown function " + name + "()");
			}
		}
		final Matcher v = VALUE_FUNC.matcher(text);
		while(v.find())
		{
			final String name = v.group(1).toUpperCase();
			if(!FUNCS.contains(name))
				run.problem(S.at.file, S.at.line, "unknown function $%" + name + "()%");
		}
	}

	private static boolean balanced(final String s)
	{
		int depth = 0;
		for(int i = 0; i < s.length(); i++)
		{
			if(s.charAt(i) == '(')
				depth++;
			else
			if(s.charAt(i) == ')')
			{
				if(--depth < 0)
					return false;
			}
		}
		return depth == 0;
	}

	/** checks a room ID argument, when it is written out (no variables) */
	private static void checkRoom(final Run run, final Stmt S, final String cmd, final String arg)
	{
		String id = arg.trim();
		if(id.startsWith("\"") && id.endsWith("\"") && (id.length() > 1))
			id = id.substring(1, id.length() - 1);
		if((id.indexOf('#') <= 0) || (id.indexOf('$') >= 0) || (id.indexOf('%') >= 0))
			return;
		if(CMLib.map().getRoom(id) == null)
			run.problem(S.at.file, S.at.line, cmd + ": no room " + id);
	}

	private static void checkJavaScript(final Run run, final Stmt S)
	{
		final Context cx = Context.enter();
		try
		{
			cx.setLanguageVersion(Context.VERSION_ES6);
			// the text starts with the rest of the <SCRIPT> line
			cx.compileString(S.text.replaceAll("\\r\\n|\\n\\r|\\r", "\n"), S.at.file, S.at.line, null);
		}
		catch(final RhinoException e)
		{
			run.problem(S.at.file, (e.lineNumber() > 0) ? e.lineNumber() : S.at.line, "JavaScript: " + e.details());
		}
		catch(final RuntimeException e)
		{
			run.problem(S.at.file, S.at.line, "JavaScript: " + e.getMessage());
		}
		finally
		{
			Context.exit();
		}
		if((CMProps.getIntVar(CMProps.Int.JSCRIPTS) == CMSecurity.JSCRIPT_REQ_APPROVAL)
		&& (!CMSecurity.isApprovedJScript(new StringBuffer(S.text))))
			run.problem(S.at.file, S.at.line, "<SCRIPT> not approved (MODIFY JSCRIPT)");
	}

	private static void checkUnit(final Run run, final List<Stmt> unit)
	{
		if(unit.isEmpty())
			return;
		final Stmt first = unit.get(0);
		if(first.script)
		{
			run.problem(first.at.file, first.at.line, "<SCRIPT> before any trigger");
			return;
		}
		final String trigger = firstWord(first.text);
		if(!PROGS.contains(trigger))
			run.problem(first.at.file, first.at.line, "unknown trigger " + trigger);
		else
		if(trigger.equals("FUNCTION_PROG"))
			run.functions.add(firstWord(rest(first.text)));
		final LinkedList<Stmt> blocks = new LinkedList<Stmt>();
		final Set<String> words = abilityWords();
		for(int i = 1; i < unit.size(); i++)
		{
			final Stmt S = unit.get(i);
			if(S.script)
			{
				checkJavaScript(run, S);
				continue;
			}
			final String cmd = firstWord(S.text);
			final String args = rest(S.text);
			if((cmd.length() == 0) || (!Character.isLetter(cmd.charAt(0))))
			{
				checkFunctions(run, S, S.text, false);
				continue; // ' for SAY and the like
			}
			if(METHODS.contains(cmd) || cmd.equals("ELSE"))
			{
				if(cmd.equals("IF"))
				{
					blocks.push(S);
					checkFunctions(run, S, args, true);
					if(!balanced(args))
						run.problem(S.at.file, S.at.line, "IF: unbalanced parentheses");
				}
				else
				if(cmd.equals("SWITCH") || cmd.equals("FOR"))
				{
					blocks.push(S);
					checkFunctions(run, S, args, false);
				}
				else
				if(cmd.equals("ELSE") || cmd.equals("ENDIF"))
				{
					if(blocks.isEmpty() || (!firstWord(blocks.peek().text).equals("IF")))
						run.problem(S.at.file, S.at.line, cmd + " without IF");
					else
					if(cmd.equals("ENDIF"))
						blocks.pop();
				}
				else
				if(cmd.equals("CASE") || cmd.equals("DEFAULT") || cmd.equals("ENDSWITCH"))
				{
					if(blocks.isEmpty() || (!firstWord(blocks.peek().text).equals("SWITCH")))
						run.problem(S.at.file, S.at.line, cmd + " without SWITCH");
					else
					if(cmd.equals("ENDSWITCH"))
						blocks.pop();
				}
				else
				if(cmd.equals("NEXT"))
				{
					if(blocks.isEmpty() || (!firstWord(blocks.peek().text).equals("FOR")))
						run.problem(S.at.file, S.at.line, "NEXT without FOR");
					else
						blocks.pop();
				}
				else
				if(cmd.equals("MPWHILE"))
				{
					checkFunctions(run, S, args, true);
					if(!balanced(args))
						run.problem(S.at.file, S.at.line, "MPWHILE: unbalanced parentheses");
				}
				else
				{
					checkFunctions(run, S, args, false);
					if(cmd.equals("MPCALLFUNC"))
						run.calls.add(new Src(S.at.file, S.at.line, firstWord(args)));
					else
					if(cmd.equals("MPGOTO"))
						checkRoom(run, S, cmd, firstArg(args, 0));
					else
					if(cmd.equals("MPTRANSFER") || cmd.equals("MPOLOADROOM"))
						checkRoom(run, S, cmd, firstArg(args, 1));
				}
				continue;
			}
			if(cmd.startsWith("MP"))
			{
				final List<String> could = new ArrayList<String>();
				for(final String m : ScriptingEngine.methods)
				{
					if(m.startsWith(cmd) && (!could.contains(m)))
						could.add(m);
				}
				if(could.isEmpty())
					run.problem(S.at.file, S.at.line, "unknown command " + cmd);
				else
					run.problem(S.at.file, S.at.line, cmd + " is not a command; write " + String.join(" or ", could) + " in full");
				continue;
			}
			checkFunctions(run, S, args, false);
			if((CMClass.findCommandByTrigger(cmd, true) == null)
			&& (CMLib.socials().fetchSocial(cmd, true) == null)
			&& (!words.contains(cmd)))
				run.problem(S.at.file, S.at.line, "unknown command " + cmd);
		}
		for(final Stmt open : blocks)
			run.problem(open.at.file, open.at.line, firstWord(open.text) + " without " + (firstWord(open.text).equals("IF") ? "ENDIF"
				: firstWord(open.text).equals("SWITCH") ? "ENDSWITCH" : "NEXT"));
	}

	/** the n-th word of the arguments, quotes kept together */
	private static String firstArg(final String args, final int n)
	{
		final List<String> parts = CMParms.parse(args);
		return (parts.size() > n) ? parts.get(n) : "";
	}

	/**
	 * Checks a script, as an object holds it (inline, or LOAD=).
	 * @param name what to call the script's own text ("inline", or a file)
	 * @param text the script
	 * @param files the files read so far in this check, by name
	 * @return the problems, "file:line what"
	 */
	public static List<String> checkText(final String name, final String text, final Map<String, String> files)
	{
		final Run run = new Run((files == null) ? new HashMap<String, String>() : files);
		final List<Src> lines = new ArrayList<Src>();
		String t = (text == null) ? "" : text;
		if(t.trim().toUpperCase().startsWith("STATIC="))
			t = t.trim().substring(7);
		expand(run, name, t, 0, lines);
		for(final List<Stmt> unit : split(run, lines))
			checkUnit(run, unit);
		for(final Src call : run.calls)
		{
			if(!run.functions.contains(call.text))
				run.problem(call.file, call.line, "MPCALLFUNC " + call.text + ": no such FUNCTION_PROG");
		}
		return run.problems;
	}

	/**
	 * Checks a script file.
	 * @param file the file, as LOAD= names it
	 * @return the problems, or one saying the file is missing
	 */
	public static List<String> checkFile(final String file)
	{
		final Map<String, String> files = new HashMap<String, String>();
		final CMFile F = new CMFile(Resources.makeFileResourceName(file), null, CMFile.FLAG_FORCEALLOW);
		if(!F.exists())
		{
			final List<String> none = new ArrayList<String>();
			none.add(file + ": no such file");
			return none;
		}
		return checkText(file, F.text().toString(), files);
	}

	/** the scripts of a host: its own and those of Scriptable behaviours */
	private static List<ScriptingEngine> scriptsOf(final PhysicalAgent host)
	{
		final List<ScriptingEngine> list = new ArrayList<ScriptingEngine>();
		for(final Enumeration<ScriptingEngine> e = host.scripts(); e.hasMoreElements();)
			list.add(e.nextElement().baseEngine());
		for(final Enumeration<Behavior> e = host.behaviors(); e.hasMoreElements();)
		{
			final Behavior B = e.nextElement();
			if(B instanceof ScriptingEngine)
				list.add(((ScriptingEngine)B).baseEngine());
		}
		return list;
	}

	/**
	 * Checks a host's scripts.
	 * @param host the host
	 * @param files the files read so far in this check, by name, or null
	 * @return the problems
	 */
	public static List<String> checkHost(final PhysicalAgent host, final Map<String, String> files)
	{
		final Map<String, String> f = (files == null) ? new HashMap<String, String>() : files;
		final List<String> out = new ArrayList<String>();
		for(final ScriptingEngine S : scriptsOf(host))
		{
			final String script = S.getScript();
			if((script == null) || (script.trim().length() == 0))
				continue;
			for(final String p : checkText(host.Name() + " (inline)", script, f))
				out.add(p);
		}
		return out;
	}

	/**
	 * Checks the scripts of an area: the area's, and those of its rooms, their
	 * exits, the mobs and items in them and what the mobs carry.  A file
	 * several load is told of once.
	 * @param A the area
	 * @param files the files read so far in this check, or null
	 * @return the problems, each once
	 */
	public static List<String> checkArea(final Area A, final Map<String, String> files)
	{
		final Map<String, String> f = (files == null) ? new HashMap<String, String>() : files;
		final Set<String> out = new LinkedHashSet<String>();
		out.addAll(checkHost(A, f));
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			out.addAll(checkHost(R, f));
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Exit E = R.getRawExit(d);
				if(E != null)
					out.addAll(checkHost(E, f));
			}
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M == null) || (!M.isMonster()))
					continue;
				out.addAll(checkHost(M, f));
				for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
					out.addAll(checkHost(i.nextElement(), f));
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
				out.addAll(checkHost(i.nextElement(), f));
		}
		return new ArrayList<String>(out);
	}

	/**
	 * Checks the scripts of the whole world.
	 * @return the problems, each once
	 */
	public static List<String> checkAll()
	{
		final Map<String, String> files = new HashMap<String, String>();
		final Set<String> out = new LinkedHashSet<String>();
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
			out.addAll(checkArea(a.nextElement(), files));
		return new ArrayList<String>(out);
	}
}
