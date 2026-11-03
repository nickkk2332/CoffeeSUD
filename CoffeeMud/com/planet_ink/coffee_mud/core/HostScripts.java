package com.planet_ink.coffee_mud.core;

import java.util.Hashtable;
import java.util.Map;
import java.util.WeakHashMap;

import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.core.interfaces.Environmental;
import com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent;
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
 * P21: scripts that come with a thing rather than sit on it.  A race's or
 * class's SCRIPT runs on each mob of it as a hidden effect would: an engine
 * per mob, made when first needed and never saved.  A skill's or poison's
 * SCRIPT is one engine for its ID, whose ONFOUND, ONCRAFTED, ONAFFECT and
 * other functions are called (ScriptHooks).
 */
public class HostScripts
{
	private final Map<Object, ScriptingEngine> engines = new WeakHashMap<Object, ScriptingEngine>();
	private String script = "";

	private static final Map<String, Object[]> shared = new Hashtable<String, Object[]>();

	/**
	 * @param s the script (MOBPROG text, or LOAD=file)
	 */
	public void setScript(final String s)
	{
		final String n = (s == null) ? "" : s;
		if(!n.equals(script))
		{
			synchronized(engines)
			{
				engines.clear();
			}
			script = n;
		}
	}

	/** @return the script */
	public String script()
	{
		return script;
	}

	private static ScriptingEngine make(final String script)
	{
		final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
		if(S != null)
		{
			S.setSavable(false);
			S.setScript(script);
			S.preApproveScripts();
		}
		return S;
	}

	/**
	 * @param host a mob
	 * @return its engine for the script, or null if there is none
	 */
	public ScriptingEngine engine(final Object host)
	{
		if((host == null) || (script.trim().length() == 0))
			return null;
		synchronized(engines)
		{
			ScriptingEngine S = engines.get(host);
			if(S == null)
			{
				S = make(script);
				if(S != null)
					engines.put(host, S);
			}
			return S;
		}
	}

	/**
	 * From the race's or class's own tick.
	 * @param ticking the mob
	 * @param tickID the tick
	 */
	public void tick(final Tickable ticking, final int tickID)
	{
		final ScriptingEngine S = engine(ticking);
		if(S != null)
			S.tick(ticking, tickID);
	}

	/**
	 * @param host the mob
	 * @param msg a message
	 * @return false if the script cancelled it
	 */
	public boolean okMessage(final Environmental host, final CMMsg msg)
	{
		final ScriptingEngine S = engine(host);
		return (S == null) || S.okMessage(host, msg);
	}

	/**
	 * @param host the mob
	 * @param msg a message
	 */
	public void executeMsg(final Environmental host, final CMMsg msg)
	{
		final ScriptingEngine S = engine(host);
		if(S != null)
			S.executeMsg(host, msg);
	}

	/**
	 * The one engine of a skill's or poison's script, by its ID, remade when
	 * the script changes.
	 * @param key the skill's ID
	 * @param script its script
	 * @return the engine, or null for no script
	 */
	public static ScriptingEngine shared(final String key, final String script)
	{
		if((key == null) || (script == null) || (script.trim().length() == 0))
			return null;
		final Object[] o = shared.get(key);
		if((o != null) && script.equals(o[0]))
			return (ScriptingEngine)o[1];
		final ScriptingEngine S = make(script);
		shared.put(key, new Object[] { script, S });
		return S;
	}

	/**
	 * Calls a function of a skill's or poison's script, $o the item.
	 * @param key the skill's ID
	 * @param script its script
	 * @param host the mob it is on
	 * @param fn ONFOUND, ONCRAFTED, ONFAILED ...
	 * @param target $t
	 * @param item $o, or null
	 * @return what the function returned, or null
	 */
	public static String call(final String key, final String script, final PhysicalAgent host, final String fn, final Environmental target,
							  final com.planet_ink.coffee_mud.Items.interfaces.Item item)
	{
		final ScriptingEngine S = shared(key, script);
		if(S == null)
			return null;
		return ScriptHooks.call(S, host, fn, (host instanceof com.planet_ink.coffee_mud.MOBS.interfaces.MOB) ? (com.planet_ink.coffee_mud.MOBS.interfaces.MOB)host : null,
								target, item, (item != null) ? item.name() : "");
	}
}
