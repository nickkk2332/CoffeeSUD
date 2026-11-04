// Valid presentation definitions should pass authoring checks; actual typos still report.
FIXFILE='pr-authoring';var PC=CM.core.PropCheck;
function valid(id,text,label){var errors=PC.unknownKeys(id,text);check(label,errors.isEmpty(),String(errors));}
valid('BEHAVIOR_EMOTER','min=1 max=2 chance=50 RETRY NOREPEAT=2 QUIETAFTER=3;IFVAR QUEST_STAGE=2 WEIGHT=3 MASK="-NAME +Ned" SOUND a bell.','ambient options');
valid('BEHAVIOR_SOUNDER','min=1 max=2;IDLE: IFVAR QUEST_STAGE=2 WEIGHT=3 ONCE SOUND a bell.','sounder conditions');
valid('Prop_Details','runes,carving=<VARIES><IFVAR BRIDGE=1>Runes.</IFVAR></VARIES> TATTOO=RUNE SETVAR=RUNE=1 HIGHLIGHT;ledger=Entry. READ="Third bell." MASK="-INT +8"','detail keywords and state');
valid('Prop_Senses','SOUND="Water; drips.";TOUCH="Stone." SETVAR=FELT=1 TATTOO=STUD CHANCE=90','sensory state');
valid('Prop_FirstEntry','TEXT="A new hall." ONCE=PROFILE SETVAR=MET=1 TATTOO=MET ID=hall','arrival state');
check('typos still found',String(PC.unknownKeys('BEHAVIOR_EMOTER','min=1 max=2 NOREPEET=2;SOUND a bell.')).indexOf('NOREPEET')>=0,'typo silently accepted');
done();
