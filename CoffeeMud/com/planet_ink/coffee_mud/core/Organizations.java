package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.regex.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;

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
/** Shared organisation rules for commands, scripts and world content. */
public final class Organizations
{
    private Organizations() {}

    public static long now()
    {
        final String mode=CMProps.instance().getProperty("CLANCLOCK");
        return (mode==null||mode.equalsIgnoreCase("GAME"))?GameTime.now():System.currentTimeMillis();
    }

    public static Clan firstClan(final MOB mob)
    {
        Clan C=CMLib.clans().findRivalrousClan(mob);
        if(C==null && mob.clans().iterator().hasNext()) C=mob.clans().iterator().next().first;
        return C;
    }

    public static int relationCode(final String name)
    {
        for(int i=0;i<Clan.REL_DESCS.length;i++)
            if(Clan.REL_DESCS[i].equalsIgnoreCase(name)) return i;
        return -1;
    }

    public static String clanNames(final MOB mob)
    {
        final List<String> names=new ArrayList<String>();
        for(final Pair<Clan,Integer> p : mob.clans()) names.add(p.first.clanID());
        return CMParms.toListString(names);
    }

    public static String memberValue(final MOB mob, final Clan clan, final String field)
    {
        if(mob==null||clan==null) return "";
        final Pair<Clan,Integer> role=mob.getClanRole(clan.clanID());
        if(role==null) return "";
        final ClanPosition pos=clan.getGovernment().getPosition(role.second.toString());
        if(field.equalsIgnoreCase("POSITION")) return pos==null?"":pos.getID();
        if(field.equalsIgnoreCase("RANKNAME")) return pos==null?"":pos.getName();
        for(final Clan.MemberRecord r : clan.getMemberList())
        {
            if(r.name.equalsIgnoreCase(mob.Name()))
            {
                if(field.equalsIgnoreCase("JOINDATE")) return ""+r.joinDate;
                if(field.equalsIgnoreCase("DUESOWED")) return ""+r.dues;
                if(field.equalsIgnoreCase("DONATEDGOLD")) return ""+r.donatedGold;
                if(field.equalsIgnoreCase("DONATEDXP")) return ""+r.donatedXP;
            }
        }
        return "0";
    }

    public static boolean rankTest(final MOB mob, final String clanName, final String op, final String position)
    {
        final Clan clan=CMLib.clans().findClan(clanName);
        if(clan==null||mob==null) return false;
        final Pair<Clan,Integer> role=mob.getClanRole(clan.clanID());
        final ClanPosition want=clan.getGovernment().getPosition(position);
        final ClanPosition have=role==null?null:clan.getGovernment().getPosition(role.second.toString());
        if(have==null||want==null) return false;
        // A lower position rank is more senior; comparisons describe seniority.
        final int c=Integer.compare(want.getRank(),have.getRank());
        if(op.equals(">=")) return c>=0;
        if(op.equals("<=")) return c<=0;
        if(op.equals(">")) return c>0;
        if(op.equals("<")) return c<0;
        if(op.equals("!=")) return c!=0;
        return c==0;
    }

    private static final Pattern RANK=Pattern.compile("^(.+?)(>=|<=|==|!=|>|<|=)([^=<>!]+)$");
    public static boolean rankMask(final MOB mob, final String condition)
    {
        final Matcher m=RANK.matcher(condition.trim());
        return m.matches()&&rankTest(mob,m.group(1).trim(),m.group(2),m.group(3).trim());
    }

    public static boolean rulerMatches(final Environmental object, final String name)
    {
        final Area A=CMLib.map().areaLocation(object);
        final LegalBehavior B=A==null?null:CMLib.law().getLegalBehavior(A);
        final String ruler=B==null?"":B.rulingOrganization();
        if(name.equals("*")) return ruler.length()>0;
        final Clan C=CMLib.clans().findClan(name);
        return C!=null&&C.clanID().equalsIgnoreCase(ruler);
    }
    public static String setting(final Clan C, final String key, final String fallback)
    {
        String value=C.getStat(key);
        if(value.length()==0) value=C.getGovernment().getStat(key);
        return value.length()==0?fallback:value;
    }

