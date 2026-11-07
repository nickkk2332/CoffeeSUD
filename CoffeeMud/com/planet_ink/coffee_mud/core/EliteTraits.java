package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.ModuleContent.Entry;
import com.planet_ink.coffee_mud.core.ModuleContent.Library;
import com.planet_ink.coffee_mud.core.ModuleContent.Node;
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
 * A24: elite traits, world/traits/(id).v(n).json, part of the encounter
 * director (encounters.director).  A trait has a title, a readable line, a
 * power (what it costs of a budget), effects (Prop_SpellAdder's form, worked
 * through Prop_EliteTraits), the traits it excludes, and whether it
 * possesses (G08): when its host dies, it passes to a living member of the
 * same pack.  A director's spawn picks traits for each actor within a power
 * budget, from its own stream, and can give its whole pack traits.
 */
public final class EliteTraits
{
	private EliteTraits()
	{
	}

	public static final String	FOLDER	= "traits";

	/** one trait */
	public static final class Trait
	{
		public final String			title;
		public final String			line;
		public final int			power;
		public final String			effects;
		public final Set<String>	excludes;
		public final boolean		possess;

		Trait(final Node n) throws IOException
		{
			n.allow("schema", "id", "version", "title", "line", "power", "effects", "excludes", "possess");
			title = n.string("title", "", 40);
			line = n.string("line", "", 200);
			power = (int)n.integer("power", 1, 0, 100);
			effects = n.string("effects", "", 500);
			excludes = Collections.unmodifiableSet(new TreeSet<String>(n.strings("excludes", 16, true)));
			possess = n.bool("possess", false);
		}
	}

	private static Library<Trait> library = null;

	private static final ModuleContent.Reader<Trait> READER = new ModuleContent.Reader<Trait>()
	{
		@Override
		public Trait read(final Node node) throws IOException
		{
			return new Trait(node);
		}
	};

	public static synchronized Library<Trait> definitions()
	{
		if(library == null)
			library = ModuleContent.load(ModuleContent.world(), FOLDER, "trait", READER);
		return library;
	}

	public static synchronized void reset()
	{
		library = null;
	}

	/** a spawn's trait rules: picked per actor within a budget, and given to the whole pack */
	public static final class Rules
	{
		public final int			pick;
		public final int			budget;
		public final List<String>	from;
		public final List<String>	pack;

		public Rules(final Node n) throws IOException
		{
			n.allow("pick", "budget", "from", "pack");
			pick = (int)n.integer("pick", 1, 0, 4);
			budget = (int)n.integer("budget", 100, 0, 1000);
			from = Collections.unmodifiableList(n.strings("from", 16, true));
			pack = Collections.unmodifiableList(n.strings("pack", 4, true));
		}
	}

	/**
	 * Picks an actor's traits: candidates in an order drawn from the
	 * encounters stream, each taken while the pick count and power budget
	 * allow and no trait taken excludes it (or is excluded by it).
	 * @param rules the spawn's rules
	 * @param already traits the actor has (the pack's)
	 * @return the IDs picked, in order
	 */
	public static List<String> pick(final Rules rules, final List<String> already) throws IOException
	{
		final Library<Trait> lib = definitions();
		final List<String> order = new ArrayList<String>(rules.from);
		// a shuffle from the stream, so a saved seed picks the same again
		for(int i = order.size() - 1; i > 0; i--)
		{
			final int j = RandomStreams.roll(RandomStreams.draw(RandomStreams.ENCOUNTERS), i + 1) - 1;
			final String t = order.get(i);
			order.set(i, order.get(j));
			order.set(j, t);
		}
		final List<String> out = new ArrayList<String>();
		final List<String> all = new ArrayList<String>(already);
		int left = rules.budget;
		for(final String id : order)
		{
			if(out.size() >= rules.pick)
				break;
			final Trait T = lib.latest(id).value;
			if(T.power > left)
				continue;
			boolean clash = all.contains(id);
			for(final String other : all)
				clash = clash || T.excludes.contains(other) || (lib.has(other) && lib.latest(other).value.excludes.contains(id));
			if(clash)
				continue;
			out.add(id);
			all.add(id);
			left -= T.power;
		}
		return out;
	}

	/**
	 * Gives an actor traits: the Prop_EliteTraits effect, holding each trait
	 * as it is now (so a later edit does not change a living actor).
	 * @param M the actor
	 * @param ids the traits
	 * @param pack the pack it belongs to (for possession)
	 */
	public static void apply(final MOB M, final List<String> ids, final String pack) throws IOException
	{
		if(ids.isEmpty())
			return;
		final Library<Trait> lib = definitions();
		Ability A = M.fetchEffect("Prop_EliteTraits");
		final JSONObject data = ((A == null) || (A.text().trim().length() == 0)) ? new JSONObject() : ModuleDefinitions.parse(A.text());
		data.put("pack", pack);
		final List<Object> list = new ArrayList<Object>();
		if(data.containsKey("traits"))
			list.addAll(Arrays.asList(ModuleDefinitions.array(data.get("traits"), "traits")));
		for(final String id : ids)
		{
			final Entry<Trait> e = lib.latest(id);
			list.add(record(id, e.value));
			ModuleStats.count(EncounterDirectors.FAMILY, id, "traits given");
		}
		data.put("traits", list.toArray());
		if(A == null)
		{
			A = CMClass.getAbility("Prop_EliteTraits");
			if(A == null)
				return;
			A.setMiscText(ModuleDefinitions.json(data));
			M.addNonUninvokableEffect(A);
		}
		else
			A.setMiscText(ModuleDefinitions.json(data));
		M.recoverCharStats();
		M.recoverPhyStats();
		M.recoverMaxState();
	}

	/** a trait as an actor holds it */
	public static JSONObject record(final String id, final Trait T)
	{
		final JSONObject o = new JSONObject();
		o.put("id", id);
		o.put("title", T.title.isEmpty() ? CMStrings.capitalizeAndLower(id.replaceFirst("^[^.]*\\.", "")) : T.title);
		o.put("line", T.line);
		o.put("effects", T.effects);
		o.put("power", Long.valueOf(T.power));
		if(T.possess)
			o.put("possess", Boolean.TRUE);
		return o;
	}

	/** the problems of the traits a director names: unknown, and budgets no trait fits */
	public static List<String> check(final String path, final Rules rules)
	{
		final List<String> out = new ArrayList<String>();
		final Library<Trait> lib = definitions();
		final List<String> named = new ArrayList<String>(rules.from);
		named.addAll(rules.pack);
		for(final String id : named)
		{
			if(!lib.has(id))
				out.add(path + ": /traits: no trait " + id + " is installed in traits/");
		}
		return out;
	}
}
