FIXFILE='remaining-regressions';
function s(v){return ''+v;}function has(v,p){return s(v).indexOf(p)>=0;}
var SC=CM.core.ScriptCheck, GC=CM.core.GenerateCheck;
var f=new java.io.File(WorldFiles.worldDir(),'resources/randareas/remaining-check.xml');f.getParentFile().mkdirs();
try {
 java.nio.file.Files.write(f.toPath(),new java.lang.String('<AREADATA>\n<STRING id="good" predefine="COUNT=4" requires="COUNT=int">$COUNT</STRING>\n<STRING id="unbound">$MISSING_REMAINING_VALUE</STRING>\n<STRING id="conflict" requires="COLOR=red,COLOR=blue">x</STRING>\n<STRING id="badconstant" predefine="VALUE=oops" requires="VALUE=int">x</STRING>\n<STRING id="typo" pick="any-1">x</STRING>\n</AREADATA>').getBytes('UTF-8'));
 var result=GC.check('randareas/remaining-check.xml');
 check('generator undeclared variable',has(result,'MISSING_REMAINING_VALUE is not defined'),result);
 check('generator impossible requirements',has(result,'REQUIRES COLOR has no possible value'),result);
 check('generator bad required constant',has(result,'REQUIRES VALUE=int cannot be met by definition oops'),result);
 check('generator attribute typo',has(result,'unknown attribute PICK'),result);
 check('generator declared variable accepted',!has(result,'STRING good:'),result);
 var bad=SC.checkText('remaining','ONCE_PROG\nIF VAR($i)\nENDIF\n~',null);
 check('function argument count',has(bad,'VAR() needs at least 3 arguments'),bad);
 var good=SC.checkText('remaining','ONCE_PROG\nMPSETVAR $i V yes\nIF VAR($i V == "foo(1)")\nMPECHO yes\nENDIF\n~',null);
 check('quoted function text ignored',!has(good,'unknown function FOO'),good);
 var loop=SC.checkText('remaining','ONCE_PROG\nWHILE VAR($i V == yes)\nMPECHO loop\n~',null);
 check('while block checked',has(loop,'WHILE without ENDWHILE'),loop);
 check('empty variable assignment accepted',SC.checkText('remaining','ONCE_PROG\nMPSETVAR $i CLEARED\n~',null).isEmpty(),'empty value rejected');
 var noFalseShadow=CM.Behaviors.MudChat.lintChat('>tester\n*(hello)\n1say fight\n(hello friend)\n1say peace\n');
 check('chat combat patterns separate',!has(noFalseShadow,'hidden'),noFalseShadow);
 var noGuardShadow=CM.Behaviors.MudChat.lintChat('>tester\n?VAR($i OPEN == yes)\n(hello)\n1say welcome\n(hello friend)\n1say peace\n');
 check('guarded chat pattern not assumed universal',!has(noGuardShadow,'hidden'),noGuardShadow);
 var stable=CMath.getRandState(),prev=CM.core.GenSeed.begin(19);
 CMLib.dice().roll(1,100,0);
 var thread=new java.lang.Thread(new java.lang.Runnable({run:function(){CMLib.dice().roll(1,100,0);}}));thread.start();thread.join();
 var after=CMath.getRandState();CM.core.GenSeed.end(prev);
 check('generation does not rewind other threads',after.equals(CMath.getRandState())&&!stable.equals(after),'shared rolls lost');
} finally {f['delete']();}
done();
