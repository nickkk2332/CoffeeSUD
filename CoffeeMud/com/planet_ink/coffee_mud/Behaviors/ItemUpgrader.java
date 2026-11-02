package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
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
 * I27: upgrades at a smith or a workbench (a mob or an item).  Rows: UPGRADE1=
 * ... UPGRADE9= (or one a line in a file, UPGRADES=world/upgrades.txt), each
 * FROM="name|tag:X|catalog:X" and TO="catalog item" (it becomes that, in its
 * place) or ADJ="dam+2 att+5 arm+3" (the same item, better), COST= gold,
 * MATERIALS="starmetal ingot x3, coal" what is used up, LEVEL= the least
 * level, KEEP= MPTRANSFORM's (STATE,TAGS,USES; its place, CONTAINER,WORN, always),
 * MSG= what is seen.  An item may carry its own path (Prop_UpgradePath with
 * the same keys but FROM).  UPGRADE item says what it would take; UPGRADE
 * item CONFIRM does it.
 */
public class ItemUpgrader extends StdBehavior implements VerbTaker
{
	@Override
	public String ID()
	{
		return "ItemUpgrader";
	}

	@Override
	public String name()
	{
		return "an upgrader of items";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS | Behavior.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return "upgrading items";
	}

	protected final List<Map<String, String>>	rows	= new ArrayList<Map<String, String>>();
	protected String							file	= "";
	protected PhysicalAgent						host	= null;

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		final Map<String, String> q = CMParms.parseExactParms((newParms == null) ? "" : newParms);
		rows.clear();
		for(int i = 0; i <= 9; i++)
		{
			final String r = CMParms.getParmStr(q, "UPGRADE" + ((i == 0) ? "" : ("" + i)), "").trim();
			if(r.length() > 0)
				rows.add(CMParms.parseExactParms(r));
		}
		file = CMParms.getParmStr(q, "UPGRADES", "").trim();
		ScriptEvents.propVerbsSeen();
	}

	@Override
	public void startBehavior(final PhysicalAgent forMe)
	{
		super.startBehavior(forMe);
		host = forMe;
	}

	/** the rows, its own and its file's */
	protected List<Map<String, String>> allRows()
	{
		final List<Map<String, String>> all = new ArrayList<Map<String, String>>(rows);
		if(file.length() > 0)
		{
			try
			{
				java.io.File F;
				if(file.toLowerCase().startsWith("world/"))
					F = new java.io.File((WorldFiles.worldDir() == null) ? new java.io.File(".") : WorldFiles.worldDir(), file.substring(6));
				else
					F = new java.io.File(file);
				if(F.isFile())
				{
					for(final String l : new String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8").split("\\r?\\n"))
					{
						if((l.trim().length() > 0) && (!l.trim().startsWith("#")))
							all.add(CMParms.parseExactParms(l.trim()));
					}
				}
			}
			catch(final Exception e)
			{
				Log.errOut("ItemUpgrader", e.getMessage());
			}
		}
		return all;
	}

	/**
	 * The upgrade for an item: its own path, or the first row that fits it.
	 * @param I the item
	 * @return the row, or null
	 */
	public Map<String, String> rowFor(final Item I)
	{
		final Ability P = I.fetchEffect("Prop_UpgradePath");
		if(P != null)
			return CMParms.parseExactParms(P.text());
		for(final Map<String, String> r : allRows())
		{
			for(final String f : CMParms.getParmStr(r, "FROM", "").split("\\|"))
			{
				if((f.trim().length() > 0) && Combinations.fits(f.trim(), I))
					return r;
			}
		}
		return null;
	}

	/** the materials of a row: name and how many */
	protected static List<Object[]> materials(final Map<String, String> r)
	{
		final List<Object[]> list = new ArrayList<Object[]>();
		for(final String raw : CMParms.getParmStr(r, "MATERIALS", "").split(","))
		{
			String m = raw.trim();
			if(m.length() == 0)
				continue;
			int n = 1;
			final java.util.regex.Matcher x = java.util.regex.Pattern.compile("(?i)^(.*?)\\s+x\\s*(\\d+)$").matcher(m);
			if(x.matches())
			{
				m = x.group(1).trim();
				n = CMath.s_int(x.group(2));
			}
			list.add(new Object[] { m, Integer.valueOf(Math.max(1, n)) });
		}
		return list;
	}

	/** the carried items of a material, up to how many */
	protected static List<Item> carried(final MOB mob, final String name, final int n, final Item not)
	{
		final List<Item> got = new ArrayList<Item>();
		for(final Enumeration<Item> i = mob.items(); i.hasMoreElements() && (got.size() < n);)
		{
			final Item I = i.nextElement();
			if((I != null) && (I != not) && (I.container() == null) && Combinations.fits(name, I))
				got.add(I);
		}
		return got;
	}

	/** what a row costs, in words */
	protected String costText(final MOB mob, final Map<String, String> r)
	{
		final StringBuilder b = new StringBuilder();
		final int cost = CMParms.getParmInt(r, "COST", 0);
		if(cost > 0)
			b.append(CMLib.beanCounter().nameCurrencyShort(mob, cost));
		for(final Object[] m : materials(r))
			b.append((b.length() > 0) ? ", " : "").append(m[0]).append((((Integer)m[1]).intValue() > 1) ? (" x" + m[1]) : "");
		return (b.length() == 0) ? "nothing" : b.toString();
	}

	/** what a row makes, in words */
	protected static String toText(final Map<String, String> r)
	{
		final String to = CMParms.getParmStr(r, "TO", "").trim();
		if(to.length() > 0)
			return to.toLowerCase().startsWith("catalog:") ? to.substring(8).trim() : to;
		return "a better one (" + CMParms.getParmStr(r, "ADJ", "") + ")";
	}

	/**
	 * Upgrades an item for one.
	 * @param mob who
	 * @param I the item
	 * @param confirm true to do it, false to say what it would take
	 * @return true if it was understood
	 */
	public boolean upgrade(final MOB mob, final Item I, final boolean confirm)
	{
		final Map<String, String> r = rowFor(I);
		if(r == null)
		{
			mob.tell(mob, I, null, CMLib.lang().L("Nothing here can be done with <T-NAME>."));
			return true;
		}
		final int level = CMParms.getParmInt(r, "LEVEL", 0);
		if(mob.phyStats().level() < level)
		{
			mob.tell(CMLib.lang().L("You must be level @x1 for that.", "" + level));
			return true;
		}
		if(!confirm)
		{
			mob.tell(mob, I, null, CMLib.lang().L("<T-NAME> can become @x1, for @x2.  UPGRADE @x3 CONFIRM to have it done.", toText(r), costText(mob, r), CMLib.english().getContextName((ItemCollection)mob, I)));
			return true;
		}
		final int cost = CMParms.getParmInt(r, "COST", 0);
		if((cost > 0) && (CMLib.beanCounter().getTotalAbsoluteNativeValue(mob) < cost))
		{
			mob.tell(CMLib.lang().L("You cannot afford @x1.", CMLib.beanCounter().nameCurrencyShort(mob, cost)));
			return true;
		}
		final List<Item> use = new ArrayList<Item>();
		for(final Object[] m : materials(r))
		{
			final List<Item> got = carried(mob, (String)m[0], ((Integer)m[1]).intValue(), I);
			if(got.size() < ((Integer)m[1]).intValue())
			{
				mob.tell(CMLib.lang().L("You need @x1 x@x2.", (String)m[0], "" + m[1]));
				return true;
			}
			use.addAll(got);
		}
		if(cost > 0)
			CMLib.beanCounter().subtractMoney(mob, cost);
		for(final Item M : use)
			M.destroy();
		final String to = CMParms.getParmStr(r, "TO", "").trim();
		Item done = I;
		if(to.length() > 0)
		{
			final String name = to.toLowerCase().startsWith("catalog:") ? to.substring(8).trim() : to;
			// the place is always kept: in the same hand, the same container
			done = Transform.into(I, name, Transform.DEFAULT + "," + CMParms.getParmStr(r, "KEEP", "").toUpperCase().replace("SOCKETS", ""));
			if(done == null)
			{
				mob.tell(CMLib.lang().L("Something goes wrong."));
				Log.errOut("ItemUpgrader", "no item called " + name);
				return true;
			}
		}
		else
		{
			for(final String a : CMParms.parse(CMParms.getParmStr(r, "ADJ", "")))
			{
				final java.util.regex.Matcher x = java.util.regex.Pattern.compile("(?i)^(dam|att|arm|lvl)([+-]\\d+)$").matcher(a.trim());
				if(!x.matches())
					continue;
				final int n = CMath.s_int(x.group(2).replace("+", ""));
				final String k = x.group(1).toLowerCase();
				if(k.equals("dam"))
					I.basePhyStats().setDamage(I.basePhyStats().damage() + n);
				else
				if(k.equals("att"))
					I.basePhyStats().setAttackAdjustment(I.basePhyStats().attackAdjustment() + n);
				else
				if(k.equals("arm"))
					I.basePhyStats().setArmor(I.basePhyStats().armor() + n);
				else
					I.basePhyStats().setLevel(I.basePhyStats().level() + n);
			}
			I.recoverPhyStats();
			final Ability P = I.fetchEffect("Prop_UpgradePath");
			if(P != null)
				I.delEffect(P);
		}
		final Room R = mob.location();
		final String msg = CMParms.getParmStr(r, "MSG", CMLib.lang().L("<S-NAME> hand(s) over @x1, and <T-NAME> comes back better.", costText(mob, r)));
		if(R != null)
			R.show(mob, done, CMMsg.MSG_OK_ACTION, msg);
		mob.recoverPhyStats();
		return true;
	}

	@Override
	public boolean takeVerb(final MOB mob, final PhysicalAgent at, final String verb, final String rest)
	{
		if((at != host) || (!verb.equalsIgnoreCase("upgrade")))
			return false;
		String r = rest.trim();
		boolean confirm = false;
		if(r.toUpperCase().endsWith(" CONFIRM"))
		{
			confirm = true;
			r = r.substring(0, r.length() - 8).trim();
		}
		if(r.length() == 0)
		{
			mob.tell(CMLib.lang().L("Upgrade what?"));
			return true;
		}
		final Item I = mob.fetchItem(null, Wearable.FILTER_ANY, r);
		if(I == null)
		{
			mob.tell(CMLib.lang().L("You don't have '@x1'.", r));
			return true;
		}
		return upgrade(mob, I, confirm);
	}
}
