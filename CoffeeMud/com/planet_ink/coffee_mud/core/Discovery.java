package com.planet_ink.coffee_mud.core;

import java.util.List;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.PlayerAccount;
import com.planet_ink.coffee_mud.Exits.interfaces.Exit;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.Physical;
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
 * P11: experience and marks for discoveries (Prop_Discovery on a room, exit,
 * item or mob: XP, ON, ONCE, MESSAGE, TATTOO, MASK, JOURNAL), and DISCOVERYXP,
 * a formula paid for each room a character enters for the first time.  The
 * experience is tagged DISCOVERY:, so XPMOD can scale it.
 */
public class Discovery
{
	private Discovery()
	{
	}

	/**
	 * @param P what carries a Prop_Discovery
	 * @return the event it waits for when ON= is not given
	 */
	public static String defaultOn(final Physical P)
	{
		if(P instanceof Room)
			return "ENTER";
		if(P instanceof Exit)
			return "OPEN";
		if(P instanceof Item)
			return "GET";
		if(P instanceof MOB)
			return "TALK";
		return "LOOK";
	}

	/**
	 * The tattoo a discovery is kept as: TATTOO=, else one made from what it is.
	 * @param P the discovery
	 * @param text its Prop_Discovery's text
	 * @return the tattoo
	 */
	public static String key(final Physical P, final String text)
	{
		final String t = CMParms.getParmStr(text, "TATTOO", "").trim();
		if(t.length() > 0)
			return t.toUpperCase();
		final Room R = CMLib.map().roomLocation(P);
		final String where = (P instanceof Room) ? CMLib.map().getExtendedRoomID((Room)P) : ((R == null) ? "" : CMLib.map().getExtendedRoomID(R));
		return "DISCOVERY_" + Integer.toHexString((P.ID() + "@" + P.Name() + "@" + where).hashCode()).toUpperCase();
	}

	private static List<MOB> party(final MOB mob)
	{
		return Party.inOrder(Party.leaderOf(mob));
	}

	/**
	 * Whether a discovery has been made already, by the character, their
	 * party or their profile.
	 * @param mob the character
	 * @param key its tattoo
	 * @param once CHARACTER, PARTY or PROFILE
	 * @return true if it has
	 */
	public static boolean already(final MOB mob, final String key, final String once)
	{
		if(mob.findTattoo(key) != null)
			return true;
		if(once.startsWith("PROF"))
		{
			final PlayerAccount acct = Party.profileOf(mob);
			return (acct != null) && (acct.findTattoo(key) != null);
		}
		if(once.startsWith("PART"))
		{
			for(final MOB M : party(mob))
			{
				if(M.findTattoo(key) != null)
					return true;
			}
		}
		return false;
	}

	private static void mark(final MOB mob, final String key, final String once)
	{
		mob.addTattoo(key);
		if(once.startsWith("PROF"))
		{
			final PlayerAccount acct = Party.profileOf(mob);
			if((acct != null) && (acct.findTattoo(key) == null))
				acct.addTattoo(key);
		}
		else
		if(once.startsWith("PART"))
		{
			for(final MOB M : party(mob))
			{
				if(M.findTattoo(key) == null)
					M.addTattoo(key);
			}
		}
	}

	/**
	 * Experience by an amount or a percent of the character's level.
	 * @param mob the character
	 * @param xp as 150 or 10%
	 * @return the amount
	 */
	public static int amount(final MOB mob, final String xp)
	{
		final String x = (xp == null) ? "" : xp.trim();
		if(x.length() == 0)
			return 0;
		if(x.endsWith("%"))
		{
			final int L = mob.basePhyStats().level();
			final int span = CMLib.leveler().getLevelExperience(mob, L) - ((L <= 1) ? 0 : CMLib.leveler().getLevelExperience(mob, L - 1));
			return (int)Math.round(span * CMath.s_pct(x));
		}
		return CMath.s_int(x);
	}

	/**
	 * A Prop_Discovery's event happened: the first time it is the character's
	 * (or party's, or profile's), they are paid and marked.
	 * @param mob the character
	 * @param P what carries the property
	 * @param text the property's text
	 * @param event ENTER, LOOK, READ, OPEN, GET, FIND or TALK
	 * @return true if it was a discovery
	 */
	public static boolean fire(final MOB mob, final Physical P, final String text, final String event)
	{
		if((mob == null) || (P == null) || (mob.playerStats() == null) || mob.isMonster())
			return false;
		final String on = CMParms.getParmStr(text, "ON", defaultOn(P)).toUpperCase();
		boolean matches = false;
		for(final String o : CMParms.parseCommas(on.replace('|', ','), true))
			matches = matches || o.trim().equals(event);
		if(!matches)
			return false;
		final String mask = CMParms.getParmStr(text, "MASK", "").trim();
		if((mask.length() > 0) && (!CMLib.masking().maskCheck(mask, mob, true)))
			return false;
		final String once = CMParms.getParmStr(text, "ONCE", "CHARACTER").trim().toUpperCase();
		final String key = key(P, text);
		if(already(mob, key, once))
			return false;
		mark(mob, key, once);
		final String message = CMParms.getParmStr(text, "MESSAGE", "").trim();
		if(message.length() > 0)
			mob.tell(mob, P, null, message);
		final int xp = amount(mob, CMParms.getParmStr(text, "XP", ""));
		if(xp > 0)
			CMLib.leveler().postExperience(mob, "DISCOVERY:" + key, null, null, xp, false);
		final String journal = CMParms.getParmStr(text, "JOURNAL", "").trim();
		if(journal.length() > 0)
			QuestJournal.journal(mob, "", journal);
		return true;
	}

	/**
	 * I11: a hidden thing was found; its Prop_Discovery with ON=FIND fires.
	 * @param mob who found it
	 * @param P what was found
	 */
	public static void found(final MOB mob, final Physical P)
	{
		final Ability A = (P == null) ? null : P.fetchEffect("Prop_Discovery");
		if(A != null)
			fire(mob, P, A.text(), "FIND");
	}

	/**
	 * A character entered a room for the first time: DISCOVERYXP pays them.
	 * @param mob the character
	 * @param R the room
	 */
	public static void firstVisit(final MOB mob, final Room R)
	{
		if((mob == null) || (R == null) || (mob.playerStats() == null) || mob.isMonster())
			return;
		final CMProps P = CMProps.instance();
		final String formula = (P == null) ? "" : P.getStr("DISCOVERYXP", "").trim();
		if(formula.length() == 0)
			return;
		final int xp = CMath.parseIntExpression(formula, new double[] { mob.basePhyStats().level(), (R.getArea() == null) ? 0 : R.getArea().getIStat(com.planet_ink.coffee_mud.Areas.interfaces.Area.Stats.MED_LEVEL) });
		if(xp > 0)
			CMLib.leveler().postExperience(mob, "DISCOVERY:ROOM", null, null, xp, true);
	}
}
