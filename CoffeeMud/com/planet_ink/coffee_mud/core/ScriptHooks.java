package com.planet_ink.coffee_mud.core;

import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine.MPContext;
import com.planet_ink.coffee_mud.core.interfaces.Environmental;
import com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
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
 * P3: named hooks in a generic ability's or trap's SCRIPT, called as
 * GenCommand calls EXECUTE: a FUNCTION_PROG of that name runs, with $n the
 * one acting, $t the target and $0... the words given.  CANCAST may RETURN
 * false to refuse a cast.  Also the target a cast mask's +IF and -IF see as $t.
 */
public final class ScriptHooks
{
	private ScriptHooks()
	{
	}

	/** what a skill that runs ONLEARN is, for the mob that learns it */
	public interface Learner
	{
		/**
		 * @param mob the mob that has just gained it
		 */
		public void learnedBy(MOB mob);
	}

	private static final ThreadLocal<Environmental> maskTarget = new ThreadLocal<Environmental>();

	/** @return the target a cast mask is being checked against, or null */
	public static Environmental maskTarget()
	{
		return maskTarget.get();
	}

	/**
	 * A mask check with a target, which the mask's +IF and -IF see as $t.
	 * @param mask the mask
	 * @param mob who is checked
	 * @param target the target
	 * @return whether they pass
	 */
	public static boolean maskCheck(final String mask, final MOB mob, final Environmental target)
	{
		final Environmental was = maskTarget.get();
		maskTarget.set(target);
		try
		{
			return CMLib.masking().maskCheck(mask, mob, true);
		}
		finally
		{
			maskTarget.set(was);
		}
	}

	/**
	 * @param S a script engine, or null
	 * @param name a function's name
	 * @return whether it has that FUNCTION_PROG
	 */
	public static boolean has(final ScriptingEngine S, final String name)
	{
		return (S != null) && S.isFunc(name);
	}

	/**
	 * Runs a named hook, if the script has it.
	 * @param S the script engine, or null
	 * @param host what the script is on, or the one acting
	 * @param name the hook, as ONCAST
	 * @param source $n, or null for the host's own mob
	 * @param target $t, or null
	 * @param args $0..., the words given
	 * @return what it returned, or null if there is no such hook
	 */
	public static String call(final ScriptingEngine S, final PhysicalAgent host, final String name, final MOB source, final Environmental target, final String args)
	{
		return call(S, host, name, source, target, (host instanceof Item) ? (Item)host : null, args);
	}

	/**
	 * Runs a named hook, with $o an item (P21: the item found or made).
	 * @param S the script engine, or null
	 * @param host what the script is on, or the one acting
	 * @param name the hook
	 * @param source $n, or null for the host's own mob
	 * @param target $t, or null
	 * @param item $o, or null
	 * @param args $0..., the words given
	 * @return what it returned, or null if there is no such hook
	 */
	public static String call(final ScriptingEngine S, final PhysicalAgent host, final String name, final MOB source, final Environmental target, final Item item, final String args)
	{
		if((host == null) || !has(S, name))
			return null;
		final MOB monster = (host instanceof MOB) ? (MOB)host : S.getMakeMOB(host);
		final Object[] tmp = new Object[ScriptingEngine.SPECIAL_NUM_OBJECTS];
		final java.util.List<String> words = CMParms.parse((args == null) ? "" : args);
		tmp[0] = "" + words.size();
		for(int i = 1; i < tmp.length; i++)
			tmp[i] = (i <= words.size()) ? words.get(i - 1) : "";
		final MPContext ctx = new MPContext(host, monster, (source != null) ? source : monster, target,
			item, null, (args == null) ? "" : args, tmp);
		return S.callFunc(name, (args == null) ? "" : args, ctx);
	}

	/**
	 * @param r what a hook returned
	 * @return whether it refused: false, 0 or CANCEL
	 */
	public static boolean refused(final String r)
	{
		if(r == null)
			return false;
		final String t = r.trim();
		return t.equalsIgnoreCase("false") || t.equals("0") || t.equalsIgnoreCase("CANCEL");
	}
}
