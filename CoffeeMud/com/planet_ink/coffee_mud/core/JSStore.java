package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.util.*;

import org.mozilla.javascript.*;
import org.mozilla.javascript.json.JsonParser;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.interfaces.Quest;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.save.WorldFiles;

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
 * JS7: cm.store(key), a plain JavaScript object kept with the game.  A key
 * is a string ('world', 'quest:name', anything) or an object: a room is
 * keyed by its ID, a mob by its start room and name, an item by where it is
 * and its name, an area and a quest by name.  A save writes each store as
 * JSON in world.xml's &lt;JSSTATE&gt;; a restore puts them back (at the end of
 * WorldState.apply, before onRestore), and a new game starts from
 * world/js/state.json ({"key": {...}, ...}) if the world has one.  Stores
 * hold data: what JSON cannot write (a function, a Java object) is lost at
 * the save.
 */
public final class JSStore
{
	/** the stores in use, as JavaScript objects */
	private static final Map<String, Scriptable>	live	= new TreeMap<String, Scriptable>();
	/** the stores not yet asked for since the restore or new game, as JSON */
	private static final Map<String, String>		saved	= new TreeMap<String, String>();

	private JSStore()
	{
	}

	/**
	 * The key of a store.
	 * @param holder a string, or a room, mob, item, area or quest
	 * @return the key
	 */
	public static String keyOf(final Object holder)
	{
		if(holder instanceof Room)
			return "room:" + CMLib.map().getExtendedRoomID((Room)holder);
		if(holder instanceof MOB)
		{
			final MOB M = (MOB)holder;
			final Room R = (M.getStartRoom() != null) ? M.getStartRoom() : M.location();
			return "mob:" + ((R == null) ? "" : CMLib.map().getExtendedRoomID(R)) + "/" + M.Name();
		}
		if(holder instanceof Item)
		{
			final Item I = (Item)holder;
			final String where;
			if(I.owner() instanceof Room)
				where = CMLib.map().getExtendedRoomID((Room)I.owner());
			else
			if(I.owner() instanceof MOB)
				where = keyOf(I.owner()).substring(4);
			else
				where = "";
			return "item:" + where + "/" + I.Name();
		}
		if(holder instanceof Area)
			return "area:" + ((Area)holder).Name();
		if(holder instanceof Quest)
			return "quest:" + ((Quest)holder).name();
		final String s = (holder == null) ? "world" : holder.toString().trim();
		return (s.length() == 0) ? "world" : s;
	}

	/**
	 * A store, made empty if it is new.
	 * @param cx the context
	 * @param scope the scope asking, for a new object
	 * @param key the store's key
	 * @return the store
	 */
	public static synchronized Scriptable store(final Context cx, final Scriptable scope, final String key)
	{
		Scriptable S = live.get(key);
		if(S != null)
			return S;
		final Scriptable top = ScriptableObject.getTopLevelScope(scope);
		final String json = saved.remove(key);
		if(json != null)
		{
			try
			{
				final Object o = new JsonParser(cx, top).parseValue(json);
				if(o instanceof Scriptable)
					S = (Scriptable)o;
			}
			catch(final JsonParser.ParseException e)
			{
				Log.errOut("JSStore", key + ": " + e.getMessage());
			}
		}
		if(S == null)
			S = cx.newObject(top);
		live.put(key, S);
		return S;
	}

	/**
	 * @param key a store's key
	 * @return true if there is such a store
	 */
	public static synchronized boolean has(final String key)
	{
		return live.containsKey(key) || saved.containsKey(key);
	}

	/** @return the keys of the stores, sorted */
	public static synchronized List<String> keys()
	{
		final TreeSet<String> all = new TreeSet<String>(live.keySet());
		all.addAll(saved.keySet());
		return new ArrayList<String>(all);
	}

