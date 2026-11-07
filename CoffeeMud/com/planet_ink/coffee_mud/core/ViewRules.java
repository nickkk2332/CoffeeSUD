package com.planet_ink.coffee_mud.core;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.util.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** Viewer-dependent visibility without changing global dispositions. */
public final class ViewRules
{
	private ViewRules()
	{
	}
	private static final ThreadLocal<Set<Environmental>> checking=ThreadLocal
		.withInitial(()->Collections.newSetFromMap(new IdentityHashMap<Environmental,Boolean>()));
	private static String token(final Environmental host,final Ability a)
	{
		return StoryRecords.token("FOUND_",CMLib.map().getExtendedRoomID(CMLib.map().roomLocation(host))+":"+host.ID()+":"+host.Name()+":"
			+Scenery.option(a.text(),"ID",a.text()));
	}
	public static boolean allowed(final Environmental host,final MOB viewer)
	{
		if(!(host instanceof Physical))
			return true;
		final Ability a=((Physical)host).fetchEffect("Prop_ShowWhen");
		if(a==null)
			return true;
		if(viewer!=null&&viewer.isAttributeSet(MOB.Attrib.SYSOPMSGS))
			return true;
		if(viewer!=null&&Scenery.option(a.text(),"HIDEMODE","UNSEEN").equalsIgnoreCase("HIDDEN")
			&&AuthorIds.recorded(viewer,"FOUND_",(Physical)host,"SHOWWHEN",a.text(),"",token(host,a)))
			return true;
		if(!checking.get().add(host))
			return false;
		try
		{
			final String check=Scenery.option(a.text(),"CHECK","VIEWER");
			final Room room=CMLib.map().roomLocation(host);
			if(check.equalsIgnoreCase("HOST"))
				return test(host instanceof MOB?(MOB)host:viewer,host,a.text(),host);
			if(check.equalsIgnoreCase("ROOMPLAYERS")&&room!=null)
			{
				for(final Enumeration<MOB> e=room.inhabitants();e.hasMoreElements();)
				{
					final MOB m=e.nextElement();
					if(m.isPlayer()&&test(m,host,a.text()))
						return true;
				}
				return false;
			}
			if(check.equalsIgnoreCase("ANYPLAYER"))
			{
				for(final Enumeration<MOB> e=CMLib.players().players();e.hasMoreElements();)
				{
					final MOB m=e.nextElement();
					if(m.isPlayer()&&test(m,host,a.text()))
						return true;
				}
				return false;
			}
			return test(viewer,host,a.text());
		}
		finally
		{
			checking.get().remove(host);
		}
	}
	private static boolean test(final MOB viewer,final Environmental host,final String params)
	{
		return test(viewer,host,params,viewer);
	}
	private static boolean test(final MOB viewer,final Environmental host,final String params,final Environmental subject)
	{
		if(subject==null)
			return false;
		final String mask=Scenery.option(params,"MASK","");
		if(!mask.isEmpty()&&!CMLib.masking().maskCheck(mask,subject,true))
			return false;
		final String when=Scenery.option(params,"WHEN","");
		if(when.isEmpty())
			return true;
		final int at=when.indexOf(' ');
		final String name=(at<0?when:when.substring(0,at)).toUpperCase();
		return StoryText.known(name)?StoryText.test(viewer,host,null,CMLib.map().roomLocation(host),name,at<0?"":when.substring(at+1))
			:CMLib.masking().maskCheck(when,viewer,true);
	}
	public static void discover(final MOB who,final Physical host)
	{
		if(host==null)
			return;
		final Ability a=host.fetchEffect("Prop_ShowWhen");
		if(a!=null&&Scenery.option(a.text(),"HIDEMODE","UNSEEN").equalsIgnoreCase("HIDDEN")&&!allowed(host,who))
		{
			who.addTattoo(AuthorIds.key("FOUND_",(Physical)host,"SHOWWHEN",a.text(),"",true));
			who.tell("You discover "+host.name(who)+".");
		}
	}
	public static void search(final MOB who)
	{
		final Room r=who.location();
		if(r==null)
			return;
		for(final Enumeration<com.planet_ink.coffee_mud.Items.interfaces.Item> e=r.items();e.hasMoreElements();)
			discover(who,e.nextElement());
		for(final Enumeration<MOB> e=r.inhabitants();e.hasMoreElements();)
			discover(who,e.nextElement());
		for(int d=0;d<Directions.NUM_DIRECTIONS();d++)
			discover(who,r.getExitInDir(d));
	}
}
