package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;

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
 * ST16: an area's entry banner: Prop_AreaEntry with the area's name as the
 * title and its description shown unless told otherwise.  TITLE="..."
 * TEXT="..." ONCE (or EACH, the default) MASK="..." COLOR="^H".
 */
public class Prop_AreaBanner extends Prop_AreaEntry
{
	@Override
	public String ID()
	{
		return "Prop_AreaBanner";
	}

	@Override
	public String name()
	{
		return "An area's entry banner";
	}

	@Override
	public String accountForYourself()
	{
		return "A banner on coming into the area";
	}
}