    public static boolean npcRun(final Clan C)
    {
        return C.isSet(Clan.ClanFlag.NPCRUN)||setting(C,"NPCFLAGS","").toUpperCase().contains("NPCRUN");
    }

    public static List<MOB> npcMembers(final Clan C)
    {
        final List<MOB> result=new ArrayList<MOB>();
        for(final Enumeration<MOB> e=CMLib.map().worldMobs();e.hasMoreElements();)
        {
            final MOB M=e.nextElement();
            if(M!=null&&M.isMonster()&&!M.amDestroyed()&&M.getClanRole(C.clanID())!=null) result.add(M);
        }
        return result;
    }

    public static int roleID(final Clan C, final String name)
    {
        final ClanPosition P=C==null?null:C.getGovernment().getPosition(name);
        return P==null?-1:P.getRoleID();
    }

    private static final ThreadLocal<Set<MOB>> checking=new ThreadLocal<Set<MOB>>() {
        @Override protected Set<MOB> initialValue() { return new HashSet<MOB>(); }
    };
    public static void changed(final MOB M)
    {
        if(M==null||M.location()==null||!CMLib.flags().isInTheGame(M,true)||!checking.get().add(M)) return;
        try
        {
            final com.planet_ink.coffee_mud.Abilities.interfaces.Ability awards=M.fetchEffect("AutoAwards");
            if(awards!=null) awards.setStat("FORCETICK","true");
            final List<Clan> clans=new ArrayList<Clan>();
            for(final Pair<Clan,Integer> p : M.clans()) clans.add(p.first);
            for(final Clan C : clans) merit(M,C);
            M.recoverPhyStats(); M.recoverCharStats(); M.recoverMaxState();
        }
        finally { checking.get().remove(M); }
    }

    public static void merit(final MOB M, final Clan C)
    {
        final Clan.AutoPromoteFlag mode=C.getGovernment().getAutoPromoteBy();
        if(mode!=Clan.AutoPromoteFlag.MERIT&&mode!=Clan.AutoPromoteFlag.MERIT_DEMOTE) return;
        final Pair<Clan,Integer> role=M.getClanRole(C.clanID());
        if(role==null) return;
        if(role.second.intValue()==C.getGovernment().getAutoRole()&&C.getGovernment().getAutoRole()!=C.getGovernment().getAcceptPos()) return;
        final ClanPosition current=C.getGovernment().getPosition(role.second.toString());
        if(current==null) return;
        ClanPosition best=null;
        final List<Clan.MemberRecord> members=CMLib.database().DBReadClanMembers(C.clanID());
        final List<MOB> npcs=npcMembers(C);
        for(final ClanPosition P : C.getGovernment().getPositions())
        {
            if((P.getRoleID()==C.getGovernment().getAutoRole())&&(C.getGovernment().getAutoRole()!=C.getGovernment().getAcceptPos())) continue;
            if(mode==Clan.AutoPromoteFlag.MERIT&&P.getRank()>current.getRank()) continue;
            if(P.getRank()<current.getRank()&&!M.isMonster()&&C.getDues()>0&&C.getCurrentClanDuesOwed(M)>C.getDues()*(1+Math.max(0,CMath.s_int(setting(C,"DUESGRACE","0"))))) continue;
            if(!CMLib.masking().maskCheck(P.getInnerMaskStr(),M,true)) continue;
            int seats=0;
            for(final Clan.MemberRecord r : members) if(r.role==P.getRoleID()&&(M.isMonster()||!r.name.equalsIgnoreCase(M.Name()))) seats++;
            // NPC names are not identities: exclude only this exact candidate.
            for(final MOB npc:npcs) if(npc!=M&&npc.getClanRole(C.clanID()).second.intValue()==P.getRoleID()) seats++;
            final int max=C.getMostInRole(P.getRoleID());
            if(max>0&&seats>=max) continue;
            if(best==null||P.getRank()<best.getRank()) best=P;
        }
        if(best!=null&&best.getRoleID()!=current.getRoleID()) C.addMember(M,best.getRoleID());
    }

