package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.Environmental;

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
 * A23: one rule for knowing a thing.  An item with Prop_Unknown is of a kind
 * (KIND=); until a character knows that kind, everything shows them its
 * unknown name, look and description: inventory, the room, messages (the
 * engine's own name(viewer)), examination, identification, appraisal and the
 * price a shop pays them.  A character learns a kind once, by one of the ways
 * its REVEAL allows (IDENTIFY, APPRAISE, EXAMINE, USE), and then knows every
 * item of that kind; the knowledge is a tattoo, KNOWN_[KIND], so it is saved
 * with them.  Looking again changes nothing.  Core code reaches the property
 * through its text only (the engine loads properties with its own loader).
 */
public final class ItemKnowledge
{
	private ItemKnowledge()
	{
	}

	public static final String	PROPERTY	= "Prop_Unknown";
	public static final String	FAMILY		= "knowledge";

	private static Ability prop(final Environmental E)
	{
		return (E instanceof Item) ? ((Item)E).fetchEffect(PROPERTY) : null;
	}

	private static String parm(final Ability p, final String key, final String dflt)
	{
		return CMStrings.deEscape(CMParms.getParmStr(p.text(), key, dflt));
	}

	/** an item's kind, or "" when it has none to know */
	public static String kind(final Item I)
	{
		final Ability p = prop(I);
		return (p == null) ? "" : parm(p, "KIND", "").trim().toUpperCase(Locale.ROOT);
	}

	/** the tattoo a character carries once they know a kind */
	public static String tattoo(final String kind)
	{
		return "KNOWN_" + kind.toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	/** whether a viewer knows what the item is; anything without a kind, and no viewer, is known */
	public static boolean knows(final MOB viewer, final Item I)
	{
		final String kind = kind(I);
		if(kind.isEmpty() || (viewer == null))
			return true;
		return viewer.findTattoo(tattoo(kind)) != null;
	}

	/** the unknown name, for anyone to refer to the item by (matching does not tell who looks); "" for none */
	public static String alias(final Environmental E)
	{
		final Ability p = prop(E);
		return (p == null) ? "" : parm(p, "NAME", "something unfamiliar");
	}

	/** the name a viewer sees */
	public static String name(final MOB viewer, final Item I, final String real)
	{
		return knows(viewer, I) ? real : parm(prop(I), "NAME", "something unfamiliar");
	}

	/** the look in the room a viewer sees; an item with no look (one the room's description names) still has none */
	public static String display(final MOB viewer, final Item I, final String real)
	{
		if(knows(viewer, I) || (real == null) || real.isEmpty())
			return real;
		final String d = parm(prop(I), "DISPLAY", "");
		return d.isEmpty() ? CMStrings.capitalizeFirstLetter(parm(prop(I), "NAME", "something unfamiliar")) + " lies here." : d;
	}

	/** the description a viewer reads */
	public static String description(final MOB viewer, final Item I, final String real)
	{
		if(knows(viewer, I))
			return real;
		final String d = parm(prop(I), "DESC", "");
		return d.isEmpty() ? "You cannot tell what it is." : d;
	}

	/** the value a viewer takes it to have: what VALUE says until they know it (the real one when it says nothing) */
	public static double value(final MOB viewer, final Item I, final double real)
	{
		if(knows(viewer, I))
			return real;
		final String v = parm(prop(I), "VALUE", "");
		return v.isEmpty() ? real : Math.max(0, CMath.s_double(v));
	}

	/** whether a way reveals it: IDENTIFY, APPRAISE, EXAMINE or USE, as its REVEAL lists (IDENTIFY when it lists none) */
	public static boolean reveals(final Item I, final String how)
	{
		final Ability p = prop(I);
		if(p == null)
			return false;
		for(final String w : CMParms.parseCommas(parm(p, "REVEAL", "IDENTIFY"), true))
		{
			if(w.trim().equalsIgnoreCase(how))
				return true;
		}
		return false;
	}

	/**
	 * Teaches a character an item's kind, if the way reveals it and they may
	 * (its MASK); once, for every item of the kind.
	 * @return what they learn, in words, or "" when nothing changed
	 */
	public static String reveal(final MOB who, final Item I, final String how)
	{
		final String kind = kind(I);
		if((who == null) || kind.isEmpty() || knows(who, I) || !reveals(I, how))
			return "";
		final String mask = parm(prop(I), "MASK", "");
		if(!mask.isEmpty() && !CMLib.masking().maskCheck(mask, who, true))
			return "";
		final String was = name(who, I, I.name());
		who.addTattoo(tattoo(kind));
		ModuleStats.count(FAMILY, kind.toLowerCase(Locale.ROOT), "reveals by " + how.toLowerCase(Locale.ROOT));
		return CMLib.lang().L("You recognize @x1: it is @x2.", was, I.name());
	}
}
