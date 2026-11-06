package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Behaviors.interfaces.ChattyBehavior;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

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
 * N24: topics a character learns from what NPCs say.  In an NPC's spoken
 * line, [[mine]] or [[the old mine|mine]] shows the words highlighted and
 * teaches the topic (mine) to each player character who hears it.  The
 * topics are kept in the character's script variable KNOWN_TOPICS, saved
 * with the game.  ASK npc ABOUT, or TALK npc ABOUT, with no topic lists
 * the topics the character knows that the NPC answers to: its scripts'
 * SPEECH_PROG and TALK_PROG words and its MudChat entries.  HINTTOPICS in
 * the world's settings (or the tag HINTTOPICS on an NPC) has an NPC tell
 * its topics the first time a character talks with it.
 */
public final class NpcTopics
{
	private NpcTopics()
	{
	}

	/** the variable that holds a character's topics, | between them */
	public static final String	VAR			= "KNOWN_TOPICS";
	/** the variable that holds the NPCs that have told a character their topics */
	public static final String	HINTED_VAR	= "TOPICS_HINTED";
	/** the tag that has one NPC tell its topics */
	public static final String	HINT_TAG	= "HINTTOPICS";

	private static final Pattern	MARK	= Pattern.compile("\\[\\[([^\\[\\]|]+?)(?:\\|([^\\[\\]]+?))?\\]\\]");

	private static ScriptingEngine	vars	= null;

	private static ScriptingEngine vars()
	{
		if(vars == null)
		{
			final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
			S.setSavable(false);
			S.setVarScope("");
			vars = S;
		}
		return vars;
	}

	/**
	 * Turns the markup in a line into highlighted words.
	 * @param text the line
	 * @param topics the topics it names are added here, or null
	 * @return the line as shown
	 */
	public static String render(final String text, final List<String> topics)
	{
		if((text == null) || (text.indexOf("[[") < 0))
			return text;
		final Matcher m = MARK.matcher(text);
		final StringBuffer out = new StringBuffer();
		while(m.find())
		{
			final String shown = m.group(1).trim();
			final String topic = ((m.group(2) != null) ? m.group(2) : m.group(1)).trim().toLowerCase();
			if((topics != null) && (topic.length() > 0) && (!topics.contains(topic)))
				topics.add(topic);
			m.appendReplacement(out, Matcher.quoteReplacement("^H" + shown + "^?"));
		}
		m.appendTail(out);
		return out.toString();
	}

	/**
	 * @param M a character
	 * @return the topics they know, in the order learned
	 */
	public static List<String> known(final MOB M)
	{
		final List<String> list = new ArrayList<String>();
		if(M == null)
			return list;
		for(final String t : vars().getVar(M.Name(), VAR).split("\\|"))
		{
			if((t.trim().length() > 0) && (!list.contains(t.trim().toLowerCase())))
				list.add(t.trim().toLowerCase());
		}
		return list;
	}

	/**
	 * Teaches a character a topic.
	 * @param M the character
	 * @param topic the topic
	 * @param quiet true not to tell them
	 * @return true if it was new to them
	 */
	public static boolean learn(final MOB M, final String topic, final boolean quiet)
	{
		final String t = (topic == null) ? "" : topic.trim().toLowerCase().replace('|', ' ');
		if((M == null) || (t.length() == 0))
			return false;
		final List<String> list = known(M);
		if(list.contains(t))
			return false;
		list.add(t);
		vars().setVar(M.Name(), VAR, "=" + String.join("|", list)); // "=": as it is (SC11)
		if(!quiet)
			M.tell(CMLib.lang().L("^H(You can ask about @x1 now.)^?", t));
		return true;
	}