    public static String rankDetails(final MOB M, final Clan C)
    {
        if(M==null||M.getClanRole(C.clanID())==null) return "";
        final ClanPosition current=C.getGovernment().getPosition(M.getClanRole(C.clanID()).second.toString());
        ClanPosition next=null;
        for(final ClanPosition P : C.getGovernment().getPositions())
            if(P.getRank()<current.getRank()&&(next==null||P.getRank()>next.getRank())) next=P;
        String result=next==null?"":"Next rank: "+next.getName()+", requires: "+CMLib.masking().maskDesc(next.getInnerMaskStr());
        final String standing=setting(C,"STANDINGFACTION","");
        final Faction F=CMLib.factions().getFaction(standing);
        if(F!=null)
        {
            final Faction.FRange r=F.fetchRange(M.fetchFaction(F.factionID()));
            if(r!=null) result+="\n\rStanding: "+r.name();
        }
        return result;
    }

    public static void announce(final Clan C, final String message)
    {
        final String raw=CMProps.instance().getProperty("CLANNEWS");
        final String mode=raw==null?"INFO":raw.toUpperCase();
        if(mode.equals("OFF")) return;
        if(mode.equals("MEMBERS"))
        {
            for(final Enumeration<MOB> e=CMLib.players().players();e.hasMoreElements();)
            {
                final MOB M=e.nextElement();
                if(M.getClanRole(C.clanID())!=null&&M.session()!=null) tellMember(M,message);
            }
        }
        else
        if(CMLib.channels().getChannelIndex("INFO")>=0)
            CMLib.commands().postChannel("INFO",new PairVector<Clan,Integer>(),message,true,null);
    }

    public static long periodHours(final Clan C)
    {
        final TimeClock clock=CMLib.time().globalClock();
        final String period=setting(C,"DUESPERIOD","YEAR").toUpperCase();
        final long day=clock.getHoursInDay();
        if(period.equals("DAY")) return day;
        if(period.equals("WEEK")) return day*Math.max(1,clock.getWeekNames().length);
        if(period.equals("MONTH")) return day*clock.getDaysInMonth();
        return day*clock.getDaysInYear();
    }

    public static String payDues(final MOB M, final Clan C)
    {
        synchronized(C)
        {
            if(M.getClanRole(C.clanID())==null) return "You are not a member of "+C.name()+".";
            final double owed=C.getCurrentClanDuesOwed(M);
            if(owed<=0) return "Your dues to "+C.name()+" are paid up.";
            final String currency=CMLib.beanCounter().getCurrency(M);
            final double paid=Math.min(owed,CMLib.beanCounter().getTotalAbsoluteValue(M,currency));
            if(paid<=0) return "You have no money to pay your dues.";
            CMLib.beanCounter().subtractMoney(M,currency,paid);
            CMLib.database().DBUpdateClanDonates(C.clanID(),M.Name(),0,0,-paid);
            credit(C,currency,paid);
            C.update();
            return "You pay "+CMLib.beanCounter().nameCurrencyShort(currency,paid)+" in dues to "+C.name()+". Remaining: "+CMLib.beanCounter().nameCurrencyShort(currency,owed-paid)+".";
        }
    }

