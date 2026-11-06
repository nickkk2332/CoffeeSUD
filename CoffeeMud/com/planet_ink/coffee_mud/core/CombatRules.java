package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.CompositionDefinitions.Compiled;
import com.planet_ink.coffee_mud.core.CompositionDefinitions.Modifier;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Pure versioned combat rules. Dice are explicit inputs; this class never changes the world. */
public final class CombatRules
{
    private CombatRules() { }
    public static final String MODULE = "combat.profiles";
    public static final List<String> TYPES = CompositionDefinitions.TYPES;
    public static final List<String> SCALARS = Collections.unmodifiableList(Arrays.asList("accuracy", "evasion", "armor", "block", "spell_block", "crit_chance", "crit_multiplier"));
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal ZERO = BigDecimal.ZERO, ONE = BigDecimal.ONE, HUNDRED = BigDecimal.valueOf(100);
    private static BigDecimal d(final double n) { return BigDecimal.valueOf(n); }
    private static BigDecimal bound(final BigDecimal n, final double min, final double max) { return n.max(d(min)).min(d(max)); }
    public static String display(final double n) { return d(n).stripTrailingZeros().toPlainString(); }
    private static double number(final Object raw, final String where, final double min, final double max) throws IOException
    {
        if(!(raw instanceof Number)) throw new IOException(where + " must be a number");
        final double n = ((Number)raw).doubleValue();
        if(!Double.isFinite(n) || n < min || n > max) throw new IOException(where + " must be in " + min + ".." + max);
        return n;
    }
    private static double number(final JSONObject obj, final String key, final double fallback, final double min, final double max) throws IOException
    { return obj.containsKey(key) ? number(obj.get(key), key, min, max) : fallback; }
    private static JSONObject optional(final JSONObject obj, final String key) throws IOException
    { return obj.containsKey(key) ? ModuleDefinitions.object(obj.get(key), key) : new JSONObject(); }
    private static String string(final JSONObject obj, final String key, final String fallback) throws IOException
    { return obj.containsKey(key) ? ModuleDefinitions.string(obj.get(key), key) : fallback; }
    private static Map<String,BigDecimal> zeroes()
    {
        final Map<String,BigDecimal> out = new LinkedHashMap<String,BigDecimal>();
        for(final String name : SCALARS) out.put(name, ZERO);
        for(final String type : TYPES) { out.put("resistance." + type, ZERO); out.put("penetration." + type, ZERO); }
        return out;
    }
    public static final class Stats
    {
        private final Map<String,BigDecimal> values;
        public final Set<String> immune;
        private Stats(final Map<String,BigDecimal> values, final Set<String> immune)
        { this.values = Collections.unmodifiableMap(values); this.immune = Collections.unmodifiableSet(immune); }
        public double get(final String name) { return values.get(name).doubleValue(); }
        public String describe()
        {
            final List<String> parts = new ArrayList<String>();
            for(final Map.Entry<String,BigDecimal> e : values.entrySet()) if(e.getValue().signum() != 0) parts.add(e.getKey() + "=" + display(e.getValue().doubleValue()));
            if(!immune.isEmpty()) parts.add("immune=" + immune);
            return parts.isEmpty() ? "no numeric bonuses" : String.join(", ", parts);
        }
    }
    private static Stats stats(final JSONObject obj, final boolean additive) throws IOException
    {
        ModuleDefinitions.keys(obj, "combat stats", "schema", "accuracy", "evasion", "armor", "block", "spell_block", "crit_chance", "crit_multiplier", "resistance", "penetration", "immune");
        if((additive || obj.containsKey("schema")) && ModuleDefinitions.integer(obj.get("schema"), "combat stats schema") != 1) throw new IOException("unsupported combat stats schema");
        final Map<String,BigDecimal> out = zeroes();
        if(!additive) { out.put("accuracy", d(100)); out.put("crit_multiplier", d(150)); }
        for(final String key : SCALARS) if(obj.containsKey(key))
        {
            final double limit = key.equals("accuracy") || key.equals("evasion") || key.equals("armor") ? 1000000 : key.equals("crit_multiplier") ? 1000 : 100;
            out.put(key, d(number(obj.get(key), key, additive ? -limit : key.equals("crit_multiplier") ? 100 : 0, limit)));
        }
        for(final String group : Arrays.asList("resistance", "penetration"))
        {
            final JSONObject entries = optional(obj, group);
            for(final String type : entries.keySet())
            {
                if(!TYPES.contains(type)) throw new IOException("unknown " + group + " type " + type);
                out.put(group + "." + type, d(number(entries.get(type), group + "." + type, group.equals("resistance") ? -200 : additive ? -100 : 0, group.equals("resistance") ? 200 : 100)));
            }
        }
        final Set<String> immune = new LinkedHashSet<String>();
        if(obj.containsKey("immune")) for(final Object raw : ModuleDefinitions.array(obj.get("immune"), "immune"))
        {
            final String type = ModuleDefinitions.string(raw, "immunity");
            if(!TYPES.contains(type) && !type.equals("burning")) throw new IOException("unknown immunity " + type);
            if(!immune.add(type)) throw new IOException("duplicate immunity " + type);
        }
        return new Stats(out, immune);
    }
    public static Stats bonuses(final String text) throws IOException { return stats(ModuleDefinitions.parse(text), true); }
    public static Stats burnSource(final double penetration) throws IOException
    {
        final Map<String,BigDecimal> values = zeroes(); values.put("crit_multiplier", d(100));
        values.put("penetration.fire", d(number(Double.valueOf(penetration), "burn penetration", 0, 100)));
        return new Stats(values, new LinkedHashSet<String>());
    }
    public static Stats combine(final Stats defaults, final Collection<Stats> sources)
    {
        final Map<String,BigDecimal> out = new LinkedHashMap<String,BigDecimal>(defaults.values);
        final Set<String> immune = new LinkedHashSet<String>(defaults.immune);
        for(final Stats source : sources)
        {
            for(final String key : out.keySet()) out.put(key, out.get(key).add(source.values.get(key), MC));
            immune.addAll(source.immune);
        }
        for(final String name : SCALARS) out.put(name, bound(out.get(name), name.equals("crit_multiplier") ? 100 : 0,
            name.equals("accuracy") || name.equals("evasion") || name.equals("armor") ? 10000000 : name.equals("crit_multiplier") ? 1000 : 100));
        for(final String type : TYPES) out.put("penetration." + type, bound(out.get("penetration." + type), 0, 100));
        return new Stats(out, immune);
    }
    /** Sum already validated deltas without introducing absolute-stat defaults/floors. */
    public static Stats sumBonuses(final Collection<Stats> sources)
    {
        final Map<String,BigDecimal> out=zeroes();final Set<String> immune=new LinkedHashSet<String>();
        for(final Stats source:sources){for(final String key:out.keySet())out.put(key,out.get(key).add(source.values.get(key),MC));immune.addAll(source.immune);}
        return new Stats(out,immune);
    }
    public static final class Profile
    {
        public final String id, title, conversion, hash, path;
        public final int version;
        public final double minHit, maxHit, armorConstant, armorCap, resistanceFloor, resistanceCap, blockCap, blockReduction;
        public final int maxDamage;
        public final Stats defaults;
        public Profile(final JSONObject obj, final String path) throws IOException
        {
            ModuleDefinitions.keys(obj, "combat profile", "schema", "id", "version", "title", "conversion", "hit", "armor", "resistance", "block_cap", "block_reduction", "maximum_damage", "defaults");
            if(ModuleDefinitions.integer(obj.get("schema"), "combat profile schema") != 1) throw new IOException("unsupported combat profile schema");
            id = ModuleDefinitions.id(obj.get("id")); version = ModuleDefinitions.integer(obj.get("version"), "profile version");
            title = ModuleDefinitions.string(obj.get("title"), "title");
            if(title.isEmpty() || title.length() > 80 || title.matches("(?s).*[\\x00-\\x1f^<>].*")) throw new IOException("title must be 1..80 plain text characters");
            conversion = string(obj, "conversion", "final");
            if(!conversion.equals("final") && !conversion.equals("history")) throw new IOException("conversion must be final or history");
            final JSONObject hit = optional(obj, "hit"), armor = optional(obj, "armor"), resistance = optional(obj, "resistance");
            ModuleDefinitions.keys(hit, "hit", "minimum", "maximum"); ModuleDefinitions.keys(armor, "armor", "constant", "maximum_reduction"); ModuleDefinitions.keys(resistance, "resistance", "minimum", "maximum");
            minHit = number(hit, "minimum", 5, 0, 100); maxHit = number(hit, "maximum", 95, 0, 100);
            if(minHit > maxHit) throw new IOException("hit minimum exceeds maximum");
            armorConstant = number(armor, "constant", 5, .01, 100); armorCap = number(armor, "maximum_reduction", 90, 0, 100);
            resistanceFloor = number(resistance, "minimum", -100, -100, 0); resistanceCap = number(resistance, "maximum", 75, 0, 100);
            blockCap = number(obj, "block_cap", 75, 0, 100); blockReduction = number(obj, "block_reduction", 100, 0, 100);
            maxDamage = obj.containsKey("maximum_damage") ? ModuleDefinitions.integer(obj.get("maximum_damage"), "maximum_damage") : 1000000;
            if(maxDamage > 1000000) throw new IOException("maximum_damage exceeds 1000000");
            defaults = stats(optional(obj, "defaults"), false); this.path = path;
            try
            {
                final byte[] bytes = MessageDigest.getInstance("SHA-256").digest(ModuleDefinitions.json(obj).getBytes(StandardCharsets.UTF_8));
                final StringBuilder text = new StringBuilder(); for(final byte b : bytes) text.append(String.format("%02x", Integer.valueOf(b & 255))); hash = text.toString();
            }
            catch(final Exception e) { throw new IOException(e); }
        }
        public String key() { return id + ".v" + version; }
    }
    public static JSONObject pin(final Profile profile)
    { final JSONObject out = new JSONObject(); out.put("id", profile.id); out.put("version", Long.valueOf(profile.version)); out.put("hash", profile.hash); return out; }
    public static JSONObject checkPin(final JSONObject pin) throws IOException
    {
        ModuleDefinitions.keys(pin, "combat profile pin", "id", "version", "hash"); ModuleDefinitions.id(pin.get("id")); ModuleDefinitions.integer(pin.get("version"), "profile pin version");
        if(!ModuleDefinitions.string(pin.get("hash"), "profile pin hash").matches("[0-9a-f]{64}")) throw new IOException("invalid combat profile hash");
        return ModuleDefinitions.copy(pin);
    }
    public static final class Library
    {
        public final Map<String,Profile> profiles = new TreeMap<String,Profile>();
        public final List<String> errors = new ArrayList<String>();
        public Profile find(final String id, final int version) throws IOException
        {
            Profile result = null;
            for(final Profile p : profiles.values()) if(p.id.equals(id) && (version == 0 || version == p.version) && (result == null || p.version > result.version)) result = p;
            if(result == null) throw new IOException("combat profile unavailable: " + id + (version == 0 ? "" : " v" + version));
            return result;
        }
        public Profile pinned(final JSONObject raw) throws IOException
        {
            final JSONObject pin = checkPin(raw); final Profile p = find((String)pin.get("id"), ModuleDefinitions.integer(pin.get("version"), "profile version"));
            if(!p.hash.equals(pin.get("hash"))) throw new IOException(p.key() + " changed without a new version; profile suspended");
            return p;
        }
        public String report() { return errors.isEmpty() ? "Combat profiles are valid (" + profiles.size() + " versions)." : String.join("\n", errors); }
    }
    public static Library load(final File world)
    {
        final Library lib = new Library(); final File dir = new File(world, "combat");
        if(Files.isSymbolicLink(dir.toPath())) { lib.errors.add("combat/: directory must not be a symbolic link"); return lib; }
        if(dir.exists() && !dir.isDirectory()) lib.errors.add("combat/: must be a directory");
        final File[] files = dir.listFiles(f -> f.getName().endsWith(".json")); if(files == null) return lib;
        Arrays.sort(files, Comparator.comparing(File::getName)); if(files.length > 128) lib.errors.add("combat/: at most 128 profiles");
        for(int i=0;i<Math.min(128, files.length);i++)
        {
            final File file = files[i]; final String path = "combat/" + file.getName();
            try
            {
                if(!file.isFile() || Files.isSymbolicLink(file.toPath()) || !file.getCanonicalPath().startsWith(world.getCanonicalPath() + File.separator)) throw new IOException("must be a regular file inside the world");
                if(file.length() > 65536) throw new IOException("profile exceeds 64 KiB");
                final Profile p = new Profile(ModuleDefinitions.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)), path);
                if(!file.getName().equals(p.key() + ".json")) throw new IOException("filename must be " + p.key() + ".json");
                if(lib.profiles.put(p.key(), p) != null) throw new IOException("duplicate combat profile " + p.key());
            }
            catch(final Exception e) { lib.errors.add(path + ": " + e.getMessage()); }
        }
        return lib;
    }
    public static final class Offense
    {
        public final Map<String,Double> damage;
        public final List<String> lines;
        private Offense(final Map<String,Double> damage, final List<String> lines) { this.damage = Collections.unmodifiableMap(damage); this.lines = Collections.unmodifiableList(lines); }
    }
    private static final class Path
    {
        final String source, target;
        BigDecimal amount;
        Path(final String source, final String target, final BigDecimal amount) { this.source=source;this.target=target;this.amount=amount; }
    }
    /** One simultaneous conversion pass; history mode scales a packet by each unique visited type. */
    public static Offense offense(final Compiled c, final Set<String> conditions, final String mode) throws IOException
    {
        if(!mode.equals("final") && !mode.equals("history")) throw new IOException("unknown conversion model " + mode);
        final List<Modifier> active = new ArrayList<Modifier>(); final List<String> lines = new ArrayList<String>();
        final Map<String,BigDecimal> pools = new LinkedHashMap<String,BigDecimal>(), totals = new LinkedHashMap<String,BigDecimal>();
        for(final String type : TYPES) { pools.put(type, d(c.skill.base.get("damage." + type))); totals.put(type, ZERO); }
        int shown = 0, omitted = 0;
        for(final Modifier m : c.modifiers) if(m.stat.equals("damage") || m.stat.startsWith("damage."))
        {
            final boolean used = m.active(c.tags, conditions); if(used) active.add(m);
            if(shown++ < 24) lines.add((used ? "Applied " : "Inactive ") + m); else omitted++;
            if(used && m.op.equals("flat")) pools.put(m.stat.substring(7), pools.get(m.stat.substring(7)).add(d(m.value), MC));
        }
        if(omitted > 0) lines.add(omitted + " additional damage modifiers evaluated.");
        final List<Path> paths = new ArrayList<Path>();
        for(final String source : TYPES)
        {
            final Map<String,BigDecimal> packets = new LinkedHashMap<String,BigDecimal>();
            final BigDecimal original = pools.get(source).max(ZERO); packets.put(source, original);
            for(final Modifier m : active) if(m.op.equals("convert") && m.from.equals(source))
            {
                final BigDecimal amount = original.multiply(d(m.value).movePointLeft(2), MC);
                packets.put(source, packets.get(source).subtract(amount, MC));
                packets.put(m.to, (packets.containsKey(m.to) ? packets.get(m.to) : ZERO).add(amount, MC));
            }
            for(final Map.Entry<String,BigDecimal> packet : packets.entrySet()) if(packet.getValue().signum() > 0)
                paths.add(new Path(source, packet.getKey(), packet.getValue()));
        }
        // A negative destination pool offsets incoming converted damage before scaling.
        for(final String type : TYPES) if(pools.get(type).signum() < 0)
        {
            BigDecimal incoming = ZERO; for(final Path path : paths) if(path.target.equals(type)) incoming = incoming.add(path.amount, MC);
            if(incoming.signum() > 0)
            {
                final BigDecimal ratio = incoming.add(pools.get(type), MC).max(ZERO).divide(incoming, MC);
                for(final Path path : paths) if(path.target.equals(type)) path.amount = path.amount.multiply(ratio, MC);
            }
        }
        for(final Path path : paths) if(path.amount.signum() > 0)
        {
                BigDecimal increased = ZERO, more = ONE; final String dest = path.target;
                for(final Modifier m : active) if(m.stat.equals("damage") || m.stat.equals("damage." + dest) || mode.equals("history") && m.stat.equals("damage." + path.source))
                {
                    if(m.op.equals("increased")) increased = increased.add(d(m.value), MC);
                    if(m.op.equals("more")) more = more.multiply(ONE.add(d(m.value).movePointLeft(2), MC), MC);
                }
                final BigDecimal scaled = path.amount.multiply(ONE.add(increased.movePointLeft(2), MC).max(ZERO), MC).multiply(more, MC);
                totals.put(dest, totals.get(dest).add(scaled, MC));
                lines.add(path.source + " -> " + dest + ": " + display(path.amount.doubleValue()) + " scaled to " + display(scaled.doubleValue()) + " (" + mode + ")");
        }
        final Map<String,Double> result = new LinkedHashMap<String,Double>();
        final Map<String,Double> canonical = mode.equals("final") ? c.values(conditions) : null;
        for(final String type : TYPES)
        {
            BigDecimal floor = ZERO, cap = d(100000);
            for(final Modifier m : active) if(m.stat.equals("damage") || m.stat.equals("damage." + type))
            { if(m.op.equals("floor")) floor = floor.max(d(m.value)); if(m.op.equals("cap")) cap = cap.min(d(m.value)); }
            if(floor.compareTo(cap) > 0) throw new IOException("damage." + type + ": floor exceeds cap");
            result.put(type, canonical == null ? totals.get(type).max(floor).min(cap).doubleValue() : canonical.get("damage." + type));
        }
        return new Offense(result, lines);
    }
    public interface Roller { int roll(String reason); }
    public static final class Resolution
    {
        public final Map<String,Integer> damage;
        public final List<String> lines;
        public final boolean hit, blocked, critical;
        private Resolution(final Map<String,Integer> damage, final List<String> lines, final boolean hit, final boolean blocked, final boolean critical)
        { this.damage = Collections.unmodifiableMap(damage); this.lines = Collections.unmodifiableList(lines); this.hit = hit; this.blocked = blocked; this.critical = critical; }
    }
    private static boolean succeeds(final double chance, final String reason, final Roller dice, final List<String> lines) throws IOException
    {
        if(chance <= 0 || chance >= 100) { lines.add(reason + ": " + display(chance) + "% (" + (chance >= 100 ? "certain" : "never") + ")"); return chance >= 100; }
        final int roll = dice.roll(reason); if(roll < 1 || roll > 100) throw new IOException("invalid percentile roll");
        final boolean success = d(roll).compareTo(d(chance)) <= 0; lines.add(reason + ": roll " + roll + " <= " + display(chance) + "%: " + success); return success;
    }
    public static Resolution resolve(final Profile p, final Map<String,Double> offense, final Stats attacker, final Stats target, final boolean spell, final boolean dot, final Roller dice) throws IOException
    {
        final Map<String,Integer> result = new LinkedHashMap<String,Integer>(); for(final String type : TYPES) result.put(type, Integer.valueOf(0));
        final List<String> lines = new ArrayList<String>();
        if(!dot && !spell)
        {
            final BigDecimal accuracy = attacker.values.get("accuracy"), evasion = target.values.get("evasion"), total = accuracy.add(evasion, MC);
            final double chance = bound(total.signum() == 0 ? ZERO : accuracy.multiply(HUNDRED, MC).divide(total, MC), p.minHit, p.maxHit).doubleValue();
            if(!succeeds(chance, "hit", dice, lines)) return new Resolution(result, lines, false, false, false);
        }
        else lines.add(dot ? "Burn tick: no hit, block or critical roll." : "Spell: no accuracy roll.");
        final boolean blocked = !dot && succeeds(Math.min(p.blockCap, target.get(spell ? "spell_block" : "block")), "block", dice, lines);
        if(blocked && p.blockReduction >= 100) { lines.add("Full block: no damage or critical roll."); return new Resolution(result, lines, true, true, false); }
        final boolean critical = !dot && succeeds(attacker.get("crit_chance"), "critical", dice, lines);
        for(final String type : TYPES)
        {
            BigDecimal amount = bound(d(offense.containsKey(type) ? offense.get(type) : 0), 0, p.maxDamage);
            if(amount.signum() == 0) continue;
            if(target.immune.contains(type) || dot && target.immune.contains("burning")) { lines.add(type + ": immune, rejected"); continue; }
            if(critical) amount = amount.multiply(attacker.values.get("crit_multiplier").movePointLeft(2), MC).min(d(p.maxDamage));
            lines.add(type + " after critical: " + display(amount.doubleValue()));
            if(type.equals("physical") && !dot && target.get("armor") > 0)
            {
                final BigDecimal armor = target.values.get("armor");
                final BigDecimal reduction = armor.multiply(HUNDRED, MC).divide(armor.add(amount.multiply(d(p.armorConstant), MC), MC), MC).min(d(p.armorCap));
                amount = amount.multiply(ONE.subtract(reduction.movePointLeft(2), MC), MC); lines.add("physical armor: " + display(reduction.doubleValue()) + "% reduction");
            }
            final BigDecimal rawRes = target.values.get("resistance." + type), capped = bound(rawRes, p.resistanceFloor, p.resistanceCap);
            final BigDecimal penetration = attacker.values.get("penetration." + type), effective = bound(capped.subtract(penetration, MC), p.resistanceFloor, p.resistanceCap);
            amount = amount.multiply(ONE.subtract(effective.movePointLeft(2), MC), MC);
            lines.add(type + " resistance: raw " + display(rawRes.doubleValue()) + ", capped " + display(capped.doubleValue()) + ", penetration " + display(penetration.doubleValue()) + ", effective " + display(effective.doubleValue()) + "%");
            if(blocked) { amount = amount.multiply(ONE.subtract(d(p.blockReduction).movePointLeft(2), MC), MC); lines.add("Partial block: " + display(p.blockReduction) + "% reduction"); }
            final int rounded = amount.max(ZERO).min(d(p.maxDamage)).setScale(0, RoundingMode.HALF_UP).intValueExact();
            result.put(type, Integer.valueOf(rounded)); lines.add(type + " profile result: " + rounded + " (round half up once)");
        }
        return new Resolution(result, lines, true, blocked, critical);
    }
}
