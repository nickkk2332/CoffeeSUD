package com.planet_ink.coffee_mud.Abilities.Properties;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
public class Prop_MapReveal extends Property {
 @Override public String ID(){return "Prop_MapReveal";}
 @Override public String name(){return "Map knowledge";}
 @Override protected int canAffectCode(){return CAN_ITEMS;}
 @Override public void executeMsg(final Environmental host,final CMMsg msg){super.executeMsg(host,msg);if(msg.target()==affected&&msg.targetMinor()==CMMsg.TYP_READ&&msg.source().isPlayer())PlayerMaps.reveal(msg.source(),text());}
}