	/**
	 * After an NPC spoke a line with markup: each player character who heard it learns its topics.
	 * @param speaker the NPC
	 * @param R where it spoke
	 * @param topics the line's topics
	 */
	public static void teach(final MOB speaker, final Room R, final List<String> topics)
	{
        teach(speaker,R,topics,"");
    }
    public static void teach(final MOB speaker,final Room R,final List<String> topics,final String text)
    {
		if((R == null) || (topics == null) || topics.isEmpty())
			return;
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M == null) || (M == speaker) || (M.playerStats() == null))
				continue;
			if((speaker != null) && (!CMLib.flags().canBeHeardSpeakingBy(speaker, M)))
				continue;
			final List<String> learned=new ArrayList<String>();
			for(final String t : topics) if(learn(M,t,false)) learned.add(t);
			// JOURNALTALK: what was learned, once: a repeated bark adds nothing new, and is not
			// to push the player's own notes out of the journal
            if((!learned.isEmpty())&&CMProps.instance().getStr("JOURNALTALK").equalsIgnoreCase("true")) Chronicle.add(M,"People",text.isEmpty()?(speaker==null?"Someone":speaker.Name())+" mentioned "+String.join(", ",learned)+".":CMStrings.removeColors(text),speaker);
		}
	}

	/**
	 * @param npc an NPC
	 * @return the script engines on it
	 */
	private static List<ScriptingEngine> engines(final MOB npc)
	{
		final List<ScriptingEngine> list = new ArrayList<ScriptingEngine>();
		for(final Enumeration<ScriptingEngine> s = npc.scripts(); s.hasMoreElements();)
		{
			final ScriptingEngine S = s.nextElement();
			if(S != null)
				list.add(S);
		}
		for(final Enumeration<Behavior> b = npc.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if(B instanceof ScriptingEngine)
				list.add((ScriptingEngine)B);
		}
		return list;
	}

	/** an NPC's SPEECH_PROG lines, split as the engine splits them */
	private static List<String[]> speechLines(final MOB npc)
	{
		final List<String[]> lines = new ArrayList<String[]>();
		final int speech = ScriptEvents.code("SPEECH_PROG");
		for(final ScriptingEngine S : engines(npc))
			lines.addAll(S.triggerLines(npc, speech, "CT"));
		return lines;
	}

	/** an NPC's TALK_PROG lines: the name and the rest */
	private static List<String[]> talkLines(final MOB npc)
	{
		final List<String[]> lines = new ArrayList<String[]>();
		for(final ScriptingEngine S : engines(npc))
			lines.addAll(S.triggerLines(npc, ScriptEvents.TALK_PROG, "Cr"));
		return lines;
	}

	/** upper case, and only letters, digits and apostrophes, single spaced */
	private static String norm(final String s)
	{
		return (s == null) ? "" : s.toUpperCase().replace('`', '\'').replaceAll("[^A-Z0-9']+", " ").trim();
	}

	/** whether a trigger's words are none, ALL or *: any talk, not a topic */
	private static boolean any(final String w)
	{
		final String s = (w == null) ? "" : w.trim();
		return (s.length() == 0) || s.equalsIgnoreCase("ALL") || s.equals("*");
	}

	/**
	 * @param npc an NPC
	 * @param speaker who asks
	 * @param topic a topic
	 * @return true if it has an answer for it: a script's word, or a MudChat entry with the topic's words
	 */
	public static boolean answers(final MOB npc, final MOB speaker, final String topic)
	{
		final String said = norm(topic);
		if(said.length() == 0)
			return false;
		for(final String[] t : speechLines(npc))
		{
			if((t.length < 2) || any(t[1]))
				continue;
			if(t[1].equals("P"))
			{
				// as the engine: the whole of what is said is the phrase
				if((t.length > 2) && said.equals(norm(t[2])))
					return true;
				continue;
			}
			for(int i = 1; i < t.length; i++)
			{
				final String w = norm(t[i]);
				if((w.length() > 0) && ((" " + said + " ").indexOf(" " + w + " ") >= 0))
					return true;
			}
		}
		for(final String[] t : talkLines(npc))
		{
			if((t.length > 1) && (!any(t[1])) && ScriptEvents.talkFits(t[1], topic))
				return true;
		}
		for(final Enumeration<Behavior> b = npc.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if((B instanceof ChattyBehavior) && ((ChattyBehavior)B).answersTopic(npc, speaker, topic))
				return true;
		}
		return false;
	}

	/**
	 * @param npc an NPC
	 * @return the topics it has answers for: its scripts' words and its own chat entries' words
	 */
	public static List<String> topicsOf(final MOB npc)
	{
		final List<String> list = new ArrayList<String>();
		for(final String[] t : speechLines(npc))
		{
			if((t.length < 2) || any(t[1]))
				continue;
			for(int i = t[1].equals("P") ? 2 : 1; i < t.length; i++)
			{
				final String w = t[i].replace('`', '\'').trim().toLowerCase();
				if((w.length() > 0) && (!list.contains(w)))
					list.add(w);
			}
		}
		for(final String[] t : talkLines(npc))
		{
			if((t.length < 2) || any(t[1]))
				continue;
			final String w = t[1].trim();
			final List<String> ws = w.toUpperCase().startsWith("P ") ? Arrays.asList(w.substring(2)) : Arrays.asList(w.split("[\\s,]+"));
			for(final String s : ws)
			{
				final String n = s.replace('`', '\'').trim().toLowerCase();
				if((n.length() > 0) && (!list.contains(n)))
					list.add(n);
			}
		}
		for(final Enumeration<Behavior> b = npc.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if(B instanceof ChattyBehavior)
			{
				for(final String w : ((ChattyBehavior)B).chatTopics(npc))
				{
					if(!list.contains(w.toLowerCase()))
						list.add(w.toLowerCase());
				}
			}
		}
		return list;
	}

	/**
	 * @param M a character
	 * @param npc an NPC
	 * @return the topics they know that it answers to
	 */
	public static List<String> askable(final MOB M, final MOB npc)
	{
		final List<String> list = new ArrayList<String>();
		for(final String t : known(M))
		{
			if(answers(npc, M, t))
				list.add(t);
		}
		return list;
	}

	/**
	 * ASK npc ABOUT, TALK npc ABOUT: tells the character what they can ask it about.
	 * @param M the character
	 * @param npc the NPC
	 */
	public static void list(final MOB M, final MOB npc)
	{
		final List<String> l = askable(M, npc);
		if(l.isEmpty())
			M.tell(CMLib.lang().L("You know of nothing to ask @x1 about.", npc.name(M)));
		else
			M.tell(CMLib.lang().L("You could ask @x1 about: @x2.", npc.name(M), CMLib.english().toEnglishStringList(l)));
	}

	/**
	 * @param npc an NPC
	 * @return true if it tells its topics when first talked with
	 */
	public static boolean hints(final MOB npc)
	{
		return (npc != null) && (npc.hasTag(HINT_TAG) || CMProps.instance().getStr("HINTTOPICS").trim().equalsIgnoreCase("true"));
	}

	/**
	 * The first time a character talks with an NPC that hints: it tells
	 * them its topics, and they learn them.
	 * @param M the character
	 * @param npc the NPC
	 * @return true if it told them
	 */
	public static boolean hint(final MOB M, final MOB npc)
	{
		if((M == null) || (npc == null) || (M.playerStats() == null) || (!hints(npc)))
			return false;
		final String key = npc.Name().toUpperCase().replace('|', ' ');
		final List<String> told = new ArrayList<String>(Arrays.asList(vars().getVar(M.Name(), HINTED_VAR).split("\\|")));
		if(told.contains(key))
			return false;
		told.remove("");
		told.add(key);
		vars().setVar(M.Name(), HINTED_VAR, "=" + String.join("|", told)); // "=": as it is (SC11)
		final List<String> topics = topicsOf(npc);
		if(topics.isEmpty())
			return false;
		for(final String t : topics)
			learn(M, t, true);
		M.tell(CMLib.lang().L("@x1 could tell you about: @x2.", npc.name(M), CMLib.english().toEnglishStringList(topics)));
		return true;
	}
}
