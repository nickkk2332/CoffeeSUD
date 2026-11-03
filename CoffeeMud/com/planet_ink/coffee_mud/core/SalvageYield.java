package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.core.interfaces.ItemPossessor;
import com.planet_ink.coffee_mud.Items.interfaces.RawMaterial;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
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
 * CR19: salvage yields as data.  A Prop_SalvageYield on an item (or a
 * catalog entry) says what taking it apart gives: SKILL= (Scrapping when not
 * given, or a crafting skill, for its DISMANTLE), YIELD= (a comma list of
 * [RESOURCE]:[N], [RESOURCE]([VARIANT]):[N] and catalog:[ITEM NAME]:[N], each
 * with @[PCT]% for a chance), XP= (on success) and DESTROY= (false leaves the
 * item, with nothing more in it).  Without one, [SKILL] DISMANTLE [ITEM] gives
 * DISMANTLEPCT (50 when blank, 0 for none) percent of the materials the
 * item's recipe uses, times the crafter's proficiency.
 */
public final class SalvageYield
{
	private SalvageYield()
	{
	}

	/** the property's ID */
	public static final String	PROP	= "Prop_SalvageYield";

	/**
	 * @param text a Prop_SalvageYield's text
	 * @param skill a skill
	 * @return whether it is for that skill
	 */
	public static boolean forSkill(final String text, final Ability skill)
	{
		if(skill == null)
			return false;
		final String which = CMParms.getParmStr(text, "SKILL", CMParms.getParmStr(text, "SKILLS", "Scrapping"));
		for(final String s : CMParms.parseCommas(which, true))
		{
			final String t = s.trim();
			if(t.equalsIgnoreCase(skill.ID()) || t.equalsIgnoreCase(skill.Name()) || t.equals("*") || t.equalsIgnoreCase("ALL"))
				return true;
		}
		return false;
	}

	/**
	 * @param I an item
	 * @param skill the skill taking it apart
	 * @return its Prop_SalvageYield for that skill, or null
	 */
	public static Ability on(final Item I, final Ability skill)
	{
		if(I == null)
			return null;
		for(final Enumeration<Ability> e = I.effects(); e.hasMoreElements();)
		{
			final Ability A = e.nextElement();
			if((A != null) && A.ID().equals(PROP) && forSkill(A.text(), skill))
				return A;
		}
		return null;
	}

	/**
	 * @param text a Prop_SalvageYield's text
	 * @return its XP=
	 */
	public static int xp(final String text)
	{
		return Math.max(0, CMParms.getParmInt(text, "XP", 0));
	}

	/**
	 * @param text a Prop_SalvageYield's text
	 * @return false when DESTROY=false: the item stays
	 */
	public static boolean destroys(final String text)
	{
		final String s = CMParms.getParmStr(text, "DESTROY", "true").trim();
		return !(s.equalsIgnoreCase("false") || s.equals("0") || s.equalsIgnoreCase("no"));
	}

	/**
	 * @return DISMANTLEPCT, the percent of a recipe's materials DISMANTLE gives back
	 */
	public static int dismantlePct()
	{
		final CMProps P = CMProps.instance();
		final String s = (P == null) ? "" : P.getStr("DISMANTLEPCT", "").trim();
		return (s.length() == 0) ? 50 : Math.max(0, Math.min(100, CMath.s_int(s)));
	}

	/**
	 * @param text a Prop_SalvageYield's text
	 * @return its YIELD= list; unquoted, it runs to the next space (a plain
	 * parameter would stop at its first colon)
	 */
	public static String yieldList(final String text)
	{
		final int x = (text == null) ? -1 : text.toUpperCase().indexOf("YIELD=");
		if((x >= 0) && ((x == 0) || !Character.isLetter(text.charAt(x - 1)))
		&& (x + 6 < text.length()) && (text.charAt(x + 6) != '"'))
		{
			int e = x + 6;
			while((e < text.length()) && !Character.isWhitespace(text.charAt(e)))
				e++;
			return text.substring(x + 6, e);
		}
		return CMParms.getParmStr(text, "YIELD", "");
	}

