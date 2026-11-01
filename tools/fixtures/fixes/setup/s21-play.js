// S21: a cron job, as CREATE CRON and MODIFY CRON make it, that has the
// player say something every 2 minutes of game time (30 ticks): in
// turn-based play, WAIT passes that much game time in about a second
FIXFILE = "s21-play";
var GameTime = CM.core.GameTime;
var E = CMClass.getCommon("DefaultJournalEntry");
E.from(M.Name());
E.subj("fixcron");
E.msg("say FIXCRON ran");
E.dateStr("" + java.lang.System.currentTimeMillis());
E.update(GameTime.now() + 120000);
E.parent("");
E.msgIcon("");
E.data('INTERVAL="120000"');
E.to("ALL");
CMLib.database().DBWriteJournal("SYSTEM_CRON", E);
CMLib.journals().activate();
check("job", CMLib.database().DBReadJournalMsgsByCreateDate("SYSTEM_CRON", true).size() > 0, "no cron job");
done();
