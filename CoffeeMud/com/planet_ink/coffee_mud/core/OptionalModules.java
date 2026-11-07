package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.ModuleDefinitions.*;
import com.planet_ink.coffee_mud.core.save.*;
import com.planet_ink.coffee_mud.core.interfaces.Physical;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Common.interfaces.Clan;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Optional module activation, typed scoped campaign state, and non-destructive version transitions. */
public final class OptionalModules
{
	private OptionalModules()
	{
	}
	public static final String FILE = "modules.json";
	private static final int MAX_STATE_BYTES = 8 * 1024 * 1024;
	private static Campaign current = null;

	public static final class Owner
	{
		public final String scope, key;
		private Owner(final String scope, final String key) throws IOException
		{
			if(!ModuleDefinitions.SCOPES.contains(scope) || key == null || key.isEmpty() || key.length() > 512
				|| key.matches("(?s).*[\\x00-\\x1f].*"))
				throw new IOException("invalid module state owner");
			this.scope = scope;
			this.key = key;
		}
		String index()
		{
			return scope + "\n" + key;
		}
	}
	public static Owner world() throws IOException
	{
		return new Owner("world", "world");
	}
	public static Owner character(final MOB mob) throws IOException
	{
		if(mob == null)
			throw new IOException("a character owner is required");
		return new Owner("character", InstanceIds.reference(mob));
	}
	public static Owner item(final Item item) throws IOException
	{
		if(item == null)
			throw new IOException("an item owner is required");
		return new Owner("item", InstanceIds.reference(item));
	}
	public static Owner profile(final MOB mob) throws IOException
	{
		if(Party.profileOf(mob) == null)
			throw new IOException("this character has no profile");
		return new Owner("profile", "@profile:" + Party.profileOf(mob).getAccountName());
	}
	public static Owner organization(final Clan clan) throws IOException
	{
		if(clan == null)
			throw new IOException("an organization owner is required");
		return new Owner("organization", "@organization:" + clan.clanID());
	}
	/** Relationships are ordered: the first and second participant retain their distinct roles. */
	public static Owner relationship(final Physical first, final Physical second) throws IOException
	{
		if(!(first instanceof MOB || first instanceof Item) || !(second instanceof MOB || second instanceof Item) || first == second)
			throw new IOException("two distinct character/item relationship participants are required");
		return new Owner("relationship", ModuleDefinitions.json(new Object[] { InstanceIds.reference(first), InstanceIds.reference(second) }));
	}
	/** For extension adapters that already have persistent IDs, not display names. */
	public static Owner owner(final String scope, final String persistentKey) throws IOException
	{
		final Owner result = new Owner(scope, persistentKey);
		if(scope.equals("world") && !persistentKey.equals("world") || (scope.equals("character") || scope.equals("item"))
			&& !persistentKey.startsWith(InstanceIds.PREFIX)
			|| scope.equals("profile") && !persistentKey.startsWith("@profile:") || scope.equals("organization")
			&& !persistentKey.startsWith("@organization:"))
			throw new IOException("owner key does not match scope " + scope);
		if(scope.equals("relationship"))
		{
			try
			{
				final Object[] ids = ModuleDefinitions.array(new MiniJSON().parse(persistentKey), "relationship owner");
				if(ids.length != 2 || !ModuleDefinitions.string(ids[0], "participant").startsWith("@")
					|| !ModuleDefinitions.string(ids[1], "participant").startsWith("@") || ids[0].equals(ids[1]))
					throw new IOException("invalid relationship participants");
			}
			catch(final Exception e)
			{
				throw new IOException("invalid relationship owner", e);
			}
		}
		return result;
	}
	private static final class Capsule
	{
		final String id;
		final String requiredHandler;
		int version;
		String fingerprint, removal, problem = "";
		JSONObject settings;
		final Map<String,JSONObject> records = new TreeMap<String,JSONObject>();
		/** A capsule whose saved data did not read: kept exactly as saved, and suspended. */
		private JSONObject damaged = null;
		Capsule(final Definition def, final JSONObject configured) throws IOException
		{
			id = def.id;
			requiredHandler = ModuleDefinitions.nativeHandler(def.handler) ? def.handler : "";
			version = def.version;
			fingerprint = def.fingerprint;
			removal = def.removal;
			settings = ModuleDefinitions.copy(configured);
		}
		Capsule(final JSONObject saved) throws IOException
		{
			ModuleDefinitions.keys(saved, "saved module", "id", "version", "fingerprint", "on_disable", "settings", "records", "requires_handler");
			id = ModuleDefinitions.id(saved.get("id"));
			version = ModuleDefinitions.integer(saved.get("version"), "saved module version");
			requiredHandler = handler(saved, id);
			fingerprint = ModuleDefinitions.string(saved.get("fingerprint"), "saved fingerprint");
			if(!fingerprint.matches("[0-9a-f]{64}"))
				throw new IOException(id + ": invalid saved fingerprint");
			removal = policy(saved, id);
			settings = ModuleDefinitions.copy(ModuleDefinitions.object(saved.get("settings"), "saved settings"));
			for(final Object raw : ModuleDefinitions.array(saved.get("records"), "saved records"))
			{
				final JSONObject record = ModuleDefinitions.copy(ModuleDefinitions.object(raw, "saved record"));
				ModuleDefinitions.keys(record, "saved record", "scope", "owner", "values");
				final Owner who = owner(ModuleDefinitions.string(record.get("scope"), "scope"), ModuleDefinitions
					.string(record.get("owner"), "owner"));
				ModuleDefinitions.object(record.get("values"), "record values");
				if(records.put(who.index(), record) != null)
					throw new IOException(id + ": duplicate state owner");
				if(records.size() > ModuleDefinitions.MAX_RECORDS)
					throw new IOException(id + ": too many records");
			}
		}
		/** Damaged saved data under a suspend policy: the saved object is written back as it was. */
		Capsule(final JSONObject saved, final String id, final String problem) throws IOException
		{
			this.id = id;
			requiredHandler = handler(saved, id);
			removal = policy(saved, id);
			fingerprint = "";
			version = saved.get("version") instanceof Number ? ((Number)saved.get("version")).intValue() : 0;
			settings = new JSONObject();
			damaged = ModuleDefinitions.copy(saved);
			this.problem = problem;
		}
		/** A native capability the save needs: refused whatever the policy, as older readers refuse it. */
		static String handler(final JSONObject saved, final String id) throws IOException
		{
			final String required = saved.containsKey("requires_handler")
				? ModuleDefinitions.string(saved.get("requires_handler"), "requires_handler") : ModuleDefinitions.nativeHandler(id) ? id : "";
			if(saved.containsKey("requires_handler") && !ModuleDefinitions.nativeHandler(required)
				|| ModuleDefinitions.nativeHandler(id) && !id.equals(required))
				throw new IOException(id + ": unavailable native module handler " + required);
			return required;
		}
		static String policy(final JSONObject saved, final String id) throws IOException
		{
			final String removal = ModuleDefinitions.string(saved.get("on_disable"), "saved on_disable");
			if(!Arrays.asList("suspend", "refuse").contains(removal))
				throw new IOException(id + ": invalid saved on_disable policy");
			return removal;
		}
		JSONObject saved() throws IOException
		{
			if(damaged != null)
				return ModuleDefinitions.copy(damaged);
			final JSONObject out = new JSONObject();
			out.put("id", id);
			out.put("version", Long.valueOf(version));
			if(!requiredHandler.isEmpty())
				out.put("requires_handler", requiredHandler);
			out.put("fingerprint", fingerprint);
			out.put("on_disable", removal);
			out.put("settings", settings);
			out.put("records", records.values().toArray(new Object[0]));
			return out;
		}
	}
	/** A staged campaign can be validated/migrated before replacing the running campaign. */
	public static final class Campaign
	{
		public final Plan plan;
		private final Map<String,Capsule> capsules = new TreeMap<String,Capsule>();
		private final List<String> notes = new ArrayList<String>();
		/** What only a creator can act on (adoption, pinned settings): kept from an ordinary player's report. */
		private final List<String> creatorNotes = new ArrayList<String>();
		private int stateBytes, recordCount;
		private Campaign(final Plan plan)
		{
			this.plan = plan;
		}

