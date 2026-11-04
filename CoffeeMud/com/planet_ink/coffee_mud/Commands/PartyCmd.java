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
 * were, for the player to go and meet them.  PARTY FIGHT ON or OFF chooses
 * whether the player's characters may fight each other (OFF, the default).
 * <p>
 * For turn-based time (see PARTYTURNS), PARTY ORDER NAME NAME... sets the
 * order the party acts in, PARTY LEAD NAME hands the lead over, and PARTY
 * DISABLE NAME has a member act on their own, as followers do, rather than
 * the turn waiting for an order for them; PARTY ENABLE NAME undoes it.
 * See {@link Party}.
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
		if(what.equals("CLANS")) mob.tell(Organizations.partyClans(mob));
		else
		if(what.equals("RULES"))
		{
			mob.tell(L("Party size limit: @x1. Profile limit: @x2. Active character limit: @x4. Summoning: @x3.",
				WorldRules.partyMax() == 0 ? L("unlimited") : "" + WorldRules.partyMax(),
				WorldRules.profileMax() == 0 ? L("unlimited") : "" + WorldRules.profileMax(),
				WorldRules.partySummon().equals("MEET") ? L("meet companions where they are")
				: WorldRules.partySummon().equals("MASK") ? L("only where this world's conditions allow") : L("allowed"),
				WorldRules.activeMax()==0?L("unlimited"):""+WorldRules.activeMax()));
		}
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
		if(what.equals("FIGHT"))
			fight(mob, name);
		else
		if(what.equals("ORDER"))
			order(mob, commands.subList(2, commands.size()));
		else
		if(what.equals("LEAD"))
			lead(mob, name);
		else
		if(what.equals("DISABLE") || what.equals("ENABLE"))
			onTheirOwn(mob, name, what.equals("DISABLE"));
		else
			mob.tell(L("PARTY, then ADD, REMOVE, REST, LEAD, DISABLE or ENABLE and a name, ORDER and names, MEET or FIGHT and ON or OFF, RULES, or CLANS?"));
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

	/**
	 * Describes a member of the party in a line, with what matters in
	 * turn-based time: whether they lead, act on their own, or have orders.
	 * @param mob the player
	 * @param M the member
	 * @param leader the party's leader
	 * @return the line
	 */
	protected String memberLine(final MOB mob, final MOB M, final MOB leader)
	{
		final StringBuilder str = new StringBuilder(line(mob, M));
		str.setLength(str.length() - 2);
		if(M == leader)
			str.append(L(", leading"));
		else
		if(Party.isOnTheirOwn(M))
			str.append(L(", acting on their own"));
		if((M != leader) && (M.commandQueSize() > 0) && GameTime.isTurnBased())
			str.append(L(", with orders"));
		return str.append("\n\r").toString();
	}

	protected void list(final MOB mob, final PlayerAccount profile)
	{
		final MOB leader = Party.leaderOf(mob);
		final List<MOB> inWorld = new ArrayList<MOB>(Party.othersInWorld(mob));
		inWorld.add(0, mob);
		final StringBuilder elsewhere = new StringBuilder("");
		for(final MOB M : inWorld)
		{
			if(!Party.isInParty(leader, M))
				elsewhere.append(line(mob, M));
		}
		final StringBuilder str = new StringBuilder("");
		final List<MOB> party = Party.inOrder(leader);
		if((party.size() < 2) && (leader == mob))
			str.append(L("^HYou are on your own.^N\n\r"));
		else
		{
			str.append(L("^HYour party:^N\n\r"));
			for(final MOB M : party)
				str.append(memberLine(mob, M, leader));
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
		final String roles = WorldRules.roleWarning(party); // P10: PARTYROLES
		if(roles.length() > 0)
			str.append(roles).append("\n\r");
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
		ScriptEvents.party("JOIN", M, leader); // ST10
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
			final String full = WorldRules.partyRefusal(profile, Party.inOrder(leader).size(), 0, false); // P10
			if(full != null)
			{
				mob.tell(full);
				return;
			}
			if((M.location() != mob.location()) && (!meet))
			{
				final String refusal = WorldRules.summonRefusal(mob);
				if(refusal != null)
				{
					mob.tell(refusal);
					return;
				}
				mob.location().bringMobHere(M, true);
			}
		}
		else
		{
			final String found = profile.findPlayer(name);
			if(found == null)
			{
				mob.tell(L("You have no character called '@x1'.", name));
				return;
			}
			final String full = WorldRules.partyRefusal(profile, Party.inOrder(leader).size(), WorldRules.inWorld(profile), true); // P10
			if(full != null)
			{
				mob.tell(full);
				return;
			}
			final MOB resting = CMLib.players().getLoadPlayer(found);
			if((!meet) && ((resting == null) || (resting.location() != mob.location())))
			{
				final String refusal = WorldRules.summonRefusal(mob);
				if(refusal != null)
				{
					mob.tell(refusal);
					return;
				}
			}
			M = Party.bringIn(mob, found, meet ? null : mob.location());
			if(M == null)
			{
				mob.tell(L("@x1 could not come into the world.", found));
				return;
			}
		}
		if(M.location() == mob.location())
		{
			join(M, leader);
			Party.addToOrder(leader);
			Party.addToOrder(M);
		}
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
		ScriptEvents.party("LEAVE", M, leader); // ST10
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
		if(onOff.equalsIgnoreCase("OFF") && WorldRules.partySummon().equals("MEET"))
		{
			mob.tell(WorldRules.summonRefusal(mob));
			return;
		}
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

	/**
	 * Finds a member of the player's party by name: the one being played, or
	 * another, the whole name or its start.
	 * @param mob the player
	 * @param name the name
	 * @return the member, or null
	 */
	protected MOB findMember(final MOB mob, final String name)
	{
		final MOB leader = Party.leaderOf(mob);
		final MOB M = name.equalsIgnoreCase(mob.Name()) ? mob : Party.findInWorld(mob, name);
		return ((M != null) && Party.isInParty(leader, M)) ? M : null;
	}

	protected void order(final MOB mob, final List<String> names)
	{
		final MOB leader = Party.leaderOf(mob);
		if(names.size() > 0)
		{
			final StringBuilder set = new StringBuilder("");
			for(final String name : names)
			{
				final MOB M = findMember(mob, name);
				if(M == null)
				{
					mob.tell(L("'@x1' is not in your party.", name));
					return;
				}
				set.append((set.length() > 0) ? "," : "").append(M.Name());
			}
			Party.setOrder(set.toString());
		}
		final StringBuilder str = new StringBuilder("");
		for(final MOB M : Party.inOrder(leader))
			str.append((str.length() > 0) ? ", " : "").append(M.Name());
		mob.tell(L("Your party acts in this order: @x1.", str.toString()));
	}

	protected void lead(final MOB mob, final String name)
	{
		final MOB leader = Party.leaderOf(mob);
		final MOB M = findMember(mob, name);
		if(M == null)
		{
			mob.tell(L("'@x1' is not in your party.", name));
			return;
		}
		if(M == leader)
		{
			mob.tell(L("@x1 leads the party already.", M.Name()));
			return;
		}
		Party.handOver(leader, M);
		M.location().show(M, null, CMMsg.MSG_OK_ACTION, L("<S-NAME> lead(s) the party now."));
	}

	protected void onTheirOwn(final MOB mob, final String name, final boolean own)
	{
		final MOB M = findMember(mob, name);
		if((M == null) || (M == Party.leaderOf(mob)))
		{
			mob.tell(L("'@x1' is not one of those your party leader leads.", name));
			return;
		}
		Party.setOnTheirOwn(M, own);
		if(own)
			mob.tell(L("@x1 acts on their own in turn-based time: the turn does not wait for orders for them, and they do what followers do.", M.Name()));
		else
			mob.tell(L("When the turn waits for orders for all of your party (PARTYTURNS ALL), it waits for @x1's too.", M.Name()));
	}

	protected void fight(final MOB mob, final String onOff)
	{
		if(onOff.equalsIgnoreCase("ON"))
			Party.setFightEachOther(true);
		else
		if(onOff.equalsIgnoreCase("OFF"))
			Party.setFightEachOther(false);
		else
		if(onOff.length() > 0)
		{
			mob.tell(L("PARTY FIGHT ON or PARTY FIGHT OFF?"));
			return;
		}
		if(Party.isFightEachOther())
			mob.tell(L("Your characters may fight each other.  PARTY FIGHT OFF stops that."));
		else
			mob.tell(L("Your characters may not fight each other.  PARTY FIGHT ON lets them."));
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
