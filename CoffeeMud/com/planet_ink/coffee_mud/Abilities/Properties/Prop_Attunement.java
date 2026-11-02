package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
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
 * I23: powers that wake only once the item is attuned.  PROPS=
 * "Prop_WearEnabler(Spell_Haste);Prop_WearAdjuster(dex+2)" are inert until
 * then.  HOW=REST:10 (rest ten ticks with it), RITUAL="INROOM Shrine#3 & SAY
 * attune" (both), or ON=EQUIP (wearing it); LIMIT=3 how many one may be
 * attuned to; BIND=true: it works for no one else; MSG= what the one who
 * attunes is told; MASK= who may.  UNATTUNE item frees it.  Kept in its
 * text (ATTUNED=name).  Identifying it says it needs attunement.
 */
public class Prop_Attunement extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Attunement";
	}

	@Override
	public String name()
	{
		return "Needs attunement";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return (attuned.length() > 0) ? ("Attuned to " + attuned) : "Requires attunement";
	}

	protected String						base		= "";
	protected Map<String, String>			parms		= new HashMap<String, String>();
	protected String						attuned		= "";
	protected int							restNeeded	= 0;
	protected int							rested		= 0;
	protected String						ritualRoom	= "";
	protected String						ritualWord	= "";
	protected boolean						onEquip		= false;
	protected int							limit		= 3;
	protected boolean						bind		= false;
	protected MaskingLibrary.CompiledZMask	mask		= null;
	protected final List<Ability>			woken		= new ArrayList<Ability>();
	protected boolean						ticking		= false;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final String t = (newText == null) ? "" : newText;
		parms = CMParms.parseExactParms(t);
		attuned = CMParms.getParmStr(parms, "ATTUNED", "");
		final String how = CMParms.getParmStr(parms, "HOW", "").toUpperCase();
		restNeeded = how.startsWith("REST") ? Math.max(1, CMath.s_int(how.substring(how.indexOf(':') + 1))) : 0;
		ritualRoom = "";
		ritualWord = "";
		for(final String part : CMParms.getParmStr(parms, "RITUAL", "").split("&"))
		{
			final String p = part.trim();
			if(p.toUpperCase().startsWith("INROOM "))
				ritualRoom = p.substring(7).trim();
			else
			if(p.toUpperCase().startsWith("SAY "))
				ritualWord = p.substring(4).trim();
		}
		onEquip = CMParms.getParmStr(parms, "ON", "").equalsIgnoreCase("EQUIP");
		limit = Math.max(1, CMParms.getParmInt(parms, "LIMIT", 3));
		bind = CMParms.getParmBool(parms, "BIND", false);
		final String m = CMParms.getParmStr(parms, "MASK", "").trim();
		mask = (m.length() == 0) ? null : CMLib.masking().getPreCompiledMask(m);
		base = t.replaceAll("(?i)\\s*\\bATTUNED=(\"[^\"]*\"|\\S*)", "").trim();
	}

	@Override
	public String text()
	{
		return (attuned.length() > 0) ? (base + " ATTUNED=\"" + attuned + "\"") : base;
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		super.setAffectedOne(P);
		if((P != null) && (!ticking))
		{
			ticking = true;
			CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
		}
	}

	/**
	 * @return to whom it is attuned, or ""
	 */
	public String attunedTo()
	{
		return attuned;
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("ATTUNED"))
			return attuned;
		return super.getStat(code);
	}

	@Override
	public void setStat(final String code, final String val)
	{
		if(code.equalsIgnoreCase("UNATTUNE"))
			unattune();
		else
			super.setStat(code, val);
	}

	/**
	 * How many items one is attuned to, of those they carry.
	 * @param M one
	 * @return the count
	 */
	public static int attunedCount(final MOB M)
	{
		int n = 0;
		for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
		{
			final Item I = i.nextElement();
			final Ability A = (I == null) ? null : I.fetchEffect("Prop_Attunement");
			if((A instanceof Prop_Attunement) && ((Prop_Attunement)A).attuned.equalsIgnoreCase(M.Name()))
				n++;
		}
		return n;
	}

	/**
	 * One attunes to it.
	 * @param M one
	 * @return true if they did
	 */
	public boolean attune(final MOB M)
	{
		if((M == null) || (!(affected instanceof Item)) || (attuned.length() > 0))
			return false;
		if((mask != null) && (!CMLib.masking().maskCheck(mask, M, true)))
		{
			M.tell(M, affected, null, CMLib.lang().L("<T-NAME> does not answer you."));
			return false;
		}
		if(attunedCount(M) >= limit)
		{
			M.tell(CMLib.lang().L("You cannot be attuned to more than @x1 items.", "" + limit));
			return false;
		}
		attuned = M.Name();
		M.tell(M, affected, null, CMParms.getParmStr(parms, "MSG", CMLib.lang().L("You are attuned to <T-NAME>.")));
		wake();
		return true;
	}

	/**
	 * Frees it.
	 */
	public void unattune()
	{
		attuned = "";
		rested = 0;
		sleep();
	}

	/** the powers on */
	protected void wake()
	{
		if((!woken.isEmpty()) || (affected == null))
			return;
		for(final String[] w : Prop_When.parseWraps(CMParms.getParmStr(parms, "PROPS", "")))
		{
			final Ability A = CMClass.getAbility(w[0]);
			if(A != null)
			{
				A.setMiscText(w[1]);
				A.setSavable(false);
				affected.addNonUninvokableEffect(A);
				woken.add(A);
			}
		}
		recover();
	}

	/** the powers off */
	protected void sleep()
	{
		for(final Ability A : woken)
		{
			if(affected != null)
				affected.delEffect(A);
			A.setAffectedOne(null);
		}
		woken.clear();
		recover();
	}

	protected void recover()
	{
		if(affected == null)
			return;
		affected.recoverPhyStats();
		if((affected instanceof Item) && (((Item)affected).owner() instanceof MOB))
		{
			final MOB M = (MOB)((Item)affected).owner();
			M.recoverPhyStats();
			M.recoverCharStats();
			M.recoverMaxState();
		}
	}

	protected MOB holder()
	{
		return ((affected instanceof Item) && (((Item)affected).owner() instanceof MOB)) ? (MOB)((Item)affected).owner() : null;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(!(affected instanceof Item))
			return;
		final MOB M = holder();
		if((M == null) || (msg.source() != M) || (attuned.length() > 0))
			return;
		if(onEquip && (msg.target() == affected)
		&& ((msg.targetMinor() == CMMsg.TYP_WEAR) || (msg.targetMinor() == CMMsg.TYP_HOLD) || (msg.targetMinor() == CMMsg.TYP_WIELD)))
			attune(M);
		else
		if((ritualWord.length() > 0) && (msg.sourceMinor() == CMMsg.TYP_SPEAK) && (msg.sourceMessage() != null))
		{
			final String said = CMStrings.getSayFromMessage(msg.sourceMessage());
			final boolean place = (ritualRoom.length() == 0) || ((M.location() != null) && CMLib.map().getExtendedRoomID(M.location()).equalsIgnoreCase(ritualRoom));
			if(place && (said != null) && said.toLowerCase().contains(ritualWord.toLowerCase()))
				attune(M);
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		if((!(affected instanceof Item)) || ((Item)affected).amDestroyed())
		{
			this.ticking = false;
			return false;
		}
		final MOB M = holder();
		if(attuned.length() > 0)
		{
			// bound: inert in another's hands
			final boolean theirs = (M != null) && M.Name().equalsIgnoreCase(attuned);
			if(bind && (!theirs))
				sleep();
			else
				wake();
			return true;
		}
		if((restNeeded > 0) && (M != null) && (CMLib.flags().isSitting(M) || CMLib.flags().isSleeping(M)))
		{
			if(++rested >= restNeeded)
				attune(M);
		}
		else
			rested = 0;
		return true;
	}
}