    /** Saved deadline advances before collection, preventing a second charge after restoring. */
    public static void dues(final Clan C)
    {
        if(C.getDues()<=0) return;
        synchronized(C)
        {
            final long hour=ScriptEvents.clockHours(CMLib.time().globalClock());
            final long period=Math.max(1,periodHours(C));
            long next=CMath.s_long(C.getStat("DUESNEXT"));
            if(next<=0) { C.setStat("DUESNEXT",""+(hour+period)); C.update(); return; }
            if(hour<next) return;
            final long periods=1+(hour-next)/period;
            C.setStat("DUESNEXT",""+(next+periods*period));
            C.update();
            final int grace=Math.max(0,CMath.s_int(setting(C,"DUESGRACE","0")));
            final List<String> steps=CMParms.parseCommas(setting(C,"DUESLATE","WARN"),true);
            for(final Clan.MemberRecord r : C.getMemberList())
            {
                if(!CMLib.players().playerExists(r.name)) continue;
                final MOB M=CMLib.players().getLoadPlayer(r.name);
                if(M==null) continue;
                CMLib.database().DBUpdateClanDonates(C.clanID(),r.name,0,0,periods*C.getDues());
                if(CMath.s_bool(setting(C,"DUESAUTO","TRUE")))
                {
                    tellMember(M,payDues(M,C));
                    payFromBanks(M,C);
                }
                final double owed=C.getCurrentClanDuesOwed(M);
                final int late=(int)Math.ceil(owed/C.getDues())-grace;
                if(late<=0||steps.isEmpty()) continue;
                final String step=steps.get(Math.min(late-1,steps.size()-1)).toUpperCase();
                tellMember(M,"Your dues to "+C.name()+" are overdue ("+step.toLowerCase()+").");
                ScriptEvents.clan("DUESLATE",C.clanID(),M,null,""+owed,step);
                if(step.equals("EXILE")) C.delMember(M);
                else if(step.equals("DEMOTE"))
                {
                    final ClanPosition old=C.getGovernment().getPosition(M.getClanRole(C.clanID()).second.toString());
                    ClanPosition lower=null;
                    for(final ClanPosition P : C.getGovernment().getPositions())
                        if(P.getRank()>old.getRank()&&(lower==null||P.getRank()<lower.getRank())) lower=P;
                    if(lower!=null) C.addMember(M,lower.getRoleID());
                }
            }
        }
    }

    private static final Map<Clan,Long> maintenanceDays=Collections.synchronizedMap(new WeakHashMap<Clan,Long>());
    public static void tick(final Clan C)
    {
        if(!"REAL".equalsIgnoreCase(CMProps.instance().getProperty("CLANCLOCK"))) dues(C);
        final long day=ScriptEvents.clockHours(CMLib.time().globalClock())/Math.max(1,CMLib.time().globalClock().getHoursInDay());
        final Long old=maintenanceDays.put(C,Long.valueOf(day));
        if(old!=null&&old.longValue()==day) return;
        for(final Clan.MemberRecord r : C.getMemberList())
        {
            final MOB M=CMLib.players().getLoadPlayer(r.name);
            if(M!=null) changed(M);
        }
        for(final MOB M : npcMembers(C)) changed(M);
        C.update();
    }

    public static boolean soleProfileVoter(final MOB M, final Clan C, final Clan.Function function)
    {
        if(!"PROFILE".equalsIgnoreCase(CMProps.instance().getProperty("CLANVOTES"))) return false;
        final PlayerAccount account=Party.profileOf(M);
        if(account==null) return false;
        final Clan.Function vote=(function==Clan.Function.ASSIGN)?Clan.Function.VOTE_ASSIGN:Clan.Function.VOTE_OTHER;
        boolean found=false;
        for(final Clan.MemberRecord r : C.getMemberList())
        {
            if(C.getAuthority(r.role,vote)!=Clan.Authority.CAN_DO) continue;
            final MOB voter=CMLib.players().getLoadPlayer(r.name);
            if(voter==null||Party.profileOf(voter)!=account) return false;
            found=true;
        }
        return found;
    }

    public static boolean visible(final Faction F, final MOB M)
    {
        final String mask=F.getTagValue("SHOWMASK");
        return mask.length()==0||CMLib.masking().maskCheck(mask,M,true);
    }

    public static String factionLadder(final Faction F, final MOB M)
    {
        final List<Faction.FRange> ranges=new ArrayList<Faction.FRange>();
        for(final Enumeration<Faction.FRange> e=F.ranges();e.hasMoreElements();) ranges.add(e.nextElement());
        Collections.sort(ranges,new Comparator<Faction.FRange>() {
            @Override public int compare(final Faction.FRange a, final Faction.FRange b) { return Integer.compare(a.low(),b.low()); }
        });
        final int value=M.fetchFaction(F.factionID());
        final StringBuilder text=new StringBuilder(F.name()+"\n\r");
        Faction.FRange next=null;
        for(final Faction.FRange r : ranges)
        {
            text.append(value>=r.low()&&value<=r.high()?" * ":"   ").append(r.name()).append(" (").append(r.low()).append(" to ").append(r.high()).append(")\n\r");
            if(r.low()>value&&next==null) next=r;
        }
        if(next!=null) text.append(next.low()-value).append(" points to ").append(next.name()).append(".\n\r");
        return text.toString();
    }

