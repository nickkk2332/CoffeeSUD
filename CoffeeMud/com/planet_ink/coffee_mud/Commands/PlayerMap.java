package com.planet_ink.coffee_mud.Commands;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
public class PlayerMap extends StdCommand {
 private final String[] access=I(new String[]{"MAP"});
 @Override public String[] getAccessWords(){return access;}
 @Override public boolean execute(final MOB who,final List<String> words,final int flags) {
  Room start=who.location();int radius=PlayerMaps.size(who);
  if(words.size()>1) { if(words.get(1).equalsIgnoreCase("AREA")) {start=PlayerMaps.inArea(who,CMParms.combine(words,2));radius=12;} else if(CMath.isInteger(words.get(1))) radius=CMath.s_int(words.get(1));else {who.tell("Use MAP [radius] or MAP AREA name.");return false;} }
  who.tell(PlayerMaps.render(who,start,radius));return false;
 }
 @Override public boolean canBeOrdered(){return false;}
}
