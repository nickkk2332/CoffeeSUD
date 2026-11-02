package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

/*
   Copyright 2026 CoffeeSUD contributors

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

	   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
/**
 * I21: whether a key fits a lock.  A key's code may be a list (crypt;vault)
 * or end in * (jail.*, a master key for every lock whose code begins jail.);
 * a lock with Prop_LockRules KEYS=a;b takes those codes as well as its own.
 * A key on a keyring is found however deep the keyring is.
 */
public final class Keys
{
	private Keys()
	{
	}

	/**
	 * Whether one code fits one lock name.
	 * @param code a key's code, or one of its list
	 * @param lock a lock's name
	 * @return true if it fits
	 */
	public static boolean fitsOne(final String code, final String lock)
	{
		final String c = code.trim();
		final String l = lock.trim();
		if((c.length() == 0) || (l.length() == 0))
			return false;
		if(c.endsWith("*"))
			return l.toLowerCase().startsWith(c.substring(0, c.length() - 1).toLowerCase());
		return c.equalsIgnoreCase(l);
	}

	/**
	 * Whether a key fits a lock.
	 * @param keyCode the key's code
	 * @param lockName the lock's key name
	 * @param lock the exit or container, or null
	 * @return true if it fits
	 */
	public static boolean fits(final String keyCode, final String lockName, final Physical lock)
	{
		if((keyCode == null) || (lockName == null))
			return false;
		String extra = "";
		final Ability A = (lock == null) ? null : lock.fetchEffect("Prop_LockRules");
		if(A != null)
			extra = CMParms.getParmStr(CMParms.parseExactParms(A.text()), "KEYS", "");
		for(final String code : keyCode.split(";"))
		{
			if(fitsOne(code, lockName))
				return true;
			for(final String l : extra.split(";"))
			{
				if(fitsOne(code, l))
					return true;
			}
		}
		return false;
	}

	/**
	 * Whether one may use a key: its Prop_KeyRules MASK, if it has one.
	 * @param I the key
	 * @param mob who would use it
	 * @return true if they may
	 */
	public static boolean usable(final Item I, final MOB mob)
	{
		final Ability A = I.fetchEffect("Prop_KeyRules");
		if((A == null) || (mob == null))
			return true;
		final String m = CMParms.getParmStr(CMParms.parseExactParms(A.text()), "MASK", "").trim();
		return (m.length() == 0) || CMLib.masking().maskCheck(m, mob, true);
	}

	/**
	 * Whether a key is where it may be used: carried, or on a keyring (however
	 * deep the keyring is).
	 * @param I the key
	 * @return true if it is
	 */
	public static boolean reachable(final Item I)
	{
		final Container C = I.container();
		return (C == null) || ((C.containTypes() & Container.CONTAIN_KEYS) > 0);
	}
}
