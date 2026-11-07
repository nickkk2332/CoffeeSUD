package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Areas.interfaces.AutoGenArea;
import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.core.interfaces.Physical;
import com.planet_ink.coffee_mud.core.ModuleContent.Entry;

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
 * Q02: the references between the optional module families, followed in one
 * pass (WORLD CHECK ONLY=REFS): a composition's ability and pools, a support's
 * fit with the installed skills, a trigger's skill, pool and summoned mob, a
 * reward's items, pools and claim rooms, an encounter's mobs, rewards and
 * activity, an expedition's generated destination, return room, modifier
 * effects and activity, a track's skills, a loot policy's base, and the item
 * modifier library's pools, successors and recipes.  Each reference is reported
 * where it is written: its file and JSON path.  What the check cannot see into
 * (a script on a mob or item, a generator with scripts) is reported as unknown,
 * not as safe or as broken.
 */
public final class CrossRefs
{
	private CrossRefs()
	{
	}

	/** one reference: where it is written, the kind of thing it names, the name, and what was found */
	public static final class Ref
	{
		public final String	file, path, kind, target;
		public String		status	= "ok";
		public String		note	= "";

		Ref(final String file, final String path, final String kind, final String target)
		{
			this.file = file;
			this.path = path;
			this.kind = kind;
			this.target = target;
		}

		public String where()
		{
			return file + ": " + path;
		}
	}

	private static void add(final List<Ref> out, final String file, final String path, final String kind, final String target)
	{
		if((target != null) && (target.length() > 0))
			out.add(new Ref(file, path, kind, target));
	}

	/** every reference written in the installed definitions, unresolved */
	public static List<Ref> collect()
	{
		final List<Ref> out = new ArrayList<Ref>();
		final File world = ModuleContent.world();
		// A01 compositions: the native ability a skill needs, the pools it costs and gives
		final CompositionDefinitions.Library comps = CompositionDefinitions.load(world);
		for(final CompositionDefinitions.Definition d : comps.definitions.values())
		{
			add(out, d.path, "/requires_ability", "ability", d.requiresAbility);
			for(final String p : d.resourceCost.keySet())
				add(out, d.path, "/resources/cost/" + p, "pool", p);
			for(final String p : d.resourceGain.keySet())
				add(out, d.path, "/resources/gain/" + p, "pool", p);
			if(d.kind.equals("support"))
				add(out, d.path, "/requires_all", "support-fit", d.id);
		}
		// A03 triggers
		for(final Entry<AbilityTriggers.Trigger> e : AbilityTriggers.load().entries.values())
		{
			final AbilityTriggers.Trigger t = e.value;
			add(out, e.path, "/action/skill", "composition", t.skill);
			add(out, e.path, "/action/pool", "pool", t.pool);
			add(out, e.path, "/action/mob", "catalog-mob", t.mob);
			for(final String p : t.cost.keySet())
				add(out, e.path, "/cost/" + p, "pool", p);
		}
		// I18 rewards
		for(final Entry<RewardEscrow.Def> e : RewardEscrow.load().entries.values())
		{
			int i = 0;
			for(final String name : e.value.items.keySet())
				add(out, e.path, "/items/" + (i++) + "/name", "catalog-item", name);
			for(final String p : e.value.resources.keySet())
				add(out, e.path, "/resources/" + p, "pool", p);
			for(int r = 0; r < e.value.claimRooms.size(); r++)
				add(out, e.path, "/claim_rooms/" + r, "room", e.value.claimRooms.get(r));
		}
		// G03 encounters
		for(final Entry<EncounterDirectors.Def> e : EncounterDirectors.load().entries.values())
		{
			for(int p = 0; p < e.value.phases.size(); p++)
			{
				final EncounterDirectors.Phase ph = e.value.phases.get(p);
				for(int s = 0; s < ph.spawns.size(); s++)
					add(out, e.path, "/phases/" + p + "/spawn/" + s + "/mob", "catalog-mob", ph.spawns.get(s).mob);
				add(out, e.path, "/phases/" + p + "/reward", "reward", ph.reward);
			}
			add(out, e.path, "/reward", "reward", e.value.reward);
			add(out, e.path, "/activity", "activity", e.value.activity);
		}
		// G01 expeditions
		for(final Entry<Expeditions.Def> e : Expeditions.load().entries.values())
		{
			add(out, e.path, "/destination/area", "instance-area", e.value.area);
			add(out, e.path, "/return_room", "room", e.value.returnRoom);
			for(int m = 0; m < e.value.modifiers.size(); m++)
				add(out, e.path, "/modifiers/" + m + "/mob_effect/id", "ability", e.value.modifiers.get(m).effectId);
			add(out, e.path, "/activity", "activity", e.value.activity);
		}
		// P01 tracks
		for(final Entry<ProgressionTracks.Track> e : ProgressionTracks.load().entries.values())
		{
			for(int i = 0; i < e.value.earn.size(); i++)
				add(out, e.path, "/earn/" + i + "/skill", "composition", e.value.earn.get(i).skill);
		}
		// I02 loot policies
		for(final Entry<ItemLoot.Policy> e : ItemLoot.load().entries.values())
			add(out, e.path, "/base", "base", e.value.base);
		// I01/I04 item modifiers: pools, successors, operation affixes, recipes
		final ItemModifierRules.Library mods = ItemModifiers.check();
		for(final ItemModifierRules.Definition d : mods.definitions.values())
		{
			final String file = "itemmods/" + d.key() + ".json";
			try
			{
				if(d.kind.equals("base"))
				{
					final List<String> pool = d.list("pool");
					for(int i = 0; i < pool.size(); i++)
						add(out, file, "/pool/" + i, "affix", pool.get(i));
					if(d.raw.containsKey("successor"))
						add(out, file, "/successor", "base", d.string("successor"));
				}
				else
				if(d.kind.equals("operation"))
				{
					final List<String> affixes = d.list("affixes");
					for(int i = 0; i < affixes.size(); i++)
						add(out, file, "/affixes/" + i, "affix", affixes.get(i));
				}
				else
				if(d.kind.equals("recipe"))
				{
					final MiniJSON.JSONObject r = (MiniJSON.JSONObject)d.raw.get("result");
					add(out, file, "/result/catalog", "catalog-item", (String)r.get("catalog"));
					if(r.containsKey("base"))
						add(out, file, "/result/base", "base", (String)r.get("base"));
					if(r.containsKey("policy"))
						add(out, file, "/result/policy", "policy", (String)r.get("policy"));
				}
			}
			catch(final IOException x)
			{
				final Ref bad = new Ref(file, "/", "definition", d.key());
				bad.status = "error";
				bad.note = x.getMessage();
				out.add(bad);
			}
		}
		return out;
	}

