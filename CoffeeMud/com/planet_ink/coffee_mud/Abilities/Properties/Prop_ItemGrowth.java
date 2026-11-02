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
 * I24: an item that grows with use.  GAIN=KILL (its wielder's kills, with
 * KILLMASK= on the slain), HIT (its hits), USE (anything done with it) or
 * WORNTICK (each tick worn); XP= how much each gives, a formula (@x1 the
 * slain's level; 1 by default); STAGES="100:NAME='a humming blade'
 * PROPS=Prop_WearAdjuster(dam+2);400:NAME='Dawnbringer' PROPS=..." what it
 * becomes as it reaches each; MSG= ("$p shudders as it grows stronger!");
 * BIND=CHARACTER: it grows for its first owner only.  Its growth is kept in
 * its text (GROWN=, STAGE=, OWNER=); identifying it shows them.
 */
public class Prop_ItemGrowth extends Property
{
	@Override
	public String ID()
	{
		return "Prop_ItemGrowth";
	}

	@Override
	public String name()
	{
		return "Grows with use";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		final int next = nextAt();
		return "Grows with use: stage " + stage + ((next > 0) ? (", " + grown + " of " + next + " toward the next") : ", fully grown");
	}

	protected String						base		= "";
	protected Map<String, String>			parms		= new HashMap<String, String>();
	protected String						gain		= "KILL";
	protected String						xp			= "1";
	protected MaskingLibrary.CompiledZMask	killMask	= null;
	protected final List<Object[]>			stages		= new ArrayList<Object[]>();
	protected int							grown		= 0;
	protected int							stage		= 0;
	protected String						owner		= "";
	protected boolean						bind		= false;
	protected final List<Ability>			stageProps	= new ArrayList<Ability>();
	protected boolean						ticking		= false;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final String t = (newText == null) ? "" : newText;
		parms = CMParms.parseExactParms(t);
		gain = CMParms.getParmStr(parms, "GAIN", "KILL").toUpperCase();
		xp = CMParms.getParmStr(parms, "XP", "1");
		final String km = CMParms.getParmStr(parms, "KILLMASK", "").trim();
		killMask = (km.length() == 0) ? null : CMLib.masking().getPreCompiledMask(km);
		stages.clear();
		for(final String s : CMParms.getParmStr(parms, "STAGES", "").split(";(?=\\s*\\d+\\s*:)"))
		{
			final int x = s.indexOf(':');
			if((x > 0) && CMath.isInteger(s.substring(0, x).trim()))
			{
				final String rest = s.substring(x + 1).trim().replace('\'', '"');
				stages.add(new Object[] { Integer.valueOf(CMath.s_int(s.substring(0, x).trim())), CMParms.parseExactParms(rest), rest });
			}
		}
		grown = CMParms.getParmInt(parms, "GROWN", 0);
		stage = CMParms.getParmInt(parms, "STAGE", 0);
		owner = CMParms.getParmStr(parms, "OWNER", "");
		bind = CMParms.getParmStr(parms, "BIND", "").equalsIgnoreCase("CHARACTER");
		base = t.replaceAll("(?i)\\s*\\bGROWN=\\S*", "").replaceAll("(?i)\\s*\\bSTAGE=\\S*", "").replaceAll("(?i)\\s*\\bOWNER=(\"[^\"]*\"|\\S*)", "").trim();
	}

	@Override
	public String text()
	{
		return base + ((grown > 0) ? (" GROWN=" + grown) : "") + ((stage > 0) ? (" STAGE=" + stage) : "") + ((owner.length() > 0) ? (" OWNER=\"" + owner + "\"") : "");
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		super.setAffectedOne(P);
		if(P != null)
		{
			applyStageProps();
			if((!ticking) && gain.equals("WORNTICK"))
			{
				ticking = true;
				CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
			}
		}
	}

	/**
	 * @return its growth so far
	 */
	public int grown()
	{
		return grown;
	}

	/**
	 * @return the stages it has reached
	 */
	public int stage()
	{
		return stage;
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("GROWN"))
			return "" + grown;
		if(code.equalsIgnoreCase("STAGE"))
			return "" + stage;
		return super.getStat(code);
	}

	/** the growth the next stage needs, or 0 */
	protected int nextAt()
	{
		return (stage < stages.size()) ? ((Integer)stages.get(stage)[0]).intValue() : 0;
	}

	/** the properties of the stage reached, on the item, unsaved */
	@SuppressWarnings("unchecked")
	protected void applyStageProps()
	{
		if(affected == null)
			return;
		for(final Ability A : stageProps)
		{
			affected.delEffect(A);
			A.setAffectedOne(null);
		}
		stageProps.clear();
		if((stage <= 0) || (stage > stages.size()))
			return;
		final Map<String, String> s = (Map<String, String>)stages.get(stage - 1)[1];
		for(final String[] w : Prop_When.parseWraps(CMParms.getParmStr(s, "PROPS", "")))
		{
			final Ability A = CMClass.getAbility(w[0]);
			if(A != null)
			{
				A.setMiscText(w[1]);
				A.setSavable(false);
				affected.addNonUninvokableEffect(A);
				stageProps.add(A);
			}
		}
		affected.recoverPhyStats();
	}

	/**
	 * Grows.
	 * @param M who used it
	 * @param n how much
	 */
	@SuppressWarnings("unchecked")
	public void grow(final MOB M, final int n)
	{
		if((n <= 0) || (affected == null))
			return;
		if(bind && (M != null))
		{
			if(owner.length() == 0)
				owner = M.Name();
			else
			if(!owner.equalsIgnoreCase(M.Name()))
				return;
		}
		grown += n;
		while((stage < stages.size()) && (grown >= ((Integer)stages.get(stage)[0]).intValue()))
		{
			stage++;
			final Map<String, String> s = (Map<String, String>)stages.get(stage - 1)[1];
			final String nm = CMParms.getParmStr(s, "NAME", "");
			if(nm.length() > 0)
				affected.setName(nm);
			final String d = CMParms.getParmStr(s, "DISPLAY", "");
			if(d.length() > 0)
				affected.setDisplayText(d);
			applyStageProps();
			final String m = CMParms.getParmStr(parms, "MSG", "");
			if((m.length() > 0) && (M != null))
				M.tell(CMStrings.capitalizeFirstLetter(m.replace("$p", affected.name())));
		}
		if(M != null)
		{
			M.recoverPhyStats();
			M.recoverCharStats();
		}
	}

	protected int amount(final int level)
	{
		final String f = xp.replace("@x1", "" + level);
		return CMath.isMathExpression(f) ? (int)Math.round(CMath.parseMathExpression(f)) : CMath.s_int(f);
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(!(affected instanceof Item))
			return;
		final Item I = (Item)affected;
		final MOB holder = (I.owner() instanceof MOB) ? (MOB)I.owner() : null;
		if(holder == null)
			return;
		if(gain.equals("KILL") && (msg.sourceMinor() == CMMsg.TYP_DEATH) && (msg.tool() == holder) && (msg.source() != holder)
		&& (!I.amWearingAt(Wearable.IN_INVENTORY)))
		{
			if((killMask == null) || CMLib.masking().maskCheck(killMask, msg.source(), true))
				grow(holder, amount(msg.source().phyStats().level()));
		}
		else
		if(gain.equals("HIT") && (msg.targetMinor() == CMMsg.TYP_DAMAGE) && (msg.source() == holder) && (msg.tool() == I) && (msg.value() > 0))
			grow(holder, amount((msg.target() instanceof MOB) ? ((MOB)msg.target()).phyStats().level() : 1));
		else
		if(gain.equals("USE") && (msg.source() == holder) && (msg.tool() == I))
			grow(holder, amount(1));
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
		final Item I = (Item)affected;
		if((I.owner() instanceof MOB) && (!I.amWearingAt(Wearable.IN_INVENTORY)))
			grow((MOB)I.owner(), amount(1));
		return true;
	}
}
