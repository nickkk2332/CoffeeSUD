package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.core.threads.ServiceEngine;

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
 * The definition families of the optional modules that came after the first
 * three (resources, triggers, rewards, encounters, expeditions, tracks): one
 * loader for world/[folder]/[id].v[N].json, the checks of a definition's
 * fields with the file and JSON path of each problem, pins, and the registry
 * that WORLD, WORLD CHECK and the boot reset go through.
 */
public final class ModuleContent
{
	private ModuleContent()
	{
	}

	/** the largest definition file */
	public static final int		MAX_BYTES	= 65536;
	/** the most definitions in one family */
	public static final int		MAX_FILES	= 256;

	/**
	 * One JSON object of a definition, at a path in its file, with checked
	 * reads: each problem names the file and the path.
	 */
	public static final class Node
	{
		public final String		file;
		public final String		path;
		public final JSONObject	obj;

		public Node(final String file, final String path, final JSONObject obj)
		{
			this.file = file;
			this.path = path;
			this.obj = obj;
		}

		/** where a key is: its JSON path */
		public String at(final String key)
		{
			return path + "/" + key;
		}

		/** a problem at a key of this object */
		public IOException error(final String key, final String message)
		{
			return new IOException(file + ": " + ((key == null) ? (path.length() == 0 ? "/" : path) : at(key)) + ": " + message);
		}

		/** refuses keys other than these */
		public void allow(final String... keys) throws IOException
		{
			final Set<String> known = new HashSet<String>(Arrays.asList(keys));
			for(final String key : obj.keySet())
			{
				if(!known.contains(key))
					throw error(key, "unknown field");
			}
		}

		public boolean has(final String key)
		{
			return obj.containsKey(key) && (obj.get(key) != MiniJSON.NULL);
		}

		public String string(final String key) throws IOException
		{
			if(!has(key))
				throw error(key, "required");
			return string(key, "", 4096);
		}

		public String string(final String key, final String fallback, final int maxLength) throws IOException
		{
			if(!has(key))
				return fallback;
			final Object raw = obj.get(key);
			if(!(raw instanceof String))
				throw error(key, "must be a string");
			final String s = (String)raw;
			if(s.length() > maxLength)
				throw error(key, "at most " + maxLength + " characters");
			return s;
		}

		public String choice(final String key, final String fallback, final String... choices) throws IOException
		{
			final String s = string(key, fallback, 4096);
			for(final String c : choices)
			{
				if(c.equals(s))
					return s;
			}
			throw error(key, "must be one of " + String.join(", ", choices));
		}

		/** an ID, as module and definition IDs are written */
		public String id(final String key) throws IOException
		{
			final String s = string(key);
			try
			{
				return ModuleDefinitions.id(s);
			}
			catch(final IOException e)
			{
				throw error(key, e.getMessage());
			}
		}

		public long integer(final String key, final long min, final long max) throws IOException
		{
			if(!has(key))
				throw error(key, "required");
			return integer(key, min, min, max);
		}

		public long integer(final String key, final long fallback, final long min, final long max) throws IOException
		{
			if(!has(key))
				return fallback;
			final Object raw = obj.get(key);
			if(!(raw instanceof Number))
				throw error(key, "must be a whole number");
			final double d = ((Number)raw).doubleValue();
			if((d != Math.rint(d)) || Double.isInfinite(d))
				throw error(key, "must be a whole number");
			final long n = ((Number)raw).longValue();
			if((n < min) || (n > max))
				throw error(key, "must be from " + min + " to " + max);
			return n;
		}

		public double number(final String key, final double fallback, final double min, final double max) throws IOException
		{
			if(!has(key))
				return fallback;
			final Object raw = obj.get(key);
			if(!(raw instanceof Number))
				throw error(key, "must be a number");
			final double d = ((Number)raw).doubleValue();
			if(Double.isNaN(d) || Double.isInfinite(d) || (d < min) || (d > max))
				throw error(key, "must be from " + min + " to " + max);
			return d;
		}

		public boolean bool(final String key, final boolean fallback) throws IOException
		{
			if(!has(key))
				return fallback;
			final Object raw = obj.get(key);
			if(!(raw instanceof Boolean))
				throw error(key, "must be true or false");
			return ((Boolean)raw).booleanValue();
		}

		/** an object, or null when it is not there */
		public Node object(final String key) throws IOException
		{
			if(!has(key))
				return null;
			final Object raw = obj.get(key);
			if(!(raw instanceof JSONObject))
				throw error(key, "must be an object");
			return new Node(file, at(key), (JSONObject)raw);
		}

