package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.CharState;
import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.Common.interfaces.Faction;
import com.planet_ink.coffee_mud.Common.interfaces.PhyStats;
import com.planet_ink.coffee_mud.Common.interfaces.Tattoo;
import com.planet_ink.coffee_mud.Items.interfaces.Container;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
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
 * P27: creation's REVIEW step.  The character is kept as it was at the start
 * of each step, so BACK [step] puts it back and asks that step and the ones
 * after it again; the sheet shows what has been chosen.
 */
public class CreationReview
{
	private CreationReview()
	{
	}

	/** a character, and the login's place, as they were when a step began */
	public static class Snapshot
	{
		public CharStats					charStats;
		public PhyStats						phyStats;
		public CharState					state;
		public final List<Ability>			abilities	= new ArrayList<Ability>();
		public final List<Ability>			effects		= new ArrayList<Ability>();
		public final List<Item>				items		= new ArrayList<Item>();
		public final Map<Item, Item>		containers	= new IdentityHashMap<Item, Item>();
		public final List<String>			tattoos		= new ArrayList<String>();
		public final Map<String, Integer>	factions	= new LinkedHashMap<String, Integer>();
		public final List<String>			expertises	= new ArrayList<String>();
		public Room							start;
		public String						description	= "";
		/** the login's step state (CharCreation's), its queue and choices so far */
		public Object						loginState;
		public Object						resume;
		public String						question;
		public final List<String>			queue		= new ArrayList<String>();
		public final List<String>			kits		= new ArrayList<String>();
		public int							kitGroup;
		public int							index;
	}

	private static List<Item> copyItems(final List<Item> from, final Map<Item, Item> containedIn, final Map<Item, Item> newContainers)
	{
		final IdentityHashMap<Item, Item> map = new IdentityHashMap<Item, Item>();
		final List<Item> copies = new ArrayList<Item>();
		for(final Item I : from)
		{
			final Item C = (Item)I.copyOf();
			map.put(I, C);
			copies.add(C);
		}
		for(final Item I : from)
		{
			final Item in = containedIn.get(I);
			final Item C = map.get(I);
			if((in != null) && (map.get(in) instanceof Container))
				newContainers.put(C, map.get(in));
		}
		return copies;
	}

	/**
	 * The character as it is now.
	 * @param mob the character being made
	 * @return the snapshot, or null if it could not be taken
	 */
	public static Snapshot take(final MOB mob)
	{
		try
		{
			final Snapshot S = new Snapshot();
			S.charStats = (CharStats)mob.baseCharStats().copyOf();
			S.phyStats = (PhyStats)mob.basePhyStats().copyOf();
			S.state = (CharState)mob.baseState().copyOf();
			for(final Enumeration<Ability> a = mob.abilities(); a.hasMoreElements();)
			{
				final Ability A = a.nextElement();
				if(A != null)
					S.abilities.add((Ability)A.copyOf());
			}
			for(final Enumeration<Ability> a = mob.effects(); a.hasMoreElements();)
			{
				final Ability A = a.nextElement();
				if(A != null)
					S.effects.add((Ability)A.copyOf());
			}
			final List<Item> now = new ArrayList<Item>();
			final Map<Item, Item> in = new IdentityHashMap<Item, Item>();
			for(int i = 0; i < mob.numItems(); i++)
			{
				final Item I = mob.getItem(i);
				if(I != null)
				{
					now.add(I);
					if(I.container() != null)
						in.put(I, I.container());
				}
			}
			S.items.addAll(copyItems(now, in, S.containers));
			for(final Enumeration<Tattoo> t = mob.tattoos(); t.hasMoreElements();)
				S.tattoos.add(t.nextElement().getTattooName());
			for(final Enumeration<String> f = mob.factions(); f.hasMoreElements();)
			{
				final String id = f.nextElement();
				S.factions.put(id, Integer.valueOf(mob.fetchFaction(id)));
			}
			for(final Enumeration<String> e = mob.expertises(); e.hasMoreElements();)
				S.expertises.add(e.nextElement());
			S.start = mob.getStartRoom();
			S.description = mob.description();
			return S;
		}
		catch(final Exception e)
		{
			Log.errOut("CreationReview", e);
			return null;
		}
	}

