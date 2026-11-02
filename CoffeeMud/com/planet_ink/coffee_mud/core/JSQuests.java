package com.planet_ink.coffee_mud.core;

import java.util.*;

import org.mozilla.javascript.*;

import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Common.interfaces.Quest;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.*;

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
 * JS18: a quest's JavaScript handlers, in a SET JSFILE world:js/quests/x.js
 * file or a &lt;JSQUEST&gt; block of the quest: onStart(state), onStep(n,
 * state), onTimeUp(state), onWin(player), onAccept(player) and onStop().
 * state is cm.store('quest:name'), saved with the game; the scope also has
 * quest, quests and what every scope has.  The JScriptables of the quest's
 * mobs and items may define onQuestAccept(quest, mob), onQuestStep(quest,
 * step, label), onQuestWin(quest, mob, outcome), onQuestFail(quest, mob,
 * reason) and onQuestEnd(quest, reason).
 */
public final class JSQuests
{
	/** the quests' scopes, by name: the source they were made from, and the scope */
	private static final Map<String, Object[]>	scopes	= new HashMap<String, Object[]>();
	/** in a handler's arguments: the quest's state */
	private static final Object					STATE	= new Object();

	private JSQuests()
	{
	}

	/** each quest's handlers, by its name: SET JSFILE's file and its <JSQUEST> block */
	private static final Map<String, String[]>	sources	= new HashMap<String, String[]>();

	/**
	 * From DefaultQuest.setScript (a class of its own loader, so not looked
	 * at here): where a quest's handlers are.
	 * @param questName the quest's name
	 * @param file SET JSFILE's file, or ""
	 * @param block its <JSQUEST> block, or ""
	 */
	public static synchronized void setSource(final String questName, final String file, final String block)
	{
		if((questName == null) || (questName.length() == 0))
			return;
		if(((file == null) || (file.trim().length() == 0)) && ((block == null) || (block.trim().length() == 0)))
			sources.remove(questName);
		else
			sources.put(questName, new String[] { (file == null) ? "" : file.trim(), (block == null) ? "" : block });
	}

	/**
	 * @param Q a quest
	 * @return its JavaScript, or null if it has none
	 */
	public static String source(final Quest Q)
	{
		final String[] S;
		synchronized(JSQuests.class)
		{
			S = sources.get(Q.name());
		}
		if(S == null)
			return null;
		if(S[1].trim().length() > 0)
			return S[1];
		final String text = JSScope.fileText(S[0]);
		if(text == null)
			Log.errOut("JSQuests", "Quest '" + Q.name() + "': no such JSFILE " + S[0]);
		return text;
	}

