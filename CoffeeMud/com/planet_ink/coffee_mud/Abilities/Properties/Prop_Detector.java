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
 * I14: an item that reacts to what is near.  DETECT= a mask on mobs, or
 * DETECTITEM= one on items; RANGE=0..3 rooms away (0, its own room);
 * WHERE=WORN|HELD|ANY (ANY: carried or lying anywhere; WORN, any worn
 * place); EVERY=n ticks (1) it looks; while it finds one: AMBIANCE=
 * "(glowing blue)" is on it, and its holder was told MSG= ("$p glows
 * faintly blue.", $p the item); STOPMSG= when it stops.
 */
public class Prop_Detector extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Detector";
	}

	@Override
	public String name()
	{
		return "Senses what is near";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return "Senses what is near";
	}

	protected MaskingLibrary.CompiledZMask	mobMask		= null;
	protected MaskingLibrary.CompiledZMask	itemMask	= null;
	protected int							range		= 0;
	protected String						where		= "ANY";
	protected int							every		= 1;
	protected int							down		= 0;
	protected String						ambiance	= "";
	protected String						msgText		= "";
	protected String						stopMsg		= "";
	protected boolean						sensing		= false;
	protected boolean						ticking		= false;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final Map<String, String> q = CMParms.parseExactParms((newText == null) ? "" : newText);
		final String d = CMParms.getParmStr(q, "DETECT", "").trim();
		mobMask = (d.length() == 0) ? null : CMLib.masking().getPreCompiledMask(d);
		final String di = CMParms.getParmStr(q, "DETECTITEM", "").trim();
		itemMask = (di.length() == 0) ? null : CMLib.masking().getPreCompiledMask(di);
		range = Math.max(0, Math.min(3, CMParms.getParmInt(q, "RANGE", 0)));
		where = CMParms.getParmStr(q, "WHERE", "ANY").trim().toUpperCase();
		every = Math.max(1, CMParms.getParmInt(q, "EVERY", 1));
		ambiance = CMParms.getParmStr(q, "AMBIANCE", "");
		msgText = CMParms.getParmStr(q, "MSG", "");
		stopMsg = CMParms.getParmStr(q, "STOPMSG", "");
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		super.setAffectedOne(P);
		if((P instanceof Item) && (!ticking))
		{
			ticking = true;
			CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
		}
	}

	/**
	 * @return whether it senses something now
	 */
	public boolean sensing()
	{
		return sensing;
	}

	@Override
	public void affectPhyStats(final Physical host, final PhyStats stats)
	{
		super.affectPhyStats(host, stats);
		if(sensing && (ambiance.length() > 0))
			stats.addAmbiance(ambiance);
	}

	/** whether it is where it works */
	protected boolean placed(final Item I)
	{
		if(where.equals("WORN"))
			return (I.owner() instanceof MOB) && (!I.amWearingAt(Wearable.IN_INVENTORY));
		if(where.equals("HELD"))
			return (I.owner() instanceof MOB) && (I.amWearingAt(Wearable.WORN_HELD) || I.amWearingAt(Wearable.WORN_WIELD));
		return true;
	}

	/**
	 * Looks about once.
	 * @return whether it finds something
	 */
	public boolean look()
	{
		if(!(affected instanceof Item))
			return false;
		final Item I = (Item)affected;
		final Room here = CMLib.map().roomLocation(I);
		if((here == null) || (!placed(I)))
			return false;
		final MOB holder = (I.owner() instanceof MOB) ? (MOB)I.owner() : null;
		final Set<Room> seen = new HashSet<Room>();
		List<Room> ring = new ArrayList<Room>();
		ring.add(here);
		seen.add(here);
		for(int step = 0; step <= range; step++)
		{
			final List<Room> next = new ArrayList<Room>();
			for(final Room R : ring)
			{
				if(mobMask != null)
				{
					for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
					{
						final MOB M = m.nextElement();
						if((M != null) && (M != holder) && (!M.amDead()) && CMLib.masking().maskCheck(mobMask, M, true))
							return true;
					}
				}
				if(itemMask != null)
				{
					for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
					{
						final Item J = i.nextElement();
						if((J != null) && (J != I) && CMLib.masking().maskCheck(itemMask, J, true))
							return true;
					}
				}
				for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
				{
					final Room R2 = R.getRoomInDir(d);
					if((R2 != null) && seen.add(R2))
						next.add(R2);
				}
			}
			ring = next;
		}
		return false;
	}

	protected void tell(final String text)
	{
		if((text.length() == 0) || (!(affected instanceof Item)))
			return;
		final Item I = (Item)affected;
		final String t = CMStrings.capitalizeFirstLetter(text.replace("$p", I.name()));
		if(I.owner() instanceof MOB)
			((MOB)I.owner()).tell(t);
		else
		if(I.owner() instanceof Room)
			((Room)I.owner()).showHappens(CMMsg.MSG_OK_VISUAL, t);
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		if(!(affected instanceof Item) || ((Item)affected).amDestroyed())
		{
			this.ticking = false;
			return false;
		}
		if(--down > 0)
			return true;
		down = every;
		final boolean now = look();
		if(now != sensing)
		{
			sensing = now;
			affected.recoverPhyStats();
			tell(now ? msgText : stopMsg);
		}
		return true;
	}
}
