package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Pure world-owned optional-module definitions. Loading/validation never activates gameplay. */
public final class ModuleDefinitions
{
    private ModuleDefinitions() { }
    public static final int SCHEMA = 1;
    public static final int MAX_FILES = 256, MAX_FILE_BYTES = 262144, MAX_RECORDS = 10000;
    public static final Set<String> SCOPES = names("world", "character", "profile", "item", "relationship", "organization");
    private static Set<String> names(final String... values)
    { return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(values))); }
    public static String id(final Object value) throws IOException
    {
        final String text = string(value, "ID");
        if(!text.matches("[a-z][a-z0-9_.-]{0,95}")) throw new IOException("invalid ID '" + text + "' (lower-case letters, digits, dots, underscores and dashes)");
        return text;
    }
    public static String string(final Object value, final String where) throws IOException
    {
        if(!(value instanceof String) || ((String)value).length() > 4096) throw new IOException(where + " must be a string of at most 4096 characters");
        return (String)value;
    }
    public static JSONObject object(final Object value, final String where) throws IOException
    {
        if(!(value instanceof JSONObject)) throw new IOException(where + " must be an object");
        return (JSONObject)value;
    }
    public static Object[] array(final Object value, final String where) throws IOException
    {
        if(!(value instanceof Object[])) throw new IOException(where + " must be an array");
        return (Object[])value;
    }
    public static int integer(final Object value, final String where) throws IOException
    {
        final long number = whole(value, where);
        if(number < 1 || number > Integer.MAX_VALUE) throw new IOException(where + " must be a positive 32-bit integer");
        return (int)number;
    }
    private static long whole(final Object value, final String where) throws IOException
    {
        if(!(value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte))
            throw new IOException(where + " must be an integer");
        return ((Number)value).longValue();
    }
    public static void keys(final JSONObject obj, final String where, final String... permitted) throws IOException
    {
        final Set<String> allowed = names(permitted);
        for(final String key : obj.keySet()) if(!allowed.contains(key)) throw new IOException(where + ": unknown field '" + key + "'");
    }
    private static JSONObject optionalObject(final JSONObject obj, final String key) throws IOException
    { return obj.containsKey(key) ? object(obj.get(key), key) : new JSONObject(); }
    private static Set<String> strings(final JSONObject obj, final String key) throws IOException
    {
        final Set<String> result = new LinkedHashSet<String>();
        if(obj.containsKey(key)) for(final Object val : array(obj.get(key), key))
        {
            final String name = string(val, key);
            if(!result.add(name)) throw new IOException(key + ": duplicate '" + name + "'");
        }
        return result;
    }
    public static final class Field
    {
        public final String type;
        public final Object initial;
        private final Long minimum, maximum;
        private final Set<String> choices;
        private final int length;
        Field(final JSONObject obj, final String where) throws IOException
        {
            keys(obj, where, "type", "default", "min", "max", "choices", "max_length");
            type = string(obj.get("type"), where + ".type");
            if(!names("integer", "boolean", "string").contains(type)) throw new IOException(where + ": unsupported type '" + type + "'");
            minimum = obj.containsKey("min") ? Long.valueOf(whole(obj.get("min"), where + ".min")) : null;
            maximum = obj.containsKey("max") ? Long.valueOf(whole(obj.get("max"), where + ".max")) : null;
            choices = strings(obj, "choices");
            length = obj.containsKey("max_length") ? integer(obj.get("max_length"), where + ".max_length") : 4096;
            if(length > 4096 || minimum != null && maximum != null && minimum.longValue() > maximum.longValue()) throw new IOException(where + ": invalid bounds");
            if(!type.equals("integer") && (minimum != null || maximum != null) || !type.equals("string") && (!choices.isEmpty() || obj.containsKey("max_length")))
                throw new IOException(where + ": bounds do not match type");
            initial = checked(obj.get("default"), where + ".default");
        }
        Object checked(final Object val, final String where) throws IOException
        {
            if(type.equals("integer"))
            {
                final long number = whole(val, where);
                if(minimum != null && number < minimum.longValue() || maximum != null && number > maximum.longValue()) throw new IOException(where + ": outside declared bounds");
                return Long.valueOf(number);
            }
            if(type.equals("boolean"))
            {
                if(!(val instanceof Boolean)) throw new IOException(where + " must be boolean");
                return val;
            }
            final String text = string(val, where);
            if(text.length() > length || !choices.isEmpty() && !choices.contains(text)) throw new IOException(where + ": string length/choice is invalid");
            return text;
        }
    }
    private static Map<String,Field> fields(final JSONObject obj, final String where) throws IOException
    {
        if(obj.size() > 128) throw new IOException(where + ": at most 128 fields");
        final Map<String,Field> result = new TreeMap<String,Field>();
        for(final String key : obj.keySet()) result.put(id(key), new Field(object(obj.get(key), where + "." + key), where + "." + key));
        return Collections.unmodifiableMap(result);
    }
    public static JSONObject values(final Map<String,Field> schema, final JSONObject supplied, final String where) throws IOException
    {
        for(final String key : supplied.keySet()) if(!schema.containsKey(key)) throw new IOException(where + ": unknown field '" + key + "'");
        final JSONObject result = new JSONObject();
        for(final Map.Entry<String,Field> entry : schema.entrySet()) result.put(entry.getKey(), supplied.containsKey(entry.getKey())
            ? entry.getValue().checked(supplied.get(entry.getKey()), where + "." + entry.getKey()) : entry.getValue().initial);
        return result;
    }
    public static final class Migration
    {
        public final int from, to;
        private final JSONObject stateRenames, settingRenames;
        Migration(final JSONObject obj) throws IOException
        {
            keys(obj, "migration", "from", "to", "state_renames", "setting_renames");
            from = integer(obj.get("from"), "migration.from"); to = integer(obj.get("to"), "migration.to");
            if(to <= from) throw new IOException("migration.to must exceed from");
            stateRenames = renames(optionalObject(obj, "state_renames"));
            settingRenames = renames(optionalObject(obj, "setting_renames"));
        }
        private JSONObject renames(final JSONObject obj) throws IOException
        {
            final Set<String> targets = new HashSet<String>();
            for(final String name : obj.keySet()) if(!targets.add(id(obj.get(id(name))))) throw new IOException("migration: duplicate rename destination");
            return obj;
        }
        public JSONObject apply(final JSONObject input, final boolean settings) throws IOException
        {
            final JSONObject next = copy(input), mappings = settings ? settingRenames : stateRenames;
            // Simultaneous renames permit swaps without overwriting either source.
            for(final String source : mappings.keySet()) if(input.containsKey(source)) next.remove(source);
            for(final String source : mappings.keySet()) if(input.containsKey(source))
            {
                final String target = (String)mappings.get(source);
                if(next.containsKey(target)) throw new IOException("migration would overwrite '" + target + "'");
                next.put(target, input.get(source));
            }
            return next;
        }
    }
    public static final class Definition
    {
        public final String id, title, path, removal, handler, fingerprint;
        public final int version;
        public final Set<String> conflicts, modes, scopes, provides;
        public final Map<String,Integer> requires;
        public final Map<String,Field> settings, state;
        public final List<JSONObject> references;
        public final Map<Integer,Migration> migrations;
        Definition(final JSONObject obj, final String source) throws IOException
        {
            keys(obj, source, "schema", "id", "title", "version", "requires", "conflicts", "clock_modes", "scopes", "settings", "state", "on_disable", "handler", "provides", "references", "migrations");
            if(integer(obj.get("schema"), "schema") != SCHEMA) throw new IOException("unsupported manifest schema");
            id = id(obj.get("id")); title = obj.containsKey("title") ? string(obj.get("title"), "title") : id;
            path = source; version = integer(obj.get("version"), "version");
            removal = obj.containsKey("on_disable") ? string(obj.get("on_disable"), "on_disable") : "suspend";
            if(!names("suspend", "refuse").contains(removal)) throw new IOException("on_disable must be suspend or refuse");
            handler = obj.containsKey("handler") ? string(obj.get("handler"), "handler") : "data";
            if(!names("data", "example.counter").contains(handler)) throw new IOException("unsupported handler '" + handler + "'");
            final Map<String,Integer> deps = new TreeMap<String,Integer>();
            final JSONObject requested = optionalObject(obj, "requires");
            for(final String dep : requested.keySet()) deps.put(id(dep), Integer.valueOf(integer(requested.get(dep), "requires." + dep)));
            if(deps.containsKey(id)) throw new IOException("a module cannot require itself");
            requires = Collections.unmodifiableMap(deps);
            conflicts = Collections.unmodifiableSet(strings(obj, "conflicts"));
            for(final String conflict : conflicts) id(conflict);
            if(conflicts.contains(id)) throw new IOException("a module cannot conflict with itself");
            final Set<String> times = strings(obj, "clock_modes");
            if(times.isEmpty() || !names("TURNS", "REALTIME").containsAll(times)) throw new IOException("clock_modes must list TURNS and/or REALTIME");
            modes = Collections.unmodifiableSet(times);
            final Set<String> owners = strings(obj, "scopes");
            if(owners.isEmpty() || !SCOPES.containsAll(owners)) throw new IOException("scopes must name supported state owners");
            scopes = Collections.unmodifiableSet(owners);
            settings = fields(optionalObject(obj, "settings"), "settings"); state = fields(optionalObject(obj, "state"), "state");
            if(handler.equals("example.counter") && (state.size() != 1 || !state.containsKey("count") || !state.get("count").type.equals("integer")
                || !settings.containsKey("step") || !settings.get("step").type.equals("integer") || !scopes.contains("character")))
                throw new IOException("example.counter requires integer state.count, integer settings.step and character scope");
            provides = Collections.unmodifiableSet(strings(obj, "provides")); for(final String name : provides) id(name);
            final List<JSONObject> refs = new ArrayList<JSONObject>();
            if(obj.containsKey("references")) for(final Object ref : array(obj.get("references"), "references"))
            {
                final JSONObject target = object(ref, "reference"); keys(target, "reference", "module", "name");
                id(target.get("module")); id(target.get("name")); refs.add(target);
            }
            references = Collections.unmodifiableList(refs);
            final Map<Integer,Migration> routes = new TreeMap<Integer,Migration>();
            if(obj.containsKey("migrations")) for(final Object raw : array(obj.get("migrations"), "migrations"))
            {
                final Migration migration = new Migration(object(raw, "migration"));
                if(migration.to > version || routes.put(Integer.valueOf(migration.from), migration) != null) throw new IOException("ambiguous or future migration");
            }
            migrations = Collections.unmodifiableMap(routes);
            for(final Integer from : migrations.keySet()) route(from.intValue());
            for(final Migration migration : migrations.values()) if(migration.to == version)
            {
                for(final Object target : migration.stateRenames.values()) if(!state.containsKey(target)) throw new IOException("migration: unknown final state field " + target);
                for(final Object target : migration.settingRenames.values()) if(!settings.containsKey(target)) throw new IOException("migration: unknown final setting " + target);
            }
            final JSONObject contract = copy(obj); contract.remove("title"); contract.remove("migrations");
            fingerprint = hash(json(contract));
        }
        public List<Migration> route(final int from) throws IOException
        {
            final List<Migration> route = new ArrayList<Migration>(); int at = from;
            while(at != version)
            {
                final Migration next = migrations.get(Integer.valueOf(at));
                if(next == null) throw new IOException(id + ": no migration from version " + at + " to " + version);
                route.add(next); at = next.to;
            }
            return route;
        }
    }
    public static final class Plan
    {
        public final Map<String,Definition> definitions = new TreeMap<String,Definition>();
        public final Map<String,JSONObject> settings = new TreeMap<String,JSONObject>();
        public final Map<String,Set<String>> reasons = new TreeMap<String,Set<String>>();
        public final Set<String> roots = new TreeSet<String>(), enabled = new LinkedHashSet<String>();
        public final List<String> errors = new ArrayList<String>();
        public final File world;
        private Plan(final File root) { world = root; }
        public String problems() { return String.join("\n", errors); }
        public void requireValid() throws IOException { if(!errors.isEmpty()) throw new IOException(problems()); }
    }
    public static Plan load(final File world, final String selection, final String rawSettings)
    {
        final Plan plan = new Plan(world);
        final File dir = new File(world, "modules");
        if(dir.exists() && !dir.isDirectory()) plan.errors.add("modules/: must be a directory");
        final File[] listed = dir.listFiles(f -> f.getName().endsWith(".json"));
        if(listed != null)
        {
            Arrays.sort(listed, Comparator.comparing(File::getName));
            if(listed.length > MAX_FILES) plan.errors.add("modules/: at most " + MAX_FILES + " manifests");
            for(int i = 0; i < Math.min(listed.length, MAX_FILES); i++)
            {
                final File file = listed[i]; final String path = "modules/" + file.getName();
                try
                {
                    if(!file.isFile() || Files.isSymbolicLink(file.toPath()) || !file.getCanonicalPath().startsWith(world.getCanonicalPath() + File.separator)) throw new IOException("must be a regular file inside the world");
                    final Definition def = new Definition(parse(read(file)), path);
                    if(!file.getName().equals(def.id + ".json")) throw new IOException("filename must be " + def.id + ".json");
                    if(plan.definitions.put(def.id, def) != null) throw new IOException("duplicate module ID " + def.id);
                }
                catch(final Exception e) { plan.errors.add(path + ": " + e.getMessage()); }
            }
        }
        try
        {
            final JSONObject configured = rawSettings == null || rawSettings.trim().isEmpty() ? new JSONObject() : parse(rawSettings);
            for(final String name : configured.keySet()) if(!plan.definitions.containsKey(name)) throw new IOException("MODULESETTINGS: unknown module " + name);
            for(final Definition def : plan.definitions.values()) plan.settings.put(def.id,
                values(def.settings, configured.containsKey(def.id) ? object(configured.get(def.id), "MODULESETTINGS." + def.id) : new JSONObject(), def.path + " settings"));
        }
        catch(final IOException e) { plan.errors.add(e.getMessage()); }
        if(selection != null && !selection.trim().isEmpty()) for(final String raw : selection.split(",", -1))
        {
            try { final String root = id(raw.trim()); if(!plan.roots.add(root)) throw new IOException("duplicate selected module " + root); }
            catch(final IOException e) { plan.errors.add("MODULES: " + e.getMessage()); }
        }
        // Validate all definitions, including disabled ones, without enabling them.
        for(final Definition def : plan.definitions.values())
        {
            for(final Map.Entry<String,Integer> dep : def.requires.entrySet())
            {
                final Definition target = plan.definitions.get(dep.getKey());
                if(target == null || target.version != dep.getValue().intValue()) plan.errors.add(def.path + ": requires " + dep.getKey() + " version " + dep.getValue());
            }
            for(final JSONObject ref : def.references)
            {
                final String module = (String)ref.get("module"), name = (String)ref.get("name");
                final Definition target = plan.definitions.get(module);
                if(target == null || !target.provides.contains(name) || !module.equals(def.id) && !def.requires.containsKey(module))
                    plan.errors.add(def.path + ": unresolved or undeclared reference " + module + "/" + name);
            }
        }
        final Set<String> checked = new HashSet<String>();
        for(final String name : plan.definitions.keySet()) visit(plan, name, new LinkedHashSet<String>(), checked, null);
        for(final String root : plan.roots) visit(plan, root, new LinkedHashSet<String>(), new HashSet<String>(), root);
        for(final String name : plan.enabled)
        {
            final Definition def = plan.definitions.get(name);
            for(final String conflict : def.conflicts) if(plan.enabled.contains(conflict)) plan.errors.add(def.path + ": enabled conflict with " + conflict);
        }
        return plan;
    }
    private static void visit(final Plan plan, final String name, final Set<String> active, final Set<String> done, final String root)
    {
        if(active.contains(name)) { plan.errors.add("modules/: dependency cycle " + String.join(" -> ", active) + " -> " + name); return; }
        if(done.contains(name)) return;
        final Definition def = plan.definitions.get(name);
        if(def == null) { if(root != null) plan.errors.add("MODULES: unknown module " + name); return; }
        active.add(name);
        for(final String dep : def.requires.keySet()) visit(plan, dep, active, done, root);
        active.remove(name); done.add(name);
        if(root != null)
        {
            plan.enabled.add(name);
            plan.reasons.computeIfAbsent(name, k -> new TreeSet<String>()).add(root);
        }
    }
    public static String read(final File file) throws IOException
    {
        if(file.length() > MAX_FILE_BYTES) throw new IOException("file exceeds " + MAX_FILE_BYTES + " bytes");
        final byte[] bytes = Files.readAllBytes(file.toPath());
        if(bytes.length > MAX_FILE_BYTES) throw new IOException("file exceeds limit");
        return new String(bytes, StandardCharsets.UTF_8);
    }
    public static JSONObject copy(final JSONObject obj) throws IOException { return parse(json(obj)); }
    public static JSONObject parse(final String text) throws IOException
    {
        try
        {
            new StrictJson(text).check();
            return object(new MiniJSON().parse(text), "JSON");
        }
        catch(final IOException e) { throw e; }
        catch(final Exception e) { throw new IOException("invalid JSON: " + e.getMessage(), e); }
    }
    /** Sorted-key, Unicode-preserving JSON used for save data and contract hashes. */
    public static String json(final Object value)
    {
        if(value instanceof Map)
        {
            final StringBuilder out = new StringBuilder("{"); boolean first = true;
            final Map<?,?> map = (Map<?,?>)value;
            final List<String> keys = new ArrayList<String>(); for(final Object key : map.keySet()) keys.add((String)key); Collections.sort(keys);
            for(final String key : keys) { if(!first) out.append(','); first = false; out.append(json(key)).append(':').append(json(map.get(key))); }
            return out.append('}').toString();
        }
        if(value instanceof Collection || value instanceof Object[])
        {
            final Collection<?> values = value instanceof Collection ? (Collection<?>)value : Arrays.asList((Object[])value);
            final StringBuilder out = new StringBuilder("["); boolean first = true;
            for(final Object item : values) { if(!first) out.append(','); first = false; out.append(json(item)); }
            return out.append(']').toString();
        }
        if(value == null || value == MiniJSON.NULL) return "null";
        if(value instanceof String)
        {
            final StringBuilder out = new StringBuilder("\"");
            for(final char ch : ((String)value).toCharArray())
            {
                if(ch == '"' || ch == '\\') out.append('\\').append(ch);
                else if(ch < 32) out.append(String.format("\\u%04x", Integer.valueOf(ch)));
                else out.append(ch);
            }
            return out.append('"').toString();
        }
        return value.toString();
    }
    private static String hash(final String input) throws IOException
    {
        try
        {
            final byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            final StringBuilder out = new StringBuilder(); for(final byte b : bytes) out.append(String.format("%02x", Integer.valueOf(b & 255)));
            return out.toString();
        }
        catch(final Exception e) { throw new IOException(e); }
    }
    /** Small lexical check adds duplicate-key, depth and trailing-data guarantees to MiniJSON. */
    private static final class StrictJson
    {
        private final String text; private int at = 0;
        StrictJson(final String raw) { text = raw; }
        void space() { while(at < text.length() && " \r\n\t".indexOf(text.charAt(at)) >= 0) at++; }
        void fail() throws IOException { throw new IOException("invalid JSON at character " + at); }
        void check() throws Exception { value(0); space(); if(at != text.length()) fail(); }
        String word() throws Exception
        {
            final int start = at++; boolean escape = false;
            while(at < text.length())
            {
                final char ch = text.charAt(at++);
                if(ch < 32) fail();
                if(!escape && ch == '"') return (String)new MiniJSON().parse(text.substring(start, at));
                if(!escape && ch == '\\') escape = true; else escape = false;
            }
            fail(); return "";
        }
        void value(final int depth) throws Exception
        {
            space(); if(depth > 32 || at >= text.length()) fail(); final char ch = text.charAt(at);
            if(ch == '"') { word(); return; }
            if(ch == '{' || ch == '[')
            {
                final char end = ch == '{' ? '}' : ']'; at++; space(); final Set<String> keys = new HashSet<String>();
                if(at < text.length() && text.charAt(at) == end) { at++; return; }
                while(true)
                {
                    if(ch == '{')
                    {
                        space(); if(at >= text.length() || text.charAt(at) != '"') fail();
                        if(!keys.add(word())) throw new IOException("duplicate JSON key near character " + at);
                        space(); if(at >= text.length() || text.charAt(at++) != ':') fail();
                    }
                    value(depth + 1); space(); if(at >= text.length()) fail();
                    final char next = text.charAt(at++); if(next == end) return; if(next != ',') fail();
                }
            }
            final int start = at;
            while(at < text.length() && ",]} \r\n\t".indexOf(text.charAt(at)) < 0) at++;
            final String token = text.substring(start, at);
            if(!token.matches("true|false|null|-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) fail();
        }
    }
}
