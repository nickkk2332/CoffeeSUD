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
/**
 * Resets a room or an area (its mobs and items, from the world) once no
 * player has been there for TICKS ticks, or DAYS game days of the area's
 * calendar (W9), or at a mud hour (MUDHOUR) or a real hour (RLHOUR, which
 * means nothing with pause and turn-based time).  EXCEPT= is a mask of the
 * mobs and items that never come back once gone: a slain boss stays slain,
 * one still there stays.  MSG= is shown to the first who comes in after a
 * reset.
 */
public class ResetWhole extends StdBehavior implements Travel.CommittedEntry
{
	@Override
	public String ID()
	{
		return "ResetWhole";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_ROOMS|Behavior.CAN_AREAS;
	}

	protected long		lastAccess	= -1;
	protected long		time		= 1800000;
	protected int		rlHour		= -1;
	protected int		mudHour		= -1;
	protected boolean	resetDone	= false;
	/** W9: game days on the area's calendar, in place of TICKS, or 0 */
	protected double	days		= 0;
	/** W9: what never comes back once gone, or null */
	protected MaskingLibrary.CompiledZMask exceptMask = null;
	/** W9: shown on the first entry after a reset */
	protected String	resetMsg	= "";
	/** W9: a reset happened and no one has come in since */
	protected boolean	msgPending	= false;
	protected boolean onReentry=false, away=false;
	protected double minAway=0;
	protected MaskingLibrary.CompiledZMask skipRooms=null;

	@Override
	public String accountForYourself()
	{
		return "periodic resetting";
	}

	@Override
	public void setParms(final String parameters)
	{
		super.setParms(parameters);
		try
		{
			time = 30000;
			rlHour = -1;
			mudHour= -1;
			days = 0;
			resetDone = false;
			if(CMath.isNumber(parameters))
			{
				time=Long.parseLong(parameters);
				time=time*CMProps.getTickMillis();
			}
			else
			{
				String s = CMParms.getParmStr(parameters, "TICKS", "");
				if(CMath.isNumber(s))
				{
					time=CMath.s_long(s);
					time=time*CMProps.getTickMillis();
				}
				else
				{
					s = CMParms.getParmStr(parameters, "DAYS", "");
					if(CMath.isNumber(s) && (CMath.s_double(s) > 0))
						days = CMath.s_double(s);
					else
					{
						s = CMParms.getParmStr(parameters, "RLHOUR", "");
						if(CMath.isNumber(s))
							this.rlHour = CMath.s_int(s);
						else
						{
							s = CMParms.getParmStr(parameters, "MUDHOUR", "");
							if(CMath.isNumber(s))
								this.mudHour=CMath.s_int(s);
						}
					}
				}
			}
			final String except = CMParms.getParmStr(parameters, "EXCEPT", "").trim();
			exceptMask = (except.length() == 0) ? null : CMLib.masking().getPreCompiledMask(quotedNames(except));
			resetMsg = CMParms.getParmStr(parameters,"RESETMSG",CMParms.getParmStr(parameters, "MSG", "")).trim();
			onReentry=CMParms.getParmBool(parameters,"ONREENTRY",false);
			minAway=Math.max(0,CMParms.getParmDouble(parameters,"MINAWAY",0));
			final String skip=quotedNames(Organizations.parameterMask(CMParms.getParmStr(parameters,"SKIPROOMS","")));
			skipRooms=skip.length()==0?null:CMLib.masking().getPreCompiledMask(skip);
		}
		catch(final Exception e)
		{
		}
	}

	/**
	 * W9: a mask's +'name with spaces' (or -'...'), as a parameter in double
	 * quotes can hold it, in the masks' own "+name with spaces" form.
	 * @param mask the mask
	 * @return the mask the masking library reads
	 */
	public static String quotedNames(final String mask)
	{
		return mask.replaceAll("([+-])'([^']*)'", "\"$1$2\"");
	}

	/**
	 * W9: how long no player must be there before a reset: DAYS on the
	 * host's calendar, else TICKS.
	 * @param host the room or area
	 * @return the game milliseconds
	 */
	protected long period(final Tickable host)
	{
		if(days <= 0)
			return time;
		final Physical P = (host instanceof Physical) ? (Physical)host : super.getBehaversRoom(host);
		final TimeClock C = (P == null) ? null : CMLib.time().localClock(P);
		final int hours = (C == null) ? 24 : Math.max(1, C.getHoursInDay());
		return Math.round(days * hours * CMProps.getMillisPerMudHour());
	}

	private boolean mine(final Environmental host, final Room R)
	{
		if(R == null)
			return false;
		if(host instanceof Area)
			return ((Area)host).inMyMetroArea(R.getArea());
		return R == host;
	}

