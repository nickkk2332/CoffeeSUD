package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Items.interfaces.Wearable;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
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
 * A19: named loadouts.  LOADOUT SAVE name keeps what the character wears and
 * wields (each item and where), their composed skills' linked supports, and
 * their own reservations (Prop_Reserve OF=SELF); LOADOUT name puts all of it
 * back at once, or changes nothing and says why.  A swap costs LOADOUTCOST
 * actions for each thing it changes.  Off unless the world's LOADOUTS= is
 * above 0 (the most a character may keep).
 */
public final class Loadouts
{
	private Loadouts()
	{
	}

	/** @return whether loadouts are on in this world */
	public static boolean enabled()
	{
		return WorldRules.loadoutsMax() > 0;
	}

	private static Ability store(final MOB mob, final boolean create)
	{
		Ability A = mob.fetchEffect("Prop_Loadouts");
		if((A == null) && create)
		{
			A = CMClass.getAbility("Prop_Loadouts");
			if(A == null)
				return null;
			A.setMiscText("{}");
			mob.addNonUninvokableEffect(A);
		}
		return A;
	}

	private static JSONObject all(final MOB mob)
	{
		final Ability A = store(mob, false);
		if((A == null) || (A.text().trim().length() == 0))
			return new JSONObject();
		try
		{
			return ModuleDefinitions.parse(A.text());
		}
		catch(final IOException e)
		{
			return new JSONObject();
		}
	}

	private static void put(final MOB mob, final JSONObject all)
	{
		final Ability A = store(mob, true);
		if(A != null)
			A.setMiscText(ModuleDefinitions.json(all));
	}

	private static String key(final String name)
	{
		return name.trim().toLowerCase();
	}

	/** what the character wears and wields now (items in the main inventory only) */
	private static List<Item> worn(final MOB mob)
	{
		final List<Item> out = new ArrayList<Item>();
		for(int i = 0; i < mob.numItems(); i++)
		{
			final Item I = mob.getItem(i);
			if((I != null) && (I.container() == null) && (!I.amWearingAt(Wearable.IN_INVENTORY)))
				out.add(I);
		}
		return out;
	}

	private static List<Ability> ownReserves(final MOB mob)
	{
		final List<Ability> out = new ArrayList<Ability>();
		for(final Enumeration<Ability> e = mob.effects(); e.hasMoreElements();)
		{
			final Ability A = e.nextElement();
			if((A instanceof NamedResources.Reserver) && (!((NamedResources.Reserver)A).reservesFromLeader()))
				out.add(A);
		}
		return out;
	}

	/**
	 * LOADOUT SAVE: keeps what is worn, the composed skills' links and the
	 * character's own reservations under a name.
	 * @return what to tell them
	 */
	public static String save(final MOB mob, final String name)
	{
		if(name.trim().length() == 0)
			return CMLib.lang().L("Name the loadout: LOADOUT SAVE [name].");
		final JSONObject all = all(mob);
		if((!all.containsKey(key(name))) && (all.size() >= WorldRules.loadoutsMax()))
			return CMLib.lang().L("You can keep @x1 loadouts; LOADOUT FORGET one first.", "" + WorldRules.loadoutsMax());
		final JSONObject L = new JSONObject();
		L.put("name", name.trim());
		final List<Object> items = new ArrayList<Object>();
		for(final Item I : worn(mob))
		{
			final JSONObject o = new JSONObject();
			o.put("ref", InstanceIds.reference(I));
			o.put("item", I.name());
			o.put("worn", Long.valueOf(I.rawWornCode()));
			items.add(o);
		}
		L.put("items", items.toArray());
		final JSONObject b = AbilityComposition.bindings(mob);
		if(b != null)
			L.put("compose", ModuleDefinitions.json(b));
		final List<Object> res = new ArrayList<Object>();
		for(final Ability A : ownReserves(mob))
			res.add(((NamedResources.Reserver)A).reservationSpec());
		L.put("reserves", res.toArray());
		all.put(key(name), L);
		put(mob, all);
		return CMLib.lang().L("Loadout '@x1' kept: @x2 items worn, @x3 reservations@x4.", name.trim(), "" + items.size(), "" + res.size(),
			(b != null) ? CMLib.lang().L(", your composed skills' links") : "");
	}

