package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
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
 * Q8: the items Prop_QuestItem binds to a quest: found on a character, and
 * taken away when the quest leaves ACTIVE for them in a way its REMOVEON
 * names (WON, FAILED, ABANDONED, EXPIRED, or ENDED for the quest stopping;
 * by default any of them; NONE keeps it).
 */
public final class QuestItems
{
	/** the property's ID, looked for by name (it is of another class loader) */
	public static final String	ID	= "Prop_QuestItem";

	private QuestItems()
	{
	}

	/**
	 * @param I an item
	 * @return its Prop_QuestItem's QUEST and REMOVEON, or null if it has none
	 */
	public static Map<String, String> parms(final Item I)
	{
		final Ability A = (I == null) ? null : I.fetchEffect(ID);
		if(A == null)
			return null;
		// as properties read them: a value is one word, or quoted; bare words (NODROP) are not values
		final Map<String, String> p = new HashMap<String, String>();
		p.put("QUEST", CMParms.getParmStr(A.text(), "QUEST", "").trim());
		p.put("REMOVEON", CMParms.getParmStr(A.text(), "REMOVEON", "ALL").trim());
		return p;
	}

	/**
	 * @param mob a character
	 * @param quest a quest's name
	 * @return what they carry that is bound to the quest
	 */
	public static List<Item> of(final MOB mob, final String quest)
	{
		final List<Item> found = new ArrayList<Item>();
		if((mob == null) || (quest == null))
			return found;
		for(final Enumeration<Item> i = mob.items(); i.hasMoreElements();)
		{
			final Item I = i.nextElement();
			final Map<String, String> p = parms(I);
			if((p != null) && quest.equalsIgnoreCase(p.get("QUEST")))
				found.add(I);
		}
		return found;
	}

	/**
	 * Whether REMOVEON names the way the quest left ACTIVE.
	 * @param p the item's parameters
	 * @param status WON, FAILED, ABANDONED, EXPIRED or ENDED
	 * @return true to take the item away
	 */
	public static boolean removedOn(final Map<String, String> p, final String status)
	{
		final String on = p.get("REMOVEON").toUpperCase().trim();
		if(on.equals("NONE"))
			return false;
		if(on.equals("ALL") || (on.length() == 0))
			return true;
		for(final String s : CMParms.parseCommas(on, true))
		{
			if(s.trim().equals(status))
				return true;
		}
		return false;
	}

	/**
	 * The quest left ACTIVE for a character: their bound items go, as
	 * REMOVEON says.
	 * @param quest the quest's name
	 * @param who the character's name
	 * @param status WON, FAILED, ABANDONED, EXPIRED or ENDED
	 */
	public static void left(final String quest, final String who, final String status)
	{
		final MOB mob = (who == null) ? null : CMLib.players().getPlayerAllHosts(who);
		left(quest, mob, status);
	}

	/**
	 * @param quest the quest's name
	 * @param mob the character
	 * @param status WON, FAILED, ABANDONED, EXPIRED or ENDED
	 */
	public static void left(final String quest, final MOB mob, final String status)
	{
		if(mob == null)
			return;
		for(final Item I : of(mob, quest))
		{
			if(removedOn(parms(I), status))
			{
				mob.tell(CMLib.lang().L("@x1 is gone: it was for the quest.", I.name(mob)));
				I.destroy();
			}
		}
	}

	/**
	 * The quest stopped: ENDED for every character in the world (not while
	 * a save is being put back, when the boot's own quests are stopped).
	 * @param quest the quest's name
	 */
	public static void ended(final String quest)
	{
		if(com.planet_ink.coffee_mud.core.save.SaveGame.isWorldPending())
			return;
		for(final Enumeration<MOB> m = CMLib.players().players(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && (M.location() != null))
				left(quest, M, "ENDED");
		}
	}
}
