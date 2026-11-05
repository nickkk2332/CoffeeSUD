# Project status audit probes

These are diagnostic reproductions for [the feature review](../project-status-review.md),
not engine fixes or additions to the normal regression suite. They use compiled
CoffeeSUD classes at `d1787779`. The JavaScript probe reports observations with
`AUDIT`; a harness PASS means it completed, not that the observed behaviors are
correct.

The recorded observations are the pre-fix baseline. The
[implementation follow-up](../project-review-fixes.md) links the regression
fixtures that assert the corrected behavior; rerunning these diagnostic scripts
against a later tree may produce different observations.

Run from the repository root with a working JDK and Python 3. Build the engine
first if needed:

```
CoffeeMud/build.sh
```

For the JavaScript probe, use the fixes harness's isolated world/database. Do not
run it in a real campaign. The script temporarily writes authored asset files
inside that scratch world, exercises stores and behavior STATE, and cleans up.

```sh
audit_fixture=tools/fixtures/fixes/audit-project-probe.js
if [ -e "$audit_fixture" ]; then
    echo "The temporary fixture path already exists; inspect it first."
    exit 1
fi
trap 'rm -f "$audit_fixture"' EXIT
cp audit/probes/project-status.js "$audit_fixture"
KEEP=1 tools/fixes.sh audit-project-probe
```

Do not run another fixes harness concurrently. The kept scratch directory's
`jrun.txt` contains the AUDIT lines. `project-status-results.txt` records the
observations from this review.

Run the standalone probes in separate JVMs. They create temporary files rather
than using a real save folder:

```sh
audit_classes=$(mktemp -d)
javac --release 8 -cp CoffeeMud:CoffeeMud/lib/js.jar -d "$audit_classes" audit/probes/*.java
java -cp "$audit_classes":CoffeeMud:CoffeeMud/lib/js.jar SaveDurabilityProbe
java -cp "$audit_classes":CoffeeMud:CoffeeMud/lib/js.jar RuntimeBudgetProbe
```

On Windows, use the equivalent classpath separator. The runtime-budget probe
sleeps for 1.5 seconds through Java under a one-second interpreted budget; it
does not run an infinite loop. The save probe directly exercises filesystem
operations and simulates the rename window; it is not a power-failure test or a
live two-process corruption test. The combat probes use the same STATE API the
save writer uses; full played fight/restore cases remain follow-up tests.
