package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
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
   Copyright 2026 CoffeeSUD contributors

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
 * G6: a loot table on a mob or a container.  FILE= and ID= name an ITEM
 * tag in a template file (resources/randareas, or the world's own), rolled
 * ROLLS= times (math, 1?3), each a fresh roll with $mob_level, $area_level,
 * $theme and $player_level set; CHANCE= the percent chance it rolls at all;
 * GOLD= money (math) as well.  ON=DEATH (a mob: into its corpse as it
 * dies), OPEN (a container: the first time it is opened), SPAWN or RESET (as
 * the host comes into the world: a mob as it is brought to life, a
 * container as a player first comes into its room; again after its room
 * resets, which brings it back new).  A container rolls once until its
 * contents reset: ROLLED=TRUE in this property's text (getStat ROLLED),
 * which a save keeps; DEVPLAN item 1's container rule is to share it.
 * Catalog entries (class="catalog") keep the catalog's cap.
 */
public class Prop_LootTable extends Property
{
	@Override
	public String ID()
	{
		return "Prop_LootTable";
	}

	@Override
	public String name()
	{
		return "Loot Table";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS | Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return "Carries loot from " + file + " (" + id + ")";
	}

	protected String	file	= "randareas/loot.xml";
	protected String	id		= "";
	protected String	rolls	= "1";
	protected String	on		= "";
	protected int		chance	= 100;
	protected String	gold	= "";
	protected boolean	rolled	= false;
	/** G10: how many affixes each item gets (math), or "" */
	protected String	affixes	= "";
	/** a mob's roll for its death or this life, made once */
	protected boolean	lifeRolled	= false;

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		final Map<String, String> P = CMParms.parseEQParms(newMiscText);
		file = P.containsKey("FILE") ? P.get("FILE").trim() : "randareas/loot.xml";
		id = P.containsKey("ID") ? P.get("ID").trim() : "";
		rolls = P.containsKey("ROLLS") ? P.get("ROLLS").trim() : "1";
		on = P.containsKey("ON") ? P.get("ON").trim().toUpperCase() : "";
		chance = P.containsKey("CHANCE") ? CMath.s_int(P.get("CHANCE").replace('%', ' ').trim()) : 100;
		gold = P.containsKey("GOLD") ? P.get("GOLD").trim() : "";
		rolled = P.containsKey("ROLLED") && CMath.s_bool(P.get("ROLLED"));
		affixes = P.containsKey("AFFIXES") ? P.get("AFFIXES").trim() : "";
	}

	private String when()
	{
		if(on.length() > 0)
			return on.equals("RESET") ? "SPAWN" : on;
		return (affected instanceof MOB) ? "DEATH" : "OPEN";
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("ROLLED"))
			return rolled ? "TRUE" : "FALSE";
		return super.getStat(code);
	}

	@Override
	public void setStat(final String code, final String val)
	{
		if(code.equalsIgnoreCase("ROLLED"))
			markRolled(CMath.s_bool(val));
		else
			super.setStat(code, val);
	}

	/**
	 * A container rolled stays rolled until its contents reset: kept in this
	 * property's text, so that a save keeps it.
	 * @param yes true for rolled
	 */
	protected void markRolled(final boolean yes)
	{
		rolled = yes;
		String t = text().replaceAll("(?i)\\s*ROLLED=\\S+", "").trim();
		if(yes)
			t = (t + " ROLLED=TRUE").trim();
		super.setMiscText(t);
	}

	/**
	 * Rolls the table: ROLLS times, if CHANCE allows, and GOLD.
	 * @param player who it is for, or null
	 * @return how many items it gave
	 */
	public int roll(final MOB player)
	{
		final Physical host = affected;
		if((host == null) || (id.length() == 0))
			return 0;
		if((chance < 100) && (CMLib.dice().rollPercentage() > chance))
			return 0;
		final Room R = CMLib.map().roomLocation(host);
		final Area A = (R == null) ? null : R.getArea();
		final Map<String, String> vars = new HashMap<String, String>();
		vars.put("MOB_LEVEL", "" + host.phyStats().level());
		if(A != null)
		{
			vars.put("AREA_LEVEL", "" + A.getIStat(Area.Stats.MED_LEVEL));
			final String theme = A.getBlurbFlag("THEME");
			vars.put("THEME", ((theme != null) && (theme.trim().length() > 0)) ? theme.trim().toLowerCase()
				: (((A.getTheme() >= 0) && (A.getTheme() < Area.THEME_NAMES.length)) ? Area.THEME_NAMES[A.getTheme()].toLowerCase() : "fantasy"));
		}
		vars.put("PLAYER_LEVEL", "" + ((player == null) ? host.phyStats().level() : player.phyStats().level()));
		final int n = Math.max(0, CMath.s_parseIntExpression(rolls.replace(" ", "")));
		int given = 0;
		for(int i = 0; i < n; i++)
		{
			try
			{
				for(final Item I : GenCalls.items(file, id, vars, player, R))
				{
					if(affixes.length() > 0) // G10
						Affixes.apply(I, CMath.s_parseIntExpression(affixes.replace(" ", "")));
					place(host, I);
					given++;
				}
			}
			catch(final CMException e)
			{
				Log.errOut("Prop_LootTable", host.Name() + ": " + e.getMessage());
				break;
			}
		}
		if(gold.length() > 0)
		{
			final double amount = CMath.s_parseMathExpression(gold);
			if(amount > 0)
			{
				if(host instanceof MOB)
					CMLib.beanCounter().giveSomeoneMoney((MOB)host, amount);
				else
				if((host instanceof Container) && (((Container)host).owner() instanceof ItemCollection))
					CMLib.beanCounter().makeBestCurrency(CMLib.beanCounter().getCurrency(host), amount, (ItemCollection)((Container)host).owner(), (Container)host);
			}
		}
		return given;
	}

	private static void place(final Physical host, final Item I)
	{
		if(host instanceof MOB)
			((MOB)host).addItem(I);
		else
		if((host instanceof Container) && (((Container)host).owner() != null))
		{
			((Container)host).owner().addItem(I);
			I.setContainer((Container)host);
		}
		else
		{
			final Room R = CMLib.map().roomLocation(host);
			if(R != null)
				R.addItem(I, ItemPossessor.Expire.Monster_EQ);
		}
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		// DEATH: into the mob's pack as it dies, so its corpse has it
		if((affected instanceof MOB)
		&& (msg.sourceMinor() == CMMsg.TYP_DEATH)
		&& (msg.source() == affected)
		&& when().equals("DEATH")
		&& (!lifeRolled))
		{
			lifeRolled = true;
			final MOB killer = ((MOB)affected).getVictim();
			roll(((killer != null) && killer.isPlayer()) ? killer : null);
		}
		return super.okMessage(myHost, msg);
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		final String w = when();
		if((affected instanceof MOB) && (msg.sourceMinor() == CMMsg.TYP_LIFE) && (msg.source() == affected))
		{
			lifeRolled = false; // a new life
			if(w.equals("SPAWN"))
			{
				lifeRolled = true;
				roll(null);
			}
		}
		else
		if((affected instanceof Container) && (!rolled))
		{
			if(w.equals("OPEN") && (msg.targetMinor() == CMMsg.TYP_OPEN) && (msg.target() == affected))
			{
				markRolled(true);
				roll(msg.source());
			}
			else
			if(w.equals("SPAWN") && (msg.targetMinor() == CMMsg.TYP_ENTER) && (msg.target() instanceof Room)
			&& (msg.target() == CMLib.map().roomLocation(affected)) && msg.source().isPlayer())
			{
				markRolled(true);
				roll(msg.source());
			}
		}
	}
}
