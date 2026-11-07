package com.planet_ink.coffee_mud.core;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Persistent instance references. Definition IDs and explicit string stores have separate semantics. */
public final class InstanceIds
{
	public static final String STAT = "INSTANCEID";
	public static final String PREFIX = "@instance:";
	private static final Map<String,WeakReference<Physical>> index = new ConcurrentHashMap<String,WeakReference<Physical>>();
	private InstanceIds()
	{
	}

	public static String existing(final Physical host)
	{
		final Ability data = host == null ? null : host.fetchEffect("ExtraData");
		return data != null && data.isStat(STAT) ? data.getStat(STAT) : "";
	}

	public static String id(final Physical host)
	{
		if(host == null)
			return "";
		synchronized(host)
		{
			Ability data = host.fetchEffect("ExtraData");
			if(data == null)
			{
				data = CMClass.getAbility("ExtraData");
				host.addNonUninvokableEffect(data);
			}
			String id = existing(host);
			final WeakReference<Physical> registered = index.get(id);
			final Physical prior = registered == null ? null : registered.get();
			if(id.isEmpty() || prior != null && prior != host && !prior.amDestroyed() && existing(prior).equals(id))
			{
				id = UUID.randomUUID().toString();
				data.setStat(STAT, id);
			}
			index.put(id, new WeakReference<Physical>(host));
			return id;
		}
	}

	public static String reference(final Environmental host)
	{
		if(host == null)
			return "";
		if(host instanceof Room)
			return "@room:" + CMLib.map().getExtendedRoomID((Room)host);
		if(host instanceof Area)
			return "@area:" + host.Name();
		if(host instanceof Physical)
			return PREFIX + id((Physical)host);
		return host.Name();
	}

	public static void restore(final Physical host, final String id)
	{
		if(id == null || id.isEmpty())
			return;
		Ability data=host.fetchEffect("ExtraData");
		if(data==null)
		{
			data=CMClass.getAbility("ExtraData");
			host.addNonUninvokableEffect(data);
		}
		data.setStat(STAT,id);
		index.put(id,new WeakReference<Physical>(host));
	}

	public static boolean isReference(final String value)
	{
		return value != null && (value.startsWith(PREFIX) || value.startsWith("@room:") || value.startsWith("@area:"));
	}

	/** Old snapshots had names only. Rebind only one exact candidate, never the first duplicate. */
	public static Environmental legacy(final Environmental host, final String name, final Class<?> type)
	{
		if(name==null||name.isEmpty())
			return null;
		final Set<Environmental> matches=Collections.newSetFromMap(new IdentityHashMap<Environmental,Boolean>());
		final Consumer<Environmental> consider=value->
		{
			if(value!=null&&type.isInstance(value)&&value.Name().equals(name))
				matches.add(value);
		};
		final Room room=CMLib.map().roomLocation(host);
		if(room!=null)
		{
			for(int i=0;i<room.numInhabitants();i++)
				consider.accept(room.fetchInhabitant(i));
			for(int i=0;i<room.numItems();i++)
				consider.accept(room.getItem(i));
		}
		if(host instanceof MOB)
			for(int i=0;i<((MOB)host).numItems();i++)
				consider.accept(((MOB)host).getItem(i));
		consider.accept(CMLib.players().getPlayer(name));
		consider.accept(host);
		if(matches.size()==1)
			return matches.iterator().next();
		if(matches.size()>1)
			Log.warnOut("InstanceIds","Ambiguous legacy "+type.getSimpleName()+" reference '"+name+"' was not rebound.");
		return null;
	}

	public static Environmental find(final String reference)
	{
		if(reference == null)
			return null;
		if(reference.startsWith("@room:"))
			return CMLib.map().getRoom(reference.substring(6));
		if(reference.startsWith("@area:"))
			return CMLib.map().getArea(reference.substring(6));
		if(!reference.startsWith(PREFIX))
			return null;
		final String id = reference.substring(PREFIX.length());
		final WeakReference<Physical> cached = index.get(id);
		final Physical old = cached == null ? null : cached.get();
		if(old != null && !old.amDestroyed() && existing(old).equals(id))
			return old;
		final Physical[] found = { null };
		visit(host ->
		{
			if(existing(host).equals(id))
				found[0] = host;
		});
		if(found[0] != null)
			index.put(id, new WeakReference<Physical>(found[0]));
		else
			index.remove(id);
		return found[0];
	}

	/** Visit each live object once, including inventories and other party windows. */
	public static void visit(final Consumer<Physical> visitor)
	{
		final Set<Physical> seen = Collections.newSetFromMap(new IdentityHashMap<Physical,Boolean>());
		final Consumer<Physical> once = host ->
		{
			if(host != null && !host.amDestroyed() && seen.add(host))
				visitor.accept(host);
		};
		for(final Enumeration<Room> rooms = CMLib.map().rooms(); rooms.hasMoreElements();)
		{
			final Room room = rooms.nextElement();
			if(room == null)
				continue;
			once.accept(room);
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
				once.accept(room.getRawExit(d));
			for(int i = 0; i < room.numItems(); i++)
				once.accept(room.getItem(i));
			for(int m = 0; m < room.numInhabitants(); m++)
			{
				final MOB mob = room.fetchInhabitant(m);
				once.accept(mob);
				if(mob != null)
					for(int i = 0; i < mob.numItems(); i++)
						once.accept(mob.getItem(i));
			}
		}
		for(final Enumeration<MOB> players = CMLib.players().players(); players.hasMoreElements();)
		{
			final MOB mob = players.nextElement();
			once.accept(mob);
			if(mob != null)
				for(int i = 0; i < mob.numItems(); i++)
					once.accept(mob.getItem(i));
		}
	}

	/** Assign IDs before any save record is emitted, so forward/backward references both survive. */
	public static void prepareSave()
	{
		visit(host ->
		{
			if(host instanceof MOB || host instanceof Item)
				id(host);
		});
	}
}
