// Reopening a database with deleted rows compacts it without changing live records.
// The default temp directory can be on a different filesystem from the game.
FIXFILE="db-vacuum";
var dir=java.nio.file.Files.createTempDirectory("sud-vacuum-"), conn=null, stmt=null;
try {
 var schema=new java.lang.String("VACUUMTEST V1\nID integer KEY\nPAYLOAD string NULL\n\n");
 java.nio.file.Files.write(dir.resolve("fakedb.schema"),schema.getBytes("UTF-8"));
 conn=new Packages.com.planet_ink.fakedb.backend.Connection(String(dir.toAbsolutePath()));stmt=conn.createStatement();
 for(var i=0;i<20;i++)stmt.executeUpdate("INSERT INTO VACUUMTEST (ID,PAYLOAD) VALUES ("+i+",'kept-"+i+"')");
 for(var i=0;i<15;i++)stmt.executeUpdate("DELETE FROM VACUUMTEST WHERE ID="+i);
 stmt.close();conn.close();conn=null;
 var data=dir.resolve("fakedb.data.VACUUMTEST").toFile(), before=data.length();
 conn=new Packages.com.planet_ink.fakedb.backend.Connection(String(dir.toAbsolutePath()));stmt=conn.createStatement();
 var rows=stmt.executeQuery("SELECT * FROM VACUUMTEST"), count=0, valid=true;
 while(rows.next()){count++;var id=rows.getInt("ID");valid=valid&&id>=15&&String(rows.getString("PAYLOAD"))=="kept-"+id;}
 rows.close();check("live records after reopen",count==5&&valid,count+" records, valid="+valid);
 check("deleted rows compacted",data.length()<before,"compaction did not replace the data file");
 stmt.executeUpdate("INSERT INTO VACUUMTEST (ID,PAYLOAD) VALUES (25,'kept-25')");stmt.close();conn.close();conn=null;
 conn=new Packages.com.planet_ink.fakedb.backend.Connection(String(dir.toAbsolutePath()));stmt=conn.createStatement();rows=stmt.executeQuery("SELECT * FROM VACUUMTEST WHERE ID=25");
 check("append after compaction",rows.next()&&String(rows.getString("PAYLOAD"))=="kept-25","append overwrote compacted rows");rows.close();
} finally {
 if(stmt!=null)stmt.close();if(conn!=null)conn.close();
 var files=dir.toFile().listFiles();if(files!=null)for(var i=0;i<files.length;i++)files[i].delete();dir.toFile().delete();
}
done();