	/**
	 * A store as JSON.
	 * @param key its key
	 * @param indent spaces to indent by, or 0 for one line
	 * @return the JSON, or null if there is no such store
	 */
	public static synchronized String json(final String key, final int indent)
	{
		final Scriptable S = live.get(key);
		if(S == null)
		{
			final String j = saved.get(key);
			return ((j == null) || (indent == 0)) ? j : reformat(j, indent);
		}
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Object o = NativeJSON.stringify(cx, ScriptableObject.getTopLevelScope(S), S, null, (indent > 0) ? Integer.valueOf(indent) : null);
			return (o instanceof String) ? (String)o : "{}";
		}
		catch(final RuntimeException e)
		{
			Log.errOut("JSStore", key + ": " + e.getMessage());
			return "{}";
		}
		finally
		{
			Context.exit();
		}
	}

	private static String reformat(final String json, final int indent)
	{
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Scriptable scope = cx.initSafeStandardObjects();
			final Object o = new JsonParser(cx, scope).parseValue(json);
			final Object r = NativeJSON.stringify(cx, scope, o, null, Integer.valueOf(indent));
			return (r instanceof String) ? (String)r : json;
		}
		catch(final Exception e)
		{
			return json;
		}
		finally
		{
			Context.exit();
		}
	}

	/**
	 * Replaces a store (JS STATE key = value).
	 * @param key its key
	 * @param value its new value: a JSON object, or a JavaScript object as a
	 *        script writes one ({gold: 5, who: 'Ann'}), which the command line
	 *        leaves alone where it takes JSON's quotes away
	 * @return null, or what is wrong with the value
	 */
	public static synchronized String set(final String key, final String value)
	{
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		final String json;
		try
		{
			final Scriptable scope = cx.initSafeStandardObjects();
			Object o;
			try
			{
				o = new JsonParser(cx, scope).parseValue(value);
			}
			catch(final JsonParser.ParseException e)
			{
				o = cx.evaluateString(scope, "(" + value + ")", "JS STATE", 1, null);
			}
			if(!(o instanceof NativeObject))
				return "a store is an object, as {gold: 5}";
			final Object j = NativeJSON.stringify(cx, scope, o, null, null);
			if(!(j instanceof String))
				return "it cannot be written as JSON";
			json = (String)j;
		}
		catch(final RuntimeException e)
		{
			return e.getMessage();
		}
		finally
		{
			Context.exit();
		}
		live.remove(key);
		saved.put(key, json);
		return null;
	}

	/**
	 * Takes a store away.
	 * @param key its key
	 * @return true if there was one
	 */
	public static synchronized boolean remove(final String key)
	{
		final boolean had = (live.remove(key) != null);
		return (saved.remove(key) != null) || had;
	}

	/** takes every store away */
	public static synchronized void clear()
	{
		live.clear();
		saved.clear();
	}

	/**
	 * The save's &lt;JSSTATE&gt;.
	 * @return the XML, or "" if there are no stores
	 */
	public static synchronized String toXML()
	{
		final List<String> keys = keys();
		if(keys.isEmpty())
			return "";
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder("<JSSTATE>\n");
		for(final String key : keys)
		{
			final String json = json(key, 0);
			if(json == null)
				continue;
			str.append("<STORE>").append(xml.convertXMLtoTag("KEY", xml.parseOutAngleBrackets(key)))
				.append(xml.convertXMLtoTag("JSON", xml.parseOutAngleBrackets(json))).append("</STORE>\n");
		}
		return str.append("</JSSTATE>\n").toString();
	}

	/**
	 * Puts back the stores of a save, and takes away the rest.
	 * @param tag the &lt;JSSTATE&gt;, or null for none
	 */
	public static synchronized void fromXML(final XMLTag tag)
	{
		clear();
		if(tag == null)
			return;
		final XMLLibrary xml = CMLib.xml();
		for(final XMLTag S : tag.contents())
		{
			if(!S.tag().equalsIgnoreCase("STORE"))
				continue;
			final String key = xml.restoreAngleBrackets(S.getValFromPieces("KEY"));
			final String json = xml.restoreAngleBrackets(S.getValFromPieces("JSON"));
			if((key.length() > 0) && (json.length() > 0))
				saved.put(key, json);
		}
	}

	/** @return world/js/state.json, or null if the world has none */
	public static File stateFile()
	{
		final File world = WorldFiles.worldDir();
		if(world == null)
			return null;
		final File F = new File(new File(world, "js"), "state.json");
		return F.isFile() ? F : null;
	}

	/**
	 * A new game: the stores of world/js/state.json, or none.
	 * @return null, or what was wrong with the file
	 */
	public static synchronized String newGame()
	{
		clear();
		final File F = stateFile();
		if(F == null)
			return null;
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final String text = new String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8");
			final Scriptable scope = cx.initSafeStandardObjects();
			final Object o = new JsonParser(cx, scope).parseValue(text);
			if(!(o instanceof NativeObject))
				return "world/js/state.json is not a JSON object";
			final NativeObject all = (NativeObject)o;
			for(final Object id : all.getIds())
			{
				final Object v = all.get(id.toString(), all);
				final Object j = NativeJSON.stringify(cx, scope, v, null, null);
				if((v instanceof NativeObject) && (j instanceof String))
					saved.put(id.toString(), (String)j);
			}
			return null;
		}
		catch(final Exception e)
		{
			Log.errOut("JSStore", "world/js/state.json: " + e.getMessage());
			return "world/js/state.json: " + e.getMessage();
		}
		finally
		{
			Context.exit();
		}
	}
}
