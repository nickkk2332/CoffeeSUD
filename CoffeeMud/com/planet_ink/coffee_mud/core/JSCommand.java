package com.planet_ink.coffee_mud.core;

import java.util.*;

import org.mozilla.javascript.*;

import com.planet_ink.coffee_mud.Commands.StdCommand;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.CMObject;

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
 * JS20: a command written in JavaScript: cm.command('dig', {help: 'DIG
 * [direction]', cost: 1, combatCost: 2, words: ['excavate']}, function(mob,
 * args) {...}).  It is a real command, so its cost is taken as a command's
 * is (in turn-based time too); 'dig ?' shows its help.  Made by code, not
 * kept in the save: register it in world/js/init.js's onBoot (or a library
 * init.js requires), and a later one of the same name replaces it.
 */
public class JSCommand extends StdCommand
{
	private static final Map<String, JSCommand>	made	= new HashMap<String, JSCommand>();

	private final String		id;
	private final String[]		words;
	private final String		help;
	private final double		cost;
	private final double		combatCost;
	private final Function		fn;
	private final Scriptable	scope;

	private JSCommand(final String id, final String[] words, final String help, final double cost, final double combatCost, final Function fn, final Scriptable scope)
	{
		this.id = id;
		this.words = words;
		this.help = help;
		this.cost = cost;
		this.combatCost = combatCost;
		this.fn = fn;
		this.scope = scope;
	}

	@Override
	public String ID()
	{
		return id;
	}

	@Override
	public String name()
	{
		return words[0];
	}

	@Override
	public CMObject newInstance()
	{
		return this;
	}

	@Override
	public CMObject copyOf()
	{
		return this;
	}

	@Override
	public String[] getAccessWords()
	{
		return words;
	}

	@Override
	public double actionsCost(final MOB mob, final List<String> cmds)
	{
		return cost;
	}

	@Override
	public double combatActionsCost(final MOB mob, final List<String> cmds)
	{
		return combatCost;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return true;
	}

	@Override
	public boolean canBeOrdered()
	{
		return true;
	}

	/** @return its help, as 'cmd ?' shows it */
	public String help()
	{
		return help;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags) throws java.io.IOException
	{
		final String args = (commands.size() > 1) ? CMParms.combine(commands, 1) : "";
		if(args.trim().equals("?") || args.trim().equalsIgnoreCase("HELP"))
		{
			mob.tell((help.length() > 0) ? help : (words[0].toUpperCase() + ": a command of this world."));
			return false;
		}
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Object r = fn.call(cx, scope, scope, new Object[] { Context.javaToJS(mob, scope), args });
			return !Boolean.FALSE.equals(Context.jsToJava(r, Object.class));
		}
		catch(final Exception e)
		{
			JSRuntime.report("command " + words[0], e);
			return false;
		}
		finally
		{
			Context.exit();
		}
	}

	/**
	 * cm.command: makes (or makes again) a command.
	 * @param name its word
	 * @param opts help, cost, combatCost, words (more words for it), or null
	 * @param fn function(mob, args), false if it did nothing
	 * @param scope its scope
	 * @return its ID
	 */
	public static synchronized String make(final String name, final Scriptable opts, final Function fn, final Scriptable scope)
	{
		if((name == null) || (name.trim().length() == 0) || (name.trim().indexOf(' ') >= 0) || (fn == null))
			throw ScriptRuntime.constructError("Error", "cm.command: a word, and a function(mob, args)");
		final String word = name.trim();
		final List<String> words = new ArrayList<String>();
		words.add(word.toUpperCase());
		String help = "";
		double cost = 1.0, combatCost = 1.0;
		if(opts != null)
		{
			final Object h = ScriptableObject.getProperty(opts, "help");
			if(h instanceof CharSequence)
				help = h.toString();
			final Object c = ScriptableObject.getProperty(opts, "cost");
			if(c instanceof Number)
				cost = ((Number)c).doubleValue();
			final Object cc = ScriptableObject.getProperty(opts, "combatCost");
			combatCost = (cc instanceof Number) ? ((Number)cc).doubleValue() : cost;
			final Object w = ScriptableObject.getProperty(opts, "words");
			if(w instanceof Scriptable)
			{
				for(final Object id : ((Scriptable)w).getIds())
				{
					final Object v = ((Scriptable)w).get(((Number)id).intValue(), (Scriptable)w);
					if(v instanceof CharSequence)
						words.add(v.toString().trim().toUpperCase());
				}
			}
		}
		final String id = "JS" + word.toUpperCase();
		final JSCommand was = made.remove(id);
		if(was != null)
			CMClass.delClass(CMClass.CMObjectType.COMMAND, was);
		final JSCommand C = new JSCommand(id, words.toArray(new String[0]), help, cost, combatCost, fn, scope);
		made.put(id, C);
		CMClass.addClass(CMClass.CMObjectType.COMMAND, C);
		return id;
	}

	/**
	 * Takes a command away.
	 * @param name its word
	 * @return true if there was one
	 */
	public static synchronized boolean unmake(final String name)
	{
		final JSCommand was = made.remove("JS" + name.trim().toUpperCase());
		if(was == null)
			return false;
		CMClass.delClass(CMClass.CMObjectType.COMMAND, was);
		CMClass.reloadCommandWords();
		return true;
	}

	/** @return the words of the commands made */
	public static synchronized List<String> names()
	{
		final List<String> out = new ArrayList<String>();
		for(final JSCommand C : made.values())
			out.add(C.words[0].toLowerCase());
		Collections.sort(out);
		return out;
	}
}
