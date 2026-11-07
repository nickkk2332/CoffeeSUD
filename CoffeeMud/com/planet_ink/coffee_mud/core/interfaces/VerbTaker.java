package com.planet_ink.coffee_mud.core.interfaces;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * I9: an effect on something within reach that takes a command line before
 * the game's own commands do (ScriptEvents.verb), as VERB_PROG and Prop_Verbs
 * do: PULL LEVER, TURN DIAL 2 TO 3, CLEAR RUBBLE.  One that is in the game
 * calls ScriptEvents.propVerbsSeen().
 */
public interface VerbTaker
{
	/**
	 * Whether it takes a line.
	 * @param mob who typed it
	 * @param host what the effect is on
	 * @param verb the line's first word
	 * @param rest the rest of the line
	 * @return true if it took it
	 */
	public boolean takeVerb(MOB mob, PhysicalAgent host, String verb, String rest);
}