	/** LOADOUT FORGET */
	public static String forget(final MOB mob, final String name)
	{
		final JSONObject all = all(mob);
		if(all.remove(key(name)) == null)
			return CMLib.lang().L("You have no loadout called '@x1'.", name);
		put(mob, all);
		return CMLib.lang().L("Loadout '@x1' forgotten.", name);
	}

	/** LOADOUT: the loadouts kept */
	public static String list(final MOB mob)
	{
		final JSONObject all = all(mob);
		if(all.isEmpty())
			return CMLib.lang().L("You have no loadouts.  LOADOUT SAVE [name] keeps what you wear, your composed links and your reservations (up to @x1).",
				"" + WorldRules.loadoutsMax());
		final StringBuilder s = new StringBuilder(CMLib.lang().L("Your loadouts (@x1 of @x2):\n\r", "" + all.size(), "" + WorldRules.loadoutsMax()));
		for(final String k : new java.util.TreeSet<String>(all.keySet()))
		{
			final Plan P = plan(mob, k);
			s.append("  ").append(P.name).append(": ");
			s.append((P.why != null) ? CMLib.lang().L("cannot be used now (@x1)", P.why)
									: (P.changes() == 0) ? CMLib.lang().L("in use") : CMLib.lang().L("@x1 changes", "" + P.changes()));
			s.append("\n\r");
		}
		return s.append(CMLib.lang().L("LOADOUT [name] swaps to one; LOADOUT SHOW [name] says what it would change.\n\r")).toString();
	}

	/** what a swap would do */
	public static final class Plan
	{
		public String				name	= "";
		public String				why		= null;
		public final List<Item>		remove	= new ArrayList<Item>();
		public final List<Item>		wear	= new ArrayList<Item>();
		public final List<Long>		at		= new ArrayList<Long>();
		public final List<Ability>	unreserve	= new ArrayList<Ability>();
		public final List<String>	reserve	= new ArrayList<String>();
		public JSONObject			compose	= null;
		public boolean				relink	= false;

		/** @return how many things change */
		public int changes()
		{
			return remove.size() + wear.size() + unreserve.size() + reserve.size() + (relink ? 1 : 0);
		}
	}