    public static String partyFactions(final MOB M)
    {
        final List<MOB> party=Party.inOrder(Party.leaderOf(M));
        final Set<String> ids=new TreeSet<String>();
        final StringBuilder text=new StringBuilder(CMStrings.padRight("Faction",24));
        for(final MOB member : party)
        {
            text.append(" | ").append(CMStrings.padRight(member.Name(),18));
            for(final Enumeration<String> e=member.factions();e.hasMoreElements();) ids.add(e.nextElement());
        }
        text.append("\n\r");
        for(final String id : ids)
        {
            final Faction F=CMLib.factions().getFaction(id);
            if(F==null||!F.showInFactionsCommand()) continue;
            boolean any=false;
            for(final MOB member : party) if(visible(F,member)&&member.hasFaction(id)) any=true;
            if(!any) continue;
            text.append(CMStrings.padRight(F.name(),24));
            for(final MOB member : party)
            {
                final Faction.FRange r=F.fetchRange(member.fetchFaction(id));
                text.append(" | ").append(CMStrings.padRight(visible(F,member)&&r!=null?r.name():"?",18));
            }
            text.append("\n\r");
        }
        return text.toString();
    }
    public static String clanCode(final Clan C) { return C.clanID().toUpperCase().replaceAll("[^A-Z0-9_]","_"); }

    public static Faction clanFaction(final Clan C, final boolean persistent)
    {
        final String code=clanCode(C), id="CLAN_"+code;
        Faction F=CMLib.factions().getFaction(id);
        if(F==null)
        {
            StringBuffer definition=Resources.getFileResource(CMLib.factions().makeFactionFilename("clanreaction_"+code.toLowerCase()+".ini"),false);
            if(definition==null||definition.length()==0) definition=Resources.getFileResource("examples/clanreaction.ini",false);
            if(definition==null||definition.length()==0) return null;
            String source=definition.toString();
            source=source.replace("<NAME>",C.name()).replace("<CODE>",code).replace("<FACTIONID>",id);
            F=(Faction)CMClass.getCommon("DefaultFaction");
            F.initializeFaction(new StringBuffer(source),id);
            F.setInternalFlags(Faction.IFLAG_IGNOREAUTO|Faction.IFLAG_CUSTOMTICK|(persistent?0:Faction.IFLAG_NEVERSAVE));
            CMLib.factions().addFaction(F);
        }
        if(persistent)
        {
            F.setInternalFlags(F.getInternalFlags()&~Faction.IFLAG_NEVERSAVE);
            CMLib.factions().resaveFaction(F);
        }
        refreshRelations(C);
        return F;
    }

    public static void refreshRelations(final Clan C)
    {
        final Faction F=CMLib.factions().getFaction("CLAN_"+clanCode(C));
        if(F==null) return;
        for(final Enumeration<Clan> e=CMLib.clans().clans();e.hasMoreElements();)
        {
            final Clan other=e.nextElement();
            if(other==C||!npcRun(other)) continue;
            final int relation=CMLib.clans().getCommonClanRelations(C,other);
            final Faction target=CMLib.factions().getFaction("CLAN_"+clanCode(other));
            final String id=target==null?"CLAN_"+clanCode(other):target.factionID();
            F.delRelation("CLAN_"+clanCode(other));
            F.delRelation(id);
            if(relation==Clan.REL_ALLY) F.addRelation(id,0.5);
            else if(relation==Clan.REL_WAR) F.addRelation(id,-0.5);
        }
    }

