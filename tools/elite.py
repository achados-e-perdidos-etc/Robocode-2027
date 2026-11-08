#!/usr/bin/env python3
"""Avalia a versao atual contra referencias fortes, sem treino ou promocao automatica."""
import argparse
import hashlib
import json
from pathlib import Path
import attack
import defense
import samples
import train

CONTEXTS = {
    'diamond': ('duel', 'voidious.Diamond 1.8.28', 2),
    'supermercutio': ('duel', 'reference.supermercutio*', 2),
    'strong_melee': ('melee', 'voidious.Diamond 1.8.28,reference.supermercutio*', 3),
}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--home', required=True, type=Path)
    p.add_argument('--ecj', type=Path)
    p.add_argument('--output', required=True, type=Path)
    p.add_argument('--source', type=Path, default=train.ROOT/'robots/equipe/titanprime.java')
    p.add_argument('--seeds', default='5801,6007,6211')
    p.add_argument('--parse-only', action='store_true')
    a = p.parse_args()
    for key in ('home', 'ecj', 'output', 'source'):
        if getattr(a, key) is not None:
            setattr(a, key, getattr(a, key).resolve())
    seeds = tuple(map(int, a.seeds.split(',')))
    if not seeds or len(set(seeds)) != len(seeds):
        p.error('Use distinct integer seeds')
    a.output.mkdir(parents=True, exist_ok=True)
    source = a.source.read_text()
    plan = {'source_sha256': hashlib.sha256(source.encode()).hexdigest(),
            'seeds': seeds, 'contexts': CONTEXTS, 'rounds': 3,
            'objective': 'At least two round wins; diagnostic only; no strategy selection',
            'melee_note': 'Titan alone; opponents also fight each other, not a coordinated team'}
    plan_path = a.output/'plan.json'
    if a.parse_only:
        if json.loads(plan_path.read_text()) != json.loads(json.dumps(plan)):
            raise ValueError('Source or protocol changed')
    else:
        plan_path.write_text(json.dumps(plan, indent=2)+'\n')
        runner = a.output/'samplerunner.java'
        runner.write_text(samples.RUNNER)
        train.compile_java(a, [runner], a.output)
        attack.install(a, 'eliteaudit', source)
    previous = defense.CONTEXTS
    try:
        defense.CONTEXTS = CONTEXTS
        results = defense.evaluate(a, 'elite', ['eliteaudit'], seeds)['eliteaudit']
    finally:
        defense.CONTEXTS = previous
    summary = {c: {'series_wins': sum(r['series_win'] for r in results if r['context'] == c),
                   'series': len(seeds),
                   'round_wins': sum(r['wins'] for r in results if r['context'] == c),
                   'rounds': 3*len(seeds),
                   'skipped': sum(r['skipped'] for r in results if r['context'] == c)}
               for c in CONTEXTS}
    report = {**plan, 'engine': a.engine_version, 'summary': summary, 'series': results}
    (a.output/'elite-results.json').write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(summary, indent=2), flush=True)


if __name__ == '__main__':
    main()
