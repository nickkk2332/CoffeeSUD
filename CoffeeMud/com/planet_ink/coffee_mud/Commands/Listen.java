package com.planet_ink.coffee_mud.Commands;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
public class Listen extends StdCommand {
 private final String[] access=I(new String[]{"LISTEN"});
 @Override public String[] getAccessWords(){return access;}
 @Override public boolean execute(final MOB who,final List<String> words,final int flags) {
  final Environmental target=Scenery.target(who,CMParms.combine(words,1));
  if(!Scenery.sense(who,target,"SOUND")) { final Ability a=who.fetchAbility("Thief_Listen"); if(a!=null) a.invoke(who,new ArrayList<String>(words.subList(1,words.size())),null,false,0); else who.tell("You hear nothing unusual."); }
  return false;
 }
 @Override public boolean canBeOrdered(){return true;}
}