	/**
	 * Works out a swap without changing anything.
	 * @param mob the character
	 * @param name the loadout
	 * @return the plan; its why is set when it cannot be done
	 */
	public static Plan plan(final MOB mob, final String name)
	{
		final Plan P = new Plan();
		P.name = name;
		final JSONObject all = all(mob);
		if(!all.containsKey(key(name)))
		{
			P.why = CMLib.lang().L("you have no loadout called '@x1'", name);
			return P;
		}
		try
		{
			final JSONObject L = ModuleDefinitions.object(all.get(key(name)), "loadout");
			P.name = String.valueOf(L.get("name"));
			final List<Item> keep = new ArrayList<Item>();
			for(final Object raw : ModuleDefinitions.array(L.get("items"), "items"))
			{
				final JSONObject o = ModuleDefinitions.object(raw, "item");
				final String ref = String.valueOf(o.get("ref"));
				final long code = ((Number)o.get("worn")).longValue();
				Item found = null;
				for(int i = 0; i < mob.numItems(); i++)
				{
					final Item I = mob.getItem(i);
					if((I != null) && ref.equals(InstanceIds.reference(I)))
						found = I;
				}
				if(found == null)
				{
					P.why = CMLib.lang().L("@x1 is not with you", String.valueOf(o.get("item")));
					return P;
				}
				if(found.container() != null)
				{
					P.why = CMLib.lang().L("@x1 is in @x2", found.name(), found.container().name());
					return P;
				}
				keep.add(found);
				if(found.rawWornCode() != code)
				{
					P.wear.add(found);
					P.at.add(Long.valueOf(code));
				}
			}
			for(final Item I : worn(mob))
			{
				if((!keep.contains(I)) || P.wear.contains(I))
					P.remove.add(I);
			}
			final List<String> want = new ArrayList<String>();
			for(final Object raw : ModuleDefinitions.array(L.get("reserves"), "reserves"))
				want.add(String.valueOf(raw));
			final List<String> have = new ArrayList<String>();
			for(final Ability A : ownReserves(mob))
			{
				final String spec = ((NamedResources.Reserver)A).reservationSpec();
				have.add(spec);
				if(!want.contains(spec))
					P.unreserve.add(A);
			}
			for(final String t : want)
			{
				if(!have.contains(t))
					P.reserve.add(t);
			}
			if(L.containsKey("compose") && (AbilityComposition.bindings(mob) != null))
			{
				P.compose = ModuleDefinitions.parse(String.valueOf(L.get("compose")));
				P.relink = !ModuleDefinitions.json(P.compose).equals(ModuleDefinitions.json(AbilityComposition.bindings(mob)));
				if(P.relink)
				{
					try
					{
						AbilityComposition.checkBindings(mob, P.compose);
					}
					catch(final IOException e)
					{
						P.why = CMLib.lang().L("its composed skills cannot be used: @x1", e.getMessage());
						return P;
					}
				}
			}
		}
		catch(final IOException e)
		{
			P.why = CMLib.lang().L("it cannot be read: @x1", e.getMessage());
		}
		return P;
	}

	/** @return the actions a swap to this loadout costs now */
	public static double cost(final MOB mob, final String name)
	{
		final Plan P = plan(mob, name);
		return (P.why != null) ? 0.0 : (P.changes() * WorldRules.loadoutCost());
	}

	/** LOADOUT SHOW: what a swap would change, or why it cannot be made */
	public static String show(final MOB mob, final String name)
	{
		final Plan P = plan(mob, name);
		if(P.why != null)
			return CMLib.lang().L("You cannot use loadout '@x1': @x2.", P.name, P.why);
		if(P.changes() == 0)
			return CMLib.lang().L("Loadout '@x1' is what you have now.", P.name);
		final StringBuilder s = new StringBuilder(CMLib.lang().L("Loadout '@x1' would:\n\r", P.name));
		for(final Item I : P.remove)
			if(!P.wear.contains(I))
				s.append(CMLib.lang().L("  take off @x1\n\r", I.name()));
		for(int i = 0; i < P.wear.size(); i++)
			s.append(CMLib.lang().L("  put on @x1 (@x2)\n\r", P.wear.get(i).name(), Wearable.CODES.instance().name(P.at.get(i).longValue()).toLowerCase()));
		for(final Ability A : P.unreserve)
			s.append(CMLib.lang().L("  end @x1\n\r", ((NamedResources.Reserver)A).reservationName()));
		for(final String t : P.reserve)
			s.append(CMLib.lang().L("  begin @x1\n\r", reserveName(t)));
		if(P.relink)
			s.append(CMLib.lang().L("  link your composed skills as they were\n\r"));
		return s.append(CMLib.lang().L("@x1 changes, @x2 actions.\n\r", "" + P.changes(), "" + (Math.round(P.changes() * WorldRules.loadoutCost() * 100) / 100.0))).toString();
	}

	private static String reserveName(final String text)
	{
		final Ability A = CMClass.getAbility("Prop_Reserve");
		if(A == null)
			return text;
		A.setMiscText(text);
		return ((NamedResources.Reserver)A).reservationName();
	}

