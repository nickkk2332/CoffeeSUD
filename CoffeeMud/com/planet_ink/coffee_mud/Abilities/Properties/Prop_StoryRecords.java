package com.planet_ink.coffee_mud.Abilities.Properties;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Internal saved journal, map knowledge, unread lore and colour preferences. */
public class Prop_StoryRecords extends Property
{
    @Override public String ID() { return "Prop_StoryRecords"; }
    @Override public String name() { return "Story records"; }
    @Override protected int canAffectCode() { return CAN_MOBS; }
}
