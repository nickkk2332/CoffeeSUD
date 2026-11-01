package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Behaviors.MudChat;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
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
 * The builder's tools for NPCs that talk (MudChat):
 * <ul>
 * <li>CHATTEST npc sentence: what the NPC would make of the sentence, without
 * anyone speaking (see {@link ChattyBehavior#chatTest(MOB, MOB, String)}).</li>
 * <li>CHATLINT file: what in a chat file the parser would skip or take
 * otherwise than meant.</li>
 * <li>CHAT RELOAD [file]: reads a chat file again (or all), for every NPC that
 * talks from it.  In builder mode an edited chat file is read again by itself.</li>
 * </ul>
 */
public class ChatTools extends StdCommand
{
	public ChatTools()
	{
	}

	private final String[] access = I(new String[] { "CHATTEST", "CHATLINT", "CHAT" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final String word = commands.get(0).toUpperCase();
		if(word.equals("CHATTEST"))
		{
			if(commands.size() < 3)
			{
				mob.tell(L("CHATTEST [NPC] [WHAT YOU WOULD SAY]"));
				return false;
			}
			final Room R = mob.location();
			final MOB npc = (R == null) ? null : R.fetchInhabitant(commands.get(1));
			if(npc == null)
			{
				mob.tell(L("There is no '@x1' here.", commands.get(1)));
				return false;
			}
			ChattyBehavior chatty = null;
			for(final Enumeration<Behavior> b = npc.behaviors(); b.hasMoreElements();)
			{
				final Behavior B = b.nextElement();
				if(B instanceof ChattyBehavior)
					chatty = (ChattyBehavior)B;
			}
			if(chatty == null)
			{
				mob.tell(L("@x1 has no chat behaviour (MudChat).", npc.Name()));
				return false;
			}
			mob.tell(chatty.chatTest(npc, mob, CMParms.combine(commands, 2)));
			return false;
		}
		if(word.equals("CHATLINT"))
		{
			if(commands.size() < 2)
			{
				mob.tell(L("CHATLINT [CHAT FILE]"));
				return false;
			}
			final String name = CMParms.combine(commands, 1);
			// the builder may use CHATLINT (see securityCheck), so any chat file may be read
			final CMFile F = new CMFile(Resources.makeFileResourceName(name), null, CMFile.FLAG_FORCEALLOW);
			if(!F.exists())
			{
				mob.tell(L("There is no chat file '@x1'.", name));
				return false;
			}
			final List<String> problems = MudChat.lintChat(F.text().toString());
			if(problems.isEmpty())
				mob.tell(L("@x1: no problems found.", name));
			else
				mob.tell(L("@x1: @x2 problems:\n\r", name, "" + problems.size()) + String.join("\n\r", problems));
			return false;
		}
		// CHAT RELOAD [file]
		if((commands.size() < 2) || (!commands.get(1).equalsIgnoreCase("RELOAD")))
		{
			mob.tell(L("CHAT RELOAD ([CHAT FILE]) reads chat files again.  See also CHATTEST and CHATLINT."));
			return false;
		}
		final String file = (commands.size() > 2) ? CMParms.combine(commands, 2) : null;
		final int n = MudChat.reloadChat(file);
		mob.tell((file == null)
			? L("Every chat file will be read again (@x1 were loaded).", "" + n)
			: L("The chat file @x1 will be read again.", file));
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
		return CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDMOBS);
	}
}
