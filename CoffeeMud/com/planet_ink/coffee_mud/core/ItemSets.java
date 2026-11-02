package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * I6: the pieces of an item set (Prop_ItemSet) one has, for the property
 * and for SETCOUNT().
 */
public final class ItemSets
{
	private ItemSets()
	{
	}

	/**
	 * The pieces of a set one has that count: worn, or carried when a piece
	 * says COUNT=HELD.
	 * @param M the one
	 * @param setId the set's id
	 * @return the pieces
	 */
	public static List<Item> pieces(final MOB M, final String setId)
	{
		final List<Item> list = new ArrayList<Item>();
		if((M == null) || (setId == null))
			return list;
		String count = "WORN";
		final List<Item> all = new ArrayList<Item>();
		for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
		{
			final Item I = i.nextElement();
			final Ability A = (I == null) ? null : I.fetchEffect("Prop_ItemSet");
			if((A != null) && A.getStat("SET").equalsIgnoreCase(setId))
			{
				final String c = A.getStat("COUNT");
				if(c.length() > 0)
					count = c;
				all.add(I);
			}
		}
		for(final Item I : all)
		{
			if(count.equals("HELD") || (!I.amWearingAt(Wearable.IN_INVENTORY)))
				list.add(I);
		}
		return list;
	}

	/**
	 * @param M one
	 * @param setId a set
	 * @return how many of its pieces count for them
	 */
	public static int count(final MOB M, final String setId)
	{
		return pieces(M, setId).size();
	}
}
