package com.planet_ink.coffee_mud.core.interfaces;

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
 * W13: a thing that keeps lines by key, with alternatives (Prop_SkyText).
 * The engine asks through this, as the classes that implement it are loaded
 * apart from it.
 */
public interface KeyedLines
{
	/**
	 * One of a key's lines.
	 * @param key the key
	 * @return the line ("" for NONE), or null when it is not set
	 */
	public String line(String key);

	/**
	 * @param key a key
	 * @return true if it is set
	 */
	public boolean has(String key);
}
