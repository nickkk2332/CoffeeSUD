package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

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
 * PARTY lists the player's characters: the party (the group following one
 * leader), the others in the world, and those resting in the profile.
 * PARTY ADD NAME brings one into the party, PARTY REMOVE NAME takes one out
 * of it, leaving them in the world, and PARTY REST NAME sends one back to
 * rest in the profile.  PARTY MEET ON or OFF chooses where a character comes
 * to join: beside the player (OFF, the default), or where they are or last
 * were, for the player to go and meet them.  See {@link Party}.
 */
public class PartyCmd extends StdCommand
{
	public PartyCmd()
	{
	}

	private final String[] access = I(new String[] { "PARTY" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final PlayerAccount profile = Party.profileOf(mob);
		if((profile == null) || (mob.location() == null))
		{
			mob.tell(L("You have no party."));
			return false;
		}
		final String what = (commands.size() > 1) ? commands.get(1).toUpperCase() : "";
		final String name = CMParms.combine(commands, 2).trim();
		if(what.length() == 0)
			list(mob, profile);
		else
		if(what.equals("ADD") || what.equals("JOIN"))
			add(mob, profile, name);
		else
		if(what.equals("REMOVE") || what.equals("LEAVE"))
			remove(mob, name);
		else
		if(what.equals("REST") || what.equals("DISMISS"))
			rest(mob, name);
		else
		if(what.equals("MEET"))
			meet(mob, name);
		else
			mob.tell(L("PARTY, PARTY ADD NAME, PARTY REMOVE NAME, PARTY REST NAME, or PARTY MEET ON/OFF?"));
		return false;
	}

	/**
	 * Describes a character in a line: name, level and class, and where they
	 * are if it is not where the player is.
	 * @param mob the player
	 * @param M the character
	 * @return the line
	 */
	protected String line(final MOB mob, final MOB M)
	{
		final StringBuilder str = new StringBuilder("  ").append(M.Name());
		str.append(L(", level @x1 @x2", ""+M.phyStats().level(), M.charStats().getCurrentClass().name(M.charStats().getCurrentClassLevel())));
		if(M == mob)
			str.append(L(" (you)"));
		if(M.location() != mob.location())
			str.append(L(", in @x1", CMStrings.removeColors(M.location().displayText(mob))));
		return str.append("\n\r").toString();
	}

	protected void list(final MOB mob, final PlayerAccount profile)
	{
		final MOB leader = Party.leaderOf(mob);
		final List<MOB> inWorld = new ArrayList<MOB>(Party.othersInWorld(mob));
		inWorld.add(0, mob);
		final StringBuilder party = new StringBuilder("");
		final StringBuilder elsewhere = new StringBuilder("");
		for(final MOB M : inWorld)
		{
			if(M == leader)
				continue;
			if(Party.isInParty(leader, M))
				party.append(line(mob, M));
			else
				elsewhere.append(line(mob, M));
		}
		final StringBuilder str = new StringBuilder("");
		if((party.length() == 0) && (leader == mob))
			str.append(L("^HYou are on your own.^N\n\r"));
		else
		{
			str.append(L("^HYour party:^N\n\r"));
			str.append(line(mob, leader).replaceFirst("\n\r$", L(", leading") + "\n\r"));
			str.append(party);
		}
		if(elsewhere.length() > 0)
			str.append(L("^HElsewhere in the world:^N\n\r")).append(elsewhere);
		final StringBuilder resting = new StringBuilder("");
		for(final Enumeration<String> e = profile.getPlayers(); e.hasMoreElements();)
		{
			final String name = e.nextElement();
			boolean found = false;
			for(final MOB M : inWorld)
				found = found || M.Name().equalsIgnoreCase(name);
			if(!found)
				resting.append("  ").append(name).append("\n\r");
		}
		if(resting.length() > 0)
			str.append(L("^HResting:^N\n\r")).append(resting);
		mob.tell(str.toString());
	}

	/**
	 * Has a character join the party: they follow its leader.
	 * @param M the character
	 * @param leader the party's leader
	 */
	protected void join(final MOB M, final MOB leader)
	{
		M.setFollowing(leader);
		M.location().show(M, leader, CMMsg.MSG_OK_ACTION, L("<S-NAME> join(s) <T-YOUPOSS> party."));
	}

	protected void add(final MOB mob, final PlayerAccount profile, final String name)
	{
		if(name.length() == 0)
		{
			mob.tell(L("Add whom to your party?"));
			return;
		}
		final MOB leader = Party.leaderOf(mob);
		final boolean meet = Party.isMeetWhereLeft();
		MOB M = Party.findInWorld(mob, name);
		if(M != null)
		{
			if(Party.isInParty(leader, M))
			{
				mob.tell(L("@x1 is already in your party.", M.Name()));
				return;
			}
			if((M.location() != mob.location()) && (!meet))
				mob.location().bringMobHere(M, true);
		}
		else
		{
			final String found = profile.findPlayer(name);
			if(found == null)
			{
				mob.tell(L("You have no character called '@x1'.", name));
				return;
			}
			M = Party.bringIn(mob, found, meet ? null : mob.location());
			if(M == null)
			{
				mob.tell(L("@x1 could not come into the world.", found));
				return;
			}
		}
		if(M.location() == mob.location())
			join(M, leader);
		else
			mob.tell(L("@x1 is in @x2.  Go and meet them there, then PARTY ADD them.", M.Name(), CMStrings.removeColors(M.location().displayText(mob))));
	}

	protected void remove(final MOB mob, final String name)
	{
		final MOB M = name.equalsIgnoreCase(mob.Name()) ? mob : Party.findInWorld(mob, name);
		final MOB leader = Party.leaderOf(mob);
		if((M == null) || (!Party.isInParty(leader, M)))
		{
			mob.tell(L("'@x1' is not in your party.", name));
			return;
		}
		if(M == leader)
		{
			mob.tell(L("@x1 leads the party.  Remove the others instead.", M.Name()));
			return;
		}
		M.setFollowing(null);
		M.location().show(M, leader, CMMsg.MSG_OK_ACTION, L("<S-NAME> leave(s) <T-YOUPOSS> party."));
	}

	protected void rest(final MOB mob, final String name)
	{
		final MOB M = Party.findInWorld(mob, name);
		if(M == null)
		{
			if(name.equalsIgnoreCase(mob.Name()))
				mob.tell(L("To rest the one you are playing, SWITCH to another, or LOGOFF."));
			else
				mob.tell(L("'@x1' is not in the world.", name));
			return;
		}
		if(!Party.isBackground(M))
			return;
		M.location().show(M, null, CMMsg.MSG_OK_ACTION, L("<S-NAME> leave(s) the world to rest."));
		Party.rest(M);
		mob.tell(L("@x1 rests in your profile.", M.Name()));
	}

	protected void meet(final MOB mob, final String onOff)
	{
		if(onOff.equalsIgnoreCase("ON"))
			Party.setMeetWhereLeft(true);
		else
		if(onOff.equalsIgnoreCase("OFF"))
			Party.setMeetWhereLeft(false);
		else
		if(onOff.length() > 0)
		{
			mob.tell(L("PARTY MEET ON or PARTY MEET OFF?"));
			return;
		}
		if(Party.isMeetWhereLeft())
			mob.tell(L("A character you add to your party stays where they are, or comes back where they last were, and you go to meet them.  PARTY MEET OFF has them come to you."));
		else
			mob.tell(L("A character you add to your party comes to you.  PARTY MEET ON has them stay where they are, or come back where they last were, for you to go and meet them."));
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return CMProps.isSinglePlayer() && CMProps.isUsingAccountSystem();
	}
}