	/** whether a mob or item carries scripts the check cannot follow */
	private static boolean scripted(final Physical P)
	{
		if(P == null)
			return false;
		if((P instanceof com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent) && (((com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent)P).numScripts() > 0))
			return true;
		for(final Enumeration<Ability> e = P.effects(); e.hasMoreElements();)
		{
			final Ability A = e.nextElement();
			if((A != null) && A.ID().toUpperCase().contains("SCRIPT"))
				return true;
		}
		if(P instanceof com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent)
		{
			for(final Enumeration<Behavior> e = ((com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent)P).behaviors(); e.hasMoreElements();)
			{
				final Behavior B = e.nextElement();
				if((B != null) && B.ID().toUpperCase().contains("SCRIPT"))
					return true;
			}
		}
		return false;
	}

	private static boolean composition(final CompositionDefinitions.Library comps, final String id, final String kind)
	{
		for(final CompositionDefinitions.Definition d : comps.definitions.values())
		{
			if(d.id.equals(id) && ((kind == null) || d.kind.equals(kind)))
				return true;
		}
		return false;
	}

	/** resolves every reference: ok, error (with what is missing) or unknown (with why it is not checked) */
	public static List<Ref> resolve()
	{
		final List<Ref> refs = collect();
		final File world = ModuleContent.world();
		final CompositionDefinitions.Library comps = CompositionDefinitions.load(world);
		final ModuleContent.Library<NamedResources.Pool> pools = NamedResources.load();
		final ModuleContent.Library<RewardEscrow.Def> rewards = RewardEscrow.load();
		final ModuleContent.Library<ItemLoot.Policy> policies = ItemLoot.load();
		final ItemModifierRules.Library mods = ItemModifiers.check();
		for(final Ref r : refs)
		{
			if(!r.status.equals("ok"))
				continue;
			try
			{
				if(r.kind.equals("ability"))
				{
					if(CMClass.getAbility(r.target) == null)
						fail(r, "no ability " + r.target);
				}
				else
				if(r.kind.equals("pool"))
				{
					if(!pools.has(r.target))
						fail(r, "no pool " + r.target + " is installed in pools/");
				}
				else
				if(r.kind.equals("composition"))
				{
					if(!composition(comps, r.target, null))
						fail(r, "no composition " + r.target + " is installed in compositions/");
				}
				else
				if(r.kind.equals("support-fit"))
				{
					CompositionDefinitions.Definition support = null;
					for(final CompositionDefinitions.Definition d : comps.definitions.values())
					{
						if(d.id.equals(r.target) && d.kind.equals("support"))
							support = d;
					}
					boolean fits = false;
					for(final CompositionDefinitions.Definition d : comps.definitions.values())
					{
						if(d.kind.equals("skill") && (support != null))
						{
							try
							{
								CompositionDefinitions.compile(d, Arrays.asList(support));
								fits = true;
							}
							catch(final IOException x)
							{
							}
						}
					}
					if(!fits)
						fail(r, "support " + r.target + " fits no installed skill");
				}
				else
				if(r.kind.equals("catalog-mob") || r.kind.equals("catalog-item"))
				{
					final Physical P = (CMLib.catalog() == null) ? null
						: r.kind.equals("catalog-mob") ? CMLib.catalog().getCatalogMob(r.target) : CMLib.catalog().getCatalogItem(r.target);
					if(P == null)
						fail(r, "no catalog " + (r.kind.equals("catalog-mob") ? "mob" : "item") + " '" + r.target + "'");
					else
					if(scripted(P))
						unknown(r, "'" + r.target + "' carries scripts; what they do is not checked");
				}
				else
				if(r.kind.equals("reward") || r.kind.equals("activity"))
				{
					final boolean activity = r.kind.equals("activity");
					boolean found = false;
					for(final Entry<RewardEscrow.Def> e : rewards.entries.values())
						found |= e.id.equals(r.target) && (e.value.activity == activity);
					if(!found)
						fail(r, "no " + (activity ? "activity " : "reward bundle ") + r.target + " in rewards/");
				}
				else
				if(r.kind.equals("room"))
				{
					if(CMLib.map().getRoom(r.target) == null)
						fail(r, "no room " + r.target);
				}
				else
				if(r.kind.equals("instance-area"))
				{
					final Area A = CMLib.map().getArea(r.target);
					if(A == null)
						fail(r, "no area " + r.target);
					else
					if(!(A instanceof AutoGenArea))
						fail(r, r.target + " is not a generated instance area (StdAutoGenInstance)");
					else
					{
						final String gen = A.getStat("GENERATIONFILEPATH");
						if(generatorScripted(gen))
							unknown(r, "its generator " + gen + " runs scripts; what they make is not checked");
					}
				}
				else
				if(r.kind.equals("base") || r.kind.equals("affix"))
					mods.find(r.target, 0, r.kind);
				else
				if(r.kind.equals("policy"))
				{
					if(!policies.has(r.target))
						fail(r, "no loot policy " + r.target + " in loot/");
				}
			}
			catch(final IOException x)
			{
				fail(r, x.getMessage());
			}
		}
		return refs;
	}