		public Node required(final String key) throws IOException
		{
			final Node n = object(key);
			if(n == null)
				throw error(key, "required");
			return n;
		}

		private Object[] array(final String key, final int max) throws IOException
		{
			if(!has(key))
				return new Object[0];
			final Object raw = obj.get(key);
			if(!(raw instanceof Object[]) && !(raw instanceof List))
				throw error(key, "must be an array");
			final Object[] all = (raw instanceof Object[]) ? (Object[])raw : ((List<?>)raw).toArray();
			if(all.length > max)
				throw error(key, "at most " + max + " entries");
			return all;
		}

		/** an array of objects, none when it is not there */
		public List<Node> objects(final String key, final int max) throws IOException
		{
			final List<Node> out = new ArrayList<Node>();
			final Object[] all = array(key, max);
			for(int i = 0; i < all.length; i++)
			{
				if(!(all[i] instanceof JSONObject))
					throw new IOException(file + ": " + at(key) + "/" + i + ": must be an object");
				out.add(new Node(file, at(key) + "/" + i, (JSONObject)all[i]));
			}
			return out;
		}

		/** an array of strings, none when it is not there; duplicates refused */
		public List<String> strings(final String key, final int max, final boolean ids) throws IOException
		{
			final List<String> out = new ArrayList<String>();
			final Object[] all = array(key, max);
			for(int i = 0; i < all.length; i++)
			{
				if(!(all[i] instanceof String))
					throw new IOException(file + ": " + at(key) + "/" + i + ": must be a string");
				String s = (String)all[i];
				if(ids)
				{
					try
					{
						s = ModuleDefinitions.id(s);
					}
					catch(final IOException e)
					{
						throw new IOException(file + ": " + at(key) + "/" + i + ": " + e.getMessage());
					}
				}
				if(out.contains(s))
					throw new IOException(file + ": " + at(key) + "/" + i + ": duplicate " + s);
				out.add(s);
			}
			return out;
		}

		/** an object of IDs to whole numbers, in key order */
		public Map<String, Long> amounts(final String key, final long min, final long max, final int maxEntries) throws IOException
		{
			final Map<String, Long> out = new TreeMap<String, Long>();
			final Node n = object(key);
			if(n == null)
				return out;
			if(n.obj.size() > maxEntries)
				throw error(key, "at most " + maxEntries + " entries");
			for(final String k : n.obj.keySet())
			{
				try
				{
					ModuleDefinitions.id(k);
				}
				catch(final IOException e)
				{
					throw n.error(k, e.getMessage());
				}
				out.put(k, Long.valueOf(n.integer(k, min, max)));
			}
			return out;
		}
	}

	/** reads one definition's own fields, after its schema, ID, version and title */
	public interface Reader<T>
	{
		T read(Node node) throws IOException;
	}

	/** one installed definition version */
	public static final class Entry<T>
	{
		public final String		id;
		public final int		version;
		public final String		title;
		public final String		path;
		public final String		hash;
		public final JSONObject	raw;
		public final T			value;

		Entry(final String id, final int version, final String title, final String path, final String hash, final JSONObject raw, final T value)
		{
			this.id = id;
			this.version = version;
			this.title = title;
			this.path = path;
			this.hash = hash;
			this.raw = raw;
			this.value = value;
		}

		public String key()
		{
			return id + ".v" + version;
		}

		public JSONObject pin()
		{
			final JSONObject p = new JSONObject();
			p.put("id", id);
			p.put("version", Long.valueOf(version));
			p.put("hash", hash);
			return p;
		}
	}

	/** a family's installed definitions, every version, and the problems found */
	public static final class Library<T>
	{
		public final String					folder;
		public final String					kind;
		public final Map<String, Entry<T>>	entries	= new TreeMap<String, Entry<T>>();
		public final List<String>			errors	= new ArrayList<String>();

		Library(final String folder, final String kind)
		{
			this.folder = folder;
			this.kind = kind;
		}

		/** the highest installed version of a definition */
		public Entry<T> latest(final String id) throws IOException
		{
			Entry<T> best = null;
			for(final Entry<T> e : entries.values())
			{
				if(e.id.equals(id) && ((best == null) || (e.version > best.version)))
					best = e;
			}
			if(best == null)
				throw new IOException("unknown " + kind + " " + id);
			return best;
		}

