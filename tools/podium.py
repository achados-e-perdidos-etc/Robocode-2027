#!/usr/bin/env python3
"""Compara oportunidades de ataque e rotas curtas com pontos e vitorias reais."""
import argparse, hashlib, json, os, re, shutil, subprocess
from pathlib import Path
import attack, four, pair, samples, train

PROFILES = {'base': None, 'opportunity': (1, 0), 'routes': (0, 1), 'both': (1, 1)}
CONTEXTS = {
    'all': (','.join(pair.ROBOTS.values()), 8),
    'leaders': (','.join(pair.ROBOTS[k] for k in ('titanv2', 'titans', 'obrabo')), 4),
    'elite': ('voidious.Diamond 1.8.28,reference.supermercutio*', 3),
    'diamond': ('voidious.Diamond 1.8.28', 2),
    'titanv2': (pair.ROBOTS['titanv2'], 2),
}

def profile(source, name):
    if name == 'base': return source
    patch = json.loads(Path(__file__).with_name('podium-prototype.json').read_text())
    if four.sha(source) != patch['baseline_sha256']: raise ValueError('Original Prime 3.9 required')
    for change in patch['changes']:
        if source.count(change['old']) != 1: raise ValueError('Ambiguous patch')
        source = source.replace(change['old'], change['new'], 1)
    for key, value in zip(('OPPORTUNITY', 'SHORT_ROUTES'), PROFILES[name]):
        source, count = re.subn('USE_'+key+' = (?:true|false)', 'USE_'+key+' = '+str(bool(value)).lower(), source)
        if count != 1: raise ValueError('Missing feature flag')
    return source

def official(row):
    n, count = row['rounds'], row['physical_count']
    result = next((s for s in row['scores'] if s['robot'] == 'treino.four'+row['profile']+'*'), None)
    row['outcome_valid'] = 'battle_not_completed' not in row['validation_reasons'] and not row['errors'] and result is not None and row['engine'] == ['1.11.1'] and len(row['scores']) == count and set(map(int, row['round_turns'])) == set(range(n)) and len({(s['round'],s['robot']) for s in row['start_positions']}) == n*count
    row['official_wins'] = result['wins'] if row['outcome_valid'] else None
    row['points'] = result['score'] if row['outcome_valid'] else None
    row['score_share'] = result['score']/max(1,sum(s['score'] for s in row['scores'])) if row['outcome_valid'] else None
    row['score_first'] = result['score'] == max(s['score'] for s in row['scores']) if row['outcome_valid'] else None
    return row

def summary(rows):
    if not rows or not all(r['outcome_valid'] for r in rows): return {'valid': False}
    contexts = sorted({r['context'] for r in rows})
    by = {}
    for c in contexts:
        group = [r for r in rows if r['context']==c]
        by[c] = {'wins':sum(r['official_wins'] for r in group), 'rounds':sum(r['rounds'] for r in group), 'points':sum(r['points'] for r in group), 'score_share':sum(r['score_share'] for r in group)/len(group), 'score_firsts':sum(r['score_first'] for r in group), 'battles':len(group)}
    win = sum(v['wins']/v['rounds'] for v in by.values())/len(by)
    share = sum(v['score_share'] for v in by.values())/len(by)
    return {'valid':True,'wins':sum(v['wins'] for v in by.values()),'rounds':sum(v['rounds'] for v in by.values()),'points':sum(v['points'] for v in by.values()),'macro_win_rate':win,'macro_score_share':share,'utility':(win+share)/2,'skipped':sum(r['skipped'] for r in rows),'contexts':by}

