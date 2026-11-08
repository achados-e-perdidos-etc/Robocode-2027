#!/usr/bin/env python3
"""Repete pares invalidados pelo motor; conserva o lote original sem altera-lo."""
import argparse,copy,hashlib,json
from pathlib import Path
import attack,bo3,defense,reactive,samples,train

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',required=True,type=Path);p.add_argument('--ecj',type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--original',required=True,type=Path);p.add_argument('--source',type=Path,default=train.ROOT/'robots/equipe/titanprime.java');a=p.parse_args()
 for k in ('home','ecj','output','original','source'):
  if getattr(a,k) is not None:setattr(a,k,getattr(a,k).resolve())
 original_bytes=a.original.read_bytes();d=json.loads(original_bytes);base=a.source.read_text();candidate=reactive.candidate_source(base,d.get('conservative',False))
 if hashlib.sha256(base.encode()).hexdigest()!=d['baseline_sha256'] or hashlib.sha256(candidate.encode()).hexdigest()!=d['candidate_sha256']:raise ValueError('Frozen sources required')
 pairs=sorted({(r['context'],r['seed']) for rows in d['validation'].values() for r in rows if r['status']!='complete'})
 if not pairs:raise ValueError('No invalid pairs; valid losses cannot be replaced')
 if a.output.exists():raise ValueError('Use a new output folder; previous attempts must be preserved')
 a.output.mkdir(parents=True);a.parse_only=False;a.engine_version=d['engine']
 (a.output/'plan.json').write_text(json.dumps({'original_report_sha256':hashlib.sha256(original_bytes).hexdigest(),'pairs':pairs,'baseline_sha256':d['baseline_sha256'],'candidate_sha256':d['candidate_sha256'],'policy':'One retry of each invalid pair, both versions. No valid-only pair may be replaced.'},indent=2)+'\n')
 runner=a.output/'samplerunner.java';runner.write_text(samples.RUNNER);train.compile_java(a,[runner],a.output)
 bo3.install(a,'reactiveanchor',base);attack.install(a,'reactivebase',base);attack.install(a,'reactivenew',candidate)
 replacements={n:[] for n in d['validation']};eligible=copy.deepcopy(d['validation']);old=reactive.CONTEXTS
 try:
  for i,(context,seed) in enumerate(pairs):
   reactive.CONTEXTS={context:old[context]};result=reactive.evaluate(a,'pair'+str(i),(seed,))
   for n,rows in result.items():
    replacements[n]+=rows
    eligible[n]=[r for r in eligible[n] if (r['context'],r['seed'])!=(context,seed)]+rows
 finally:reactive.CONTEXTS=old
 b,c=eligible['reactivebase'],eligible['reactivenew'];valid=all(r['status']=='complete' for r in b+c)
 checks=defense.promotion_checks(b,c) if valid else {'all_included_series_valid':False};checks.pop('first_rounds_preserved',None)
 checks['all_included_series_valid']=valid
 checks['target_improvement']=any(sum(bool(r['series_win']) for r in c if r['context']==k)>sum(bool(r['series_win']) for r in b if r['context']==k) for k in ('diamond','strong_melee'))
 report={'baseline_sha256':d['baseline_sha256'],'candidate_sha256':d['candidate_sha256'],'engine':a.engine_version,'original_report_sha256':hashlib.sha256(original_bytes).hexdigest(),'replaced_pairs':pairs,'replacements':replacements,'validation':eligible,'checks':checks,'proposed_promotion':all(checks.values()),'diagnostic':{n:reactive.summary(rows) for n,rows in eligible.items()}}
 (a.output/'results.json').write_text(json.dumps(report,indent=2)+'\n');print({n:defense.rank([r for r in rows if r['status']=='complete']) for n,rows in eligible.items()},checks,flush=True)
if __name__=='__main__':main()
