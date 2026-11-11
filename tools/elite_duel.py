#!/usr/bin/env python3
"""Batalha auditavel: um Prime sozinho, com adversarios originais.

Cria instalacao isolada com libs/config do --home e os JARs adversarios.
Em --random-starts os numeros de --seeds identificam lotes, sem fixar PRNG.
"""
import argparse
import csv
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
from types import SimpleNamespace
import samples
import train

SEEDS = (31013, 31337, 31817)
ERROR_MARKERS = ('Exception', 'ERROR:', 'ERROR\t', 'Unable to stop thread',
                 'still has not started', 'No score will be generated',
                 'DIAGNOSTIC_ERROR', 'SYSTEM: Disabled', 'An error occurred',
                 'Error:', 'has been disabled', 'has been banned')


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def parse_result(log, rounds, expected_names, coordinates):
    text = Path(log).read_text(encoding='utf-8')
    scores = [dict(robot=n, points=int(p), firsts=int(w)) for n, p, w in
              re.findall(r'RESULT\t([^\t\n]+)\t(\d+)\t(\d+)', text)]
    errors = sorted(set(line for line in text.splitlines()
                        if any(marker in line for marker in ERROR_MARKERS)))
    turns = [(int(i), int(t)) for i, t in
             re.findall(r'ROUND_TURNS\t(\d+)\t(\d+)', text)]
    starts = [dict(round=int(i), robot=n, x=float(x), y=float(y), heading=float(h))
              for i, n, x, y, h in re.findall(
                  r'START_POSITION\t(\d+)\t([^\t\n]+)\t([^\t\n]+)\t([^\t\n]+)\t([^\n]+)', text)]
    roster = [dict(robot=n, team=team, contestant=int(c), team_index=int(t),
                   sentry=s == 'true', energy=float(e), state=state)
              for n, team, c, t, s, e, state in re.findall(
                  r'ROSTER\t([^\t\n]+)\t([^\t\n]+)\t(-?\d+)\t(-?\d+)\t(true|false)\t([^\t\n]+)\t([^\t\n]+)\t[^\t\n]+\t[^\n]+', text)]
    expected = {n: coordinates[i*3:i*3+3] for i, n in enumerate(expected_names)} if coordinates is not None else None
    keys = {(i, n) for i in range(rounds) for n in expected_names}
    positions_ok = len(starts) == len(keys) and {(s['round'], s['robot']) for s in starts} == keys
    for s in starts:
        if (s['robot'] not in expected_names or not
                all(math.isfinite(s[k]) for k in ('x','y','heading')) or not
                18 <= s['x'] <= 782 or not 18 <= s['y'] <= 582 or not
                0 <= s['heading'] <= math.tau + .000001):
            positions_ok = False
            continue
        if expected is not None:
            x, y, heading = expected[s['robot']]
            angle = (s['heading'] - math.radians(heading) + math.pi) % math.tau - math.pi
            positions_ok &= math.hypot(s['x'] - x, s['y'] - y) <= 1.01 and abs(angle) <= math.radians(10.01)
    for index in range(rounds):
        group=[s for s in starts if s['round']==index]
        for i,s in enumerate(group):
            for other in group[:i]:
                if abs(s['x']-other['x']) < 35.99 and abs(s['y']-other['y']) < 35.99:
                    positions_ok=False
    checks = {
        'completed': text.count('STATUS\tcomplete') == 1 and 'STATUS\terror' not in text,
        'no_engine_or_robot_error': not errors,
        'exact_roster': len(roster) == len(expected_names) and {r['robot'] for r in roster} == set(expected_names),
        'independent_active_robots': all(r['team_index'] == -1 and not r['sentry'] and r['energy'] == 100 and r['state'] == 'ACTIVE' for r in roster),
        'exact_results': len(scores) == len(expected_names) and {s['robot'] for s in scores} == set(expected_names),
        'all_rounds': len(turns) == rounds and {i for i, t in turns} == set(range(rounds)) and all(t > 0 for i, t in turns),
        'positions_verified': bool(positions_ok),
    }
    ours = next((s for s in scores if s['robot'] == 'equipe.titanprime*'), None)
    excess = max(0, sum(s['firsts'] for s in scores) - rounds)
    total = sum(s['points'] for s in scores)
    return dict(valid=all(checks.values()), checks=checks, errors=errors,
                scores=scores, official_firsts=ours['firsts'] if ours else None,
                conservative_wins=max(0, ours['firsts'] - excess) if ours else None,
                simultaneous_firsts_excess=excess,
                score_share=ours['points']/total if ours and total else None,
                round_turns=turns, start_positions=starts, roster=roster,
                engine=re.findall(r'VERSION\t([^\n]+)', text), log_sha256=digest(log))


