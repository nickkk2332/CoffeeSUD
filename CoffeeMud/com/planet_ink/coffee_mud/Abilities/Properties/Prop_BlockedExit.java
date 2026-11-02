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
 * I13: a blockage on an exit that the player must clear.  NAME="a pile of
 * rubble"; HP=40; TOOL= a mask on what is held or wielded (none: bare
 * hands do); SKILL=Digging+STR what adds to each try (as Prop_Ambush's
 * DETECT: a stat's (n-10)/2, a skill's proficiency/10) on top of 1d6;
 * CLEAR, DIG, SMASH, HIT or ATTACK the direction (or the blockage), and
 * BURN it with a lit light when FIRE=true; MSG= each try, CLEARMSG= when
 * it is gone, ONCLEAR= actions then; RESPAWN=n ticks before it grows back
 * (0, never).  While it stands nobody goes that way.  What is left of it
 * is kept in the text (LEFT=), so saves keep it; one on the far side of
 * the exit is kept the same.
 */
public class Prop_BlockedExit extends Property implements VerbTaker
{
	@Override
	public String ID()
	{
		return "Prop_BlockedExit";
	}

	@Override
	public String name()
	{
		return "A blockage";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_EXITS;
	}

	@Override
	public String accountForYourself()
	{
		return blockName + " blocks the way";
	}

	protected static final String[]	VERBS	= { "clear", "dig", "smash", "hit", "attack", "burn" };

