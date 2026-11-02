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
 * I25: a pressure plate, on a room (the mobs there, with what they carry,
 * and the items lying there) or on a fixture (what is on or in it, and who
 * sits on it).  WEIGHT=&gt;=120 and COUNT=&gt;=2 (the mobs) test it, MASK= what
 * counts; ON= and OFF= actions run when it goes down and comes up again,
 * MSGON= and MSGOFF= show there; DELAY=n ticks before it comes back up.
 * It looks again at each entering, leaving, dropping, getting, putting,
 * sitting and standing there.
 */
public class Prop_PressurePlate extends Property
{
	@Override
	public String ID()
	{
		return "Prop_PressurePlate";
	}

	@Override
	public String name()
	{
		return "A pressure plate";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS | Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return "A pressure plate";
	}

	protected String		weight		= "";
	protected String		count		= "";
	protected MaskingLibrary.CompiledZMask	mask	= null;
	protected Map<String, String>	parms	= new HashMap<String, String>();
	protected int			delay		= 0;
	protected int			delayDown	= 0;
	protected Boolean		down		= null;
	protected boolean		ticking		= false;
	protected MOB			lastActor	= null;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		parms = CMParms.parseExactParms((newText == null) ? "" : newText);
		weight = CMParms.getParmStr(parms, "WEIGHT", "").trim();
		count = CMParms.getParmStr(parms, "COUNT", "").trim();
		if((weight.length() == 0) && (count.length() == 0))
			count = ">=1";
		final String m = CMParms.getParmStr(parms, "MASK", "").trim();
		mask = (m.length() == 0) ? null : CMLib.masking().getPreCompiledMask(m);
		delay = Math.max(0, CMParms.getParmInt(parms, "DELAY", 0));
		down = null;
	}

	/** the room it is in, or is */
	protected Room room()
	{
		if(affected instanceof Room)
			return (Room)affected;
		return CMLib.map().roomLocation(affected);
	}

	/**
	 * Whether it is pressed, as things are, or will be.
	 * @param plusM a mob coming, or null
	 * @param minusM a mob going, or null
	 * @param plusI an item coming, or null
	 * @param minusI an item going, or null
	 * @return true if it is pressed
	 */
	protected boolean pressed(final MOB plusM, final MOB minusM, final Item plusI, final Item minusI)
	{
		int w = 0, n = 0;
		if(affected instanceof Room)
		{
			final Room R = (Room)affected;
			w = Weights.room(R, mask);
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && (!M.amDead()) && (M != minusM) && (M != plusM) && Weights.counts(M, mask))
					n++;
			}
			if((minusM != null) && R.isInhabitant(minusM) && Weights.counts(minusM, mask))
				w -= Weights.mob(minusM);
			if((plusM != null) && (!R.isInhabitant(plusM)) && Weights.counts(plusM, mask))
				w += Weights.mob(plusM);
			if((plusM != null) && Weights.counts(plusM, mask))
				n++;
			if((minusI != null) && R.isContent(minusI) && Weights.counts(minusI, mask))
				w -= minusI.phyStats().weight();
			if((plusI != null) && (!R.isContent(plusI)) && Weights.counts(plusI, mask))
				w += plusI.phyStats().weight();
		}
		else
		if(affected instanceof Item)
		{
			final Item F = (Item)affected;
			w = Weights.contents(F, mask);
			if(F instanceof Rideable)
			{
				for(final Enumeration<Rider> r = ((Rideable)F).riders(); r.hasMoreElements();)
				{
					final Rider X = r.nextElement();
					if((X instanceof MOB) && (X != minusM) && (X != plusM) && Weights.counts((MOB)X, mask))
						n++;
				}
				if((minusM != null) && ((Rideable)F).amRiding(minusM) && Weights.counts(minusM, mask))
					w -= Weights.mob(minusM);
				if((plusM != null) && (!((Rideable)F).amRiding(plusM)) && Weights.counts(plusM, mask))
					w += Weights.mob(plusM);
				if((plusM != null) && Weights.counts(plusM, mask))
					n++;
			}
			if((minusI != null) && (minusI.container() == F) && Weights.counts(minusI, mask))
				w -= minusI.phyStats().weight();
			if((plusI != null) && (plusI.container() != F) && Weights.counts(plusI, mask))
				w += plusI.phyStats().weight();
		}
		return ((weight.length() == 0) || Weights.meets(weight, w))
			&& ((count.length() == 0) || Weights.meets(count, n));
	}

	/**
	 * @return whether it is down now
	 */
	public boolean isDown()
	{
		return pressed(null, null, null, null);
	}

	protected void turn(final boolean on, final MOB actor)
	{
		final Room R = room();
		down = Boolean.valueOf(on);
		final String m = CMParms.getParmStr(parms, on ? "MSGON" : "MSGOFF", "");
		if((R != null) && (m.length() > 0))
			R.showHappens(CMMsg.MSG_OK_VISUAL, m);
		final String a = CMParms.getParmStr(parms, on ? "ON" : "OFF", "");
		if((a.length() > 0) && (R != null))
		{
			final MOB who = ((actor != null) && (!actor.amDestroyed())) ? actor : CMLib.map().getFactoryMOB(R);
			try
			{
				Actions.run((affected instanceof PhysicalAgent) ? (PhysicalAgent)affected : R, who, a);
			}
			finally
			{
				if(who != actor)
					who.destroy();
			}
		}
	}

	protected void settle(final boolean before, final boolean after, final MOB actor)
	{
		if(down == null)
			down = Boolean.valueOf(before);
		lastActor = actor;
		if(after)
		{
			delayDown = 0;
			if(!down.booleanValue())
				turn(true, actor);
		}
		else
		if(down.booleanValue())
		{
			if(delay > 0)
			{
				if(delayDown <= 0)
				{
					delayDown = delay;
					if(!ticking)
					{
						ticking = true;
						CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
					}
				}
			}
			else
				turn(false, actor);
		}
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(affected == null)
			return;
		final MOB src = msg.source();
		MOB plusM = null, minusM = null;
		Item plusI = null, minusI = null;
		boolean look = false;
		final int t = msg.targetMinor();
		if(affected instanceof Room)
		{
			final Room R = (Room)affected;
			if((t == CMMsg.TYP_ENTER) && (msg.target() == R))
			{
				plusM = src;
				look = true;
			}
			else
			if(((t == CMMsg.TYP_LEAVE) || (t == CMMsg.TYP_FLEE)) && (msg.target() == R))
			{
				minusM = src;
				look = true;
			}
			else
			if((t == CMMsg.TYP_DROP) && (msg.target() instanceof Item) && (src.location() == R))
			{
				// dropped: on the floor now, carried before; the weight is the same
				look = true;
			}
			else
			if((t == CMMsg.TYP_GET) && (msg.target() instanceof Item) && R.isContent((Item)msg.target()) && (msg.tool() == null))
			{
				look = true;
			}
		}
		else
		if(affected instanceof Item)
		{
			final Item F = (Item)affected;
			if((t == CMMsg.TYP_PUT) && (msg.target() == F) && (msg.tool() instanceof Item))
			{
				plusI = (Item)msg.tool();
				look = true;
			}
			else
			if((t == CMMsg.TYP_GET) && (msg.target() == F) && (msg.tool() instanceof Item))
			{
				minusI = (Item)msg.tool();
				look = true;
			}
			else
			if(((t == CMMsg.TYP_SIT) || (t == CMMsg.TYP_SLEEP) || (t == CMMsg.TYP_MOUNT)) && (msg.target() == F))
			{
				plusM = src;
				look = true;
			}
			else
			if(((t == CMMsg.TYP_STAND) || (t == CMMsg.TYP_DISMOUNT) || (t == CMMsg.TYP_LEAVE)) && (F instanceof Rideable) && ((Rideable)F).amRiding(src))
			{
				minusM = src;
				look = true;
			}
		}
		if(!look)
			return;
		final boolean before = pressed(null, null, null, null);
		final boolean after = ((plusM == null) && (minusM == null) && (plusI == null) && (minusI == null)) ? before : pressed(plusM, minusM, plusI, minusI);
		if((plusM == null) && (minusM == null) && (plusI == null) && (minusI == null))
		{
			// a drop or a get in the room: the change is made after; look on the next tick
			if(down == null)
				down = Boolean.valueOf(before);
			lastActor = src;
			if(!ticking)
			{
				ticking = true;
				CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
			}
			return;
		}
		settle(before, after, src);
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		final boolean now = pressed(null, null, null, null);
		if(delayDown > 0)
		{
			if(now)
				delayDown = 0;
			else
			if(--delayDown <= 0)
				turn(false, lastActor);
		}
		else
			settle(now, now, lastActor);
		final boolean more = delayDown > 0;
		if(!more)
			this.ticking = false;
		return more;
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("DOWN"))
			return "" + isDown();
		return super.getStat(code);
	}
}
