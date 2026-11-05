package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.util.*;

import org.mozilla.javascript.*;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
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
 * JS12: JCONSOLE @target binds the console to a mob, item, room (here) or
 * area: its MOBPROG calls run as the target, with the builder as source,
 * and me, here and cm are the target's.  Lines that start with a dot are
 * the console's own: .trigger GREET|SPEECH text|GIVE item|LOOK|TICK fires
 * that event on the target (its JScriptable and its scripts hear it);
 * .load file runs a file in the console; .reset clears the console;
 * .inspect expr shows what an expression gives (name, class, where, and its
 * stats); .time on|off shows how long each line took; .history shows the
 * lines typed before, and !n runs line n again.  In builder mode the
 * history is kept, in world/.history/, from one game to the next.
 */
public final class JSConsole
{
	/** what .reset returns: the caller drops the console's scope */
	public static final String	RESET	= "\u0000RESET";

	private static final Map<String, List<String>>	histories	= new HashMap<String, List<String>>();
	private static final Set<String>				timed		= new HashSet<String>();
	private static final int						KEEP		= 200;

	private JSConsole()
	{
	}

	/**
	 * What JCONSOLE @word names: here, area, me, or a mob or item by name,
	 * in the builder's room, carried, then anywhere.
	 * @param builder the builder
	 * @param word the word after @
	 * @return the thing, or null
	 */
	public static PhysicalAgent target(final MOB builder, final String word)
	{
		final String w = (word == null) ? "" : word.trim();
		final Room R = builder.location();
		if(w.equalsIgnoreCase("here") || w.equalsIgnoreCase("room"))
			return R;
		if(w.equalsIgnoreCase("area"))
			return (R == null) ? null : R.getArea();
		if(w.equalsIgnoreCase("me") || w.equalsIgnoreCase("self"))
			return builder;
		if(R != null)
		{
			final MOB M = R.fetchInhabitant(w);
			if(M != null)
				return M;
			final Item I = R.findItem(null, w);
			if(I != null)
				return I;
		}
		final Item C = builder.findItem(null, w);
		if(C != null)
			return C;
		final Room W = CMLib.map().getRoom(w);
		if(W != null)
			return W;
		final JSCm cm = new JSCm(builder);
		final MOB M = cm.findMob(w);
		if(M != null)
			return M;
		return cm.findItem(w);
	}

	/**
	 * @param P a target
	 * @return how its console is known: the room's ID, or its name
	 */
	public static String key(final PhysicalAgent P)
	{
		if(P instanceof Room)
			return CMLib.map().getExtendedRoomID((Room)P);
		return (P == null) ? "" : P.Name();
	}

	/**
	 * Before each line: the builder's here, area and target (JS6), and for a
	 * bound console the target's me and here.
	 * @param scope the console's scope
	 * @param builder the builder
	 * @param target the target, or null
	 */
	public static void bind(final Scriptable scope, final MOB builder, final PhysicalAgent target)
	{
		JSScope.where(scope, builder);
		if(target == null)
		{
			ScriptableObject.putProperty(scope, "me", Context.javaToJS(builder, scope));
			return;
		}
		ScriptableObject.putProperty(scope, "me", Context.javaToJS(target, scope));
		final Room R = (target instanceof Room) ? (Room)target : CMLib.map().roomLocation(target);
		if(R != null)
		{
			ScriptableObject.putProperty(scope, "here", Context.javaToJS(R, scope));
			ScriptableObject.putProperty(scope, "area", Context.javaToJS((target instanceof Area) ? target : R.getArea(), scope));
		}
	}

