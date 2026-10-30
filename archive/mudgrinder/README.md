# MUDGrinder archive

The MUDGrinder was CoffeeMud's web-based world editor, served by its built-in
web server. CoffeeSUD removed the web server, so the Grinder no longer runs.
This folder keeps it as a reference for how the editors were laid out and what
each one did. Nothing here is compiled or loaded by the game.

## Browsing it

Open `snapshot/index.html` in a web browser, straight from disk. No server
is involved.

The snapshot was taken from the original CoffeeMud 5.11 server (commit
`ea8fb18`, before the web server was removed) running the stock two-room world,
with one generic mob, weapon and armor added so that their editors have
something in them. It holds 169 pages: the main menu, the area map, the room,
mob, item, exit and area editors, the ability, race, class, faction, quest,
recipe, social, command and holiday editors with their lists, and the lists
of achievements and planes.

- Menu buttons, links and popups go to the matching saved page (`shim.js`
  does this, since the Grinder navigates by submitting forms).
- Anything that would save, create or delete goes to `notcaptured.html`, as
  do pages the snapshot did not reach and pages that need data the empty
  world lacks (players, accounts, clans, journals).
- Editors show the values they had when the snapshot was taken. You can type
  into them, but nothing is saved anywhere.
- The guide links open the HTML guides in `CoffeeMud/guides`.

## What else is here

- `web/` holds the original page templates (`admin/grinder/*.cmvp`), the
  admin site around them, and the web server's settings. The `@Name@` tokens in
  the templates were web macros that the server replaced with data.
- `WebMacros/` holds the Java behind those tokens, one class per macro;
  `WebMacros/grinder/` has the classes that read and saved each editor's form
  fields. These show which game fields each editor touched.
- `tools/` holds the scripts that made the snapshot: `grindsnap.py` crawls a
  running Grinder through `browse.js`, which drives Chromium with Playwright.
  To make a new snapshot, run the original server from commit `ea8fb18` with
  `RUNWEBSERVERS=admin`, make an Archon character, and run

  ```sh
  python3 tools/grindsnap.py http://127.0.0.1:27777 NAME PASSWORD snapshot seeds.txt
  ```

  where `seeds.txt` lists pages reached only through the map, such as
  `/grinder/editroomform.cmvp?AREA=New+Area&ROOM=New+Area%230`.
