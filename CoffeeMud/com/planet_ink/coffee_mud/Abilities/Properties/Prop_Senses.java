package com.planet_ink.coffee_mud.Abilities.Properties;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Authored presentation metadata, interpreted without modifying the host. */
public class Prop_Senses extends Property {
 @Override public String ID(){return "Prop_Senses";}
 @Override public String name(){return "Senses";}
 @Override protected int canAffectCode(){return CAN_ROOMS|CAN_ITEMS|CAN_MOBS|CAN_EXITS;}
}
