package com.planet_ink.coffee_mud.Abilities.Properties;
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
 * W3: a typed hazard zone on a room or an area: each tick (EVERY=), those
 * in it that the masks let through take DAMAGE= (dice or a formula, @x1
 * their level) as that TYPE=, so its saves and resistances count, or lose
 * moves or mana (DRAIN=), and may catch an AFFECT=.  ROOMS= and WHEN= are
 * masks checked on each victim every tick (where they are, the weather),
 * PROTECT= the mask of those it spares; ONSET= ticks before it starts,
 * ESCALATE= percent more each time after, WARN= said once as they come in,
 * DARKONLY, LITONLY and NONPC (players only) narrow it.
 */
public class Prop_Hazard extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Hazard";
	}

	@Override
	public String name()
	{
		return "A hazard zone";
	}

	@Override
	public long flags()
	{
		return super.flags() | Ability.FLAG_POTENTIALLY_DEADLY;
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS | Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "a " + type.toLowerCase() + " hazard";
	}

	/** the hazard types: their message type, save stat, and what hurts */
	protected static final String[][]	TYPES	= {
		{ "COLD", "" + CMMsg.TYP_COLD, "" + CharStats.STAT_SAVE_COLD, "the biting cold" },
		{ "HEAT", "" + CMMsg.TYP_FIRE, "" + CharStats.STAT_SAVE_FIRE, "the searing heat" },
		{ "ACID", "" + CMMsg.TYP_ACID, "" + CharStats.STAT_SAVE_ACID, "the acid" },
		{ "GAS", "" + CMMsg.TYP_GAS, "" + CharStats.STAT_SAVE_GAS, "the foul gas" },
		{ "POISON", "" + CMMsg.TYP_POISON, "" + CharStats.STAT_SAVE_POISON, "the poison" },
		{ "ELECTRIC", "" + CMMsg.TYP_ELECTRIC, "" + CharStats.STAT_SAVE_ELECTRIC, "the lightning" },
		{ "HOLY", "" + CMMsg.TYP_JUSTICE, "" + CharStats.STAT_SAVE_JUSTICE, "the holy light" },
		{ "UNHOLY", "" + CMMsg.TYP_UNDEAD, "" + CharStats.STAT_SAVE_UNDEAD, "the unholy taint" },
		{ "DISEASE", "" + CMMsg.TYP_DISEASE, "" + CharStats.STAT_SAVE_DISEASE, "the sickness" },
		{ "THINAIR", "" + CMMsg.TYP_GAS, "" + CharStats.STAT_SAVE_GAS, "the thin air" },
	};

	protected String		type		= "COLD";
	protected int			msgType		= CMMsg.TYP_COLD;
	protected int			saveStat	= -1;
	protected String		damage		= "";
	protected String		drain		= "";
	protected String		affectID	= "";
	protected int			affectPct	= 0;
	protected String		roomMask	= null;
	protected String		whenMask	= null;
	protected String		protectMask	= null;
	protected int			every		= 1;
	protected int			onset		= 0;
	protected int			escalate	= 0;
	protected boolean		darkOnly	= false;
	protected boolean		litOnly		= false;
	protected boolean		npcToo		= true;
	protected String		warn		= "";
	protected String		hurter		= "the biting cold";
	protected String		message		= "<S-NAME> <DAMAGES> <T-NAME>!";
	protected String		drainMsg	= "";
	protected boolean		ticking		= false;

	/** ticks each victim has been in it, in a row; harms done */
	protected final Map<MOB, int[]>	exposure	= new WeakHashMap<MOB, int[]>();

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		final Map<String, String> P = CMParms.parseEQParms(newMiscText);
		final List<String> words = CMParms.parse(newMiscText.toUpperCase());
		type = P.containsKey("TYPE") ? P.get("TYPE").trim().toUpperCase() : "COLD";
		String[] T = TYPES[0];
		for(final String[] t : TYPES)
		{
			if(t[0].equals(type))
				T = t;
		}
		type = T[0];
		msgType = CMath.s_int(T[1]);
		saveStat = CMath.s_int(T[2]);
		hurter = P.containsKey("NAME") ? P.get("NAME") : T[3];
		if(P.containsKey("SAVE"))
		{
			saveStat = -1;
			final String sv = P.get("SAVE").trim().toUpperCase();
			for(final String[] t : TYPES)
			{
				if(t[0].equals(sv))
					saveStat = CMath.s_int(t[2]);
			}
			if(saveStat < 0)
			{
				final int code = CMParms.indexOf(CharStats.CODES.NAMES(), "SAVE_" + sv);
				saveStat = (code >= 0) ? code : CMParms.indexOf(CharStats.CODES.NAMES(), sv);
			}
		}
		damage = P.containsKey("DAMAGE") ? P.get("DAMAGE").trim() : "";
		drain = P.containsKey("DRAIN") ? P.get("DRAIN").trim().toUpperCase() : (type.equals("THINAIR") ? "MOVE" : "");
		if((damage.length() == 0) && (drain.length() > 0))
			damage = "1d6";
		affectID = "";
		affectPct = 0;
		if(P.containsKey("AFFECT"))
		{
			final List<String> a = CMParms.parse(P.get("AFFECT"));
			if(a.size() > 0)
			{
				affectID = a.get(0);
				affectPct = (a.size() > 1) ? CMath.s_int(a.get(1).replace("%", "")) : 100;
			}
		}
		roomMask = mask(P, "ROOMS");
		whenMask = mask(P, "WHEN");
		protectMask = mask(P, "PROTECT");
		every = Math.max(1, P.containsKey("EVERY") ? CMath.s_int(P.get("EVERY")) : 1);
		onset = Math.max(0, P.containsKey("ONSET") ? CMath.s_int(P.get("ONSET")) : 0);
		escalate = P.containsKey("ESCALATE") ? CMath.s_int(P.get("ESCALATE").replace("%", "")) : 0;
		darkOnly = words.contains("DARKONLY");
		litOnly = words.contains("LITONLY");
		npcToo = !words.contains("NONPC");
		warn = P.containsKey("WARN") ? P.get("WARN") : "";
		message = P.containsKey("MSG") ? P.get("MSG") : "<S-NAME> <DAMAGES> <T-NAME>!";
		drainMsg = P.containsKey("DRAINMSG") ? P.get("DRAINMSG") : ("You feel " + hurter + " sap your " + (drain.equals("MANA") ? "mana" : "strength") + ".");
		synchronized(exposure)
		{
			exposure.clear();
		}
	}

	private static String mask(final Map<String, String> P, final String key)
	{
		final String v = P.get(key);
		return ((v == null) || (v.trim().length() == 0)) ? null : v;
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		super.setAffectedOne(P);
		if(((P instanceof Room) || (P instanceof Area)) && (!ticking))
		{
			ticking = true;
			CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
		}
	}

	/**
	 * The harm of one hit, before saves and resistances.
	 * @param M the victim
	 * @param hits how many times it has hurt them in a row
	 * @return the amount
	 */
	protected int amount(final MOB M, final int hits)
	{
		final String d = damage.replace("@x1", "" + M.phyStats().level()).replace(" ", "");
		int base;
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)d(\\d+)([+-]\\d+)?$").matcher(d.toLowerCase());
		if(m.find())
			base = CMLib.dice().roll(CMath.s_int(m.group(1)), CMath.s_int(m.group(2)), (m.group(3) == null) ? 0 : CMath.s_int(m.group(3).replace("+", "")));
		else
		{
			try
			{
				base = (int)Math.round(CMath.parseMathExpression(d));
			}
			catch(final Exception e)
			{
				base = 0;
			}
		}
		if((escalate != 0) && (hits > 0))
			base = (int)Math.round(base * (1.0 + ((escalate / 100.0) * hits)));
		return Math.max(0, base);
	}

	/**
	 * Whether the hazard acts on a mob now.
	 * @param M the mob
	 * @param R where it is
	 * @return true if it does
	 */
	protected boolean acts(final MOB M, final Room R)
	{
		if((M == null) || M.amDead() || (M.location() != R))
			return false;
		if((!npcToo) && (!M.isPlayer()))
			return false;
		if(CMSecurity.isAllowed(M, R, CMSecurity.SecFlag.IMMORT))
			return false;
		final boolean dark = CMLib.flags().isInDark(R) && (!CMLib.flags().canSeeInDark(M));
		if((darkOnly && (!dark)) || (litOnly && dark))
			return false;
		if((roomMask != null) && (!CMLib.masking().maskCheck(roomMask, M, true)))
			return false;
		if((whenMask != null) && (!CMLib.masking().maskCheck(whenMask, M, true)))
			return false;
		if((protectMask != null) && CMLib.masking().maskCheck(protectMask, M, true))
			return false;
		return true;
	}

	/**
	 * One tick of the hazard on one mob in it.
	 * @param M the mob
	 * @param R its room
	 */
	protected void hurt(final MOB M, final Room R)
	{
		int[] e;
		synchronized(exposure)
		{
			e = exposure.get(M);
			if(e == null)
			{
				e = new int[] { 0, 0, 0 }; // ticks, harms, the tick last seen
				exposure.put(M, e);
			}
		}
		e[0]++;
		e[2] = 1;
		if((e[0] == 1) && (warn.length() > 0))
			M.tell(warn);
		if(e[0] <= onset)
			return;
		if(((e[0] - onset - 1) % every) != 0)
			return;
		int dmg = amount(M, e[1]);
		e[1]++;
		if((saveStat >= 0) && (CMLib.dice().rollPercentage() <= M.charStats().getStat(saveStat)))
			dmg = dmg / 2;
		final MOB hurterM = CMLib.map().getFactoryMOB(R);
		try
		{
			hurterM.setName(hurter);
			if(drain.equals("MOVE") || drain.equals("MANA"))
			{
				if(drain.equals("MOVE"))
					M.curState().adjMovement(-dmg, M.maxState());
				else
					M.curState().adjMana(-dmg, M.maxState());
				if(drainMsg.length() > 0)
					M.tell(drainMsg);
			}
			else
			if(dmg > 0)
				CMLib.combat().postDamage(hurterM, M, null, dmg, CMMsg.MASK_ALWAYS | CMMsg.MASK_MALICIOUS | msgType, Weapon.TYPE_BURSTING, message);
			if((affectID.length() > 0) && (!M.amDead()) && (M.fetchEffect(affectID) == null) && (CMLib.dice().rollPercentage() <= affectPct))
			{
				final Ability A = CMClass.getAbility(affectID);
				if(A != null)
					A.invoke(hurterM, M, true, 0);
			}
		}
		finally
		{
			hurterM.destroy();
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		final Physical P = affected;
		if((P == null) || (P.fetchEffect(ID()) != this) || ((P instanceof Area) && (((Area)P).amDestroyed())))
		{
			this.ticking = false;
			return false;
		}
		synchronized(exposure)
		{
			for(final int[] e : exposure.values())
				e[2] = 0;
		}
		final List<Room> rooms = new ArrayList<Room>();
		if(P instanceof Room)
			rooms.add((Room)P);
		else
		{
			for(final Enumeration<Room> r = ((Area)P).getMetroMap(); r.hasMoreElements();)
			{
				final Room R = r.nextElement();
				if((R != null) && (R.numInhabitants() > 0))
					rooms.add(R);
			}
		}
		for(final Room R : rooms)
		{
			for(final MOB M : new XVector<MOB>(R.inhabitants()))
			{
				if(acts(M, R))
					hurt(M, R);
			}
		}
		// those no longer in it start again when they come back
		synchronized(exposure)
		{
			for(final Iterator<int[]> i = exposure.values().iterator(); i.hasNext();)
			{
				if(i.next()[2] == 0)
					i.remove();
			}
		}
		return true;
	}

	/**
	 * For tests and DEBUG: how long a mob has been in it, and the harms done.
	 * @param M the mob
	 * @return ticks, harms, or null if it is not in it
	 */
	public int[] exposureOf(final MOB M)
	{
		synchronized(exposure)
		{
			final int[] e = exposure.get(M);
			return (e == null) ? null : new int[] { e[0], e[1] };
		}
	}
}
