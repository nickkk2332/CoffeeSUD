package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
/** Authored minutes per step; evaluated once by the movement engine. */
public class Prop_TravelTime extends Property
{
    @Override public String ID() { return "Prop_TravelTime"; }
    @Override public String name() { return "Travel time"; }
    @Override protected int canAffectCode() { return Ability.CAN_ROOMS|Ability.CAN_EXITS|Ability.CAN_AREAS; }
}
