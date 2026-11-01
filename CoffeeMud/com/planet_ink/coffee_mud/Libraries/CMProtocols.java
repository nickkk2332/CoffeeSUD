package com.planet_ink.coffee_mud.Libraries;
import com.planet_ink.coffee_mud.core.exceptions.BadEmailAddressException;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.CMProps.ListFile;
import com.planet_ink.coffee_mud.core.CMProps.Str;
import com.planet_ink.coffee_mud.core.CMSecurity.DbgFlag;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.MiniJSON.MJSONException;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.CharCreationLibrary.LoginSession;
import com.planet_ink.coffee_mud.Libraries.interfaces.PlayerLibrary.ThinPlayer;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.GridZones.XYVector;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.mozilla.javascript.*;
import java.util.Hashtable; // not Rhino's
import org.mozilla.javascript.optimizer.*;
/*
   Copyright 2013-2025 Bo Zimmerman

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
public class CMProtocols extends StdLibrary implements ProtocolLibrary
{
	@Override
	public String ID()
	{
		return "CMProtocols";
	}

	private Object llmModel = null;
	private URLClassLoader llmClassLoader = null;

	// this is the sound support method.
	// it builds a valid MSP sound code from built-in web server
	// info, and the info provided.
	public String msp(final String soundName, final int volume, final int priority)
	{
		if((soundName==null)||(soundName.length()==0)||CMSecurity.isDisabled(CMSecurity.DisFlag.MSP))
			return "";
		final String mspSoundPath=CMProps.getVar(Str.MSPPATH);
		if(mspSoundPath.length()>0)
			return " !!SOUND("+soundName+" V="+volume+" P="+priority+" U="+mspSoundPath+") ";
		return " !!SOUND("+soundName+" V="+volume+" P="+priority+") ";
	}

	@Override
	public String[] mxpImagePath(String fileName)
	{
		if((fileName==null)||(fileName.trim().length()==0))
			return new String[]{"",""};
		final String mxpImagePath=CMProps.getVar(Str.MXPIMAGEPATH);
		if((mxpImagePath.length()==0)
		||(CMSecurity.isDisabled(CMSecurity.DisFlag.MXP)))
			return new String[]{"",""};
		int x=fileName.lastIndexOf('=');
		String preFilename="";
		if(x>=0)
		{
			preFilename=fileName.substring(0,x+1);
			fileName=fileName.substring(x+1);
		}
		x=fileName.lastIndexOf('/');
		if(x>=0)
		{
			preFilename+=fileName.substring(0,x+1);
			fileName=fileName.substring(x+1);
		}
		if(mxpImagePath.endsWith("/"))
			return new String[]{mxpImagePath+preFilename,fileName};
		return new String[]{mxpImagePath+"/"+preFilename,fileName};
	}

	@Override
	public String mxpImage(final Environmental E, final String parms)
	{
		if((CMProps.getVar(Str.MXPIMAGEPATH).length()==0)
		||(CMSecurity.isDisabled(CMSecurity.DisFlag.MXP)))
			return "";
		final String image=E.image();
		if(image.length()==0)
			return "";
		final String[] fixedFilenames=mxpImagePath(image);
		if(fixedFilenames[0].length()==0)
			return "";
		return "^<IMAGE '"+fixedFilenames[1]+"' URL=\""+fixedFilenames[0]+"\" "+parms+"^>^N";
	}

	@Override
	public String mxpImage(final Environmental E, final String parms, final String pre, final String post)
	{
		if((CMProps.getVar(Str.MXPIMAGEPATH).length()==0)
		||(CMSecurity.isDisabled(CMSecurity.DisFlag.MXP)))
			return "";
		final String image=E.image();
		if(image.length()==0)
			return "";
		final String[] fixedFilenames=mxpImagePath(image);
		if(fixedFilenames[0].length()==0)
			return "";
		return pre+"^<IMAGE '"+fixedFilenames[1]+"' URL=\""+fixedFilenames[0]+"\" "+parms+"^>^N"+post;
	}

	@SuppressWarnings("unchecked")
	public String getHashedMXPImage(final String key)
	{
		Map<String,String> H=(Map<String,String>)Resources.getResource("MXP_IMAGES");
		if(H==null)
			getDefaultMXPImage(null);
		H=(Map<String,String>)Resources.getResource("MXP_IMAGES");
		if(H==null)
			return "";
		return getHashedMXPImage(H,key);

	}

	@Override
	public String msp(final String soundName, final int priority)
	{
		return msp(soundName,50,CMLib.dice().roll(1,50,priority));
	}

	public String getHashedMXPImage(final Map<String, String> H, final String key)
	{
		if(H==null)
			return "";
		final String s=H.get(key);
		if(s==null)
			return null;
		if(s.trim().length()==0)
			return null;
		if(s.equalsIgnoreCase("NULL"))
			return "";
		return s;
	}

	@Override
	public String getDefaultMXPImage(final CMObject O)
	{
		if((CMProps.getVar(Str.MXPIMAGEPATH).length()==0)
		||(CMSecurity.isDisabled(CMSecurity.DisFlag.MXP)))
			return "";
		@SuppressWarnings("unchecked")
		Map<String,String> H=(Map<String,String>)Resources.getResource("PARSED: mxp_images.ini");
		if(H==null)
		{
			H=new Hashtable<String,String>();
			final List<String> V=Resources.getFileLineVector(new CMFile("resources/mxp_images.ini",null).text());
			if((V!=null)&&(V.size()>0))
			{
				String s=null;
				int x=0;
				for(int v=0;v<V.size();v++)
				{
					s=V.get(v).trim();
					if(s.startsWith("//")||s.startsWith(";"))
						continue;
					x=s.indexOf('=');
					if(x<0)
						continue;
					if(s.substring(x+1).trim().length()>0)
						H.put(s.substring(0,x),s.substring(x+1));
				}
			}
			Resources.submitResource("PARSED: mxp_images.ini",H);
		}
		String image=null;
		if(O instanceof Race)
		{
			image=getHashedMXPImage(H,"RACE_"+O.ID().toUpperCase());
			if(image==null)
				image=getHashedMXPImage(H,"RACECAT_"+((Race)O).racialCategory().toUpperCase().replace(' ','_'));
			if(image==null)
				image=getHashedMXPImage(H,"RACE_*");
			if(image==null)
				image=getHashedMXPImage(H,"RACECAT_*");
		}
		else
		if(O instanceof MOB)
		{
			final String raceName=((MOB)O).charStats().raceName();
			Race R=null;
			for(final Enumeration<Race> e=CMClass.races();e.hasMoreElements();)
			{
				R=e.nextElement();
				if(raceName.equalsIgnoreCase(R.name()))
					image=getDefaultMXPImage(R);
			}
			if(image==null)
				image=getDefaultMXPImage(((MOB)O).charStats().getMyRace());
		}
		else
		if(O instanceof Room)
		{
			image=getHashedMXPImage(H,"ROOM_"+O.ID().toUpperCase());
			if(image==null)
				if(CMath.bset(((Room)O).domainType(),Room.INDOORS))
					image=getHashedMXPImage(H,"LOCALE_INDOOR_"+Room.DOMAIN_INDOORS_DESCS[((Room)O).domainType()-Room.INDOORS]);
				else
					image=getHashedMXPImage(H,"LOCALE_"+Room.DOMAIN_OUTDOOR_DESCS[((Room)O).domainType()]);
			if(image==null)
				image=getHashedMXPImage(H,"ROOM_*");
			if(image==null)
				image=getHashedMXPImage(H,"LOCALE_*");
		}
		else
		if(O instanceof Exit)
		{
			image=getHashedMXPImage(H,"EXIT_"+O.ID().toUpperCase());
			if(image==null)
				image=getHashedMXPImage(H,"EXIT_"+((Exit)O).doorName().toUpperCase());
			if(image==null)
				if(((Exit)O).hasADoor())
					image=getHashedMXPImage(H,"EXIT_WITHDOOR");
				else
					image=getHashedMXPImage(H,"EXIT_OPEN");
			if(image==null)
				image=getHashedMXPImage(H,"EXIT_*");
		}
		else
		if(O instanceof Rideable)
		{
			image=getHashedMXPImage(H,"RIDEABLE_"+((Rideable)O).rideBasis().toString());
			if(image==null)
				image=getHashedMXPImage(H,"RIDEABLE_*");
		}
		else
		if(O instanceof Shield)
		{
			image=getHashedMXPImage(H,"SHIELD_"+RawMaterial.Material.findByMask(((Shield)O).material()&RawMaterial.MATERIAL_MASK).desc());
			if(image==null)
				image=getHashedMXPImage(H,"SHIELD_*");
		}
		else
		if(O instanceof Coins)
		{
			image=getHashedMXPImage(H,"COINS_"+RawMaterial.CODES.NAME(((Coins)O).material()));
			if(image==null)
				image=getHashedMXPImage(H,"COINS_*");
		}
		else
		if(O instanceof Ammunition)
		{
			image=getHashedMXPImage(H,"AMMO_"+((Ammunition)O).ammunitionType().toUpperCase().replace(' ','_'));
			if(image==null)
				image=getHashedMXPImage(H,"AMMO_*");
		}
		else
		if(O instanceof CagedAnimal)
		{
			final MOB mob=((CagedAnimal)O).unCageMe();
			return getDefaultMXPImage(mob);
		}
		else
		if(O instanceof ClanItem)
		{
			image=getHashedMXPImage(H,"CLAN_"+O.ID().toUpperCase());
			if(image==null)
				image=getHashedMXPImage(H,"CLAN_"+((ClanItem)O).getClanItemType().toString().toUpperCase());
			if(image==null)
				image=getHashedMXPImage(H,"CLAN_*");
		}
		else
		if(O instanceof DeadBody)
		{
			final Race R=((DeadBody)O).charStats().getMyRace();
			if(R!=null)
			{
				image=getHashedMXPImage(H,"CORPSE_"+R.ID().toUpperCase());
				if(image==null)
					image=getHashedMXPImage(H,"CORPSECAT_"+R.racialCategory().toUpperCase().replace(' ','_'));
			}
			if(image==null)
				image=getHashedMXPImage(H,"CORPSE_*");
			if(image==null)
				image=getHashedMXPImage(H,"CORPSECAT_*");
		}
		else
		if(O instanceof RawMaterial)
		{
			image=getHashedMXPImage(H,"RESOURCE_"+RawMaterial.CODES.NAME(((RawMaterial)O).material()));
			if(image==null)
				image=getHashedMXPImage(H,"RESOURCE_"+RawMaterial.Material.findByMask(((RawMaterial)O).material()&RawMaterial.MATERIAL_MASK).desc());
			if(image==null)
				image=getHashedMXPImage(H,"RESOURCE_*");
		}
		else
		if(O instanceof DoorKey)
		{
			image=getHashedMXPImage(H,"KEY_"+RawMaterial.CODES.NAME(((DoorKey)O).material()));
			image=getHashedMXPImage(H,"KEY_"+RawMaterial.Material.findByMask(((DoorKey)O).material()&RawMaterial.MATERIAL_MASK).desc());
			if(image==null)
				image=getHashedMXPImage(H,"KEY_*");
		}
		else
		if(O instanceof LandTitle)
			image=getHashedMXPImage(H,"ITEM_LANDTITLE");
		else
		if(O instanceof SpaceShip)
			image=getHashedMXPImage(H,"ITEM_SPACESHIP");
		else
		if(O instanceof MagicDust)
		{
			final List<Ability> V=((MagicDust)O).getSpells();
			if(V.size()>0)
				image=getHashedMXPImage(H,"DUST_"+V.get(0).ID().toUpperCase());
			if(image==null)
				image=getHashedMXPImage(H,"DUST_*");
		}
		else
		if(O instanceof com.planet_ink.coffee_mud.Items.interfaces.RoomMap)
			image=getHashedMXPImage(H,"ITEM_MAP");
		else
		if(O instanceof MusicalInstrument)
		{
			image=getHashedMXPImage(H,"MUSINSTR_"+((MusicalInstrument)O).getInstrumentTypeName());
			if(image==null)
				image=getHashedMXPImage(H,"MUSINSTR_*");
		}
		else
		if(O instanceof PackagedItems)
			image=getHashedMXPImage(H,"ITEM_PACKAGED");
		else
		if(O instanceof Perfume)
			image=getHashedMXPImage(H,"ITEM_PERFUME");
		else
		if(O instanceof Pill)
		{
			final List<Ability> V=((Pill)O).getSpells();
			if(V.size()>0)
				image=getHashedMXPImage(H,"PILL_"+V.get(0).ID().toUpperCase());
			if(image==null)
				image=getHashedMXPImage(H,"PILL_*");
		}
		else
		if(O instanceof Potion)
		{
			final List<Ability> V=((Potion)O).getSpells();
			if(V.size()>0)
				image=getHashedMXPImage(H,"POTION_"+V.get(0).ID().toUpperCase());
			if(image==null)
				image=getHashedMXPImage(H,"POTION_*");
		}
		else
		if(O instanceof Recipes)
			image=getHashedMXPImage(H,"ITEM_RECIPE");
		else
		if(O instanceof Scroll)
		{
			final List<Ability> V=((Scroll)O).getSpells();
			if(V.size()>0)
				image=getHashedMXPImage(H,"SCROLL_"+V.get(0).ID().toUpperCase());
			if(image==null)
				image=getHashedMXPImage(H,"SCROLL_*");
		}
		else
		if(O instanceof PowerGenerator)
		{
			final String key = "POWERGENERATOR_"+O.ID().toUpperCase();
			if(H.containsKey(key))
				image=getHashedMXPImage(H,"POWERGENERATOR_"+key);
			else
				image=getHashedMXPImage(H,"POWERGENERATOR");
		}
		else
		if(O instanceof PowerSource)
		{
			final String key = "POWERSOURCE_"+O.ID().toUpperCase();
			if(H.containsKey(key))
				image=getHashedMXPImage(H,key);
			else
				image=getHashedMXPImage(H,"POWERSOURCE");
		}
		else
		if(O instanceof ElecPanel)
		{
			final String key = "ELECPANEL_"+O.ID().toUpperCase();
			if(H.containsKey(key))
				image=getHashedMXPImage(H,key);
			else
			if(((ElecPanel) O).panelType()==null)
				image=getHashedMXPImage(H,"ELECPANEL");
			else
			if(H.containsKey(((ElecPanel) O).panelType().toString()))
				image=getHashedMXPImage(H,((ElecPanel) O).panelType().toString());
			else
			if(H.containsKey(((ElecPanel) O).getTechType().toString()))
				image=getHashedMXPImage(H,((ElecPanel) O).getTechType().toString());
			else
				image=getHashedMXPImage(H,"ELECPANEL");
		}
		else
		if(O instanceof TechComponent)
		{
			final String key = "SHIPCOMP_"+O.ID().toUpperCase();
			if(H.containsKey(key))
				image=getHashedMXPImage(H,key);
			else
				image=getHashedMXPImage(H,((TechComponent) O).getTechType().toString());
			if(image==null)
				image=getHashedMXPImage(H,"SHIPCOMP_*");
		}
		else
		if(O instanceof Software)
			image=getHashedMXPImage(H,"ITEM_SOFTWARE");
		else
		if(O instanceof Technical)
		{
			String key;
			key = "TECHNICAL_"+((Technical)O).getTechType().toString();
			if(!H.containsKey(key))
				key = "ELECTRONICS_"+((Technical)O).getTechType().toString();
			if(!H.containsKey(key))
				key = "ELECTRONICS_*";
			image=getHashedMXPImage(H,key);
			if(image==null)
				image=getHashedMXPImage(H,"TECH_*");
		}
		else
		if(O instanceof Armor)
		{
			final Armor A=(Armor)O;
			final long[] bits=
			{Wearable.WORN_TORSO, Wearable.WORN_FEET, Wearable.WORN_LEGS, Wearable.WORN_HANDS, Wearable.WORN_ARMS,
			 Wearable.WORN_HEAD, Wearable.WORN_EARS, Wearable.WORN_EYES, Wearable.WORN_MOUTH, Wearable.WORN_NECK,
			 Wearable.WORN_LEFT_FINGER, Wearable.WORN_LEFT_WRIST, Wearable.WORN_BACK, Wearable.WORN_WAIST,
			 Wearable.WORN_ABOUT_BODY, Wearable.WORN_FLOATING_NEARBY, Wearable.WORN_HELD, Wearable.WORN_WIELD};
			final String[] bitdesc=
			{"TORSO","FEET","LEGS","HANDS","ARMS","HEAD","EARS","EYES","MOUTH",
			 "NECK","FINGERS","WRIST","BACK","WAIST","BODY","FLOATER","HELD","WIELDED"};
			for(int i=0;i<bits.length;i++)
			{
				if(CMath.bset(A.rawProperLocationBitmap(),bits[i]))
				{
					image=getHashedMXPImage(H,"ARMOR_"+bitdesc[i]);
					break;
				}
			}
			if(image==null)
				image=getHashedMXPImage(H,"ARMOR_*");
		}
		else
		if(O instanceof Weapon)
		{
			image=getHashedMXPImage(H,"WEAPON_"+Weapon.CLASS_DESCS[((Weapon)O).weaponClassification()]);
			if(image==null)
				image=getHashedMXPImage(H,"WEAPON_"+Weapon.TYPE_DESCS[((Weapon)O).weaponDamageType()]);
			if(O instanceof AmmunitionWeapon)
			{
				if(image==null)
					image=getHashedMXPImage(H,"WEAPON_"+((AmmunitionWeapon)O).ammunitionType().toUpperCase().replace(' ','_'));
			}
			if(image==null)
				image=getHashedMXPImage(H,"WEAPON_*");
		}
		else
		if(O instanceof Wand)
		{
			image=getHashedMXPImage(H,"WAND_"+O.ID().toUpperCase());
			if(image==null)
			{
				final Ability A=((Wand)O).getSpell();
				if(A!=null)
					image=getHashedMXPImage(H,"WAND_"+A.ID().toUpperCase());
			}
			if(image==null)
				image=getHashedMXPImage(H,"WAND_*");
		}
		else
		if(O instanceof Food)
		{
			image=getHashedMXPImage(H,"FOOD_"+O.ID().toUpperCase());
			if(image==null)
				image=getHashedMXPImage(H,"FOOD_"+RawMaterial.CODES.NAME(((Food)O).material()));
			if(image==null)
				image=getHashedMXPImage(H,"FOOD_"+RawMaterial.Material.findByMask(((Food)O).material()&RawMaterial.MATERIAL_MASK).desc());
			if(image==null)
				image=getHashedMXPImage(H,"FOOD_*");
		}
		else
		if(O instanceof Drink)
		{
			image=getHashedMXPImage(H,"DRINK_"+O.ID().toUpperCase());
			if(image==null)
				image=getHashedMXPImage(H,"DRINK_"+RawMaterial.CODES.NAME(((Item)O).material()));
			if(image==null)
				image=getHashedMXPImage(H,"DRINK_"+RawMaterial.Material.findByMask(((Item)O).material()&RawMaterial.MATERIAL_MASK).desc());
			if(image==null)
				image=getHashedMXPImage(H,"DRINK_*");
		}
		else
		if(O instanceof Light)
		{
			image=getHashedMXPImage(H,"LIGHT_"+O.ID().toUpperCase());
			image=getHashedMXPImage(H,"LIGHT_"+RawMaterial.Material.findByMask(((Light)O).material()&RawMaterial.MATERIAL_MASK).desc());
			if(image==null)
				image=getHashedMXPImage(H,"LIGHT_*");
		}
		else
		if(O instanceof Container)
		{
			image=getHashedMXPImage(H,"CONTAINER_"+O.ID().toUpperCase());
			final String lid=((Container)O).hasADoor()?"LID_":"";
			if(image==null)
				image=getHashedMXPImage(H,"CONTAINER_"+lid+RawMaterial.Material.findByMask(((Container)O).material()&RawMaterial.MATERIAL_MASK).desc());
			if(image==null)
				image=getHashedMXPImage(H,"CONTAINER_"+lid+"*");
		}
		else
		if(O instanceof Electronics)
			image=getHashedMXPImage(H,"ITEM_ELECTRONICS");
		else
		if(O instanceof MiscMagic)
			image=getHashedMXPImage(H,"ITEM_MISCMAGIC");
		if((image==null)&&(O instanceof Item))
		{
			image=getHashedMXPImage(H,"ITEM_"+O.ID().toUpperCase());
			if(image==null)
				image=getHashedMXPImage(H,"ITEM_"+RawMaterial.CODES.NAME(((Item)O).material()));
			if(image==null)
				image=getHashedMXPImage(H,"ITEM_"+RawMaterial.Material.findByMask(((Item)O).material()&RawMaterial.MATERIAL_MASK).desc());
			if(image==null)
				image=getHashedMXPImage(H,"ITEM_*");
		}
		if(image==null)
			image=getHashedMXPImage(H,"*");
		if(image==null)
			return "";
		return image;
	}

	@Override
	public byte[] stripTelnet(final byte[] buf)
	{
		boolean strip=false;
		final int num = buf.length;
		for(int i=0;i<num;i++)
		{
			if(buf[i] == (byte)Session.TELNET_IAC)
				strip=true;
		}
		if(!strip)
			return buf;
		final ByteArrayOutputStream bout = new ByteArrayOutputStream();
		int last=0;
		for(int i=0;i<num;i++)
		{
			if(buf[i] == (byte)Session.TELNET_IAC)
			{
				bout.write(buf,last,i-last);
				last=i+1;
				if(++i<num)
				{
					switch(buf[i])
					{
					case (byte)Session.TELNET_DO:
					case (byte)Session.TELNET_DONT:
					case (byte)Session.TELNET_WILL:
					case (byte)Session.TELNET_WONT:
						++i; // i is now on the offending code
						last=i+1;
						break;
					case (byte)Session.TELNET_SB:
						while(++i < num && (buf[i] != (byte)Session.TELNET_SE))
						{}
						last=(i<num)?i+1:num;
						break;
					default:
						if(buf[i] < 0)
							last=i+1;
						break;
					}
				}
			}
		}
		bout.write(buf,last,num-last);
		return bout.toByteArray();
	}

	@Override
	public byte[] stripLF(final byte[] buf)
	{
		boolean strip=false;
		final int num = buf.length;
		for(int i=0;i<num;i++)
		{
			if(buf[i] == (byte)10)
				strip=true;
		}
		if(!strip)
			return buf;
		final ByteArrayOutputStream bout = new ByteArrayOutputStream();
		int last=0;
		for(int i=0;i<num;i++)
		{
			if(buf[i] == 10)
			{
				bout.write(buf,last,i-last);
				last=i+1;
			}
		}
		bout.write(buf,last,num-last);
		return bout.toByteArray();
	}

	@Override
	public byte[] stripNonASCII(final byte[] buf)
	{
		boolean strip=false;
		final int num = buf.length;
		for(int i=0;i<num;i++)
		{
			if(buf[i] < 0)
				strip=true;
		}
		if(!strip)
			return buf;
		final ByteArrayOutputStream bout = new ByteArrayOutputStream();
		int last=0;
		for(int i=0;i<num;i++)
		{
			if(buf[i] < 0)
			{
				bout.write(buf,last,i-last);
				last=i+1;
			}
		}
		bout.write(buf,last,num-last);
		return bout.toByteArray();
	}

	@Override
	public byte[] stripANSI(final byte[] buf)
	{
		boolean strip=false;
		final int num = buf.length;
		for(int i=0;i<num;i++)
		{
			if(buf[i] == (byte)'\033')
				strip=true;
		}
		if(!strip)
			return buf;
		final ByteArrayOutputStream bout = new ByteArrayOutputStream();
		int last=0;
		for(int i=0;i<num-1;i++)
		{
			if((buf[i] == '\033') && (buf[i+1]=='['))
			{
				bout.write(buf,last,i-last);
				i++;
				while(++i<num && (buf[i] != 'm')&& (buf[i] != 'z'))
				{}
				last=i+1;
			}
		}
		bout.write(buf,last,num-last);
		return bout.toByteArray();
	}

	private static enum LLM
	{
		OLLAMA("dev.langchain4j.model.ollama.OllamaChatModel",
				"dev.langchain4j.model.ollama.OllamaChatModel$OllamaChatModelBuilder",
				new String[] {"baseUrl", "modelName"}),
		ANTHROPIC("dev.langchain4j.model.anthropic.AnthropicChatModel",
				"dev.langchain4j.model.anthropic.AnthropicChatModel$AnthropicChatModelBuilder",
				new String[] {"apiKey"}),
		AZURE("dev.langchain4j.model.azure.AzureOpenAiChatModel",
				"dev.langchain4j.model.azure.AzureOpenAiChatModel$AzureOpenAiChatModelBuilder",
				new String[] {"endpoint","apiKey","deploymentName"}),
		DASHSCOPE("dev.langchain4j.model.qwen.QwenChatModel",
				"dev.langchain4j.model.qwen.QwenChatModel$QwenChatModelBuilder",
				new String[] {"apiKey"}),
		GITHUB("dev.langchain4j.model.github.GitHubModelsChatModel",
				"dev.langchain4j.model.github.GitHubModelsChatModel$GitHubModelsChatModelBuilder",
				new String[] {"gitHubToken","modelName"}),
		GEMINI("dev.langchain4j.model.googleai.GoogleAiGeminiChatModel",
				"dev.langchain4j.model.googleai.GoogleAiGeminiChatModel$GoogleAiGeminiChatModelBuilder",
				new String[] {"apiKey"}),
		VERTEXGEMINI("dev.langchain4j.model.vertexai.VertexAiGeminiChatModel",
				"dev.langchain4j.model.vertexai.VertexAiGeminiChatModel$VertexAiGeminiChatModelBuilder",
				new String[] {"project","location","modelName"}),
		VERTEXPALM2("dev.langchain4j.model.vertexai.VertexAiChatModel",
				"dev.langchain4j.model.vertexai.VertexAiChatModel$VertexAiChatModelBuilder",
				new String[] {"endpoint","location","publisher","project","modelName"}),
		HUGGINGFACE("dev.langchain4j.model.huggingface.HuggingFaceChatModel",
				"dev.langchain4j.model.huggingface.HuggingFaceChatModel$HuggingFaceChatModelBuilder",
				new String[] {"apiKey","modelId"}),
		JLAMA("dev.langchain4j.model.jlama.JlamaChatModel",
				"dev.langchain4j.model.jlama.JlamaChatModel$JlamaChatModelBuilder",
				new String[] {"modelPath"}),
		LOCALAI("dev.langchain4j.model.localai.LocalAiChatModel",
				"dev.langchain4j.model.localai.LocalAiChatModel$LocalAiChatModelBuilder",
				new String[] {"baseUrl","modelName"}),
		MISTRAL("dev.langchain4j.model.mistralai.MistralAiChatModel",
				"dev.langchain4j.model.mistralai.MistralAiChatModel$MistralAiChatModelBuilder",
				new String[] {"apiKey"}),
		OPENAI("dev.langchain4j.model.openai.OpenAiChatModel",
				"dev.langchain4j.model.openai.OpenAiChatModel$OpenAiChatModelBuilder",
				new String[] {"apiKey"}),
		ORACLEGENAI("dev.langchain4j.model.oracle.oci.genai.OciGenAiCohereChatModel",
				"dev.langchain4j.model.oracle.oci.genai.OciGenAiCohereChatModel$OciGenAiCohereChatModelBuilder",
				new String[] {"compartmentId"}),
		QIANFAN("dev.langchain4j.model.qianfan.QianfanChatModel",
				"dev.langchain4j.model.qianfan.QianfanChatModel$QianfanChatModelBuilder",
				new String[] {"apiKey","secretKey"}),
		CLOUDFLARE("dev.langchain4j.model.cloudflare.WorkersAiChatModel",
				"dev.langchain4j.model.cloudflare.WorkersAiChatModel$WorkersAiChatModelBuilder",
				new String[] {"accountId","apiToken","modelName"}),
		ZHIPUAI("dev.langchain4j.model.zhipuai.ZhipuAiChatModel",
				"dev.langchain4j.model.zhipuai.ZhipuAiChatModel$ZhipuAiChatModelBuilder",
				new String[] {"apiKey"}),
		BEDROCK_ANTHROPIC("dev.langchain4j.model.bedrock.BedrockAnthropicClaudeChatModel",
				"dev.langchain4j.model.bedrock.BedrockAnthropicClaudeChatModel$BedrockAnthropicClaudeChatModelBuilder",
				new String[] {}),
		BEDROCK_COHERE("dev.langchain4j.model.bedrock.BedrockCohereCommandChatModel",
				"dev.langchain4j.model.bedrock.BedrockCohereCommandChatModel$BedrockCohereCommandChatModelBuilder",
				new String[] {}),
		BEDROCK_LLAMA("dev.langchain4j.model.bedrock.BedrockLlama2ChatModel",
				"dev.langchain4j.model.bedrock.BedrockLlama2ChatModel$BedrockLlama2ChatModelBuilder",
				new String[] {}),
		COHERE("dev.langchain4j.model.cohere.CohereChatModel",
				"dev.langchain4j.model.cohere.CohereChatModel$CohereChatModelBuilder",
				new String[] {"apiKey"}),
		DEEPINFRA("dev.langchain4j.model.deepinfra.DeepInfraChatModel",
				"dev.langchain4j.model.deepinfra.DeepInfraChatModel$DeepInfraChatModelBuilder",
				new String[] {"apiKey"}),
		GROQ("dev.langchain4j.model.groq.GroqChatModel",
				"dev.langchain4j.model.groq.GroqChatModel$GroqChatModelBuilder",
				new String[] {"apiKey"}),
		IBM("dev.langchain4j.model.ibm.IbmChatModel",
				"dev.langchain4j.model.ibm.IbmChatModel$IbmChatModelBuilder",
				new String[] {"apiKey","modelId","projectId"}),
		NVIDIA("dev.langchain4j.model.nvidia.NvidiaChatModel",
				"dev.langchain4j.model.nvidia.NvidiaChatModel$NvidiaChatModelBuilder",
				new String[] {"apiKey"}),
		TOGETHERAI("dev.langchain4j.model.together.TogetherAiChatModel",
				"dev.langchain4j.model.together.TogetherAiChatModel$TogetherAiChatModelBuilder",
				new String[] {"apiKey","modelName"}),
		XINFERENCE("dev.langchain4j.model.xinference.XinferenceChatModel",
				"dev.langchain4j.model.xinference.XinferenceChatModel$XinferenceChatModelBuilder",
				new String[] {"baseUrl", "modelName"})
		;

		public String modelClass;
		public String builderClass;
		public String[] reqs;

		private LLM(final String modelClass, final String builderClass, final String[] reqs) {
			this.modelClass = modelClass;
			this.builderClass = builderClass;
			this.reqs = reqs;
		}
	}

	private Object initializeLLMSession()
	{
		Object llmModel = this.llmModel;
		if(llmModel instanceof Long)
		{
			final Long l = (Long)llmModel;
			if(System.currentTimeMillis() < (l.longValue()+360000))
				return llmModel;
		}
		else
		if(llmModel != null)
			return llmModel;
		final String jarPath = CMProps.getVar(Str.LANGCHAIN4J_JAR_PATH);
		if(jarPath.length()==0)
		{
			this.llmModel = Long.valueOf(System.currentTimeMillis());
			return this.llmModel;
		}
		System.setProperty("slf4j.internal.verbosity", "ERROR");
		final CMFile F = new CMFile(jarPath, null);
		if(!F.exists())
		{
			Log.errOut("LANGCHAIN4J jar file not found in "+jarPath);
			this.llmModel = Long.valueOf(System.currentTimeMillis());
			return this.llmModel;
		}
		final URL jarUrl;
		try
		{
			jarUrl = new URI("vfs:" + jarPath).toURL();
		}
		catch (final Exception e)
		{
			Log.errOut(e);
			this.llmModel = Long.valueOf(System.currentTimeMillis());
			return this.llmModel;
		}
		llmClassLoader=new URLClassLoader(new URL[]{jarUrl});
		LLM llmType = null;
		llmType = (LLM)CMath.s_valueOf(LLM.class, CMProps.getVar(Str.LANGCHAIN4J_LLM_TYPE));
		if(llmType == null)
		{
			Log.errOut("LANGCHAIN4J_LLM_TYPE not correctly set in INI file. ");
			this.llmModel = Long.valueOf(System.currentTimeMillis());
			return this.llmModel;
		}
		try
		{
			final Double TEMPERATURE = Double.valueOf(0.7);
			final Long TIMEOUT_SECONDS = Long.valueOf(20);
			// Load LangChain4j classes dynamically
			final Class<?> ollamaChatModelBuilderClass = llmClassLoader.loadClass(llmType.builderClass);
			final Class<?> ollamaChatModelClass = llmClassLoader.loadClass(llmType.modelClass);
			final Method builderMethod = ollamaChatModelClass.getMethod("builder");
			final Object builder = builderMethod.invoke(null);
			try {
				final Method temperatureMethod = ollamaChatModelBuilderClass.getMethod("temperature", Double.class);
				temperatureMethod.invoke(builder, TEMPERATURE);
			} catch(final Exception e){}
			try {
				final Duration timeout = Duration.ofSeconds(TIMEOUT_SECONDS.longValue());
				final Method timeoutMethod = ollamaChatModelBuilderClass.getMethod("timeout", timeout.getClass());
				timeoutMethod.invoke(builder, timeout);
			} catch(final Exception e){}
			for(final String method : llmType.reqs)
			{
				final String key = "LANGCHAIN4J_"+method.toUpperCase().trim();
				final String value = CMProps.getProp(key);
				if(value == null)
				{
					Log.errOut(key+" not correctly set in INI file. ");
					this.llmModel = Long.valueOf(System.currentTimeMillis());
					return this.llmModel;
				}
				final Method baseUrlMethod = ollamaChatModelBuilderClass.getMethod(method, String.class);
				baseUrlMethod.invoke(builder, value);
			}
			final Map<String,PairList<Method,Object[]>> methodsFound = new HashMap<String,PairList<Method,Object[]>>();
			final Map<String,List<Method>> failedMethodsFound = new HashMap<String,List<Method>>();
			for(final Method m : ollamaChatModelBuilderClass.getMethods())
			{
				if(CMParms.contains(llmType.reqs, m.getName()))
					continue;
				final String key = "LANGCHAIN4J_"+m.getName().toUpperCase().trim();
				final String value = CMProps.getProp(key);
				if((value != null)&&(value.length()>0))
				{
					if(!methodsFound.containsKey(key))
						methodsFound.put(key, new PairArrayList<Method,Object[]>());
					if(!failedMethodsFound.containsKey(key))
						failedMethodsFound.put(key, new ArrayList<Method>());
					final String[] strValues;
					if(m.getParameterCount()>0)
					{
						if(CMStrings.countChars(value, ',')<(m.getParameterCount()-1))
						{
							failedMethodsFound.get(key).add(m);
							continue;
						}
						strValues = CMParms.parseCommas(value,true).toArray(new String[0]);
					}
					else
					if(m.getParameterCount()==1)
						strValues = new String[] {value};
					else
						strValues = new String[0];

					final Object[] values = new Object[strValues.length];
					for(int s = 0; s<strValues.length;s++)
						values[s] = CMParms.castString(strValues[s], m.getParameterTypes()[s]);
					if(CMParms.contains(values, new Object[] {null}))
						failedMethodsFound.get(key).add(m);
					else
						methodsFound.get(key).add(new Pair<Method,Object[]>(m,values));
				}
			}
			for(final String cleanUpKey : methodsFound.keySet())
				if(methodsFound.get(cleanUpKey).size()>0)
					failedMethodsFound.remove(cleanUpKey);
			if(failedMethodsFound.size()>0)
			{
				for(final String failedKey : failedMethodsFound.keySet())
					Log.errOut(failedKey+" not correctly set in INI file. Check argument count and type for casting.");
			}
			else
			for(final String key : methodsFound.keySet())
			{
				if(methodsFound.get(key).size()>0)
				{
					try {
						final Pair<Method,Object[]> p = methodsFound.get(key).get(0);
						p.first.invoke(builder, p.second);
					} catch(final Exception e){
						Log.errOut(key+": "+e.getMessage());
					}
				}
			}
			final Method buildMethod = ollamaChatModelBuilderClass.getMethod("build");
			llmModel = buildMethod.invoke(builder);
		} catch (final ClassNotFoundException e) {
			Log.errOut("LangChain4j classes not found: " + e.getMessage());
			llmModel = Long.valueOf(System.currentTimeMillis());
		} catch (final NoSuchMethodException e) {
			Log.errOut("Method not found: " + e.getMessage());
			llmModel = Long.valueOf(System.currentTimeMillis());
		}
		catch(final Exception e)
		{
			Log.errOut(e);
			llmModel = Long.valueOf(System.currentTimeMillis());
		}
		this.llmModel = llmModel;
		return llmModel;
	}

	@Override
	public boolean isLLMInstalled()
	{
		final Object llmModel = initializeLLMSession();
		return !(llmModel instanceof Long);
	}

	private Object buildCMRagRetreiver() throws Exception
	{
		try
		{
			Object retriever = Resources.getResource("LANGCHAIN4J_ARCHON_RETRIEVER");
			if(retriever != null)
				return retriever;
			final String url = CMProps.getProp("LANGCHAIN4J_BASEURL");
			if(url == null)
			{
				Log.warnOut("LANGCHAIN4J_BASEURL not set in INI file. LLM Archon disabled.");
				return null;
			}
			//final Class<?> fileSystemDocumentLoaderClass = llmClassLoader.loadClass("dev.langchain4j.data.document.loader.FileSystemDocumentLoader");
			//final Method loadDocumentsRecursivelyMethod = fileSystemDocumentLoaderClass.getMethod("loadDocumentsRecursively", Path.class);
			//final Method loadDocumentMethod = fileSystemDocumentLoaderClass.getMethod("loadDocument", Path.class);
			//@SuppressWarnings("unchecked")
			//final List<Object> documents = (List<Object>) loadDocumentsRecursivelyMethod.invoke(fileSystemDocumentLoaderClass, new File("resources"+File.separator+"help").toPath());
			//allDocuments.addAll(documents);
			//final Class<?> documentClass = llmClassLoader.loadClass("dev.langchain4j.data.document.Document");
			//final Method fromMethod = documentClass.getMethod("from", String.class);  // Or use the overload with Metadata if needed

			final List<Object> allDocuments = new ArrayList<Object>();
			/**
				LLM_ADMIN_RAGS=\
					/guides/ArchonGuide.html,\
					/guides/Features.html,\
					/guides/GameBuildersGuide.html
			for(final String ragF : CMProps.getListFileStringList(ListFile.LLM_ADMIN_RAGS))
			{
				final CMFile F = new CMFile(ragF,null);
				if(F.exists())
					allDocuments.add(fromMethod.invoke(documentClass, F.textUnformatted().toString()));
			}
			 */

			final Class<?> documentSplittersClass = llmClassLoader.loadClass("dev.langchain4j.data.document.splitter.DocumentSplitters");
			final Method recursiveSplitterMethod = documentSplittersClass.getMethod("recursive", int.class, int.class);
			final Object splitter = recursiveSplitterMethod.invoke(documentSplittersClass, Integer.valueOf(500), Integer.valueOf(100));

			final Method splitAllMethod = splitter.getClass().getMethod("splitAll", List.class);
			@SuppressWarnings("unchecked")
			final List<Object> segments = (List<Object>) splitAllMethod.invoke(splitter, allDocuments);

			final Class<?> ollamaEmbeddingModelClass = llmClassLoader.loadClass("dev.langchain4j.model.ollama.OllamaEmbeddingModel");
			final Class<?> ollamaEmbeddingModelBuilderClass = llmClassLoader.loadClass("dev.langchain4j.model.ollama.OllamaEmbeddingModel$OllamaEmbeddingModelBuilder");
			final Method builderMethod = ollamaEmbeddingModelClass.getMethod("builder");
			final Object builder = builderMethod.invoke(ollamaEmbeddingModelClass);

			final Method baseUrlMethod = ollamaEmbeddingModelBuilderClass.getMethod("baseUrl", String.class);
			baseUrlMethod.invoke(builder, url);

			final Method modelNameMethod = ollamaEmbeddingModelBuilderClass.getMethod("modelName", String.class);
			modelNameMethod.invoke(builder, "nomic-embed-text:latest");

			final Method buildMethod = ollamaEmbeddingModelBuilderClass.getMethod("build");
			final Object embeddingModel = buildMethod.invoke(builder);

			final Class<?> responseClass = llmClassLoader.loadClass("dev.langchain4j.model.output.Response");
			final Method embedAllMethod = embeddingModel.getClass().getMethod("embedAll", List.class);
			final Object embedResponse = embedAllMethod.invoke(embeddingModel, segments);
			final Method contentMethod = responseClass.getMethod("content");
		    @SuppressWarnings("rawtypes")
			final List embeddings = (List)contentMethod.invoke(embedResponse);

			final Class<?> inMemoryEmbeddingStoreClass = llmClassLoader.loadClass("dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore");
			final Constructor<?> storeConstructor = inMemoryEmbeddingStoreClass.getConstructor();
			final Object embeddingStore = storeConstructor.newInstance();

			final Method addAllMethod = inMemoryEmbeddingStoreClass.getMethod("addAll", List.class, List.class);
			addAllMethod.invoke(embeddingStore, embeddings, segments);

			final Class<?> embeddingStoreRetrieverClass = llmClassLoader.loadClass("dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever");
			final Method retrieverBuilderMethod = embeddingStoreRetrieverClass.getMethod("builder");
			final Object retrieverBuilder = retrieverBuilderMethod.invoke(embeddingStoreRetrieverClass);
			final Method retrieverStoreMethod = retrieverBuilder.getClass().getMethod("embeddingStore",llmClassLoader.loadClass("dev.langchain4j.store.embedding.EmbeddingStore"));
			retrieverStoreMethod.invoke(retrieverBuilder,embeddingStore);
			final Method retrieverModelMethod = retrieverBuilder.getClass().getMethod("embeddingModel",llmClassLoader.loadClass("dev.langchain4j.model.embedding.EmbeddingModel"));
			retrieverModelMethod.invoke(retrieverBuilder,embeddingModel);
			final Method retrieverBuildMethod = retrieverBuilder.getClass().getMethod("build");
			retriever = retrieverBuildMethod.invoke(retrieverBuilder);
			Resources.submitResource("LANGCHAIN4J_ARCHON_RETRIEVER", retriever);
			return retriever;
		}
		catch(final Exception e)
		{
			Log.errOut(e);
		}
		return null;
	}

	@Override
	public LLMSession createArchonLLMSession()
	{
		return createLLMSession("", Integer.valueOf(100), true);
	}

	@Override
	public LLMSession createLLMSession(final String personality, final Integer maxMsgs)
	{
		return createLLMSession(personality, maxMsgs, false);
	}

	private LLMSession createLLMSession(final String personality, final Integer maxMsgs, final boolean archon)
	{
		final Object llmModel = initializeLLMSession();
		if(llmModel instanceof Long)
			return null;
		try
		{
			final Integer MAX_MSGS = (maxMsgs!=null)?maxMsgs:Integer.valueOf(10);
			final Class<?> messageWindowChatMemoryClass = llmClassLoader.loadClass("dev.langchain4j.memory.chat.MessageWindowChatMemory");
			final Method chatMemBuilderMethod = messageWindowChatMemoryClass.getMethod("builder");
			final Object memBuilder = chatMemBuilderMethod.invoke(null);
			final Method maxMessagesMethod = memBuilder.getClass().getMethod("maxMessages", Integer.class);
			final Method chatMemBuildMethod = memBuilder.getClass().getMethod("build");
			maxMessagesMethod.invoke(memBuilder, MAX_MSGS);
			final Object memory = chatMemBuildMethod.invoke(memBuilder);
			final Class<?> aiServicesClass = llmClassLoader.loadClass("dev.langchain4j.service.AiServices");
			final Method aiBuilderMethod = aiServicesClass.getMethod("builder", Class.class);
			final Object aiBuilder = aiBuilderMethod.invoke(null, LLMSession.class);
			final Class<?> aiBuilderClass = aiBuilder.getClass();
			final Class<?> chatModelClass = llmClassLoader.loadClass("dev.langchain4j.model.chat.ChatModel");
			final Class<?> chatMemoryClass = llmClassLoader.loadClass("dev.langchain4j.memory.ChatMemory");
			final Method chatModelMethod = aiBuilderClass.getMethod("chatModel", chatModelClass);
			chatModelMethod.setAccessible(true);
			final Method chatMemoryMethod = aiBuilderClass.getMethod("chatMemory", chatMemoryClass);
			chatMemoryMethod.setAccessible(true);
			final Method aiBuildMethod = aiBuilderClass.getMethod("build");
			aiBuildMethod.setAccessible(true);
			chatModelMethod.invoke(aiBuilder, llmModel);
			chatMemoryMethod.invoke(aiBuilder, memory);
			if(archon)
			{
				final LLM llmType = (LLM)CMath.s_valueOf(LLM.class, CMProps.getVar(Str.LANGCHAIN4J_LLM_TYPE));
				if(llmType == LLM.OLLAMA)
				{
					final Object retriever = this.buildCMRagRetreiver();
					if(retriever != null)
					{
						final Class<?> retrieverClass = llmClassLoader.loadClass("dev.langchain4j.rag.content.retriever.ContentRetriever");
						final Method retreiverMethod = aiBuilderClass.getMethod("contentRetriever", retrieverClass);
						retreiverMethod.setAccessible(true);
						retreiverMethod.invoke(aiBuilder, retriever);
					}
				}
			}
			final LLMSession ss = (LLMSession)aiBuildMethod.invoke(aiBuilder);
			final Class<?> chatMessageClass = llmClassLoader.loadClass("dev.langchain4j.data.message.ChatMessage");
			final Method messagesMethod = chatMemoryClass.getMethod("messages");
			final Method clearMethod = chatMemoryClass.getMethod("clear");
			final Method addMethod = chatMemoryClass.getMethod("add", chatMessageClass);
			return new LLMSession()
			{
				final LLMSession ai = ss;
				private volatile String p = personality;
				private final Object chatMemory = memory;
				private final int maxMsgs = MAX_MSGS.intValue();
				@Override
				public String chat(final String msg)
				{
					try
					{
						final List<?> currentMessages = (List<?>)messagesMethod.invoke(chatMemory);
						if (!currentMessages.isEmpty()
						&&(currentMessages.size() >= maxMsgs - 1))
						{
							final Object systemMsg = currentMessages.get(0);
							clearMethod.invoke(chatMemory);
							addMethod.invoke(chatMemory, systemMsg);
							for (int i = 3; i < currentMessages.size(); i++)
								addMethod.invoke(chatMemory, currentMessages.get(i));
						}
						final String finalMsg = (p==null)?msg:(p + " " + msg);
						p=null;
						String respStr;
						try
						{
							respStr = ai.chat(finalMsg);
						}
						catch(final Exception e)
						{
							respStr = "LLM Error: "+e.getMessage();
						}
						final StringBuffer resp = new StringBuffer(respStr);
						CMStrings.dikufyLineEndings(resp);
						CMStrings.normalizeCharacters(resp);
						if((resp.length()>2)
						&&(resp.charAt(0)=='\"')
						&&(resp.indexOf("\"",1)==resp.length()-1))
						{
							resp.deleteCharAt(resp.length()-1);
							resp.deleteCharAt(0);
						}
						return resp.toString();
					}
					catch(final Exception e)
					{
						Log.errOut(e);
						return L("Something went wrong. Ask the admins to check the log.");
					}
				}

			};
		}
		catch (final ClassNotFoundException e)
		{
			Log.errOut("LangChain4j classes not found: " + e.getMessage());
			this.llmModel = Long.valueOf(System.currentTimeMillis());
			return null;
		}
		catch (final NoSuchMethodException e)
		{
			Log.errOut("Method not found: " + e.getMessage());
			this.llmModel = Long.valueOf(System.currentTimeMillis());
			return null;
		}
		catch(final Exception e)
		{
			Log.errOut(e);
			this.llmModel = Long.valueOf(System.currentTimeMillis());
			return null;
		}
	}
}
