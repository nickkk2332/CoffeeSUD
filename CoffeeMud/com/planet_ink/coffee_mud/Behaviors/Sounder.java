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
public class Sounder extends StdBehavior
{
	@Override
	public String ID()
	{
		return "Sounder";
	}

	protected int	minTicks	= 23;
	protected int	maxTicks	= 23;
	protected int	tickDown	= (int) Math.round(CMath.random() * (maxTicks - minTicks)) + minTicks;

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_ITEMS|Behavior.CAN_MOBS|Behavior.CAN_ROOMS|Behavior.CAN_EXITS|Behavior.CAN_AREAS;
	}

	protected int[]			triggers		= null;
	protected String[]		strings			= null;
	protected static int	UNDER_MASK		= 1023;
	protected static int	TICK_MASK		= 65536;
	protected static int	ROOM_MASK		= 32768;
	protected CMMsg			lastMsg			= null;
	protected boolean		oncePerRound1	= false;

	public Sounder()
	{
		super();
		minTicks=23;
		maxTicks=23;
		tickReset();
	}

	@Override
	public String accountForYourself()
	{
		return "triggered emoting";
	}

	protected void tickReset()
	{
		tickDown=(int)Math.round(CMath.random()*(maxTicks-minTicks))+minTicks;
	}

	/** N22: the bark table, and what it has done */
	protected final List<Barks.Entry>    barks        = new ArrayList<Barks.Entry>();
	protected final Map<String,Long>    barkedAt    = new HashMap<String,Long>();
	protected final Set<String>        barkedFor    = new HashSet<String>();
	protected final Set<String>        greeted        = new HashSet<String>();
	protected final Set<Barks.Entry>    hurtDone    = new HashSet<Barks.Entry>();
	protected long                    barkTicks    = 0;
	protected String                lastTod        = null;
	protected String                lastWeather    = null;

	/**
	 * N22: takes the bark table entries (and FILE=) out of the parameters.
	 * @param all the parameters
	 * @return the old entries, as before
	 */
	protected String readBarks(final String all)
	{
		barks.clear();
		final StringBuilder rest=new StringBuilder("");
		for(final String e : CMParms.parseSemicolons((all==null)?"":all,true))
		{
			final String t=e.trim();
			if(t.toUpperCase().startsWith("FILE="))
			{
				final StringBuffer text=Resources.getFileResource(t.substring(5).trim(),true);
				if(text!=null)
				{
					for(final String line : text.toString().split("\\r?\\n"))
					{
						final String l=line.trim();
						if((l.length()>0)&&(!l.startsWith("#"))&&Barks.isEntry(l))
						{
							final Barks.Entry B=Barks.parse(l);
							if(B!=null)
								barks.add(B);
						}
					}
				}
				continue;
			}
			if(Barks.isEntry(t))
			{
				final Barks.Entry B=Barks.parse(t);
				if(B!=null)
					barks.add(B);
				continue;
			}
			if(rest.length()>0)
				rest.append(";");
			rest.append(e);
		}
		return rest.toString();
	}

	/**
	 * N22: barks for an event, if the table has a line for it now.
	 * @param host what has it
	 * @param event the event
	 * @param about the one it is about, or null
	 * @param thing what the entry's mask is tested on, or null for about
	 * @return true if it barked
	 */
	protected boolean bark(final Tickable host, final String event, final MOB about, final Environmental thing)
	{
		if(barks.isEmpty()||CMSecurity.isDisabled(CMSecurity.DisFlag.EMOTERS))
			return false;
		for(final Barks.Entry E : barks)
		{
			if(!E.event.equals(event))
				continue;
			if(!Barks.argFits(E,(thing!=null)?thing:about))
				continue;
			final String once=event+"/"+((about==null)?"":about.Name());
			if(E.oncePer&&(about!=null)&&barkedFor.contains(once))
				continue;
			final Long at=barkedAt.get(event);
			if((E.cooldown>0)&&(at!=null)&&((barkTicks-at.longValue())<E.cooldown))
				continue;
			if((E.chance<100)&&(CMLib.dice().rollPercentage()>E.chance))
				continue;
			final Barks.Line L=Barks.pick(E,about);
			if(L==null)
				continue;
			barkedAt.put(event,Long.valueOf(barkTicks));
			if(E.oncePer&&(about!=null))
				barkedFor.add(once);
			final String text=Barks.fill(L.text,about);
			if(host instanceof MOB)
			{
				final MOB mob=(MOB)host;
				final Room R=mob.location();
				if(R==null)
					return false;
				if(L.kind.equals("SAY"))
					CMLib.commands().postSay(mob,null,text,false,false);
				else
				if(L.kind.equals("SOUND"))
					R.showHappens(CMMsg.MSG_NOISE,"^E"+text+"^?");
				else
					R.show(mob,null,CMMsg.MSG_EMOTE,"<S-NAME> "+text);
			}
			else
			if(L.kind.equals("SOUND"))
				doEmote(host,"^E"+text+"^?");
			else
				doEmote(host,"<S-NAME> "+text);
			return true;
		}
		return false;
	}

	/** N22: the events the table hears in a message */
	protected void barkMsg(final Environmental host, final CMMsg msg)
	{
		if(barks.isEmpty()||(!(host instanceof Tickable)))
			return;
		final Tickable T=(Tickable)host;
		final MOB src=msg.source();
		final Room here=(host instanceof Room)?(Room)host:CMLib.map().roomLocation(host);
		if((src==null)||(here==null))
			return;
		final boolean death=(msg.sourceMinor()==CMMsg.TYP_DEATH)||(msg.othersMinor()==CMMsg.TYP_DEATH);
		if(death)
		{
			final MOB killer=(msg.tool() instanceof MOB)?(MOB)msg.tool():null;
			if((killer!=null)&&(killer==host))
				bark(T,"KILL",src,src);
			else
			if((host instanceof MOB)&&(src!=host)&&(src.playerStats()==null)
			&&((((MOB)host).getGroupMembers(new HashSet<MOB>()).contains(src))
				||src.charStats().raceName().equals(((MOB)host).charStats().raceName())))
				bark(T,"ALLY_DIES",(killer!=null)?killer:src,src);
			return;
		}
		if(src==host)
		{
			if(msg.sourceMinor()==CMMsg.TYP_FLEE)
				bark(T,"FLEE",(host instanceof MOB)?((MOB)host).getVictim():null,null);
			return;
		}
		if((msg.targetMinor()==CMMsg.TYP_ENTER)&&(msg.target()==here))
		{
			if(src.playerStats()!=null)
			{
				if(greeted.add(src.Name().toUpperCase()))
				{
					if(!bark(T,"GREET_FIRST",src,null))
						bark(T,"GREET",src,null);
				}
				else
					bark(T,"GREET",src,null);
				for(final Enumeration<Item> i=src.items();i.hasMoreElements();)
				{
					final Item I=i.nextElement();
					if((I!=null)&&(I.container()==null)&&bark(T,"SEE_ITEM",src,I))
						break;
				}
			}
			bark(T,"SEE_MOB",src,src);
			return;
		}
		if(msg.target()==host)
		{
			if(msg.targetMinor()==CMMsg.TYP_LIST)
				bark(T,"SHOP_LIST",src,null);
			else
			if(msg.targetMinor()==CMMsg.TYP_BUY)
				bark(T,"SHOP_BUY",src,null);
			else
			if((msg.targetMinor()==CMMsg.TYP_GIVE)&&(msg.tool() instanceof Coins))
				bark(T,"BRIBED",src,null);
			else
			if(CMath.bset(msg.targetMajor(),CMMsg.MASK_MALICIOUS))
				bark(T,"FIGHT",src,null);
			return;
		}
		if((src.location()==here)&&(msg.target() instanceof Item))
		{
			switch(msg.targetMinor())
			{
			case CMMsg.TYP_WIELD:
				bark(T,"WIELD",src,(Item)msg.target());
				break;
			case CMMsg.TYP_WEAR:
				bark(T,"WEAR",src,(Item)msg.target());
				break;
			case CMMsg.TYP_HOLD:
				bark(T,"HOLD",src,(Item)msg.target());
				break;
			case CMMsg.TYP_GET:
				bark(T,"GET",src,(Item)msg.target());
				break;
			case CMMsg.TYP_DROP:
				bark(T,"DROP",src,(Item)msg.target());
				break;
			}
		}
	}

	/** N22: the events the table hears on its ticks: the hour, the weather, being hurt, idling */
	protected void barkTick(final Tickable host)
	{
		if(barks.isEmpty())
			return;
		barkTicks++;
		final Physical P=(host instanceof Physical)?(Physical)host:null;
		final TimeClock C=(P!=null)?CMLib.time().localClock(P):CMLib.time().globalClock();
		if(C!=null)
		{
			final String tod=C.getTODCode().name();
			if((lastTod!=null)&&(!tod.equals(lastTod)))
				bark(host,tod,null,null);
			lastTod=tod;
		}
		final Room R=(host instanceof Room)?(Room)host:((P!=null)?CMLib.map().roomLocation(P):null);
		if((R!=null)&&(R.getArea()!=null))
		{
			final int w=R.getArea().getClimateObj().weatherType(R);
			final String wn=((w==Climate.WEATHER_RAIN)||(w==Climate.WEATHER_THUNDERSTORM))?"RAIN":
					((w==Climate.WEATHER_SNOW)||(w==Climate.WEATHER_BLIZZARD))?"SNOW":
					(w==Climate.WEATHER_CLEAR)?"CLEAR":"OTHER";
			if((lastWeather!=null)&&(!wn.equals(lastWeather)))
				bark(host,wn,null,null);
			lastWeather=wn;
		}
		if(host instanceof MOB)
		{
			final MOB mob=(MOB)host;
			final double pct=CMath.div(mob.curState().getHitPoints(),Math.max(1,mob.maxState().getHitPoints()))*100.0;
			for(final Barks.Entry E : barks)
			{
				if(!E.event.equals("HURT"))
					continue;
				final int under=CMath.s_int(E.arg);
				if(pct<under)
				{
					if(hurtDone.add(E))
						bark(host,"HURT",mob.getVictim(),null);
				}
				else
					hurtDone.remove(E);
			}
		}
		bark(host,"IDLE",null,null);
	}

	@Override
	protected String getRunState()
	{
		if(greeted.isEmpty()&&barkedFor.isEmpty())
			return "";
		return "<BARKS GREETED=\""+CMLib.xml().parseOutAngleBrackets(CMParms.toListString(greeted))
			+"\" ONCE=\""+CMLib.xml().parseOutAngleBrackets(CMParms.toListString(barkedFor))+"\" />";
	}

	@Override
	protected void setRunState(final String xml)
	{
		greeted.clear();
		barkedFor.clear();
		final List<XMLLibrary.XMLTag> V=CMLib.xml().parseAllXML(xml);
		if((V==null)||V.isEmpty())
			return;
		for(final String s : CMParms.parseCommas(CMLib.xml().restoreAngleBrackets(V.get(0).getParmValue("GREETED")),true))
			greeted.add(s);
		for(final String s : CMParms.parseCommas(CMLib.xml().restoreAngleBrackets(V.get(0).getParmValue("ONCE")),true))
			barkedFor.add(s);
	}

	@Override
	public void setParms(final String allParms)
	{
		super.setParms(allParms);
		final String newParms=readBarks(allParms); // N22: the bark table out, the rest as before
		final List<String> emote=CMParms.parseSemicolons(newParms,true);
		triggers=new int[emote.size()];
		strings=new String[emote.size()];

		if(emote.size()>0)
		{
			String s=emote.get(0);
			minTicks=23;
			minTicks=CMParms.getParmInt(newParms,"min",minTicks);
			maxTicks=23;
			maxTicks=CMParms.getParmInt(newParms,"max",maxTicks);
			if((minTicks!=23)||(maxTicks!=23))
				emote.remove(0);
			for(int v=0;v<emote.size();v++)
			{
				s=emote.get(v).trim();
				s=CMStrings.replaceAll(s,"$n","<S-NAME>");
				s=CMStrings.replaceAll(s,"$N","<S-NAME>");
				s=CMStrings.replaceAll(s,"$e","<S-HE-SHE>");
				s=CMStrings.replaceAll(s,"$E","<S-HE-SHE>");
				s=CMStrings.replaceAll(s,"$s","<S-HIS-HER>");
				s=CMStrings.replaceAll(s,"$S","<S-HIS-HER>");
				if(s.toUpperCase().startsWith("SOUND "))
				{
					s=s.substring(6).trim();
					final int x=s.indexOf(' ');
					if(x<0)
						continue;
					final String y=s.substring(0,x);
					if(!CMath.isNumber(y))
						continue;
					triggers[v]=TICK_MASK+CMath.s_int(y);
					s="^E"+s.substring(x+1).trim()+"^?";
					strings[v]=s;
				}
				else
				if((s.toUpperCase().startsWith("GET ")))
				{
					triggers[v]=CMMsg.TYP_GET;
					strings[v]=s.substring(4).trim();
				}
				else
				if((s.toUpperCase().startsWith("GET_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_GET|ROOM_MASK;
					strings[v]=s.substring(9).trim();
				}
				else
				if((s.toUpperCase().startsWith("EAT_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_EAT|ROOM_MASK;
					strings[v]=s.substring(9).trim();
				}
				else
				if((s.toUpperCase().startsWith("EAT ")))
				{
					triggers[v]=CMMsg.TYP_EAT;
					strings[v]=s.substring(4).trim();
				}
				else
				if((s.toUpperCase().startsWith("PUSH_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_PUSH|ROOM_MASK;
					strings[v]=s.substring(10).trim();
				}
				else
				if((s.toUpperCase().startsWith("PUSH ")))
				{
					triggers[v]=CMMsg.TYP_PUSH;
					strings[v]=s.substring(5).trim();
				}
				else
				if((s.toUpperCase().startsWith("PULL_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_PULL|ROOM_MASK;
					strings[v]=s.substring(10).trim();
				}
				else
				if((s.toUpperCase().startsWith("PULL ")))
				{
					triggers[v]=CMMsg.TYP_PULL;
					strings[v]=s.substring(5).trim();
				}
				else
				if((s.toUpperCase().startsWith("SIT ")))
				{
					triggers[v]=CMMsg.TYP_SIT;
					strings[v]=s.substring(4).trim();
				}
				else
				if((s.toUpperCase().startsWith("SIT_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_SIT|ROOM_MASK;
					strings[v]=s.substring(9).trim();
				}
				else
				if((s.toUpperCase().startsWith("DROP ")))
				{
					triggers[v]=CMMsg.TYP_DROP;
					strings[v]=s.substring(5).trim();
				}
				else
				if((s.toUpperCase().startsWith("DROP_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_DROP|ROOM_MASK;
					strings[v]=s.substring(10).trim();
				}
				else
				if((s.toUpperCase().startsWith("WEAR ")))
				{
					triggers[v]=CMMsg.TYP_WEAR;
					strings[v]=s.substring(5).trim();
				}
				else
				if((s.toUpperCase().startsWith("WEAR_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_WEAR|ROOM_MASK;
					strings[v]=s.substring(10).trim();
				}
				else
				if((s.toUpperCase().startsWith("OPEN ")))
				{
					triggers[v]=CMMsg.TYP_OPEN;
					strings[v]=s.substring(5).trim();
				}
				else
				if((s.toUpperCase().startsWith("OPEN_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_OPEN|ROOM_MASK;
					strings[v]=s.substring(10).trim();
				}
				else
				if((s.toUpperCase().startsWith("CLOSE ")))
				{
					triggers[v]=CMMsg.TYP_CLOSE;
					strings[v]=s.substring(6).trim();
				}
				else
				if((s.toUpperCase().startsWith("CLOSE_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_CLOSE|ROOM_MASK;
					strings[v]=s.substring(11).trim();
				}
				else
				if((s.toUpperCase().startsWith("HOLD ")))
				{
					triggers[v]=CMMsg.TYP_HOLD;
					strings[v]=s.substring(5).trim();
				}
				else
				if((s.toUpperCase().startsWith("HOLD_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_HOLD|ROOM_MASK;
					strings[v]=s.substring(10).trim();
				}
				else
				if((s.toUpperCase().startsWith("WIELD ")))
				{
					triggers[v]=CMMsg.TYP_WIELD;
					strings[v]=s.substring(6).trim();
				}
				else
				if((s.toUpperCase().startsWith("WIELD_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_WIELD|ROOM_MASK;
					strings[v]=s.substring(11).trim();
				}
				else
				if((s.toUpperCase().startsWith("DRINK ")))
				{
					triggers[v]=CMMsg.TYP_DRINK;
					strings[v]=s.substring(6).trim();
				}
				else
				if((s.toUpperCase().startsWith("DRINK_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_DRINK|ROOM_MASK;
					strings[v]=s.substring(11).trim();
				}
				else
				if((s.toUpperCase().startsWith("MOUNT ")))
				{
					triggers[v]=CMMsg.TYP_MOUNT;
					strings[v]=s.substring(6).trim();
				}
				else
				if((s.toUpperCase().startsWith("MOUNT_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_MOUNT|ROOM_MASK;
					strings[v]=s.substring(11).trim();
				}
				else
				if((s.toUpperCase().startsWith("REMOVE ")))
				{
					triggers[v]=CMMsg.TYP_REMOVE;
					strings[v]=s.substring(7).trim();
				}
				else
				if((s.toUpperCase().startsWith("REMOVE_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_REMOVE|ROOM_MASK;
					strings[v]=s.substring(12).trim();
				}
				else
				if((s.toUpperCase().startsWith("PORTAL_ENTER ")))
				{
					triggers[v]=CMMsg.TYP_ENTER;
					strings[v]=s.substring(13).trim();
				}
				else
				if((s.toUpperCase().startsWith("PORTAL_ENTER_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_ENTER|ROOM_MASK;
					strings[v]=s.substring(18).trim();
				}
				else
				if((s.toUpperCase().startsWith("PORTAL_EXIT ")))
				{
					triggers[v]=CMMsg.TYP_LEAVE;
					strings[v]=s.substring(12).trim();
				}
				else
				if((s.toUpperCase().startsWith("PORTAL_EXIT_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_LEAVE|ROOM_MASK;
					strings[v]=s.substring(17).trim();
				}
				else
				if((s.toUpperCase().startsWith("DAMAGE ")))
				{
					triggers[v]=CMMsg.TYP_DAMAGE;
					strings[v]=s.substring(7).trim();
				}
				else
				if((s.toUpperCase().startsWith("DAMAGE_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_DAMAGE|ROOM_MASK;
					strings[v]=s.substring(12).trim();
				}
				else
				if((s.toUpperCase().startsWith("FIGHT ")))
				{
					triggers[v]=CMMsg.TYP_WEAPONATTACK;
					strings[v]=s.substring(6).trim();
				}
				else
				if((s.toUpperCase().startsWith("FIGHT_ROOM ")))
				{
					triggers[v]=CMMsg.TYP_WEAPONATTACK|ROOM_MASK;
					strings[v]=s.substring(11).trim();
				}
			}
		}
		tickReset();
	}

	protected void emoteHere(final Room room, final MOB emoter, final String emote)
	{
		if(room==null)
			return;
		final Room oldLoc=emoter.location();
		if(emoter.location()!=room)
			emoter.setLocation(room);
		final CMMsg msg=CMClass.getMsg(emoter,null,CMMsg.MSG_EMOTE,emote);
		if(room.okMessage(emoter,msg))
		{
			for(int i=0;i<room.numInhabitants();i++)
			{
				final MOB M=room.fetchInhabitant(i);
				if((M!=null)
				&&(!M.isMonster())
				&&(CMLib.flags().canSenseMoving(emoter,M)))
					M.executeMsg(M,msg);
			}
		}
		if(oldLoc!=null)
			emoter.setLocation(oldLoc);
	}

	public void doEmote(final Tickable ticking, String emote)
	{
		MOB emoter=null;
		emote=CMStrings.replaceAll(emote,"$p",ticking.name());
		emote=CMStrings.replaceAll(emote,"$P",ticking.name());
		if(ticking instanceof Area)
		{
			emoter=CMClass.getMOB("StdMOB");
			emoter.setName(ticking.name());
			emoter.charStats().setStat(CharStats.STAT_GENDER,'N');
			for(final Enumeration<Room> r=((Area)ticking).getMetroMap();r.hasMoreElements();)
			{
				final Room R=r.nextElement();
				emoteHere(R,emoter,emote);
			}
			emoter.destroy();
		}
		else
		if(ticking instanceof Room)
		{
			emoter=CMClass.getMOB("StdMOB");
			emoter.setName(ticking.name());
			emoter.charStats().setStat(CharStats.STAT_GENDER,'N');
			emoteHere((Room)ticking,emoter,emote);
			emoter.destroy();
		}
		else
		if(ticking instanceof MOB)
		{
			emoter=(MOB)ticking;
			if(!canFreelyBehaveNormal(ticking))
				return;
			emoteHere(((MOB)ticking).location(),emoter,emote);
		}
		else
		{
			if((ticking instanceof Item)&&(!CMLib.flags().isInTheGame((Item)ticking,false)))
				return;
			final Room R=getBehaversRoom(ticking);
			if(R!=null)
			{
				emoter=CMClass.getMOB("StdMOB");
				emoter.setName(ticking.name());
				emoter.charStats().setStat(CharStats.STAT_GENDER,'N');
				emoteHere(R,emoter,emote);
				emoter.destroy();
			}
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking,tickID))
			return false;
		if((tickID==Tickable.TICKID_MOB)||(!(ticking instanceof MOB)))
			barkTick(ticking); // N22
		if(((--tickDown)<=0)
		&&(!CMSecurity.isDisabled(CMSecurity.DisFlag.EMOTERS))
		&&((!(ticking instanceof MOB))||(canFreelyBehaveNormal(ticking))))
		{
			tickReset();
			for(int v=0;v<triggers.length;v++)
			if((CMath.bset(triggers[v],TICK_MASK))
			&&(CMLib.dice().rollPercentage()<(triggers[v]&UNDER_MASK)))
			{
				doEmote(ticking,strings[v]);
				break;
			}
		}
		oncePerRound1=false;
		return true;
	}

	@Override
	public void executeMsg(final Environmental host, final CMMsg msg)
	{
		barkMsg(host,msg); // N22
		// this will work because, for items, behaviors
		// get the first tick.
		int lookFor=-1;
		if((msg!=lastMsg)&&(!CMSecurity.isDisabled(CMSecurity.DisFlag.EMOTERS)))
		switch(msg.targetMinor())
		{
		case CMMsg.TYP_OPEN:
		case CMMsg.TYP_CLOSE:
			if((msg.target()==host)
			||((!(host instanceof Item))&&(!(host instanceof Exit))))
				lookFor=msg.targetMinor();
			break;
		case CMMsg.TYP_GET:
		case CMMsg.TYP_PUSH:
		case CMMsg.TYP_PULL:
		case CMMsg.TYP_REMOVE:
		case CMMsg.TYP_WEAR:
		case CMMsg.TYP_HOLD:
		case CMMsg.TYP_WIELD:
		case CMMsg.TYP_EAT:
		case CMMsg.TYP_DRINK:
		case CMMsg.TYP_SIT:
		case CMMsg.TYP_SLEEP:
		case CMMsg.TYP_MOUNT:
			if((msg.target()==host)||(!(host instanceof Item)))
				lookFor=msg.targetMinor();
			break;
		case CMMsg.TYP_DROP:
			if(((!(host instanceof Item))||(msg.target()==host))
			&&(msg.target() instanceof Item))
				lookFor=CMMsg.TYP_DROP;
			break;
		case CMMsg.TYP_ENTER:
			if((msg.target()!=null)
			&&(msg.target()==getBehaversRoom(host)))
				lookFor=CMMsg.TYP_ENTER;
			break;
		case CMMsg.TYP_LEAVE:
			if((msg.target()!=null)
			&&(msg.target()==getBehaversRoom(host)))
				lookFor=CMMsg.TYP_LEAVE;
			break;
		case CMMsg.TYP_WEAPONATTACK:
			if((msg.target()!=null)
			&&(msg.target()!=host)
			&&((msg.source()==host)||(msg.tool()==host)||(host instanceof Room)||(host instanceof Exit))
			&&(!oncePerRound1))
				lookFor=CMMsg.TYP_WEAPONATTACK;
			break;
		case CMMsg.TYP_DAMAGE:
			if((msg.target()!=null)
			&&(msg.source()!=host)
			&&((msg.target()==host)||(msg.tool()==host)||(host instanceof Room)||(host instanceof Exit)))
				lookFor=CMMsg.TYP_DAMAGE;
			break;
		}
		lastMsg=msg;
		final Room room=msg.source().location();
		if((lookFor>=0)
		&&(room!=null)
		&&((!(host instanceof MOB))||(lookFor==CMMsg.TYP_WEAPONATTACK)
								||(lookFor==CMMsg.TYP_DAMAGE)
								||(canFreelyBehaveNormal(host))))
		{
			for(int v=0;v<triggers.length;v++)
			{
				if(((triggers[v]&UNDER_MASK)==lookFor)
				&&(!CMath.bset(triggers[v],TICK_MASK)))
				{
					final CMMsg msg2;
					if(CMath.bset(triggers[v],ROOM_MASK))
						msg2=CMClass.getMsg(msg.source(),null,null,CMMsg.NO_EFFECT,CMMsg.NO_EFFECT,CMMsg.MSG_EMOTE,CMStrings.replaceAll(strings[v],"$p",host.name()));
					else
						msg2=CMClass.getMsg(msg.source(),null,null,CMMsg.MSG_EMOTE,CMMsg.NO_EFFECT,CMMsg.NO_EFFECT,CMStrings.replaceAll(strings[v],"$p",host.name()));
					if(lookFor==CMMsg.TYP_ENTER)
					{
						msg.addTrailerRunnable(new Runnable()
						{
							final Room R=room;
							final CMMsg msg = msg2;
							@Override
							public void run()
							{
								CMLib.threads().scheduleRunnable(new Runnable()
								{
									@Override
									public void run()
									{
										if(R.isInhabitant(msg.source()))
										{
											if(R.okMessage(msg.source(),msg))
												R.send(msg.source(), msg);
										}
									}
								}, 500);
							}
						});
					}
					else
						msg.addTrailerMsg(msg2);
				}
			}
		}
		super.executeMsg(host,msg);
	}
}