	/**
	 * LOADOUT name: the swap, all of it or none.  Each item is asked, as
	 * REMOVE and WEAR ask (a curse, a binding, a mask can refuse); the
	 * reservations must all fit afterwards; the composed links are checked
	 * first.  Anything refused puts back everything already changed.
	 * @return what to tell them
	 */
	public static String use(final MOB mob, final String name)
	{
		final Plan P = plan(mob, name);
		if(P.why != null)
			return CMLib.lang().L("You cannot use loadout '@x1': @x2.", P.name, P.why);
		if(P.changes() == 0)
			return CMLib.lang().L("Loadout '@x1' is what you have now.", P.name);
		final Room R = mob.location();
		final List<Item> touched = new ArrayList<Item>();
		final List<Long> was = new ArrayList<Long>();
		final List<Ability> ended = new ArrayList<Ability>(), begun = new ArrayList<Ability>();
		String refused = null;
		for(final Item I : P.remove)
		{
			if((R != null) && (!R.okMessage(mob, CMClass.getMsg(mob, I, null, CMMsg.MSG_REMOVE, null))))
			{
				refused = CMLib.lang().L("you cannot take off @x1", I.name());
				break;
			}
			touched.add(I);
			was.add(Long.valueOf(I.rawWornCode()));
			I.unWear();
		}
		if(refused == null)
		{
			for(int i = 0; i < P.wear.size(); i++)
			{
				final Item I = P.wear.get(i);
				final long code = P.at.get(i).longValue();
				final int type = (code == Wearable.WORN_WIELD) ? CMMsg.MSG_WIELD : (code == Wearable.WORN_HELD) ? CMMsg.MSG_HOLD : CMMsg.MSG_WEAR;
				if(((R != null) && (!R.okMessage(mob, CMClass.getMsg(mob, I, null, type, null)))) || (!I.canWear(mob, code)))
				{
					refused = CMLib.lang().L("you cannot put on @x1 there", I.name());
					break;
				}
				if(!touched.contains(I))
				{
					touched.add(I);
					was.add(Long.valueOf(I.rawWornCode()));
				}
				I.unWear();
				I.wearAt(code);
			}
		}
		if(refused == null)
		{
			for(final Ability A : P.unreserve)
			{
				mob.delEffect(A);
				ended.add(A);
			}
			for(final String t : P.reserve)
			{
				final Ability A = CMClass.getAbility("Prop_Reserve");
				A.setMiscText(t);
				mob.addNonUninvokableEffect(A);
				begun.add(A);
			}
			for(final Ability A : begun)
			{
				if(!NamedResources.fits(mob, (NamedResources.Reserver)A))
				{
					refused = CMLib.lang().L("there is not enough @x1 for @x2", ((NamedResources.Reserver)A).reservedPool(),
						((NamedResources.Reserver)A).reservationName());
					break;
				}
			}
		}
		if((refused == null) && P.relink)
		{
			try
			{
				AbilityComposition.setBindings(mob, P.compose);
			}
			catch(final IOException e)
			{
				refused = CMLib.lang().L("its composed skills cannot be used: @x1", e.getMessage());
			}
		}
		if(refused != null)
		{
			// put back everything already changed
			for(final Ability A : begun)
				mob.delEffect(A);
			for(final Ability A : ended)
				mob.addNonUninvokableEffect(A);
			for(int i = touched.size() - 1; i >= 0; i--)
			{
				final Item I = touched.get(i);
				I.unWear();
				if(was.get(i).longValue() != 0)
					I.wearAt(was.get(i).longValue());
			}
			recover(mob);
			return CMLib.lang().L("You cannot use loadout '@x1': @x2.  Nothing was changed.", P.name, refused);
		}
		for(final Ability A : begun)
			A.tick(mob, com.planet_ink.coffee_mud.core.interfaces.Tickable.TICKID_MOB);
		recover(mob);
		if(R != null)
			R.show(mob, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> change(s) <S-HIS-HER> gear."));
		return CMLib.lang().L("You change to loadout '@x1' (@x2 changes).", P.name, "" + P.changes());
	}

	private static void recover(final MOB mob)
	{
		mob.forgetWornItems(); // gear changed without the wear messages
		mob.recoverCharStats();
		mob.recoverPhyStats();
		mob.recoverMaxState();
	}
}