	@Override
	public void executeMsg(final Environmental host, final CMMsg msg)
	{
		super.executeMsg(host,msg);
		if(!msg.source().isMonster() || msg.source().isPlayer())
		{
			if(msg.targetMinor()==CMMsg.TYP_LEAVE&&msg.target() instanceof Room&&mine(host,(Room)msg.target())&&!occupied(host,msg.source()))
			{ away=true; lastAccess=GameTime.now(); }
			if(msg.targetMinor()==CMMsg.TYP_ENTER&&msg.target() instanceof Room&&mine(host,(Room)msg.target())) away=false;
			if(mine(host, msg.source().location()))
				lastAccess=GameTime.now();
			if(msgPending
			&& (msg.targetMinor() == CMMsg.TYP_ENTER)
			&& (msg.target() instanceof Room)
			&& mine(host, (Room)msg.target()))
			{
				lastAccess=GameTime.now();
				msgPending = false;
				if(resetMsg.length() > 0)
				{
					final MOB M = msg.source();
					final String text = resetMsg;
					msg.addTrailerRunnable(new Runnable()
					{
						@Override
						public void run()
						{
							M.tell(text);
						}
					});
				}
			}
		}
	}

	private boolean occupied(final Environmental host, final MOB except)
	{
		if(!(host instanceof Tickable)) return false;
		for(final Room R : rooms((Tickable)host,true))
			for(final Enumeration<MOB> e=R.inhabitants();e.hasMoreElements();)
			{ final MOB M=e.nextElement(); if(M!=except&&M.isPlayer()) return true; }
		return false;
	}

	@Override public void entering(final Environmental host, final MOB who, final Room from, final Room to)
	{
		if(onReentry&&away&&lastAccess>=0&&who.isPlayer()&&mine(host,to)&&!mine(host,from)
		&&!occupied(host,who)&&GameTime.now()-lastAccess>=Math.round(minAway*CMProps.getMillisPerMudHour()))
		{
			resetNow((Tickable)host,who.getGroupLeader()); away=false; lastAccess=GameTime.now();
		}
	}

	private boolean isRoomBeingCamped(final Room R)
	{
		if(CMLib.flags().canNotBeCamped(R)
		&& (R.numPCInhabitants() > 0)
		&& (!CMLib.hunt().isAnAdminHere(R,false)))
		{
			return true;
		}
		return false;
	}

	/**
	 * W9: the rooms a reset reaches: the host room, or each room of the
	 * host area without a ResetWhole of its own.
	 * @param ticking the host
	 * @param all true for every room of the area, those skipped too
	 * @return the rooms
	 */
	protected List<Room> rooms(final Tickable ticking, final boolean all)
	{
		final List<Room> list = new ArrayList<Room>();
		if(ticking instanceof Area)
		{
			for(final Enumeration<Room> r=((Area)ticking).getMetroMap();r.hasMoreElements();)
			{
				final Room R=r.nextElement();
				boolean own = false;
				for(final Enumeration<Behavior> e=R.behaviors();e.hasMoreElements();)
				{
					final Behavior B=e.nextElement();
					if((B!=null)&&(B.ID().equals(ID())))
						own = true;
				}
				if(all || ((!own) && (!this.isRoomBeingCamped(R)) && (skipRooms==null||!CMLib.masking().maskCheck(skipRooms,R,true))))
					list.add(R);
			}
		}
		else
		{
			final Room R = (ticking instanceof Room) ? (Room)ticking : super.getBehaversRoom(ticking);
			if((R != null) && (all || ((!this.isRoomBeingCamped(R))&&(skipRooms==null||!CMLib.masking().maskCheck(skipRooms,R,true)))))
				list.add(R);
		}
		return list;
	}

	private static String key(final Environmental E)
	{
		return ((E instanceof MOB) ? "M:" : "I:") + E.Name();
	}

	private void count(final Environmental E, final Map<String, int[]> counts)
	{
		if((E != null) && (!E.amDestroyed()) && CMLib.masking().maskCheck(exceptMask, E, true))
		{
			final int[] n = counts.get(key(E));
			if(n == null)
				counts.put(key(E), new int[] { 1 });
			else
				n[0]++;
		}
	}

