package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;
import com.planet_ink.coffee_mud.Common.interfaces.*;

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
 * G10: data-driven affixes for random enchantment: world/affixes.xml, else
 * resources/randareas/affixes.xml (or the world's own copy there).  Each
 * &lt;AFFIX id name="Flaming" pos="prefix|suffix" kinds="weapon,armor,ammo,item"
 * level="5-30" pickweight="10" power="1" cursed="false"&gt; holds &lt;AFFECT
 * class="..." parms="..."/&gt; tags.  enchant() uses them when the file is
 * there (ItemGenerator ENCHANTED=), as do Prop_LootTable's and RandomItems'
 * AFFIXES= and a template ITEM's AFFIXES="0?2": the item's name takes the
 * prefix and suffix, its level rises by the affix's power and its value by
 * a tenth for each.
 */
public final class Affixes
{
	private Affixes()
	{
	}

	/** an affix, as read */
	public static final class Affix
	{
		/** its id and name */
		public final String			id, name;
		/** true for a prefix, false for a suffix */
		public final boolean		prefix;
		/** the kinds of item it goes on: weapon, armor, ammo, item, any */
		public final Set<String>	kinds	= new HashSet<String>();
		/** the item levels it goes on */
		public final int			lo, hi;
		/** its weight in the pick */
		public final double			weight;
		/** how much it raises the item's level */
		public final int			power;
		/** a cursed affix: the item cannot be taken off */
		public final boolean		cursed;
		/** its effects: class and parameters */
		public final List<String[]>	affects	= new ArrayList<String[]>();

		Affix(final String id, final String name, final boolean prefix, final int lo, final int hi, final double weight, final int power, final boolean cursed)
		{
			this.id = id;
			this.name = name;
			this.prefix = prefix;
			this.lo = lo;
			this.hi = hi;
			this.weight = weight;
			this.power = power;
			this.cursed = cursed;
		}
	}

	private static volatile List<Affix>	cache	= null;

	/** drops the affixes read, so the file is read again */
	public static void reload()
	{
		cache = null;
	}

	private static String text()
	{
		for(final String path : new String[] { "world/affixes.xml", Resources.buildResourcePath("randareas/affixes.xml") })
		{
			final CMFile F = new CMFile(path, null);
			if(F.canRead())
				return F.text().toString();
		}
		return null;
	}

	/**
	 * @return the affixes, read once, or an empty list where there is no file
	 */
	public static List<Affix> all()
	{
		List<Affix> L = cache;
		if(L != null)
			return L;
		L = new ArrayList<Affix>();
		final String t = text();
		if(t != null)
		{
			for(final XMLTag tag : CMLib.xml().parseAllXML(t))
			{
				final List<XMLTag> list = tag.tag().equalsIgnoreCase("AFFIXES") ? tag.contents() : Collections.singletonList(tag);
				if(list == null)
					continue;
				for(final XMLTag a : list)
				{
					if(!a.tag().equalsIgnoreCase("AFFIX"))
						continue;
					final String name = (a.getParmValue("NAME") == null) ? "" : a.getParmValue("NAME").trim();
					if(name.length() == 0)
						continue;
					final String lv = (a.getParmValue("LEVEL") == null) ? "1-9999" : a.getParmValue("LEVEL");
					final String[] r = lv.split("\\s*-\\s*");
					final int lo = CMath.s_int(r[0].trim()), hi = (r.length > 1) ? CMath.s_int(r[1].trim()) : 9999;
					final String pw = a.getParmValue("PICKWEIGHT");
					final String po = a.getParmValue("POWER");
					final Affix X = new Affix((a.getParmValue("ID") == null) ? name.toLowerCase() : a.getParmValue("ID"), name,
						!"suffix".equalsIgnoreCase(a.getParmValue("POS")), lo, hi, (pw == null) ? 10 : CMath.s_double(pw),
						(po == null) ? 1 : CMath.s_int(po), CMath.s_bool(a.getParmValue("CURSED")));
					final String kinds = (a.getParmValue("KINDS") == null) ? "any" : a.getParmValue("KINDS");
					for(final String k : kinds.split("\\s*,\\s*"))
						X.kinds.add(k.trim().toLowerCase());
					if(a.contents() != null)
					{
						for(final XMLTag e : a.contents())
						{
							if(e.tag().equalsIgnoreCase("AFFECT") && (e.getParmValue("CLASS") != null))
								X.affects.add(new String[] { e.getParmValue("CLASS"),
									(e.getParmValue("PARMS") != null) ? CMLib.xml().restoreAngleBrackets(e.getParmValue("PARMS")) : e.value() });
						}
					}
					L.add(X);
				}
			}
		}
		cache = L;
		return L;
	}

	/**
	 * @return true if there is an affix file with any in it
	 */
	public static boolean available()
	{
		return all().size() > 0;
	}

	/**
	 * @param I an item
	 * @return its kind for the affixes: ammo, weapon, armor or item
	 */
	public static String kindOf(final Item I)
	{
		if(I instanceof Ammunition)
			return "ammo";
		if(I instanceof Weapon)
			return "weapon";
		if(I instanceof Armor)
			return "armor";
		return "item";
	}

	private static Affix pick(final Item I, final Boolean prefix, final Set<String> used)
	{
		final String kind = kindOf(I);
		final int level = I.basePhyStats().level();
		final List<Affix> fit = new ArrayList<Affix>();
		double total = 0;
		for(final Affix A : all())
		{
			if(((prefix != null) && (A.prefix != prefix.booleanValue())) || used.contains(A.id)
			|| ((!A.kinds.contains(kind)) && (!A.kinds.contains("any")))
			|| (level < A.lo) || (level > A.hi) || (A.weight <= 0))
				continue;
			fit.add(A);
			total += A.weight;
		}
		if(fit.isEmpty())
			return null;
		double at = CMLib.dice().getRandomizer().nextDouble() * total;
		for(final Affix A : fit)
		{
			at -= A.weight;
			if(at <= 0)
				return A;
		}
		return fit.get(fit.size() - 1);
	}

	/**
	 * A name with a prefix after its article (a Flaming sword, an Icy axe).
	 * @param name the name
	 * @param prefix the prefix
	 * @return the name
	 */
	public static String withPrefix(final String name, final String prefix)
	{
		final String lower = name.toLowerCase();
		for(final String art : new String[] { "a ", "an ", "the ", "some " })
		{
			if(lower.startsWith(art))
			{
				final String a = (art.equals("a ") || art.equals("an ")) ? ("aeiou".indexOf(Character.toLowerCase(prefix.charAt(0))) >= 0 ? "an " : "a ") : name.substring(0, art.length());
				return a + prefix + " " + name.substring(art.length());
			}
		}
		return prefix + " " + name;
	}

	/**
	 * Puts affixes on an item: up to one prefix and one suffix, each fitting
	 * the item's kind and level, picked by weight.
	 * @param I the item
	 * @param count how many (2 at most)
	 * @return how many it put on
	 */
	public static int apply(final Item I, final int count)
	{
		if((I == null) || (count <= 0) || (!available()))
			return 0;
		final Set<String> used = new HashSet<String>();
		Affix pre = null, suf = null;
		for(int n = 0; n < Math.min(2, count); n++)
		{
			final Affix A = pick(I, (pre != null) ? Boolean.FALSE : ((suf != null) ? Boolean.TRUE : null), used);
			if(A == null)
				break;
			used.add(A.id);
			if(A.prefix)
				pre = A;
			else
				suf = A;
		}
		final String oldName = I.Name();
		String name = oldName;
		int done = 0;
		for(final Affix A : new Affix[] { pre, suf })
		{
			if(A == null)
				continue;
			if(A.prefix)
				name = withPrefix(name, A.name);
			else
				name = name + " " + (A.name.toLowerCase().startsWith("of ") ? A.name : ("of " + A.name));
			for(final String[] e : A.affects)
			{
				final Ability X = CMClass.getAbility(e[0]);
				if(X == null)
				{
					Log.errOut("Affixes", A.id + ": no ability " + e[0]);
					continue;
				}
				final String parms = (e[1] == null) ? "" : e[1].trim();
				final Ability had = I.fetchEffect(X.ID());
				if(had != null) // one of a kind on an item: the parameters join it (a spell list with ;)
					had.setMiscText((had.text().trim() + (X.ID().endsWith("SpellCast") ? ";" : " ") + parms).trim());
				else
				{
					X.setMiscText(parms);
					I.addNonUninvokableEffect(X);
				}
			}
			if(A.cursed)
				CMLib.flags().setRemovable(I, false);
			I.basePhyStats().setLevel(I.basePhyStats().level() + A.power);
			I.setBaseValue((int)Math.round(I.baseGoldValue() * (1.0 + (A.power / 10.0))));
			done++;
		}
		if(done > 0)
		{
			I.setName(name);
			final String disp = I.displayText();
			final int x = (disp == null) ? -1 : disp.toLowerCase().indexOf(oldName.toLowerCase());
			if(x >= 0)
				I.setDisplayText(disp.substring(0, x) + name + disp.substring(x + oldName.length()));
			I.basePhyStats().setDisposition(I.basePhyStats().disposition() | com.planet_ink.coffee_mud.Common.interfaces.PhyStats.IS_BONUS);
			I.recoverPhyStats();
		}
		return done;
	}
}