	/**
	 * Runs one line of the console: a dot command, !n, or JavaScript.
	 * @param cx the context
	 * @param scope the console's scope
	 * @param builder the builder
	 * @param target the target, or null
	 * @param line the line
	 * @return what to show, or RESET
	 */
	public static String run(final Context cx, final Scriptable scope, final MOB builder, final PhysicalAgent target, final String line)
	{
		String l = line.trim();
		if(l.startsWith("!") && CMath.isInteger(l.substring(1).trim()))
		{
			final List<String> H = history(builder);
			final int n = CMath.s_int(l.substring(1).trim());
			if((n < 1) || (n > H.size()))
				return "There is no line " + n + ": .history lists them.";
			l = H.get(n - 1);
			builder.tell(l);
		}
		if(l.startsWith("."))
			return dot(cx, scope, builder, target, l);
		remember(builder, l);
		final long t0 = System.nanoTime();
		final Object r = cx.evaluateString(scope, l, "<cmd>", 1, null);
		final String out = show(r);
		if(timed.contains(builder.Name()))
			return out + "\n\r(" + String.format("%.2f", Double.valueOf((System.nanoTime() - t0) / 1000000.0)) + " ms)";
		return out;
	}

	private static String show(final Object r)
	{
		if(r == null)
			return "null";
		if(r instanceof Undefined)
			return "undefined";
		if(r instanceof Wrapper)
			return String.valueOf(((Wrapper)r).unwrap());
		return Context.toString(r);
	}

	private static String dot(final Context cx, final Scriptable scope, final MOB builder, final PhysicalAgent target, final String line)
	{
		final String cmd = CMParms.getCleanBit(line, 0).toLowerCase();
		final String rest = CMParms.getPastBitClean(line, 0).trim();
		switch(cmd)
		{
		case ".reset":
			return RESET;
		case ".time":
			if(rest.equalsIgnoreCase("off"))
				timed.remove(builder.Name());
			else
				timed.add(builder.Name());
			return timed.contains(builder.Name()) ? "Each line's time is shown." : "Times are not shown.";
		case ".history":
		{
			final List<String> H = history(builder);
			if(H.isEmpty())
				return "No lines yet.";
			final int show = (rest.length() > 0) ? Math.max(1, CMath.s_int(rest)) : 20;
			final StringBuilder str = new StringBuilder("");
			for(int i = Math.max(0, H.size() - show); i < H.size(); i++)
				str.append(i + 1).append(": ").append(H.get(i)).append("\n\r");
			return str.toString().trim();
		}
		case ".load":
		{
			final Script S = JSScope.compileFile(cx, rest);
			if(S == null)
				return "There is no file '" + rest + "' (under resources/, or world:path).";
			return show(S.exec(cx, scope));
		}
		case ".inspect":
			return inspect(cx.evaluateString(scope, rest.replace('`', '\''), "<inspect>", 1, null));
		case ".trigger":
			return trigger(builder, (target != null) ? target : builder.location(), rest);
		default:
			return "Dot commands: .trigger GREET|SPEECH text|GIVE item|LOOK|TICK, .load file, .reset, .inspect expr, .time on|off, .history [n], and !n.";
		}
	}

	/**
	 * .inspect: what a value is.
	 * @param r the value
	 * @return its name, class, where it is, and its stats; or its JSON
	 */
	public static String inspect(final Object r)
	{
		final Object o = (r instanceof Wrapper) ? ((Wrapper)r).unwrap() : r;
		if(o instanceof Scriptable)
		{
			final Context cx = Context.getCurrentContext();
			final Object j = (cx == null) ? null : NativeJSON.stringify(cx, ScriptableObject.getTopLevelScope((Scriptable)o), o, null, Integer.valueOf(2));
			return (j instanceof String) ? (String)j : Context.toString(o);
		}
		if(!(o instanceof CMObject))
			return (o == null) ? "null" : (o.getClass().getSimpleName() + ": " + o);
		final StringBuilder str = new StringBuilder("");
		if(o instanceof Environmental)
			str.append(((Environmental)o).Name()).append(" (").append(((CMObject)o).ID()).append(")");
		else
			str.append(((CMObject)o).ID());
		if(o instanceof Physical)
		{
			final Room R = (o instanceof Room) ? (Room)o : CMLib.map().roomLocation((Physical)o);
			if(R != null)
				str.append(" @ ").append(CMLib.map().getExtendedRoomID(R));
		}
		if(o instanceof Modifiable)
		{
			final Modifiable M = (Modifiable)o;
			for(final String code : M.getStatCodes())
			{
				String v = M.getStat(code);
				if((v == null) || (v.length() == 0))
					continue;
				v = v.replace('\n', ' ').replace('\r', ' ');
				str.append("\n\r  ").append(code).append(": ").append(CMStrings.ellipse(v, 70));
			}
		}
		return str.toString();
	}

