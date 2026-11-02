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
 * I3: a thing read a page at a time (GenTome), for READ_PROG PAGE n.
 */
public interface Paged
{
	/**
	 * The page a READ would show, without turning to it.
	 * @param mob who reads
	 * @param request what they asked (a number, NEXT, PREV, a title), or nothing for the contents
	 * @return the page, from 1, or 0 for the contents
	 */
	public int pageFor(MOB mob, String request);

	/**
	 * @param page a page, from 1
	 * @return its title, or ""
	 */
	public String pageTitle(int page);
}