	private static void fail(final Ref r, final String why)
	{
		r.status = "error";
		r.note = why;
	}

	private static void unknown(final Ref r, final String why)
	{
		r.status = "unknown";
		r.note = why;
	}

	/** whether a generator file (resources/...) holds scripts */
	private static boolean generatorScripted(final String path)
	{
		if((path == null) || path.trim().length() == 0)
			return false;
		try
		{
			File f = new File(ModuleContent.world(), "resources/" + path);
			if(!f.exists())
				f = new File("resources/" + path);
			if(!f.exists())
				return false;
			final String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
			return text.contains("<SCRIPT") || text.contains("ONGENERATE") || text.contains("SCRIPT=") || text.contains("LLM");
		}
		catch(final IOException | RuntimeException e)
		{
			return false;
		}
	}

	/** the module a definition file belongs to, by its folder, or null */
	public static String moduleOf(final String file)
	{
		final String folder = (file.indexOf('/') < 0) ? file : file.substring(0, file.indexOf('/'));
		if(folder.equals("compositions"))
			return CompositionDefinitions.MODULE;
		if(folder.equals("itemmods"))
			return ItemModifiers.MODULE;
		for(final ModuleContent.Family f : ModuleContent.families())
		{
			if(f.folder().equals(folder))
				return f.module();
		}
		return null;
	}

	/** the problems, as WORLD CHECK findings: "file: /path: what is missing" */
	public static List<String> errors()
	{
		final List<String> out = new ArrayList<String>();
		for(final Ref r : resolve())
		{
			if(r.status.equals("error"))
				out.add(r.where() + ": " + r.note);
		}
		return out;
	}

	/** WORLD CHECK ONLY=REFS in words: the counts, each problem, each unknown */
	public static String report()
	{
		final List<Ref> refs = resolve();
		int ok = 0, bad = 0, unknown = 0;
		final Map<String, Integer> byKind = new TreeMap<String, Integer>();
		for(final Ref r : refs)
		{
			byKind.put(r.kind, Integer.valueOf(byKind.containsKey(r.kind) ? byKind.get(r.kind).intValue() + 1 : 1));
			if(r.status.equals("ok"))
				ok++;
			else
			if(r.status.equals("error"))
				bad++;
			else
				unknown++;
		}
		final StringBuilder s = new StringBuilder("References between the module families: ").append(refs.size()).append(" followed, ")
			.append(ok).append(" found, ").append(bad).append(" broken, ").append(unknown).append(" unknown.");
		if(!byKind.isEmpty())
		{
			s.append("\nBy kind:");
			for(final Map.Entry<String, Integer> e : byKind.entrySet())
				s.append(' ').append(e.getKey()).append(' ').append(e.getValue()).append(';');
		}
		for(final Ref r : refs)
		{
			if(r.status.equals("error"))
				s.append("\nERROR ").append(r.where()).append(": ").append(r.note);
		}
		for(final Ref r : refs)
		{
			if(r.status.equals("unknown"))
				s.append("\nUNKNOWN ").append(r.where()).append(": ").append(r.note);
		}
		return s.toString();
	}
}
