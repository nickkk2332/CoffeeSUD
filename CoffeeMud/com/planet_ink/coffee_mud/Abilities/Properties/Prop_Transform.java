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
 * I5: a thing that changes form over time or by condition.  AFTER=n ticks,
 * or DAYS=n game days (time left kept in the text, LEFT=), or WHEN= NIGHT,
 * DAY, DAWN, DUSK, FULLMOON, a season (WINTER...), or a zapper mask tested
 * on its room; START=GET|DROP|WEAR|ALWAYS when the time begins to run;
 * RESET=true starts again when that stops being so; ONLYIN=ROOM|CONTAINER|
 * CARRIED where the time runs.  Into: INTO= an item (catalog or class),
 * MOB= a mob (catalog or class), or SET="NAME=.. DISPLAY=.. DESC=..
 * PROPS=Prop_X(args);.." in place; REVERT=true goes back when WHEN ends;
 * KEEP=FOLLOW (the mob follows the one who carried it) and MPTRANSFORM's
 * STATE,TAGS,USES,CONTAINER,WORN (CONTAINER,WORN by default); MSG= what is
 * seen.  What it becomes may have its own Prop_Transform: a chain.
 */
public class Prop_Transform extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Transform";
	}

	@Override
	public String name()
	{
		return "Changes form";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS | Ability.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "Changes form in time";
	}

	/** the forms put away while a REVERT=true change lasts: the new thing, the old */
	protected static final Map<Physical, Physical>	putAway	= Collections.synchronizedMap(new WeakHashMap<Physical, Physical>());

	protected String		base		= "";
	protected Map<String, String>	parms	= new HashMap<String, String>();
	protected int			total		= 0;
	protected int			left		= 0;
	protected String		when		= "";
	protected String		start		= "ALWAYS";
	protected boolean		reset		= false;
	protected String		onlyIn		= "";
	protected boolean		revert		= false;
	protected boolean		started		= false;
	protected boolean		changed		= false;
	protected MOB			lastHolder	= null;
	protected boolean		ticking		= false;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final String t = (newText == null) ? "" : newText;
		parms = CMParms.parseExactParms(t);
		final int days = CMParms.getParmInt(parms, "DAYS", 0);
		if(days > 0)
		{
			final long hourTicks = Math.max(1, CMProps.getMillisPerMudHour() / Math.max(1, CMProps.getTickMillis()));
			final int hours = (CMLib.time().globalClock() != null) ? CMLib.time().globalClock().getHoursInDay() : 24;
			total = (int)Math.min(Integer.MAX_VALUE, hourTicks * hours * days);
		}
		else
			total = Math.max(0, CMParms.getParmInt(parms, "AFTER", 0));
		left = CMParms.getParmInt(parms, "LEFT", total);
		when = CMParms.getParmStr(parms, "WHEN", "").trim();
		start = CMParms.getParmStr(parms, "START", "ALWAYS").toUpperCase();
		reset = CMParms.getParmBool(parms, "RESET", false);
		onlyIn = CMParms.getParmStr(parms, "ONLYIN", "").toUpperCase();
		revert = CMParms.getParmBool(parms, "REVERT", false);
		started = CMParms.getParmBool(parms, "STARTED", false);
		changed = CMParms.getParmBool(parms, "CHANGED", false);
		base = t.replaceAll("(?i)\\s*\\bLEFT=\\S*", "").replaceAll("(?i)\\s*\\bSTARTED=\\S*", "").replaceAll("(?i)\\s*\\bCHANGED=\\S*", "").trim();
	}

	@Override
	public String text()
	{
		return base + (((total > 0) && (left != total)) ? (" LEFT=" + left) : "") + (started ? " STARTED=true" : "") + (changed ? " CHANGED=true" : "");
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
	 * @return the ticks left before it changes
	 */
	public int left()
	{
		return left;
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("LEFT"))
			return "" + left;
		return super.getStat(code);
	}

	protected Room room()
	{
		return CMLib.map().roomLocation(affected);
	}

	/**
	 * Whether WHEN holds now.
	 * @return true if it does
	 */
	public boolean whenHolds()
	{
		final Room R = room();
		if(R == null)
			return false;
		final TimeClock C = CMLib.time().localClock(R);
		final String w = when.toUpperCase();
		if(w.equals("NIGHT") || w.equals("DAY") || w.equals("DAWN") || w.equals("DUSK"))
			return (C != null) && C.getTODCode().name().equals(w);
		if(w.equals("FULLMOON"))
			return (C != null) && (C.getMoonPhase(R) == TimeClock.MoonPhase.FULL);
		for(final TimeClock.Season S : TimeClock.Season.values())
		{
			if(S.name().equals(w))
				return (C != null) && (C.getSeasonCode() == S);
		}
		final MOB M = CMLib.map().getFactoryMOB(R);
		try
		{
			return CMLib.masking().maskCheck(when, M, true);
		}
		finally
		{
			M.destroy();
		}
	}

	/** whether the time may run, where it is */
	protected boolean placeFits()
	{
		if(!(affected instanceof Item))
			return true;
		final Item I = (Item)affected;
		if(onlyIn.equals("ROOM"))
			return (I.owner() instanceof Room) && (I.container() == null);
		if(onlyIn.equals("CONTAINER"))
			return I.container() != null;
		if(onlyIn.equals("CARRIED"))
			return I.owner() instanceof MOB;
		return true;
	}

	/** whether START holds now */
	protected boolean startFits()
	{
		if(!(affected instanceof Item))
			return true;
		final Item I = (Item)affected;
		if(start.equals("GET"))
			return I.owner() instanceof MOB;
		if(start.equals("DROP"))
			return I.owner() instanceof Room;
		if(start.equals("WEAR"))
			return (I.owner() instanceof MOB) && (!I.amWearingAt(Wearable.IN_INVENTORY));
		return true;
	}

	protected void say(final String text, final Physical what)
	{
		if(text.length() == 0)
			return;
		final String t = CMStrings.capitalizeFirstLetter(text.replace("$p", what.name()));
		final Room R = CMLib.map().roomLocation(what);
		if(R != null)
			R.showHappens(CMMsg.MSG_OK_VISUAL, t);
	}

	/**
	 * Changes now.
	 * @return what it became, or null
	 */
	public Physical change()
	{
		final Physical P = affected;
		if(P == null)
			return null;
		final String keep = CMParms.getParmStr(parms, "KEEP", "").toUpperCase();
		final String msgText = CMParms.getParmStr(parms, "MSG", "");
		final String into = CMParms.getParmStr(parms, "INTO", "").trim();
		final String mob = CMParms.getParmStr(parms, "MOB", "").trim();
		final String set = CMParms.getParmStr(parms, "SET", "").trim();
		final Room R = room();
		if(P instanceof Item)
		{
			final Item I = (Item)P;
			if(I.owner() instanceof MOB)
				lastHolder = (MOB)I.owner();
		}
		if(set.length() > 0)
		{
			final Map<String, String> s = CMParms.parseExactParms(set);
			if(revert && (!changed))
				parms.put("WAS", "NAME=\"" + P.Name() + "\" DISPLAY=\"" + P.displayText() + "\" DESC=\"" + P.description() + "\"");
			apply(P, s);
			changed = true;
			say(msgText, P);
			return P;
		}
		if(into.length() > 0)
		{
			if(P instanceof Item)
			{
				final Set<String> k = Transform.keeps(keep.replace("FOLLOW", "").replace("DAMAGE", "USES").replace("CONTENTS", ""));
				if(keep.length() == 0)
					k.addAll(Transform.keeps(null));
				final Item neu = Transform.make(into);
				if(neu == null)
					return null;
				if(revert)
					putAway.put(neu, (Item)((Item)P).copyOf());
				final Item done = Transform.replace((Item)P, neu, k);
				if(done == null)
				{
					neu.destroy();
					return null;
				}
				say(msgText, done);
				return done;
			}
			if((P instanceof MOB) && (R != null))
			{
				final Item neu = Transform.make(into);
				if(neu == null)
					return null;
				R.addItem(neu, ItemPossessor.Expire.Never);
				if(revert)
				{
					((MOB)P).removeFromGame(false, false);
					putAway.put(neu, P);
					addRevert(neu);
				}
				else
					((MOB)P).destroy();
				say(msgText, neu);
				return neu;
			}
		}
		if((mob.length() > 0) && (R != null))
		{
			final Physical made = Encounters.make(mob, true);
			if(!(made instanceof MOB))
				return null;
			final MOB M = (MOB)made;
			M.setStartRoom(null);
			M.bringToLife(R, true);
			if(keep.contains("FOLLOW") && (lastHolder != null) && (lastHolder.location() == R))
				M.setFollowing(lastHolder);
			if(P instanceof Item)
			{
				final Item I = (Item)P;
				if(revert)
				{
					final ItemPossessor owner = I.owner();
					if(owner instanceof MOB)
						((MOB)owner).delItem(I);
					else
					if(owner instanceof Room)
						((Room)owner).delItem(I);
					putAway.put(M, I);
					addRevert(M);
				}
				else
					I.destroy();
			}
			say(msgText, M);
			return M;
		}
		return null;
	}

	/** the change back, on the new form */
	protected void addRevert(final Physical neu)
	{
		final Prop_Transform B = (Prop_Transform)CMClass.getAbility(ID());
		B.setMiscText("WHEN=\"" + when + "\" BACK=true");
		B.setSavable(false);
		neu.addNonUninvokableEffect(B);
	}

	/** a SET's changes */
	protected static void apply(final Physical P, final Map<String, String> s)
	{
		final String n = CMParms.getParmStr(s, "NAME", "");
		if(n.length() > 0)
			P.setName(n);
		final String d = CMParms.getParmStr(s, "DISPLAY", "");
		if(d.length() > 0)
			P.setDisplayText(d);
		final String desc = CMParms.getParmStr(s, "DESC", "");
		if(desc.length() > 0)
			P.setDescription(desc);
		for(final String[] w : Prop_When.parseWraps(CMParms.getParmStr(s, "PROPS", "")))
		{
			final Ability A = CMClass.getAbility(w[0]);
			if((A != null) && (P.fetchEffect(A.ID()) == null))
			{
				A.setMiscText(w[1]);
				P.addNonUninvokableEffect(A);
			}
		}
		P.recoverPhyStats();
	}

	/** back to the form put away (on a form REVERT made), or the old SET */
	protected void back()
	{
		final Physical P = affected;
		if(P == null)
			return;
		final Physical old = putAway.remove(P);
		final Room R = room();
		if((old == null) || (R == null))
			return;
		if(old instanceof Item)
		{
			R.addItem((Item)old, ItemPossessor.Expire.Never);
			final Ability A = old.fetchEffect(ID());
			if(A instanceof Prop_Transform)
				((Prop_Transform)A).changed = false;
		}
		else
		if(old instanceof MOB)
			((MOB)old).bringToLife(R, true);
		if(P instanceof MOB)
			((MOB)P).destroy();
		else
		if(P instanceof Item)
			((Item)P).destroy();
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		final Physical P = affected;
		if((P == null) || ((P instanceof Item) && ((Item)P).amDestroyed()) || ((P instanceof MOB) && ((MOB)P).amDestroyed()))
		{
			this.ticking = false;
			return false;
		}
		if((P instanceof Item) && (((Item)P).owner() instanceof MOB))
			lastHolder = (MOB)((Item)P).owner();
		// the way back, on a form a REVERT made
		if(CMParms.getParmBool(parms, "BACK", false))
		{
			if(!whenHolds())
			{
				this.ticking = false;
				back();
				return false;
			}
			return true;
		}
		if(when.length() > 0)
		{
			final boolean holds = whenHolds();
			if(holds && (!changed))
			{
				if(CMParms.getParmStr(parms, "SET", "").length() > 0)
					change();
				else
				{
					changed = true;
					this.ticking = false;
					change();
					return false;
				}
			}
			else
			if((!holds) && changed && revert && (CMParms.getParmStr(parms, "SET", "").length() > 0))
			{
				apply(P, CMParms.parseExactParms(CMParms.getParmStr(parms, "WAS", "")));
				changed = false;
			}
			return true;
		}
		if(total <= 0)
			return true;
		if(!started)
		{
			if(startFits())
				started = true;
			else
				return true;
		}
		else
		if(reset && (!startFits()))
		{
			started = false;
			left = total;
			return true;
		}
		if(!placeFits())
			return true;
		if(--left <= 0)
		{
			this.ticking = false;
			change();
			return false;
		}
		return true;
	}
}
