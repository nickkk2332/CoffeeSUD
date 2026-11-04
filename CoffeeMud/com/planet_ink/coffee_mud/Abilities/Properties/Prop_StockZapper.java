package com.planet_ink.coffee_mud.Abilities.Properties;

import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
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
/** Access rules on a shop's stock prototype, evaluated for each customer. */
public class Prop_StockZapper extends Property
{
    @Override public String ID() { return "Prop_StockZapper"; }
    @Override public String name() { return "Restricted stock"; }
    @Override protected int canAffectCode() { return Ability.CAN_ITEMS; }
    private static Ability rule(final Environmental product) { return product instanceof Physical?((Physical)product).fetchEffect("Prop_StockZapper"):null; }
    public static boolean allowed(final Environmental product, final MOB buyer)
    {
        final Ability A=rule(product);
        return A==null||buyer==null||CMLib.masking().maskCheck(Organizations.parameterMask(CMParms.getParmStr(A.text(),"MASK","")),buyer,true);
    }
    public static boolean visible(final Environmental product, final MOB buyer)
    {
        final Ability A=rule(product);
        return A==null||allowed(product,buyer)||!CMParms.getParmBool(A.text(),"HIDE",true);
    }
    public static String refusal(final Environmental product)
    {
        final Ability A=rule(product);
        return A==null?"":CMParms.getParmStr(A.text(),"MSG","You are not permitted to buy that item.");
    }
}
