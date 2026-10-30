package com.planet_ink.coffee_mud.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.planet_ink.coffee_mud.core.CMProps;

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
 * The single-player entry point.  Boots the CoffeeMud engine with no network
 * listeners or outside services, plays one character through this process's
 * console, and exits when that character quits.
 *
 * Any NAME=VALUE argument overrides the ini file, as with {@link MUD}.
 */
public class SUD
{
	private static final String[] SINGLE_PLAYER_SETTINGS =
	{
		"RUNWEBSERVERS=false",
		"RUNSMTPSERVER=false",
		"RUNI3SERVER=false",
		"RUNIMC2CLIENT=false",
		"RUNCM1SERVER=false",
		"RUNGVSERVER=false",
		"COMMONACCOUNTSYSTEM=0",
		"EMAILREQ=DISABLED",
		"SYSMSGS=FILE",
		"ERRMSGS=FILE",
		"WRNMSGS=FILE",
		"DBGMSGS=FILE",
	};

	public static void main(final String[] args)
	{
		CMProps.setSinglePlayer(true);
		MUD.consoleMode = true;
		final List<String> fullArgs = new ArrayList<String>();
		fullArgs.add("CoffeeSUD");
		fullArgs.addAll(Arrays.asList(SINGLE_PLAYER_SETTINGS));
		fullArgs.addAll(Arrays.asList(args));
		System.out.println("Loading the world...");
		MUD.main(fullArgs.toArray(new String[fullArgs.size()]));
		System.exit(0);
	}
}