    public static String voterKey(final String name)
    {
        if(!"PROFILE".equalsIgnoreCase(CMProps.instance().getProperty("CLANVOTES"))) return name;
        final MOB M=CMLib.players().getLoadPlayer(name);
        final PlayerAccount A=M==null?null:Party.profileOf(M);
        return A==null?name:"PROFILE:"+A.getAccountName();
    }

    public static String partyClans(final MOB M)
    {
        final StringBuilder text=new StringBuilder();
        for(final MOB member : Party.inOrder(Party.leaderOf(M)))
        {
            text.append(member.Name()).append(": ");
            boolean found=false;
            for(final Pair<Clan,Integer> p : member.clans())
            {
                if(found) text.append(", ");
                text.append(p.first.name()).append(" (").append(p.first.getRoleName(p.second.intValue(),true,false)).append(")");
                found=true;
            }
            if(!found) text.append("no clans");
            text.append("\n\r");
        }
        return text.toString();
    }

    public static void tellMember(final MOB M, final String message)
    {
        M.tell(message);
        if(Party.isBackground(M)&&!Party.isWindowed(M))
        {
            final Session terminal=Party.terminalOf(M);
            if(terminal!=null&&terminal.mob()!=null&&terminal.mob()!=M) terminal.mob().tell("["+M.Name()+"] "+message);
        }
    }

    public static long memberActive(final Clan C, final String name, final long realDate)
    {
        if("REAL".equalsIgnoreCase(CMProps.instance().getProperty("CLANCLOCK"))) return realDate;
        final Map<String,String> dates=CMParms.parseEQParms(C.getStat("MEMBERACTIVITY"));
        final String key=name.toUpperCase();
        MOB M=CMLib.players().getPlayer(name);
        if(M==null) for(final MOB npc : npcMembers(C)) if(npc.Name().equalsIgnoreCase(name)) { M=npc; break; }
        long time=dates.containsKey(key)?CMath.s_long(dates.get(key)):now();
        if(M!=null&&CMLib.flags().isInTheGame(M,true)) time=now();
        dates.put(key,""+time);
        final StringBuilder encoded=new StringBuilder();
        for(final Map.Entry<String,String> e : new TreeMap<String,String>(dates).entrySet()) encoded.append(e.getKey()).append('=').append(e.getValue()).append(' ');
        C.setStat("MEMBERACTIVITY",encoded.toString());
        return time;
    }

    private static void credit(final Clan C, final String currency, final double amount)
    {
        final Pair<String,String> bank=C.getPreferredBanking();
        if(bank!=null&&bank.second.equals(currency)) CMLib.beanCounter().modifyBankGold(bank.first,C.clanID(),"Membership dues",currency,amount);
        else C.setStat("TREASURY",""+(CMath.s_double(C.getStat("TREASURY"))+amount));
    }

    private static void payFromBanks(final MOB M, final Clan C)
    {
        double owed=C.getCurrentClanDuesOwed(M);
        if(owed<=0) return;
        final String currency=CMLib.beanCounter().getCurrency(M);
        for(final String chain : CMLib.beanCounter().getBankAccountChains(M.Name()))
        {
            final Pair<String,Double> account=CMLib.beanCounter().getBankBalance(chain,M.Name(),currency);
            final double amount=account==null?0:Math.min(owed,Math.max(0,account.second.doubleValue()-1));
            if(amount<=0) continue;
            if(CMLib.beanCounter().modifyBankGold(chain,M.Name(),"Membership dues to "+C.name(),currency,-amount))
            {
                CMLib.database().DBUpdateClanDonates(C.clanID(),M.Name(),0,0,-amount);
                credit(C,currency,amount); owed-=amount; C.update();
                tellMember(M,"Your bank pays "+CMLib.beanCounter().nameCurrencyShort(currency,amount)+" in dues to "+C.name()+".");
            }
            if(owed<=0) break;
        }
    }

    /** Masks nested in quoted property parameters may quote their own arguments. */
    public static String parameterMask(final String text)
    {
        return text.replace("\\\"","\"").replaceAll("(^|\\s)'([^']*)'(?=\\s|$)","$1\"$2\"");
    }
}
