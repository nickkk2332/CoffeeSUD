package com.planet_ink.coffee_mud.Commands;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
public class Lore extends StdCommand {
 private final String[] access=I(new String[]{"LORE","CODEX"});
 @Override public String[] getAccessWords(){return access;}
 @Override public boolean execute(final MOB who,final List<String> words,final int flags){who.tell(LoreBook.show(who,CMParms.combine(words,1)));return false;}
 @Override public boolean canBeOrdered(){return false;}
}
