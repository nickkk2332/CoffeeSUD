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
 * I7: a curse on an item that Remove Curse ends cleanly.  LEVEL=15: the
 * least level of the priest who can lift it; HIDDEN=true: it shows nothing
 * until it is first worn (REVEAL= is then told); NOREMOVE and NODROP: it
 * will not come off, or be dropped; PENALTY="Prop_WearAdjuster(str-3);
 * Prop_WearSpellCast(Prayer_CurseLuck)" what it does while it holds; BREAK=
 * "SAY:i confess;ENTER:Chapel#3" deeds, in order, that break it, or
 * BREAKMASK= a mask the wearer comes to fit; ONBREAK=CRUMBLE (the item goes),
 * KEEP (the default) or BECOME:catalog item.  Lifted or broken, its
 * penalties go with it.  It is of the cursing domain, so Remove Curse finds
 * it; its state is kept in its text (SHOWN=, STEP=).
 */
public class Prop_Cursed extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Cursed";
	}

	@Override
	public String name()
	{
		return "Cursed";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public int classificationCode()
	{
		return Ability.ACODE_PROPERTY | Ability.DOMAIN_CURSING;
	}

	@Override
	public String accountForYourself()
	{
		return shown() ? "Cursed" : "";
	}

	public boolean detectedBy(final MOB viewer)
	{
		return viewer != null && viewer.phyStats().level() >= level;
	}

	protected String						base		= "";
	protected Map<String, String>			parms		= new HashMap<String, String>();
	protected int							level		= 1;
	protected boolean						hidden		= false;
	protected boolean						noRemove	= false;
	protected boolean						noDrop		= false;
	protected boolean						revealed	= false;
	protected List<String>					steps		= new ArrayList<String>();
	protected int							step		= 0;
	protected MaskingLibrary.CompiledZMask	breakMask	= null;
	protected final List<Ability>			penalties	= new ArrayList<Ability>();
	protected int							liftLevel	= -1;
	protected boolean						gone		= false;
	protected boolean						ticking		= false;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final String t = (newText == null) ? "" : newText;
		parms = CMParms.parseExactParms(t);
		level = Math.max(1, CMParms.getParmInt(parms, "LEVEL", 1));
		hidden = CMParms.getParmBool(parms, "HIDDEN", false);
		noRemove = CMParms.getParmBool(parms, "NOREMOVE", false) || CMParms.getParmBool(parms, "NOREMOVE", false);
		noDrop = CMParms.getParmBool(parms, "NODROP", false);
		final String up = " " + t.toUpperCase() + " ";
		noRemove = noRemove || up.contains(" NOREMOVE ");
		noDrop = noDrop || up.contains(" NODROP ");
		hidden = hidden || up.contains(" HIDDEN ");
		revealed = CMParms.getParmBool(parms, "SHOWN", false);
		steps.clear();
		for(final String s : CMParms.getParmStr(parms, "BREAK", "").split(";"))
		{
			if(s.trim().length() > 0)
				steps.add(s.trim());
		}
		step = CMParms.getParmInt(parms, "STEP", 0);
		final String bm = CMParms.getParmStr(parms, "BREAKMASK", "").trim();
		breakMask = (bm.length() == 0) ? null : CMLib.masking().getPreCompiledMask(bm);
		base = t.replaceAll("(?i)\\s*\\bSHOWN=\\S*", "").replaceAll("(?i)\\s*\\bSTEP=\\S*", "").trim();
	}

	@Override
	public String text()
	{
		return base + (revealed ? " SHOWN=true" : "") + ((step > 0) ? (" STEP=" + step) : "");
	}

	/**
	 * @return whether the curse shows: not hidden, or worn once
	 */
	public boolean shown()
	{
		return (!hidden) || revealed;
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
		if((P != null) && shown())
			penalize();
	}

	@Override
	public void affectPhyStats(final Physical host, final PhyStats stats)
	{
		super.affectPhyStats(host, stats);
		if(gone || (!shown()))
			return;
		if(noRemove)
			stats.setSensesMask(stats.sensesMask() | PhyStats.SENSE_ITEMNOREMOVE);
		if(noDrop)
			stats.setSensesMask(stats.sensesMask() | PhyStats.SENSE_ITEMNODROP);
	}

	/** puts the penalties on the item */
	protected void penalize()
	{
		if((!penalties.isEmpty()) || gone || (affected == null))
			return;
		for(final String[] w : Prop_When.parseWraps(CMParms.getParmStr(parms, "PENALTY", "")))
		{
			final Ability A = CMClass.getAbility(w[0]);
			if(A != null)
			{
				A.setMiscText(w[1]);
				A.setSavable(false);
				affected.addNonUninvokableEffect(A);
				penalties.add(A);
			}
		}
		affected.recoverPhyStats();
		recoverWearer();
	}

	protected void recoverWearer()
	{
		if((affected instanceof Item) && (((Item)affected).owner() instanceof MOB))
		{
			final MOB M = (MOB)((Item)affected).owner();
			M.recoverPhyStats();
			M.recoverCharStats();
			M.recoverMaxState();
		}
	}

	/**
	 * Ends the curse: its penalties go, and the item as ONBREAK says.
	 * @param mob who ended it, or null
	 */
	public void lift(final MOB mob)
	{
		if(gone || (!(affected instanceof Item)))
			return;
		gone = true;
		final Item I = (Item)affected;
		for(final Ability A : penalties)
		{
			I.delEffect(A);
			A.setAffectedOne(null);
		}
		penalties.clear();
		I.delEffect(this);
		setAffectedOne(null);
		I.recoverPhyStats();
		final MOB wearer = (I.owner() instanceof MOB) ? (MOB)I.owner() : null;
		if(wearer != null)
		{
			wearer.recoverPhyStats();
			wearer.recoverCharStats();
			wearer.recoverMaxState();
		}
		final String on = CMParms.getParmStr(parms, "ONBREAK", "KEEP").toUpperCase();
		final String msg = CMParms.getParmStr(parms, "BREAKMSG", "");
		if((msg.length() > 0) && (wearer != null))
			wearer.tell(msg);
		if(on.equals("CRUMBLE"))
		{
			if(wearer != null)
				wearer.tell(wearer, I, null, CMLib.lang().L("<T-NAME> crumble(s) to dust."));
			I.destroy();
		}
		else
		if(on.startsWith("BECOME:"))
			Transform.into(I, CMParms.getParmStr(parms, "ONBREAK", "").substring(7).trim(), "ALL");
	}

	@Override
	public void unInvoke()
	{
		// Remove Curse: only a priest of LEVEL or more lifts it
		if(liftLevel >= level)
			lift(null);
		liftLevel = -1;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(gone || (!(affected instanceof Item)))
			return;
		final Item I = (Item)affected;
		// a Remove Curse aimed at it, or its wearer: the priest's level
		if((msg.tool() instanceof Ability) && (msg.target() == I || ((I.owner() == msg.target()) && (msg.target() instanceof MOB)))
		&& ((Ability)msg.tool()).ID().toUpperCase().contains("REMOVECURSE"))
		{
			liftLevel = Math.max(liftLevel, msg.source().phyStats().level());
			if(liftLevel < level)
				msg.source().tell(msg.source(), I, null, CMLib.lang().L("The curse on <T-NAME> is too strong for you."));
		}
		// worn the first time: it shows
		if((msg.target() == I) && (msg.source() == I.owner())
		&& ((msg.targetMinor() == CMMsg.TYP_WEAR) || (msg.targetMinor() == CMMsg.TYP_HOLD) || (msg.targetMinor() == CMMsg.TYP_WIELD))
		&& (!revealed))
		{
			revealed = true;
			final String r = CMParms.getParmStr(parms, "REVEAL", "");
			if(r.length() > 0)
				msg.addTrailerMsg(CMClass.getMsg(msg.source(), I, null, CMMsg.MSG_OK_VISUAL, r, CMMsg.NO_EFFECT, null, CMMsg.NO_EFFECT, null));
			penalize();
			I.recoverPhyStats();
		}
		// the deeds that break it
		if((step < steps.size()) && (I.owner() instanceof MOB) && (msg.source() == I.owner()))
		{
			final String s = steps.get(step);
			final int x = s.indexOf(':');
			final String kind = ((x < 0) ? s : s.substring(0, x)).trim().toUpperCase();
			final String arg = (x < 0) ? "" : s.substring(x + 1).trim();
			boolean done = false;
			if(kind.equals("SAY") && (msg.sourceMinor() == CMMsg.TYP_SPEAK) && (msg.sourceMessage() != null))
			{
				final String said = CMStrings.getSayFromMessage(msg.sourceMessage());
				done = (said != null) && said.toLowerCase().contains(arg.toLowerCase());
			}
			else
			if(kind.equals("ENTER") && (msg.targetMinor() == CMMsg.TYP_ENTER) && (msg.target() instanceof Room))
				done = CMLib.map().getExtendedRoomID((Room)msg.target()).equalsIgnoreCase(arg);
			if(done && (++step >= steps.size()))
				lift(msg.source());
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		if(gone || (!(affected instanceof Item)) || ((Item)affected).amDestroyed())
		{
			this.ticking = false;
			return false;
		}
		final Item I = (Item)affected;
		if((breakMask != null) && (I.owner() instanceof MOB) && CMLib.masking().maskCheck(breakMask, (MOB)I.owner(), true))
			lift((MOB)I.owner());
		return true;
	}
}
