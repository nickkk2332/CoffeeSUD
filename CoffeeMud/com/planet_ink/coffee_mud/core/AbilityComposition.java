package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.util.*;
import com.planet_ink.coffee_mud.core.CompositionDefinitions.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Character-owned, version-pinned support loadouts. Runtime effects are snapshots, never stat mutations. */
public final class AbilityComposition
{
    private AbilityComposition() { }
    public static final String MODULE = CompositionDefinitions.MODULE;
    private static Library current;
    private static File root;
    public static synchronized void reset() { current = null; root = null; }
    public static Library check()
    {
        final Library lib = CompositionDefinitions.load(WorldFiles.worldDir());
        for(final Definition def : lib.definitions.values())
        {
            if(!def.requiresAbility.isEmpty() && CMClass.getAbility(def.requiresAbility) == null)
                lib.errors.add(def.path + ": unknown requires_ability " + def.requiresAbility);
            final List<String> errors = new ArrayList<String>();
            if(!def.mask.isEmpty() && !CMLib.masking().syntaxCheck(def.mask, errors))
                lib.errors.add(def.path + ": invalid character mask: " + String.join("; ", errors));
        }
        return lib;
    }
    private static synchronized Library library() throws IOException
    {
        if(current == null || !WorldFiles.worldDir().equals(root)) { current = check(); root = WorldFiles.worldDir(); }
        if(!current.errors.isEmpty()) throw new IOException(current.report());
        return current;
    }
    public static synchronized String reload() throws IOException
    {
        final Library next = check();
        if(!next.errors.isEmpty()) throw new IOException(next.report());
        current = next; root = WorldFiles.worldDir(); WorldFiles.acceptCompositionChanges();
        return next.report() + " Character bindings retain their versions and hashes; changed or missing pins suspend.";
    }
    public static void require() throws IOException
    { if(!OptionalModules.enabled(MODULE)) throw new IOException("the abilities.composition module is inactive in this campaign"); }
    public static boolean eligible(final MOB mob, final Definition def)
    {
        return mob != null && mob.fetchAbility("Skill_Compose") != null
            && (def.requiresAbility.isEmpty() || mob.fetchAbility(def.requiresAbility) != null)
            && (def.mask.isEmpty() || CMLib.masking().maskCheck(def.mask, mob, true));
    }
    private static void access(final MOB mob, final Definition def) throws IOException
    { if(!eligible(mob, def)) throw new IOException("you do not meet the learned-ability or character requirements for " + def.id); }
    private static JSONObject state(final MOB mob) throws IOException
    { require(); return OptionalModules.get(MODULE, OptionalModules.character(mob)); }
    private static JSONObject field(final JSONObject state, final String name) throws IOException
    {
        final String raw = ModuleDefinitions.string(state.get(name), MODULE + "." + name);
        return raw.isEmpty() ? new JSONObject() : ModuleDefinitions.parse(raw);
    }
    private static void write(final MOB mob, final JSONObject state, final String field, final JSONObject values) throws IOException
    {
        final String raw = ModuleDefinitions.json(values);
        if(raw.length() > 4096) throw new IOException(field + " storage is full; remove an unused skill/support or wait for cooldowns to expire");
        state.put(field, raw); OptionalModules.put(MODULE, OptionalModules.character(mob), state);
    }
    private static JSONObject binding(final JSONObject bindings, final String id) throws IOException
    {
        if(!bindings.containsKey(id)) throw new IOException("choose this skill first with COMPOSE LEARN " + id);
        final JSONObject binding = ModuleDefinitions.object(bindings.get(id), "binding");
        ModuleDefinitions.keys(binding, "binding", "skill", "supports"); return binding;
    }
    private static Compiled compile(final MOB mob, final Library lib, final JSONObject binding) throws IOException
    {
        final Definition skill = lib.pinned(ModuleDefinitions.object(binding.get("skill"), "skill pin"), "skill"); access(mob, skill);
        final List<Definition> supports = new ArrayList<Definition>();
        for(final Object raw : ModuleDefinitions.array(binding.get("supports"), "support pins"))
        {
            final Definition support = lib.pinned(ModuleDefinitions.object(raw, "support pin"), "support"); access(mob, support); supports.add(support);
        }
        if(supports.size() > setting("max_supports", 0, 3)) throw new IOException("this loadout exceeds the campaign's support limit; remove a support");
        return CompositionDefinitions.compile(skill, supports);
    }
    public static int setting(final String key, final int min, final int max) throws IOException
    {
        require(); final Object raw = OptionalModules.setting(MODULE, key);
        if(!(raw instanceof Long) && !(raw instanceof Integer)) throw new IOException("composition setting must be an integer: " + key);
        final long value = ((Number)raw).longValue();
        if(value < min || value > max) throw new IOException("invalid composition setting " + key);
        return (int)value;
    }
    public static Compiled compiled(final MOB mob, final String id) throws IOException
    {
        final JSONObject bindings = field(state(mob), "loadouts");
        final Compiled result = compile(mob, library(), binding(bindings, id));
        if(!result.skill.id.equals(id)) throw new IOException("skill binding ID mismatch");
        return result;
    }
    public static String list(final MOB mob) throws IOException
    {
        final JSONObject bindings = field(state(mob), "loadouts"); final Library lib = library();
        final SortedSet<String> skills = new TreeSet<String>(), supports = new TreeSet<String>();
        for(final Definition def : lib.definitions.values()) if(eligible(mob, def)) (def.kind.equals("skill") ? skills : supports).add(def.id);
        final StringBuilder out = new StringBuilder("Available skills: " + skills + "\nAvailable supports: " + supports + "\nChosen skills:");
        for(final String id : new TreeSet<String>(bindings.keySet()))
        {
            out.append("\n  ").append(id);
            try { out.append(" ").append(compiled(mob, id).skill.key()); }
            catch(final IOException e) { out.append(" [suspended: ").append(e.getMessage()).append("]"); }
        }
        return out.append("\nCOMPOSE LEARN/SHOW/LINK/UNLINK/FORGET/CAST; HELP COMPOSE explains the syntax.").toString();
    }
    /** Explicit selection/upgrade. Keeps cooldowns, and clears supports so changing a base cannot silently reinterpret them. */
    public static void learn(final MOB mob, final String id) throws IOException
    {
        synchronized(mob)
        {
            final JSONObject state = state(mob), bindings = field(state, "loadouts");
            final Definition skill = library().latest(id, "skill"); access(mob, skill);
            if(!bindings.containsKey(id) && bindings.size() >= 4) throw new IOException("at most four chosen skills; COMPOSE FORGET an unused one");
            final JSONObject binding = new JSONObject(); binding.put("skill", CompositionDefinitions.pin(skill)); binding.put("supports", new Object[0]);
            CompositionDefinitions.compile(skill, Collections.<Definition>emptyList()); bindings.put(id, binding); write(mob, state, "loadouts", bindings);

        }
    }
    public static void forget(final MOB mob, final String id) throws IOException
    {
        synchronized(mob)
        {
            final JSONObject state = state(mob), bindings = field(state, "loadouts");
            if(bindings.remove(id) == null) throw new IOException("no chosen skill " + id);
            write(mob, state, "loadouts", bindings);

        }
    }
    public static void link(final MOB mob, final String id, final String supportID) throws IOException
    {
        synchronized(mob)
        {
            final JSONObject state = state(mob), bindings = field(state, "loadouts"), binding = binding(bindings, id);
            final Compiled old = compile(mob, library(), binding); final Definition support = library().latest(supportID, "support"); access(mob, support);
            final List<Definition> next = new ArrayList<Definition>(old.supports); next.add(support);
            if(next.size() > setting("max_supports", 0, 3)) throw new IOException("campaign support limit reached");
            CompositionDefinitions.compile(old.skill, next);
            final List<JSONObject> pins = new ArrayList<JSONObject>(); for(final Definition def : next) pins.add(CompositionDefinitions.pin(def));
            binding.put("supports", pins.toArray()); write(mob, state, "loadouts", bindings);

        }
    }
    /** Removal deliberately works even for an unavailable definition, lost prerequisite, or reduced support limit. */
    public static void unlink(final MOB mob, final String id, final String supportID) throws IOException
    {
        synchronized(mob)
        {
            final JSONObject state = state(mob), bindings = field(state, "loadouts"), binding = binding(bindings, id);
            final List<Object> next = new ArrayList<Object>(); boolean found = false;
            for(final Object raw : ModuleDefinitions.array(binding.get("supports"), "support pins"))
            {
                if(supportID.equals(ModuleDefinitions.object(raw, "support pin").get("id"))) found = true; else next.add(raw);
            }
            if(!found) throw new IOException("support is not linked: " + supportID);
            binding.put("supports", next.toArray()); write(mob, state, "loadouts", bindings);

        }
    }
    public static String show(final MOB mob, final String id) throws IOException
    {
        final Compiled c = compiled(mob, id);
        return (OptionalModules.enabled(CombatRules.MODULE) ? CombatProfiles.describe(mob, c) : c.describe()) + "\nCampaign limits: " + setting("max_supports", 0, 3) + " supports; " + setting("max_chains", 0, 8)
            + " chain hops; " + setting("event_budget", 1, 64) + " damage/secondary events per cast."
            + "\nCooldown remaining: " + remaining(mob, id) + " ticks. Engine damage adjustments still apply.";
    }
    private static JSONObject cooldowns(final JSONObject state) throws IOException
    {
        final JSONObject values = field(state, "cooldowns"); final long now = GameTime.now();
        for(final String id : new ArrayList<String>(values.keySet()))
        {
            ModuleDefinitions.id(id); final Object raw = values.get(id);
            if(!(raw instanceof Long) && !(raw instanceof Integer)) throw new IOException("invalid saved composition cooldown");
            if(((Number)raw).longValue() <= now) values.remove(id);
        }
        return values;
    }
    public static long remaining(final MOB mob, final String id) throws IOException
    {
        final JSONObject values = cooldowns(state(mob));
        return values.containsKey(id) ? Math.max(0, (((Number)values.get(id)).longValue() - GameTime.now() + CMProps.getTickMillis() - 1) / CMProps.getTickMillis()) : 0;
    }
    /** Called after one paid invocation, including a proficiency fizzle. Forget/relearn and party switching cannot clear it. */
    public static void cooldown(final MOB mob, final String id, final int ticks) throws IOException
    {
        synchronized(mob)
        {
            final JSONObject state = state(mob), values = cooldowns(state);
            if(ticks > 0) values.put(id, Long.valueOf(GameTime.now() + ticks * CMProps.getTickMillis()));
            write(mob, state, "cooldowns", values);

        }
    }
    /** Check persistence budget before any resources are spent. */
    public static void canCast(final MOB mob, final String id) throws IOException
    {
        if(remaining(mob, id) > 0) throw new IOException("this skill is cooling down (" + remaining(mob, id) + " ticks)");
        final JSONObject values = cooldowns(state(mob)); values.put(id, Long.valueOf(GameTime.now() + 1000 * CMProps.getTickMillis()));
        if(ModuleDefinitions.json(values).length() > 4096) throw new IOException("cooldown storage is full; wait for cooldowns to expire");
    }
}
