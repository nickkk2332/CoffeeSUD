package com.planet_ink.coffee_mud.core;

import java.util.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Saved source identities, independent of names, locations and authored prose. */
public final class AuthorIds
{
	private AuthorIds()
	{
	}
	private static String identity(final Physical host, final String scope, final String params, final boolean create)
	{
		final String explicit=Scenery.option(params,"ID","");
		if(!explicit.isEmpty())
			return explicit;
		synchronized(host)
		{
			Ability ids=host.fetchEffect("Prop_AuthorIdentity");
			String id=ids==null?"":Scenery.option(ids.text(),scope,"");
			if(id.isEmpty())
			{
				// A room or area is the same source in every game: its identity comes from its ID or
				// name, and is not written onto it. A random one was new in each game (so ONCE=PROFILE
				// text and profile waypoints came back), and writing it changed the room's text, which
				// made the room look changed by play and kept author fixes out of restored games.
				final String stable=stableIdentity(host);
				if(stable!=null)
					return stable;
			}
			if(id.isEmpty()&&create)
			{
				if(ids==null)
				{
					ids=CMClass.getAbility("Prop_AuthorIdentity");
					host.addNonUninvokableEffect(ids);
				}
				id="AUTO:"+UUID.randomUUID().toString();
				ids.setMiscText(ids.text()+" "+scope+"="+id);
			}
			return id;
		}
	}
	/**
	 * The identity of a source that is the same in every game, or null: a room by its ID, an area by its
	 * name.  Things that move about keep a saved identity of their own instead.
	 * @param host the source
	 * @return the identity, or null
	 */
	private static String stableIdentity(final Physical host)
	{
		if(host instanceof com.planet_ink.coffee_mud.Locales.interfaces.Room)
		{
			final String roomID=CMLib.map().getExtendedRoomID((com.planet_ink.coffee_mud.Locales.interfaces.Room)host);
			return ((roomID==null)||roomID.isEmpty())?null:"AUTO:ROOM:"+roomID;
		}
		if(host instanceof com.planet_ink.coffee_mud.Areas.interfaces.Area)
			return "AUTO:AREA:"+host.Name();
		return null;
	}
	public static String key(final String prefix, final Physical host, final String scope, final String params, final String part, final boolean create)
	{
		final String id=identity(host,scope,params,create);
		return id.isEmpty()?null:StoryRecords.token(prefix,scope+":"+id+":"+part);
	}
	/** The first entry is canonical. Old complete hashes may be supplied when migrating shipped content. */
	public static List<String> keys(final String prefix, final Physical host, final String scope, final String params, final String part, final String legacy, final boolean create)
	{
		final List<String> keys=new ArrayList<String>();
		final String current=key(prefix,host,scope,params,part,create);
		if(current!=null)
			keys.add(current);
		if(legacy!=null)
			keys.add(legacy);
		for(final String alias:CMParms.parseCommas(Scenery.option(params,"ALIASES",""),true))
		{
			keys.add(StoryRecords.token(prefix,scope+":"+alias+":"+part));
			if(scope.equals("FIRSTENTRY"))
				keys.add(StoryRecords.token(prefix,alias));
			if(scope.equals("WAYPOINT"))
				keys.add(StoryRecords.token(prefix,part+"\n"+alias));
		}
		keys.addAll(CMParms.parseCommas(Scenery.option(params,"LEGACYKEYS",""),true));
		return keys;
	}
	public static boolean recorded(final Tattooable record, final String prefix, final Physical host, final String scope, final String params, final String part, final String legacy)
	{
		if(record==null)
			return false;
		for(final String token:keys(prefix,host,scope,params,part,legacy,false))
			if(record.findTattoo(token)!=null)
			{
				final String canonical=key(prefix,host,scope,params,part,true);
				if(record.findTattoo(canonical)==null)
					record.addTattoo(canonical);
				return true;
			}
		return false;
	}
}