	/**
	 * JS16: JS HELP: what a value offers: a Java object's methods (each name
	 * with the numbers of arguments it takes) and, for a thing with stats,
	 * its stat codes and values; a class's static methods; a JavaScript
	 * object's properties.
	 * @param r the value
	 * @return the description
	 */
	public static String describe(final Object r)
	{
		if(r instanceof NativeJavaClass)
		{
			final Class<?> C = ((NativeJavaClass)r).getClassObject();
			return C.getSimpleName() + " (a class): " + methods(C, true);
		}
		final Object o = (r instanceof Wrapper) ? ((Wrapper)r).unwrap() : r;
		if((o == null) || (o instanceof Undefined))
			return "null: nothing to describe.";
		if((o instanceof Scriptable) && !(o instanceof Wrapper))
		{
			final Scriptable S = (Scriptable)o;
			final List<String> names = new ArrayList<String>();
			for(final Object id : S.getIds())
			{
				final Object v = ScriptableObject.getProperty(S, id.toString());
				names.add(id + ((v instanceof Function) ? "()" : ""));
			}
			Collections.sort(names);
			return "a JavaScript object: " + (names.isEmpty() ? "no properties." : String.join(", ", names));
		}
		if((o instanceof String) || (o instanceof Number) || (o instanceof Boolean))
			return o.getClass().getSimpleName() + ": " + o;
		final StringBuilder str = new StringBuilder(o.getClass().getSimpleName());
		if(o instanceof Environmental)
			str.append(" '").append(((Environmental)o).Name()).append("'");
		str.append(": ").append(methods(o.getClass(), false));
		if(o instanceof Modifiable)
		{
			str.append("\n\rStats:");
			final Modifiable M = (Modifiable)o;
			for(final String code : M.getStatCodes())
			{
				String v = M.getStat(code);
				v = (v == null) ? "" : v.replace('\n', ' ').replace('\r', ' ');
				str.append("\n\r  ").append(code).append(": ").append(CMStrings.ellipse(v, 60));
			}
		}
		return str.toString();
	}

	private static String methods(final Class<?> C, final boolean statics)
	{
		final TreeMap<String, TreeSet<Integer>> found = new TreeMap<String, TreeSet<Integer>>();
		for(final java.lang.reflect.Method m : C.getMethods())
		{
			if(java.lang.reflect.Modifier.isStatic(m.getModifiers()) != statics)
				continue;
			if(m.getDeclaringClass() == Object.class)
				continue;
			TreeSet<Integer> counts = found.get(m.getName());
			if(counts == null)
			{
				counts = new TreeSet<Integer>();
				found.put(m.getName(), counts);
			}
			counts.add(Integer.valueOf(m.getParameterCount()));
		}
		final List<String> out = new ArrayList<String>();
		for(final Map.Entry<String, TreeSet<Integer>> e : found.entrySet())
		{
			final StringBuilder s = new StringBuilder(e.getKey()).append("(");
			int i = 0;
			for(final Integer n : e.getValue())
				s.append((i++ > 0) ? "|" : "").append((n.intValue() == 0) ? "" : n.toString());
			out.add(s.append(")").toString());
		}
		return out.isEmpty() ? "no methods." : String.join(", ", out);
	}

