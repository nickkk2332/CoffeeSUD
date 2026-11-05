FIXFILE='remaining-ui';
var D=CM.core.DevLog;
if(!CM.core.CharacterWindow.canOpen()) {
 check('headless fallback',!D.openWindow(D.Level.WARN,'RemainingUI'),'opened without display');
} else {
try {
 check('window opens',D.openWindow(D.Level.WARN,'RemainingUI'), 'no log window');
 check('window active',D.hasWindow(),'window missing');
 CM.core.Log.errOut('RemainingUI','VISIBLE_LOG_LINE');CM.core.Log.infoOut('RemainingUI','FILTERED_LOG_LINE');CM.core.Log.errOut('AnotherModule','FILTERED_MODULE_LINE');
 var f=java.lang.Class.forName('com.planet_ink.coffee_mud.core.DevLog').getDeclaredField('logWindow');f.setAccessible(true);var win=f.get(null),shown='';
 javax.swing.SwingUtilities.invokeAndWait(new java.lang.Runnable({run:function(){shown=''+field(win,'text').getText();}}));
 check('window receives filtered log',shown.indexOf('VISIBLE_LOG_LINE')>=0 && shown.indexOf('FILTERED_LOG_LINE')<0 && shown.indexOf('FILTERED_MODULE_LINE')<0,shown);
} finally {D.closeWindow();}
check('window closes',!D.hasWindow(),'window left open');
}
done();
