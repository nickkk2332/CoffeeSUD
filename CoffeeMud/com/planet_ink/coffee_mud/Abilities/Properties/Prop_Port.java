package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
/** A named navigation destination; HIDDEN and KNOWNMASK control discovery. */
public class Prop_Port extends Property
{
    @Override public String ID() { return "Prop_Port"; }
    @Override public String name() { return "Named port"; }
    @Override protected int canAffectCode() { return Ability.CAN_ROOMS; }
}
