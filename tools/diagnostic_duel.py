#!/usr/bin/env python3
"""Diagnostico persistente e teste reservado de espaco/distancia em duelos."""
import argparse,csv,json,re,shutil,hashlib
from pathlib import Path
import attack,four,podium,samples,train

def audited_run_case(args,case,attempt):
    row=ORIGINAL_RUN_CASE(args,case,attempt)
    text=(args.output/(case['key']+'-attempt'+str(attempt)+'.log')).read_text()
    messages=re.findall(r'^\[[^\n]+\] ERROR:.*$|^.*still has not started.*giving up\..*$',text,re.M)
    if messages:
        row['errors']+=list(dict.fromkeys(messages));row['validation_reasons'].append('robot_reported_error');row['status']='invalid';row['wins']=None
    return row

ORIGINAL_RUN_CASE=four.run_case

def profile(source,level):
    patch=json.loads(Path(__file__).with_name('diagnostic-duel-prototype.json').read_text())
    if four.sha(source)!=patch['baseline_sha256']:raise ValueError('Original 3.9 required')
    for change in patch['changes']:
        if source.count(change['old'])!=1:raise ValueError('Ambiguous patch')
        source=source.replace(change['old'],change['new'],1)
    return source.replace('DUEL_SPACE_LEVEL = 0','DUEL_SPACE_LEVEL = '+str(level),1)

def audit_diagnostics(output,cases):
    csvs=[]
    for file in output.rglob('diagnostico.csv'):
        rows=list(csv.DictReader(file.open()))
        csvs.append({'path':str(file.relative_to(output)),'rows':rows})
    checks=[]
    for case in cases:
        data_name='four'+case['profile']+'.data'
        def compatible(record):
            rows=record['rows']
            return data_name in Path(record['path']).parts and len(rows)==case['rounds'] and all(any(int(v['round'])==t['round']+1 and all(int(v[k])==t[k] for k in ('shots','hits','walls','skipped')) for v in rows) for t in case['round_stats'])
        matches=[f for f in csvs if compatible(f)]
        rows=matches[0]['rows'] if len(matches)==1 else []
        tests={'unique_file':len(matches)==1,'one_row_per_round':sorted(int(v['round']) for v in rows)==list(range(1,case['rounds']+1)), 'wins_match':sum(int(v['won']) for v in rows)==case['official_wins'], 'bounded':all((output/f['path']).stat().st_size<=48000 for f in matches),'no_write_error':'DIAGNOSTIC_ERROR' not in (output/(case['key']+'-attempt'+str(case['attempt'])+'.log')).read_text()}
        tests['console_stats_match']=bool(rows) and all(any(int(v['round'])==t['round']+1 and int(v['shots'])==t['shots'] and int(v['hits'])==t['hits'] for v in rows) for t in case['round_stats'])
        checks.append({'key':case['key'],'checks':tests,'files':[f['path'] for f in matches]})
    return csvs,checks

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for k in ('home','ecj','output','source'):p.add_argument('--'+k,required=True,type=Path)
    a=p.parse_args()
    for k in ('home','ecj','output','source'):setattr(a,k,getattr(a,k).resolve())
    if a.output.exists():p.error('New output directory required')
    a.output.mkdir(parents=True);original=a.source.read_text();sources={n:profile(original,l) for n,l in [('base',0),('space1',1),('space2',2)]}
    plan={'baseline_sha256':four.sha(original),'variants_sha256':{n:four.sha(s) for n,s in sources.items()},'selection_seeds':[20101,20327],'selection_rounds':6,'validation_seeds':[21001,21407],'validation_rounds':8,'selection_contexts':['diamond'],'validation_contexts':['diamond','pc_trio'],'metric':'All round wins equally and macro score share equally. Logger enabled in all comparison arms; level zero preserves the old movement equations.','promotion':'Movement: at least two extra reserved wins and at least 2% relative macro score-share gain; complete outcomes, no more than three extra skipped turns. Logging: every completed round must have one persistent row; won, shots and hits must agree with official results and available console stats.'}
    (a.output/'plan.json').write_text(json.dumps(plan,indent=2)+'\n')
    home=a.output/'engine';shutil.copytree(a.home,home,ignore=shutil.ignore_patterns('treino','equipe','*.data','robot.database','*.log'));a.home=home
    seen_jars={}
    for jar in sorted((home/'robots').rglob('*.jar')):
        digest=hashlib.sha256(jar.read_bytes()).hexdigest()
        if digest in seen_jars:jar.unlink()
        else:seen_jars[digest]=jar
    # Use the user's original TitanV3, independently of the similarly named source bots.
    podium.CONTEXTS['pc_trio']=('voidious.Diamond 1.8.28,titan.TitanV3 3.0',3)
    runner=a.output/'samplerunner.java';runner.write_text(samples.RUNNER.replace('new BattlefieldSpecification(800,600),3,450','new BattlefieldSpecification(800,600),Integer.parseInt(a[2]),450'));train.compile_java(a,[runner],a.output)
    for n,s in sources.items():attack.install(a,'four'+n,s)
    four.run_case=audited_run_case
    selection=podium.phase(a,'selection',list(sources),['diamond'],plan['selection_seeds'],6)
    summaries={n:podium.summary([r for r in selection['results'] if r['profile']==n]) for n in sources}
    selected=max(('space1','space2'),key=lambda n:summaries[n].get('utility',-1))
    print('SELECTED',selected,json.dumps(summaries),flush=True)
    validation=podium.phase(a,'validation',['base',selected],['diamond','pc_trio'],plan['validation_seeds'],8)
    b=[r for r in validation['results'] if r['profile']=='base'];c=[r for r in validation['results'] if r['profile']==selected]
    bs,cs=podium.summary(b),podium.summary(c);valid=bs['valid'] and cs['valid']
    cases=selection['results']+validation['results'];csvs,checks=audit_diagnostics(a.output,cases)
    movement={'outcomes':valid,'two_more_wins':valid and cs['wins']>=bs['wins']+2,'score_gain':valid and cs['macro_score_share']>=bs['macro_score_share']*1.02,'cpu':valid and cs['skipped']<=bs['skipped']+3}
    report={**plan,'selected':selected,'selection_summaries':summaries,'validation_summaries':{'base':bs,selected:cs},'movement_checks':movement,'promote_movement':all(movement.values()),'diagnostic_checks':checks,'diagnostics_valid':all(all(x['checks'].values()) for x in checks),'csv_records':csvs}
    (a.output/'results.json').write_text(json.dumps(report,indent=2)+'\n');(a.output/'logging.java.txt').write_text(sources['base']);(a.output/'candidate.java.txt').write_text(sources[selected]);print('FINAL',json.dumps({'movement':movement,'diagnostics_valid':report['diagnostics_valid'],'base':bs,'candidate':cs}),flush=True)

if __name__=='__main__':main()
