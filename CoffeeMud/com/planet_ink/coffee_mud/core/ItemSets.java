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

	private static final java.util.regex.Pattern LEGACY = java.util.regex.Pattern.compile("(?i)\\bALLSET\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^;\\s]+))");
	public static String legacySpec(final String text)
	{
		final java.util.regex.Matcher m=LEGACY.matcher(text);
		return m.find() ? (m.group(1)!=null?m.group(1):m.group(2)!=null?m.group(2):m.group(3)) : "";
	}
	public static String withoutLegacy(final String text) { return LEGACY.matcher(text).replaceAll(""); }
	private static String legacyName(final String spec) { return spec.replaceFirst("-\\d+$","").trim(); }
	public static boolean legacyEnabled(final Item item, final String spec) { return legacyEnabled(item,spec,true); }
	public static boolean legacyEnabled(final Item item, final String spec, final boolean wornOnly)
	{
		if(spec.isEmpty()) return true;
		if(!(item.owner() instanceof MOB)) return false;
		final String name=legacyName(spec);
		final int dash=spec.lastIndexOf('-');
		final int needed=dash>0&&CMath.isInteger(spec.substring(dash+1)) ? Math.max(1,CMath.s_int(spec.substring(dash+1))) : 2;
		int count=0;
		for(final java.util.Enumeration<Item> e=((MOB)item.owner()).items();e.hasMoreElements();)
		{
			final Item piece=e.nextElement();
			if(wornOnly && !piece.amBeingWornProperly()) continue;
			for(final java.util.Enumeration<Ability> a=piece.effects();a.hasMoreElements();)
			{
				final Ability effect=a.nextElement();
				final String other=effect.getStat("ALLSET");
				if(!other.isEmpty() && legacyName(other).equalsIgnoreCase(name)) {count++;break;}
			}
		}
		return count>=needed;
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
