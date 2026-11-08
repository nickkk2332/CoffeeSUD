# Optional module example: practice journal

[Module guide](../../14-optional-modules.md) · [Example index](../README.md)

This example uses supported APIs and adds no balance rules. Copy its `modules/`
folder into an existing world's root, merge the two lines in this example's
`world.ini` into that world's settings, and start a new game. Do not replace an
existing world's complete world.ini with this short fragment.

`example.counter` requires `example.core`, demonstrating dependency selection
and a named export/reference. MODULES is blank by default; installing these
files alone does not select anything. The default world is not modified.

As an ordinary player:

```text
MODULES
MODULES DEMO example.counter
MODULES DEMO example.counter
SAVE practice
QUIT
```

Restore `practice` in a fresh process and invoke the demonstration again. With
`step=2`, the journal reports 2, 4, then 6. The count belongs to the character's
persistent identity, while the effective step is pinned in the save.

As a creator in a disposable DEV=1 campaign:

```text
WORLD MODULES
WORLD CHECK ONLY=MODULES
WORLD MODULES PLAN
WORLD MODULES STATE example.counter
WORLD MODULES CONFIGURE example.counter step 3
```

Changing the startup MODULES value to blank and restoring suspends this module
while retaining the count. Re-enable it and restore that suspended save to
resume. Existing campaigns made before modules were selected remain unchanged;
`WORLD MODULES ADOPT example.counter` explicitly adopts the world-selected
module into such a campaign.

Run the focused and played checks from the repository root, with a JDK on PATH:

```sh
tools/fixes.sh optional-modules
tools/fixes.sh modules
```

The played harness copies the default world, database schema and these manifests
to a scratch directory. Its transcripts and saves remain separate from user
worlds and campaigns.
