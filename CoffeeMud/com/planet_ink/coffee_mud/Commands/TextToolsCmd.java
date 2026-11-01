package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.Room.VariationCode;
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
 * PREVIEW and CHECKTEXT (DT17), for a room's &lt;VARIES&gt; text.
 * <ul>
 * <li>PREVIEW (ROOM) [DAY|NIGHT|DAWN|DUSK] [SPRING|SUMMER|FALL|WINTER]
 * [RAIN|SNOW|CLEAR|...] [FROM direction|FROM UNKNOWN] [VISITED|FIRSTVISIT]
 * [AS character] [VAR name=value]: the room's title and description as they
 * read under those conditions, the rest as they are; nothing changes.</li>
 * <li>CHECKTEXT [ROOM|AREA|WORLD]: what in the rooms' texts would print as
 * text instead of varying ({@link TextCheck}).</li>
 * </ul>
 */
public class TextToolsCmd extends StdCommand
{
	public TextToolsCmd()
	{
	}

	private final String[] access = I(new String[] { "PREVIEW", "CHECKTEXT" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	private static final String PREVIEW_USAGE = "PREVIEW (ROOM) (DAY/NIGHT/DAWN/DUSK) (SPRING/SUMMER/FALL/WINTER) (RAIN/SNOW/CLEAR...) "
											+ "(FROM [DIRECTION]) (VISITED/FIRSTVISIT) (AS [CHARACTER]) (VAR [NAME]=[VALUE])";

	/**
	 * A room's title and description under conditions.
	 * @param mob the one asking (the reader, unless AS)
	 * @param R the room
	 * @param words the conditions
	 * @return the text, or a line saying what could not be read
	 */
	public static String preview(final MOB mob, final Room R, final List<String> words)
	{
		final TextPreview P = new TextPreview();
		MOB reader = mob;
		final Map<String, String> vars = new LinkedHashMap<String, String>();
		final List<String> said = new ArrayList<String>();
		for(int i = 0; i < words.size(); i++)
		{
			final String w = words.get(i).toUpperCase();
			if(w.equals("ROOM") || w.equals("HERE"))
				continue;
			if(w.equals("VISITED"))
			{
				P.visited = 1;
				said.add("visited");
				continue;
			}
			if(w.equals("FIRSTVISIT") || w.equals("NOTVISITED"))
			{
				P.visited = 0;
				said.add("a first visit");
				continue;
			}
			if(w.equals("FROM") && (i < words.size() - 1))
			{
				final String d = words.get(++i);
				if(d.equalsIgnoreCase("UNKNOWN") || d.equalsIgnoreCase("NOWHERE"))
				{
					P.from = -1;
					said.add("from nowhere known");
					continue;
				}
				final int dir = CMLib.directions().getGoodDirectionCode(d);
				if(dir < 0)
					return "There is no direction '" + d + "'.";
				P.from = dir;
				said.add("from the " + CMLib.directions().getDirectionName(dir).toLowerCase());
				continue;
			}
			if(w.equals("AS") && (i < words.size() - 1))
			{
				final String who = words.get(++i);
				MOB M = (R == null) ? null : R.fetchInhabitant(who);
				if(M == null)
					M = CMLib.players().getLoadPlayer(who);
				if(M == null)
					return "There is no character '" + who + "'.";
				reader = M;
				said.add("as " + M.Name());
				continue;
			}
			if(w.equals("VAR") && (i < words.size() - 1))
			{
				final String nv = words.get(++i);
				final int x = nv.indexOf('=');
				if(x <= 0)
					return "VAR [NAME]=[VALUE]";
				vars.put(nv.substring(0, x).trim().toUpperCase(), nv.substring(x + 1).trim());
				continue;
			}
			final VariationCode code = (VariationCode)CMath.s_valueOf(VariationCode.class, w);
			if(code == null)
				return "'" + words.get(i) + "' is no condition.  " + PREVIEW_USAGE;
			switch(code.c)
			{
			case 'C':
				P.timeOfDay = code.num;
				break;
			case 'S':
				P.season = code.num;
				break;
			case 'W':
				P.weather = code.num;
				break;
			default:
				return "'" + words.get(i) + "' is no condition PREVIEW sets.  " + PREVIEW_USAGE;
			}
			said.add(w.toLowerCase());
		}
		// script variables, set while reading and put back after
		final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
		final Map<String, String> before = new LinkedHashMap<String, String>();
		for(final Map.Entry<String, String> e : vars.entrySet())
		{
			before.put(e.getKey(), S.getVar(reader.Name(), e.getKey()));
			S.setVar(reader.Name(), e.getKey(), e.getValue());
			said.add(e.getKey() + "=" + e.getValue());
		}
		final StringBuilder out = new StringBuilder("");
		final MOB asM = reader;
		try
		{
			P.run(new Runnable()
			{
				@Override
				public void run()
				{
					out.append("^H").append(R.displayText(asM)).append("^N\n\r").append(R.description(asM));
				}
			});
		}
		finally
		{
			for(final Map.Entry<String, String> e : before.entrySet())
				S.setVar(asM.Name(), e.getKey(), (e.getValue() == null) ? "" : e.getValue());
		}
		return "PREVIEW " + (said.isEmpty() ? "as things are" : String.join(", ", said)) + ":\n\r" + out.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final String word = commands.get(0).toUpperCase();
		final List<String> rest = new ArrayList<String>(commands.subList(1, commands.size()));
		if(word.equals("PREVIEW"))
		{
			if((!rest.isEmpty()) && (rest.get(0).equalsIgnoreCase("ITEM") || rest.get(0).equalsIgnoreCase("MOB")))
			{
				mob.tell(L("Only rooms' titles and descriptions vary (<VARIES>); PREVIEW ROOM shows them."));
				return false;
			}
			if(mob.location() == null)
				return false;
			mob.tell(preview(mob, mob.location(), rest));
			return false;
		}
		final String what = rest.isEmpty() ? "ROOM" : rest.get(0).toUpperCase();
		final List<String> problems;
		if(what.equals("WORLD") || what.equals("ALL"))
			problems = TextCheck.checkRooms(null);
		else
		if(what.equals("AREA"))
			problems = TextCheck.checkRooms(mob.location().getArea());
		else
			problems = TextCheck.checkRoom(mob.location());
		if(problems.isEmpty())
			mob.tell(L("CHECKTEXT: no problems found."));
		else
			mob.tell(L("CHECKTEXT: @x1 problems:", "" + problems.size()) + "\n\r" + String.join("\n\r", problems));
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
		return CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS);
	}
}
