#!/usr/bin/env python3
"""Compara defesa retangular e KNN em series independentes de tres rounds.

Oponente adicional: reference.supermercutio*, compilado separadamente.
A ferramenta propoe uma candidata; nao altera o Java principal nem publica.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import bo3
import samples
import train

CONTEXTS = {
    'diamond': ('duel', 'voidious.Diamond 1.8.28', 2),
    'supermercutio': ('duel', 'reference.supermercutio*', 2),
    'trackfire': ('duel', 'sample.TrackFire', 2),
    'veloci': ('duel', 'sample.VelociRobot', 2),
    'crowd': ('melee', samples.GROUPS['six'], 7),
    'team': ('team', 'sampleteam.MyFirstTeam', 6),
}
PROFILES = {'defbase': None, 'defrect': (True, False),
            'defcontext': (False, True), 'defboth': (True, True)}
SELECTION = (811, 877)
VALIDATION = (1009, 1151, 1297)


def prepare_template(source):
    if 'static final boolean USE_RECT_SURF' in source:
        return source
    patch = json.loads(Path(__file__).with_name('defense-prototype.json').read_text())
    accepted = [patch['baseline_sha256']] + patch.get('compatible_source_sha256', [])
    if hashlib.sha256(source.encode()).hexdigest() not in accepted:
        raise ValueError('Defense prototype does not match this robot version')
    for change in patch['changes']:
        if source.count(change['old']) != 1:
            raise ValueError('Ambiguous defense patch')
        source = source.replace(change['old'], change['new'], 1)
    return source


def profile(source, flags):
    if flags is None:
        return source
    text = prepare_template(source)
    for key, value in zip(('RECT', 'CONTEXT'), flags):
        text, count = re.subn('USE_' + key + '_SURF = (?:true|false)',
                              'USE_' + key + '_SURF = ' + str(value).lower(), text)
        if count != 1:
            raise ValueError('Missing defense flag ' + key)
    return text


def rank(rows):
    return (sum(r['series_win'] for r in rows), sum(r['first_round_win'] for r in rows),
            sum(r['wins'] for r in rows), sum(r['score_share'] for r in rows) / len(rows),
            -sum(r['skipped'] for r in rows))


def promotion_checks(base, candidate):
    contexts = {r['context'] for r in base}
    if contexts != {r['context'] for r in candidate} or len(base) != len(candidate):
        raise ValueError('Comparison scenarios differ')
    return {
        'more_series_won': sum(r['series_win'] for r in candidate) > sum(r['series_win'] for r in base),
        'no_context_series_regression': all(
            sum(r['series_win'] for r in candidate if r['context'] == key)
            >= sum(r['series_win'] for r in base if r['context'] == key) for key in contexts),
        'first_rounds_preserved': sum(r['first_round_win'] for r in candidate)
            >= sum(r['first_round_win'] for r in base),
        'at_most_three_extra_skips': sum(r['skipped'] for r in candidate)
            <= sum(r['skipped'] for r in base) + 3,
    }


def install(args, name, source):
    instrumented = source.replace('shots++;', 'shots++; out.println("AUDIT_SHOT " + e.name);', 1)
    if instrumented == source:
        raise ValueError('Shot audit anchor missing')
    bo3.install(args, name, instrumented)


def evaluate(args, label, names, seeds):
    cases = []
    for name in names:
        for context, (category, opponents, physical) in CONTEXTS.items():
            for seed in seeds:
                cases.append({'key': f'{name}-{context}-{seed}', 'name': name, 'context': context,
                              'category': category, 'opponents': opponents, 'physical_count': physical, 'seed': seed})
    suite = args.output / (label + '.suite')
    suite_text = '\n'.join(r['key'] + '|treino.' + r['name'] + '*,' + r['opponents'] + '|3|' +
                           samples.positions(r) for r in cases) + '\n'
    log = args.output / (label + '.log')
    if args.parse_only:
        if suite.read_text() != suite_text:
            raise ValueError('Saved suite differs from requested scenarios')
    else:
        suite.write_text(suite_text)
        with log.open('w', encoding='utf-8') as stream:
            subprocess.run(['java', '-Xmx768m', '-Djava.awt.headless=true', *train.JAVA_OPTIONS, '-cp',
                            str(args.output) + os.pathsep + str(args.home / 'libs/*'),
                            'samplerunner', str(args.home), str(suite)], cwd=args.home,
                           stdout=stream, stderr=subprocess.STDOUT, timeout=3600, check=True)
    text = log.read_text(encoding='utf-8')
    versions = re.findall(r'^VERSION\t([^\n]+)', text, re.M)
    if len(versions) != 1 or (getattr(args, 'engine_version', None) not in (None, versions[0])):
        raise ValueError('Missing engine version or mixed engine versions')
    args.engine_version = versions[0]
    blocks = dict(re.findall(r'BEGIN\t([^\n]+)\n(.*?)END\t\1(?:\n|$)', text, re.S))
    if set(blocks) != {r['key'] for r in cases}:
        raise ValueError('Incomplete defense suite; inspect ' + str(log))
    results = {name: [] for name in names}
    for case in cases:
        row = samples.parse_block(blocks[case['key']], case, case['name'])
        if row['status'] != 'complete' or not row['telemetry_complete']:
            raise ValueError('Invalid defense series ' + case['key'])
        results[case['name']].append(row)
    if len({tuple(r['cold_start']) for rows in results.values() for r in rows}) != 1:
        raise ValueError('Knowledge leaked across independent series')
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home', required=True, type=Path)
    parser.add_argument('--ecj', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--source', type=Path, default=train.ROOT / 'robots/equipe/titanprime.java')
    parser.add_argument('--parse-only', action='store_true', help='Reprocessa os logs existentes sem executar batalhas')
    parser.add_argument('--supermercutio', type=Path, help='Fonte original opcional, instalado so nesta copia de testes')
    args = parser.parse_args()
    for key in ('home', 'ecj', 'output', 'supermercutio', 'source'):
        if getattr(args, key) is not None:
            setattr(args, key, getattr(args, key).resolve())
    args.output.mkdir(parents=True, exist_ok=True)
    opponent = None
    if args.supermercutio:
        original = args.supermercutio.read_text(encoding='utf-8')
        changed = original.replace('package sample;', 'package reference;', 1).replace('SuperMercutio', 'supermercutio')
        path = args.home / 'robots/reference/supermercutio.java'
        if not args.parse_only:
            path.parent.mkdir(parents=True, exist_ok=True); path.write_text(changed, encoding='utf-8')
            train.compile_java(args, [path], args.home / 'robots')
        opponent = {'original_sha256': hashlib.sha256(original.encode()).hexdigest(),
                    'installed_sha256': hashlib.sha256(changed.encode()).hexdigest(),
                    'changes': 'package/class rename only; strategy unchanged'}
    if not args.parse_only:
        runner = args.output / 'samplerunner.java'; runner.write_text(samples.RUNNER, encoding='utf-8')
        train.compile_java(args, [runner], args.output)
    source = args.source.read_text(encoding='utf-8')
    sources = {name: profile(source, flags) for name, flags in PROFILES.items()}
    plan = {'source_sha256': hashlib.sha256(source.encode()).hexdigest(),
            'variant_sha256': {n: hashlib.sha256(s.encode()).hexdigest() for n, s in sources.items()},
            'rounds_per_series': 3, 'profiles': PROFILES, 'contexts': CONTEXTS,
            'selection_seeds': SELECTION, 'validation_seeds': VALIDATION,
            'opponent_source': opponent,
            'promotion_rule': 'More validation series won; no context regression in series; preserve first-round wins; at most 3 extra skipped turns; then sample regression suite before adopting.'}
    if args.parse_only:
        recorded = json.loads((args.output / 'plan.json').read_text())
        if (recorded['source_sha256'] != plan['source_sha256']
                or recorded.get('variant_sha256') != plan['variant_sha256']):
            raise ValueError('Use the original source with --source to reprocess these logs')
    else:
        (args.output / 'plan.json').write_text(json.dumps(plan, indent=2) + '\n')
    print('PLAN', plan['promotion_rule'], flush=True)
    if not args.parse_only:
        for name, text in sources.items():
            install(args, name, text)
    selection = evaluate(args, 'selection', list(sources), SELECTION)
    selected = max((n for n in sources if n != 'defbase'), key=lambda n: rank(selection[n]))
    progress = {**plan, 'engine': args.engine_version, 'selection': selection, 'selected_experiment': selected,
                'selection_rank': {n: rank(rows) for n, rows in selection.items()}}
    (args.output / 'progress.json').write_text(json.dumps(progress, indent=2) + '\n')
    print('SELECTED', selected, progress['selection_rank'], flush=True)
    validation = evaluate(args, 'validation', ['defbase', selected], VALIDATION)
    checks = promotion_checks(validation['defbase'], validation[selected])
    checks['selection_series_preserved'] = rank(selection[selected])[0] >= rank(selection['defbase'])[0]
    report = {**progress, 'validation': validation, 'promotion_checks': checks,
              'validation_rank': {n: rank(rows) for n, rows in validation.items()},
              'candidate_sha256': hashlib.sha256(sources[selected].encode()).hexdigest(),
              'proposed_promotion': all(checks.values())}
    (args.output / 'candidate.java.txt').write_text(sources[selected], encoding='utf-8')
    (args.output / 'results.json').write_text(json.dumps(report, indent=2) + '\n')
    print('VALIDATION', report['validation_rank'], 'PROPOSED_PROMOTION', report['proposed_promotion'], checks, flush=True)


if __name__ == '__main__':
    main()
