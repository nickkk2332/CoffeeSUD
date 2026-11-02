package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.util.*;

import org.mozilla.javascript.*;
import org.mozilla.javascript.Scriptable; // not this package's Scriptable behaviour

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
 * JS11: a mob, item, room, exit or area scripted wholly in JavaScript.  The
 * parameters are LOAD=world:js/npc/ferryman.js (and SCOPE=), or the
 * script itself.  The script defines the handlers it wants, and only those
 * are called:
 * onLoad(), onGreet(who), onArrive(room), onLeave(who), onSpeech(who, text),
 * onGive(who, item), onGet(who, item), onDrop(who, item), onWear(who, item),
 * onRemove(who, item), onOpen(who, thing), onClose(who, thing),
 * onLock(who, thing), onUnlock(who, thing), onDeath(killer), onKill(victim),
 * onDamage(who, amount), onCast(who, ability, target), onLook(who),
 * onTick(), onHour(hour), onDay(day); onCommand(who, verb, rest), true to
 * handle a command typed near the host; canMessage(msg), false to refuse a
 * message; afterMessage(msg) after any message; onReload(old) after JS
 * RELOAD.  Any other function can be called from a script with MPCALLFUNC
 * and CALLFUNC (its arguments as one string, and the caller).
 * <p>
 * The scope has me (the host), cm (with cm.host() the host), state (the
 * host's cm.store, saved with the game), CMMsg, and what every scope has.
 * SCOPE=INDIVIDUAL (the default) gives each host a scope of its own;
 * SCOPE=GLOBAL or a name shares one scope among the hosts that give it, its
 * me and cm set for each call, and its state cm.store('scope:name').
 */
public class JScriptable extends StdBehavior implements JSReload.Holder, ScriptEvents.JSFunctions
{
	@Override
	public String ID()
	{
		return "JScriptable";
	}

	@Override
	protected int canImproveCode()
	{
		return Ability.CAN_MOBS | Ability.CAN_ITEMS | Ability.CAN_EXITS | Ability.CAN_ROOMS | Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "scripted in JavaScript";
	}

	/** the handlers that are not functions to call */
	public static final String[] HANDLERS = { "onLoad", "onGreet", "onArrive", "onLeave", "onSpeech", "onGive", "onGet", "onDrop", "onWear",
		"onRemove", "onOpen", "onClose", "onLock", "onUnlock", "onDeath", "onKill", "onDamage", "onCast", "onLook", "onTick", "onHour", "onDay",
		"onCommand", "canMessage", "afterMessage", "onReload" };

	/** the scopes shared by name (SCOPE=GLOBAL or a name), by name and file */
	private static final Map<String, Scriptable>	shared		= new HashMap<String, Scriptable>();
	/** the inline scripts compiled, by their text */
	private static final Map<String, Script>		inline		= new HashMap<String, Script>();

	protected PhysicalAgent		host		= null;
	protected Scriptable		scope		= null;
	protected String			loaded		= null;
	protected String			path		= "";
	protected String			scopeName	= "INDIVIDUAL";
	protected String			sharedKey	= null;
	protected final Set<String>	broken		= new HashSet<String>();
	protected int				lastHour	= -1;
	protected int				lastDay		= -1;

	@Override
	public void setParms(final String parameters)
	{
		super.setParms(parameters);
		scope = null;
		loaded = null;
	}

	@Override
	public String scriptPath()
	{
		ready();
		return path;
	}

	/** @return the scope's name: INDIVIDUAL, GLOBAL or a name */
	public String scopeName()
	{
		ready();
		return scopeName;
	}

	/** @return the script's scope, made if need be, or null if it cannot run */
	public Scriptable scope()
	{
		return ready() ? scope : null;
	}

	private boolean isParms(final String p)
	{
		final String u = p.trim().toUpperCase();
		return u.startsWith("LOAD=") || u.startsWith("SCOPE=");
	}

	/** reads the parameters and makes the scope, if they changed */
	protected boolean ready()
	{
		final String p = (getParms() == null) ? "" : getParms();
		if(p.equals(loaded))
			return scope != null;
		loaded = p;
		scope = null;
		broken.clear();
		path = "";
		scopeName = "INDIVIDUAL";
		if(isParms(p))
		{
			final Map<String, String> P = CMParms.parseEQParms(p);
			path = P.containsKey("LOAD") ? P.get("LOAD").trim() : "";
			if(P.containsKey("SCOPE") && (P.get("SCOPE").trim().length() > 0))
				scopeName = P.get("SCOPE").trim().toUpperCase();
		}
		if(p.trim().length() == 0)
			return false;
		sharedKey = scopeName + "|" + (isParms(p) ? path : p);
		if(!scopeName.equals("INDIVIDUAL"))
		{
			synchronized(shared)
			{
				final Scriptable S = shared.get(sharedKey);
				if(S != null)
				{
					scope = S;
					return true;
				}
			}
		}
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Script S = compile(cx, p);
			if(S == null)
				return false;
			final ImporterTopLevel top = new ImporterTopLevel(cx);
			JSScope.prepare(cx, top, host);
			ScriptableObject.putProperty(top, "CMMsg", new NativeJavaClass(top, CMMsg.class));
			ScriptableObject.putProperty(top, "me", Context.javaToJS(host, top));
			final String storeKey = scopeName.equals("INDIVIDUAL") ? JSStore.keyOf(host) : ("scope:" + scopeName.toLowerCase());
			ScriptableObject.putProperty(top, "state", JSStore.store(cx, top, storeKey));
			S.exec(cx, top);
			scope = top;
			if(!scopeName.equals("INDIVIDUAL"))
			{
				synchronized(shared)
				{
					shared.put(sharedKey, top);
				}
			}
			if(ScriptableObject.getProperty(top, "onCommand") instanceof Function)
				ScriptEvents.jsCommands = true;
		}
		catch(final Exception e)
		{
			JSRuntime.report(where(null), e);
			return false;
		}
		finally
		{
			Context.exit();
		}
		return true;
	}

	private Script compile(final Context cx, final String p)
	{
		if(isParms(p))
		{
			final Script S = JSScope.compileFile(cx, path);
			if(S == null)
				Log.errOut(ID(), where(null) + ": no such file: " + path);
			return S;
		}
		synchronized(inline)
		{
			Script S = inline.get(p);
			if(S == null)
			{
				S = cx.compileString(p, where(null), 1, null);
				inline.put(p, S);
			}
			return S;
		}
	}

	private String where(final String fn)
	{
		final String h = (host == null) ? "?" : host.Name();
		return "JScriptable " + ((path.length() > 0) ? path : "(inline)") + " on '" + h + "'" + ((fn == null) ? "" : (" " + fn + "()"));
	}

	/**
	 * @param fn a function of the script
	 * @return true if the script defines it (and it has not failed)
	 */
	public boolean has(final String fn)
	{
		return (scope != null) && (!broken.contains(fn)) && (ScriptableObject.getProperty(scope, fn) instanceof Function);
	}

	/**
	 * Calls a function of the script, if it defines it.
	 * @param fn the function
	 * @param args its arguments, Java objects
	 * @return what it returned, as a Java object, or null
	 */
	public Object call(final String fn, final Object... args)
	{
		if((!ready()) || (!has(fn)))
			return null;
		final Function F = (Function)ScriptableObject.getProperty(scope, fn);
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			if(!scopeName.equals("INDIVIDUAL"))
			{
				ScriptableObject.putProperty(scope, "me", Context.javaToJS(host, scope));
				ScriptableObject.putProperty(scope, "cm", Context.javaToJS(JSScope.cmFor(scope, host), scope));
			}
			final Object[] jsArgs = new Object[args.length];
			for(int i = 0; i < args.length; i++)
				jsArgs[i] = Context.javaToJS(args[i], scope);
			return Context.jsToJava(F.call(cx, scope, scope, jsArgs), Object.class);
		}
		catch(final Exception e)
		{
			broken.add(fn);
			JSRuntime.report(where(fn), e);
			return null;
		}
		finally
		{
			Context.exit();
		}
	}

	@Override
	public void reloadScript()
	{
		final Scriptable old = scope;
		// a shared scope is made again once, by the first of its hosts to reload
		boolean mine = scopeName.equals("INDIVIDUAL");
		if(!mine)
		{
			synchronized(shared)
			{
				if((sharedKey != null) && (shared.get(sharedKey) == old))
				{
					shared.remove(sharedKey);
					mine = true;
				}
			}
		}
		loaded = null;
		scope = null;
		if(ready() && mine && (old != null) && (old != scope))
			call("onReload", old);
	}

	@Override
	public String callFunction(final PhysicalAgent on, final String fn, final String args, final MOB source)
	{
		if((fn == null) || Arrays.asList(HANDLERS).contains(fn) || (!ready()) || (!has(fn)))
			return null;
		final Object r = call(fn, (args == null) ? "" : args, source);
		return ((r == null) || (r instanceof Undefined)) ? "" : r.toString();
	}

	@Override
	public Object callHandler(final String fn, final Object... args)
	{
		return call(fn, args);
	}

	@Override
	public boolean command(final MOB who, final String verb, final String rest)
	{
		if((!ready()) || (!has("onCommand")))
			return false;
		final Object r = call("onCommand", who, verb, rest);
		return Boolean.TRUE.equals(r);
	}

	@Override
	public void startBehavior(final PhysicalAgent forMe)
	{
		super.startBehavior(forMe);
		host = forMe;
		loaded = null;
		scope = null;
		call("onLoad");
	}

	/** @return the room the host is in, or null */
	protected Room hostRoom()
	{
		if(host instanceof Room)
			return (Room)host;
		return CMLib.map().roomLocation(host);
	}

	/** @return true if the room is where the host hears comings and goings */
	protected boolean isHere(final Environmental R)
	{
		if(!(R instanceof Room))
			return false;
		if(host instanceof Area)
			return ((Room)R).getArea() == host;
		return hostRoom() == R;
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if((host == null) && (myHost instanceof PhysicalAgent))
			host = (PhysicalAgent)myHost;
		if(!ready() || (!has("canMessage")))
			return true;
		return !Boolean.FALSE.equals(call("canMessage", msg));
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((host == null) && (myHost instanceof PhysicalAgent))
			host = (PhysicalAgent)myHost;
		if(!ready())
			return;
		if(has("afterMessage"))
			call("afterMessage", msg);
		final MOB src = msg.source();
		final Environmental tgt = msg.target();
		final int tm = msg.targetMinor();
		final int sm = msg.sourceMinor();
		switch(tm)
		{
		case CMMsg.TYP_ENTER:
			if(src == host)
				call("onArrive", tgt);
			else
			if(isHere(tgt))
				call("onGreet", src);
			break;
		case CMMsg.TYP_LEAVE:
			if((src != host) && isHere(tgt))
				call("onLeave", src);
			break;
		case CMMsg.TYP_GIVE:
			if(tgt == host)
				call("onGive", src, msg.tool());
			break;
		case CMMsg.TYP_GET:
			if(src != host)
				call("onGet", src, tgt);
			break;
		case CMMsg.TYP_DROP:
			if(src != host)
				call("onDrop", src, tgt);
			break;
		case CMMsg.TYP_WEAR:
		case CMMsg.TYP_WIELD:
		case CMMsg.TYP_HOLD:
			call("onWear", src, tgt);
			break;
		case CMMsg.TYP_REMOVE:
			call("onRemove", src, tgt);
			break;
		case CMMsg.TYP_OPEN:
			call("onOpen", src, tgt);
			break;
		case CMMsg.TYP_CLOSE:
			call("onClose", src, tgt);
			break;
		case CMMsg.TYP_LOCK:
			call("onLock", src, tgt);
			break;
		case CMMsg.TYP_UNLOCK:
			call("onUnlock", src, tgt);
			break;
		case CMMsg.TYP_DAMAGE:
			if(tgt == host)
				call("onDamage", src, Integer.valueOf(msg.value()));
			break;
		case CMMsg.TYP_LOOK:
		case CMMsg.TYP_EXAMINE:
			if((tgt == host) && (src != host))
				call("onLook", src);
			break;
		default:
			break;
		}
		if(sm == CMMsg.TYP_SPEAK)
		{
			if((src != host) && (msg.sourceMessage() != null))
			{
				final String said = CMStrings.getSayFromMessage(msg.sourceMessage());
				if(said != null)
					call("onSpeech", src, said);
			}
		}
		else
		if(sm == CMMsg.TYP_DEATH)
		{
			if(src == host)
				call("onDeath", msg.tool());
			else
			if((msg.tool() == host) && (src != null))
				call("onKill", src);
		}
		else
		if((sm == CMMsg.TYP_CAST_SPELL) && (msg.tool() instanceof Ability) && (src != host))
			call("onCast", src, msg.tool(), tgt);
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((host == null) && (ticking instanceof PhysicalAgent))
			host = (PhysicalAgent)ticking;
		if(!ready())
			return true;
		call("onTick");
		if(has("onHour") || has("onDay"))
		{
			final Room R = hostRoom();
			final Area A = (host instanceof Area) ? (Area)host : ((R == null) ? null : R.getArea());
			if(A != null)
			{
				final TimeClock C = A.getTimeObj();
				if((lastHour >= 0) && (C.getHourOfDay() != lastHour))
					call("onHour", Integer.valueOf(C.getHourOfDay()));
				if((lastDay >= 0) && (C.getDayOfMonth() != lastDay))
					call("onDay", Integer.valueOf(C.getDayOfMonth()));
				lastHour = C.getHourOfDay();
				lastDay = C.getDayOfMonth();
			}
		}
		return true;
	}
}
