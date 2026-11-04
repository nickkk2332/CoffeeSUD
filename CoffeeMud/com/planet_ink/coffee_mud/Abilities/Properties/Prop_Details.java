package com.planet_ink.coffee_mud.Abilities.Properties;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Authored presentation metadata, interpreted without modifying the host. */
public class Prop_Details extends Property {
 @Override public String ID(){return "Prop_Details";}
 @Override public String name(){return "Details";}
 @Override protected int canAffectCode(){return CAN_ROOMS|CAN_ITEMS|CAN_MOBS;}
}
