package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import com.planet_ink.coffee_mud.core.CombatRules.*;
import com.planet_ink.coffee_mud.core.CompositionDefinitions.Compiled;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.Abilities.Misc.CompositionBurn;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.*;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Opt-in combat adapter, native-message receipts, and bounded immutable result history. */
public final class CombatProfiles
{
    private CombatProfiles() { }
    public static final String MODULE = CombatRules.MODULE;
    private static Library library;
    private static File root;
    private static final AtomicLong sequence = new AtomicLong();
    private static final Map<MOB,History> histories = new WeakHashMap<MOB,History>();
    private static final ThreadLocal<Receipt> delivery = new ThreadLocal<Receipt>();
    public static synchronized void reset() { library = null; root = null; histories.clear(); sequence.set(0); }
    public static Library check() { return CombatRules.load(WorldFiles.worldDir()); }
    public static String selectionProblem(final Library lib)
    {
        if(!OptionalModules.enabled(MODULE)) return "";
        try { lib.pinned(ModuleDefinitions.parse(ModuleDefinitions.string(OptionalModules.setting(MODULE,"profile"), "profile setting"))); return ""; }
        catch(final IOException e) { return "Campaign combat profile suspended: " + e.getMessage(); }
    }
    private static synchronized Library definitions() throws IOException
    {
        if(library == null || !WorldFiles.worldDir().equals(root)) { library = check(); root = WorldFiles.worldDir(); }
        if(!library.errors.isEmpty()) throw new IOException(library.report()); return library;
    }
    public static synchronized String reload() throws IOException
    {
        final Library next = check(); if(!next.errors.isEmpty()) throw new IOException(next.report());
        library = next; root = WorldFiles.worldDir(); WorldFiles.acceptCombatChanges(); return next.report() + " Existing pins retain their versions/hashes.";
    }
    private static void require() throws IOException
    { if(!OptionalModules.enabled(MODULE)) throw new IOException("combat.profiles is inactive in this campaign"); }
    public static Profile current() throws IOException
    {
        require(); return definitions().pinned(ModuleDefinitions.parse(ModuleDefinitions.string(OptionalModules.setting(MODULE, "profile"), "profile setting")));
    }
    public static String pin(final String id, final int version) throws IOException
    { return ModuleDefinitions.json(CombatRules.pin(definitions().find(id, version))); }
    public static String use(final String id, final int version) throws IOException
    {
        require(); final String pin = pin(id, version); OptionalModules.configure(MODULE, "profile", pin);
        return "Selected " + current().key() + " for this campaign; SAVE preserves the exact pin. Existing burns retain their own profile.";
    }
    public static String list() throws IOException
    {
        final StringBuilder out = new StringBuilder();
        for(final Profile p : definitions().profiles.values()) out.append(p.key()).append(" - ").append(p.title).append(" (conversion: ").append(p.conversion).append(")\n");
        return out.length() == 0 ? "No authored combat profiles." : out.toString();
    }
    public static final class Collected
    {
        public final Stats stats;
        public final List<String> sources;
        private Collected(final Stats stats, final List<String> sources) { this.stats = stats; this.sources = Collections.unmodifiableList(sources); }
    }
    private static void collect(final Physical host, final List<Stats> values, final List<String> sources) throws IOException
    {
        for(final Enumeration<Ability> e = host.effects(); e.hasMoreElements();)
        {
            final Ability a = e.nextElement();
            if(a instanceof CombatStatSource)
            {
                if(values.size() >= 64) throw new IOException("at most 64 active combat stat sources per actor");
                final Stats delta = ((CombatStatSource)a).combatStats(); values.add(delta);
                final String label=host.Name().replaceAll("[\\p{Cntrl}<>^]", "");
                sources.add(label + ": " + delta.describe());
                final List<String> details=((CombatStatSource)a).combatStatDetails();
                for(int i=0;i<Math.min(6,details.size());i++)sources.add(label+" / "+details.get(i));
            }
        }
    }
    public static Collected stats(final MOB actor, final Profile p) throws IOException
    {
        final List<Stats> values = new ArrayList<Stats>(); final List<String> sources = new ArrayList<String>(); collect(actor, values, sources);
        for(final Enumeration<Item> e = actor.items(); e.hasMoreElements();)
        {
            final Item item = e.nextElement(); if(item.owner() == actor && item.container() == null && item.rawWornCode() != Wearable.IN_INVENTORY) collect(item, values, sources);
        }
        return new Collected(CombatRules.combine(p.defaults, values), sources);
    }
    public static String describeStats(final MOB actor) throws IOException
    {
        final Profile p = current(); final Collected c = stats(actor, p);
        return p.title + " (" + p.key() + ")\nMerged stats: " + c.stats.describe() + "\nSources: " + c.sources;
    }
    public static List<String> checkProperties(final Collection<Room> rooms)
    {
        final List<String> errors = new ArrayList<String>(); final Set<Physical> seen = Collections.newSetFromMap(new IdentityHashMap<Physical,Boolean>());
        for(final Room room : rooms)
        {
            for(int i=0;i<room.numItems();i++) seen.add(room.getItem(i));
            for(int i=0;i<room.numInhabitants();i++)
            {
                final MOB m = room.fetchInhabitant(i); seen.add(m); for(final Enumeration<Item> e=m.items();e.hasMoreElements();) seen.add(e.nextElement());
            }
        }
        for(final Physical p : seen) if(p != null) try { collect(p, new ArrayList<Stats>(), new ArrayList<String>()); }
        catch(final IOException e) { errors.add(p.Name() + ": " + e.getMessage()); }
        return errors;
    }
    public static final class Cast
    {
        public final Profile profile;
        public final Collected attacker;
        private int mana, movement;
        private Cast(final Profile profile, final Collected attacker) { this.profile = profile; this.attacker = attacker; }
        public JSONObject pin() { return CombatRules.pin(profile); }
    }
    public static void paid(final Cast cast, final int mana, final int movement)
    { if(cast != null) { cast.mana = mana; cast.movement = movement; } }
    private static String costs(final Compiled c, final Cast cast) throws IOException
    {
        return "Cast resources: authored mana " + (int)Math.ceil(c.value("mana")) + ", movement " + (int)Math.ceil(c.value("movement"))
            + "; observed net spent mana " + cast.mana + ", movement " + cast.movement + ". Paid once for the whole cast; native cheats/components/compounding and world callbacks may adjust these.";
    }
    public static void failed(final MOB caster, final MOB target, final Compiled c, final Cast cast, final String reason) throws IOException
    {
        if(cast != null) record(caster, target, cast.profile, "cast", Arrays.asList(costs(c,cast), reason, "No profile hit/critical/block/burn rolls or damage."));
    }
    /** Validate the selected profile and primary participants before paying for a cast. */
    public static Cast prepare(final MOB actor, final MOB target) throws IOException
    {
        if(!OptionalModules.enabled(MODULE)) return null;
        final Profile p = current(); final Collected source = stats(actor, p); stats(target, p); return new Cast(p, source);
    }
    public static String describe(final MOB actor, final Compiled c) throws IOException
    {
        final Profile p = current(); final Offense offense = CombatRules.offense(c, Collections.<String>emptySet(), p.conversion);
        return c.describeDamage(offense.damage, "Profile " + p.key() + ", " + p.conversion + " conversion") + "\n" + describeStats(actor)
            + "\nDamage shown is before criticals, target conditions/defenses and external world rules. COMBATRESULT explains resolved events.";
    }
    public interface Budget { boolean take(); }
    private static final int[] MESSAGES = { CMMsg.TYP_JUSTICE, CMMsg.TYP_FIRE, CMMsg.TYP_COLD, CMMsg.TYP_ELECTRIC, CMMsg.TYP_ACID };
    private static final int[] WEAPONS = { Weapon.TYPE_BASHING, Weapon.TYPE_BURNING, Weapon.TYPE_FROSTING, Weapon.TYPE_STRIKING, Weapon.TYPE_MELTING };
    private static final Roller DICE = reason -> CMLib.dice().rollPercentage();
    private static void sourceLines(final List<String> lines, final String label, final Collected c)
    {
        lines.add(label + " merged: " + c.stats.describe());
        for(int i=0;i<Math.min(8, c.sources.size());i++) lines.add(label + " source " + c.sources.get(i));
        if(c.sources.size() > 8) lines.add((c.sources.size()-8) + " additional " + label + " sources included in the aggregate.");
    }
    private static boolean legal(final MOB source, final MOB target, final Room room)
    {
        return room != null && source != target && source.location() == room && target.location() == room && !source.amDead() && !source.amDestroyed()
            && !target.amDead() && !target.amDestroyed() && source.mayIFight(target) && CMLib.flags().canBeSeenBy(target, source)
            && !source.getGroupMembers(new HashSet<MOB>()).contains(target);
    }
    public static void hit(final MOB caster, final MOB target, final Ability tool, final Compiled c, final Cast cast, final Budget budget) throws IOException
    {
        final Room room = caster.location(); final List<String> lines = new ArrayList<String>();
        lines.add(costs(c,cast));
        if(!OptionalModules.enabled(MODULE)) { lines.add("Profile disabled before hit; stopped."); record(caster,target,cast.profile,"hit",lines); budget.take(); return; }
        sourceLines(lines, "Attacker", cast.attacker);
        final Collected defender;
        try { defender = stats(target, cast.profile); }
        catch(final IOException e) { budget.take(); lines.add("Rejected target: " + e.getMessage()); record(caster, target, cast.profile, "hit", lines); return; }
        sourceLines(lines, "Defender", defender);
        final Set<String> conditions = new LinkedHashSet<String>();
        if(target.curState().getHitPoints() < target.maxState().getHitPoints()/2.0) conditions.add("target_wounded");
        if(target.fetchEffect("CompositionBurn") != null) conditions.add("target_burning");
        lines.add("Target conditions: " + conditions);
        final Offense offense = CombatRules.offense(c, conditions, cast.profile.conversion); lines.addAll(offense.lines);
        final Resolution resolved = CombatRules.resolve(cast.profile, offense.damage, cast.attacker.stats, defender.stats, c.tags.contains("spell"), false, DICE); lines.addAll(resolved.lines);
        boolean hurt = false;
        if(!resolved.hit || resolved.blocked && cast.profile.blockReduction >= 100)
        {
            budget.take(); room.show(caster, target, tool, CMMsg.MSG_NOISYMOVEMENT,
                !resolved.hit ? c.skill.name + " miss(es) <T-NAME>." : "<T-NAME> block(s) " + c.skill.name + ".");
        }
        else for(int i=0;i<CombatRules.TYPES.size();i++)
        {
            final String type = CombatRules.TYPES.get(i); if(offense.damage.get(type) <= 0) continue;
            if(!budget.take()) { lines.add(type + ": event budget exhausted; not delivered"); continue; }
            final int damage = resolved.damage.get(type).intValue(); if(damage <= 0) continue;
            if(!OptionalModules.enabled(MODULE) || !legal(caster, target, room)) { lines.add(type + ": profile disabled or participant unavailable/no longer legal; not delivered"); continue; }
            final Receipt receipt = post(caster, target, tool, damage, MESSAGES[i], WEAPONS[i], c.skill.name + " <DAMAGE> <T-NAME>!");
            lines.add(receipt.describe(type)); hurt |= receipt.applied > 0;
        }
        final Map<String,Double> values = c.values(conditions);
        if(hurt && legal(caster, target, room) && values.get("burn_damage") >= .5 && values.get("burn_ticks") >= 1)
        {
            if(!budget.take()) lines.add("Burn rejected: event budget exhausted.");
            else if(defender.stats.immune.contains("fire") || defender.stats.immune.contains("burning")) lines.add("Burn rejected: immunity.");
            else
            {
                final double chance = values.get("burn_chance"); final int roll = chance <= 0 || chance >= 100 ? 0 : DICE.roll("burn");
                final boolean applied = chance >= 100 || chance > 0 && roll <= chance;
                lines.add("Burn chance: " + CombatRules.display(chance) + "%" + (roll == 0 ? " (no roll needed)" : ", roll " + roll) + ": " + applied);
                if(applied) lines.add(CompositionBurn.applyProfile(caster, target, (int)Math.round(values.get("burn_damage")), (int)Math.floor(values.get("burn_ticks")), tool, cast.pin(), cast.attacker.stats.get("penetration.fire"))
                    ? "Burn snapshot applied/refreshed; no additional native save." : "Burn not applied: stronger existing burn or world veto.");
            }
        }
        record(caster, target, cast.profile, "hit", lines);
    }
    /** The burn stores offense and the profile pin; target defenses are sampled each tick. False ends it. */
    public static boolean burn(final MOB caster, final MOB target, final Ability tool, final int amount, final JSONObject pin, final double penetration)
    {
        if(!OptionalModules.enabled(MODULE)) return false;
        Profile p = null; final List<String> lines = new ArrayList<String>();
        try
        {
            p = definitions().pinned(pin); final Collected defender = stats(target, p); sourceLines(lines, "Defender", defender);
            if(defender.stats.immune.contains("fire") || defender.stats.immune.contains("burning")) { lines.add("Burn removed: immunity."); record(caster, target, p, "burn", lines); return false; }
            final Map<String,Double> damage = new LinkedHashMap<String,Double>(); damage.put("fire", Double.valueOf(amount));
            lines.add("Stored burn offense: fire=" + amount + ", penetration=" + CombatRules.display(penetration));
            final Resolution resolved = CombatRules.resolve(p, damage, CombatRules.burnSource(penetration), defender.stats, true, true, DICE); lines.addAll(resolved.lines);
            final int finalDamage = resolved.damage.get("fire").intValue();
            if(finalDamage > 0) lines.add(post(caster, target, tool, finalDamage, CMMsg.TYP_FIRE, Weapon.TYPE_BURNING, "The lingering flames <DAMAGE> <T-NAME>!").describe("fire"));
            record(caster, target, p, "burn", lines); return true;
        }
        catch(final IOException e)
        {
            lines.add("Burn suspended for this tick: " + e.getMessage() + "; ordinary duration continues.");
            record(caster, target, p, "burn", lines); return true;
        }
    }
    /** One-use receipt binds the bypass to the exact native CMMsg, not a broad damage thread/ability flag. */
    public static final class Receipt
    {
        final MOB source, target; final Environmental tool; final int requested;
        boolean claimed, approved, redirected; CMMsg message; int sourceType, targetType, worldValue, applied;
        Receipt(final MOB source, final MOB target, final Environmental tool, final int requested) { this.source=source;this.target=target;this.tool=tool;this.requested=requested; }
        String describe(final String type)
        {
            return type + " delivery: profile " + requested + ", " + (!claimed ? "native combat library did not issue a receipt" : redirected ? "world changed the recipient/source/tool; delivery refused" : !approved ? "world veto" : "world-approved " + worldValue)
                + ", HP removed " + applied + ". Native spell fudge/critical and damage saves skipped once; world properties/difficulty/defeat rules remain active.";
        }
    }
    private static Receipt post(final MOB caster, final MOB target, final Ability tool, final int amount, final int message, final int weapon, final String text)
    {
        final Receipt previous = delivery.get(), receipt = new Receipt(caster, target, tool, amount); delivery.set(receipt);
        try { CMLib.combat().postDamage(caster, target, tool, amount, CMMsg.MASK_ALWAYS | message, weapon, text); return receipt; }
        finally { if(previous == null) delivery.remove(); else delivery.set(previous); }
    }
    public static Receipt claim(final MOB source, final MOB target, final Environmental tool)
    {
        final Receipt receipt = delivery.get();
        if(receipt == null || receipt.claimed || receipt.source != source || receipt.target != target || receipt.tool != tool) return null;
        receipt.claimed = true; return receipt;
    }
    public static void bind(final Receipt receipt, final CMMsg message)
    { if(receipt != null) { receipt.message = message; receipt.sourceType=message.sourceMinor(); receipt.targetType=message.targetMinor(); } }
    public static boolean resolved(final CMMsg message)
    { final Receipt receipt = delivery.get(); return receipt != null && receipt.message == message && receipt.source == message.source() && receipt.target == message.target() && receipt.tool == message.tool()
        && receipt.sourceType == message.sourceMinor() && receipt.targetType == message.targetMinor(); }
    /** Own cast/status messages skip native saving throws, while ordinary world vetoes still run. */
    public static boolean announce(final MOB source, final MOB target, final Ability tool, final CMMsg message)
    {
        if(!OptionalModules.enabled(MODULE) || source.location() == null || source.location() != target.location()) return false;
        final Room room = source.location(); final Receipt previous=delivery.get(), receipt=new Receipt(source,target,tool,0);
        receipt.claimed=true; bind(receipt,message); delivery.set(receipt);
        try
        {
            if(!room.okMessage(source,message) || !resolved(message)) return false;
            room.send(source,message); return true;
        }
        finally { if(previous==null) delivery.remove(); else delivery.set(previous); }
    }
    public static boolean approved(final Receipt receipt, final CMMsg message, final boolean allowed)
    {
        if(receipt == null) return allowed;
        receipt.redirected = !resolved(message) || message.targetMinor() != CMMsg.TYP_DAMAGE;
        receipt.approved = allowed && !receipt.redirected; receipt.worldValue = message.value(); return receipt.approved;
    }
    public static void applied(final CMMsg message, final int before, final int after)
    { if(resolved(message)) delivery.get().applied += Math.max(0, before-after); }
    private static final class Trace
    {
        final long serial;
        final String text;
        Trace(final long serial, final String text) { this.serial=serial;this.text=text; }
    }
    private static final class History { final Deque<Trace> results = new ArrayDeque<Trace>(); long serial; }
    private static synchronized void remember(final MOB actor, final Trace trace)
    {
        History history = histories.get(actor);
        if(history == null)
        {
            if(histories.size() >= 256)
            {
                MOB oldest = null; long serial = Long.MAX_VALUE;
                for(final Map.Entry<MOB,History> entry : histories.entrySet()) if(entry.getValue().serial < serial) { oldest=entry.getKey();serial=entry.getValue().serial; }
                if(oldest != null) histories.remove(oldest);
            }
            history = new History(); histories.put(actor, history);
        }
        history.serial = trace.serial; history.results.addFirst(trace); while(history.results.size() > 8) history.results.removeLast();
    }
    private static void record(final MOB caster, final MOB target, final Profile p, final String kind, final List<String> lines)
    {
        final long serial = sequence.incrementAndGet();
        final StringBuilder text = new StringBuilder("Combat result #" + serial + " - " + kind + ": " + caster.Name() + " -> " + target.Name()
            + "\nProfile: " + (p == null ? "unavailable pinned profile" : p.key() + " / " + p.conversion) + "\n");
        for(int i=0;i<lines.size();i++)
        {
            if(lines.size()>96 && i==75) { text.append("[Middle detail omitted; final delivery outcomes follow.]\n"); i=lines.size()-20; }
            final String line=lines.get(i); text.append(line.length()>500 ? line.substring(0,500)+"..." : line).append('\n');
        }
        final Trace trace = new Trace(serial, text.toString()); remember(caster, trace); if(caster != target) remember(target, trace);
    }
    public static synchronized String trace(final MOB actor, final int index)
    {
        final History history = histories.get(actor);
        if(history == null || history.results.size() < index || index < 1 || index > 8) return "No such recent profile combat result. History starts anew after boot/restore.";
        int at=0; for(final Trace trace : history.results) if(++at == index) return trace.text;
        return "No recent profile combat results.";
    }
    public static void playerAccess() throws IOException
    { require(); if(!Boolean.TRUE.equals(OptionalModules.setting(MODULE,"player_trace"))) throw new IOException("the creator disabled player combat inspection"); }
}
