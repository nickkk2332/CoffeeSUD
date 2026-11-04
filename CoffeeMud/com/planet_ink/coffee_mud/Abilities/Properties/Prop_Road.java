package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
/** Drivable terrain, with optional TIME multiplier and NOTFOR vehicle names. */
public class Prop_Road extends Property
{
    @Override public String ID() { return "Prop_Road"; }
    @Override public String name() { return "Road"; }
    @Override protected int canAffectCode() { return Ability.CAN_ROOMS|Ability.CAN_AREAS; }
}
