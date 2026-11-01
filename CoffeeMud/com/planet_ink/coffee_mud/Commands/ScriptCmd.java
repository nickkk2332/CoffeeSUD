package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.DefaultScriptingEngine;
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
 * SCRIPT: runs and inspects a host's script triggers on demand, to test them
 * without waiting for the moment they are for.  The target is a mob or item
 * here (or carried), ROOM, AREA, or name@room.
 * <ul>
 * <li>SCRIPT LIST target: its triggers, with the file and line of each.</li>
 * <li>SCRIPT FIRE target trigger [words] [AS who] [WITH item]: runs the
 * triggers of a kind now, as if who had caused them (SPEECH hello, GIVE WITH
 * a sword, DEATH, GREET, TIME_PROG 5).</li>
 * <li>SCRIPT CALL target function [arguments]: runs a FUNCTION_PROG.</li>
 * <li>SCRIPT RESET target [ONCE|DELAY|TIME|ALL]: lets triggers that ran run
 * again.</li>
 * <li>SCRIPT DISABLE|ENABLE target [trigger]: turns triggers off or on.</li>
 * <li>SCRIPT ALARMS target [LIST|CLEAR]: what is queued to run.</li>
 * </ul>
 * See the methods of {@link DefaultScriptingEngine} each calls.
 */
public class ScriptCmd extends StdCommand
{
	public ScriptCmd()
	{
	}

