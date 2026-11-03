package com.planet_ink.coffee_mud.Behaviors;
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
import com.planet_ink.coffee_mud.Libraries.interfaces.MaskingLibrary.CompiledZMask;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;

/*
   Copyright 2003-2025 Bo Zimmerman

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
public class ResourceOverride extends ActiveTicker
{
	@Override
	public String ID()
	{
		return "ResourceOverride";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_ROOMS | Behavior.CAN_AREAS | CAN_MOBS;
	}

	private final List<String>	aids		= new Vector<String>();
	private final List<Integer>	rscs		= new Vector<Integer>();
	private final List<Item>	crscs		= new Vector<Item>();
	private final Set<Integer>	roomTypes	= new TreeSet<Integer>();
	private CompiledZMask		mobMask		= null;

	private volatile Pair<MOB,Ability> corpseWatcher = null;

	// CR11: by the calendar
	private final Map<TimeClock.Season,List<Integer>> bySeason = new EnumMap<TimeClock.Season,List<Integer>>(TimeClock.Season.class);
	private final Set<TimeClock.Season>    seasons        = EnumSet.noneOf(TimeClock.Season.class);
	private final Set<Integer>            months        = new TreeSet<Integer>();
	private final Set<Integer>            hours        = new TreeSet<Integer>();
	private String                        changeMsg    = "";
	private String                        lastState    = null;
	private final Set<Room>                overridden    = Collections.newSetFromMap(new WeakHashMap<Room,Boolean>());

	private static TimeClock.Season seasonNamed(final String s)
	{
		final String u=s.trim().toUpperCase();
		if(u.equals("AUTUMN"))
			return TimeClock.Season.FALL;
		return (TimeClock.Season)CMath.s_valueOf(TimeClock.Season.class, u);
	}

	private static void numbers(final String val, final Set<Integer> into)
	{
		for(final String s : CMParms.parseCommas(val, true))
		{
			final int dash=s.indexOf('-',1);
			if(dash>0)
			{
				final int lo=CMath.s_int(s.substring(0,dash).trim());
				final int hi=CMath.s_int(s.substring(dash+1).trim());
				for(int i=lo;i<=hi;i++)
					into.add(Integer.valueOf(i));
			}
			else
			if(CMath.isInteger(s.trim()))
				into.add(Integer.valueOf(CMath.s_int(s.trim())));
		}
	}

	/**
	 * CR11: the resources that apply now, by the clock: none (null) outside
	 * SEASON=, MONTH= and HOUR=, the season's own list (SPRING=...) when it has
	 * one, or else the usual list.
	 * @param C the clock
	 * @return the resources, or null for the room's own
	 */
	protected List<Integer> active(final TimeClock C)
	{
		if(C!=null)
		{
			if((seasons.size()>0)&&(!seasons.contains(C.getSeasonCode())))
				return null;
			if((months.size()>0)&&(!months.contains(Integer.valueOf(C.getMonth()))))
				return null;
			if((hours.size()>0)&&(!hours.contains(Integer.valueOf(C.getHourOfDay()))))
				return null;
			final List<Integer> L=bySeason.get(C.getSeasonCode());
			if(L!=null)
				return L;
		}
		return (rscs.size()>0)?rscs:null;
	}

	/**
	 * CR11: puts the resources that apply now on a room, or gives it back its
	 * own, telling MSG= there when the calendar turned it.
	 * @param R the room
	 * @param active what applies now, or null
	 * @param turned whether the calendar just changed it
	 */
	protected void applyTo(final Room R, final List<Integer> active, final boolean turned)
	{
		boolean changed=false;
		if(active==null)
		{
			if(overridden.remove(R))
			{
				R.setResource(-1);
				changed=true;
			}
		}
		else
		if((active.size()>0)&&(!active.contains(Integer.valueOf(R.myResource()))))
		{
			R.setResource(active.get(CMLib.dice().roll(1,active.size(),-1)).intValue());
			overridden.add(R);
			changed=true;
		}
		if(changed&&turned&&(changeMsg.length()>0))
			R.showHappens(CMMsg.MSG_OK_ACTION, changeMsg);
	}

	@Override
	public String accountForYourself()
	{
		return "resource overriding";
	}

	protected static Integer getRscTypeIfAny(String which)
	{
		int code=-1;
		if(which.equalsIgnoreCase("none"))
			return Integer.valueOf(0);
		if(CMath.isInteger(which))
			code=CMath.s_int(which);
		if(code<0)
			code = RawMaterial.CODES.FIND_IgnoreCase(which);
		if(which.indexOf('_')>0)
			which=which.replace('_',' ');
		if(code<0)
		{
			final RawMaterial.Material m=RawMaterial.Material.findIgnoreCase(which);
			if(m!=null)
				code=RawMaterial.CODES.COMPOSE_RESOURCES(m.mask()).get(0).intValue();
		}
		if(code<0)
			code = RawMaterial.CODES.FIND_StartsWith(which);
		if(code<0)
		{
			final RawMaterial.Material m=RawMaterial.Material.startsWith(which);
			if(m!=null)
				code=RawMaterial.CODES.COMPOSE_RESOURCES(m.mask()).get(0).intValue();
		}
		if((code<0)&&(which.endsWith("S")||which.endsWith("s"))&&(which.length()>1))
		{
			final Integer I=getRscTypeIfAny(which.substring(0,which.length()-1));
			if(I!=null)
				code=I.intValue();

		}
		if(code < 0)
			return null;
		return Integer.valueOf(code);
	}

	@Override
	public void setParms(final String newStr)
	{
		super.setParms(newStr);
		super.tickDown=1;
		rscs.clear();
		crscs.clear();
		roomTypes.clear();
		this.mobMask = null;
		bySeason.clear(); // CR11
		seasons.clear();
		months.clear();
		hours.clear();
		lastState=null;
		String parms=getParms();
		changeMsg=CMParms.getParmStr(parms,"MSG",CMParms.getParmStr(parms,"MESSAGE","")).trim();
		parms=parms.replaceAll("(?i)\\b(MSG|MESSAGE)\\s*=\\s*(\"[^\"]*\"|\\S+)", " ");
		final Vector<String> V=CMParms.parse(parms);
		for(int v=0;v<V.size();v++) // CR11: the calendar's keys, before any MOBMASK=
		{
			final String w=V.elementAt(v).trim();
			if(w.toUpperCase().startsWith("MOBMASK"))
				break;
			final int eq=w.indexOf('=');
			if(eq<=0)
				continue;
			final String key=w.substring(0,eq).trim().toUpperCase();
			final String val=w.substring(eq+1).trim();
			final TimeClock.Season season=seasonNamed(key);
			if(season!=null)
			{
				final List<Integer> L=new Vector<Integer>();
				for(final String r : CMParms.parseCommas(val, true))
				{
					final Integer c=getRscTypeIfAny(r.trim().toUpperCase());
					if(c==null)
						Log.errOut("Unknown ResourceOverride resource '"+r+"' in '"+newStr+"'");
					else
					if(!L.contains(c))
						L.add(c);
				}
				bySeason.put(season, L);
			}
			else
			if(key.equals("SEASON")||key.equals("SEASONS"))
			{
				for(final String s : CMParms.parseCommas(val, true))
				{
					final TimeClock.Season S=seasonNamed(s);
					if(S!=null)
						seasons.add(S);
				}
			}
			else
			if(key.equals("MONTH")||key.equals("MONTHS"))
				numbers(val, months);
			else
			if(key.equals("HOUR")||key.equals("HOURS"))
				numbers(val, hours);
			else
				continue;
			V.removeElementAt(v);
			v--;
		}
		if(V.size()==0)
			return;
		for(int v=0;v<V.size();v++)
		{
			// first try for a real one
			final String which=V.elementAt(v).toUpperCase().trim();
			if(which.startsWith("MOBMASK"))
			{
				final String w=which.substring(7).trim();
				if(w.startsWith("="))
				{
					final String mask=(w.substring(1) + " " + CMParms.combineQuoted(V,v+1)).trim();
					this.mobMask = CMLib.masking().getPreCompiledMask(mask);
					break;
				}
			}
			if(which.startsWith("MIN")
			||which.startsWith("MAX")
			||which.startsWith("CHANCE"))
				continue;
			final Integer c = getRscTypeIfAny(which);
			if(c!=null)
			{
				if(!rscs.contains(c))
					rscs.add(c);
			}
			else
			{
				int code=-1;
				for(int i=0;i<Room.DOMAIN_OUTDOOR_DESCS.length;i++)
				{
					if(which.equalsIgnoreCase(Room.DOMAIN_OUTDOOR_DESCS[i]))
					{
						code = i;
						break;
					}
				}
				if(code<0)
				{
					for(int i=0;i<Room.DOMAIN_INDOORS_DESCS.length;i++)
					{
						if(which.equalsIgnoreCase(Room.DOMAIN_INDOORS_DESCS[i]))
						{
							code = Room.INDOORS | i;
							break;
						}
					}
				}
				if(code>=0)
					roomTypes.add(Integer.valueOf(code));
				else
				{
					String uwhich = which;
					int p=uwhich.indexOf('(');
					String rscName = null;
					if((p>0)&&(uwhich.endsWith(")")))
					{
						final String lwhich = V.elementAt(v).trim();
						rscName = lwhich.substring(0,p);
						uwhich = uwhich.substring(p+1,uwhich.length()-1).trim();
					}
					p=uwhich.indexOf(' ');
					Integer rscType = null;
					String subType = null;
					if(p>0)
					{
						rscType = ResourceOverride.getRscTypeIfAny(uwhich.substring(0,p).trim());
						if(rscType != null)
							subType = uwhich.substring(p+1).trim();
					}
					else
						rscType = ResourceOverride.getRscTypeIfAny(uwhich);
					if(rscType != null)
					{
						final Item rscPrototype = CMLib.materials().makeItemResource(rscType.intValue(), (subType == null)?"":subType );
						if(rscName != null)
						{
							rscPrototype.setName(rscName);
							rscPrototype.setDisplayText(L("@x1 is here",rscName));
						}
						crscs.add(rscPrototype);
					}
					else
					{
						final Ability A=(p<0)?CMClass.findAbility(which):null;
						if(A!=null)
							this.aids.add(A.ID());
						else
							Log.errOut("Unknown ResourceOverride resource/room code '"+uwhich+"' in '"+newStr+"'");
					}
				}
			}
		}
	}

	@Override
	public boolean tick(final Tickable ticking, int tickID)
	{
		super.tick(ticking,tickID);
		if((rscs.size()==0)&&(bySeason.size()==0))
			return true;
		if(super.canAct(ticking, tickID))
		{
			if(tickID == 0)
				tickID = (ticking instanceof Area) ? Tickable.TICKID_AREA : Tickable.TICKID_ROOM_BEHAVIOR;
			final TimeClock C=(ticking instanceof Area)?((Area)ticking).getTimeObj()
				:((ticking instanceof Room)&&(((Room)ticking).getArea()!=null))?((Room)ticking).getArea().getTimeObj():null;
			final List<Integer> active=active(C); // CR11: by the calendar
			final String state=(active==null)?"-":active.toString();
			final boolean turned=(lastState!=null)&&(!lastState.equals(state));
			lastState=state;
			switch(tickID)
			{
			case Tickable.TICKID_ROOM_BEHAVIOR:
				if(ticking instanceof Room)
					applyTo((Room)ticking, active, turned);
				break;
			case Tickable.TICKID_AREA:
				if(ticking instanceof Area)
				{
					final Area A=(Area)ticking;
					Room R=null;
					for(final Enumeration<Room> e=A.getMetroMap();e.hasMoreElements();)
					{
						R=e.nextElement();
						if((R!=null)
						&&((roomTypes.size()==0)||(roomTypes.contains(Integer.valueOf(R.domainType())))))
							applyTo(R, active, turned);
					}
				}
			}
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental host, final CMMsg msg)
	{
		if((msg.source()==host)
		&&(msg.sourceMinor()==CMMsg.TYP_BODYDROP)
		&&(msg.target() instanceof DeadBody)
		&&(canChance()))
			((DeadBody)msg.target()).addBehavior(this);
		else
		if((host instanceof DeadBody)
		&&(msg.tool() instanceof Ability)
		&&((((Ability)msg.tool()).classificationCode()&Ability.ALL_ACODES)==Ability.ACODE_COMMON_SKILL)
		&&(msg.sourceMinor()!=CMMsg.TYP_ITEMGENERATED)
		&&((aids.size()==0)||((msg.tool() instanceof Ability)&&(aids.contains(msg.tool().ID())))))
		{
			final Pair<MOB, Ability> w;
			synchronized(this)
			{
				w = corpseWatcher;
			}
			if((w!=null)
			&&((w.first==msg.source())||(w.second==msg.tool())))
				corpseWatcher = null;
			if(msg.target()==host)
				corpseWatcher = new Pair<MOB,Ability>(msg.source(), (Ability)msg.tool());
		}
	}

	@Override
	public boolean okMessage(final Environmental host, final CMMsg msg)
	{
		if(!super.okMessage(host, msg))
			return false;

		if((msg.sourceMinor()==CMMsg.TYP_ITEMGENERATED)
		&&(msg.target() instanceof RawMaterial)
		&&((aids.size()==0)||((msg.tool() instanceof Ability)&&(aids.contains(msg.tool().ID())))))
		{
			if(host instanceof DeadBody)
			{
				final Pair<MOB, Ability> corpseWatcher = this.corpseWatcher;
				if(((mobMask == null)||(CMLib.masking().maskCheck(mobMask, msg.source(), true)))
				&&(corpseWatcher!=null)
				&&(msg.source()==corpseWatcher.first)
				&&(msg.tool().ID().equals(corpseWatcher.second.ID())))
				{
					if(CMLib.dice().roll(1, crscs.size()+rscs.size(), 0)<=crscs.size())
						msg.setTarget((Physical)crscs.get(CMLib.dice().roll(1, crscs.size(), -1)).copyOf());
					else
						msg.setTarget(CMLib.materials().makeItemResource(rscs.get(CMLib.dice().roll(1, rscs.size(), -1)).intValue(),""));
					if(msg.target() instanceof RawMaterial)
						((Item)msg.target()).setSecretIdentity("");
				}
			}
			else
			if(((host instanceof Room)||(host instanceof Area))
			&&(crscs.size()>0))
			{
				if((mobMask == null)
				||(CMLib.masking().maskCheck(mobMask, msg.source(), true)))
				{
					if(CMLib.dice().roll(1, crscs.size()+rscs.size(), 0)<=crscs.size())
					{
						msg.setTarget((Physical)crscs.get(CMLib.dice().roll(1, crscs.size(), -1)).copyOf());
						if(msg.target() instanceof RawMaterial)
							((Item)msg.target()).setSecretIdentity("");
					}
				}
			}
		}
		return true;
	}
}
