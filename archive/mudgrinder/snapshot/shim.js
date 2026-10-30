// Turns the MUDGrinder's form submits, popups and links into navigation
// between the saved pages of this snapshot.  There is no server.
(function () {
  var INDEX = {
"/grinder/loggedin.cmvp": [
{
"params": {},
"file": "grinder/loggedin.html"
},
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/loggedin-1.html"
},
{
"params": {
"CHANNELBACKLOGPAGE": "0"
},
"file": "grinder/loggedin-2.html"
}
],
"/grinder/rmmenu.cmvp": [
{
"params": {
"AREA": "New Area",
"ROOM": "New Area#0"
},
"file": "grinder/rmmenu.html"
}
],
"/grinder/editroom.cmvp": [
{
"params": {
"AREA": "New Area",
"ROOM": "New Area#0"
},
"file": "grinder/editroom.html"
},
{
"params": {
"AREA": "New Area",
"ROOM": "New Area#1"
},
"file": "grinder/editroom-1.html"
}
],
"/grinder/editroomform.cmvp": [
{
"params": {
"AREA": "New Area",
"ROOM": "New Area#0"
},
"file": "grinder/editroomform.html"
},
{
"params": {
"AREA": "New Area",
"ROOM": "New Area#1"
},
"file": "grinder/editroomform-1.html"
},
{
"params": {
"area": "New Area",
"bestfilebrowse": "/",
"room": "New Area#0"
},
"file": "grinder/editroomform-2.html"
}
],
"/grinder/mapworld.cmvp": [
{
"params": {
"AREA": "New Area"
},
"file": "grinder/mapworld.html"
}
],
"/grinder/lnkxmenu.cmvp": [
{
"params": {
"AREA": "New Area",
"ROOM": "New Area#0",
"LINK": "N"
},
"file": "grinder/lnkxmenu.html"
}
],
"/grinder/edxmenu.cmvp": [
{
"params": {
"AREA": "New Area",
"ROOM": "New Area#0",
"LINK": "N",
"LINKSTO": "New Area#1"
},
"file": "grinder/edxmenu.html"
}
],
"/ahelps.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "ahelps.html"
}
],
"/abilities.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "abilities.html"
},
{
"params": {
"ROOM": "ANY",
"ACTION": "MODIFYMOB",
"NAME": "@break[error]endif[error]endif[error]if?CheckReqParm?<MOB=CATALOG-&@<TR>    <TD><FONT COLOR=",
"CATACAT": "@break[error]endif@<TR>\t<TD><FONT COLOR=",
"DISPLAYTEXT": "@break@",
"DESCRIPTION": "@break@",
"IMAGE": "@break[error]else@<TR>\t<TD><FONT COLOR=",
"MISCTEXT": "@break[error]endif@<TR>\t<TD><FONT COLOR=",
"LEVEL": "@break@",
"REJUV": "@break[error]if?!MobData?ISGENERIC@<TR>\t<TD><FONT COLOR=",
"ABILITY": "@break@",
"HEIGHT": "@break@",
"WEIGHT": "@break@",
"SPEED": "@break@",
"ATTACK": "@break@",
"DAMAGE": "@break@",
"ARMOR": "@break@",
"MONEY": "@break@",
"VARMONEY": "@break@",
"TATTOOS": "@break@",
"TAGS": "@break@",
"EXPERTISES": "@break@"
},
"file": "abilities-1.html"
},
{
"params": {
"ROOM": "ANY",
"MOB": "NEW",
"ACTION": "MODIFYMOB",
"CLASSES": "GenMob",
"NAME": "a generic mob",
"DISPLAYTEXT": "A generic mob stands here.",
"LEVEL": "1",
"REJUV": "0",
"RACE": "Human",
"GENDER": "M",
"HEIGHT": "0",
"WEIGHT": "0",
"ABILITY": "11",
"SPEED": "1.0",
"ATTACK": "0",
"DAMAGE": "0",
"ARMOR": "100",
"FACTION1": "ALIGNMENT.INI",
"FACTDATA1": "0",
"MONEY": "0",
"VARMONEY": "0.0"
},
"file": "abilities-2.html"
}
],
"/behaviors.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "behaviors.html"
},
{
"params": {
"ROOM": "New Area#0",
"AREA": "New Area",
"ACTION": "MODIFYROOM",
"CLASSES": "StdRoom",
"NAME": "New Room",
"DESCRIPTION": "Brand new database room! You need to change this text with the MODIFY ROOM command.  If your character is not an Archon, pick up the book you see here and read it immediately!",
"ITEM1": "-13930425600"
},
"file": "behaviors-1.html"
},
{
"params": {
"ROOM": "New Area#1",
"AREA": "New Area",
"ACTION": "MODIFYROOM",
"CLASSES": "StoneRoom",
"NAME": "Rocky Room",
"DESCRIPTION": "You`re standing in a dull room of stone.",
"AFFECT1": "Prop_Smell",
"ADATA1": "stinky"
},
"file": "behaviors-2.html"
}
],
"/affects.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "affects.html"
},
{
"params": {
"ROOM": "New Area#0",
"AREA": "New Area",
"ACTION": "MODIFYROOM",
"CLASSES": "StdRoom",
"NAME": "New Room",
"DESCRIPTION": "Brand new database room! You need to change this text with the MODIFY ROOM command.  If your character is not an Archon, pick up the book you see here and read it immediately!",
"ITEM1": "-13930425600"
},
"file": "affects-1.html"
},
{
"params": {
"ROOM": "New Area#1",
"AREA": "New Area",
"ACTION": "MODIFYROOM",
"CLASSES": "StoneRoom",
"NAME": "Rocky Room",
"DESCRIPTION": "You`re standing in a dull room of stone.",
"AFFECT1": "Prop_Smell",
"ADATA1": "stinky"
},
"file": "affects-2.html"
}
],
"/grinder/control.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/control.html"
},
{
"params": {},
"file": "grinder/control-1.html"
}
],
"/grinder/journal.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/journal.html"
},
{
"params": {
"CHANNELBACKLOGPAGE": "0"
},
"file": "grinder/journal-1.html"
},
{
"params": {
"area": "New Area",
"areamasktype": "AREA",
"jrnlnum": "7",
"bestfilebrowse": "/",
"channel": "GOSSIP",
"CHANNEL": "GOSSIP",
"CHANNELBACKLOGPAGE": "0"
},
"file": "grinder/journal-2.html"
}
],
"/grinder/accounts.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/accounts.html"
},
{
"params": {},
"file": "grinder/accounts-1.html"
}
],
"/grinder/players.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/players.html"
},
{
"params": {},
"file": "grinder/players-1.html"
}
],
"/grinder/clans.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/clans.html"
},
{
"params": {},
"file": "grinder/clans-1.html"
}
],
"/grinder/polls.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/polls.html"
},
{
"params": {},
"file": "grinder/polls-1.html"
}
],
"/grinder/quests.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/quests.html"
},
{
"params": {},
"file": "grinder/quests-1.html"
}
],
"/grinder/resources.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/resources.html"
},
{
"params": {},
"file": "grinder/resources-1.html"
}
],
"/grinder/reports.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/reports.html"
},
{
"params": {},
"file": "grinder/reports-1.html"
}
],
"/grinder/log.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/log.html"
},
{
"params": {
"PAGELEN": "250",
"PAGE": "1"
},
"file": "grinder/log-1.html"
}
],
"/grinder/stats.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/stats.html"
},
{
"params": {
"DAYS": "1",
"SCALE": "1",
"RPTTYP": "USAGERPT",
"CODEPART": "BASECLASS",
"CODE": "*"
},
"file": "grinder/stats-1.html"
}
],
"/grinder/commands.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/commands.html"
},
{
"params": {},
"file": "grinder/commands-1.html"
}
],
"/grinder/races.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/races.html"
},
{
"params": {},
"file": "grinder/races-1.html"
}
],
"/grinder/charclasses.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/charclasses.html"
},
{
"params": {},
"file": "grinder/charclasses-1.html"
}
],
"/grinder/abilities.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/abilities.html"
},
{
"params": {},
"file": "grinder/abilities-1.html"
},
{
"params": {
"NEW": "Acidbreath"
},
"file": "grinder/abilities-2.html"
}
],
"/grinder/recipes.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/recipes.html"
},
{
"params": {},
"file": "grinder/recipes-1.html"
}
],
"/grinder/achievements.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/achievements.html"
},
{
"params": {},
"file": "grinder/achievements-1.html"
}
],
"/grinder/planarlist.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/planarlist.html"
},
{
"params": {},
"file": "grinder/planarlist-1.html"
}
],
"/grinder/holidays.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/holidays.html"
},
{
"params": {},
"file": "grinder/holidays-1.html"
}
],
"/grinder/factions.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/factions.html"
},
{
"params": {
"PRELOAD_ALIGNMENT.INI": "on"
},
"file": "grinder/factions-1.html"
}
],
"/grinder/socials.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/socials.html"
},
{
"params": {
"CHECK4LFS": "on"
},
"file": "grinder/socials-1.html"
}
],
"/grinder/deities.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/deities.html"
},
{
"params": {},
"file": "grinder/deities-1.html"
}
],
"/grinder/catalog.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/catalog.html"
},
{
"params": {},
"file": "grinder/catalog-1.html"
}
],
"/grinder/newarea.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/newarea.html"
}
],
"/grinder/possmodarea.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/possmodarea.html"
}
],
"/grinder/maparea.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "grinder/maparea.html"
}
],
"/grinder/browse.cmvp": [
{
"params": {},
"file": "grinder/browse.html"
},
{
"params": {
"FUNCTION": "NAMESEARCH"
},
"file": "grinder/browse-1.html"
},
{
"params": {
"FUNCTION": "SEARCH"
},
"file": "grinder/browse-2.html"
}
],
"/index.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "index.html"
}
],
"/pub/gamestatus.cmvp": [
{
"params": {
"AREAMASKTYPE": "AREA",
"AREA": "New Area"
},
"file": "pub/gamestatus.html"
}
],
"/grinder/editarea.cmvp": [
{
"params": {
"AREA": "New Area",
"ROOM": "New Area#0"
},
"file": "grinder/editarea.html"
},
{
"params": {
"AREA": "New Area",
"ROOM": "New Area#0",
"LINK": "N"
},
"file": "grinder/editarea-1.html"
},
{
"params": {
"AREA": "New Area"
},
"file": "grinder/editarea-2.html"
}
],
"/grinder/modarea.cmvp": [
{
"params": {
"AREA": "New Area",
"ROOM": "New Area#0"
},
"file": "grinder/modarea.html"
},
{
"params": {
"AREA": "New Area",
"ROOM": "New Area#0",
"LINK": "N"
},
"file": "grinder/modarea-1.html"
},
{
"params": {
"AREA": "New Area",
"ACTION": "MODIFYAREA"
},
"file": "grinder/modarea-2.html"
}
],
"/grinder/canmenu.cmvp": [
{
"params": {
"area": "New Area",
"bestfilebrowse": "/",
"room": "New Area#0"
},
"file": "grinder/canmenu.html"
},
{
"params": {
"area": "New Area",
"bestfilebrowse": "/",
"room": "New Area#1"
},
"file": "grinder/canmenu-1.html"
},
{
"params": {
"room": "New Area#0",
"name": "New Room",
"area": "New Area",
"classes": "StdRoom",
"action": "MODIFYROOM",
"bestfilebrowse": "/",
"item1": "-13930425600",
"errmsg": "No MOB Data!",
"description": "Brand new database room! You need to change this text with the MODIFY ROOM command.  If your character is not an Archon, pick up the book you see here and read it immediately!"
},
"file": "grinder/canmenu-2.html"
}
],
"/grinder/addroomitem.cmvp": [
{
"params": {
"ROOM": "New Area#0",
"AREA": "New Area",
"ACTION": "MODIFYROOM",
"CLASSES": "StdRoom",
"NAME": "New Room",
"DESCRIPTION": "Brand new database room! You need to change this text with the MODIFY ROOM command.  If your character is not an Archon, pick up the book you see here and read it immediately!",
"ITEM1": "-13930425600"
},
"file": "grinder/addroomitem.html"
},
{
"params": {
"ROOM": "New Area#1",
"AREA": "New Area",
"ACTION": "MODIFYROOM",
"CLASSES": "StoneRoom",
"NAME": "Rocky Room",
"DESCRIPTION": "You`re standing in a dull room of stone.",
"AFFECT1": "Prop_Smell",
"ADATA1": "stinky"
},
"file": "grinder/addroomitem-1.html"
}
],
"/grinder/editroomitem.cmvp": [
{
"params": {
"ROOM": "New Area#0",
"AREA": "New Area",
"ACTION": "MODIFYROOM",
"CLASSES": "StdRoom",
"NAME": "New Room",
"DESCRIPTION": "Brand new database room! You need to change this text with the MODIFY ROOM command.  If your character is not an Archon, pick up the book you see here and read it immediately!",
"ITEM1": "-13930425600"
},
"file": "grinder/editroomitem.html"
},
{
"params": {
"ROOM": "New Area#1",
"AREA": "New Area",
"ACTION": "MODIFYROOM",
"CLASSES": "StoneRoom",
"NAME": "Rocky Room",
"DESCRIPTION": "You`re standing in a dull room of stone.",
"AFFECT1": "Prop_Smell",
"ADATA1": "stinky"
},
"file": "grinder/editroomitem-1.html"
}
],
"/grinder/editmob.cmvp": [
{
"params": {
"ROOM": "New Area#0",
"AREA": "New Area",
"ACTION": "MODIFYROOM",
"CLASSES": "StdRoom",
"NAME": "New Room",
"DESCRIPTION": "Brand new database room! You need to change this text with the MODIFY ROOM command.  If your character is not an Archon, pick up the book you see here and read it immediately!",
"ITEM1": "-13930425600"
},
"file": "grinder/editmob.html"
},
{
"params": {
"ROOM": "New Area#1",
"AREA": "New Area",
"ACTION": "MODIFYROOM",
"CLASSES": "StoneRoom",
"NAME": "Rocky Room",
"DESCRIPTION": "You`re standing in a dull room of stone.",
"AFFECT1": "Prop_Smell",
"ADATA1": "stinky"
},
"file": "grinder/editmob-1.html"
}
],
"/grinder/addmob.cmvp": [
{
"params": {
"ROOM": "New Area#0",
"AREA": "New Area",
"ACTION": "MODIFYROOM",
"CLASSES": "StdRoom",
"NAME": "New Room",
"DESCRIPTION": "Brand new database room! You need to change this text with the MODIFY ROOM command.  If your character is not an Archon, pick up the book you see here and read it immediately!",
"ITEM1": "-13930425600"
},
"file": "grinder/addmob.html"
},
{
"params": {
"ROOM": "New Area#1",
"AREA": "New Area",
"ACTION": "MODIFYROOM",
"CLASSES": "StoneRoom",
"NAME": "Rocky Room",
"DESCRIPTION": "You`re standing in a dull room of stone.",
"AFFECT1": "Prop_Smell",
"ADATA1": "stinky"
},
"file": "grinder/addmob-1.html"
}
],
"/grinder/deviations.cmvp": [
{
"params": {
"area": "New Area",
"bestfilebrowse": "/",
"room": "New Area#0"
},
"file": "grinder/deviations.html"
},
{
"params": {
"area": "New Area",
"bestfilebrowse": "/",
"room": "New Area#1"
},
"file": "grinder/deviations-1.html"
},
{
"params": {
"AREA": "New Area"
},
"file": "grinder/deviations-2.html"
}
],
"/grinder/worldmap.cmvp": [
{
"params": {
"area": "New Area",
"bestfilebrowse": "/"
},
"file": "grinder/worldmap.html"
},
{
"params": {
"AREA": "New Area"
},
"file": "grinder/worldmap-1.html"
}
],
"/grinder/menu3.cmvp": [
{
"params": {
"area": "New Area",
"bestfilebrowse": "/"
},
"file": "grinder/menu3.html"
}
],
"/grinder/editexitform.cmvp": [
{
"params": {
"AREA": "New Area",
"ROOM": "New Area#0",
"LINK": "N"
},
"file": "grinder/editexitform.html"
},
{
"params": {
"ROOM": "New Area#0",
"LINK": "N",
"AREA": "New Area",
"ACTION": "MODIFYEXIT",
"CLASSES": "StdOpenDoorway"
},
"file": "grinder/editexitform-1.html"
},
{
"params": {
"link": "N",
"area": "New Area",
"bestfilebrowse": "/",
"room": "New Area#0"
},
"file": "grinder/editexitform-2.html"
}
],
"/grinder/editexit.cmvp": [
{
"params": {
"AREA": "New Area",
"ROOM": "New Area#0",
"LINK": "N"
},
"file": "grinder/editexit.html"
},
{
"params": {
"AREA": "New Area"
},
"file": "grinder/editexit-1.html"
}
],
"/ahelp.cmvp": [
{
"params": {},
"file": "ahelp.html"
}
],
"/grinder/sfideas.cmvp": [
{
"params": {
"CHANNELBACKLOGPAGE": "0"
},
"file": "grinder/sfideas.html"
},
{
"params": {},
"file": "grinder/sfideas-1.html"
}
],
"/grinder/banners.cmvp": [
{
"params": {},
"file": "grinder/banners.html"
}
],
"/grinder/whitelists.cmvp": [
{
"params": {},
"file": "grinder/whitelists.html"
}
],
"/grinder/bankaccts.cmvp": [
{
"params": {},
"file": "grinder/bankaccts.html"
}
],
"/grinder/postaccts.cmvp": [
{
"params": {},
"file": "grinder/postaccts.html"
}
],
"/grinder/governments.cmvp": [
{
"params": {},
"file": "grinder/governments.html"
}
],
"/grinder/poll.cmvp": [
{
"params": {},
"file": "grinder/poll.html"
}
],
"/grinder/quest.cmvp": [
{
"params": {},
"file": "grinder/quest.html"
},
{
"params": {
"RAWTEXT": "set name MyQuest\n#   Set the quest name\n\n\tset duration 450\n#   The duration of the quest is 30 minutes (450 ticks).  \n#   The scripts on the mobs will do the rest...\n\n#    Some Sample Commands below: Use help for more!\n#\n#set wait 200\n#set interval 25\n#   For auto-quests: wait about 15 mins between quests\n#set area Midgaard\n#   inform the quest that it occurs in Midgaard\n#set mobtype ShopKeeper\n#   load up any mob from the above area of type shopkeeper. \n#   This command will set a room!\n#give script LOAD=quests/robbed/shopkeeper.script\n#   load the above behavior into the last mob \n#   (in this case, the shopkeeper)\n#set locale CityStreet \n#   find any locale in Midgaard of type CityStreet\n#set mob \"the thief\"\n#   load up any mob from area with that name.  \n#   Since a room was set above,  this will move the mob into \n#   the room (locale CityStreet)\n#give script \"LOAD=quests/robbed/thief.script\"\n#   load the above behavior into the last mob \n#   (in this case, the thief)\n"
},
"file": "grinder/quest-1.html"
}
],
"/grinder/questmaker.cmvp": [
{
"params": {},
"file": "grinder/questmaker.html"
}
],
"/grinder/thread.cmvp": [
{
"params": {
"TICKGROUPCOUNTER": "0"
},
"file": "grinder/thread.html"
}
],
"/grinder/command.cmvp": [
{
"params": {},
"file": "grinder/command.html"
}
],
"/grinder/race.cmvp": [
{
"params": {},
"file": "grinder/race.html"
}
],
"/grinder/crossclass.cmvp": [
{
"params": {},
"file": "grinder/crossclass.html"
}
],
"/grinder/charclass.cmvp": [
{
"params": {},
"file": "grinder/charclass.html"
}
],
"/grinder/ablesummary.cmvp": [
{
"params": {},
"file": "grinder/ablesummary.html"
}
],
"/grinder/components.cmvp": [
{
"params": {},
"file": "grinder/components.html"
},
{
"params": {
"NEW": "Acidbreath"
},
"file": "grinder/components-1.html"
}
],
"/grinder/allqualifys.cmvp": [
{
"params": {},
"file": "grinder/allqualifys.html"
},
{
"params": {
"NEW1": "Acidbreath",
"NEW2": "Acidbreath"
},
"file": "grinder/allqualifys-1.html"
}
],
"/grinder/ability.cmvp": [
{
"params": {},
"file": "grinder/ability.html"
}
],
"/grinder/recipe.cmvp": [
{
"params": {},
"file": "grinder/recipe.html"
}
],
"/grinder/autotitles.cmvp": [
{
"params": {},
"file": "grinder/autotitles.html"
}
],
"/grinder/holiday.cmvp": [
{
"params": {},
"file": "grinder/holiday.html"
}
],
"/grinder/faction.cmvp": [
{
"params": {},
"file": "grinder/faction.html"
}
],
"/grinder/social.cmvp": [
{
"params": {},
"file": "grinder/social.html"
}
],
"/grinder/pickroom.cmvp": [
{
"params": {},
"file": "grinder/pickroom.html"
}
],
"/grinder/editmobform.cmvp": [
{
"params": {
"AREA": "ANY",
"ROOM": "ANY"
},
"file": "grinder/editmobform.html"
},
{
"params": {
"MOB": "NEW",
"ROOM": "ANY"
},
"file": "grinder/editmobform-1.html"
},
{
"params": {
"ROOM": "ANY",
"ACTION": "MODIFYMOB",
"NAME": "@break[error]endif[error]endif[error]if?CheckReqParm?<MOB=CATALOG-&@<TR>    <TD><FONT COLOR=",
"CATACAT": "@break[error]endif@<TR>\t<TD><FONT COLOR=",
"DISPLAYTEXT": "@break@",
"DESCRIPTION": "@break@",
"IMAGE": "@break[error]else@<TR>\t<TD><FONT COLOR=",
"MISCTEXT": "@break[error]endif@<TR>\t<TD><FONT COLOR=",
"LEVEL": "@break@",
"REJUV": "@break[error]if?!MobData?ISGENERIC@<TR>\t<TD><FONT COLOR=",
"ABILITY": "@break@",
"HEIGHT": "@break@",
"WEIGHT": "@break@",
"SPEED": "@break@",
"ATTACK": "@break@",
"DAMAGE": "@break@",
"ARMOR": "@break@",
"MONEY": "@break@",
"VARMONEY": "@break@",
"TATTOOS": "@break@",
"TAGS": "@break@",
"EXPERTISES": "@break@"
},
"file": "grinder/editmobform-2.html"
}
],
"/grinder/edititemform.cmvp": [
{
"params": {
"AREA": "ANY",
"ROOM": "ANY"
},
"file": "grinder/edititemform.html"
},
{
"params": {
"ITEM": "NEW",
"ROOM": "ANY",
"RESPONSEFIELD": "RESOURCES.REPLACE"
},
"file": "grinder/edititemform-1.html"
},
{
"params": {
"ITEM": "NEW",
"ROOM": "ANY"
},
"file": "grinder/edititemform-2.html"
}
],
"/grinder/map.cmvp": [
{
"params": {
"areamasktype": "AREA",
"area": "New Area",
"bestfilebrowse": "/"
},
"file": "grinder/map.html"
},
{
"params": {
"area": "New Area",
"bestfilebrowse": "/",
"room": "New Area#0",
"ROOM": "New Area#0"
},
"file": "grinder/map-1.html"
},
{
"params": {
"link": "N",
"area": "New Area",
"bestfilebrowse": "/",
"room": "New Area#0",
"ROOM": "New Area#0"
},
"file": "grinder/map-2.html"
}
],
"/grinder/menu1.cmvp": [
{
"params": {
"areamasktype": "AREA",
"area": "New Area",
"bestfilebrowse": "/"
},
"file": "grinder/menu1.html"
},
{
"params": {
"area": "New Area",
"bestfilebrowse": "/",
"room": "New Area#0"
},
"file": "grinder/menu1-1.html"
},
{
"params": {
"link": "N",
"area": "New Area",
"bestfilebrowse": "/",
"room": "New Area#0"
},
"file": "grinder/menu1-2.html"
}
],
"/grinder/map2.cmvp": [
{
"params": {
"areamasktype": "AREA",
"area": "New Area",
"bestfilebrowse": "/"
},
"file": "grinder/map2.html"
},
{
"params": {
"AREA": "New Area"
},
"file": "grinder/map2-1.html"
}
],
"/grinder/menu2.cmvp": [
{
"params": {
"areamasktype": "AREA",
"area": "New Area",
"bestfilebrowse": "/"
},
"file": "grinder/menu2.html"
}
],
"/grinder/browsefile.cmvp": [
{
"params": {},
"file": "grinder/browsefile.html"
},
{
"params": {
"FILE": "new.script",
"FILESAVETO": "DEFAULT"
},
"file": "grinder/browsefile-1.html"
},
{
"params": {
"AREA": "New Area"
},
"file": "grinder/browsefile-2.html"
}
],
"/grinder/editareaform.cmvp": [
{
"params": {
"area": "New Area",
"bestfilebrowse": "/",
"room": "New Area#0"
},
"file": "grinder/editareaform.html"
},
{
"params": {
"AREA": "New Area",
"ACTION": "MODIFYAREA"
},
"file": "grinder/editareaform-1.html"
},
{
"params": {
"link": "N",
"area": "New Area",
"bestfilebrowse": "/",
"room": "New Area#0"
},
"file": "grinder/editareaform-2.html"
}
],
"/grinder/quickfind.cmvp": [
{
"params": {
"AREA": "New Area"
},
"file": "grinder/quickfind.html"
}
],
"/grinder/allqualify.cmvp": [
{
"params": {},
"file": "grinder/allqualify.html"
},
{
"params": {
"LEVEL": "@AllQualifyData?LEVEL@",
"PROF": "@AllQualifyData?PROF@",
"MASK": "@AllQualifyData?MASK@",
"@RequestParameter?REQUIRESNAME2@": "@RequestParameter?@@RequestParameter?REQUIRESNAME2@@ @"
},
"file": "grinder/allqualify-1.html"
}
],
"/grinder/crxmenu.cmvp": [
{
"params": {
"AREA": "New Area"
},
"file": "grinder/crxmenu.html"
}
],
"/grinder/editareascripts.cmvp": [
{
"params": {
"AREA": "New Area"
},
"file": "grinder/editareascripts.html"
}
],
"/grinder/editareamobs.cmvp": [
{
"params": {
"AREA": "New Area"
},
"file": "grinder/editareamobs.html"
}
],
"/grinder/editareaitems.cmvp": [
{
"params": {
"AREA": "New Area"
},
"file": "grinder/editareaitems.html"
}
],
"/grinder/editareascriptslist.cmvp": [
{
"params": {
"area": "New Area",
"bestfilebrowse": "/"
},
"file": "grinder/editareascriptslist.html"
},
{
"params": {
"AREA": "New Area"
},
"file": "grinder/editareascriptslist-1.html"
}
],
"/grinder/editareamobslist.cmvp": [
{
"params": {
"area": "New Area",
"bestfilebrowse": "/"
},
"file": "grinder/editareamobslist.html"
},
{
"params": {
"AREA": "New Area"
},
"file": "grinder/editareamobslist-1.html"
}
],
"/grinder/editareaitemslist.cmvp": [
{
"params": {
"area": "New Area",
"bestfilebrowse": "/"
},
"file": "grinder/editareaitemslist.html"
},
{
"params": {
"AREA": "New Area"
},
"file": "grinder/editareaitemslist-1.html"
}
]
};
  var here = document.currentScript.src;
  var root = here.substring(0, here.lastIndexOf('/') + 1);
  function pagePath() {
    var file = location.href.substring(root.length).split('#')[0].split('?')[0];
    for (var p in INDEX) for (var i = 0; i < INDEX[p].length; i++) if (INDEX[p][i].file === file) return p;
    return '/grinder/loggedin.cmvp';
  }
  function resolve(ref) {
    var path = ref.split('?')[0], params = {};
    if (ref.indexOf('?') >= 0) ref.split('?')[1].split('&').forEach(function (kv) {
      var x = kv.split('='); if (x[0]) params[decodeURIComponent(x[0])] = decodeURIComponent((x[1] || '').replace(/\+/g, ' '));
    });
    if (path.charAt(0) !== '/') {
      var base = pagePath(); path = base.substring(0, base.lastIndexOf('/') + 1) + path;
    }
    return {path: path.replace(/\/\.\//g, '/'), params: params};
  }
  function target(path, params) {
    if (path.indexOf('/pub/guides/') === 0) return root + '../../../CoffeeMud/guides/' + path.substring(12);
    var c = INDEX[path];
    if (!c) return root + 'notcaptured.html#' + encodeURIComponent(path);
    var best = c[0], score = -1;
    c.forEach(function (v) {
      var s = 0; for (var k in v.params) if (params[k] === v.params[k]) s++;
      if (s > score) { score = s; best = v; }
    });
    return root + best.file;
  }
  function formParams(f) {
    var d = {};
    Array.prototype.forEach.call(f.elements, function (el) {
      if (!el.name) return;
      if ((el.type === 'checkbox' || el.type === 'radio') && !el.checked) return;
      d[el.name] = el.value;
    });
    return d;
  }
  function go(f) {
    var r = resolve(f.getAttribute('action') || f.action || pagePath());
    var p = formParams(f); for (var k in r.params) p[k] = r.params[k];
    var t = target(r.path, p);
    var w = (f.target && f.target !== '_top' && f.target !== '_self' && f.target !== '_parent') ? window.open(t, f.target) : null;
    if (!w) (f.target === '_top' ? window.top : window).location.href = t;
  }
  HTMLFormElement.prototype.submit = function () { go(this); };
  document.addEventListener('submit', function (e) { e.preventDefault(); go(e.target); }, true);
  var open = window.open;
  window.open = function (url, name, feat) {
    if (url && String(url).indexOf('.cmvp') >= 0) { var r = resolve(String(url)); url = target(r.path, r.params); }
    return open.call(window, url, name, feat);
  };
  document.addEventListener('click', function (e) {
    var a = e.target.closest ? e.target.closest('a') : null;
    if (!a) return;
    var h = a.getAttribute('href') || '';
    if (h.indexOf('.cmvp') >= 0 && h.indexOf('javascript:') !== 0) {
      e.preventDefault(); var r = resolve(h); window.location.href = target(r.path, r.params);
    }
  }, true);
})();
