package com.planet_ink.coffee_mud.Abilities.Properties;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
public class Prop_ShowWhen extends Property {
 @Override public String ID(){return "Prop_ShowWhen";}
 @Override public String name(){return "Conditional visibility";}
 @Override protected int canAffectCode(){return CAN_MOBS|CAN_ITEMS|CAN_EXITS|CAN_ROOMS;}
 @Override public boolean okMessage(final Environmental host,final CMMsg msg) {
  if(!super.okMessage(host,msg)) return false;
  if((msg.target()==affected||msg.tool()==affected)&&!ViewRules.allowed(affected,msg.source())) {
   if(msg.targetMajor(com.planet_ink.coffee_mud.Common.interfaces.CMMsg.MASK_MALICIOUS)||msg.sourceMajor(CMMsg.MASK_HANDS)||msg.sourceMajor(CMMsg.MASK_EYES)||msg.targetMinor()==CMMsg.TYP_ENTER) { msg.source().tell("You cannot find that here."); return false; }
  }
  return true;
 }
}
