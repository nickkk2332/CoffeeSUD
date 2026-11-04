package com.planet_ink.coffee_mud.Commands;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
public class Taste extends StdCommand {
 private final String[] access=I(new String[]{"TASTE"});
 @Override public String[] getAccessWords(){return access;}
 @Override public boolean execute(final MOB who,final List<String> words,final int flags) {if(!Scenery.sense(who,Scenery.target(who,CMParms.combine(words,1)),"TASTE")) who.tell("You taste nothing unusual.");return false;}
 @Override public boolean canBeOrdered(){return true;}
}
