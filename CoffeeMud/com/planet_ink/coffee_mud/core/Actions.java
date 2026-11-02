package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * I9: the actions of the puzzle properties (ACTION_up=, ONFULL=, ONCLEAR=):
 * script commands, a semicolon between them, run for the object as its own
 * script would, $n the one who did it.  MP may be left off a command: "door
 * open Gate#3 east; signal WORLD gate1 on; unhide 'a narrow stair'" runs
 * MPDOOR, MPSIGNAL and MPUNHIDE.
 */
public final class Actions
{
	private Actions()
	{
	}

	/**
	 * The lines, MP put back where it was left off.
	 * @param actions the actions, a semicolon (or a line) between them
	 * @return the script lines
	 */
	public static String lines(final String actions)
	{
		final StringBuilder out = new StringBuilder();
		for(final String raw : actions.split("[;\\n]"))
		{
			final String l = raw.trim();
			if(l.length() == 0)
				continue;
			final int sp = l.indexOf(' ');
			final String w = ((sp < 0) ? l : l.substring(0, sp)).toUpperCase();
			if((!CMParms.contains(ScriptingEngine.methods, w)) && CMParms.contains(ScriptingEngine.methods, "MP" + w))
				out.append("MP").append(l).append("\n");
			else
				out.append(l).append("\n");
		}
		return out.toString();
	}

	/**
	 * Runs actions for an object.
	 * @param host the object: an item, a mob, a room or an area (an exit's
	 *        run for the room of the one who did it)
	 * @param actor who did it, $n, or null
	 * @param actions the actions
	 */
	public static void run(PhysicalAgent host, final MOB actor, final String actions)
	{
		if((actions == null) || (actions.trim().length() == 0))
			return;
		if((host instanceof Exit) || (host == null))
			host = (actor != null) ? actor.location() : null;
		if(host == null)
			return;
		final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
		S.setSavable(false);
		S.setVarScope("");
		S.setScript("FUNCTION_PROG PUZZLE_ACT\n" + lines(actions) + "~\n");
		S.callFunction(host, "PUZZLE_ACT", "", actor);
	}
}