	protected String		base		= "";
	protected String		blockName	= "a blockage";
	protected int			hp			= 40;
	protected int			left		= 40;
	protected MaskingLibrary.CompiledZMask	tool	= null;
	protected String		skill		= "STR";
	protected boolean		fire		= false;
	protected int			respawn		= 0;
	protected int			respawnDown	= 0;
	protected Map<String, String>	parms	= new HashMap<String, String>();
	protected boolean		ticking		= false;
	protected Room			clearedIn	= null;
	protected int			clearedDir	= -1;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final String t = (newText == null) ? "" : newText;
		parms = CMParms.parseExactParms(t);
		blockName = CMParms.getParmStr(parms, "NAME", "a blockage");
		hp = Math.max(1, CMParms.getParmInt(parms, "HP", 40));
		left = Math.max(0, Math.min(hp, CMParms.getParmInt(parms, "LEFT", hp)));
		final String m = CMParms.getParmStr(parms, "TOOL", "").trim();
		tool = (m.length() == 0) ? null : CMLib.masking().getPreCompiledMask(m);
		skill = CMParms.getParmStr(parms, "SKILL", "STR").replace('|', '+');
		fire = CMParms.getParmBool(parms, "FIRE", false);
		respawn = Math.max(0, CMParms.getParmInt(parms, "RESPAWN", 0));
		base = t.replaceAll("(?i)\\s*\\bLEFT=\\S*", "").trim();
		ScriptEvents.propVerbsSeen();
	}

	@Override
	public String text()
	{
		return (left == hp) ? base : (base + " LEFT=" + left);
	}

	/**
	 * @return whether it still blocks the way
	 */
	public boolean blocks()
	{
		return left > 0;
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("LEFT"))
			return "" + left;
		return super.getStat(code);
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if(blocks() && (msg.tool() == affected)
		&& ((msg.targetMinor() == CMMsg.TYP_ENTER) || (msg.targetMinor() == CMMsg.TYP_LEAVE)))
		{
			msg.source().tell(CMLib.lang().L("@x1 blocks the way.", CMStrings.capitalizeFirstLetter(blockName)));
			return false;
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(blocks() && (msg.target() == affected)
		&& ((msg.targetMinor() == CMMsg.TYP_LOOK) || (msg.targetMinor() == CMMsg.TYP_EXAMINE)))
			msg.addTrailerMsg(CMClass.getMsg(msg.source(), affected, null, CMMsg.MSG_OK_VISUAL, CMLib.lang().L("@x1 blocks the way.", CMStrings.capitalizeFirstLetter(blockName)), CMMsg.NO_EFFECT, null, CMMsg.NO_EFFECT, null));
	}

	/** the same blockage on the far side, if there is one */
	protected Prop_BlockedExit pair(final Room R, final int dir)
	{
		final Exit E2 = (R == null) || (dir < 0) ? null : R.getPairedExit(dir);
		final Ability A = (E2 == null) ? null : E2.fetchEffect(ID());
		return (A instanceof Prop_BlockedExit) && (A != this) ? (Prop_BlockedExit)A : null;
	}

	/**
	 * Sets what is left of it, and of the far side's.
	 * @param n what is left
	 * @param R the room the exit is in
	 * @param dir its direction there
	 */
	public void setLeft(final int n, final Room R, final int dir)
	{
		left = Math.max(0, Math.min(hp, n));
		final Prop_BlockedExit P = pair(R, dir);
		if(P != null)
			P.left = Math.max(0, Math.min(P.hp, n));
	}

	@Override
	public boolean takeVerb(final MOB mob, final PhysicalAgent host, final String verb, final String rest)
	{
		if((host != affected) || (!blocks()))
			return false;
		final String v = verb.toLowerCase();
		if(!CMParms.contains(VERBS, v))
			return false;
		final Room R = mob.location();
		if(R == null)
			return false;
		int dir = -1;
		for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
		{
			if(R.getExitInDir(d) == affected)
				dir = d;
		}
		final String r = rest.trim();
		final int named = CMLib.directions().getGoodDirectionCode(r);
		if(!(((named >= 0) && (named == dir)) || ((r.length() > 0) && CMLib.english().containsString(blockName, r))))
			return false;
		if(v.equals("burn"))
		{
			if(!fire)
			{
				mob.tell(CMLib.lang().L("@x1 will not burn.", CMStrings.capitalizeFirstLetter(blockName)));
				return true;
			}
			boolean lit = false;
			for(final Enumeration<Item> i = mob.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				if((I instanceof Light) && ((Light)I).isLit() && (!I.amWearingAt(Wearable.IN_INVENTORY)))
					lit = true;
			}
			if(!lit)
			{
				mob.tell(CMLib.lang().L("You need a flame in hand."));
				return true;
			}
			R.show(mob, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> set(s) @x1 alight; it burns away.", blockName));
			cleared(mob, R, dir);
			return true;
		}
		if(tool != null)
		{
			boolean has = false;
			for(final Enumeration<Item> i = mob.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				if((I != null) && (I.amWearingAt(Wearable.WORN_HELD) || I.amWearingAt(Wearable.WORN_WIELD)) && CMLib.masking().maskCheck(tool, I, true))
					has = true;
			}
			if(!has)
			{
				mob.tell(CMLib.lang().L("You need the right tool in hand for @x1.", blockName));
				return true;
			}
		}
		final int dmg = Math.max(1, CMLib.dice().roll(1, 6, 0) + Prop_Ambush.bonus(mob, skill));
		final String m = CMParms.getParmStr(parms, "MSG", CMLib.lang().L("<S-NAME> work(s) at @x1.", blockName));
		R.show(mob, null, CMMsg.MSG_OK_ACTION, m);
		setLeft(left - dmg, R, dir);
		if(!blocks())
			cleared(mob, R, dir);
		return true;
	}

	protected void cleared(final MOB mob, final Room R, final int dir)
	{
		setLeft(0, R, dir);
		clearedIn = R;
		clearedDir = dir;
		final String m = CMParms.getParmStr(parms, "CLEARMSG", CMLib.lang().L("@x1 is cleared away.", CMStrings.capitalizeFirstLetter(blockName)));
		R.showHappens(CMMsg.MSG_OK_VISUAL, m);
		final Room R2 = (dir >= 0) ? R.getRoomInDir(dir) : null;
		if((R2 != null) && (R2 != R))
			R2.showHappens(CMMsg.MSG_OK_VISUAL, m);
		Actions.run(R, mob, CMParms.getParmStr(parms, "ONCLEAR", ""));
		if(respawn > 0)
		{
			respawnDown = respawn;
			if(!ticking)
			{
				ticking = true;
				CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
			}
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		if((respawnDown > 0) && (--respawnDown <= 0))
		{
			this.ticking = false;
			setLeft(hp, clearedIn, clearedDir);
			return false;
		}
		if(respawnDown <= 0)
			this.ticking = false;
		return respawnDown > 0;
	}
}
