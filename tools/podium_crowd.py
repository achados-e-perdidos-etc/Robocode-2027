#!/usr/bin/env python3
"""Confirma ataque condicionado a quatro ou mais inimigos, em largadas novas."""
import argparse,json,shutil
from pathlib import Path
import attack,four,podium,samples,train

def crowd_source(source):
    text=podium.profile(source,'opportunity')
    anchor='USE_OPPORTUNITY && getOthers() > 1'
    if text.count(anchor)!=2:raise ValueError('Attack guard anchors changed')
    return text.replace(anchor,'USE_OPPORTUNITY && getOthers() >= 4')

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for k in ('home','ecj','output','source'):p.add_argument('--'+k,required=True,type=Path)
    a=p.parse_args()
    for k in ('home','ecj','output','source'):setattr(a,k,getattr(a,k).resolve())
    if a.output.exists():p.error('Use a new output directory')
    a.output.mkdir(parents=True);source=a.source.read_text();candidate=crowd_source(source)
    plan={'baseline_sha256':four.sha(source),'candidate_sha256':four.sha(candidate),'selected':'crowd','seeds':[18101,18413,18719],'rounds':12,'contexts':{'all':podium.CONTEXTS['all']},'reason':'Unconditional opportunity attack gained in eight-robot arenas and regressed in arenas with two or three opponents. The new guard enables it only at getOthers >= 4 and restores the base attack as the field shrinks. No opponent names enter the guard.','promotion':'At least four more round wins, at least 2% relative score-share gain, complete official outcomes, no more than three extra skips, at least 90% round telemetry for each arm. A separate two-round three-opponent guard must record zero opportunity decisions and no errors. New starts only; one paired retry for invalid execution only.'}
    (a.output/'plan.json').write_text(json.dumps(plan,indent=2)+'\n')
    home=a.output/'engine';shutil.copytree(a.home,home,ignore=shutil.ignore_patterns('treino','equipe','*.data','robot.database','*.log'));a.home=home
    runner=a.output/'samplerunner.java';runner.write_text(samples.RUNNER.replace('new BattlefieldSpecification(800,600),3,450','new BattlefieldSpecification(800,600),Integer.parseInt(a[2]),450'));train.compile_java(a,[runner],a.output)
    attack.install(a,'fourbase',source);attack.install(a,'fourcrowd',candidate)
    podium.CONTEXTS['all']=plan['contexts']['all']
    comparison=podium.phase(a,'confirmation',['base','crowd'],['all'],plan['seeds'],12)
    guards=podium.phase(a,'guard',['base','crowd'],['leaders'],[19001],2)
    b=[r for r in comparison['results'] if r['profile']=='base'];c=[r for r in comparison['results'] if r['profile']=='crowd']
    bs,cs=podium.summary(b),podium.summary(c);valid=bs['valid'] and cs['valid']
    import re
    guard_metrics=[]
    for row in guards['results']:
        if row['profile']=='crowd':
            log=(a.output/(row['key']+'-attempt'+str(row['attempt'])+'.log')).read_text()
            guard_metrics=[int(v) for v in re.findall(r'PODIUM round=\d+ opportunity=(\d+)',log)]
    checks={'outcomes':valid,'four_more_wins':valid and cs['wins']>=bs['wins']+4,'score_gain':valid and cs['macro_score_share']>=bs['macro_score_share']*1.02,'cpu':valid and cs['skipped']<=bs['skipped']+3,'telemetry':all(sum(len(r['telemetry']) for r in rows)>=sum(r['rounds'] for r in rows)*.9 for rows in (b,c)),'small_arena_guard':all(r['outcome_valid'] for r in guards['results']) and guard_metrics==[0,0]}
    report={**plan,'summaries':{'base':bs,'crowd':cs},'checks':checks,'guard_metrics':guard_metrics,'proposed_promotion':all(checks.values())}
    (a.output/'candidate.java.txt').write_text(candidate);(a.output/'results.json').write_text(json.dumps(report,indent=2)+'\n');print('FINAL',json.dumps(report),flush=True)

if __name__=='__main__':main()
