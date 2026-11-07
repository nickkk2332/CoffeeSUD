package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Pure, bounded authoring schema and deterministic modifier compiler. No live actors or global combat changes. */
public final class CompositionDefinitions
{
	private CompositionDefinitions()
	{
	}
	public static final String MODULE = "abilities.composition";
	private static final MathContext NUMBERS = MathContext.DECIMAL128;
	private static BigDecimal decimal(final double value)
	{
		return BigDecimal.valueOf(value);
	}
	public static final List<String> TYPES = Collections.unmodifiableList(Arrays.asList("physical", "fire", "cold", "electric", "acid"));
	public static final Map<String,Double> DEFAULTS;
	static
	{
		final Map<String,Double> d = new LinkedHashMap<String,Double>();
		d.put("mana", 0.0);
		d.put("movement", 0.0);
		d.put("actions", 1.0);
		d.put("cooldown", 0.0);
		d.put("range", 5.0);
		d.put("chains", 0.0);
		d.put("burn_damage", 0.0);
		d.put("burn_ticks", 0.0);
		d.put("burn_chance", 100.0);
		DEFAULTS = Collections.unmodifiableMap(d);
	}
	private static double number(final Object raw, final String where, final double min, final double max) throws IOException
	{
		if(!(raw instanceof Number))
			throw new IOException(where + " must be a number");
		final double n = ((Number)raw).doubleValue();
		if(Double.isNaN(n) || Double.isInfinite(n) || n < min || n > max)
			throw new IOException(where + " must be in " + min + ".." + max);
		return n;
	}
	private static String string(final JSONObject obj, final String key, final String fallback) throws IOException
	{
		return obj.containsKey(key) ? ModuleDefinitions.string(obj.get(key), key) : fallback;
	}
	private static Set<String> tags(final JSONObject obj, final String key) throws IOException
	{
		final Set<String> out = new LinkedHashSet<String>();
		if(obj.containsKey(key))
			for(final Object raw : ModuleDefinitions.array(obj.get(key), key))
			{
				final String tag = ModuleDefinitions.id(raw);
				if(!out.add(tag))
					throw new IOException(key + ": duplicate " + tag);
			}
		if(out.size() > 32)
			throw new IOException(key + ": at most 32 entries");
		return Collections.unmodifiableSet(out);
	}
	private static boolean damage(final String stat)
	{
		return stat.equals("damage") || stat.startsWith("damage.");
	}
	private static void stat(final String name) throws IOException
	{
		if(!DEFAULTS.containsKey(name) && !name.equals("damage") && !(name.startsWith("damage.") && TYPES.contains(name.substring(7))))
			throw new IOException("unknown modifier stat " + name);
	}
	public static final class Modifier
	{
		public final String source, stat, op, from, to, condition;
		public final double value;
		public final Set<String> when;
		Modifier(final JSONObject obj, final String source) throws IOException
		{
			ModuleDefinitions.keys(obj, "modifier", "stat", "op", "value", "from", "to", "when_tags", "condition");
			this.source = source;
			op = ModuleDefinitions.string(obj.get("op"), "op");
			stat = string(obj, "stat", "damage");
			stat(stat);
			condition = string(obj, "condition", "always");
			when = tags(obj, "when_tags");
			if(!Arrays.asList("always", "target_wounded", "target_burning").contains(condition))
				throw new IOException("unknown modifier condition " + condition);
			if(!condition.equals("always") && !damage(stat) && !stat.equals("burn_damage"))
				throw new IOException("target conditions are only allowed on damage and burn_damage");
			from = string(obj, "from", "");
			to = string(obj, "to", "");
			if(op.equals("convert"))
			{
				if(!stat.equals("damage") || !TYPES.contains(from) || !TYPES.contains(to) || from.equals(to) || !condition.equals("always"))
					throw new IOException("convert needs damage, distinct from/to damage types, and no target condition");
				value = number(obj.get("value"), "conversion percent", 0, 100);
			}
			else
			{
				if(!from.isEmpty() || !to.isEmpty())
					throw new IOException("from/to are only valid for convert");
				if(!Arrays.asList("flat", "increased", "more", "floor", "cap").contains(op))
					throw new IOException("unknown modifier operation " + op);
				if(stat.equals("damage") && op.equals("flat"))
					throw new IOException("flat damage must name a damage type");
				value = number(obj.get("value"), "modifier value", op.equals("increased") || op.equals("more") ? -100 : op.equals("flat") ? -100000
					: 0,
					op.equals("increased") ? 10000 : op.equals("more") ? 1000 : 100000);
			}
		}
		boolean active(final Set<String> tags, final Set<String> conditions)
		{
			return tags.containsAll(when) && (condition.equals("always") || conditions.contains(condition));
		}
		@Override public String toString()
		{
			return source + ": " + stat + " " + op + " " + value + (op.equals("convert") ? "% " + from + " -> " + to : "")
				+ (when.isEmpty() ? "" : " when tags " + when) + (condition.equals("always") ? "" : " when " + condition);
		}
	}
	public static final class Definition
	{
		public final String id, name, kind, fingerprint, path, requiresAbility, mask, group;
		public final int version;
		public final Set<String> tags, all, any, none, adds;
		public final Map<String,Double> base;
		public final List<Modifier> modifiers;
		/** A15: named resources the skill or support costs and gains, by pool ID */
		public final Map<String,Long> resourceCost, resourceGain;
		public Definition(final JSONObject obj, final String path) throws IOException
		{
			ModuleDefinitions.keys(obj, "composition definition", "schema", "id", "version", "name", "kind", "tags", "requires_ability", "mask", "stats", "damage", "modifiers", "requires_all", "requires_any", "excludes_tags", "adds_tags", "group", "resources");
			if(ModuleDefinitions.integer(obj.get("schema"), "schema") != 1)
				throw new IOException("unsupported composition schema");
			id = ModuleDefinitions.id(obj.get("id"));
			version = ModuleDefinitions.integer(obj.get("version"), "version");
			if(version < 1)
				throw new IOException("version must be positive");
			name = ModuleDefinitions.string(obj.get("name"), "name");
			if(name.isEmpty() || name.length() > 80 || name.matches("(?s).*[\\x00-\\x1f^<>].*"))
				throw new IOException("name must be 1..80 plain text characters");
			kind = ModuleDefinitions.string(obj.get("kind"), "kind");
			if(!kind.equals("skill") && !kind.equals("support"))
				throw new IOException("kind must be skill or support");
			requiresAbility = string(obj, "requires_ability", "");
			mask = string(obj, "mask", "");
			if(requiresAbility.length() > 100 || mask.length() > 512)
				throw new IOException("ability/mask exceeds its size limit");
			tags = tags(obj, "tags");
			all = tags(obj, "requires_all");
			any = tags(obj, "requires_any");
			none = tags(obj, "excludes_tags");
			adds = tags(obj, "adds_tags");
			group = obj.containsKey("group") ? ModuleDefinitions.id(obj.get("group")) : id;
			final Map<String,Double> values = new LinkedHashMap<String,Double>(DEFAULTS);
			for(final String type : TYPES)
				values.put("damage." + type, 0.0);
			if(kind.equals("skill"))
			{
				if(tags.contains("spell") == tags.contains("attack"))
					throw new IOException("a skill needs exactly one of the spell/attack tags");
				for(final String key : Arrays.asList("requires_all", "requires_any", "excludes_tags", "adds_tags", "group"))
					if(obj.containsKey(key))
						throw new IOException(key + " is for supports");
				final JSONObject stats = obj.containsKey("stats") ? ModuleDefinitions.object(obj.get("stats"), "stats") : new JSONObject();
				for(final String key : stats.keySet())
				{
					if(!DEFAULTS.containsKey(key))
						throw new IOException("unknown base stat " + key);
					final double n = number(stats.get(key), key, 0, limit(key));
					if(key.equals("actions") && n < .25)
						throw new IOException("actions must be at least 0.25");
					values.put(key, n);
				}
				final JSONObject dmg = ModuleDefinitions.object(obj.get("damage"), "damage");
				for(final String key : dmg.keySet())
				{
					if(!TYPES.contains(key))
						throw new IOException("unknown damage type " + key);
					values.put("damage." + key, number(dmg.get(key), key, 0, 100000));
				}
			}
			else
				for(final String key : Arrays.asList("tags", "stats", "damage"))
					if(obj.containsKey(key))
						throw new IOException(key + " is for skills");
			if(adds.contains("attack") || adds.contains("spell"))
				throw new IOException("supports cannot add the reserved attack/spell tags");
			base = Collections.unmodifiableMap(values);
			final List<Modifier> mods = new ArrayList<Modifier>();
			if(obj.containsKey("modifiers"))
				for(final Object raw : ModuleDefinitions.array(obj.get("modifiers"), "modifiers"))
					mods.add(new Modifier(ModuleDefinitions.object(raw, "modifier"), id + " v" + version));
			if(mods.size() > 32)
				throw new IOException("at most 32 modifiers per definition");
			modifiers = Collections.unmodifiableList(mods);
			this.path = path;
			final Map<String,Long> cost = new TreeMap<String,Long>(), gain = new TreeMap<String,Long>();
			if(obj.containsKey("resources"))
			{
				final JSONObject res = ModuleDefinitions.object(obj.get("resources"), "resources");
				ModuleDefinitions.keys(res, "resources", "cost", "gain");
				for(final String which : Arrays.asList("cost", "gain"))
				{
					if(!res.containsKey(which))
						continue;
					final JSONObject amounts = ModuleDefinitions.object(res.get(which), "resources." + which);
					if(amounts.size() > 8)
						throw new IOException("resources." + which + ": at most 8 pools");
					for(final String pool : amounts.keySet())
					{
						final int n = ModuleDefinitions.integer(amounts.get(pool), "resources." + which + "." + pool);
						if(n < 1 || n > 1000000)
							throw new IOException("resources." + which + "." + pool + " must be from 1 to 1000000");
						(which.equals("cost") ? cost : gain).put(ModuleDefinitions.id(pool), Long.valueOf(n));
					}
				}
			}
			resourceCost = Collections.unmodifiableMap(cost);
			resourceGain = Collections.unmodifiableMap(gain);
			try
			{
				final byte[] bytes = MessageDigest.getInstance("SHA-256").digest(ModuleDefinitions.json(obj).getBytes(StandardCharsets.UTF_8));
				final StringBuilder hash = new StringBuilder();
				for(final byte b : bytes)
					hash.append(String.format("%02x", Integer.valueOf(b & 255)));
				fingerprint = hash.toString();
			}
			catch(final Exception e)
			{
				throw new IOException(e);
			}
		}
		public String key()
		{
			return id + ".v" + version;
		}
	}
	private static double limit(final String stat)
	{
		if(stat.equals("actions"))
			return 10;
		if(stat.equals("chains"))
			return 8;
		if(stat.equals("range"))
			return 10;
		if(stat.equals("burn_ticks"))
			return 20;
		if(stat.equals("burn_chance"))
			return 100;
		if(stat.equals("cooldown"))
			return 1000;
		if(stat.equals("mana") || stat.equals("movement") || stat.equals("burn_damage"))
			return 10000;
		return 100000;
	}
	private static String display(final double value)
	{
		return java.math.BigDecimal.valueOf(value).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
	}
	private static String display(final Map<String,Double> values)
	{
		final List<String> parts = new ArrayList<String>();
		for(final Map.Entry<String,Double> value : values.entrySet())
			parts.add(value.getKey() + "=" + display(value.getValue()));
		return String.join(", ", parts);
	}
	public static final class Library
	{
		public final Map<String,Definition> definitions = new TreeMap<String,Definition>();
		public final List<String> errors = new ArrayList<String>();
		public Definition latest(final String id, final String kind) throws IOException
		{
			Definition result = null;
			for(final Definition def : definitions.values())
				if(def.id.equals(id) && def.kind.equals(kind) && (result == null || def.version > result.version))
					result = def;
			if(result == null)
				throw new IOException("unknown " + kind + " " + id);
			return result;
		}
		public Definition pinned(final JSONObject pin, final String kind) throws IOException
		{
			ModuleDefinitions.keys(pin, "definition pin", "id", "version", "hash");
			final String id = ModuleDefinitions.id(pin.get("id"));
			final int version = ModuleDefinitions.integer(pin.get("version"), "pinned version");
			final Definition def = definitions.get(id + ".v" + version);
			if(def == null || !def.kind.equals(kind))
				throw new IOException(id + " v" + version + " is unavailable; this binding is suspended");
			if(!def.fingerprint.equals(pin.get("hash")))
				throw new IOException(id + " v" + version + " changed without a new version; this binding is suspended");
			return def;
		}
		public String report()
		{
			return errors.isEmpty() ? "Composition definitions are valid (" + definitions.size() + " versions)." : String.join("\n", errors);
		}
	}
	public static JSONObject pin(final Definition def)
	{
		final JSONObject out = new JSONObject();
		out.put("id", def.id);
		out.put("version", Long.valueOf(def.version));
		out.put("hash", def.fingerprint);
		return out;
	}
	public static Library load(final File world)
	{
		final Library lib = new Library();
		final File dir = new File(world, "compositions");
		if(Files.isSymbolicLink(dir.toPath()))
		{
			lib.errors.add("compositions/: directory must not be a symbolic link");
			return lib;
		}
		if(dir.exists() && !dir.isDirectory())
			lib.errors.add("compositions/: must be a directory");
		final File[] files = dir.listFiles(f -> f.getName().endsWith(".json"));
		if(files == null)
			return lib;
		Arrays.sort(files, Comparator.comparing(File::getName));
		if(files.length > 256)
			lib.errors.add("compositions/: at most 256 definitions");
		for(int i = 0; i < Math.min(files.length, 256); i++)
		{
			final File file = files[i];
			final String path = "compositions/" + file.getName();
			try
			{
				if(!Files.isRegularFile(file.toPath()) || Files.isSymbolicLink(file.toPath())
					|| !file.getCanonicalPath().startsWith(world.getCanonicalPath() + File.separator))
					throw new IOException("must be a regular file inside the world");
				if(file.length() > 65536)
					throw new IOException("definition exceeds 64 KiB");
				final Definition def = new Definition(ModuleDefinitions
					.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)), path);
				if(!file.getName().equals(def.key() + ".json"))
					throw new IOException("filename must be " + def.key() + ".json");
				if(lib.definitions.put(def.key(), def) != null)
					throw new IOException("duplicate definition " + def.key());
				if(def.kind.equals("skill"))
					compile(def, Collections.<Definition>emptyList());
			}
			catch(final Exception e)
			{
				lib.errors.add(path + ": " + e.getMessage());
			}
		}
		return lib;
	}
	public static final class Compiled
	{
		public final Definition skill;
		public final List<Definition> supports;
		public final Set<String> tags;
		public final List<Modifier> modifiers;
		private Compiled(final Definition skill, final List<Definition> supports, final Set<String> tags, final List<Modifier> modifiers)
		{
			this.skill = skill;
			this.supports = Collections.unmodifiableList(new ArrayList<Definition>(supports));
			this.tags = Collections.unmodifiableSet(tags);
			this.modifiers = Collections.unmodifiableList(modifiers);
		}
		public Map<String,Double> values(final Set<String> conditions) throws IOException
		{
			final Map<String,BigDecimal> values = new LinkedHashMap<String,BigDecimal>();
			for(final Map.Entry<String,Double> entry : skill.base.entrySet())
				values.put(entry.getKey(), decimal(entry.getValue()));
			final List<Modifier> active = new ArrayList<Modifier>();
			for(final Modifier mod : modifiers)
				if(mod.active(tags, conditions))
					active.add(mod);
			// Flats precede one simultaneous conversion pass. Converted pools then receive destination bonuses.
			for(final Modifier mod : active)
				if(mod.op.equals("flat"))
					values.put(mod.stat, values.get(mod.stat).add(decimal(mod.value), NUMBERS));
			final Map<String,BigDecimal> before = new HashMap<String,BigDecimal>(values);
			for(final Modifier mod : active)
				if(mod.op.equals("convert"))
				{
					final BigDecimal amount = before.get("damage." + mod.from).max(BigDecimal.ZERO)
						.multiply(decimal(mod.value).movePointLeft(2), NUMBERS);
					values.put("damage." + mod.from, values.get("damage." + mod.from).subtract(amount, NUMBERS));
					values.put("damage." + mod.to, values.get("damage." + mod.to).add(amount, NUMBERS));
				}
			final Map<String,Double> result = new LinkedHashMap<String,Double>();
			for(final String key : values.keySet())
			{
				BigDecimal increased = BigDecimal.ZERO, more = BigDecimal
					.ONE, floor = decimal(key.equals("actions") ? .25 : 0), cap = decimal(limit(key));
				for(final Modifier mod : active)
					if(mod.stat.equals(key) || key.startsWith("damage.") && mod.stat.equals("damage"))
					{
						if(mod.op.equals("increased"))
							increased = increased.add(decimal(mod.value), NUMBERS);
						if(mod.op.equals("more"))
							more = more.multiply(BigDecimal.ONE.add(decimal(mod.value).movePointLeft(2), NUMBERS), NUMBERS);
						if(mod.op.equals("floor"))
							floor = floor.max(decimal(mod.value));
						if(mod.op.equals("cap"))
							cap = cap.min(decimal(mod.value));
					}
				if(floor.compareTo(cap) > 0)
					throw new IOException(key + ": floor exceeds cap");
				final BigDecimal scaled = values.get(key).max(BigDecimal.ZERO)
					.multiply(BigDecimal.ONE.add(increased.movePointLeft(2), NUMBERS).max(BigDecimal.ZERO), NUMBERS).multiply(more, NUMBERS);
				// A damage floor raises a type the hit deals; it never adds a type the hit lacks.
				final double n = (key.startsWith("damage.") && scaled.signum() <= 0 ? scaled : scaled.max(floor)).min(cap).doubleValue();
				if(!Double.isFinite(n))
					throw new IOException(key + ": non-finite result");
				result.put(key, n);
			}
			return Collections.unmodifiableMap(result);
		}
		public double value(final String key) throws IOException
		{
			return values(Collections.<String>emptySet()).get(key);
		}
		/** A15: the named resources the cast costs, the skill's and its supports' together */
		public Map<String,Long> resourceCosts()
		{
			return sum(true);
		}
		/** A15: the named resources the cast gains once it resolves, the skill's then its supports' */
		public Map<String,Long> resourceGains()
		{
			return sum(false);
		}
		private Map<String,Long> sum(final boolean costs)
		{
			final Map<String,Long> out = new TreeMap<String,Long>();
			final List<Definition> all = new ArrayList<Definition>();
			all.add(skill);
			all.addAll(supports);
			for(final Definition d : all)
				for(final Map.Entry<String,Long> e : (costs ? d.resourceCost : d.resourceGain).entrySet())
					out.put(e.getKey(), Long.valueOf((out.containsKey(e.getKey()) ? out.get(e.getKey()).longValue() : 0) + e.getValue().longValue()));
			return out;
		}
		/** Numeric values before engine proficiency, saves, criticals, and damage adjustments. */
		public String describe() throws IOException
		{
			return describeDamage(null, "");
		}
		public String describeDamage(final Map<String,Double> damage, final String model) throws IOException
		{
			final StringBuilder out = new StringBuilder(skill.name + " (" + skill.key() + ")\nTags: " + tags + "\nSupports:");
			for(final Definition support : supports)
				out.append(" ").append(support.key());
			final Map<String,Double> result = new LinkedHashMap<String,Double>(values(Collections.<String>emptySet()));
			if(damage != null)
			{
				for(final String type : TYPES)
					result.put("damage." + type, damage.get(type));
				out.append("\n").append(model);
			}
			out.append("\nBase: ").append(display(skill.base)).append("\nUnconditional result: ").append(display(result));
			out.append("\nCast boundary: ").append((int)Math.ceil(result.get("mana"))).append(" mana, ")
				.append((int)Math.ceil(result.get("movement")))
				.append(" movement, ").append(display(result.get("actions"))).append(" actions, ").append((int)Math.ceil(result.get("cooldown")))
				.append(" cooldown ticks.");
			if(!resourceCosts().isEmpty())
				out.append("\nResource costs, all checked before anything is spent: ").append(resourceCosts());
			if(!resourceGains().isEmpty())
				out.append("\nResource gains, once the cast resolves: ").append(resourceGains());
			out.append("\nTarget conditions are evaluated separately for each hit; chained targets are unique, in the same room.");
			for(final Modifier mod : modifiers)
				out.append("\n  ").append(mod);
			return out.toString();
		}
	}
	public static Compiled compile(final Definition skill, final List<Definition> supports) throws IOException
	{
		if(!skill.kind.equals("skill"))
			throw new IOException("a skill is required");
		if(supports.size() > 3)
			throw new IOException("at most three supports");
		final Set<String> groups = new HashSet<String>(), ids = new HashSet<String>();
		final Set<String> tags = new LinkedHashSet<String>(skill.tags);
		final List<Modifier> mods = new ArrayList<Modifier>(skill.modifiers);
		for(final Definition support : supports)
		{
			if(!support.kind.equals("support"))
				throw new IOException("a support is required");
			if(!ids.add(support.id))
				throw new IOException("duplicate support ID " + support.id);
			if(!groups.add(support.group))
				throw new IOException("duplicate support group " + support.group);
			if(!skill.tags.containsAll(support.all) || !support.any.isEmpty() && Collections.disjoint(skill.tags, support.any)
				|| !Collections.disjoint(skill.tags, support.none))
				throw new IOException(support.id + " is incompatible with " + skill.id + " tags " + skill.tags);
			tags.addAll(support.adds);
			mods.addAll(support.modifiers);
		}
		final Map<String,BigDecimal> conversions = new HashMap<String,BigDecimal>();
		for(final Modifier mod : mods)
			if(mod.op.equals("convert") && mod.active(tags, Collections.<String>emptySet()))
			{
				final BigDecimal total = (conversions.containsKey(mod.from) ? conversions.get(mod.from) : BigDecimal.ZERO)
					.add(decimal(mod.value), NUMBERS);
				if(total.compareTo(BigDecimal.valueOf(100)) > 0)
					throw new IOException("conversion exceeds 100% of " + mod.from);
				conversions.put(mod.from, total);
			}
		final Compiled compiled = new Compiled(skill, supports, tags, mods);
		// All supported dynamic conditions must also obey cap/floor contracts.
		compiled.values(Collections.<String>emptySet());
		compiled.values(new HashSet<String>(Arrays.asList("target_wounded", "target_burning")));
		return compiled;
	}
}
