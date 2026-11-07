package com.planet_ink.coffee_mud.Commands;
import java.util.List;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.core.*;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
public class ModCraft extends StdCommand
{
	private final String[] access=I(new String[]{"MODCRAFT"});
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
			final String sub=words.size()>1?words.get(1).toUpperCase(java.util.Locale.ROOT):"HELP";
			if(sub.equals("STATUS"))
				mob.tell(ItemCrafting.status(mob));
			else if(sub.equals("LIST"))
				mob.tell(ItemCrafting.operations());
			else if(sub.equals("POOL")&&words.size()==3)
			{
				final Item item=mob.findItem(null,words.get(2));
				if(item==null)
					throw new java.io.IOException("no such carried item; quote a multiword name");
				mob.tell(ItemCrafting.pool(item));
			}
			else if((sub.equals("COMMIT")||sub.equals("CANCEL")||sub.equals("RECEIPT"))&&words.size()<=3)
			{
				long id=0;
				if(words.size()==3)
				{
					try
					{
						id=Long.parseLong(words.get(2));
					}
					catch(final NumberFormatException e)
					{
						throw new java.io.IOException("request number must be a positive integer");
					}
					if(id<=0)
						throw new java.io.IOException("request number must be positive");
				}
				mob.tell(sub.equals("COMMIT")?ItemCrafting.commit(mob,id):sub.equals("CANCEL")?ItemCrafting.cancel(mob,id)
					:ItemCrafting.receipt(mob,id));
			}
			else if(sub.equals("BEGIN")&&words.size()==3)
				mob.tell(ItemCrafting.begin(mob,words.get(2).toLowerCase(java.util.Locale.ROOT)));
			else if((sub.equals("PREVIEW")||sub.equals("PREPARE"))&&words.size()>=4)
			{
				// the selector: a modifier number, an affix ID, "number affix" for replace, nothing for upgrade, imbue, reforge and recipe stages; then WITH and catalysts (I06)
				final Item item=mob.findItem(null,words.get(3));
				if(item==null)
					throw new java.io.IOException("no such carried item; quote a multiword name");
				final String input=words.size()>4?CMParms.combine(words,4):"";
				try
				{
					mob.tell(sub.equals("PREVIEW")?ItemCrafting.preview(mob,words.get(2).toLowerCase(java.util.Locale.ROOT),item,input)
						:ItemCrafting.prepare(mob,words.get(2).toLowerCase(java.util.Locale.ROOT),item,input));
				}
				catch(final java.io.IOException e)
				{
					throw new java.io.IOException(e.getMessage()+". Nothing was spent or drawn.");
				}
			}
			else
				mob.tell("MODCRAFT LIST / STATUS / POOL \"[item name]\" / PREVIEW or PREPARE [operation ID] \"[item name]\" [modifier number, affix ID, or for replace both] (WITH [catalyst IDs] for imbue, reforge and a recipe's last stage); BEGIN [recipe ID]; PREVIEW or PREPARE [recipe ID] \"[work piece]\"; COMMIT/CANCEL [request number]; RECEIPT [request number]. ITEMMODS lists modifier numbers. Preview/preparation are free; only commit rolls and spends materials.");
		}
		catch(final java.io.IOException e)
		{
			mob.tell("Modifier crafting: "+e.getMessage());
		}
		return false;
	}
}