def run(args):
    out = args.output.resolve()
    if out.exists():
        raise ValueError('Use uma pasta nova: resultados anteriores nunca sao sobrescritos')
    out.mkdir(parents=True)
    home = out/'engine'
    home.mkdir()
    for sub in ('libs', 'config'):
        shutil.copytree(args.home/sub, home/sub)
    (home/'robots/equipe').mkdir(parents=True)
    shutil.copyfile(args.opponent, home/'robots/opponent.jar')
    additional = []
    copied = {digest(args.opponent)}
    for index, (path, selector) in enumerate(getattr(args, 'extra_opponent', []), 2):
        jar = Path(path).resolve()
        jar_sha = digest(jar)
        if jar_sha not in copied:
            shutil.copyfile(jar, home/'robots'/('opponent-'+str(index)+'.jar'))
            copied.add(jar_sha)
        additional.append(dict(selection=selector, sha256=jar_sha))
    names = ['equipe.titanprime*', getattr(args, 'opponent_selector', 'voidious.Diamond 1.8.28')]
    names += [item['selection'] for item in additional]
    if len(names) != len(set(names)):
        raise ValueError('Use adversarios distintos; o Prime participa sozinho')
    source = home/'robots/equipe/titanprime.java'
    shutil.copyfile(args.source, source)
    seeds = tuple(int(s) for s in args.seeds.split(','))
    if not seeds or len(seeds) != len(set(seeds)):
        raise ValueError('Use sementes distintas')
    random_starts=getattr(args,'random_starts',False)
    cases = [dict(key='reserved-'+str(i+1), case_id=seed,
                  seed_initial_positions=None if random_starts else seed,
                  reversed_positions=False if random_starts else i % 2 == 1, rounds=args.rounds)
             for i, seed in enumerate(seeds)]
    plan = dict(source_sha256=digest(source), opponent_sha256=digest(args.opponent),
                opponent_selection=getattr(args,'opponent_selector','voidious.Diamond 1.8.28'),
                additional_opponents=additional, expected_roster=names,
                rules=dict(width=800, height=600, cooling=.1, inactivity=450, energy=100),
                seeds_scope=('Case IDs only. Standard engine random starts each round; no PRNG seed is set.' if random_starts else
                             'initial positions only; robot PRNGs are not seeded'),
                random_starts_each_round=random_starts,
                config_sha256={str(p.relative_to(home)): digest(p) for p in sorted((home/'config').rglob('*')) if p.is_file()},
                libraries_sha256={p.name: digest(p) for p in sorted((home/'libs').glob('*.jar'))},
                java=subprocess.run(['java', '-version'], text=True, capture_output=True, check=True).stderr.strip(),
                cases=cases, minimum_wins=args.minimum_wins,
                require_score_lead=getattr(args,'require_score_lead',False),
                acceptance='Every predeclared series must be valid and reach the minimum of conservative wins; no retries of valid losses',
                data_policy='Fresh JVM per battle; prior data archived before next battle; no imported learning')
    (out/'plan.json').write_text(json.dumps(plan, indent=2)+'\n')
    compiler = SimpleNamespace(home=home, ecj=args.ecj)
    train.compile_java(compiler, [source], home/'robots')
    runner = out/'eliterunner.java'
    runner_source=samples.RUNNER.replace('samplerunner', 'eliterunner').replace(
        'new BattlefieldSpecification(800,600),3,450',
        'new BattlefieldSpecification(800,600),Integer.parseInt(a[2]),450')
    if random_starts:
        start=runner_source.index('   String[] coordinates=a[3].split(",");')
        end=runner_source.index('   engine.runBattle(spec,true);',start)
        runner_source=runner_source[:start]+'''   listener.setups=null;
   BattleSpecification spec=new BattleSpecification(new BattlefieldSpecification(800,600),Integer.parseInt(a[2]),450,0.1,100,false,bots);
'''+runner_source[end:]
        runner_source=runner_source.replace('RobotSetup s=setups[r.getRobotIndex()];','RobotSetup s=setups==null?null:setups[r.getRobotIndex()];')
        runner_source=runner_source.replace('if(Math.hypot(r.getX()-s.getX(),r.getY()-s.getY())>1.01){','if(s!=null && Math.hypot(r.getX()-s.getX(),r.getY()-s.getY())>1.01){')
        runner_source=runner_source.replace('robot_setup_verified','random_starts_recorded')
    runner.write_text(runner_source)
    train.compile_java(compiler, [runner], out)
    results = []
    records = []
    for case in cases:
        key = case['key']
        for p in list((home/'robots').rglob('*.data')):
            if not p.is_dir() or any(q.name.endswith('.data') for q in p.relative_to(home/'robots').parents):
                continue
            dest = out/'archived-data'/key/p.relative_to(home/'robots')
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(p), dest)
        coords = None if random_starts else list(map(float, train.positions(len(names), case['seed_initial_positions']).split(',')))
        if case['reversed_positions']:
            coords = [v for i in reversed(range(len(names))) for v in coords[i*3:i*3+3]]
        suite = out/(key+'.suite')
        suite.write_text('|'.join((key, ','.join(names), str(args.rounds), 'random' if random_starts else ','.join(map(str, coords))))+'\n')
        log = out/(key+'.log')
        command = ['java', '-Xmx768m', '-Djava.awt.headless=true', *train.JAVA_OPTIONS,
                   '-cp', str(out)+os.pathsep+str(home/'libs/*'), 'eliterunner', str(home), str(suite)]
        with log.open('w') as f:
            subprocess.run(command, cwd=home, stdout=f, stderr=subprocess.STDOUT, check=True, timeout=1800)
        result = dict(**case, **parse_result(log, args.rounds, names, coords),
                      source_sha256=plan['source_sha256'], opponent_sha256=plan['opponent_sha256'])
        diagnostic = home/'robots/equipe/titanprime.data/diagnostico.csv'
        if diagnostic.exists():
            rows = list(csv.DictReader(diagnostic.open()))
            records += [dict(case=key, **r) for r in rows]
            result['diagnostic_records'] = len(rows)
            result['diagnostic_wins'] = sum(int(r['won']) for r in rows)
            result['diagnostic_complete'] = len(rows) == args.rounds and len({r['round'] for r in rows}) == args.rounds
            # Simultaneous deaths can award both robots a first without sending WinEvent.
            # Counted WinEvents must lie between the conservative bound and official firsts.
            if (not result['diagnostic_complete'] or not
                    result['conservative_wins'] <= result['diagnostic_wins'] <= result['official_firsts']):
                result['valid'] = False
                result['errors'].append('Diagnostic round/win accounting disagrees with engine')
        else:
            result['valid'] = False
            result['errors'].append('Missing diagnostic CSV')
        own_score=next((s['points'] for s in result['scores'] if s['robot']=='equipe.titanprime*'),0)
        other_score=max((s['points'] for s in result['scores'] if s['robot']!='equipe.titanprime*'),default=0)
        result['score_lead']=own_score > other_score
        result['accepted'] = (result['valid'] and result['conservative_wins'] >= args.minimum_wins
                              and (not getattr(args,'require_score_lead',False) or result['score_lead']))
        (out/(key+'.json')).write_text(json.dumps(result, indent=2)+'\n')
        results.append(result)
        report = dict(plan=plan, results=results,
                      complete=len(results) == len(cases),
                      accepted=len(results) == len(cases) and all(r['accepted'] for r in results))
        (out/'results.json').write_text(json.dumps(report, indent=2)+'\n')
        if records:
            with (out/'rounds.csv').open('w') as f:
                writer = csv.DictWriter(f, fieldnames=list(records[0]))
                writer.writeheader()
                writer.writerows(records)
        print(json.dumps({k: result[k] for k in ('key', 'valid', 'conservative_wins', 'score_share', 'accepted', 'errors')}), flush=True)
    return report


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--home', required=True, type=Path)
    p.add_argument('--opponent', required=True, type=Path)
    p.add_argument('--opponent-selector', default='voidious.Diamond 1.8.28')
    p.add_argument('--extra-opponent', action='append', nargs=2, default=[],
                   metavar=('JAR', 'SELECTOR'), help='Adiciona um adversario independente para Melee; pode repetir')
    p.add_argument('--source', type=Path, default=train.ROOT/'robots/equipe/titanprime.java')
    p.add_argument('--ecj', type=Path)
    p.add_argument('--output', required=True, type=Path)
    p.add_argument('--seeds', default=','.join(map(str, SEEDS)))
    p.add_argument('--random-starts', action='store_true', help='Largadas normais aleatorias por round; --seeds identifica lotes, sem fixar PRNG')
    p.add_argument('--rounds', default=30, type=int)
    p.add_argument('--minimum-wins', default=None, type=int,
                   help='Minimo de vitorias conservadoras; por padrao exige todos os rounds')
    p.add_argument('--require-score-lead', action='store_true', default=True,
                   help='Exige lideranca em pontos (padrao)')
    p.add_argument('--allow-score-trail', dest='require_score_lead', action='store_false',
                   help='Somente exploracao: registra resultados mesmo sem lideranca em pontos')
    args = p.parse_args()
    if args.minimum_wins is None:
        args.minimum_wins = args.rounds
    if args.rounds < 1 or not 0 <= args.minimum_wins <= args.rounds:
        p.error('Rounds e minimo de vitorias invalidos')
    for name in ('home', 'opponent', 'source', 'ecj'):
        value = getattr(args, name)
        if value is not None:
            setattr(args, name, value.resolve())
    report = run(args)
    raise SystemExit(0 if report['accepted'] else 1)


if __name__ == '__main__':
    main()
