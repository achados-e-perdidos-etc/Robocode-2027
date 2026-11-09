#!/usr/bin/env python3
"""Confirma ataque por oportunidade em novas largadas, apos a selecao inicial."""
import argparse,json,shutil
from pathlib import Path
import attack,four,podium,samples,train

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--home',required=True,type=Path);p.add_argument('--ecj',required=True,type=Path)
    p.add_argument('--output',required=True,type=Path);p.add_argument('--source',required=True,type=Path)
    a=p.parse_args()
    for k in ('home','ecj','output','source'):setattr(a,k,getattr(a,k).resolve())
    if a.output.exists():p.error('Use a new output directory')
    a.output.mkdir(parents=True);source=a.source.read_text();candidate=podium.profile(source,'opportunity')
    plan={'baseline_sha256':four.sha(source),'candidate_sha256':four.sha(candidate),'selected':'opportunity','contexts':{k:podium.CONTEXTS[k] for k in ('all','leaders','elite')},'seeds':[17011,17417,17827],'rounds':12,'metric':'Equal macro weight to round-win rate and score share. Every round equal.','promotion':'At least four more round wins; at least 2% relative macro score-share gain; all-arena wins and share not lower; no more than three extra skips; at least 90% round telemetry in each arm; complete official outcomes.','reason':'The attack arm improved both selection metrics. Short routes failed to preserve all-arena score share on the first reserved set. This confirmation uses three different, unused starts; no valid battle is retried.'}
    (a.output/'plan.json').write_text(json.dumps(plan,indent=2)+'\n')
    home=a.output/'engine';shutil.copytree(a.home,home,ignore=shutil.ignore_patterns('treino','equipe','*.data','robot.database','*.log'));a.home=home
    runner=a.output/'samplerunner.java';runner.write_text(samples.RUNNER.replace('new BattlefieldSpecification(800,600),3,450','new BattlefieldSpecification(800,600),Integer.parseInt(a[2]),450'));train.compile_java(a,[runner],a.output)
    attack.install(a,'fourbase',source);attack.install(a,'fouropportunity',candidate)
    comparison=podium.phase(a,'confirmation',['base','opportunity'],list(plan['contexts']),plan['seeds'],12)
    b=[r for r in comparison['results'] if r['profile']=='base'];c=[r for r in comparison['results'] if r['profile']=='opportunity']
    bs,cs=podium.summary(b),podium.summary(c);valid=bs['valid'] and cs['valid']
    checks={'outcomes':valid,'four_more_wins':valid and cs['wins']>=bs['wins']+4,'score_gain':valid and cs['macro_score_share']>=bs['macro_score_share']*1.02,'all_arena_preserved':valid and cs['contexts']['all']['wins']>=bs['contexts']['all']['wins'] and cs['contexts']['all']['score_share']>=bs['contexts']['all']['score_share'],'cpu':valid and cs['skipped']<=bs['skipped']+3,'telemetry':all(sum(len(r['telemetry']) for r in rows)>=sum(r['rounds'] for r in rows)*.9 for rows in (b,c))}
    report={**plan,'summaries':{'base':bs,'opportunity':cs},'checks':checks,'proposed_promotion':all(checks.values())}
    (a.output/'candidate.java.txt').write_text(candidate);(a.output/'results.json').write_text(json.dumps(report,indent=2)+'\n')
    print('FINAL',json.dumps(report),flush=True)

if __name__=='__main__':main()
