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
   Copyright 2001-2025 Bo Zimmerman

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
public class Prop_RoomDark extends Property
{
	@Override
	public String ID()
	{
		return "Prop_RoomDark";
	}

	@Override
	public String name()
	{
		return "Darkening Property";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS|Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "Darkened";
	}

	@Override
	public long flags()
	{
		return Ability.FLAG_ADJUSTER;
	}

	protected boolean setup = false;

	/** W11: darkness lights cannot beat, the lights that still work, who still sees, and torches put out */
	protected boolean	absolute	= false;
	protected String	lightMask	= null;
	protected String	seeMask		= null;
	protected int		snuff		= 0;
	protected String	snuffMsg	= "Your @x1 gutters out in the dark.";
	protected boolean	norecurse	= false;

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		setup=false;
		final Map<String,String> P=CMParms.parseEQParms(newMiscText);
		absolute=CMath.s_bool(P.containsKey("ABSOLUTE")?P.get("ABSOLUTE"):"false");
		lightMask=(P.containsKey("LIGHTMASK")&&(P.get("LIGHTMASK").trim().length()>0))?P.get("LIGHTMASK"):null;
		seeMask=(P.containsKey("SEEMASK")&&(P.get("SEEMASK").trim().length()>0))?P.get("SEEMASK"):null;
		snuff=P.containsKey("SNUFF")?CMath.s_int(P.get("SNUFF").replace("%","").trim()):0;
		if(P.containsKey("SNUFFMSG"))
			snuffMsg=P.get("SNUFFMSG");
	}

	@Override
	public boolean bubbleAffect()
	{
		return seeMask!=null; // W11: so the room asks it of each mob there, for SEEMASK
	}

	/**
	 * W11: whether a thing's light still works here: LIGHTMASK names the lights that do.
	 * @param I the light
	 * @return true if it still lights the room
	 */
	protected boolean lightWorks(final Physical I)
	{
		return (lightMask!=null)&&CMLib.flags().isLightSource(I)&&CMLib.masking().maskCheck(lightMask,I,true);
	}

	/**
	 * @param M a mob
	 * @return true if it carries a light that still works here
	 */
	protected boolean hasWorkingLight(final MOB M)
	{
		if(lightMask==null)
			return false;
		for(final Enumeration<Item> i=M.items();i.hasMoreElements();)
		{
			final Item I=i.nextElement();
			if((I!=null)&&(!I.amWearingAt(Wearable.IN_INVENTORY))&&lightWorks(I))
				return true;
		}
		return false;
	}
	protected int[] hoursOfOperation = null;

	protected boolean isItDark()
	{
		final Physical affected = this.affected;
		if(affected == null)
			return false;
		if(!setup)
		{
			hoursOfOperation = null;
			setup = true;
			if(text().length()>0)
			{
				final String hrStr = CMParms.getParmStr(text(), "HOURS", "").trim();
				if(hrStr.length()>0)
				{
					final Set<Integer> finalV=new TreeSet<Integer>();
					for(final String hr : CMParms.parseCommas(hrStr, true))
					{
						if(CMath.isInteger(hr))
							finalV.add(Integer.valueOf(CMath.s_int(hr)));
					}
					if(finalV.size()>0)
					{
						final int[] finalArray = new int[finalV.size()];
						int dex=0;
						for(final Integer I : finalV)
							finalArray[dex++] = I.intValue();
						Arrays.sort(finalArray);
						hoursOfOperation = finalArray;
					}
				}
			}
		}
		if(hoursOfOperation != null)
		{
			final TimeClock C=CMLib.time().localClock(affected);
			return CMParms.contains(hoursOfOperation, C.getHourOfDay());
		}
		return true;
	}

	@Override
	public void affectPhyStats(final Physical affected, final PhyStats affectableStats)
	{
		if(affected instanceof MOB) // W11: SEEMASK: who still sees here
		{
			if((seeMask!=null)&&isItDark()&&CMLib.masking().maskCheck(seeMask,affected,true))
				affectableStats.setSensesMask(affectableStats.sensesMask()|PhyStats.CAN_SEE_DARK);
			return;
		}
		if((!(affected instanceof Room))&&(!(affected instanceof Area)))
			return;
		if(isItDark())
		{
			affectableStats.setDisposition(affectableStats.disposition()|PhyStats.IS_DARK);
			// W11: ABSOLUTE: as Darkness Globe, the lights in the room go dark before they light it (but LIGHTMASK's)
			if(absolute&&(affected instanceof Room)&&(!norecurse))
			{
				final Room R=(Room)affected;
				norecurse=true;
				try
				{
					affectableStats.setDisposition(affectableStats.disposition()&~PhyStats.IS_LIGHTSOURCE);
					for(final Enumeration<MOB> m=R.inhabitants();m.hasMoreElements();)
					{
						final MOB M=m.nextElement();
						if((M!=null)&&(!hasWorkingLight(M)))
							M.phyStats().setDisposition((M.phyStats().disposition()&~PhyStats.IS_LIGHTSOURCE)|PhyStats.IS_DARK);
					}
					for(final Enumeration<Item> i=R.items();i.hasMoreElements();)
					{
						final Item I=i.nextElement();
						if((I!=null)&&CMLib.flags().isLightSource(I)&&(!lightWorks(I)))
							I.phyStats().setDisposition(I.phyStats().disposition()&~PhyStats.IS_LIGHTSOURCE);
					}
				}
				finally
				{
					norecurse=false;
				}
			}
		}
	}

	protected boolean mine(final Room R)
	{
		if(R==null)
			return false;
		if(affected instanceof Room)
			return R==affected;
		return (affected instanceof Area)&&((Area)affected).inMyMetroArea(R.getArea());
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost,msg);
		final Room R=msg.source().location();
		// W11: SNUFF: lit lights may go out on the way in
		if((snuff>0)
		&&(msg.targetMinor()==CMMsg.TYP_ENTER)
		&&(msg.target() instanceof Room)
		&&mine((Room)msg.target())
		&&isItDark())
		{
			final MOB M=msg.source();
			for(final Enumeration<Item> i=M.items();i.hasMoreElements();)
			{
				final Item I=i.nextElement();
				if((I instanceof Light)&&((Light)I).isLit()&&(!lightWorks(I))&&(CMLib.dice().rollPercentage()<=snuff))
				{
					((Light)I).light(false);
					I.recoverPhyStats();
					M.tell(CMStrings.replaceAll(snuffMsg,"@x1",I.name()));
				}
			}
			M.recoverPhyStats();
		}
		// W11: ABSOLUTE: the room again after anything that may light it
		if(absolute&&(R!=null)&&mine(R))
		{
			switch(msg.targetMinor())
			{
			case CMMsg.TYP_ENTER:
			case CMMsg.TYP_HOLD:
			case CMMsg.TYP_WEAR:
			case CMMsg.TYP_WIELD:
			case CMMsg.TYP_REMOVE:
			case CMMsg.TYP_DROP:
			case CMMsg.TYP_GET:
			case CMMsg.TYP_PUT:
			case CMMsg.TYP_FILL:
				msg.addTrailerRunnable(new Runnable()
				{
					@Override
					public void run()
					{
						final Room R2=msg.source().location();
						if((R2!=null)&&mine(R2))
							R2.recoverPhyStats();
					}
				});
				break;
			default:
				break;
			}
		}
	}
}
