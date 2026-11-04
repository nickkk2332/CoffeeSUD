// L5 and L12: world dictionaries, morphology, repeatable syllables and approved rule hooks survive XML.
FIXFILE="l-lexicon";
var V=CM.core.Vocabulary, L=CMClass.getAbility("GenLanguage").copyOf();L.setStat("CLASS9","FixLexicon");
var file="vocab/languages/fixlexicon.txt",f=V.file(file),before=String(V.read(file)),existed=f.isFile();
try {
 V.write(file,"# UTF-8 dictionary\nHELLO=zaal\nWALK=tal\nCITY=qarth\nWATER=ésh\n");
 L.setStat("WORDFILE","fixlexicon.txt");L.setStat("SUFFIXES","ED:ka,S:en");L.setStat("SYLLABLES","ba,ka/ri,tu");L.setStat("KEEPWORDS","Hakim,oak");L.setStat("WRITTENNAME","Glyphscript");
 check("dictionary",String(L.translate(L.ID(),"hello"))=="zaal","file dictionary ignored");
 check("dictionary UTF8",String(L.translate(L.ID(),"water"))=="ésh","UTF8 lost");
 check("dictionary case",String(L.translate(L.ID(),"Hello"))=="Zaal"&&String(L.translate(L.ID(),"HELLO"))=="ZAAL","case not retained");
 check("suffix",String(L.translate(L.ID(),"walked"))=="talka"&&String(L.translate(L.ID(),"cities"))=="qarthen","suffix not applied");
 L.setStat("HASHEDWORDS","HELLO=inline");check("inline wins",String(L.translate(L.ID(),"hello"))=="inline","dictionary overrode inline hash");
 check("keep words",String(L.translate(L.ID(),"oak"))=="oak","keep ignored");L.setStat("KEEPNAMES","true");
 check("keep names",String(L.translate(L.ID(),"Qarth"))=="Qarth","proper name translated");var named=newMob("GenMob",M.location());named.setName("fixlowercasename");check("keep lowercase mob name",String(L.translate(L.ID(),"fixlowercasename"))=="fixlowercasename","mob name not preserved");named.destroy();L.setStat("KEEPNAMES","false");
 var unknown=String(L.translate(L.ID(),"unlisted"));check("syllables deterministic",unknown==String(L.translate(L.ID(),"unlisted"))&&/^(ba|ka)(ri|tu)(ba|ka)$/.test(unknown),unknown);
 check("written name",String(L.writtenName())=="Glyphscript",L.writtenName());
 L.setStat("INTERPRETS","Elvish(60),Sylvan(30)");L.setProficiency(50);check("interpret percentages",L.getProficiency("Elvish")==30&&L.getProficiency("Sylvan")==15,L.getProficiency("Elvish"));
 var xml=String(L.getStat("ALLXML")),copy=CMClass.getAbility("GenLanguage").copyOf();copy.setStat("ALLXML",xml);
 ["WORDFILE","SUFFIXES","SYLLABLES","KEEPWORDS","KEEPNAMES","WRITTENNAME","INTERPRETS"].forEach(function(k){check("XML "+k,String(copy.getStat(k))==String(L.getStat(k)),copy.getStat(k));});
 var script="function translate(w,lang){return String(w).split('').reverse().join('');} function garble(t,n){return 'encoded:'+t;}";
 L.setStat("TRANSLATESCRIPT",script);var sb=new java.lang.StringBuffer(script);CMSecurity.approveJScript("Fixer",new java.lang.String(script).hashCode());
 check("translate script",String(L.translate(L.ID(),"hello"))=="olleh","script did not precede hash");
 check("garble script",String(L.scrambleAll(L.ID(),"hello friend",12))=="encoded:hello friend","garble hook ignored");
 var fallback="function translate(w){return null;}";CMSecurity.approveJScript("Fixer",new java.lang.String(fallback).hashCode());L.setStat("TRANSLATESCRIPT",fallback);
 check("null falls back",String(L.translate(L.ID(),"hello"))=="inline","null return did not fall back");
 copy.setStat("ALLXML",L.getStat("ALLXML"));check("script XML",String(copy.getStat("TRANSLATESCRIPT"))==fallback,"script lost");
 L.setStat("WORDFILE","../../../../etc/passwd");check("dictionary confined",CM.core.LanguageLexicon.dictionary(L,"root")==null,"escaped world dictionary");
}finally{if(existed)V.write(file,before);else f["delete"]();}
done();