		private void account() throws IOException
		{
			stateBytes = 0;
			recordCount = 0;
			for(final Capsule capsule : capsules.values())
				for(final JSONObject record : capsule.records.values())
				{
					stateBytes += ModuleDefinitions.json(record).getBytes(StandardCharsets.UTF_8).length;
					recordCount++;
				}
			if(stateBytes > MAX_STATE_BYTES || recordCount > ModuleDefinitions.MAX_RECORDS)
				throw new IOException("module campaign state exceeds its storage budget");
		}
		private Capsule require(final String id) throws IOException
		{
			final String problem = problem(id);
			if(!problem.isEmpty())
				throw new IOException(id + ": " + problem);
			return capsules.get(id);
		}
		public synchronized String problem(final String id)
		{
			final Capsule saved = capsules.get(id);
			if(saved == null)
				return plan.enabled.contains(id) ? "not adopted by this campaign" : "disabled by the world";
			if(!saved.problem.isEmpty())
				return saved.problem;
			final Definition def = plan.definitions.get(id);
			if(def == null)
				return "definition unavailable";
			final String clock = GameTime.isTurnBased() ? "TURNS" : "REALTIME";
			for(final String required : closure(id))
			{
				final Definition part = plan.definitions.get(required);
				if(part != null && !part.modes.contains(clock))
					return "clock " + clock + " is not supported by " + required;
			}
			return "";
		}
		private Set<String> closure(final String id)
		{
			final Set<String> result = new LinkedHashSet<String>();
			addClosure(id, result);
			return result;
		}
		private void addClosure(final String id, final Set<String> result)
		{
			if(!result.add(id))
				return;
			final Definition def = plan.definitions.get(id);
			if(def != null)
				for(final String dep : def.requires.keySet())
					addClosure(dep, result);
		}
		public synchronized Object setting(final String id, final String key) throws IOException
		{
			final Capsule capsule = require(id);
			if(!capsule.settings.containsKey(key))
				throw new IOException(id + ": unknown setting " + key);
			return capsule.settings.get(key);
		}
		/** Explicit creator action changes only this campaign's setting, after typed validation. */
		public synchronized void configure(final String id, final String key, final String text) throws IOException
		{
			final Capsule capsule = require(id);
			final Field field = plan.definitions.get(id).settings.get(key);
			if(field == null)
				throw new IOException(id + ": unknown setting " + key);
			Object value = text;
			if(!field.type.equals("string"))
			{
				final JSONObject literal = ModuleDefinitions.parse("{\"value\":" + text + "}");
				ModuleDefinitions.keys(literal, "setting value", "value");
				value = literal.get("value");
			}
			final JSONObject next = ModuleDefinitions.copy(capsule.settings);
			next.put(key, field.checked(value, id + " settings." + key));
			capsule.settings = next;
		}
		public synchronized JSONObject get(final String id, final Owner who) throws IOException
		{
			final Capsule capsule = require(id);
			final Definition def = plan.definitions.get(id);
			if(!def.scopes.contains(who.scope))
				throw new IOException(id + ": unsupported owner scope " + who.scope);
			final JSONObject record = capsule.records.get(who.index());
			return record == null ? ModuleDefinitions.values(def.state, new JSONObject(), id + " state")
				: ModuleDefinitions.copy((JSONObject)record.get("values"));
		}
		/** Q05: what each module keeps for one owner, by module ID: copies, read-only; nothing is allocated. */
		public synchronized Map<String,JSONObject> stored(final Owner who)
		{
			final Map<String,JSONObject> out = new TreeMap<String,JSONObject>();
			for(final Capsule capsule : capsules.values())
			{
				final JSONObject record = capsule.records.get(who.index());
				if(record != null)
					try
					{
						out.put(capsule.id, ModuleDefinitions.copy((JSONObject)record.get("values")));
					}
					catch(final IOException e)
					{
						out.put(capsule.id, new JSONObject());
					}
			}
			return out;
		}
		/** Replace one complete typed record atomically. Reads do not allocate persistent records. */
		public synchronized void put(final String id, final Owner who, final JSONObject values) throws IOException
		{
			final Capsule capsule = require(id);
			final Definition def = plan.definitions.get(id);
			if(!def.scopes.contains(who.scope))
				throw new IOException(id + ": unsupported owner scope " + who.scope);
			final JSONObject next = new JSONObject();
			next.put("scope", who.scope);
			next.put("owner", who.key);
			next.put("values", ModuleDefinitions.values(def.state, values, id + " state"));
			final JSONObject old = capsule.records.get(who.index());
			final int delta = ModuleDefinitions.json(next).getBytes(StandardCharsets.UTF_8)
				.length - (old == null ? 0 : ModuleDefinitions.json(old).getBytes(StandardCharsets.UTF_8).length);
			if(stateBytes + delta > MAX_STATE_BYTES || old == null && recordCount >= ModuleDefinitions.MAX_RECORDS)
				throw new IOException("module campaign state storage budget exhausted");
			capsule.records.put(who.index(), next);
			stateBytes += delta;
			if(old == null)
				recordCount++;
		}
		public synchronized String snapshot() throws IOException
		{
			final JSONObject root = new JSONObject();
			root.put("schema", Long.valueOf(ModuleDefinitions.SCHEMA));
			final List<JSONObject> modules = new ArrayList<JSONObject>();
			for(final Capsule capsule : capsules.values())
				modules.add(capsule.saved());
			root.put("modules", modules.toArray(new Object[0]));
			final String text = ModuleDefinitions.json(root);
			if(text.getBytes(StandardCharsets.UTF_8).length > 2 * MAX_STATE_BYTES)
				throw new IOException("module snapshot exceeds its storage budget");
			return text;
		}
		public synchronized List<String> notes(final boolean creator)
		{
			final List<String> result = new ArrayList<String>(notes);
			if(creator)
				result.addAll(creatorNotes);
			return result;
		}
		/** Explicit adoption is limited to this world's selected dependency closure. No reset of suspended data. */
		public synchronized void adopt(final String id) throws IOException
		{
			if(!plan.enabled.contains(id))
				throw new IOException(id + ": not selected by this world");
			final Set<String> wanted = closure(id);
			final JSONObject saved = ModuleDefinitions.parse(snapshot());
			final List<Object> pending = new ArrayList<Object>(Arrays.asList((Object[])saved.get("modules")));
			for(final String name : wanted)
				if(!capsules.containsKey(name))
					pending.add(new Capsule(plan.definitions.get(name), plan.settings.get(name)).saved());
			saved.put("modules", pending.toArray(new Object[0]));
			final Campaign activated = prepare(plan, ModuleDefinitions.json(saved), true);
			final String problem = activated.problem(id);
			if(!problem.isEmpty())
				throw new IOException(id + ": cannot adopt: " + problem);
			capsules.clear();
			capsules.putAll(activated.capsules);
			stateBytes = activated.stateBytes;
			recordCount = activated.recordCount;
			if(notes.size() < 60)
				notes.add("Adopted " + id + " with its declared dependencies.");
		}
		public synchronized String describe(final String requested, final boolean creator)
		{
			final StringBuilder out = new StringBuilder("Optional modules (new gameplay is default off):");
			final Set<String> all = new TreeSet<String>(capsules.keySet());
			if(creator)
				all.addAll(plan.definitions.keySet());
			int shown = 0;
			for(final String id : all)
			{
				if(requested != null && !requested.isEmpty() && !id.equals(requested))
					continue;
				final Definition def = plan.definitions.get(id);
				final Capsule capsule = capsules.get(id);
				final String reason = problem(id);
				final boolean active = reason.isEmpty();
				out.append("\n\r  ").append(id).append(" v").append(capsule != null ? capsule.version : def.version)
					.append(active ? " ACTIVE" : capsule == null ? " OFF" : " SUSPENDED");
				if(!reason.isEmpty())
					out.append(" - ").append(reason);
				if(creator)
				{
					if(def != null)
						out.append("\n\r    definition: ").append(def.path).append("; selected by: ")
							.append(plan.reasons.containsKey(id) ? plan.reasons.get(id) : "none")
							.append("; clocks: ").append(def.modes).append("; scopes: ").append(def.scopes);
					if(capsule != null)
						out.append("\n\r    saved settings: ").append(ModuleDefinitions.json(capsule.settings)).append("; state owners: ")
							.append(capsule.records.size())
							.append("; removal: ").append(capsule.removal);
				}
				shown++;
			}
			if(shown == 0)
				out.append("\n\r  none.");
			if(creator && !plan.blocking.isEmpty())
				out.append("\n\r").append(plan.blocking.size())
					.append(" problems stop campaigns of this selection from starting: WORLD MODULES CHECK lists them.");
			if(creator && !plan.warnings().isEmpty())
				out.append("\n\r").append(plan.warnings().size())
					.append(" warnings in manifests or settings the selection does not use: WORLD MODULES CHECK lists them.");
			if(creator)
				out.append("\n\rMODULES source: ").append(WorldSettings.sourceOf("MODULES")).append("; MODULESETTINGS source: ")
					.append(WorldSettings.sourceOf("MODULESETTINGS"))
					.append("\n\rWORLD MODULES CHECK | PLAN | RELOAD | ADOPT [ID]. Settings changes take effect at the next process start.");
			return out.toString();
		}
	}
	public static Campaign prepare(final Plan plan, final String saved, final boolean restoring) throws IOException
	{
		plan.requireValid();
		final Campaign next = new Campaign(plan);
		if(!restoring)
		{
			for(final String id : plan.enabled)
				next.capsules.put(id, new Capsule(plan.definitions.get(id), plan.settings.get(id)));
			return next;
		}
		if(saved != null && !saved.trim().isEmpty())
		{
			if(saved.getBytes(StandardCharsets.UTF_8).length > 2 * MAX_STATE_BYTES)
				throw new IOException("module snapshot exceeds its storage budget");
			final JSONObject root = ModuleDefinitions.parse(saved);
			ModuleDefinitions.keys(root, "module snapshot", "schema", "modules");
			if(ModuleDefinitions.integer(root.get("schema"), "module snapshot schema") != ModuleDefinitions.SCHEMA)
				throw new IOException("unsupported saved module schema");
			for(final Object raw : ModuleDefinitions.array(root.get("modules"), "saved modules"))
			{
				// Without an ID and a removal policy, or with a native handler this engine lacks, there is
				// no policy to apply: the restore is refused. Other damage follows the saved policy.
				final JSONObject stored = ModuleDefinitions.object(raw, "saved module");
				final String id = ModuleDefinitions.id(stored.get("id"));
				final String removal = Capsule.policy(stored, id);
				Capsule.handler(stored, id);
				Capsule parsed;
				try
				{
					parsed = new Capsule(stored);
				}
				catch(final IOException e)
				{
					if(removal.equals("refuse"))
						throw new IOException(id + ": restore/reload refused: damaged saved module data: " + e.getMessage(), e);
					parsed = new Capsule(stored, id, "damaged saved module data, kept as saved: " + e.getMessage());
					next.notes.add(id + ": suspended - " + parsed.problem);
				}
				final Capsule old = parsed;
				if(next.capsules.put(old.id, old) != null)
					throw new IOException("duplicate saved module " + old.id);
				if(next.capsules.size() > ModuleDefinitions.MAX_FILES)
					throw new IOException("too many saved modules");
				if(!old.problem.isEmpty())
					continue;
				final Definition def = plan.definitions.get(old.id);
				try
				{
					if(def == null || !plan.enabled.contains(old.id))
						throw new IOException("not enabled or definition removed; saved state preserved");
					if(old.version == def.version && !old.fingerprint.equals(def.fingerprint))
						throw new IOException("definition changed without a version increase");
					final List<Migration> route = def.route(old.version);
					JSONObject config = old.settings;
					final Map<String,JSONObject> migrated = new TreeMap<String,JSONObject>();
					for(final Map.Entry<String,JSONObject> entry : old.records.entrySet())
					{
						final JSONObject record = ModuleDefinitions.copy(entry.getValue());
						JSONObject values = (JSONObject)record.get("values");
						if(!def.scopes.contains(record.get("scope")))
							throw new IOException("stored owner scope is no longer supported");
						for(final Migration step : route)
							values = step.apply(values, false);
						record.put("values", ModuleDefinitions.values(def.state, values, old.id + " migrated state"));
						migrated.put(entry.getKey(), record);
					}
					for(final Migration step : route)
						config = step.apply(config, true);
					config = ModuleDefinitions.values(def.settings, config, old.id + " saved settings");
					// Only commit this capsule after every owner and setting has validated.
					old.records.clear();
					old.records.putAll(migrated);
					old.settings = config;
					if(old.version != def.version)
						next.notes.add(old.id + ": migrated v" + old.version + " to v" + def.version + ".");
					old.version = def.version;
					old.fingerprint = def.fingerprint;
					old.removal = def.removal;
					if(!ModuleDefinitions.json(config).equals(ModuleDefinitions.json(plan.settings.get(old.id))))
						next.creatorNotes.add(old.id + ": saved settings retained; world settings apply to new campaigns/adoptions.");
				}
				catch(final IOException e)
				{
					if(old.removal.equals("refuse"))
						throw new IOException(old.id + ": restore/reload refused: " + e.getMessage(), e);
					old.problem = e.getMessage();
					next.notes.add(old.id + ": suspended - " + old.problem);
				}
			}
		}
		// Dependency safety is transitive. A new world module never enters an old save implicitly.
		boolean changed;
		do
		{
			changed = false;
			for(final Capsule capsule : next.capsules.values())
				if(capsule.problem.isEmpty())
				{
					final Definition def = plan.definitions.get(capsule.id);
					for(final String required : def.requires.keySet())
					{
						final Capsule dependency = next.capsules.get(required);
						if(dependency == null || !dependency.problem.isEmpty())
						{
							final String problem = "dependency " + required + " is not active in this campaign";
							if(capsule.removal.equals("refuse"))
								throw new IOException(capsule.id + ": " + problem);
							capsule.problem = problem;
							next.notes.add(capsule.id + ": suspended - " + problem);
							changed = true;
							break;
						}
					}
				}
		}
		while(changed);
		for(final String id : plan.enabled)
			if(!next.capsules.containsKey(id))
				next.creatorNotes.add(id + ": available for explicit WORLD MODULES ADOPT; existing campaign left unchanged.");
		next.account();
		return next;
	}
	/** MODULES and MODULESETTINGS as the launcher read them, or null when it did not start this game. */
	private static volatile String[] launched = null;
	/**
	 * The launcher's MODULES and MODULESETTINGS, as it read them for the startup check: every boot
	 * of this process reads the same values the same way (the engine's own copy has Properties
	 * escapes and HTML entities applied). World settings take effect at the next process start.
	 */
	public static void launchedWith(final String selection, final String settings)
	{
		launched = new String[] { selection == null ? "" : selection, settings == null ? "" : settings };
	}
	public static Plan configuredPlan()
	{
		final String[] given = launched;
		if(given != null)
			return ModuleDefinitions.load(WorldFiles.worldDir(), given[0], given[1]);
		return ModuleDefinitions.load(WorldFiles.worldDir(), CMProps.instance().getStr("MODULES", ""), CMProps.instance()
			.getStr("MODULESETTINGS", ""));
	}
	/** Invoked before starting any new/restored campaign, including an in-process restore. */
	public static synchronized void boot(final String saved, final boolean restoring) throws IOException
	{
		final Plan plan = configuredPlan();
		for(final String warning : plan.warnings())
			Log.warnOut("OptionalModules", "not selected, so not stopping the game: " + warning);
		// Builders and CHECK=1 must be able to inspect/fix invalid definitions.
		current = !plan.blocking.isEmpty() && (SaveGame.isBuilderMode() || WorldCheck.isHeadless()) ? new Campaign(plan)
			: prepare(plan, saved, restoring);
	}
	public static synchronized void reset()
	{
		current = null;
	}
	public static synchronized boolean enabled(final String id)
	{
		return current != null && current.problem(id).isEmpty();
	}
	public static synchronized boolean hasModules()
	{
		return current != null && !current.capsules.isEmpty();
	}
	public static synchronized Object setting(final String id, final String key) throws IOException
	{
		return campaign().setting(id, key);
	}
	public static synchronized JSONObject get(final String id, final Owner who) throws IOException
	{
		return campaign().get(id, who);
	}
	public static synchronized void put(final String id, final Owner who, final JSONObject values) throws IOException
	{
		campaign().put(id, who, values);
	}
	public static synchronized void putJSON(final String id, final Owner who, final String values) throws IOException
	{
		put(id, who, ModuleDefinitions.parse(values));
	}
	public static synchronized String getJSON(final String id, final Owner who) throws IOException
	{
		return ModuleDefinitions.json(get(id, who));
	}
	public static synchronized Map<String,JSONObject> stored(final Owner who)
	{
		return current == null ? new TreeMap<String,JSONObject>() : current.stored(who);
	}
	public static synchronized String snapshot() throws IOException
	{
		return current == null ? "{\"schema\":1,\"modules\":[]}" : current.snapshot();
	}
	private static Campaign campaign() throws IOException
	{
		if(current == null)
			throw new IOException("optional modules are not initialized");
		return current;
	}
	/** What the restore found, for the player's report; with creator, also what only builder tools can act on. */
	public static synchronized List<String> notes(final boolean creator)
	{
		return current == null ? Collections.<String>emptyList() : current.notes(creator);
	}
	public static synchronized String describe(final String id, final boolean creator)
	{
		return current == null ? "Optional modules are not initialized." : current.describe(id, creator);
	}
	public static synchronized String reload(final boolean apply) throws IOException
	{
		final Campaign next = prepare(configuredPlan(), snapshot(), true);
		final String result = (apply ? "Module definitions reloaded." : "Module reload plan (no changes applied).") + "\n\r"
			+ next.describe(null, true)
			+ (next.notes(true).isEmpty() ? "" : "\n\r" + String.join("\n\r", next.notes(true)));
		if(apply)
		{
			current = next;
			WorldFiles.acceptModuleChanges();
		}
		return result;
	}
	public static synchronized void adopt(final String id) throws IOException
	{
		campaign().adopt(id);
	}
	public static synchronized void configure(final String id, final String key, final String text) throws IOException
	{
		campaign().configure(id, key, text);
	}
	/** Missing files are legacy saves; unreadable/oversized files are errors, never silently discarded state. */
	public static String readSave(final File saveDir, final String slot) throws IOException
	{
		final File file = new File(new File(saveDir, slot), FILE);
		final Properties header = SaveSlots.readHeader(saveDir, slot);
		final String format = header == null ? null : header.getProperty("moduleformat");
		if(header != null && "2".equals(header.getProperty("version")) && format == null)
			throw new IOException("module-aware save is missing its format marker");
		if(format != null && !format.equals("1"))
			throw new IOException("unsupported saved module format " + format);
		if(!file.exists())
		{
			if(format != null)
				throw new IOException("the save requires a missing " + FILE);
			return null;
		}
		if(!file.isFile() || file.length() > 2 * MAX_STATE_BYTES)
			throw new IOException("invalid or oversized " + FILE);
		try(InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream())
		{
			final byte[] buffer = new byte[8192];
			int length;
			while((length = in.read(buffer)) != -1)
			{
				if(out.size() + length > 2 * MAX_STATE_BYTES)
					throw new IOException("oversized " + FILE);
				out.write(buffer, 0, length);
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		}
	}
	public static synchronized String example(final MOB mob, final String id) throws IOException
	{
		final Campaign game = campaign();
		game.require(id);
		final Definition def = game.plan.definitions.get(id);
		if(!def.handler.equals("example.counter"))
			throw new IOException(id + ": not an example counter");
		final Owner who = character(mob);
		final JSONObject values = game.get(id, who);
		final long before = ((Number)values.get("count")).longValue(), step = ((Number)game.setting(id, "step")).longValue();
		final long after;
		try
		{
			after = Math.addExact(before, step);
		}
		catch(final ArithmeticException e)
		{
			throw new IOException("counter overflow", e);
		}
		values.put("count", Long.valueOf(after));
		game.put(id, who, values);
		return def.title + ": " + after + ".";
	}
}
