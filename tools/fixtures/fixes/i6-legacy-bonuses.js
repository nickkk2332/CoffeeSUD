FIXFILE='i6-legacy-bonuses';
var W=CM.Items.interfaces.Wearable,R=scratchRoom(),V=newMob('GenMob',R),items=[];
function piece(id,text,slot){var I=CMClass.getItem('GenArmor');I.setRawProperLocationBitmap(slot);I.setRawLogicalAnd(false);V.addItem(I);var P=CMClass.getAbility(id);P.setMiscText(text);I.addNonUninvokableEffect(P);items.push(I);return I;}
function wear(I){CMLib.commands().postWear(V,I,true);R.recoverRoomStats();}
function remove(I){CMLib.commands().postRemove(V,I,true);R.recoverRoomStats();}
try {
 var a=piece('Prop_WearEnabler','Skill_Dodge;ALLSET="legacy_enabled-2"',W.WORN_HEAD);
 var b=piece('Prop_WearResister','fire 50% ALLSET="legacy_enabled-2"',W.WORN_TORSO);
 wear(a);check('skill inactive below threshold',V.fetchAbility('Skill_Dodge')==null,'skill granted early');
 wear(b);check('skill activates at threshold',V.fetchAbility('Skill_Dodge')!=null,'skill missing');
 remove(b);check('skill removed below threshold',V.fetchAbility('Skill_Dodge')==null,'skill left behind');
 a.destroy();b.destroy();items=[];
 var c=piece('Prop_WearSpellCast','Spell_DetectMagic;ALLSET="legacy_spell-2"',W.WORN_HEAD);
 var d=piece('Prop_WearResister','fire 50% ALLSET="legacy_spell-2"',W.WORN_TORSO);
 wear(c);check('spell inactive below threshold',V.fetchEffect('Spell_DetectMagic')==null,'spell granted early');
 wear(d);check('spell activates at threshold',V.fetchEffect('Spell_DetectMagic')!=null,'spell missing');
 remove(d);check('spell removed below threshold',V.fetchEffect('Spell_DetectMagic')==null,'spell left behind');
} finally {items.forEach(function(I){I.destroy();});V.destroy();R.destroy();}
done();
