package com.planet_ink.coffee_mud.core;

import java.lang.ref.WeakReference;
import java.util.*;

import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.core.interfaces.ShopKeeper;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.Banker;
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
 * N9: who each character is talking with (TALK), so that a bare SAY goes to
 * that NPC while both are in the room; whom a bare TALK means; and the
 * services an NPC offers, as a menu.
 */
public final class Conversations
{
	private Conversations()
	{
	}

	private static final Map<MOB, WeakReference<MOB>>	partners	= new WeakHashMap<MOB, WeakReference<MOB>>();

	/**
	 * @param who a character
	 * @param npc the NPC they now talk with
	 */
	public static void begin(final MOB who, final MOB npc)
	{
		if((who == null) || (npc == null))
			return;
		synchronized(partners)
		{
			partners.put(who, new WeakReference<MOB>(npc));
		}
	}

	/**
	 * @param who a character
	 */
	public static void end(final MOB who)
	{
		synchronized(partners)
		{
			partners.remove(who);
		}
	}

	/**
	 * @param who a character
	 * @return the NPC they talk with, if it is alive and in the game, or null
	 */
	public static MOB partnerOf(final MOB who)
	{
		final WeakReference<MOB> ref;
		synchronized(partners)
		{
			ref = partners.get(who);
		}
		final MOB npc = (ref == null) ? null : ref.get();
		if((npc == null) || npc.amDead() || npc.amDestroyed() || (npc.location() == null))
			return null;
		return npc;
	}

	/**
	 * @param who a character
	 * @return the NPC they talk with if it is here and seen, or null
	 */
	public static MOB partnerHere(final MOB who)
	{
		final MOB npc = partnerOf(who);
		if((npc == null) || (who == null) || (npc.location() != who.location()) || (!CMLib.flags().canBeSeenBy(npc, who)))
			return null;
		return npc;
	}

	/**
	 * @param who a character
	 * @param M someone in the room
	 * @return true if they may TALK with M: an NPC they see
	 */
	public static boolean talkable(final MOB who, final MOB M)
	{
		if((M == null) || (who == null) || (M == who) || (M.playerStats() != null) || M.amDead())
			return false;
		return CMLib.flags().canBeSeenBy(M, who);
	}

	/**
	 * @param who a character
	 * @return the NPCs here a bare TALK could mean
	 */
	public static List<MOB> candidates(final MOB who)
	{
		final List<MOB> list = new ArrayList<MOB>();
		final Room R = who.location();
		if(R == null)
			return list;
		final Set<MOB> party = who.getGroupMembers(new HashSet<MOB>());
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if(talkable(who, M) && (!party.contains(M))) // their own party is never picked
				list.add(M);
		}
		return list;
	}

	/**
	 * The services an NPC offers, as menu choices: each a key, a label, and
	 * the command it runs for the character.
	 * @param who the character
	 * @param npc the NPC
	 * @return the choices, maybe none
	 */
	public static List<String[]> services(final MOB who, final MOB npc)
	{
		final List<String[]> list = new ArrayList<String[]>();
		final ShopKeeper SK = CMLib.coffeeShops().getShopKeeper(npc);
		if(SK != null)
		{
			final boolean several = CMLib.coffeeShops().getAllShopkeepers(who.location(), who).size() > 1;
			final String cmd = several ? ("LIST \"" + npc.name() + "\"") : "LIST";
			if(npc instanceof Banker)
				list.add(new String[] { "BANK", CMLib.lang().L("Banking (LIST, DEPOSIT, WITHDRAW)"), cmd });
			else
				list.add(new String[] { "TRADE", CMLib.lang().L("Trade (LIST, BUY, SELL)"), cmd });
		}
		for(final Enumeration<Behavior> b = npc.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if(B == null)
				continue;
			if(B.ID().equals("MOBTeacher"))
				list.add(new String[] { "LEARN", CMLib.lang().L("Learn (QUALIFY, then SAYTO it TEACH ME a skill)"), "QUALIFY" });
			else
			if(B.ID().equals("Hireling"))
				list.add(new String[] { "HIRE", CMLib.lang().L("Hire (its terms)"), "SAYTO \"" + npc.name() + "\" hire" });
		}
		if(QuestMarkers.marker(npc, who).trim().length() > 0)
			list.add(new String[] { "QUESTS", CMLib.lang().L("Quests (QUESTS OFFERED)"), "QUESTS OFFERED" });
		if(!list.isEmpty())
			list.add(new String[] { "BYE", CMLib.lang().L("Goodbye"), "TALK BYE" });
		return list;
	}

	/**
	 * Shows the services an NPC offers as a menu (N2's), each choice running
	 * its command.
	 * @param who the character
	 * @param npc the NPC
	 * @return true if it offers any
	 */
	public static boolean offerServices(final MOB who, final MOB npc)
	{
		final List<String[]> services = services(who, npc);
		if(services.isEmpty())
			return false;
		final ScriptMenus.Menu M = new ScriptMenus.Menu();
		M.prompt = CMLib.lang().L("@x1 can help you with:", npc.name());
		M.who = who;
		M.dflt = "BYE";
		for(final String[] s : services)
		{
			final ScriptMenus.Option O = new ScriptMenus.Option();
			O.key = s[0];
			O.label = s[1];
			O.command = s[2];
			M.options.add(O);
		}
		ScriptMenus.open(M, who.session());
		return true;
	}
}