	private final String[] access = I(new String[] { "SCRIPT" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	private static final String USAGE = "SCRIPT LIST|FIRE|CALL|RESET|DISABLE|ENABLE|ALARMS [TARGET] ...  (AHELP SCRIPT)";

	/**
	 * Finds a target as STAT would: ROOM, AREA, name@room, or a mob or item
	 * here or carried.
	 * @param mob the one looking
	 * @param name the target's name
	 * @return the target, or null
	 */
	public static PhysicalAgent findTarget(final MOB mob, final String name)
	{
		Room R = mob.location();
		String what = name;
		final int at = name.lastIndexOf('@');
		if(at > 0)
		{
			R = CMLib.map().getRoom(name.substring(at + 1));
			what = name.substring(0, at);
		}
		if(R == null)
			return null;
		if(what.equalsIgnoreCase("ROOM") || what.equalsIgnoreCase("HERE"))
			return R;
		if(what.equalsIgnoreCase("AREA"))
			return R.getArea();
		Environmental E = R.fetchFromRoomFavorMOBs(null, what);
		if((E == null) && (R == mob.location()))
			E = mob.findItem(null, what);
		return (E instanceof PhysicalAgent) ? (PhysicalAgent)E : null;
	}

	/**
	 * The scripting engines a host has, those added and those of Scriptable
	 * behaviours, as their own engines.
	 * @param host the host
	 * @return the engines
	 */
	public static List<DefaultScriptingEngine> engines(final PhysicalAgent host)
	{
		final List<DefaultScriptingEngine> list = new ArrayList<DefaultScriptingEngine>();
		for(final Enumeration<ScriptingEngine> e = host.scripts(); e.hasMoreElements();)
		{
			final ScriptingEngine S = e.nextElement().baseEngine();
			if((S instanceof DefaultScriptingEngine) && (!list.contains(S)))
				list.add((DefaultScriptingEngine)S);
		}
		for(final Enumeration<Behavior> e = host.behaviors(); e.hasMoreElements();)
		{
			final Behavior B = e.nextElement();
			if(B instanceof ScriptingEngine)
			{
				final ScriptingEngine S = ((ScriptingEngine)B).baseEngine();
				if((S instanceof DefaultScriptingEngine) && (!list.contains(S)))
					list.add((DefaultScriptingEngine)S);
			}
		}
		return list;
	}

	/** splits words at AS and WITH: [words, who, item] */
	private static String[] splitAsWith(final List<String> words)
	{
		final String[] parts = new String[] { "", "", "" };
		int part = 0;
		for(final String w : words)
		{
			if(w.equalsIgnoreCase("AS"))
				part = 1;
			else
			if(w.equalsIgnoreCase("WITH"))
				part = 2;
			else
				parts[part] = (parts[part].length() == 0) ? w : (parts[part] + " " + w);
		}
		return parts;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 3)
		{
			mob.tell(L(USAGE));
			return false;
		}
		final String verb = commands.get(1).toUpperCase();
		final String targetName = commands.get(2);
		final PhysicalAgent host = findTarget(mob, targetName);
		if(host == null)
		{
			mob.tell(L("There is no '@x1' here (a mob or item here, ROOM, AREA, or name@room).", targetName));
			return false;
		}
		final List<DefaultScriptingEngine> engines = engines(host);
		if(engines.isEmpty())
		{
			mob.tell(L("@x1 has no scripts.", host.Name()));
			return false;
		}
		final List<String> rest = new ArrayList<String>(commands.subList(3, commands.size()));
		final StringBuilder out = new StringBuilder("");
		if(verb.equals("LIST"))
		{
			out.append(L("Triggers of @x1:", host.Name()));
			int n = 0;
			for(final DefaultScriptingEngine S : engines)
			{
				if(engines.size() > 1)
					out.append("\n\r").append(L("-- script @x1 --", "" + (++n)));
				for(final String line : S.triggerList(host))
					out.append("\n\r").append(line);
			}
		}
		else
		if(verb.equals("FIRE"))
		{
			if(rest.isEmpty())
			{
				mob.tell(L("SCRIPT FIRE [TARGET] [TRIGGER] ([WORDS]) (AS [WHO]) (WITH [ITEM])"));
				return false;
			}
			final String trigger = rest.remove(0);
			if(DefaultScriptingEngine.triggerCodeOf(trigger) <= 0)
			{
				mob.tell(L("'@x1' is not a trigger (SPEECH_PROG, GREET_PROG, TIME_PROG...).", trigger));
				return false;
			}
			final String[] parts = splitAsWith(rest);
			MOB source = mob;
			if(parts[1].length() > 0)
			{
				source = (mob.location() == null) ? null : mob.location().fetchInhabitant(parts[1]);
				if(source == null)
					source = CMLib.players().getPlayer(parts[1]);
				if(source == null)
				{
					mob.tell(L("There is no '@x1' here to be the one who does it.", parts[1]));
					return false;
				}
			}
			Item item = null;
			if(parts[2].length() > 0)
			{
				item = mob.findItem(null, parts[2]);
				if((item == null) && (mob.location() != null))
					item = mob.location().findItem(null, parts[2]);
				if(item == null)
				{
					mob.tell(L("There is no '@x1' here.", parts[2]));
					return false;
				}
			}
			int n = 0;
			for(final DefaultScriptingEngine S : engines)
				n += Math.max(0, S.fireTrigger(host, trigger, parts[0], source, item));
			final String name = ScriptingEngine.progs[DefaultScriptingEngine.triggerCodeOf(trigger) - 1];
			if(n == 0)
				out.append(L("@x1 has no @x2@x3.", host.Name(), name, (parts[0].length() > 0) ? L(" answering '@x1'", parts[0]) : ""));
			else
				out.append(L("Ran @x1 @x2 of @x3, as @x4.", "" + n, name, host.Name(), source.Name()));
		}
		else
		if(verb.equals("CALL"))
		{
			if(rest.isEmpty())
			{
				mob.tell(L("SCRIPT CALL [TARGET] [FUNCTION] ([ARGUMENTS])"));
				return false;
			}
			final String function = rest.remove(0);
			final String args = CMParms.combine(rest, 0);
			String result = null;
			for(final DefaultScriptingEngine S : engines)
			{
				result = S.callFunction(host, function, args, mob);
				if(result != null)
					break;
			}
			if(result == null)
				out.append(L("@x1 has no function @x2.", host.Name(), function.toUpperCase()));
			else
				out.append(L("@x1 returned '@x2'.", function.toUpperCase(), result));
		}
		else
		if(verb.equals("RESET"))
		{
			final String what = rest.isEmpty() ? "ALL" : rest.get(0).toUpperCase();
			String done = "";
			for(final DefaultScriptingEngine S : engines)
				done = S.resetTriggers(what);
			if(done.length() == 0)
			{
				mob.tell(L("SCRIPT RESET [TARGET] (ONCE/DELAY/TIME/ALL)"));
				return false;
			}
			out.append(L("@x1 will run its @x2 again.", host.Name(), done));
		}
		else
		if(verb.equals("DISABLE") || verb.equals("ENABLE"))
		{
			final boolean on = verb.equals("ENABLE");
			final String trigger = rest.isEmpty() ? null : rest.get(0);
			final Set<String> names = new TreeSet<String>();
			for(final DefaultScriptingEngine S : engines)
			{
				final List<String> changed = S.enableTrigger(host, trigger, on);
				if(changed == null)
				{
					mob.tell(L("'@x1' is not a trigger.", trigger));
					return false;
				}
				names.addAll(changed);
			}
			if(names.isEmpty())
				out.append(L("@x1 has no triggers.", host.Name()));
			else
				out.append(L("@x1: @x2 @x3.", host.Name(), String.join(", ", names), on ? L("on") : L("off")));
		}
		else
		if(verb.equals("ALARMS"))
		{
			final boolean clear = (!rest.isEmpty()) && rest.get(0).equalsIgnoreCase("CLEAR");
			if(clear)
			{
				int n = 0;
				for(final DefaultScriptingEngine S : engines)
					n += S.clearPendingResponses();
				out.append(L("@x1: @x2 queued to run forgotten.", host.Name(), "" + n));
			}
			else
			{
				final List<String> lines = new ArrayList<String>();
				for(final DefaultScriptingEngine S : engines)
					lines.addAll(S.pendingResponses());
				if(lines.isEmpty())
					out.append(L("@x1 has nothing queued to run.", host.Name()));
				else
					out.append(L("Queued to run for @x1:", host.Name())).append("\n\r").append(String.join("\n\r", lines));
			}
		}
		else
		{
			mob.tell(L(USAGE));
			return false;
		}
		mob.tell(out.toString());
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
		return CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDMOBS)
			|| CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDITEMS)
			|| CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS);
	}
}
