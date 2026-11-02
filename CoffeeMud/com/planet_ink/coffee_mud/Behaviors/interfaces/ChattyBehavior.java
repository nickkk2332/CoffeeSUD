package com.planet_ink.coffee_mud.Behaviors.interfaces;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.MaskingLibrary;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;

/*
   Copyright 2010-2025 Bo Zimmerman

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
 * A ChattyBehavior is a Behavior causes a mob to have a conversation,
 * or even just simply respond to a player or even another mob.
 * @see com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior
 */
public interface ChattyBehavior extends Behavior
{
	/**
	 * Returns the last thing actually spoken by the wielder of this
	 * behavior, or null if nothing has been said yet.
	 * @return the last thing said.
	 */
	public String getLastThingSaid();

	/**
	 * Returns the last MOB object spoken to.
	 * @return the last MOB object spoken to.
	 */
	public MOB getLastRespondedTo();

	/**
	 * Tells what the NPC would make of a sentence said to it, without anyone
	 * speaking: its chat groups, the entries the words lead to and how each
	 * matches, the replies of the first that does with their weights and
	 * their variables filled in, and anything that would stop a reply (CHATTEST).
	 * @param npc the NPC with this behaviour
	 * @param speaker who says it
	 * @param sentence what is said
	 * @return the report, lines ended with \n\r
	 */
	public default String chatTest(final MOB npc, final MOB speaker, final String sentence)
	{
		return "This NPC's talk cannot be tested.";
	}

	/**
	 * Forgets the chat groups and entries this behaviour worked out, so that
	 * they are worked out again from the chat files as they are now.
	 */
	public default void resetChat()
	{
	}

	/**
	 * N24: whether one of this NPC's chat entries answers a topic said to it:
	 * an entry for speech, out of combat, found by the topic's own words (an
	 * entry that answers anything does not count).
	 * @param npc the NPC with this behaviour
	 * @param speaker who asks
	 * @param topic the topic
	 * @return true if an entry answers it
	 */
	public default boolean answersTopic(final MOB npc, final MOB speaker, final String topic)
	{
		return false;
	}

	/**
	 * N24: what this NPC's own chat entries for speech talk about, a word or
	 * phrase from each, to hint at; none from the default chat file.
	 * @param npc the NPC with this behaviour
	 * @return the topics
	 */
	public default List<String> chatTopics(final MOB npc)
	{
		return new ArrayList<String>();
	}
}
