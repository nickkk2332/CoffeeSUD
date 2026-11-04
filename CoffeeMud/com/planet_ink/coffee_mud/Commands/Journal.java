package com.planet_ink.coffee_mud.Commands;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
public class Journal extends StdCommand
{
 private final String[] access=I(new String[]{"JOURNAL"});
 @Override public String[] getAccessWords(){return access;}
 @Override public boolean execute(final MOB who,final List<String> words,final int flags) {
  final String op=words.size()>1?words.get(1).toUpperCase():"";
  if(op.equals("WRITE")) { final String text=CMParms.combine(words,2).trim();if(text.isEmpty())who.tell("Write what in your journal?");else{Chronicle.add(who,"Notes",text,who);who.tell("Journal entry added.");} }
  else if(op.equals("DELETE")) who.tell(Chronicle.delete(who,CMParms.combine(words,2))?"Journal entry deleted.":"No such journal entry.");
  else who.tell(Chronicle.show(who,CMParms.combine(words,op.equals("SEARCH")?2:1),op.equals("SEARCH")));
  return false;
 }
 @Override public boolean canBeOrdered(){return false;}
}
