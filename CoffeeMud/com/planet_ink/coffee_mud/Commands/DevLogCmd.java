package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.DevLog.Level;
import com.planet_ink.coffee_mud.core.save.SaveGame;
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
 * DEVLOG ON [ERROR|WARN|INFO|DEBUG] [MODULE mask] / OFF / LAST [n] [level]
 * [MODULE mask] / CLEAR: the lines the engine logs, told to the builder as
 * they happen ({@link DevLog}), in builder mode or a DEV=1 game.
 */
public class DevLogCmd extends StdCommand
{
	public DevLogCmd()
	{
	}

	private final String[] access = I(new String[] { "DEVLOG" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	private static final String USAGE = "DEVLOG ON (ERROR/WARN/INFO/DEBUG) (MODULE [MASK]), DEVLOG OFF, DEVLOG LAST ([N]) ([LEVEL]) (MODULE [MASK]), DEVLOG CLEAR, DEVLOG WINDOW ([LEVEL]) (MODULE [MASK]), DEVLOG WINDOW OFF";

	/** reads [level] [MODULE mask] from the words after the verb: [level, mask, n] */
	private static Object[] options(final List<String> words, final Level defLevel)
	{
		Level level = defLevel;
		String mask = "*";
		int n = 20;
		for(int i = 0; i < words.size(); i++)
		{
			final String w = words.get(i);
			final Level L = (Level)CMath.s_valueOf(Level.class, w.toUpperCase());
			if(L != null)
				level = L;
			else
			if(w.equalsIgnoreCase("WARNING"))
				level = Level.WARN;
			else
			if((w.equalsIgnoreCase("MODULE") || w.equalsIgnoreCase("MODULES")) && (i < words.size() - 1))
				mask = words.get(++i);
			else
			if(CMath.isInteger(w))
				n = CMath.s_int(w);
			else
				return null;
		}
		return new Object[] { level, mask, Integer.valueOf(n) };
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 2)
		{
			mob.tell(L("DEVLOG is @x1.  @x2", DevLog.setting(), USAGE));
			return false;
		}
		final String verb = commands.get(1).toUpperCase();
		final List<String> rest = new ArrayList<String>(commands.subList(2, commands.size()));
		if(verb.equals("ON"))
		{
			final Object[] o = options(rest, Level.WARN);
			if(o == null)
			{
				mob.tell(L(USAGE));
				return false;
			}
			DevLog.watch(mob, (Level)o[0], (String)o[1]);
			mob.tell(L("DEVLOG: you are told each line logged at @x1 or worse, from @x2.", ((Level)o[0]).name(),
					((String)o[1]).equals("*") ? L("any module") : (String)o[1]));
		}
		else
		if(verb.equals("OFF"))
		{
			DevLog.unwatch();
			mob.tell(L("DEVLOG is off."));
		}
		else
		if(verb.equals("LAST"))
		{
			final Object[] o = options(rest, Level.DEBUG);
			if(o == null)
			{
				mob.tell(L(USAGE));
				return false;
			}
			final List<String> lines = DevLog.last(((Integer)o[2]).intValue(), (Level)o[0], (String)o[1]);
			if(lines.isEmpty())
				mob.tell(L("DEVLOG: nothing logged lately."));
			else
				mob.tell(String.join("\n\r", lines));
		}
		else
		if(verb.equals("CLEAR"))
		{
			DevLog.clear();
			mob.tell(L("DEVLOG: the lines kept are forgotten."));
		}
		else
		if(verb.equals("WINDOW"))
		{
			if(rest.size() == 1 && rest.get(0).equalsIgnoreCase("OFF"))
			{
				DevLog.closeWindow();
				mob.tell(L("The developer log window is closed."));
			}
			else
			{
				final Object[] o = options(rest, Level.WARN);
				if(o == null) mob.tell(L(USAGE));
				else mob.tell(DevLog.openWindow((Level)o[0], (String)o[1])
					? L("The developer log window is open.")
					: L("No display is available. DEVLOG ON or LAST shows the log in this terminal."));
			}
		}
		else
			mob.tell(L(USAGE));
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