	/**
	 * A quest's scope, made again when its JavaScript changes.
	 * @param Q the quest
	 * @return the scope, or null if it has no JavaScript (or it failed)
	 */
	public static synchronized Scriptable scope(final Quest Q)
	{
		final String src = source(Q);
		if(src == null)
			return null;
		final Object[] had = scopes.get(Q.name());
		if((had != null) && had[0].equals(src))
			return (Scriptable)had[1];
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final ImporterTopLevel top = new ImporterTopLevel(cx);
			JSScope.prepare(cx, top, null);
			ScriptableObject.putProperty(top, "quest", Context.javaToJS(Q, top));
			ScriptableObject.putProperty(top, "quests", Context.javaToJS(new Helper(), top));
			ScriptableObject.putProperty(top, "state", JSStore.store(cx, top, JSStore.keyOf(Q)));
			cx.evaluateString(top, src, "quest '" + Q.name() + "'", 1, null);
			scopes.put(Q.name(), new Object[] { src, top });
			return top;
		}
		catch(final Exception e)
		{
			JSRuntime.report("quest '" + Q.name() + "' JavaScript", e);
			scopes.put(Q.name(), new Object[] { src, null });
			return null;
		}
		finally
		{
			Context.exit();
		}
	}

	private static Object call(final Quest Q, final Scriptable S, final String fn, final Object... args)
	{
		final Object F = ScriptableObject.getProperty(S, fn);
		if(!(F instanceof Function))
			return null;
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Scriptable state = JSStore.store(cx, S, JSStore.keyOf(Q));
			ScriptableObject.putProperty(S, "state", state); // a restore makes the store again
			final Object[] jsArgs = new Object[args.length];
			for(int i = 0; i < args.length; i++)
				jsArgs[i] = (args[i] == STATE) ? state : Context.javaToJS(args[i], S);
			return ((Function)F).call(cx, S, S, jsArgs);
		}
		catch(final Exception e)
		{
			JSRuntime.report("quest '" + Q.name() + "' " + fn + "()", e);
			return null;
		}
		finally
		{
			Context.exit();
		}
	}

	/**
	 * From ScriptEvents.quest: the quest's handlers, and its objects'.
	 * @param Q the quest
	 * @param kind ACCEPT, START, STEP, WIN, FAIL or END
	 * @param who the character, or null
	 * @param detail the step, the outcome, or the reason
	 */
	public static void event(final Quest Q, final String kind, final MOB who, final String detail)
	{
		if(Q == null)
			return;
		final String d = (detail == null) ? "" : detail;
		final Scriptable S = scope(Q);
		if(S != null)
		{
			switch(kind)
			{
			case "START":
				call(Q, S, "onStart", STATE);
				break;
			case "STEP":
				call(Q, S, "onStep", Integer.valueOf(CMath.s_int(d)), STATE);
				break;
			case "WIN":
				call(Q, S, "onWin", who);
				break;
			case "ACCEPT":
				call(Q, S, "onAccept", who);
				break;
			case "FAIL":
				if(d.equalsIgnoreCase("EXPIRED"))
					call(Q, S, "onTimeUp", STATE);
				break;
			case "END":
				call(Q, S, "onStop");
				break;
			default:
				break;
			}
		}
		final String fn;
		final Object[] args;
		switch(kind)
		{
		case "ACCEPT":
			fn = "onQuestAccept";
			args = new Object[] { Q, who };
			break;
		case "STEP":
			fn = "onQuestStep";
			args = new Object[] { Q, Integer.valueOf(CMath.s_int(d)), "" };
			break;
		case "WIN":
			fn = "onQuestWin";
			args = new Object[] { Q, who, "WON" };
			break;
		case "FAIL":
			fn = "onQuestFail";
			args = new Object[] { Q, who, d };
			break;
		case "END":
			fn = "onQuestEnd";
			args = new Object[] { Q, d };
			break;
		default:
			return;
		}
		for(final PhysicalAgent P : objects(Q))
		{
			for(final Enumeration<Behavior> b = P.behaviors(); b.hasMoreElements();)
			{
				final Behavior B = b.nextElement();
				if(B instanceof ScriptEvents.JSFunctions)
					((ScriptEvents.JSFunctions)B).callHandler(fn, args);
			}
		}
	}

	/**
	 * @param Q a quest
	 * @return the mobs and items it has
	 */
	public static List<PhysicalAgent> objects(final Quest Q)
	{
		final List<PhysicalAgent> all = new ArrayList<PhysicalAgent>();
		for(int i = 1; i < 200; i++)
		{
			final MOB M = Q.getQuestMob(i);
			if(M == null)
				break;
			all.add(M);
		}
		for(int i = 1; i < 200; i++)
		{
			final Item I = Q.getQuestItem(i);
			if(I == null)
				break;
			all.add(I);
		}
		return all;
	}

	/** JS18: the quests helper, by quest name */
	public static final class Helper
	{
		/**
		 * @param name a quest's name
		 * @return the quest, or null
		 */
		public Quest get(final String name)
		{
			return (name == null) ? null : CMLib.quests().fetchQuest(name);
		}

		/**
		 * @param name a quest's name
		 * @return false if there is no such quest, or it would not start
		 */
		public boolean start(final String name)
		{
			final Quest Q = get(name);
			return (Q != null) && Q.startQuest();
		}

		/**
		 * @param name a quest's name
		 * @return false if there is no such quest, or no step after
		 */
		public boolean step(final String name)
		{
			final Quest Q = get(name);
			return (Q != null) && Q.stepQuest();
		}

		/**
		 * Names a winner, as MPQUESTWIN does.
		 * @param name a quest's name
		 * @param who the winner, or their name
		 * @return false if there is no such quest
		 */
		public boolean win(final String name, final Object who)
		{
			final Quest Q = get(name);
			if((Q == null) || (who == null))
				return false;
			Q.declareWinner((who instanceof MOB) ? ((MOB)who).Name() : who.toString());
			return true;
		}

		/**
		 * Stops a quest with no winner: it fails.
		 * @param name a quest's name
		 * @return false if there is no such quest
		 */
		public boolean fail(final String name)
		{
			return stop(name);
		}

		/**
		 * @param name a quest's name
		 * @return false if there is no such quest
		 */
		public boolean stop(final String name)
		{
			final Quest Q = get(name);
			if(Q == null)
				return false;
			Q.stopQuest();
			return true;
		}

		/**
		 * @param name a quest's name
		 * @return true if it runs
		 */
		public boolean running(final String name)
		{
			final Quest Q = get(name);
			return (Q != null) && Q.running();
		}

		/**
		 * @param name a quest's name
		 * @return its step, from 1, or 0
		 */
		public int stepNumber(final String name)
		{
			final Quest Q = get(name);
			return (Q == null) ? 0 : CMath.s_int(Q.getStat("STEPNUM"));
		}

		/**
		 * @param name a quest's name
		 * @return running, stopping, waiting, or none
		 */
		public String status(final String name)
		{
			final Quest Q = get(name);
			if(Q == null)
				return "none";
			if(Q.running())
				return Q.stopping() ? "stopping" : "running";
			if("true".equals(Q.getStat("FINISHED"))) // Q1
				return "finished";
			return Q.waiting() ? "waiting" : "stopped";
		}

		/**
		 * @param name a quest's name
		 * @return what the player is to do (its INSTRUCTIONS)
		 */
		public String objective(final String name)
		{
			final Quest Q = get(name);
			return (Q == null) ? "" : Q.getStat("INSTRUCTIONS");
		}

		/**
		 * @param name a quest's name
		 * @return its name as shown and what to do, as the player's list of quests has them
		 */
		public String journal(final String name)
		{
			final Quest Q = get(name);
			return (Q == null) ? "" : (Q.displayName() + ": " + Q.getStat("INSTRUCTIONS"));
		}

		private ScriptingEngine vars(final String name)
		{
			final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
			S.setVarScope(name);
			return S;
		}

		/**
		 * @param name a quest's name
		 * @param var a variable of the quest's own
		 * @return its value, or ""
		 */
		public String getVar(final String name, final String var)
		{
			final String v = vars(name).getVar("QUEST", var);
			return (v == null) ? "" : v;
		}

		/**
		 * @param name a quest's name
		 * @param var a variable of the quest's own
		 * @param value its value
		 */
		public void setVar(final String name, final String var, final Object value)
		{
			vars(name).setVar("QUEST", var.toUpperCase(), (value == null) ? "" : Context.toString(value));
		}
	}
}
