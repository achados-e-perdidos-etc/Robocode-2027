#!/usr/bin/env python3
"""Compara uma base com a instrumentacao e exporta telemetria por round em CSV."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import re
import defense
import samples
import train

CONTEXTS = {
    'diamond': ('duel', 'voidious.Diamond 1.8.28', 2),
    'trackfire': ('duel', 'sample.TrackFire', 2),
    'ramfire': ('duel', 'sample.RamFire', 2),
    'crowd': ('melee', samples.GROUPS['six'], 7),
    'team': ('team', 'sampleteam.MyFirstTeam', 6),
}
SEEDS = (3509, 3659, 3821)
INTEGER_FIELDS = ('v round decisions no_target scans gap_count gap_sum gap_max shots hits aim_age_sum aim_age_max '
                  'received body_matched body_unmatched unknown_hit_range').split()
FLOAT_FIELDS = ('spent dealt_nominal received_nominal bonus_nominal error_sum error_max confidence_sum').split()
ARRAY_FIELDS = ('shots_range', 'hits_range')


def ratio(numerator, denominator):
    return numerator / denominator if denominator else None


def parse_telemetry(line, rounds=3):
    pairs = re.findall(r'([a-z_]+)=([^\s]+)', line)
    fields = dict(pairs)
    expected = set(INTEGER_FIELDS + FLOAT_FIELDS + list(ARRAY_FIELDS))
    if len(fields) != len(pairs) or set(fields) != expected:
        raise ValueError('Missing, duplicate or unknown telemetry fields')
    row = {k: int(fields[k]) for k in INTEGER_FIELDS}
    row.update({k: float(fields[k]) for k in FLOAT_FIELDS})
    for k in ARRAY_FIELDS:
        row[k] = list(map(int, fields[k].split(',')))
        if len(row[k]) != 3 or any(x < 0 for x in row[k]): raise ValueError('Invalid range bins')
    if row['v'] != 1 or row['round'] not in range(rounds): raise ValueError('Unsupported schema or round')
    if any(row[k] < 0 for k in INTEGER_FIELDS + FLOAT_FIELDS): raise ValueError('Negative counter')
    if any(not math.isfinite(row[k]) for k in FLOAT_FIELDS): raise ValueError('Non-finite telemetry')
    if row['received'] != row['body_matched'] + row['body_unmatched']: raise ValueError('Body hit accounting')
    if sum(row['shots_range']) != row['shots']: raise ValueError('Shot range accounting')
    if sum(row['hits_range']) + row['unknown_hit_range'] != row['hits']: raise ValueError('Hit range accounting')
    if row['no_target'] > row['decisions'] or row['aim_age_max'] > 8: raise ValueError('Decision/scan age bounds')
    if row['gap_count'] > row['scans'] or row['confidence_sum'] > row['body_matched'] + 1e-8: raise ValueError('Observation bounds')
    if row['received_nominal'] > 16 * row['received'] + 1e-6: raise ValueError('Nominal damage bound')
    row.update({'scan_gap_mean': ratio(row['gap_sum'], row['gap_count']),
                'no_target_decision_fraction': ratio(row['no_target'], row['decisions']),
                'aim_age_mean': ratio(row['aim_age_sum'], row['shots']),
                'body_match_rate': ratio(row['body_matched'], row['received']),
                'matched_radial_error_mean': ratio(row['error_sum'], row['body_matched']),
                'matched_confidence_mean': ratio(row['confidence_sum'], row['body_matched'])})
    return row


def evaluate(args):
    previous = defense.CONTEXTS
    try:
        defense.CONTEXTS = CONTEXTS
        results = defense.evaluate(args, 'comparison', ['diagbase', 'diagnew'], SEEDS)
    finally:
        defense.CONTEXTS = previous
    text = (args.output / 'comparison.log').read_text()
    blocks = dict(re.findall(r'BEGIN\t([^\n]+)\n(.*?)END\t\1(?:\n|$)', text, re.S))
    for name, rows in results.items():
        for result in rows:
            lines = [s for s in blocks[result['key']].splitlines() if s.startswith('[treino.' + name + '*] TELEMETRY ')]
            if name == 'diagbase':
                if lines: raise ValueError('Baseline already contains telemetry; choose original source')
                continue
            values = [parse_telemetry(s) for s in lines]
            if sorted(v['round'] for v in values) != [0, 1, 2]: raise ValueError('Missing/duplicate telemetry round')
            for v in values:
                legacy = next(x for x in result['round_stats'] if x['round'] == v['round'])
                if v['shots'] != legacy['shots'] or v['hits'] != legacy['hits']: raise ValueError('Legacy/new counters disagree')
            result['telemetry'] = values
    return results


def summarize(rows):
    output = {}
    for context in CONTEXTS:
        matches = [r for r in rows if r['context'] == context]
        records = [v for r in matches for v in r['telemetry']]
        totals = {k: sum(v[k] for v in records) for k in ('decisions','no_target','scans','gap_count','gap_sum','shots','hits',
                  'aim_age_sum','received','body_matched','body_unmatched','spent','dealt_nominal','received_nominal','error_sum','confidence_sum')}
        totals.update({'scan_gap_mean': ratio(totals['gap_sum'], totals['gap_count']),
                       'no_target_decision_fraction': ratio(totals['no_target'], totals['decisions']),
                       'aim_age_mean': ratio(totals['aim_age_sum'], totals['shots']),
                       'body_match_rate': ratio(totals['body_matched'], totals['received']),
                       'matched_radial_error_mean': ratio(totals['error_sum'], totals['body_matched']),
                       'matched_confidence_mean': ratio(totals['confidence_sum'], totals['body_matched'])})
        output[context] = totals
    return output


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--home',required=True,type=Path);p.add_argument('--ecj',type=Path)
    p.add_argument('--baseline',required=True,type=Path)
    p.add_argument('--source',type=Path,default=train.ROOT/'robots/equipe/titanprime.java')
    p.add_argument('--output',required=True,type=Path);p.add_argument('--parse-only',action='store_true')
    a=p.parse_args()
    for k in ('home','ecj','baseline','source','output'):
        if getattr(a,k) is not None:setattr(a,k,getattr(a,k).resolve())
    a.output.mkdir(parents=True,exist_ok=True)
    sources={'diagbase':a.baseline.read_text(),'diagnew':a.source.read_text()}
    plan={'source_sha256':{n:hashlib.sha256(s.encode()).hexdigest() for n,s in sources.items()},
          'contexts':CONTEXTS,'seeds':SEEDS,'rounds_per_series':3,
          'acceptance':'No series regression by context; at most three extra skipped turns. Instrumentation is not a combat improvement.',
          'scope':'Counters describe delivered combat events before the first round report; nominal damage is not actual energy lost.'}
    if a.parse_only:
        if json.loads((a.output/'plan.json').read_text())!=json.loads(json.dumps(plan)): raise ValueError('Original sources and plan required')
    else:
        (a.output/'plan.json').write_text(json.dumps(plan,indent=2)+'\n')
        runner=a.output/'samplerunner.java';runner.write_text(samples.RUNNER);train.compile_java(a,[runner],a.output)
        for n,s in sources.items():defense.install(a,n,s)
    print('PLAN',plan['acceptance'],flush=True)
    results=evaluate(a)
    base,new=results['diagbase'],results['diagnew']
    checks={'series_preserved_by_context':all(sum(r['series_win'] for r in new if r['context']==c)>=sum(r['series_win'] for r in base if r['context']==c) for c in CONTEXTS),
            'at_most_three_extra_skips':sum(r['skipped'] for r in new)<=sum(r['skipped'] for r in base)+3}
    report={**plan,'engine':a.engine_version,'results':results,'summary':summarize(new),'checks':checks,'accepted':all(checks.values()),
            'ranking':{n:defense.rank(r) for n,r in results.items()}}
    (a.output/'results.json').write_text(json.dumps(report,indent=2)+'\n')
    csv_rows=[]
    for r in new:
        for v in r['telemetry']:
            line={'scenario':r['context'],'seed':r['seed'],**v}
            for k in ARRAY_FIELDS:
                values=line.pop(k)
                for i,value in enumerate(values):line[k+'_'+str(i)]=value
            csv_rows.append(line)
    with (a.output/'rounds.csv').open('w',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=list(csv_rows[0]),lineterminator='\n');writer.writeheader();writer.writerows(csv_rows)
    print('RESULT',report['ranking'],'ACCEPTED',report['accepted'],checks,flush=True)
    print('DIAMOND',json.dumps(report['summary']['diamond']),flush=True)


if __name__=='__main__':main()
