FIXFILE='pr-play';
var room=CMLib.map().getRoom('FixPresentationSave#0'),remote=CMLib.map().getRoom('FixPresentationSave#2');room.bringMobHere(M,false);CM.core.ScriptEvents.visit(M,room);
CM.core.Chronicle.add(M,'Clues','FIX_SAVED_JOURNAL',room);CM.core.LoreBook.unlock(M,'SAVEDENTRY',false,room);CM.core.LoreBook.unlock(M,'SAVEDPROFILE',true,room);CM.core.PlayerMaps.reveal(M,remote,'X','FIX_SAVED_MARK','r');
CM.core.ColorAliases.command(M,java.util.Arrays.asList('COLORSET','LORE','LIGHTGREEN'));
var record=CM.core.StoryRecords.read(M);record.put('mapSize',4);CM.core.StoryRecords.write(M,record);
check('unvisited revealed',CM.core.PlayerMaps.known(M,remote)&&!M.playerStats().hasVisited(remote),'reveal changed visits');check('profile unlock',CM.core.Party.profileOf(M).findTattoo('LORE_SAVEDPROFILE')!=null,'profile tattoo missing');done();
