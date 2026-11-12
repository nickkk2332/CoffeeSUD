#!/usr/bin/env python3
"""Q04: a bounded, deterministic combat simulation of composed builds against a fixed boss.

A scenario file (JSON) names the example packs that make the world, extra definitions, the modules, two or more
builds (composed skills with their supports and the caster's combat stats), a boss (hit points and combat stats), a
seed set, a turn limit, a stall limit and a work limit. Each build runs in its own engine process: for each seed the
campaign dice are seeded, a caster and the boss are made, and the caster casts its first ready skill each turn (the
clock moves one tick a turn, so cooldowns pass) until the boss falls, the turn limit or the work limit is reached, or
the run stalls (no damage for the stall limit's turns in a row). By default the boss does not act.

A boss with "acts" fights back: each turn, after the caster's cast, the allies (if any) take their combat rounds, the
boss casts its first ready composed skill at its foe (or, with none ready, takes a combat round of the engine's own),
and every fighter's effects tick once (ailments decay and end, burns burn, leech comes back). The caster then has
real hit points, and a run also stops when the caster falls. A boss can have elite traits (as the encounter director
gives them), the caster and the boss named resource pools (a guard pool) and properties (a reservation), and allies
can take formation roles from the scenario's formations.ini.

The report keeps what was sampled apart from what is exact: the observed results over the seeds (kills, turns to
kill, damage per turn, how each run stopped, what the caster had left, the ailments begun, what the guard pools took
and the optional modules' own counters), and the combat profile's exact expected damage per resolved cast of each
skill (COMPOSE COMPARE's figures), with the analytical estimate of turns that follows from them. The same scenario and
seeds give the same results.

    python3 tools/simulate.py SUDDocs/creator/examples/simulation/ember-vs-strike.json
    python3 tools/simulate.py SUDDocs/creator/examples/simulation/frost-warden.json
    python3 tools/simulate.py SCENARIO --seeds 1,2,3 --json results.json
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parent.parent
GAME = ROOT / 'CoffeeMud'
EXAMPLES = ROOT / 'SUDDocs/creator/examples'
NEWCHAR = 'Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'

HARNESS = r"""
var CM = Packages.com.planet_ink.coffee_mud, CMC = CM.core.CMClass, AC = CM.core.AbilityComposition, CP = CM.core.CombatProfiles;
var BC = CM.core.BuildCompare, CMath = CM.core.CMath, GT = CM.core.GameTime, CMProps = CM.core.CMProps, Lib = CM.core.CMLib;
var NR = CM.core.NamedResources, ET = CM.core.EliteTraits, FR = CM.core.FormationRoles, MS = CM.core.ModuleStats, SE = CM.core.ScriptEvents;
var S = __SCENARIO__, B = __BUILD__;
var room = mob().location();
var ACTS = !!S.boss.acts, ALLIES = S.allies || [], TICKID = CM.core.interfaces.Tickable.TICKID_MOB;
var DEFAULT_SYSTEM = CMProps.getIntVar(CMProps.Int.COMBATSYSTEM) == CM.Libraries.interfaces.CombatLibrary.CombatSystem.DEFAULT.ordinal();
function stats(m, json) { if (!json) return; var p = CMC.getAbility("Prop_CombatStats"); p.setMiscText(JSON.stringify(json)); m.addNonUninvokableEffect(p); }
function jlist(a) { var L = new java.util.ArrayList(); for (var i = 0; i < a.length; i++) L.add(String(a[i])); return L; }
// native melee figures (what a combat round uses), over what the level gave
function melee(m, spec) {
	if (spec.damage == null && spec.attack == null && spec.armor == null) return;
	if (spec.damage != null) m.basePhyStats().setDamage(spec.damage);
	if (spec.attack != null) m.basePhyStats().setAttackAdjustment(spec.attack);
	if (spec.armor != null) m.basePhyStats().setArmor(spec.armor);
	m.recoverPhyStats();
}
// named resource pools, at their start values, and properties put on as they are (a reservation, say)
function extras(m, spec) {
	var P = spec.pools || [], E = spec.effects || [];
	for (var i = 0; i < P.length; i++) NR.grant(m, P[i]);
	for (var i = 0; i < E.length; i++) {
		var X = CMC.getAbility(E[i].id);
		if (X == null) throw "no effect " + E[i].id;
		X.setMiscText(E[i].text || ""); m.addNonUninvokableEffect(X);
	}
	if (P.length + E.length > 0) { m.recoverCharStats(); m.recoverPhyStats(); m.recoverMaxState(); }
}
function composer(m, skills) {
	var A = CMC.getAbility("Skill_Compose"); A.setProficiency(100); m.addAbility(A);
	for (var k = 0; k < skills.length; k++) {
		AC.learn(m, skills[k].id);
		var sup = skills[k].supports || [];
		for (var j = 0; j < sup.length; j++) AC.link(m, skills[k].id, sup[j]);
	}
	return A;
}
function boss() {
	var b = CMC.getMOB("GenMob"); b.setName(S.boss.name || "a training boss"); b.basePhyStats().setLevel(S.boss.level || 1); b.recoverPhyStats();
	b.bringToLife(room, true); b.baseState().setHitPoints(S.boss.hp); b.recoverMaxState(); b.resetToMaxState(); stats(b, S.boss.stats);
	melee(b, S.boss);
	// elite traits, given as the encounter director gives them to a foe it spawns
	if (S.boss.traits && S.boss.traits.length) { ET.apply(b, jlist(S.boss.traits), "simulation"); b.resetToMaxState(); }
	var BA = null;
	if (S.boss.skills && S.boss.skills.length) { BA = composer(b, S.boss.skills); b.baseState().setMana(100000); b.recoverMaxState(); b.curState().setMana(b.maxState().getMana()); }
	extras(b, S.boss);
	return [b, BA];
}
function caster() {
	var c = CMC.getMOB("GenMob"); c.setName("a simulated " + B.name); c.bringToLife(room, true);
	c.baseState().setMana(B.mana || 100000); c.baseState().setHitPoints(B.hp || 100000); c.recoverMaxState(); c.resetToMaxState();
	var A = composer(c, B.skills);
	stats(c, B.stats); extras(c, B); return [c, A];
}
function ally(spec, leader) {
	var a = CMC.getMOB("GenMob"); a.setName(spec.name); a.basePhyStats().setLevel(spec.level || 1); a.recoverPhyStats();
	a.bringToLife(room, true); a.baseState().setHitPoints(spec.hp); a.recoverMaxState(); a.resetToMaxState(); stats(a, spec.stats); melee(a, spec);
	a["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](leader);
	if (spec.role) FR.assign(a, spec.role); // the role's tattoo and row, as FORMATION name ROLE role gives them
	return a;
}
function gone(b) { return b.amDead() || b.amDestroyed() || b.curState().getHitPoints() <= 0; }
function here(m) { return m != null && !gone(m) && m.location() == room; }
function clean() {
	for (var i = room.numItems() - 1; i >= 0; i--) { var I = room.getItem(i); if (I instanceof CM.Items.interfaces.DeadBody) I.destroy(); }
}
// the first skill off its cooldown whose named resource costs can be paid (a finisher waits for its combo)
function ready(m, skills) {
	for (var k = 0; k < skills.length; k++) {
		if (AC.remaining(m, skills[k].id) != 0) continue;
		var costs = AC.compiled(m, skills[k].id).resourceCosts();
		if (!costs.isEmpty() && (!NR.enabled() || String(NR.unaffordable(m, costs)) != "")) continue;
		return skills[k].id;
	}
	return null;
}
// a combat round, as the fighter's own tick gives one: its actions for the round, then the engine's combat
function round(m) {
	if (m.commandQueSize() == 0) m.setActions(m.actions() - Math.floor(m.actions()));
	m.setActions(m.actions() + m.phyStats().speed());
	if (DEFAULT_SYSTEM) m.setActions(m.actions() + 1.0);
	Lib.combat().tickCombat(m);
}
// one tick of a fighter's effects, as its own tick runs them: ailments decay and end, burns burn, leech comes back
function tickEffects(m) {
	if (!here(m)) return;
	var L = [];
	for (var e = m.effects(); e.hasMoreElements();) L.push(e.nextElement());
	for (var i = 0; i < L.length; i++) if (!L[i].tick(m, TICKID)) L[i].unInvoke();
}
function statuses(m) {
	var H = (m == null) ? null : m.fetchEffect("CompositionAilment"), out = [];
	if (H == null) return out;
	for (var it = H.statusConditions().iterator(); it.hasNext();) out.push(String(it.next()).replace(/^status:/, ""));
	return out;
}
function begun(into, side, was, now) {
	for (var i = 0; i < now.length; i++) if (was.indexOf(now[i]) < 0) { into[side] = into[side] || {}; into[side][now[i]] = (into[side][now[i]] || 0) + 1; }
}
var GUARD = "";
function holds(m) { return GUARD != "" && NR.enabled() && NR.held(m, GUARD) != null; }
function guardOf(m) { if (GUARD == "" || m == null || gone(m) || !NR.enabled()) return 0; var h = NR.held(m, GUARD); return h == null ? 0 : Number(h.value); }
// the optional modules' own counters (WORLD MODULES REPORT), to tell what one run added
function counters() {
	var out = {}, lines = String(MS.report()).split("\n");
	for (var i = 1; i < lines.length; i++) {
		if (lines[i].indexOf("Highest readings") == 0 || lines[i].indexOf("(") == 0) break;
		var g = /^\s+(.+?): (\d+)/.exec(lines[i]);
		if (g && g[1].indexOf(" | settled periods") < 0) out[g[1]] = Number(g[2]);
	}
	return out;
}
function outcome(m, id, target, p) {
	var o = BC.outcome(m, AC.compiled(m, id), target, p);
	return { skill: String(o.id), expected: o.expected, hit: CM.core.CombatRules.probability(o.hit), critical: CM.core.CombatRules.probability(o.critical),
		blocked: CM.core.CombatRules.probability(o.block), cooldown_ticks: Number(AC.compiled(m, id).value("cooldown")) };
}
var tick = CMProps.getTickMillis(), runs = [], exact = null, bossExact = null, work = 0, profile = "", info = {};
try {
	if (S.profile) CP.use(S.profile.id, S.profile.version || 1);
	for (var si = 0; si < S.seeds.length; si++) {
		CMath.setRandState(S.seeds[si]);
		var counted = counters();
		var madeBoss = boss(), b = madeBoss[0], BA = madeBoss[1], made = caster(), c = made[0], A = made[1];
		var friends = [];
		for (var ai = 0; ai < ALLIES.length; ai++) friends.push(ally(ALLIES[ai], c));
		if (exact == null) {
			exact = []; var p = CP.current(); profile = String(p.key()); GUARD = String(p.guardPool);
			var target = CP.stats(b, p).stats;
			for (var k = 0; k < B.skills.length; k++) exact.push(outcome(c, B.skills[k].id, target, p));
			if (BA != null) {
				bossExact = []; var mine = CP.stats(c, p).stats;
				for (var k = 0; k < S.boss.skills.length; k++) bossExact.push(outcome(b, S.boss.skills[k].id, mine, p));
			}
			if (S.boss.traits && S.boss.traits.length) {
				var T = b.fetchEffect("Prop_EliteTraits");
				info.traits = []; info.titles = (T == null) ? "" : String(T.titles());
				if (T != null) for (var it = T.traitIDs().iterator(); it.hasNext();) info.traits.push(String(it.next()));
			}
			if (b.maxState().getHitPoints() != S.boss.hp) info.boss_hp = b.maxState().getHitPoints();
		}
		var ailments = {}, guard = null, taken = 0, alliesDealt = 0, bossCasts = 0, bossRounds = 0, held = {};
		if (holds(c) || holds(b)) guard = { caster: 0, boss: 0 };
		if (ACTS) { c.setVictim(b); b.setVictim(c); for (var ai = 0; ai < friends.length; ai++) friends[ai].setVictim(b); }
		var turns = 0, dealt = 0, idle = 0, casts = 0, stop = "";
		while (true) {
			if (gone(b)) { stop = "killed"; break; }
			if (ACTS && gone(c)) { stop = "caster fell"; break; }
			if (turns >= S.turn_limit) { stop = "turn limit"; break; }
			if (work >= S.work_limit) { stop = "work limit"; break; }
			turns++;
			var id = ready(c, B.skills);
			var before = b.curState().getHitPoints(), was = statuses(b), wardBefore = guardOf(b);
			if (id != null) { c.curState().setMana(c.maxState().getMana()); A.invoke(c, CM.core.CMParms.parse("CAST " + id), b, false, 0); casts++; work++; }
			begun(ailments, "boss", was, statuses(b));
			if (guard != null) guard.boss += Math.max(0, wardBefore - guardOf(b));
			if (ACTS) {
				// the allies' rounds: each one's role reacts and picks its foe, then it attacks
				var mid = gone(b) ? 0 : b.curState().getHitPoints();
				for (var ai = 0; ai < friends.length && !gone(b); ai++) {
					var F = friends[ai];
					if (!here(F)) continue;
					if (!here(F.getVictim())) F.setVictim(b);
					round(F);
				}
				alliesDealt += Math.max(0, mid - (gone(b) ? 0 : b.curState().getHitPoints()));
				// the boss's turn: its first ready composed skill at its foe, or else a combat round
				if (!gone(b)) {
					var foe = here(b.getVictim()) ? b.getVictim() : (here(c) ? c : null);
					if (foe != null) {
						if (b.getVictim() != foe) b.setVictim(foe);
						var side = [c].concat(friends), on = [], hp0 = c.curState().getHitPoints(), ward = guardOf(c);
						for (var q = 0; q < side.length; q++) on.push(statuses(side[q]));
						var bid = (BA == null) ? null : ready(b, S.boss.skills);
						if (bid != null) { b.curState().setMana(b.maxState().getMana()); BA.invoke(b, CM.core.CMParms.parse("CAST " + bid), foe, false, 0); bossCasts++; }
						else { round(b); bossRounds++; }
						for (var q = 0; q < side.length; q++) begun(ailments, (q == 0) ? "caster" : "allies", on[q], statuses(side[q]));
						taken += Math.max(0, hp0 - (gone(c) ? 0 : c.curState().getHitPoints()));
						if (guard != null) guard.caster += Math.max(0, ward - guardOf(c));
					}
				}
				tickEffects(c); for (var ai = 0; ai < friends.length; ai++) tickEffects(friends[ai]); tickEffects(b);
			}
			var after = gone(b) ? 0 : b.curState().getHitPoints();
			var d = Math.max(0, before - after); dealt += d; idle = (d > 0) ? 0 : idle + 1;
			if (!gone(b) && !(ACTS && gone(c)) && idle >= S.stall_turns) { stop = "stalled"; break; }
			// a reservation that does not fit holds nothing (and gives nothing) this turn
			if (B.effects && here(c)) for (var e = c.effects(); e.hasMoreElements();) {
				var X = e.nextElement();
				if ((X instanceof NR.Reserver) && !NR.fits(c, X)) { var nm = String(X.reservationName()); held[nm] = (held[nm] || 0) + 1; }
			}
			if (!ACTS) { c.makePeace(true); if (!gone(b)) b.makePeace(true); }
			GT.advance(tick); SE.setTurns(SE.turns() + 1);
		}
		var run = { seed: S.seeds[si], turns: turns, dealt: dealt, casts: casts, stop: stop };
		if (ACTS) {
			run.caster_hp = gone(c) ? 0 : c.curState().getHitPoints(); run.taken = taken; run.boss_casts = bossCasts; run.boss_rounds = bossRounds;
			if (friends.length) {
				run.allies_fallen = 0; run.allies_dealt = alliesDealt;
				for (var ai = 0; ai < friends.length; ai++) if (gone(friends[ai])) run.allies_fallen++;
			}
		}
		for (var k in ailments) { run.ailments = ailments; break; }
		if (guard != null) run.guard = guard;
		for (var k in held) { run.reservations_suspended = held; break; }
		var now = counters(), diff = {}, any = false;
		for (var k in now) if (now[k] != (counted[k] || 0)) { diff[k] = now[k] - (counted[k] || 0); any = true; }
		if (any) run.counters = diff;
		runs.push(run);
		for (var ai = 0; ai < friends.length; ai++) if (!friends[ai].amDestroyed()) friends[ai].destroy();
		if (!c.amDestroyed()) c.destroy();
		if (!b.amDestroyed()) b.destroy();
		clean();
	}
	var out = { build: B.name, profile: profile, runs: runs, exact: exact };
	if (bossExact != null) out.boss_exact = bossExact;
	for (var k in info) out[k] = info[k];
	mob().tell("SIMRESULT " + JSON.stringify(out));
} catch (e) {
	mob().tell("SIMERROR " + B.name + ": " + e);
}
"""


def world_for(scenario, scratch):
    """The world the scenario names: the base world, its packs, its extra definitions, its formations and its modules."""
    world = scratch / 'world'
    shutil.copytree(GAME / 'world', world)
    for pack in scenario.get('packs', []):
        source = EXAMPLES / pack
        if not source.is_dir():
            raise SystemExit('scenario: no example pack ' + pack)
        shutil.copytree(source, world, dirs_exist_ok=True, ignore=shutil.ignore_patterns('README.md', '*.py'))
    for folder, items in scenario.get('definitions', {}).items():
        if not re.fullmatch(r'[a-z]+', folder):
            raise SystemExit('scenario: a definitions folder is a plain lower-case name')
        (world / folder).mkdir(parents=True, exist_ok=True)
        for item in items:
            (world / folder / ('%s.v%d.json' % (item['id'], item['version']))).write_text(json.dumps(item))
    if 'formations' in scenario:
        (world / 'formations.ini').write_text(scenario['formations'])
    ini = world / 'world.ini'
    text = ini.read_text() if ini.exists() else ''
    text = re.sub(r'(?m)^MODULES=.*\n?', '', text) + '\nMODULES=' + scenario['modules'] + '\n'
    ini.write_text(text)
    return world


def run_build(scenario, build, world, scratch, timeout):
    """One build's batch in its own engine process: its runs over every seed."""
    name = re.sub(r'[^a-z0-9]+', '-', build['name'].lower())
    creator = world / 'resources/creator'
    creator.mkdir(parents=True, exist_ok=True)
    (creator / ('sim-%s.js' % name)).write_text(HARNESS.replace('__SCENARIO__', json.dumps(scenario)).replace('__BUILD__', json.dumps(build)))
    batch = scratch / ('batch-' + name)
    (batch / 'db').mkdir(parents=True)
    shutil.copyfile(GAME / 'resources/fakedb/fakedb.schema', batch / 'db/fakedb.schema')
    script = batch / 'input'
    script.write_text('Simulator\n' + NEWCHAR + 'jrun /resources/creator/sim-%s.js\nquit\nn\n' % name)
    with (batch / 'output.txt').open('w') as log:
        # the batch is one JRUN: it may take as long as the process may
        result = subprocess.run([sys.executable, str(ROOT / 'tools/sudplay.py'), str(script), 'DBSERVICE=jdbc:fakedb:' + str(batch / 'db'),
                                 'WORLDDIR=' + str(world), 'SAVEDIR=' + str(batch / 'saves'), 'RESTORE=NEW', 'DEV=1', 'TIMEMODE=TURNS',
                                 'RANDOMSEED=%d' % scenario['seeds'][0], 'JSTIMELIMIT_JRUN=%d' % timeout],
                                env=dict(os.environ, SUDPLAY_LIMIT=str(timeout)), stdout=log, stderr=subprocess.STDOUT, timeout=timeout + 60)
    text = ' '.join((batch / 'output.txt').read_text(errors='replace').split())
    if 'SIMERROR' in text:
        raise SystemExit('build %s: %s' % (build['name'], text[text.index('SIMERROR'):][:400]))
    at = text.find('SIMRESULT ')
    if result.returncode != 0 or at < 0:
        raise SystemExit('build %s: the engine gave no result; see %s' % (build['name'], batch / 'output.txt'))
    return json.JSONDecoder().raw_decode(text, at + len('SIMRESULT '))[0]


def totals(runs, key):
    """A run's counts summed over the runs: {name: n} or {side: {name: n}}."""
    out = {}
    for r in runs:
        for k, v in r.get(key, {}).items():
            if isinstance(v, dict):
                side = out.setdefault(k, {})
                for name, n in v.items():
                    side[name] = side.get(name, 0) + n
            else:
                out[k] = out.get(k, 0) + v
    return out


def summary(scenario, result):
    runs = result['runs']
    killed = [r for r in runs if r['stop'] == 'killed']
    turns = [r['turns'] for r in killed]
    out = {
        'build': result['build'], 'runs': len(runs), 'killed': len(killed),
        'stalled': sum(1 for r in runs if r['stop'] == 'stalled'),
        'turn_limit': sum(1 for r in runs if r['stop'] == 'turn limit'),
        'work_limit': sum(1 for r in runs if r['stop'] == 'work limit'),
        'turns_to_kill': None if not turns else {'min': min(turns), 'median': statistics.median(turns), 'mean': round(statistics.mean(turns), 2), 'max': max(turns)},
        'damage_per_turn': round(sum(r['dealt'] for r in runs) / max(1, sum(r['turns'] for r in runs)), 2),
    }
    first = result['exact'][0] if result['exact'] else None
    out['exact'] = result['exact']
    # one skill, cast whenever ready: one cast every max(1, cooldown) turns, as the clock moves a tick a turn
    hp = result.get('boss_hp', scenario['boss']['hp'])
    out['estimate_turns'] = None if (len(result['exact']) != 1 or first['expected'] <= 0) else round(hp / first['expected'] * max(1, first['cooldown_ticks']), 2)
    if scenario['boss'].get('acts'):
        out['caster_fell'] = sum(1 for r in runs if r['stop'] == 'caster fell')
        out['caster_hp_left'] = round(statistics.mean(r['caster_hp'] for r in runs), 2)
        out['taken_per_turn'] = round(sum(r['taken'] for r in runs) / max(1, sum(r['turns'] for r in runs)), 2)
        out['boss_casts'] = sum(r['boss_casts'] for r in runs)
        out['boss_rounds'] = sum(r['boss_rounds'] for r in runs)
        if scenario.get('allies'):
            out['allies_fallen'] = sum(r['allies_fallen'] for r in runs)
            out['allies_dealt'] = sum(r['allies_dealt'] for r in runs)
    if 'boss_exact' in result:
        out['boss_exact'] = result['boss_exact']
    if 'traits' in result:
        out['traits'] = result['traits']
    for key, name in (('ailments', 'ailments'), ('guard', 'guard_absorbed'), ('reservations_suspended', 'reservations_suspended'), ('counters', 'counters')):
        found = totals(runs, key)
        if found or (key == 'guard' and any('guard' in r for r in runs)):
            out[name] = found
    return out


def counts(found):
    return ', '.join('%s %d' % (k, n) for k, n in sorted(found.items()))


def report(scenario, results):
    boss = scenario['boss']
    extra = ''
    if results and results[0].get('traits') is not None:
        extra += ' (%s with its elite traits: %s)' % (results[0].get('boss_hp', boss['hp']), results[0].get('titles') or ', '.join(results[0]['traits']))
    if boss.get('acts'):
        extra += '; it fights back' + (' with %s' % ', '.join(s['id'] for s in boss['skills']) if boss.get('skills') else '')
    lines = ['Simulation: ' + scenario.get('title', '(untitled)'),
             'World: packs %s; modules %s; profile %s' % (', '.join(scenario.get('packs', [])), scenario['modules'], results[0]['profile'] if results else '?'),
             'Boss: %s, %d hit points%s; seeds %s; turn limit %d; stalled after %d turns without damage; work limit %d casts per build'
             % (boss.get('name', 'a training boss'), boss['hp'], extra, scenario['seeds'], scenario['turn_limit'], scenario['stall_turns'], scenario['work_limit'])]
    if scenario.get('allies'):
        lines.append('Allies: ' + '; '.join('%s, %d hit points%s' % (a['name'], a['hp'], (', role ' + a['role']) if a.get('role') else '') for a in scenario['allies']))
    sums = []
    for result, build in zip(results, scenario['builds']):
        s = summary(scenario, result)
        sums.append(s)
        lines.append('')
        lines.append('Build %s (its own engine process):' % s['build'])
        lines.append('  Observed over %d seeded runs (sampled):' % s['runs'])
        lines.append('    killed %d; stalled %d; stopped at the turn limit %d; at the work limit %d%s' % (s['killed'], s['stalled'], s['turn_limit'], s['work_limit'],
                     ('; the caster fell %d' % s['caster_fell']) if 'caster_fell' in s else ''))
        if s['turns_to_kill']:
            t = s['turns_to_kill']
            lines.append('    turns to kill: min %d, median %s, mean %s, max %d' % (t['min'], t['median'], t['mean'], t['max']))
        lines.append('    damage per turn: %s' % s['damage_per_turn'] + ((' (the allies dealt %d in all)' % s['allies_dealt']) if 'allies_dealt' in s else ''))
        if 'caster_fell' in s:
            lines.append('    the caster (%d hit points): %s left on average (a fallen caster counts 0); %s damage taken per turn'
                         % (build['hp'], s['caster_hp_left'], s['taken_per_turn']))
            lines.append('    the boss: %d composed casts and %d combat rounds in all' % (s['boss_casts'], s['boss_rounds']))
        if 'allies_fallen' in s:
            lines.append('    allies fallen: %d in all' % s['allies_fallen'])
        if 'ailments' in s:
            lines.append('    ailments begun: ' + '; '.join('on the %s %s' % (side, counts(found)) for side, found in sorted(s['ailments'].items())))
        if 'guard_absorbed' in s:
            g = s['guard_absorbed']
            lines.append("    guard pool absorbed: %d of the boss's hits on the caster, %d of the caster's hits on the boss" % (g.get('caster', 0), g.get('boss', 0)))
        if 'reservations_suspended' in s:
            lines.append('    reservations suspended (turns): ' + counts(s['reservations_suspended']))
        if 'counters' in s:
            lines.append('    module counters, all runs (as WORLD MODULES REPORT counts them): ' + counts(s['counters']))
        lines.append("  Exact, from the combat profile's chances (not sampled):")
        for e in s['exact']:
            lines.append('    %s: expected %.2f damage per resolved cast (hit %d%%, blocked %d%%, critical %d%%); cooldown %d tick(s)'
                         % (e['skill'], e['expected'], round(e['hit'] * 100), round(e['blocked'] * 100), round(e['critical'] * 100), e['cooldown_ticks']))
        if s['estimate_turns'] is not None:
            lines.append('    analytical estimate: %s turns to kill (hit points over the expected damage of a cast, one cast whenever the skill is ready)' % s['estimate_turns'])
        elif len(s['exact']) > 1:
            lines.append('    analytical estimate: none for a build of several skills (the order they come ready in decides it)')
        for e in s.get('boss_exact', []):
            lines.append("    the boss's %s on the caster: expected %.2f damage per resolved cast (hit %d%%, blocked %d%%, critical %d%%); cooldown %d tick(s)"
                         % (e['skill'], e['expected'], round(e['hit'] * 100), round(e['blocked'] * 100), round(e['critical'] * 100), e['cooldown_ticks']))
    lines.append('')
    ranked = [s for s in sums if s['turns_to_kill']]
    if len(ranked) >= 2:
        ranked.sort(key=lambda s: s['turns_to_kill']['mean'])
        lines.append('Observed means: ' + '; '.join('%s kills in %s turns' % (s['build'], s['turns_to_kill']['mean'])
                                                    + ((' (%d of %d runs)' % (s['killed'], s['runs'])) if boss.get('acts') else '') for s in ranked) + '.')
    lines.append("The exact figures leave out a cast's proficiency check and the world's own rules; the observed runs include them.")
    if boss.get('acts'):
        lines.append("The boss's combat rounds, the allies, the guard pools and the effects' ticks are observed only; the exact figures leave them out.")
    return '\n'.join(lines), sums


def check_skills(skills, who):
    if not skills or len(skills) > 8 or not all(isinstance(s, dict) and s.get('id') for s in skills):
        raise SystemExit('scenario: %s names 1 to 8 skills, each with an id' % who)


def check_extras(spec, who):
    pools = spec.get('pools', [])
    if not isinstance(pools, list) or len(pools) > 8 or not all(isinstance(p, str) for p in pools):
        raise SystemExit('scenario: %s pools is a list of at most 8 pool IDs' % who)
    effects = spec.get('effects', [])
    if not isinstance(effects, list) or len(effects) > 8 or not all(isinstance(e, dict) and e.get('id') for e in effects):
        raise SystemExit('scenario: %s effects is a list of at most 8 {"id", "text"}' % who)
    for key in ('damage', 'attack', 'armor', 'level'):
        if key in spec and not (isinstance(spec[key], int) and -1000 <= spec[key] <= 100000):
            raise SystemExit('scenario: %s %s is a whole number' % (who, key))


def validate(scenario):
    for key in ('modules', 'builds', 'boss', 'seeds', 'turn_limit', 'stall_turns', 'work_limit'):
        if key not in scenario:
            raise SystemExit('scenario: needs ' + key)
    if not (1 <= len(scenario['seeds']) <= 64):
        raise SystemExit('scenario: 1 to 64 seeds')
    if not (1 <= scenario['turn_limit'] <= 1000 and 1 <= scenario['stall_turns'] <= 100 and 1 <= scenario['work_limit'] <= 100000):
        raise SystemExit('scenario: turn_limit 1..1000, stall_turns 1..100, work_limit 1..100000')
    if not (1 <= len(scenario['builds']) <= 8):
        raise SystemExit('scenario: 1 to 8 builds')
    boss = scenario['boss']
    acts = boss.get('acts', False)
    if not isinstance(acts, bool):
        raise SystemExit('scenario: boss acts is true or false')
    for build in scenario['builds']:
        if not build.get('skills'):
            raise SystemExit('scenario: build %s names no skill' % build.get('name'))
        check_skills(build['skills'], 'build %s' % build.get('name'))
        check_extras(build, 'build %s' % build.get('name'))
        if 'hp' in build and not (isinstance(build['hp'], int) and 1 <= build['hp'] <= 1000000):
            raise SystemExit('scenario: build %s hp is 1 to 1000000' % build.get('name'))
        if acts and 'hp' not in build:
            raise SystemExit('scenario: build %s needs hp when the boss acts' % build.get('name'))
    if 'skills' in boss:
        if not acts:
            raise SystemExit('scenario: boss skills need "acts": true')
        check_skills(boss['skills'], 'the boss')
    check_extras(boss, 'the boss')
    traits = boss.get('traits', [])
    if not isinstance(traits, list) or len(traits) > 8 or not all(isinstance(t, str) for t in traits):
        raise SystemExit('scenario: boss traits is a list of at most 8 trait IDs')
    allies = scenario.get('allies', [])
    if allies and not acts:
        raise SystemExit('scenario: allies fight only beside a caster whose boss acts ("acts": true)')
    if not isinstance(allies, list) or len(allies) > 6:
        raise SystemExit('scenario: at most 6 allies')
    for a in allies:
        if not (isinstance(a, dict) and a.get('name') and isinstance(a.get('hp'), int) and 1 <= a['hp'] <= 1000000):
            raise SystemExit('scenario: each ally has a name and hp (1 to 1000000)')
        if 'role' in a and not isinstance(a['role'], str):
            raise SystemExit('scenario: ally %s role is a role ID from formations.ini' % a['name'])
        check_extras(dict(a, pools=[], effects=[]), 'ally %s' % a['name'])
    if 'formations' in scenario and not isinstance(scenario['formations'], str):
        raise SystemExit('scenario: formations is the text of formations.ini')
    if 'profile' in scenario and not (isinstance(scenario['profile'], dict) and scenario['profile'].get('id')):
        raise SystemExit('scenario: profile is {"id", "version"}')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('scenario')
    parser.add_argument('--seeds', help='comma-separated seeds instead of the scenario\'s')
    parser.add_argument('--json', help='also write the results as JSON here')
    parser.add_argument('--work', help='a scratch directory (kept); by default a temporary one, removed')
    parser.add_argument('--timeout', type=int, default=420, help='seconds each engine process may take')
    args = parser.parse_args()
    scenario = json.loads(Path(args.scenario).read_text())
    if args.seeds:
        scenario['seeds'] = [int(x) for x in args.seeds.split(',') if x.strip()]
    validate(scenario)
    scratch = Path(args.work).resolve() if args.work else Path(tempfile.mkdtemp(prefix='sud-sim-'))
    scratch.mkdir(parents=True, exist_ok=True)
    try:
        world = world_for(scenario, scratch)
        results = [run_build(scenario, build, world, scratch, args.timeout) for build in scenario['builds']]
        text, sums = report(scenario, results)
        print(text)
        if args.json:
            Path(args.json).write_text(json.dumps({'scenario': scenario, 'results': results, 'summary': sums}, indent=1, sort_keys=True))
    finally:
        if not args.work:
            shutil.rmtree(scratch, ignore_errors=True)
    return 0


if __name__ == '__main__':
    sys.exit(main())
