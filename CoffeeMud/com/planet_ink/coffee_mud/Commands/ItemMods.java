package com.planet_ink.coffee_mud.Commands;
import java.util.List;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.core.*;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
public class ItemMods extends StdCommand
{
	private final String[] access=I(new String[]{"ITEMMODS"});
	@Override public String[] getAccessWords()
	{
		return access;
	}
	@Override public boolean canBeOrdered()
	{
		return false;
	}
	@Override public boolean securityCheck(final MOB mob)
	{
		return CMProps.isSinglePlayer();
	}
	@Override public double actionsCost(final MOB mob,final List<String> words)
	{
		return 0;
	}
	@Override public double combatActionsCost(final MOB mob,final List<String> words)
	{
		return 0;
	}
	@Override public boolean execute(final MOB mob,final List<String> words,final int flags)throws java.io.IOException
	{
		try
		{
			final Item item=words.size()<2?null:mob.findItem(null,CMParms.combine(words,1));
			if(item==null)
				mob.tell("ITEMMODS [carried item] lists its saved modifiers, source pins and protection.");
			else
				mob.tell(ItemModifiers.inspect(item));
		}
		catch(final java.io.IOException e)
		{
			mob.tell("Item modifiers: "+e.getMessage());
		}
		return false;
	}
}
