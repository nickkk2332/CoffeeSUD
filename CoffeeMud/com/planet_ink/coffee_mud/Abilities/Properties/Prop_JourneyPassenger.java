package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.StdAbility;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** Saved identity for a passenger, including identically named mounts. */
public class Prop_JourneyPassenger extends StdAbility
{
    @Override public String ID() { return "Prop_JourneyPassenger"; }
    @Override public String name() { return "Journey companion"; }
    @Override public String displayText() { return ""; }
    @Override protected int canAffectCode() { return CAN_MOBS; }
}