	/**
	 * .trigger: an event, as if it happened, on the target.
	 * @param builder the builder, who does it
	 * @param target what hears it
	 * @param what GREET, SPEECH text, GIVE item, LOOK or TICK
	 * @return what was done
	 */
	public static String trigger(final MOB builder, final PhysicalAgent target, final String what)
	{
		if(target == null)
			return "There is nothing to fire it on.";
		final String kind = CMParms.getCleanBit(what, 0).toUpperCase();
		final String rest = CMParms.getPastBitClean(what, 0).trim();
		final Room R = (target instanceof Room) ? (Room)target : CMLib.map().roomLocation(target);
		final CMMsg msg;
		switch(kind)
		{
		case "GREET":
			if(R == null)
				return "It is in no room.";
			msg = CMClass.getMsg(builder, R, null, CMMsg.MSG_ENTER, null);
			break;
		case "SPEECH":
		case "SAY":
			if(rest.length() == 0)
				return ".trigger SPEECH [WHAT IS SAID]";
			msg = CMClass.getMsg(builder, (target instanceof MOB) ? target : null, null, CMMsg.MSG_SPEAK, "<S-NAME> say(s) '" + rest + "'");
			break;
		case "GIVE":
		{
			final Item I = builder.findItem(null, rest);
			if(I == null)
				return "You carry no '" + rest + "'.";
			msg = CMClass.getMsg(builder, target, I, CMMsg.MSG_GIVE, "<S-NAME> give(s) <O-NAME> to <T-NAMESELF>.");
			break;
		}
		case "LOOK":
			msg = CMClass.getMsg(builder, target, null, CMMsg.MSG_LOOK, null);
			break;
		case "TICK":
		{
			final int id = (target instanceof MOB) ? Tickable.TICKID_MOB
				: (target instanceof Item) ? Tickable.TICKID_ITEM_BEHAVIOR
				: (target instanceof Room) ? Tickable.TICKID_ROOM_BEHAVIOR : Tickable.TICKID_AREA;
			target.tick(target, id);
			runEngines(target);
			return "A tick for " + target.Name() + ".";
		}
		default:
			return ".trigger GREET|SPEECH [TEXT]|GIVE [ITEM]|LOOK|TICK";
		}
		target.executeMsg(target, msg);
		runEngines(target);
		return kind + " fired on " + target.Name() + ".";
	}

	/** what the target's scripts queued, run now, as a tick would */
	private static void runEngines(final PhysicalAgent target)
	{
		for(final Enumeration<ScriptingEngine> e = target.scripts(); e.hasMoreElements();)
		{
			final ScriptingEngine S = e.nextElement();
			if(S == null)
				continue;
			for(int i = 0; i < 4; i++)
				S.dequeResponses(null);
		}
	}

	private static File historyFile(final MOB builder)
	{
		final File world = WorldFiles.worldDir();
		if((world == null) || (!SaveGame.isBuilderMode()))
			return null;
		return new File(new File(world, ".history"), "jconsole-" + builder.Name().replaceAll("[^A-Za-z0-9_-]", "_") + ".txt");
	}

	/**
	 * @param builder the builder
	 * @return the lines typed before, oldest first
	 */
	public static synchronized List<String> history(final MOB builder)
	{
		List<String> H = histories.get(builder.Name());
		if(H == null)
		{
			H = new ArrayList<String>();
			final File F = historyFile(builder);
			if((F != null) && F.isFile())
			{
				try
				{
					for(final String l : java.nio.file.Files.readAllLines(F.toPath(), java.nio.charset.StandardCharsets.UTF_8))
					{
						if(l.trim().length() > 0)
							H.add(l);
					}
				}
				catch(final java.io.IOException e)
				{
					Log.errOut("JSConsole", e.getMessage());
				}
			}
			histories.put(builder.Name(), H);
		}
		return H;
	}

	private static synchronized void remember(final MOB builder, final String line)
	{
		if((line.length() == 0) || (line.indexOf('\n') >= 0))
			return;
		final List<String> H = history(builder);
		if((!H.isEmpty()) && H.get(H.size() - 1).equals(line))
			return;
		H.add(line);
		while(H.size() > KEEP)
			H.remove(0);
		final File F = historyFile(builder);
		if(F == null)
			return;
		try
		{
			F.getParentFile().mkdirs();
			java.nio.file.Files.write(F.toPath(), H, java.nio.charset.StandardCharsets.UTF_8);
		}
		catch(final java.io.IOException e)
		{
			Log.errOut("JSConsole", e.getMessage());
		}
	}
}
