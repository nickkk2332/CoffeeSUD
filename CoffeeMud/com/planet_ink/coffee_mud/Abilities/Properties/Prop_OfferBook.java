package com.planet_ink.coffee_mud.Abilities.Properties;
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
 * I14: a seller's offer book (OfferBooks).  Its text is JSON: items (catalog
 * names, or {name, weight}), size, price (a percent of value), reroll (a fee;
 * 0 for none) and refresh (as a quest board's REFRESH), and the book itself,
 * kept here so that it is saved with the seller.  OFFERS works it.
 */
public class Prop_OfferBook extends Property
{
	@Override
	public String ID()
	{
		return "Prop_OfferBook";
	}

	@Override
	public String name()
	{
		return "An offer book";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS | Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		final String why = OfferBooks.problem(text());
		return why.isEmpty() ? "Keeps an offer book: OFFERS shows it" : "An offer book that cannot be read: " + why;
	}
}
