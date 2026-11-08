#!/usr/bin/env python3
"""Implementa e compara as lacunas ofensivas em um unico Java gerado."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import attack
import bo3
import defense
import samples
import train

CONTEXTS={
 'diamond':('duel','voidious.Diamond 1.8.28',2),
 'mirror':('duel','treino.completeanchor*',2),
 'supermercutio':('duel','reference.supermercutio*',2),
 'crowd':('melee',samples.GROUPS['six'],7),
 'all':('melee',samples.GROUPS['all_autonomous'],17),
 'team':('team','sampleteam.MyFirstTeam',6)}
PROFILES={'base':None,'guns':(True,False),'finish':(False,True),'both':(True,True)}
SELECTION=(4001,4153)
VALIDATION=(4337,4513,4703)


def prepare(source):
 if 'static final boolean USE_EXTENDED_GUNS' in source:return source
 patch=json.loads(Path(__file__).with_name('complete-prototype.json').read_text())
 if hashlib.sha256(source.encode()).hexdigest()!=patch['baseline_sha256']:raise ValueError('Original 3.6 source required')
 for c in patch['changes']:
  if source.count(c['old'])!=1:raise ValueError('Ambiguous complete patch')
  source=source.replace(c['old'],c['new'],1)
 return source


def profile(source,flags):
 if flags is None:return source
 source=prepare(source)
 for key,value in zip(('EXTENDED_GUNS','FINISHER'),flags):
  source,n=re.subn('USE_'+key+' = (?:true|false)','USE_'+key+' = '+str(value).lower(),source)
  if n!=1:raise ValueError('Missing feature flag')
 return source


def evaluate(a,label,names,seeds):
 old=defense.CONTEXTS
 try:
  defense.CONTEXTS=CONTEXTS
  result=defense.evaluate(a,label,names,seeds)
 finally:defense.CONTEXTS=old
 text=(a.output/(label+'.log')).read_text()
 blocks=dict(re.findall(r'BEGIN\t([^\n]+)\n(.*?)END\t\1(?:\n|$)',text,re.S))
 for name,rows in result.items():
  for r in rows:
   lines='\n'.join(l for l in blocks[r['key']].splitlines() if l.startswith('[treino.'+name+'*]'))
   if re.findall(r'COLD_GUN_CONTEXT enemies=(\d+)',lines)!=['0']:raise ValueError('Memory leaked')
   usages=[list(map(int,x.split(','))) for x in re.findall(r'GUN_USAGE \[([^\]]+)\]',lines)]
   if len(usages)!=3 or any(len(x) not in (7,10) for x in usages):raise ValueError('Gun telemetry missing')
   total=[sum(x[i] for x in usages) for i in range(len(usages[0]))]
   if sum(total)!=r['reported_shots']:raise ValueError('Shot count mismatch')
   r['gun_usage']=total;r['extended_selections']=sum(total[7:])
 return result


def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path);p.add_argument('--ecj',type=Path)
 p.add_argument('--output',type=Path);p.add_argument('--source',type=Path,default=train.ROOT/'robots/equipe/titanprime.java');p.add_argument('--parse-only',action='store_true')
 p.add_argument('--export',type=Path,help='Exporta um unico Java com todas as tecnicas experimentais; nao executa batalhas')
 a=p.parse_args()
 if a.export:
  a.export.parent.mkdir(parents=True,exist_ok=True)
  a.export.write_text(profile(a.source.read_text(),(True,True)))
  print('Exported',a.export);return
 if a.home is None or a.output is None:p.error('--home and --output required for battles')
 for k in ('home','ecj','output','source'):
  if getattr(a,k) is not None:setattr(a,k,getattr(a,k).resolve())
 a.output.mkdir(parents=True,exist_ok=True)
 source=a.source.read_text();sources={'complete'+n:profile(source,f) for n,f in PROFILES.items()}
 plan={'source_sha256':hashlib.sha256(source.encode()).hexdigest(),'variants_sha256':{n:hashlib.sha256(s.encode()).hexdigest() for n,s in sources.items()},
       'profiles':PROFILES,'contexts':CONTEXTS,'selection_seeds':SELECTION,'validation_seeds':VALIDATION,'rounds_per_series':3,
       'promotion_rule':'More validation series; preserve series by context; at most three extra skipped turns; full sample guards before adoption. First-round wins reported separately.'}
 if a.parse_only:
  if json.loads((a.output/'plan.json').read_text())!=json.loads(json.dumps(plan)):raise ValueError('Original protocol required')
 else:
  (a.output/'plan.json').write_text(json.dumps(plan,indent=2)+'\n')
  runner=a.output/'samplerunner.java';runner.write_text(samples.RUNNER);train.compile_java(a,[runner],a.output)
  bo3.install(a,'completeanchor',source)
  for n,s in sources.items():attack.install(a,n,s)
 print('PLAN',plan['promotion_rule'],flush=True)
 selection=evaluate(a,'selection',list(sources),SELECTION)
 selected=max((n for n in sources if n!='completebase'),key=lambda n:defense.rank(selection[n]))
 progress={**plan,'engine':a.engine_version,'selection':selection,'selected':selected,'selection_rank':{n:defense.rank(r) for n,r in selection.items()}}
 (a.output/'progress.json').write_text(json.dumps(progress,indent=2)+'\n')
 print('SELECTED',selected,progress['selection_rank'],flush=True)
 validation=evaluate(a,'validation',['completebase',selected],VALIDATION)
 checks=defense.promotion_checks(validation['completebase'],validation[selected]);first=checks.pop('first_rounds_preserved')
 checks['selection_series_preserved']=defense.rank(selection[selected])[0]>=defense.rank(selection['completebase'])[0]
 report={**progress,'validation':validation,'validation_rank':{n:defense.rank(r) for n,r in validation.items()},'checks':checks,
         'first_rounds_preserved_secondary':first,'proposed_promotion':all(checks.values()),'candidate_sha256':hashlib.sha256(sources[selected].encode()).hexdigest()}
 (a.output/'candidate.java.txt').write_text(sources[selected]);(a.output/'all-features.java.txt').write_text(sources['completeboth'])
 (a.output/'results.json').write_text(json.dumps(report,indent=2)+'\n')
 print('VALIDATION',report['validation_rank'],'PROPOSED_PROMOTION',report['proposed_promotion'],checks,flush=True)


if __name__=='__main__':main()
