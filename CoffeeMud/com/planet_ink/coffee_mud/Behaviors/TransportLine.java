package com.planet_ink.coffee_mud.Behaviors;

import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Saved, scheduled routes for vessels, rideables and drivers. */
public class TransportLine extends StdBehavior implements Travel.Line
{
	@Override public String ID()
	{
		return "TransportLine";
	}
	@Override protected int canImproveCode()
	{
		return CAN_ITEMS|CAN_MOBS;
	}
	private List<String> stops=new ArrayList<String>();
	private int stop=0, next=1, direction=1;
	private boolean moving=false, initialized=false;
	private long departure=0, departureHour=-1, arrivals=0, travelReadyAt=0;
	private transient PhysicalAgent host;
	private transient Ability fareProperty, helmProperty;
	private transient Environmental equipped;
	@Override public void setParms(final String text)
	{
		super.setParms(text);
		stops=CMParms.parseSemicolons(Travel.option(text,"STOPS",""),true);
		stop=0;
		next=1;
		direction=1;
		moving=false;
		initialized=false;
		departure=0;
		departureHour=-1;
		arrivals=0;
		travelReadyAt=0;
	}
	@Override public void startBehavior(final PhysicalAgent on)
	{
		super.startBehavior(on);
		host=on;
		install(on);
	}
	private Environmental vehicle()
	{
		if(host instanceof MOB&&((MOB)host).riding()!=null)
			return ((MOB)host).riding();
		return host;
	}
	private void install(final PhysicalAgent on)
	{
		host=on;
		uninstall();
		final Environmental V=vehicle();
		if(!(V instanceof Physical))
			return;
		equipped=V;
		final Physical P=(Physical)V;
		if(CMParms.getParmBool(getParms(),"PASSENGERONLY",true)&&V instanceof NavigableItem&&P.fetchEffect("Prop_HelmRestrict")==null)
		{
			helmProperty=CMClass.getAbility("Prop_HelmRestrict");
			helmProperty.setSavable(false);
			helmProperty.setMiscText("NAVMASK=\"-PLAYERS\"");
			P.addNonUninvokableEffect(helmProperty);
		}
		if(P.fetchEffect("Prop_TicketTaker")==null)
		{
			fareProperty=CMClass.getAbility("Prop_TicketTaker");
			fareProperty.setSavable(false);
			P.addNonUninvokableEffect(fareProperty);
		}
		refreshFare();
	}
	private void uninstall()
	{
		if(fareProperty!=null&&fareProperty.affecting()!=null)
			fareProperty.affecting().delEffect(fareProperty);
		if(helmProperty!=null&&helmProperty.affecting()!=null)
			helmProperty.affecting().delEffect(helmProperty);
		fareProperty=null;
		helmProperty=null;
		equipped=null;
	}
	@Override public void endBehavior(final PhysicalAgent on)
	{
		uninstall();
		super.endBehavior(on);
	}
	private void refreshFare()
	{
		if(fareProperty==null)
			return;
		double price=CMParms.getParmDouble(getParms(),"FARE",0);
		for(final String part : CMParms.parseCommas(Travel.option(getParms(),"FARES",""),true))
		{
			final int colon=part.lastIndexOf(':');
			if(colon>0&&stop<stops.size()&&part.substring(0,colon).equalsIgnoreCase(stops.get(stop)))
				price=CMath.s_double(part.substring(colon+1));
		}
		fareProperty.setMiscText("FARE="+Math.max(0,price)+" PARTY="+Travel.option(getParms(),"PARTY","LEADER")
			+" PETS="+Travel.option(getParms(),"PETS","0")+" CARGO="+Travel.option(getParms(),"CARGO","0"));
	}
	private void schedule(final Room R)
	{
		departure=GameTime.now()+Math.max(1,CMParms.getParmInt(getParms(),"WAIT",10))*CMProps.getTickMillis();
		departureHour=-1;
		final List<String> hours=CMParms.parseCommas(Travel.option(getParms(),"DEPART",""),true);
		if(hours.isEmpty())
			return;
		final TimeClock C=CMLib.time().localClock(R);
		final long now=ScriptEvents.clockHours(C), day=Math.max(1,C.getHoursInDay());
		long earliest=Long.MAX_VALUE;
		for(final String value : hours)
		{
			if(!CMath.isInteger(value))
				continue;
			final int h=CMath.s_int(value);
			if(h<0||h>=day)
				continue;
			long due=now-(now%day)+h;
			if(due<=now)
				due+=day;
			earliest=Math.min(earliest,due);
		}
		if(earliest!=Long.MAX_VALUE)
			departureHour=earliest;
	}
	public long arrivals()
	{
		return arrivals;
	}
	public boolean isMoving()
	{
		return moving;
	}
	public String timetable()
	{
		final Room R=host==null?null:CMLib.map().roomLocation(vehicle());
		if(stops.size()<2)
			return "Transport line needs at least two stops.";
		return (host==null?"Transport line":host.Name())+": "
			+(moving?"travelling to "+Travel.label(stops.get(Math.min(next,stops.size()-1)))
			:"at "+Travel.label(stops.get(Math.min(stop,stops.size()-1))))
			+"; next "+Travel.label(stops.get(Math.min(next,stops.size()-1)))
			+(moving?"":departureHour>=0?", departure hour "+(departureHour%Math.max(1,CMLib.time().localClock(R).getHoursInDay()))
			:", departs in "+Math.max(0,(departure-GameTime.now()+CMProps.getTickMillis()-1)/CMProps.getTickMillis())+" ticks");
	}
	private void announce(final String key, final Room outside)
	{
		final String line=Travel.option(getParms(),key,"").replace("$stop",Travel.label(stops.get(stop)))
			.replace("$next",Travel.label(stops.get(next)));
		if(line.length()==0)
			return;
		outside.showHappens(CMMsg.MSG_OK_VISUAL,line);
		if(vehicle() instanceof Boardable)
			for(final Enumeration<Room> e=((Boardable)vehicle()).getArea().getProperMap();e.hasMoreElements();)
				e.nextElement().showHappens(CMMsg.MSG_OK_VISUAL,line);
	}
	private void signs()
	{
		final String name=Travel.option(getParms(),"SIGN","");
		if(name.length()==0)
			return;
		for(final String id : stops)
		{
			final Room R=Travel.destination(id,null);
			if(R==null)
				continue;
			final Item sign=R.findItem(null,name);
			if(sign!=null)
				sign.setReadableText(timetable());
		}
	}
	@Override public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking,tickID))
			return false;
		if(!(ticking instanceof PhysicalAgent)||stops.size()<2)
			return true;
		host=(PhysicalAgent)ticking;
		if(vehicle()!=equipped)
			install(host);
		final Environmental V=vehicle();
		final Room at=CMLib.map().roomLocation(V);
		if(at==null)
			return true;
		if(GameTime.now()<travelReadyAt||(V instanceof Travel.PacedVehicle&&!((Travel.PacedVehicle)V).travelReady()))
		{
			signs();
			return true;
		}
		if(!initialized)
		{
			for(int i=0;i<stops.size();i++)
				if(Travel.destination(stops.get(i),null)==at)
				{
					stop=i;
					break;
				}
			next=stop+1<stops.size()?stop+1:0;
			if(Travel.option(getParms(),"ROUND","LOOP").equalsIgnoreCase("BACK")&&stop==stops.size()-1)
			{
				direction=-1;
				next=stop-1;
			}
			initialized=true;
			schedule(at);
			refreshFare();
			announce("BOARDCALL",at);
		}
		stop=Math.max(0,Math.min(stop,stops.size()-1));
		next=Math.max(0,Math.min(next,stops.size()-1));
		final Room target=Travel.destination(stops.get(next),null);
		if(target==null)
			return true;
		if(moving&&at==target)
		{
			moving=false;
			stop=next;
			arrivals++;
			if(V instanceof NavigableItem)
			{
				((NavigableItem)V).setAnchorDown(true);
				((NavigableItem)V).setCurrentCourse(new ArrayList<Integer>());
			}
			if(Travel.option(getParms(),"ROUND","LOOP").equalsIgnoreCase("BACK"))
			{
				if(stop==0)
					direction=1;
				if(stop==stops.size()-1)
					direction=-1;
				next=stop+direction;
			}
			else
				next=(stop+1)%stops.size();
			announce("ARRIVEMSG",at);
			schedule(at);
			refreshFare();
			announce("BOARDCALL",at);
		}
		else if(!moving)
		{
			final boolean due=departureHour>=0?ScriptEvents.clockHours(CMLib.time().localClock(at))>=departureHour:GameTime.now()>=departure;
			if(due)
			{
				final List<Integer> path=Travel.route(at,target,V instanceof Rideable?(Rideable)V:null);
				if(path==null||path.isEmpty())
				{
					schedule(at);
					signs();
					return true;
				}
				if(V instanceof NavigableItem)
				{
					path.add(Integer.valueOf(-1));
					((NavigableItem)V).setCurrentCourse(path);
					((NavigableItem)V).setAnchorDown(false);
				}
				moving=true;
				announce("DEPARTMSG",at);
			}
		}
		else if(!(V instanceof NavigableItem))
		{
			final List<Integer> path=Travel.route(at,target,V instanceof Rideable?(Rideable)V:null);
			if(path==null||path.isEmpty())
			{
				moving=false;
				schedule(at);
			}
			else
			{
				final int direction=path.get(0).intValue();
				final com.planet_ink.coffee_mud.Exits.interfaces.Exit exit=at.getExitInDir(direction);
				final Room to=at.getRoomInDir(direction);
				final long cost=Travel.stepMillis(V,at,to,exit);
				final boolean moved=V instanceof MOB?CMLib.tracking().walk((MOB)V,direction,false,false)
					:V instanceof Item&&CMLib.tracking().walk((Item)V,direction);
				if(moved)
					travelReadyAt=GameTime.now()+cost;
			}
		}
		else if(((NavigableItem)V).getCurrentCourse().isEmpty())
		{
			moving=false;
			schedule(at);
		}
		signs();
		return true;
	}
	@Override protected String getRunState()
	{
		return "<LINE STOP=\""+stop+"\" NEXT=\""+next+"\" DIR=\""+direction+"\" MOVING=\""+moving+"\" INIT=\""+initialized
			+"\" TRAVELWAIT=\""+Math.max(0,travelReadyAt-GameTime.now())+"\" WAIT=\""+Math.max(0,departure-GameTime.now())+"\" HOUR=\""+departureHour
			+"\" ARRIVALS=\""+arrivals+"\" />";
	}
	@Override protected void setRunState(final String xml)
	{
		for(final XMLTag t : CMLib.xml().parseAllXML(xml))
			if(t.tag().equals("LINE"))
			{
				stop=CMath.s_int(t.getParmValue("STOP"));
				next=CMath.s_int(t.getParmValue("NEXT"));
				direction=CMath.s_int(t.getParmValue("DIR"));
				moving=CMath.s_bool(t.getParmValue("MOVING"));
				initialized=CMath.s_bool(t.getParmValue("INIT"));
				travelReadyAt=GameTime.now()+Math.max(0,CMath.s_long(t.getParmValue("TRAVELWAIT")));
				departure=GameTime.now()+CMath.s_long(t.getParmValue("WAIT"));
				departureHour=CMath.s_long(t.getParmValue("HOUR"));
				arrivals=CMath.s_long(t.getParmValue("ARRIVALS"));
			}
	}
	@Override public CMObject copyOf()
	{
		final TransportLine B=(TransportLine)super.copyOf();
		B.stops=new ArrayList<String>(stops);
		B.host=null;
		B.fareProperty=null;
		B.helmProperty=null;
		B.equipped=null;
		return B;
	}
}