	/**
	 * Puts the character back as it was.
	 * @param mob the character being made
	 * @param S how it was
	 */
	public static void restore(final MOB mob, final Snapshot S)
	{
		mob.setBaseCharStats((CharStats)S.charStats.copyOf());
		mob.setBasePhyStats((PhyStats)S.phyStats.copyOf());
		mob.setBaseState((CharState)S.state.copyOf());
		mob.delAllAbilities();
		for(final Ability A : S.abilities)
			mob.addAbility((Ability)A.copyOf());
		mob.delAllEffects(false);
		for(final Ability A : S.effects)
		{
			final Ability N = (Ability)A.copyOf();
			mob.addEffect(N);
			N.setAffectedOne(mob);
		}
		mob.delAllItems(true);
		final Map<Item, Item> in = new IdentityHashMap<Item, Item>();
		final List<Item> copies = copyItems(S.items, S.containers, in);
		for(int i = 0; i < copies.size(); i++)
		{
			final Item C = copies.get(i);
			mob.addItem(C);
			C.setRawWornCode(S.items.get(i).rawWornCode());
		}
		for(final Item C : copies)
			C.setContainer((Container)in.get(C));
		final List<String> had = new ArrayList<String>();
		for(final Enumeration<Tattoo> t = mob.tattoos(); t.hasMoreElements();)
			had.add(t.nextElement().getTattooName());
		for(final String t : had)
			mob.delTattoo(t);
		for(final String t : S.tattoos)
			mob.addTattoo(t);
		final List<String> fs = new ArrayList<String>();
		for(final Enumeration<String> f = mob.factions(); f.hasMoreElements();)
			fs.add(f.nextElement());
		for(final String f : fs)
			mob.removeFaction(f);
		for(final Map.Entry<String, Integer> f : S.factions.entrySet())
			mob.addFaction(f.getKey(), f.getValue().intValue());
		mob.delAllExpertises();
		for(final String e : S.expertises)
			mob.addExpertise(e);
		mob.setStartRoom(S.start);
		mob.setDescription(S.description);
		mob.recoverCharStats();
		mob.recoverPhyStats();
		mob.recoverMaxState();
		mob.resetToMaxState();
	}

	/**
	 * The character sheet REVIEW shows.
	 * @param mob the character being made
	 * @param kits the kits chosen
	 * @return the text
	 */
	public static String sheet(final MOB mob, final List<String> kits)
	{
		final StringBuilder s = new StringBuilder("\n\r^!").append(mob.Name()).append("^N, ");
		final CharStats C = mob.baseCharStats();
		final char g = (char)C.getStat(CharStats.STAT_GENDER);
		s.append((g == 'F') ? "female" : (g == 'M') ? "male" : "neuter").append(' ');
		s.append(C.getMyRace().name()).append(' ').append(C.getCurrentClass().name()).append("\n\r");
		final StringBuilder st = new StringBuilder("");
		for(final int i : CharStats.CODES.BASECODES())
			st.append(CMStrings.capitalizeAndLower(CharStats.CODES.NAME(i)).substring(0, 3).toUpperCase()).append(' ').append(C.getStat(i)).append("  ");
		s.append("^HStats^N: ").append(st.toString().trim()).append("\n\r");
		final StringBuilder fs = new StringBuilder("");
		for(final Enumeration<String> f = mob.factions(); f.hasMoreElements();)
		{
			final String id = f.nextElement();
			final Faction F = CMLib.factions().getFaction(id);
			if((F == null) || (!F.showInScore()))
				continue;
			final Faction.FRange R = CMLib.factions().getRange(id, mob.fetchFaction(id));
			fs.append(F.name()).append(": ").append((R == null) ? ("" + mob.fetchFaction(id)) : R.name()).append(", ");
		}
		if(fs.length() > 0)
			s.append("^HStanding^N: ").append(fs.substring(0, fs.length() - 2)).append("\n\r");
		if(C.getWorshipCharID().length() > 0)
			s.append("^HDeity^N: ").append(C.getWorshipCharID()).append("\n\r");
		Room start = mob.getStartRoom();
		final Origins.Origin O = Origins.chosen(mob);
		if(O != null)
		{
			s.append("^HOrigin^N: ").append(O.name()).append("\n\r");
			final Room R = (O.get("START").length() > 0) ? CMLib.map().getRoom(O.get("START")) : null;
			if(R != null)
				start = R;
		}
		if((kits != null) && (kits.size() > 0))
		{
			final List<String> names = new ArrayList<String>();
			for(final StartingKits.StartKit K : StartingKits.all())
			{
				if(kits.contains(K.name))
					names.add(K.title);
			}
			s.append("^HKits^N: ").append(CMParms.toListString(names)).append("\n\r");
		}
		final CreationSteps.Plan P = CreationSteps.plan();
		for(final String step : P.steps)
		{
			final CreationSteps.Question Q = P.questions.get(step);
			if(Q == null)
				continue;
			for(final CreationSteps.Choice Ch : Q.choices)
			{
				if(mob.findTattoo(CreationSteps.tattoo(Q, Ch)) != null)
				{
					s.append("^H").append(CMStrings.capitalizeAndLower(Q.name.replace('_', ' '))).append("^N: ").append(Ch.name).append("\n\r");
					final Room R = (Ch.start.length() > 0) ? CMLib.map().getRoom(Ch.start) : null;
					if(R != null)
						start = R;
				}
			}
		}
		if(start != null)
			s.append("^HStart^N: ").append(CMStrings.removeColors(start.displayText(mob))).append("\n\r");
		return s.toString();
	}
}