		/** the latest version of each definition */
		public List<Entry<T>> latestAll()
		{
			final Map<String, Entry<T>> best = new TreeMap<String, Entry<T>>();
			for(final Entry<T> e : entries.values())
			{
				final Entry<T> b = best.get(e.id);
				if((b == null) || (e.version > b.version))
					best.put(e.id, e);
			}
			return new ArrayList<Entry<T>>(best.values());
		}

		public boolean has(final String id)
		{
			for(final Entry<T> e : entries.values())
			{
				if(e.id.equals(id))
					return true;
			}
			return false;
		}

		/**
		 * The version a save pinned: refused, with the reason, when it is not
		 * installed or changed without a new version.
		 */
		public Entry<T> pinned(final Object pin) throws IOException
		{
			final JSONObject p = checkPin(pin);
			final String id = (String)p.get("id");
			final int version = ((Number)p.get("version")).intValue();
			final Entry<T> e = entries.get(id + ".v" + version);
			if(e == null)
				throw new IOException(kind + " " + id + " v" + version + " is not installed");
			if(!e.hash.equals(p.get("hash")))
				throw new IOException(kind + " " + id + " v" + version + " (" + e.path + ") changed without a new version");
			return e;
		}

		public String report()
		{
			if(errors.isEmpty())
				return CMStrings.capitalizeFirstLetter(kind) + " definitions are valid (" + entries.size() + " versions in " + folder + "/).";
			return String.join("\n", errors);
		}
	}

	/** checks a pin's form */
	public static JSONObject checkPin(final Object raw) throws IOException
	{
		final JSONObject p = ModuleDefinitions.object(raw, "definition pin");
		ModuleDefinitions.keys(p, "definition pin", "id", "version", "hash");
		ModuleDefinitions.id(p.get("id"));
		ModuleDefinitions.integer(p.get("version"), "pinned version");
		final String h = ModuleDefinitions.string(p.get("hash"), "pinned hash");
		if(!h.matches("[0-9a-f]{64}"))
			throw new IOException("a pinned hash must be 64 lower-case hex digits");
		return p;
	}

	/** the canonical hash of a definition: all but its title */
	public static String hash(final JSONObject raw) throws IOException
	{
		final JSONObject copy = new JSONObject();
		for(final String k : raw.keySet())
		{
			if(!k.equals("title"))
				copy.put(k, raw.get(k));
		}
		return ItemModifierRules.hash(copy);
	}

	/**
	 * Loads a family's definitions: world/[folder]/[id].v[N].json, each an
	 * object with schema 1, id, version, title and the family's own fields.
	 * A bad file is a problem in the library, not an exception.
	 * @param world the world directory
	 * @param folder the family's folder
	 * @param kind what one definition is called, for messages
	 * @param reader the family's reader
	 * @return the library
	 */
	public static <T> Library<T> load(final File world, final String folder, final String kind, final Reader<T> reader)
	{
		final Library<T> lib = new Library<T>(folder, kind);
		if(world == null)
			return lib;
		final File dir = new File(world, folder);
		if(Files.isSymbolicLink(dir.toPath()))
		{
			lib.errors.add(folder + "/: must not be a symbolic link");
			return lib;
		}
		if(dir.exists() && !dir.isDirectory())
		{
			lib.errors.add(folder + "/: must be a directory");
			return lib;
		}
		final File[] files = dir.listFiles(f -> f.getName().endsWith(".json"));
		if(files == null)
			return lib;
		Arrays.sort(files, Comparator.comparing(File::getName));
		if(files.length > MAX_FILES)
			lib.errors.add(folder + "/: at most " + MAX_FILES + " definitions");
		for(int i = 0; i < Math.min(files.length, MAX_FILES); i++)
		{
			final File file = files[i];
			final String path = folder + "/" + file.getName();
			try
			{
				if(!Files.isRegularFile(file.toPath()) || Files.isSymbolicLink(file.toPath())
				|| !file.getCanonicalPath().startsWith(world.getCanonicalPath() + File.separator))
					throw new IOException(path + ": must be a regular file inside the world");
				if(file.length() > MAX_BYTES)
					throw new IOException(path + ": larger than " + (MAX_BYTES / 1024) + " KiB");
				String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
				if(text.startsWith("﻿"))
					text = text.substring(1);
				final JSONObject raw;
				try
				{
					raw = ModuleDefinitions.parse(text);
				}
				catch(final IOException e)
				{
					throw new IOException(path + ": " + e.getMessage());
				}
				final Node node = new Node(path, "", raw);
				if(node.integer("schema", 1, 1) != 1)
					throw node.error("schema", "must be 1");
				final String id = node.id("id");
				final int version = (int)node.integer("version", 1, 1000000);
				final String title = node.string("title", id, 200);
				if(!file.getName().equals(id + ".v" + version + ".json"))
					throw new IOException(path + ": the file must be named " + id + ".v" + version + ".json");
				final T value = reader.read(node);
				final Entry<T> e = new Entry<T>(id, version, title, path, hash(raw), raw, value);
				if(lib.entries.put(e.key(), e) != null)
					throw new IOException(path + ": duplicate " + e.key());
			}
			catch(final IOException e)
			{
				lib.errors.add(e.getMessage());
			}
			catch(final RuntimeException e)
			{
				lib.errors.add(path + ": " + e);
			}
		}
		return lib;
	}

