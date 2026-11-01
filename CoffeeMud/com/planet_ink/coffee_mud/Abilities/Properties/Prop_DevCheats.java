package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.CMSecurity.SecFlag;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
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
 * A developer's cheats on a character, which GOD turns on and off, saved with
 * the character like any effect.  The text is a list of words.  A cheat's
 * name turns it on.  A cheat's name after a minus turns it off, even where a
 * sysop power would give it.  A security flag after a minus (-IMMORT)
 * suspends that sysop power for this character.  The cheats:
 * <ul>
 * <li>NOCLIP: walks through closed and locked doors and past Prop_Req* entry
 * checks, and walking costs no movement.  GOTO gives the first part.</li>
 * <li>NOCOST: skills cost no mana, movement or hit points, and need no wait
 * between uses.  ALLSKILLS gives the second part.</li>
 * <li>NODAMAGE: takes no damage.</li>
 * <li>NODEATH: hit points stop at 1, as IMMORT does.  -NODEATH also suspends
 * IMMORT.</li>
 * <li>SEEALL: sees in the dark, the hidden, the invisible and those who sneak,
 * and is never blind.</li>
 * <li>NOAGGRO: aggressive creatures leave the character be.  ORDER or
 * CMDROOMS gives it.</li>
 * </ul>
 * The code that each cheat changes asks {@link #state(MOB, Cheat)} or one of
 * the helpers below; the suspended powers go to
 * {@link CMSecurity#setSuspended(MOB, Collection)}.
 */
public class Prop_DevCheats extends Property
{
	/** The cheats. */
	public enum Cheat
	{
		NOCLIP("walk through doors, locks and entry checks; walking costs nothing", "GOTO"),
		NOCOST("skills cost nothing and need no wait", "ALLSKILLS"),
		NODAMAGE("take no damage", ""),
		NODEATH("hit points stop at 1", "IMMORT"),
		SEEALL("see the dark, the hidden and the invisible; never blind", ""),
		NOAGGRO("aggressive creatures leave you be", "ORDER or CMDROOMS");

		/** what the cheat does */
		public final String	what;
		/** the sysop power that gives it (or a part of it) when it is not set */
		public final String	power;

		private Cheat(final String what, final String power)
		{
			this.what = what;
			this.power = power;
		}
	}

	/** the senses SEEALL gives */
	private static final int SEEALL_SENSES = PhyStats.CAN_SEE_DARK | PhyStats.CAN_SEE_HIDDEN | PhyStats.CAN_SEE_INVISIBLE
											| PhyStats.CAN_SEE_SNEAKERS | PhyStats.CAN_SEE_INFRARED;

	/** each character with cheats, so that a check is quick */
	private static final Map<MOB, Prop_DevCheats>	holders	= new WeakHashMap<MOB, Prop_DevCheats>();
	/** whether anyone has cheats, so that a check costs nothing when no one has */
	private static volatile boolean					any		= false;

	private Map<Cheat, Boolean>	cheats		= new EnumMap<Cheat, Boolean>(Cheat.class);
	private Set<SecFlag>		suspended	= EnumSet.noneOf(SecFlag.class);

	@Override
	public String ID()
	{
		return "Prop_DevCheats";
	}

	@Override
	public String name()
	{
		return "Developer Cheats";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "Developer cheats: " + text();
	}

	@Override
	public CMObject copyOf()
	{
		final Prop_DevCheats P = (Prop_DevCheats)super.copyOf();
		P.cheats = new EnumMap<Cheat, Boolean>(Cheat.class);
		P.suspended = EnumSet.noneOf(SecFlag.class);
		P.parse(text());
		return P;
	}

	/**
	 * Reads a word of the text: a cheat, -cheat, or -flag.
	 * @param word the word
	 * @return a cheat, a flag, or null if it is neither
	 */
	public static Object wordOf(final String word)
	{
		final String w = word.startsWith("-") ? word.substring(1).toUpperCase().trim() : word.toUpperCase().trim();
		final Cheat C = (Cheat)CMath.s_valueOf(Cheat.class, w);
		if(C != null)
			return C;
		return CMath.s_valueOf(SecFlag.class, w);
	}

	private void parse(final String text)
	{
		cheats.clear();
		suspended.clear();
		for(final String word : CMParms.parse(text == null ? "" : text))
		{
			final boolean minus = word.startsWith("-");
			final Object O = wordOf(word);
			if(O instanceof Cheat)
				cheats.put((Cheat)O, Boolean.valueOf(!minus));
			else
			if((O instanceof SecFlag) && minus)
				suspended.add((SecFlag)O);
		}
	}

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		parse(newMiscText);
		register();
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		if((affected instanceof MOB) && (affected != P))
			unregister((MOB)affected);
		super.setAffectedOne(P);
		register();
	}

	/**
	 * The powers this character has suspended: the -flags, and IMMORT under
	 * -NODEATH.
	 * @return the powers
	 */
	public Set<SecFlag> suspendedPowers()
	{
		final Set<SecFlag> all = EnumSet.noneOf(SecFlag.class);
		all.addAll(suspended);
		if(cheats.get(Cheat.NODEATH) == Boolean.FALSE)
			all.add(SecFlag.IMMORT);
		return all;
	}

	/**
	 * The cheats set on or off, in order.
	 * @return the cheats and whether each is on
	 */
	public Map<Cheat, Boolean> cheats()
	{
		return Collections.unmodifiableMap(cheats);
	}

	private void register()
	{
		if(!(affected instanceof MOB))
			return;
		final MOB mob = (MOB)affected;
		synchronized(holders)
		{
			holders.put(mob, this);
			any = true;
		}
		CMSecurity.setSuspended(mob, suspendedPowers());
	}

	private void unregister(final MOB mob)
	{
		synchronized(holders)
		{
			if(holders.get(mob) != this)
				return;
			holders.remove(mob);
			any = !holders.isEmpty();
		}
		CMSecurity.setSuspended(mob, null);
	}

	/**
	 * Returns a character's cheats, if it has any.
	 * @param mob the character
	 * @return its cheats, or null
	 */
	public static Prop_DevCheats of(final MOB mob)
	{
		if((!any) || (mob == null))
			return null;
		final Prop_DevCheats P;
		synchronized(holders)
		{
			P = holders.get(mob);
		}
		if((P != null) && ((P.affected != mob) || (mob.fetchEffect(P.ID()) != P)))
		{
			P.unregister(mob);
			return null;
		}
		return P;
	}

	/**
	 * Whether a cheat is set on or off for a character.
	 * @param mob the character
	 * @param C the cheat
	 * @return true if on, false if off, null if not set (the sysop powers decide)
	 */
	public static Boolean state(final MOB mob, final Cheat C)
	{
		final Prop_DevCheats P = of(mob);
		return (P == null) ? null : P.cheats.get(C);
	}

	/**
	 * Whether a cheat is set on for a character.
	 * @param mob the character
	 * @param C the cheat
	 * @return true if it is on
	 */
	public static boolean on(final MOB mob, final Cheat C)
	{
		return state(mob, C) == Boolean.TRUE;
	}

	/**
	 * Whether a character walks past doors, locks and entry checks: NOCLIP if it
	 * is set, or else GOTO (for a player).
	 * @param mob the character
	 * @param R the room it walks to
	 * @return true if nothing stops it
	 */
	public static boolean noclip(final MOB mob, final Room R)
	{
		final Boolean b = state(mob, Cheat.NOCLIP);
		if(b != null)
			return b.booleanValue();
		return (!mob.isMonster()) && CMSecurity.isAllowed(mob, R, SecFlag.GOTO);
	}

	/**
	 * Whether a character's skills need no wait between uses: NOCOST if it is
	 * set, or else ALLSKILLS.
	 * @param mob the character
	 * @param R where it is
	 * @return true if there is no wait
	 */
	public static boolean noWait(final MOB mob, final Room R)
	{
		final Boolean b = state(mob, Cheat.NOCOST);
		if(b != null)
			return b.booleanValue();
		return CMSecurity.isAllowed(mob, R, SecFlag.ALLSKILLS);
	}

	/**
	 * Whether aggressive creatures leave a character be: NOAGGRO if it is set,
	 * or else ORDER or CMDROOMS (both, if asked).
	 * @param mob the character
	 * @param R where it is
	 * @param both true if both powers are needed
	 * @return true if they leave it be
	 */
	public static boolean noAggro(final MOB mob, final Room R, final boolean both)
	{
		final Boolean b = state(mob, Cheat.NOAGGRO);
		if(b != null)
			return b.booleanValue();
		if(both)
			return CMSecurity.isAllowed(mob, R, SecFlag.ORDER) && CMSecurity.isAllowed(mob, R, SecFlag.CMDROOMS);
		return CMSecurity.isAllowed(mob, R, SecFlag.ORDER) || CMSecurity.isAllowed(mob, R, SecFlag.CMDROOMS);
	}

	/**
	 * Whether a cheat is in effect for a character, set or given by a power.
	 * @param mob the character
	 * @param C the cheat
	 * @return true if in effect
	 */
	public static boolean inEffect(final MOB mob, final Cheat C)
	{
		final Room R = mob.location();
		switch(C)
		{
		case NOCLIP:
			return noclip(mob, R);
		case NOCOST:
			return on(mob, C) || ((state(mob, C) == null) && CMSecurity.isAllowed(mob, R, SecFlag.ALLSKILLS));
		case NODEATH:
			return on(mob, C) || CMSecurity.isAllowed(mob, R, SecFlag.IMMORT);
		case NOAGGRO:
			return noAggro(mob, R, false);
		default:
			return on(mob, C);
		}
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(affected instanceof MOB)
		{
			final MOB mob = (MOB)affected;
			if((msg.sourceMinor() == CMMsg.TYP_DEATH)
			&& (msg.source() == mob)
			&& (cheats.get(Cheat.NODEATH) == Boolean.TRUE))
			{
				mob.curState().setHitPoints(1);
				if((msg.tool() != mob) && (msg.tool() instanceof MOB))
					((MOB)msg.tool()).tell(mob, null, null, L("<S-NAME> can not die."));
				mob.tell(L("You can not die (GOD NODEATH)."));
				return false;
			}
			if((msg.targetMinor() == CMMsg.TYP_DAMAGE)
			&& (msg.amITarget(mob))
			&& (msg.value() > 0)
			&& (cheats.get(Cheat.NODAMAGE) == Boolean.TRUE))
				msg.setValue(0);
		}
		return super.okMessage(myHost, msg);
	}

	@Override
	public void affectPhyStats(final Physical affected, final PhyStats affectableStats)
	{
		super.affectPhyStats(affected, affectableStats);
		if(cheats.get(Cheat.SEEALL) == Boolean.TRUE)
			affectableStats.setSensesMask((affectableStats.sensesMask() | SEEALL_SENSES) & ~PhyStats.CAN_NOT_SEE);
	}
}
