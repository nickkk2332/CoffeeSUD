package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import org.mozilla.javascript.*;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.*;

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
 * JS20: cm.verb(thing, 'pull lever', function(mob, rest) {...}): a phrase
 * that works near one room, item or mob only.  It is looked at before the
 * game's own commands, as VERB_PROG is: the first word must be the
 * command's, and the phrase's other words must all be among what follows
 * (so 'pull the lever' is 'pull lever').  Typed where none of its things is
 * within reach, the phrase gets "You can't do that here." if its first word
 * is no command of the game's.  Made by code: register in init.js's onBoot.
 */
public final class JSVerbs
{
	private static final class Verb
	{
		final PhysicalAgent	host;
		final String		verb;
		final String[]		words;
		final Function		fn;
		final Scriptable	scope;

		Verb(final PhysicalAgent host, final String verb, final String[] words, final Function fn, final Scriptable scope)
		{
			this.host = host;
			this.verb = verb;
			this.words = words;
			this.fn = fn;
			this.scope = scope;
		}
	}

	private static final List<Verb>	verbs	= new CopyOnWriteArrayList<Verb>();

	private JSVerbs()
	{
	}

	/** @return true if any phrase is made */
	public static boolean any()
	{
		return !verbs.isEmpty();
	}

	/**
	 * cm.verb.
	 * @param host the room, item or mob it works near
	 * @param phrase its words, the first the command's
	 * @param fn function(mob, rest), false to let the game go on
	 * @param scope its scope
	 */
	public static void add(final PhysicalAgent host, final String phrase, final Function fn, final Scriptable scope)
	{
		final String[] w = (phrase == null) ? new String[0] : phrase.trim().toLowerCase().split("\\s+");
		if((host == null) || (fn == null) || (w.length == 0) || (w[0].length() == 0))
			throw ScriptRuntime.constructError("Error", "cm.verb: a thing, a phrase, and a function(mob, rest)");
		remove(host, phrase);
		verbs.add(new Verb(host, w[0], Arrays.copyOfRange(w, 1, w.length), fn, scope));
	}

	/**
	 * @param host the thing
	 * @param phrase the phrase
	 * @return how many were taken away
	 */
	public static int remove(final PhysicalAgent host, final String phrase)
	{
		final String p = (phrase == null) ? "" : String.join(" ", phrase.trim().toLowerCase().split("\\s+"));
		int n = 0;
		for(final Verb V : verbs)
		{
			final String vp = V.verb + ((V.words.length > 0) ? (" " + String.join(" ", V.words)) : "");
			if((V.host == host) && vp.equals(p))
			{
				verbs.remove(V);
				n++;
			}
		}
		return n;
	}

	/** takes every phrase away (a test, or a new world) */
	public static void clear()
	{
		verbs.clear();
	}

	/**
	 * From ScriptEvents.verb: a phrase of what is within reach.
	 * @param mob who typed it
	 * @param verb the first word
	 * @param rest the rest
	 * @param reach what is within reach
	 * @return true if it was handled
	 */
	public static boolean handle(final MOB mob, final String verb, final String rest, final Collection<PhysicalAgent> reach)
	{
		final String v = verb.toLowerCase();
		final Set<String> said = new HashSet<String>(Arrays.asList(rest.toLowerCase().split("\\s+")));
		boolean known = false;
		for(final Verb V : verbs)
		{
			if(V.host.amDestroyed())
			{
				verbs.remove(V);
				continue;
			}
			if(!V.verb.equals(v))
				continue;
			boolean all = true;
			for(final String w : V.words)
				all = all && said.contains(w);
			if(!all)
				continue;
			known = true;
			if(!reach.contains(V.host))
				continue;
			final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
			try
			{
				final Object r = V.fn.call(cx, V.scope, V.scope, new Object[] { Context.javaToJS(mob, V.scope), rest });
				if(!Boolean.FALSE.equals(Context.jsToJava(r, Object.class)))
					return true;
			}
			catch(final Exception e)
			{
				JSRuntime.report("cm.verb '" + V.verb + " " + String.join(" ", V.words) + "'", e);
				return true;
			}
			finally
			{
				Context.exit();
			}
		}
		if(known && (CMClass.findCommandByTrigger(verb, true) == null) && (CMLib.socials().fetchSocial(verb.toUpperCase(), true) == null))
		{
			mob.tell("You can't do that here.");
			return true;
		}
		return false;
	}
}
