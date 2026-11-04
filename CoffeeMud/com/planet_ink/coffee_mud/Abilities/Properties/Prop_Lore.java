package com.planet_ink.coffee_mud.Abilities.Properties;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
public class Prop_Lore extends Property {
 @Override public String ID(){return "Prop_Lore";}
 @Override public String name(){return "Lore unlock";}
 @Override protected int canAffectCode(){return CAN_ROOMS|CAN_ITEMS|CAN_MOBS;}
 @Override public void executeMsg(final Environmental host,final CMMsg msg) {
  super.executeMsg(host,msg); final String on=CMParms.getParmStr(text(),"ON","READ").toUpperCase(); MOB who=msg.source(); boolean fire=false;
  if(on.equals("KILL")) { fire=msg.sourceMinor()==CMMsg.TYP_DEATH&&msg.source()==affected&&msg.tool() instanceof MOB; if(fire) who=(MOB)msg.tool(); }
  else if(on.equals("DEATH")) fire=msg.sourceMinor()==CMMsg.TYP_DEATH&&msg.source()==affected;
  else if(msg.target()==affected) fire=on.equals("READ")&&msg.targetMinor()==CMMsg.TYP_READ||on.equals("GET")&&msg.targetMinor()==CMMsg.TYP_GET||on.equals("EXAMINE")&&msg.targetMinor()==CMMsg.TYP_EXAMINE||on.equals("ENTER")&&msg.targetMinor()==CMMsg.TYP_ENTER;
  if(fire&&who.isPlayer()&&LoreBook.unlock(who,CMParms.getParmStr(text(),"ENTRY",""),CMParms.getParmStr(text(),"SCOPE","CHAR").equalsIgnoreCase("PROFILE"),affected)) {
   final String message=StoryText.render(who,affected,CMParms.getParmStr(text(),"MSG","")); if(!message.isEmpty()) who.tell(message);
  }
 }
}
