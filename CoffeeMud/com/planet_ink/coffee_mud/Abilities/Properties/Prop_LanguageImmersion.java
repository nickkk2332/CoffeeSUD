package com.planet_ink.coffee_mud.Abilities.Properties;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
public class Prop_LanguageImmersion extends Property {
 @Override public String ID(){return "Prop_LanguageImmersion";}
 @Override public String name(){return "LanguageImmersion";}
 @Override protected int canAffectCode(){return CAN_MOBS|CAN_ITEMS|CAN_ROOMS|CAN_AREAS;}
}