	/**
	 * Rolls a YIELD= list.
	 * @param text a Prop_SalvageYield's text
	 * @return the items it gives this time
	 */
	public static List<Item> roll(final String text)
	{
		final List<Item> got = new ArrayList<Item>();
		for(final String e : CMParms.parseCommas(yieldList(text), true))
		{
			String s = e.trim();
			int chance = 100;
			final int at = s.lastIndexOf('@');
			if(at > 0)
			{
				chance = CMath.s_int(s.substring(at + 1).replace("%", "").trim());
				s = s.substring(0, at).trim();
			}
			if((chance < 100) && (CMLib.dice().rollPercentage() > chance))
				continue;
			final boolean catalog = s.toLowerCase().startsWith("catalog:");
			if(catalog)
				s = s.substring(8).trim();
			int count = 1;
			final int colon = s.lastIndexOf(':');
			if((colon > 0) && CMath.isInteger(s.substring(colon + 1).trim()))
			{
				count = CMath.s_int(s.substring(colon + 1).trim());
				s = s.substring(0, colon).trim();
			}
			for(int n = 0; n < count; n++)
			{
				final Item I = catalog ? catalogItem(s) : resource(s);
				if(I == null)
				{
					Log.errOut("SalvageYield", "no " + (catalog ? "catalog item" : "resource") + " '" + s + "' in a YIELD");
					break;
				}
				got.add(I);
			}
		}
		return got;
	}

	private static Item catalogItem(final String name)
	{
		final Item C = CMLib.catalog().getCatalogItem(name);
		if(C == null)
			return null;
		final Item I = (Item)C.copyOf();
		try
		{
			CMLib.catalog().changeCatalogUsage(I, true);
		}
		catch(final Exception x)
		{
		}
		return I;
	}

	/**
	 * @param spec a resource, as brass or brass(clockwork)
	 * @return a unit of it, or null
	 */
	public static Item resource(final String spec)
	{
		String name = spec.trim();
		String variant = null;
		final int p = name.indexOf('(');
		if((p > 0) && name.endsWith(")"))
		{
			variant = name.substring(p + 1, name.length() - 1).trim();
			name = name.substring(0, p).trim();
		}
		final int code = RawMaterial.CODES.FIND_IgnoreCase(name);
		if(code < 0)
			return null;
		return ((variant == null) || (variant.length() == 0)) ? CMLib.materials().makeItemResource(code)
			: CMLib.materials().makeItemResource(code, variant);
	}

	/**
	 * Hands what was taken apart to the one who did it: put in the room,
	 * then picked up, with XP= and a word on what they got.
	 * @param mob the one who took it apart
	 * @param got what it gave
	 * @param xp the XP for it
	 * @param skill the skill used
	 */
	public static void give(final MOB mob, final List<Item> got, final int xp, final Ability skill)
	{
		final Room R = mob.location();
		if(R == null)
			return;
		final Map<String, Integer> names = new LinkedHashMap<String, Integer>();
		for(final Item I : got)
		{
			R.addItem(I, ItemPossessor.Expire.Player_Drop);
			CMLib.commands().postGet(mob, null, I, true);
			final Integer n = names.get(I.name());
			names.put(I.name(), Integer.valueOf((n == null) ? 1 : (n.intValue() + 1)));
		}
		R.recoverRoomStats();
		if(names.size() == 0)
			mob.tell(CMLib.lang().L("You get nothing of use from it."));
		else
		{
			final List<String> L = new ArrayList<String>();
			for(final Map.Entry<String, Integer> e : names.entrySet())
				L.add(e.getKey() + ((e.getValue().intValue() > 1) ? (" (x" + e.getValue() + ")") : ""));
			mob.tell(CMLib.lang().L("You get @x1.", CMParms.toListString(L)));
		}
		if(xp > 0)
			CMLib.leveler().postExperience(mob, "ABILITY:" + ((skill == null) ? "" : skill.ID()), null, null, xp, false);
	}
}