	/**
	 * W9: the EXCEPT mobs and items there are now, by name: those in the
	 * rooms, and carried by their mobs (not by players).
	 * @param rooms the rooms
	 * @return the number of each
	 */
	protected Map<String, int[]> excepted(final List<Room> rooms)
	{
		final Map<String, int[]> counts = new HashMap<String, int[]>();
		if(exceptMask == null)
			return counts;
		for(final Room R : rooms)
		{
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M == null) || M.isPlayer())
					continue;
				count(M, counts);
				for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
					count(i.nextElement(), counts);
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
				count(i.nextElement(), counts);
		}
		return counts;
	}

	private boolean keeps(final Environmental E, final Map<String, int[]> before)
	{
		if((E == null) || E.amDestroyed() || (!CMLib.masking().maskCheck(exceptMask, E, true)))
			return true;
		final int[] n = before.get(key(E));
		if((n != null) && (n[0] > 0))
		{
			n[0]--;
			return true;
		}
		return false;
	}

	/**
	 * W9: after a reset, takes away each EXCEPT mob or item beyond the
	 * number there were before, those in rooms not reset counted first.
	 * @param rooms every room, those reset last
	 * @param before the numbers before, from {@link #excepted(List)}
	 * @return how many it took away
	 */
	protected int removeReturned(final List<Room> rooms, final Map<String, int[]> before)
	{
		if(exceptMask == null)
			return 0;
		final List<Environmental> gone = new ArrayList<Environmental>();
		for(final Room R : rooms)
		{
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M == null) || M.isPlayer())
					continue;
				if(!keeps(M, before))
				{
					gone.add(M);
					continue;
				}
				for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
				{
					final Item I = i.nextElement();
					if(!keeps(I, before))
						gone.add(I);
				}
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				if(!keeps(I, before))
					gone.add(I);
			}
		}
		for(final Environmental E : gone)
			E.destroy();
		return gone.size();
	}

	/**
	 * Resets the rooms now, keeping EXCEPT's gone ones gone.
	 * @param ticking the host
	 * @return the number of rooms reset
	 */
	protected int resetNow(final Tickable ticking)
	{
		return resetNow(ticking,null);
	}

	protected int resetNow(final Tickable ticking, final MOB leader)
	{
		final List<Room> reset = rooms(ticking, false);
		final List<Room> all = rooms(ticking, true);
		final Map<String, int[]> before = excepted(all);
		for(final Room R : reset)
		{
			resetDone = true;
			CMLib.map().resetRoom(R, true);
			Ability scaling=R.fetchEffect("InstanceArea");
			if(scaling==null) scaling=R.getArea().fetchEffect("InstanceArea");
			if(scaling instanceof Travel.InPlaceScaling)
				((Travel.InPlaceScaling)scaling).rebuildInPlace(R,leader);
		}
		if(reset.size() > 0)
		{
			final List<Room> order = new ArrayList<Room>();
			for(final Room R : all)
			{
				if(!reset.contains(R))
					order.add(R);
			}
			order.addAll(reset);
			removeReturned(order, before);
			if(resetMsg.length() > 0)
				msgPending = true;
		}
		return reset.size();
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		super.tick(ticking,tickID);
		if(onReentry) return true;
		if(lastAccess<0)
			return true;

		if((lastAccess+period(ticking))<GameTime.now())
		{
			if(this.rlHour>=0)
			{
				lastAccess = GameTime.now() + TimeManager.MILI_HOUR;
				final Calendar C=Calendar.getInstance();
				if(C.get(Calendar.HOUR_OF_DAY) != rlHour)
				{
					resetDone = false;
					lastAccess = GameTime.now() + (TimeManager.MILI_HOUR/5);
					return true;
				}
				else
				if(resetDone)
					return true;
			}
			if(this.mudHour>=0)
			{
				Physical E;
				if(ticking instanceof Physical)
					E=(Physical)ticking;
				else
					E=super.getBehaversRoom(ticking);
				lastAccess = GameTime.now() + CMProps.getMillisPerMudHour();
				if(E==null)
					return true;
				final TimeClock C=CMLib.time().localClock(E);
				if(C==null)
					return true;
				if(C.getHourOfDay() != mudHour)
				{
					resetDone = false;
					lastAccess = GameTime.now() + (CMProps.getMillisPerMudHour()/5);
					return true;
				}
				else
				if(resetDone)
					return true;
			}
			resetNow(ticking);
			if(time > 0)
				lastAccess=GameTime.now();
		}
		return true;
	}

	/** W9: the game time since a player was last there, a message waiting, and a reset done this hour, for a save */
	@Override
	protected String getRunState()
	{
		final boolean done = resetDone && ((rlHour >= 0) || (mudHour >= 0));
		if((lastAccess < 0) && (!msgPending) && (!done))
			return "";
		return "<RESETWHOLE" + ((lastAccess < 0) ? "" : (" AGO=\"" + (GameTime.now() - lastAccess) + "\""))
			+ (msgPending ? " MSG=\"TRUE\"" : "") + (done ? " DONE=\"TRUE\"" : "") + (away?" AWAY=\"TRUE\"":"") + " />";
	}

	@Override
	protected void setRunState(final String xml)
	{
		for(final XMLLibrary.XMLTag tag : CMLib.xml().parseAllXML(xml))
		{
			if(tag.tag().equalsIgnoreCase("RESETWHOLE"))
			{
				final String ago = tag.getParmValue("AGO");
				lastAccess = ((ago == null) || (ago.length() == 0)) ? -1 : (GameTime.now() - CMath.s_long(ago));
				msgPending = "TRUE".equalsIgnoreCase(tag.getParmValue("MSG"));
				resetDone = "TRUE".equalsIgnoreCase(tag.getParmValue("DONE"));
				away="TRUE".equalsIgnoreCase(tag.getParmValue("AWAY"));
			}
		}
	}
}
