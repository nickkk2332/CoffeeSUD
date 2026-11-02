package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * N9: TALK [npc] [ABOUT topic]: begins a conversation.  TALK_PROG [topic
 * words|ALL] runs on the NPC at once, $n the speaker, $g and $0 the topic;
 * if none does, the speaker says the topic (or hello) to it, for its
 * MudChat and SPEECH_PROGs, and with no topic the services it offers are
 * shown as a menu.  The NPC is then the speaker's partner: a bare SAY goes
 * to it while both are in the room.  TALK BYE ends it.  A bare TALK means
 * the partner, or the only NPC here outside the speaker's party.
 */
public class Talk extends StdCommand
{
	public Talk()
	{
	}

	private final String[]	access	= I(new String[] { "TALK" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final Room R=mob.location();
		if(R==null)
			return false;
		if((commands.size()==2)
		&&(commands.get(1).equalsIgnoreCase("BYE")||commands.get(1).equalsIgnoreCase("END")||commands.get(1).equalsIgnoreCase("STOP")))
		{
			final MOB was=Conversations.partnerOf(mob);
			Conversations.end(mob);
			if(was!=null)
				mob.tell(L("You stop talking with @x1.",was.name(mob)));
			else
				mob.tell(L("You are not talking with anyone."));
			return false;
		}
		int about=-1;
		for(int i=1;i<commands.size();i++)
		{
			if(commands.get(i).equalsIgnoreCase("ABOUT"))
			{
				about=i;
				break;
			}
		}
		final String name=CMParms.combine(commands.subList(0,(about<0)?commands.size():about),1).trim();
		final String topic=(about<0)?"":CMParms.combine(commands,about+1).trim();
		MOB npc=null;
		if(name.length()>0)
		{
			npc=R.fetchInhabitant(name);
			if((npc==null)||(!CMLib.flags().canBeSeenBy(npc,mob)))
			{
				mob.tell(L("You don't see '@x1' here.",name));
				return false;
			}
			if(!Conversations.talkable(mob,npc))
			{
				mob.tell(L("You can TALK with those the game plays, not with @x1.",npc.name(mob)));
				return false;
			}
		}
		else
		{
			npc=Conversations.partnerHere(mob);
			if(npc==null)
			{
				final List<MOB> here=Conversations.candidates(mob);
				if(here.isEmpty())
				{
					mob.tell(L("There is nobody here to talk with."));
					return false;
				}
				if(here.size()>1)
				{
					final List<String> names=new ArrayList<String>();
					for(final MOB M : here)
						names.add(M.name(mob));
					mob.tell(L("Talk with whom?  Here are @x1.",CMLib.english().toEnglishStringList(names)));
					return false;
				}
				npc=here.get(0);
			}
		}
		Conversations.begin(mob,npc);
		ScriptEvents.talked(npc,mob,topic); // ST21: a meeting, and its topic
		if(ScriptEvents.talk(npc,mob,topic))
			return false;
		// no TALK_PROG: as though they said it to it, for MudChat and SPEECH_PROG
		final String words=(topic.length()>0)?topic:L("Hello"); // no stop: SPEECH_PROG matches whole words
		final Command C=CMClass.getCommand("Say");
		if(C!=null)
			C.execute(mob,new XVector<String>("SAYTO",npc.Name(),words),metaFlags);
		if(topic.length()==0)
			Conversations.offerServices(mob,npc);
		return false;
	}

	@Override
	public double actionsCost(final MOB mob, final List<String> cmds)
	{
		return CMProps.getCommandActionCost(ID()); // as SAY
	}

	@Override
	public boolean canBeOrdered()
	{
		return true;
	}
}
