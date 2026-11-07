package com.planet_ink.coffee_mud.Abilities.Misc;

import java.io.IOException;
import com.planet_ink.coffee_mud.Abilities.StdAbility;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Items.interfaces.Weapon;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.interfaces.*;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** A bounded hit snapshot. Never procs supports, stacks, or uses restored name-based kill attribution. */
public class CompositionBurn extends StdAbility implements ComposedBurn
{
	@Override public String ID()
	{
		return "CompositionBurn";
	}
	@Override public String name()
	{
		return "Composed burning";
	}
	@Override public String displayText()
	{
		return "(Burning)";
	}
	@Override public int classificationCode()
	{
		return Ability.ACODE_SKILL;
	}
	@Override protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}
	private int damage;
	private String source = "";
	private JSONObject profile = null;
	private double penetration = 0;
	@Override public int snapshotDamage()
	{
		return damage;
	}
	@Override public void setMiscText(final String text)
	{
		super.setMiscText(text);
		damage = 0;
		source = "";
		profile = null;
		penetration = 0;
		try
		{
			final JSONObject data = ModuleDefinitions.parse(text);
			ModuleDefinitions.keys(data, "burn snapshot", "damage", "source", "profile", "penetration");
			final int n = ModuleDefinitions.integer(data.get("damage"), "burn damage");
			final String id = ModuleDefinitions.string(data.get("source"), "burn source");
			if(n < 1 || n > 10000 || !id.startsWith(InstanceIds.PREFIX))
				throw new IOException("invalid burn snapshot");
			if(data.containsKey("profile") != data.containsKey("penetration"))
				throw new IOException("incomplete profile burn snapshot");
			JSONObject pin = null;
			double pen = 0;
			if(data.containsKey("profile"))
			{
				pin = CombatRules.checkPin(ModuleDefinitions.object(data.get("profile"), "burn profile"));
				if(!(data.get("penetration") instanceof Number))
					throw new IOException("invalid burn penetration");
				pen = ((Number)data.get("penetration")).doubleValue();
				if(!Double.isFinite(pen) || pen < 0 || pen > 100)
					throw new IOException("invalid burn penetration");
			}
			damage = n;
			source = id;
			profile = pin;
			penetration = pen;
		}
		catch(final IOException e)
		{
			/* Invalid/missing snapshots expire without damage. */
		}
	}
	public static void apply(final MOB caster, final MOB target, final int damage, final int ticks, final Ability tool)
	{
		apply(caster, target, damage, ticks, tool, null, 0);
	}
	public static boolean applyProfile(final MOB caster, final MOB target, final int damage, final int ticks, final Ability tool, final JSONObject pin, final double penetration)
	{
		if(!OptionalModules.enabled(CombatRules.MODULE))
			return false;
		try
		{
			return apply(caster, target, damage, ticks, tool, CombatRules.checkPin(pin), penetration);
		}
		catch(final IOException e)
		{
			return false;
		}
	}
	private static boolean apply(final MOB caster, final MOB target, final int damage, final int ticks, final Ability tool, final JSONObject pin, final double penetration)
	{
		if(!OptionalModules.enabled(CompositionDefinitions.MODULE) || damage < 1 || damage > 10000 || ticks < 1 || ticks > 20
			|| !Double.isFinite(penetration) || penetration < 0 || penetration > 100)
			return false;
		final CMMsg save = CMClass.getMsg(caster, target, tool, CMMsg.MASK_MALICIOUS | CMMsg.TYP_FIRE | (pin == null ? 0 : CMMsg.MASK_ALWAYS), null);
		if(target.location() == null)
			return false;
		if(pin != null)
		{
			if(!CombatProfiles.announce(caster,target,tool,save))
				return false;
		}
		else
		{
			if(!target.location().okMessage(caster,save))
				return false;
			target.location().send(caster,save);
		}
		if(save.value() > 0)
			return false;
		final Ability old = target.fetchEffect("CompositionBurn");
		if(old instanceof ComposedBurn && ((ComposedBurn)old).snapshotDamage() > damage)
			return false;
		if(old != null)
			old.unInvoke();
		final Ability effect = CMClass.getAbility("CompositionBurn");
		final JSONObject data = new JSONObject();
		data.put("damage", Long.valueOf(damage));
		data.put("source", InstanceIds.reference(caster));
		if(pin != null)
		{
			data.put("profile", pin);
			data.put("penetration", Double.valueOf(penetration));
		}
		effect.setMiscText(ModuleDefinitions.json(data));
		effect.startTickDown(caster, target, ticks);
		return true;
	}
	@Override public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_MOB)
			return super.tick(ticking, tickID);
		final Environmental found = InstanceIds.find(source);
		if(!(affected instanceof MOB) || !(found instanceof MOB) || damage < 1 || !OptionalModules.enabled(CompositionDefinitions.MODULE))
		{
			unInvoke();
			return false;
		}
		final MOB victim = (MOB)affected, caster = (MOB)found;
		if(victim.amDead() || victim.amDestroyed() || Downed.isDown(victim) || caster.amDead() || caster.amDestroyed() || victim.location() == null
			|| !caster.mayIFight(victim)
			|| caster.getGroupMembers(new java.util.HashSet<MOB>()).contains(victim))
		{
			unInvoke();
			return false;
		}
		setInvoker(caster);
		if(profile != null)
		{
			if(!CombatProfiles.burn(caster, victim, this, damage, profile, penetration))
			{
				unInvoke();
				return false;
			}
			return super.tick(ticking, tickID);
		}
		CMLib.combat().postDamage(caster, victim, this, damage, CMMsg.MASK_ALWAYS | CMMsg.TYP_FIRE, Weapon
			.TYPE_BURNING, "The lingering flames <DAMAGE> <T-NAME>!");
		return super.tick(ticking, tickID);
	}
}