	/**
	 * Refuses a change to the world off the game thread, as the item
	 * transactions do: only the game thread (or the world loading at boot,
	 * before it starts) may settle module state that touches the world.
	 */
	public static void gameThread() throws IOException
	{
		if(ServiceEngine.isGameThread())
			return;
		final CMProps host = CMProps.instance(com.planet_ink.coffee_mud.core.interfaces.MudHost.MAIN_HOST);
		final boolean booting = CMProps.isSinglePlayer() && !ServiceEngine.hasGameThread() && (host != null)
			&& (host._isState(CMProps.HostState.BOOTING) || host._isState(CMProps.HostState.LOADINGMAP) || host._isState(CMProps.HostState.STARTING));
		if(!booting)
			throw new IOException("this must run on the game thread");
	}

	/** a family of definitions with a module, as the registry knows it */
	public interface Family
	{
		/** the module and native handler ID */
		String module();

		/** the WORLD CHECK part and WORLD subcommand, upper case */
		String part();

		/** the world folder */
		String folder();

		/** the state-owner scopes the module's manifest must allow */
		List<String> scopes();

		/** the module's JSON-object string state fields, empty by default */
		List<String> stringState();

		/** the modules this one's manifest must require */
		List<String> requires();

		/** forgets loaded definitions and runtime state, at boot */
		void reset();

		/** the problems in the installed definitions */
		List<String> check();

		/** the installed definitions, for WORLD [part] LIST */
		String list() throws IOException;

		/** reloads the definitions, for WORLD [part] RELOAD */
		String reload() throws IOException;

		/**
		 * The family's own WORLD [part] subcommands beyond LIST, CHECK and
		 * RELOAD, or null when the words are not one of them.
		 */
		String world(MOB mob, List<String> words) throws IOException;
	}

	private static final List<Family> families = new ArrayList<Family>();

	/** registers a family, once; called from each family's class */
	public static synchronized void register(final Family f)
	{
		for(final Family g : families)
		{
			if(g.module().equals(f.module()))
				return;
		}
		families.add(f);
	}

	/** the registered families, in the order of {@link #known()} */
	public static synchronized List<Family> families()
	{
		known();
		return new ArrayList<Family>(families);
	}

	/** the family a WORLD CHECK part or WORLD subcommand names, or null */
	public static Family family(final String part)
	{
		for(final Family f : families())
		{
			if(f.part().equalsIgnoreCase(part))
				return f;
		}
		return null;
	}

	/** the family of a module ID, or null */
	public static Family ofModule(final String module)
	{
		for(final Family f : families())
		{
			if(f.module().equals(module))
				return f;
		}
		return null;
	}

	private static volatile boolean loaded = false;

	/** loads each family's class, so that it registers */
	private static void known()
	{
		if(loaded)
			return;
		loaded = true;
		for(final String cls : KNOWN)
		{
			try
			{
				Class.forName(cls, true, ModuleContent.class.getClassLoader());
			}
			catch(final Throwable t)
			{
				Log.errOut("ModuleContent", "could not load " + cls + ": " + t);
			}
		}
	}

	/** the family classes, each registering itself when loaded */
	private static final String[] KNOWN = {
		"com.planet_ink.coffee_mud.core.NamedResources",
	};

	/** resets every family, at boot */
	public static void resetAll()
	{
		for(final Family f : families())
		{
			try
			{
				f.reset();
			}
			catch(final RuntimeException e)
			{
				Log.errOut("ModuleContent", f.module() + " reset: " + e);
			}
		}
		RandomStreams.reset();
		ModuleStats.reset();
	}

	/** the world directory, for the families' loaders */
	public static File world()
	{
		return WorldFiles.worldDir();
	}
}