def phase(a,label,names,contexts,seeds,rounds):
    results, attempts = [], []
    for ci,context in enumerate(contexts):
        for si,seed in enumerate(seeds):
            offset=(ci+si)%len(names); ordered=names[offset:]+names[:offset]
            cases=[{'key':f'{label}-{name}-{context}-{seed}','profile':name,'context':context,'seed':seed,'opponents':CONTEXTS[context][0],'physical_count':CONTEXTS[context][1],'rounds':rounds} for name in ordered]
            group=[]
            for attempt in (0,1):
                group=[]
                for case in cases:
                    row=official(four.run_case(a,case,attempt));group.append(row);attempts.append(row)
                    print(case['key'],attempt,'VALID',row['outcome_valid'],'WINS',row['official_wins'],'POINTS',row['points'],'TELEMETRY',len(row['telemetry']),flush=True)
                if all(r['outcome_valid'] for r in group): break
            results.extend(group)
            (a.output/(label+'.json')).write_text(json.dumps({'results':results,'attempts':attempts},indent=2)+'\n')
    return {'results':results,'attempts':attempts}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--home',required=True,type=Path);p.add_argument('--ecj',required=True,type=Path);p.add_argument('--output',required=True,type=Path)
    p.add_argument('--source',type=Path,default=train.ROOT/'robots/equipe/titanprime.java')
    a=p.parse_args()
    for k in ('home','ecj','output','source'):setattr(a,k,getattr(a,k).resolve())
    if a.output.exists():p.error('Use a new output directory')
    a.output.mkdir(parents=True); source=a.source.read_text(); sources={n:profile(source,n) for n in PROFILES}
    plan={'baseline_sha256':four.sha(source),'variants_sha256':{n:four.sha(s) for n,s in sources.items()},'contexts':CONTEXTS,'selection_seeds':[15101,15427],'validation_seeds':[16001,16411],'selection_rounds':8,'validation_rounds':12,'metric':'Equal weight to macro round-win rate and macro score share across contexts. Every round equal. Report absolute points and first place by score.','promotion':'At least four more reserved round wins; macro score share at least 2% relatively higher; all official outcomes complete; all-arena wins and score share not lower; no more than three extra skipped turns; at least 90% round telemetry for each version.','invalid_policy':'One paired-group retry only for incomplete official outcomes or runtime errors. Telemetry gaps reported separately and do not erase valid scores. All attempts retained.'}
    (a.output/'plan.json').write_text(json.dumps(plan,indent=2)+'\n')
    home=a.output/'engine';shutil.copytree(a.home,home,ignore=shutil.ignore_patterns('treino','equipe','*.data','robot.database','*.log'));a.home=home
    runner=a.output/'samplerunner.java';runner.write_text(samples.RUNNER.replace('new BattlefieldSpecification(800,600),3,450','new BattlefieldSpecification(800,600),Integer.parseInt(a[2]),450'))
    train.compile_java(a,[runner],a.output)
    for n,s in sources.items():attack.install(a,'four'+n,s)
    selection=phase(a,'selection',list(sources),['all','leaders'],plan['selection_seeds'],8)
    ranks={n:summary([r for r in selection['results'] if r['profile']==n]) for n in sources}
    eligible=[n for n in sources if n!='base' and ranks[n]['valid']]
    if not eligible:raise ValueError('No valid experiment')
    selected=max(eligible,key=lambda n:(ranks[n]['utility'],-sum(PROFILES[n])))
    print('SELECTED',selected,json.dumps(ranks),flush=True)
    (a.output/'selection-summary.json').write_text(json.dumps({'selected':selected,'summaries':ranks},indent=2)+'\n')
    validation=phase(a,'validation',['base',selected],list(CONTEXTS),plan['validation_seeds'],12)
    b=[r for r in validation['results'] if r['profile']=='base'];c=[r for r in validation['results'] if r['profile']==selected]
    bs,cs=summary(b),summary(c);valid=bs['valid'] and cs['valid']
    checks={'outcomes':valid,'four_more_wins':valid and cs['wins']>=bs['wins']+4,'score_gain':valid and cs['macro_score_share']>=bs['macro_score_share']*1.02,'all_arena_preserved':valid and cs['contexts']['all']['wins']>=bs['contexts']['all']['wins'] and cs['contexts']['all']['score_share']>=bs['contexts']['all']['score_share'],'cpu':valid and cs['skipped']<=bs['skipped']+3,'telemetry':all(sum(len(r['telemetry']) for r in rows)>=sum(r['rounds'] for r in rows)*.9 for rows in (b,c))}
    report={**plan,'selected':selected,'selection_summaries':ranks,'validation_summaries':{'base':bs,selected:cs},'checks':checks,'proposed_promotion':all(checks.values())}
    (a.output/'candidate.java.txt').write_text(sources[selected]);(a.output/'results.json').write_text(json.dumps(report,indent=2)+'\n')
    print('FINAL',json.dumps(report['validation_summaries']),checks,flush=True)

if __name__=='__main__':main()
