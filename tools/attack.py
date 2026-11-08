#!/usr/bin/env python3
"""Avalia KNN de mira e visitas virtuais sem substituir o robo automaticamente."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import bo3
import defense
import samples
import train

CONTEXTS = {
    'diamond': ('duel', 'voidious.Diamond 1.8.28', 2),
    'mirror': ('duel', 'treino.attackanchor*', 2),
    'supermercutio': ('duel', 'reference.supermercutio*', 2),
    'veloci': ('duel', 'sample.VelociRobot', 2),
    'crowd': ('melee', samples.GROUPS['six'], 7),
    'team': ('team', 'sampleteam.MyFirstTeam', 6),
}
PROFILES = {'atkbase': None, 'atkknn': (True, False),
            'atkvisits': (False, True), 'atkboth': (True, True)}
SELECTION = (1523, 1607)
VALIDATION = (1877, 1993, 2131)


def prepare_template(source):
    if 'static final boolean USE_KNN_GUN' in source:
        return source
    patch = json.loads(Path(__file__).with_name('attack-prototype.json').read_text())
    if hashlib.sha256(source.encode()).hexdigest() != patch['baseline_sha256']:
        raise ValueError('Attack prototype does not match the robot version')
    for c in patch['changes']:
        if source.count(c['old']) != 1:
            raise ValueError('Ambiguous attack patch')
        source = source.replace(c['old'], c['new'], 1)
    return source


def profile(source, flags):
    if flags is None:
        return source
    source = prepare_template(source)
    for key, value in zip(('KNN_GUN', 'VISIT_WAVES'), flags):
        source, count = re.subn('USE_' + key + ' = (?:true|false)',
                                'USE_' + key + ' = ' + str(value).lower(), source)
        if count != 1: raise ValueError('Missing attack flag')
    return source


def evaluate(args, label, names, seeds):
    previous = defense.CONTEXTS
    try:
        defense.CONTEXTS = CONTEXTS
        result = defense.evaluate(args, label, names, seeds)
    finally:
        defense.CONTEXTS = previous
    log = (args.output / (label + '.log')).read_text()
    blocks = dict(re.findall(r'BEGIN\t([^\n]+)\n(.*?)END\t\1(?:\n|$)', log, re.S))
    for name, rows in result.items():
        for row in rows:
            own_lines = [s for s in blocks[row['key']].splitlines() if s.startswith('[treino.' + name + '*]')]
            own = '\n'.join(own_lines)
            cold = re.findall(r'COLD_GUN_CONTEXT enemies=(\d+)', own)
            if cold != ['0']:
                raise ValueError('Enemy memory leaked across battles')
            counts = [list(map(int, s.split(','))) for s in re.findall(r'GUN_USAGE \[([^\]]+)\]', own)]
            if counts:
                if len(counts) != 3 or any(len(s) not in (7,10) or len(s) != len(counts[0]) for s in counts):
                    raise ValueError('Missing gun usage telemetry')
                total = [sum(s[i] for s in counts) for i in range(len(counts[0]))]
                if sum(total) != row['reported_shots']:
                    raise ValueError('Gun usage differs from actual shots')
                row['gun_usage'] = total
                row['knn_shots'] = total[5] + total[6]
            else:
                row['gun_usage'] = None; row['knn_shots'] = 0
            row['actual_hit_rate'] = row['hits'] / max(1, row['shots'])
    return result


def install(args, name, source):
    text = source.replace('public void run() {', 'public void run() {\n        if (getRoundNum() == 0) out.println("COLD_GUN_CONTEXT enemies=" + MEMORY.size());', 1)
    defense.install(args, name, text)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--home',required=True,type=Path);p.add_argument('--ecj',type=Path)
    p.add_argument('--output',required=True,type=Path)
    p.add_argument('--source',type=Path,default=train.ROOT/'robots/equipe/titanprime.java')
    p.add_argument('--parse-only',action='store_true')
    args=p.parse_args()
    for key in ('home','ecj','output','source'):
        if getattr(args,key) is not None:setattr(args,key,getattr(args,key).resolve())
    args.output.mkdir(parents=True,exist_ok=True)
    source=args.source.read_text();sources={n:profile(source,f) for n,f in PROFILES.items()}
    plan={'source_sha256':hashlib.sha256(source.encode()).hexdigest(),
          'variants_sha256':{n:hashlib.sha256(s.encode()).hexdigest() for n,s in sources.items()},
          'profiles':PROFILES,'contexts':CONTEXTS,'rounds_per_series':3,
          'selection_seeds':SELECTION,'validation_seeds':VALIDATION,
          'promotion_rule':'More validation series; no context series regression; preserve first-round wins; at most 3 extra skipped turns; full sample guards before adopting.'}
    if args.parse_only:
        old=json.loads((args.output/'plan.json').read_text())
        if old!=plan:raise ValueError('Original plan/source required for log replay')
    else:
        (args.output/'plan.json').write_text(json.dumps(plan,indent=2)+'\n')
        runner=args.output/'samplerunner.java';runner.write_text(samples.RUNNER)
        train.compile_java(args,[runner],args.output)
        bo3.install(args,'attackanchor',source)
        for name,s in sources.items():install(args,name,s)
    print('PLAN',plan['promotion_rule'],flush=True)
    selection=evaluate(args,'selection',list(sources),SELECTION)
    selected=max((n for n in sources if n!='atkbase'),key=lambda n:defense.rank(selection[n]))
    progress={**plan,'engine':args.engine_version,'selection':selection,
              'selected_experiment':selected,'selection_rank':{n:defense.rank(r) for n,r in selection.items()}}
    (args.output/'progress.json').write_text(json.dumps(progress,indent=2)+'\n')
    print('SELECTED',selected,progress['selection_rank'],flush=True)
    validation=evaluate(args,'validation',['atkbase',selected],VALIDATION)
    checks=defense.promotion_checks(validation['atkbase'],validation[selected])
    checks['selection_series_preserved']=defense.rank(selection[selected])[0]>=defense.rank(selection['atkbase'])[0]
    report={**progress,'validation':validation,
            'validation_rank':{n:defense.rank(r) for n,r in validation.items()},
            'promotion_checks':checks,'proposed_promotion':all(checks.values()),
            'candidate_sha256':hashlib.sha256(sources[selected].encode()).hexdigest()}
    (args.output/'candidate.java.txt').write_text(sources[selected])
    (args.output/'results.json').write_text(json.dumps(report,indent=2)+'\n')
    print('VALIDATION',report['validation_rank'],'PROPOSED_PROMOTION',report['proposed_promotion'],checks,flush=True)


if __name__=='__main__':main()
