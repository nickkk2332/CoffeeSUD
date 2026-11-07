package com.planet_ink.coffee_mud.core;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.util.*;
import java.util.regex.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
/** Shared detail and sensory dispatch. No scenery objects are manufactured. */
public final class Scenery
{
	private Scenery()
	{
	}
	public static List<String> statements(final String text)
	{
		return AuthoringOptions.statements(text);
	}
	public static String option(final String text,final String key,final String fallback)
	{
		return AuthoringOptions.option(text,key,fallback);
	}
	public static boolean flag(final String text,final String key)
	{
		return AuthoringOptions.flag(text,key);
	}
	/** Every detail's options are independent; validation and dispatch use the same record boundaries. */
	public static List<String> detailBodies(final String text)
	{
		final List<String> out=new ArrayList<String>();
		for(final String record:statements(text))
		{
			final int at=record.indexOf('=');
			if(at>0)
				out.add(record.substring(at+1).trim());
		}
		return out;
	}
	public static String clean(final String text)
	{
		return AuthoringOptions.without(text,new HashSet<String>(Arrays.asList("READ","SETVAR","TATTOO","MASK","ID","ALIASES","LEGACYKEYS")),
			new HashSet<String>(Arrays.asList("EXAMINEONLY","HIGHLIGHT")));
	}
	public static void clue(final MOB who,final Physical host,final String params,final String kind)
	{
		final String id=host instanceof Room?CMLib.map().getExtendedRoomID((Room)host)
			:CMLib.map().getExtendedRoomID(CMLib.map().roomLocation(host))+":"+host.ID()+":"+host.Name();
		final String legacy=StoryRecords.token("SCENERY_",id+":"+kind+":"+params);
		final String scope=Arrays.asList("SOUND","TOUCH","TASTE").contains(kind)?"SENSES":"DETAILS";
		final String part=scope.equals("DETAILS")&&!option(params,"ID","").isEmpty()?"":kind;
		if(AuthorIds.recorded(who,"SCENERY_",host,scope,params,part,legacy))
			return;
		who.addTattoo(AuthorIds.key("SCENERY_",host,scope,params,part,true));
		final String tattoo=option(params,"TATTOO","");
		if(!tattoo.isEmpty())
			who.addTattoo(tattoo);
		StoryText.setVariable(who,option(params,"SETVAR",""));
	}
	public static Environmental target(final MOB who,final String name)
	{
		if(who.location()==null)
			return null;
		if(name.isEmpty()||name.equalsIgnoreCase("HERE")||name.equalsIgnoreCase("ROOM"))
			return who.location();
		final int dir=CMLib.directions().getGoodDirectionCode(name);
		if(dir>=0)
			return who.location().getExitInDir(dir);
		Environmental e=who.location().fetchFromMOBRoomFavorsItems(who,null,name,Wearable.FILTER_ANY);
		if(e==null)
			e=who.location().findItem(name);
		if(e==null)
			e=who.location().fetchInhabitant(name);
		return e;
	}
	private static List<Physical> hosts(final MOB who)
	{
		final List<Physical> out=new ArrayList<Physical>();
		final Room r=who.location();
		if(r==null)
			return out;
		out.add(r);
		for(final Enumeration<Item> e=r.items();e.hasMoreElements();)
			out.add(e.nextElement());
		for(final Enumeration<MOB> e=r.inhabitants();e.hasMoreElements();)
			out.add(e.nextElement());
		for(final Enumeration<Item> e=who.items();e.hasMoreElements();)
			out.add(e.nextElement());
		for(int d=0;d<Directions.NUM_DIRECTIONS();d++)
			if(r.getExitInDir(d)!=null)
				out.add(r.getExitInDir(d));
		return out;
	}
	private static boolean accessible(final MOB who,final Physical host)
	{
		if(!ViewRules.allowed(host,who)||!CMLib.flags().canBeSeenBy(host,who))
			return false;
		if(host instanceof Item)
		{
			Container parent=((Item)host).container();
			int depth=0;
			while(parent!=null&&depth++<32)
			{
				if(parent.hasADoor()&&!parent.isOpen()||!CMLib.flags().canBeSeenBy(parent,who))
					return false;
				parent=parent.container();
			}
			if(parent!=null)
				return false;
		}
		return true;
	}
	public static boolean detail(final MOB who,final String query,final String verb)
	{
		String keyword=query.replaceFirst("(?i)^(?:AT|THE)\\s+","");
		List<Physical> hosts=hosts(who);
		final Matcher qualified=Pattern.compile("(?i)^(.+?)\\s+ON\\s+(.+)$").matcher(keyword);
		if(qualified.matches())
		{
			final Environmental e=target(who,qualified.group(2));
			if(!(e instanceof Physical))
				return false;
			hosts=Collections.singletonList((Physical)e);
			keyword=qualified.group(1);
		}
		for(final Physical host:hosts)
		{
			final Ability a=host.fetchEffect("Prop_Details");
			if(a==null||!accessible(who,host))
				continue;
			for(final String record:statements(a.text()))
			{
				final int at=record.indexOf('=');
				if(at<1)
					continue;
				final String body=record.substring(at+1).trim();
				boolean match=false;
				for(final String k:record.substring(0,at).split(","))
					if(k.trim().equalsIgnoreCase(keyword))
						match=true;
				if(!match||flag(body,"EXAMINEONLY")&&!verb.equals("EXAMINE"))
					continue;
				final String mask=option(body,"MASK","");
				if(!mask.isEmpty()&&!CMLib.masking().maskCheck(mask,who,true))
					continue;
				final String text=verb.equals("READ")?option(body,"READ",""):clean(body);
				if(text.isEmpty())
					continue;
				who.tell(StoryText.render(who,host,text));
				clue(who,host,body,record.substring(0,at).trim().toUpperCase());
				if(host instanceof PhysicalAgent)
					ScriptEvents.detail((PhysicalAgent)host,who,keyword);
				Ambience.activity(who);
				return true;
			}
		}
		return false;
	}
	public static String highlight(final MOB who,final Room room,final String text)
	{
		final Ability a=room.fetchEffect("Prop_Details");
		if(a==null)
			return text;
		String out=text;
		for(final String record:statements(a.text()))
		{
			final int at=record.indexOf('=');
			if(at<1)
				continue;
			final String body=record.substring(at+1);
			if(!flag(body,"HIGHLIGHT"))
				continue;
			final String mask=option(body,"MASK","");
			if(!mask.isEmpty()&&!CMLib.masking().maskCheck(mask,who,true))
				continue;
			for(final String k:record.substring(0,at).split(","))
				out=out.replaceAll("(?i)(?<![\\w^])("+Pattern.quote(k.trim())+")(?!\\w)","^H$1^N");
		}
		return out;
	}
	public static boolean sense(final MOB who,final Environmental target,final String sense)
	{
		if(!(target instanceof Physical)||!ViewRules.allowed(target,who)||sense.equals("SOUND")&&!CMLib.flags().canHear(who))
			return false;
		final Ability a=((Physical)target).fetchEffect("Prop_Senses");
		if(a!=null)
		{
			final String raw=option(a.text(),sense,"");
			if(!raw.isEmpty())
			{
				if(CMLib.dice().rollPercentage()>AuthoringOptions.integer(a.text(),"CHANCE",100))
					return true;
				final String[] choices=raw.split("\\|");
				final String rendered=StoryText.render(who,target,choices[CMLib.dice().roll(1,choices.length,-1)]);
				who.tell(rendered);
				clue(who,(Physical)target,a.text(),sense);
				Ambience.activity(who);
				return true;
			}
		}
		if(sense.equals("SOUND")&&target instanceof PhysicalAgent)
			for(final Enumeration<Behavior> e=((PhysicalAgent)target).behaviors();e.hasMoreElements();)
			{
				final Behavior b=e.nextElement();
				if(b instanceof Ambience.Source&&((Ambience.Source)b).listen(who,(PhysicalAgent)target))
					return true;
			}
		if(sense.equals("SOUND")&&target instanceof Room)
			for(final Enumeration<Behavior> e=((Room)target).getArea().behaviors();e.hasMoreElements();)
			{
				final Behavior b=e.nextElement();
				if(b instanceof Ambience.Source&&((Ambience.Source)b).listen(who,((Room)target).getArea()))
					return true;
			}
		return false;
	}
}
