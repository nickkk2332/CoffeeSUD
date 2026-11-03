package com.planet_ink.coffee_mud.Abilities.Common;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;

/*
   Copyright 2003-2025 Bo Zimmerman

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
public class Farming extends GatheringSkill
{
	@Override
	public String ID()
	{
		return "Farming";
	}

	private final static String localizedName = CMLib.lang().L("Farming");

	@Override
	public String name()
	{
		return localizedName;
	}

	private static final String[] triggerStrings = I(new String[] { "PLANT", "FARM", "FARMING" });

	@Override
	public String[] triggerStrings()
	{
		return triggerStrings;
	}

	@Override
	public int classificationCode()
	{
		return Ability.ACODE_COMMON_SKILL | Ability.DOMAIN_GATHERINGSKILL;
	}

	@Override
	protected boolean allowedWhileMounted()
	{
		return false;
	}

	@Override
	public String supportedResourceString()
	{
		return "VEGETATION|COTTON|HEMP|WOODEN";
	}

	protected Item		found			= null;
	protected Room		room			= null;
	protected String	foundShortName	= "";
	protected int		goodticks		= 0;
	protected int        daylight        = 0; // CR16: a long crop's counted ticks
	protected String    cropRsc            = ""; // CR16: what is planted, for crops.txt
	protected String    cropSub            = "";
	protected String    seedRules        = ""; // CR16: the seed's own Prop_CropRules

	/**
	 * CR16: the crop's rules here: crops.txt, then Prop_CropRules on the
	 * area, the room and the seed.
	 * @param R the room
	 * @return its rules
	 */
	protected CropRules.Rules cropRules(final Room R)
	{
		return CropRules.of(R, cropRsc, cropSub, seedRules);
	}

	public Farming()
	{
		super();
		displayText=L("You are planting...");
		verb=L("planting");
	}

	protected int getDuration(final MOB mob, final int level)
	{
		return getDuration(45,mob,level,15);
	}

	@Override
	protected int baseYield()
	{
		return 1;
	}

	protected String seedWord()
	{
		return L("seed");
	}

	@Override
	public void executeMsg(final Environmental host, final CMMsg msg)
	{
		super.executeMsg(host, msg);
		if((msg.target()==affected)
		&&(affected instanceof Room)
		&&((msg.targetMinor()==CMMsg.TYP_LOOK)||(msg.targetMinor()==CMMsg.TYP_EXAMINE)))
		{
			final Room R=(Room)affected;
			if(R!=null)
			{
				final double age = (tickDown == 0) ? 0 : CMath.div(tickDown, tickDown+tickUp);
				String msgStr;
				if(age < .25)
					msgStr=L("Mature @x1 are growing here.",foundShortName);
				else
				if(age < .5)
					msgStr=L("@x1 are growing here.",foundShortName);
				else
				if(age < .75)
					msgStr=L("Young @x1 are growing here.",foundShortName);
				else
					msgStr=CMStrings.capitalizeFirstLetter(CMLib.english().makePlural(seedWord()))+L(" of @x1",foundShortName);
				msg.addTrailerMsg(CMClass.getMsg(msg.source(),null,null,CMMsg.MSG_OK_VISUAL,CMMsg.NO_EFFECT,CMMsg.NO_EFFECT,
						msgStr));
			}
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if((affected instanceof Room))
		{
			final Room R=(Room)affected;
			if(R!=null)
			{
				final CropRules.Rules crop=cropRules(R); // CR16: a long crop's nights do not count against it
				if(crop.growHours>0)
				{
					if((!CropRules.isNight(R))||((crop.needSun!=null)&&(!crop.needSun.booleanValue())))
					{
						daylight++;
						if(canGrowHere(null,R,true))
							goodticks++;
					}
				}
				else
				if(canGrowHere(null,R,true))
					goodticks++;
				final MOB mob=invoker();
				if(tickUp==6)
				{
					if((found==null)
					||(mob==null)
					||(mob.location()==null))
					{
						commonTelL(mob,"Your @x1 crop has failed.\n\r",foundShortName);
						unInvoke();
					}
				}
				else
				if((crop.growHours>0)?((daylight > 10)&&(goodticks < (daylight/2))):((tickUp > 10)&&(goodticks < (tickUp/2))))
				{
					found=null;
					commonTelL(mob,"Your @x1 crop has failed due to lack of sunlight.\n\r",foundShortName);
					unInvoke();
				}
			}
		}
		else
			goodticks=0;
		return super.tick(ticking,tickID);
	}

	protected boolean isCompost(final Item I)
	{
		return ((I!=null) &&(I.rawSecretIdentity().equals("compost")));
	}

	protected int deCompost(final Item I, int doubleRemain)
	{
		int amount=0;
		if(I.phyStats().weight()<=doubleRemain)
		{
			amount+=I.phyStats().weight();
			doubleRemain-=I.phyStats().weight();
			I.destroy();
		}
		else
		{
			if(I.basePhyStats().weight()<=doubleRemain)
				I.destroy();
			else
				I.basePhyStats().setWeight(I.basePhyStats().weight()-doubleRemain);
			amount+=doubleRemain;
			doubleRemain=0;
		}
		return amount;
	}

	@Override
	public void unInvoke()
	{
		final boolean isaborted=aborted;
		final Environmental aff=affected;
		if(canBeUninvoked())
		{
			if((affected!=null)&&(affected==room))
			{
				if((found!=null)&&(!isaborted))
				{
					int amount=CropRules.yieldRoll(cropRules(room),CMLib.dice().roll(1,7,0))*(baseYield()+abilityCode()); // CR16: YIELD=
					final int origAmount = amount;
					int doubleRemain = amount * 10;
					for(final Enumeration<Item> i=room.items();i.hasMoreElements();)
					{
						final Item I=i.nextElement();
						if((I instanceof PackagedItems)
						&&(((PackagedItems)I).areAllItemsTheSame()))
						{
							Item I2=((PackagedItems)I).peekFirstItem();
							while(isCompost(I2))
							{
								final int amt=deCompost(I2,doubleRemain);
								if(!I2.amDestroyed())
									((PackagedItems)I).setNumberOfItemsInPackage(((PackagedItems)I).numberOfItemsInPackage()-1);
								if(amt != 0)
								{
									amount += amt * origAmount;
									doubleRemain -=amt;
								}
								I2.destroy();
								if(((PackagedItems)I).numberOfItemsInPackage()==0)
								{
									I.destroy();
									break;
								}
								I2=((PackagedItems)I).peekFirstItem();
							}
							I2.destroy();
						}
						else
						if(isCompost(I))
						{
							final int amt=deCompost(I,doubleRemain);
							if(amt != 0)
							{
								amount += amt * origAmount;
								doubleRemain -=amt;
							}
						}
						if(doubleRemain <= 0)
							break;
					}
					String s="s";
					if(amount==1)
						s="";
					room.showHappens(CMMsg.MSG_OK_VISUAL,L("@x1 pound@x2 of @x3 have grown here.",""+amount,s,foundShortName));
					for(int i=0;i<amount;i++)
					{
						final Item newFound=(Item)found.copyOf();
						if(!dropAWinner(null,room,newFound))
							break;
					}
				}
			}
		}
		super.unInvoke();
		if((canBeUninvoked)
		&&(aff!=null)
		&&(aff instanceof MOB)
		&&(aff!=room)
		&&(!isaborted)
		&&(room!=null))
		{
			final Farming F=((Farming)copyOf());
			F.unInvoked=false;
			F.tickUp=0;
			final int growTicks=CropRules.growTicks(cropRules(room),50); // CR16: GROWHOURS=
			F.daylight=0;
			F.tickDown=growTicks;
			F.startTickDown(invoker,room,growTicks);
		}
	}

	public boolean isPotentialCrop(final Room R, final int code)
	{
		if(R==null)
			return false;
		if(CropRules.domainNamed(R,cropRules(R))) // CR16: a crop whose DOMAINS name this room
			return true;
		if(R.resourceChoices()==null)
			return false;
		for(int i=0;i<R.resourceChoices().size();i++)
		{
			if(R.resourceChoices().get(i).intValue()==code)
				return true;
		}
		if(R.myResource()==RawMaterial.RESOURCE_DIRT)
			return true;
		return false;
	}

	protected boolean plantableResource(final int rsc)
	{
		if((rsc==RawMaterial.RESOURCE_MUSHROOMS)
		||(rsc==RawMaterial.RESOURCE_FUNGUS))
			return false;
		return ((rsc&RawMaterial.MATERIAL_MASK)==RawMaterial.MATERIAL_VEGETATION)
				||(rsc==RawMaterial.RESOURCE_COTTON)
				||(rsc==RawMaterial.RESOURCE_HEMP)
				||((rsc&RawMaterial.MATERIAL_MASK)==RawMaterial.MATERIAL_WOODEN);
	}

	protected boolean[] plantable(final MOB mob, final Item I2)
	{
		if((I2!=null)
		&&(I2 instanceof RawMaterial)
		&&(I2.container()==null)
		&&(plantableResource(I2.material())))
		{
			if(I2 instanceof Food)
				return new boolean[] { true };
			if(((RawMaterial)I2).getSubType().equals(RawMaterial.ResourceSubType.SEED.name()))
				return new boolean[] { true };
			return new boolean[] { false, true };
		}
		return new boolean[] { false, false };
	}

	/**
	 * CR16: why the crop will not grow here just now, by its rules, or null.
	 * @param R the room
	 * @return the refusal, or null
	 */
	protected String farmRefusal(final Room R)
	{
		if((R==null)||(R.getArea()==null))
			return L("The land is not suitable for farming here.");
		return CropRules.refusal(R, cropRules(R), L("You need clear sunlight to do your farming.  Check the time and weather."),
			L("The land is not suitable for farming here."), L("The current drought conditions make planting useless."));
	}

	protected boolean canGrowHere(final MOB mob, final Room R, final boolean quiet)
	{
		if(R==null)
			return false;
		final String no=farmRefusal(R); // CR16: by the crop's rules
		if(no!=null)
		{
			if(!quiet)
				commonTelL(mob,no);
			return false;
		}
		return true;
	}

	@Override
	public boolean invoke(final MOB mob, final List<String> commands, final Physical givenTarget, final boolean auto, final int asLevel)
	{
		final Room R=mob.location();
		if(R==null)
			return false;
		if(super.checkStop(mob, commands))
			return true;
		bundling=false;
		if((!auto)
		&&(commands.size()>0)
		&&((commands.get(0)).equalsIgnoreCase("bundle")))
		{
			bundling=true;
			if(super.invoke(mob,commands,givenTarget,auto,asLevel))
				return super.bundle(mob,commands);
			return false;
		}

		verb=L("planting");
		cropRsc=""; // CR16
		cropSub="";
		seedRules="";
		if((!auto) && (!CropRules.anyRows()) && (!canGrowHere(mob, R, false)))
			return false;
		if(R.fetchEffect(ID())!=null)
		{
			commonFaiL(mob,commands,"It looks like a crop is already growing here.");
			return false;
		}
		if(mob.isMonster()
		&&(!auto)
		&&(!CMLib.flags().isAnimalIntelligence(mob))
		&&(commands.size()==0))
		{
			Item mine=null;
			for(int i=0;i<R.numItems();i++)
			{
				final Item I2=R.getItem(i);
				if(plantable(mob,I2)[0])
				{
					mine=I2;
					commands.add(RawMaterial.CODES.NAME(I2.material()));
					break;
				}
			}
			if(mine==null)
			{
				for(int i=0;i<mob.numItems();i++)
				{
					final Item I2=mob.getItem(i);
					if(plantable(mob,I2)[0])
					{
						commands.add(RawMaterial.CODES.NAME(I2.material()));
						mine=(Item)I2.copyOf();
						if(R.findItem(null,R.getContextName(I2))==null)
							R.addItem(mine,ItemPossessor.Expire.Resource);
						break;
					}
				}
			}
			if(mine==null)
			{
				commonFaiL(mob,commands,"You don't have anything you can plant.");
				return false;
			}
		}
		else
		if(commands.size()==0)
		{
			commonTelL(mob,"Grow what?");
			return false;
		}
		int code=-1;
		final String what=CMParms.combine(commands,0).toUpperCase();
		final RawMaterial.CODES codes = RawMaterial.CODES.instance();
		String reqSubType = "";
		for(final int cd : codes.all())
		{
			final String str=codes.name(cd).toUpperCase();
			if((str.equals(what))
			&&(plantableResource(cd)))
			{
				code=cd;
				foundShortName=CMStrings.capitalizeAndLower(str);
				break;
			}
		}
		if(code<0)
		{
			for(final int cd : codes.all())
			{
				final String str=codes.name(cd).toUpperCase();
				if((str.toUpperCase().startsWith(what)||(what.startsWith(str)))
				&&(plantableResource(cd)))
				{
					code=cd;
					foundShortName=CMStrings.capitalizeAndLower(str);
					break;
				}
			}
		}
		if(code < 0)
		{
			final Item I = R.findItem(what);
			if((I instanceof RawMaterial)&&(plantableResource(I.material())))
			{
				final RawMaterial mI = (RawMaterial)I;
				if((mI.getSubType().length()>0)
				&&(!mI.getSubType().equals(RawMaterial.ResourceSubType.SEED.name())))
				{
					code = I.material();
					foundShortName = CMStrings.capitalizeAndLower(mI.getSubType());
					reqSubType = mI.getSubType();
				}
			}
		}
		if(code<0)
		{
			commonFaiL(mob,commands,"You can't seem to grow '@x1'  Is it on the ground?.",CMParms.combine(commands,0));
			return false;
		}

		Item mine=null;
		boolean seedProb = false;
		for(int i=0;i<R.numItems();i++)
		{
			final Item I=R.getItem(i);
			if((I  != null)
			&&(I.material()==code)
			&&((reqSubType.length()==0)
				||((I instanceof RawMaterial)&&(reqSubType.equals(((RawMaterial)I).getSubType())))))
			{
				final boolean[] resp = plantable(mob,I);
				if(resp[0])
				{
					mine = I;
					break;
				}
				else
				if(resp[1])
					seedProb = true;
			}
		}
		if(mine==null)
		{
			if(seedProb)
				commonTelL(mob,"You'll need to have some @x1 Seeds on the ground first.",foundShortName);
			else
				commonFaiL(mob,commands,"You'll need to have some @x1 to seed from on the ground first.",foundShortName);
			return false;
		}
		final Ability seedP=mine.fetchEffect("Prop_CropRules"); // CR16: the seed's own rules
		seedRules=(seedP==null)?"":seedP.text();
		final String mineName=mine.name();
		mine=CMLib.materials().unbundle(mine,-1,null);
		if(mine==null)
		{
			commonFaiL(mob,commands,"'@x1' is not suitable for use as a seed crop.",mineName);
			return false;
		}
		cropRsc=RawMaterial.CODES.NAME(code); // CR16: the crop's own rules
		cropSub=((mine instanceof RawMaterial)&&(!((RawMaterial)mine).getSubType().equalsIgnoreCase(RawMaterial.ResourceSubType.SEED.name())))?((RawMaterial)mine).getSubType():"";
		final String noCrop=farmRefusal(R);
		if((!auto)&&(noCrop!=null))
		{
			commonFaiL(mob,commands,noCrop);
			return false;
		}
		final String notNow=CropRules.seasonRefusal(R,cropRules(R));
		if((!auto)&&(notNow!=null))
		{
			commonFaiL(mob,commands,notNow);
			return false;
		}
		if(!(isPotentialCrop(R,code)))
		{
			commonFaiL(mob,commands,"'@x1' does not seem to be taking root here.",mineName);
			return false;
		}

		found=null;
		if(!super.invoke(mob,commands,givenTarget,auto,asLevel))
			return false;

		if((proficiencyCheck(mob,0,auto))
		&&(isPotentialCrop(R,code)))
		{
			if((mine instanceof RawMaterial)&&(!((RawMaterial)mine).getSubType().equals(RawMaterial.ResourceSubType.SEED.name())))
				found=(Item)CMLib.materials().makeResource(code,Integer.toString(R.domainType()),false,null, ((RawMaterial)mine).getSubType());
			else
				found=(Item)CMLib.materials().makeResource(code,Integer.toString(R.domainType()),false,null, "");
			if((found!=null)
			&&(found.material()==RawMaterial.RESOURCE_HERBS)
			&&(mine.material()==found.material()))
			{
				if(!(mine instanceof RawMaterial) && (((RawMaterial)mine).getSubType().equals(RawMaterial.ResourceSubType.SEED.name())))
				{
					found.setName(mine.name());
					found.setDisplayText(mine.displayText());
					found.setDescription(mine.description());
				}
				found.text();
			}
		}

		mine.destroy();
		final int duration=getDuration(mob,1);
		final String oldFoundName = (found==null)?"":found.Name();
		final CMMsg msg=CMClass.getMsg(mob,found,this,getActivityMessageType(),L("<S-NAME> start(s) planting @x1.",foundShortName));
		verb=L("planting @x1",foundShortName);
		displayText=L("You are planting @x1",foundShortName);
		room=R;
		if(R.okMessage(mob,msg))
		{
			R.send(mob,msg);
			found=(Item)msg.target();
			if((found!=null)&&(!found.Name().equals(oldFoundName)))
				foundShortName=CMLib.english().removeArticleLead(found.Name());
			beneficialAffect(mob,mob,asLevel,duration);
		}
		return true;
	}
}
