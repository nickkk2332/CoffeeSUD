// World-file layout must preserve literal presentation/property text verbatim.
FIXFILE = 's25-markup';
var method = java.lang.Class.forName('com.planet_ink.coffee_mud.core.save.WorldFiles').getDeclaredMethod('layOut', java.lang.Class.forName('java.lang.String'));
method.setAccessible(true);
var conditional = '<VARIES><IFVAR MUSEUM_GATE=1>open</IFVAR><ELSE>closed</ELSE></VARIES> trailing prose';
var senses = 'SOUND="<VARIES><NIGHT>an owl</NIGHT><ELSE>a gull</ELSE></VARIES>" CHANCE=100';
var fields = ['RDESC', 'RDISP', 'NAME', 'DESC', 'DISP', 'ATEXT'];
fields.forEach(function (name) {
    var value = name === 'ATEXT' ? senses : conditional;
    var element = '<' + name + '>' + value + '</' + name + '>';
    var laid = String(method.invoke(null, '<ROOT>' + element + '<NEXT>ok</NEXT></ROOT>'));
    check(name + ' markup is unchanged', laid.indexOf(element) >= 0, laid);
    check(name + ' outer layout remains readable', laid.indexOf('\n <NEXT>ok</NEXT>\n') >= 0, laid);
});
var plain = String(method.invoke(null, '<ROOT><RDESC>first\n  second</RDESC><EMPTY /></ROOT>'));
check('authored multiline text remains exact', plain.indexOf('<RDESC>first\n  second</RDESC>') >= 0, plain);
done();
