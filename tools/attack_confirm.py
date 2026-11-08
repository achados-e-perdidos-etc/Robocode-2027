#!/usr/bin/env python3
"""Confirmacao adicional do ataque congelado, priorizando vitorias de series."""
import argparse
import hashlib
import json
from pathlib import Path
import attack
import bo3
import defense
import samples
import train

SEEDS = (2393, 2539, 2671, 2801, 2957)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--home',required=True,type=Path);p.add_argument('--ecj',type=Path)
    p.add_argument('--source',required=True,type=Path);p.add_argument('--candidate',required=True,type=Path)
    p.add_argument('--output',required=True,type=Path);p.add_argument('--parse-only',action='store_true')
    a=p.parse_args()
    for k in ('home','ecj','source','candidate','output'):
        if getattr(a,k) is not None:setattr(a,k,getattr(a,k).resolve())
    a.output.mkdir(parents=True,exist_ok=True)
    sources={'confirmbase':a.source.read_text(),'confirmcandidate':a.candidate.read_text()}
    plan={'protocol':'Additional independent test planned after initial validation; candidate frozen, first-round wins reported as secondary metric.',
          'source_sha256':{n:hashlib.sha256(s.encode()).hexdigest() for n,s in sources.items()},
          'seeds':SEEDS,'contexts':attack.CONTEXTS,'rounds_per_series':3,
          'promotion_rule':'More BO3 series won, no context regression in series, at most three extra skipped turns; then full sample guards: 63/63 competitive series and at least 188/189 rounds. First-round wins are reported, not a veto.'}
    if a.parse_only:
        if json.loads((a.output/'plan.json').read_text())!=json.loads(json.dumps(plan)):raise ValueError('Original protocol required')
    else:
        (a.output/'plan.json').write_text(json.dumps(plan,indent=2)+'\n')
        runner=a.output/'samplerunner.java';runner.write_text(samples.RUNNER);train.compile_java(a,[runner],a.output)
        bo3.install(a,'attackanchor',sources['confirmbase'])
        for n,s in sources.items():attack.install(a,n,s)
    print('CONFIRM PLAN',plan['promotion_rule'],flush=True)
    rows=attack.evaluate(a,'confirmation',list(sources),SEEDS)
    checks=defense.promotion_checks(rows['confirmbase'],rows['confirmcandidate'])
    firsts=checks.pop('first_rounds_preserved')
    report={**plan,'engine':a.engine_version,'results':rows,'checks':checks,
            'first_rounds_preserved_secondary':firsts,'proposed_promotion':all(checks.values()),
            'ranking':{n:defense.rank(r) for n,r in rows.items()}}
    (a.output/'results.json').write_text(json.dumps(report,indent=2)+'\n')
    print('CONFIRM RESULT',report['ranking'],'PROPOSED_PROMOTION',report['proposed_promotion'],checks,flush=True)


if __name__=='__main__':main()
