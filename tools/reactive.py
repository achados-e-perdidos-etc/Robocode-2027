#!/usr/bin/env python3
"""Diagnostico e comparacao reservada da mira reativa, com 80% de cenarios fortes."""
import argparse,hashlib,json,re
from pathlib import Path
import attack,bo3,defense,diagnose,samples,train
CONTEXTS={'diamond':('duel','voidious.Diamond 1.8.28',2),'supermercutio':('duel','reference.supermercutio*',2),'mirror':('duel','treino.reactiveanchor*',2),'strong_melee':('melee','voidious.Diamond 1.8.28,reference.supermercutio*',3),'ramfire':('duel','sample.RamFire',2)}
SELECTION=(7001,7211)
VALIDATION=(7481,7691,7907)
def prepare(source):
 if 'USE_REACTIVE_GUN' in source:return source
 patch=json.loads(Path(__file__).with_name('reactive-prototype.json').read_text())
 if hashlib.sha256(source.encode()).hexdigest()!=patch['baseline_sha256']:raise ValueError('Original source required')
 for c in patch['changes']:
  if source.count(c['old'])!=1:raise ValueError('Ambiguous patch')
  source=source.replace(c['old'],c['new'],1)
 return source

def candidate_source(source, conservative=False):
 source=prepare(source).replace('USE_REACTIVE_GUN = false','USE_REACTIVE_GUN = true')
 if conservative and 'score < best + 0.03' not in source:
  old='                double left = escapeOffset(e, speed, direct, -1), right = escapeOffset(e, speed, direct, 1);\n                angles[9] = direct + clamp(Math.min(left, right), offset, Math.max(left, right));'
  if source.count(old)!=1:raise ValueError('Reactive clamp anchor missing')
  source=source.replace(old,'                angles[9] = direct + offset;')
  old='            if (score > best) { best = score; selected = i; }'
  if source.count(old)!=1:raise ValueError('Selection anchor missing')
  source=source.replace(old,'            if (USE_REACTIVE_GUN && i == 9 && (e.brain.gunTrials[i] < 8 || score < best + 0.03)) continue;\n'+old)
 return source

def evaluate(a,label,seeds):
 old=defense.CONTEXTS
 try:
  defense.CONTEXTS=CONTEXTS
  try:result=defense.evaluate(a,label,['reactivebase','reactivenew'],seeds)
  except ValueError as error:
   if not str(error).startswith('Invalid defense series '):raise
   text=(a.output/(label+'.log')).read_text();blocks=dict(re.findall(r'BEGIN\t([^\n]+)\n(.*?)END\t\1(?:\n|$)',text,re.S));result={}
   for n in ('reactivebase','reactivenew'):
    result[n]=[]
    for context,(category,opponents,physical) in CONTEXTS.items():
     for seed in seeds:
      case={'key':f'{n}-{context}-{seed}','name':n,'context':context,'category':category,'opponents':opponents,'physical_count':physical,'seed':seed}
      result[n].append(samples.parse_block(blocks[case['key']],case,n))
 finally:defense.CONTEXTS=old
 text=(a.output/(label+'.log')).read_text();blocks=dict(re.findall(r'BEGIN\t([^\n]+)\n(.*?)END\t\1(?:\n|$)',text,re.S))
 for n,rows in result.items():
  for r in rows:
   if r['status']!='complete':
    r['telemetry']=[];r['reactive_selections']=None
    r['invalid_markers']=[line for line in blocks[r['key']].splitlines() if any(t in line for t in ('STATUS\terror','Unable to stop thread','has not performed','No score will be generated','ERROR\t','Exception'))]
    continue
   own='\n'.join(s for s in blocks[r['key']].splitlines() if s.startswith('[treino.'+n+'*]'))
   values=[diagnose.parse_telemetry(s) for s in own.splitlines() if ' TELEMETRY ' in s]
   if sorted(v['round'] for v in values)!=[0,1,2]:raise ValueError('Telemetry incomplete')
   if re.findall(r'COLD_GUN_CONTEXT enemies=(\d+)',own)!=['0']:raise ValueError('Memory leaked')
   usage=[list(map(int,s.split(','))) for s in re.findall(r'GUN_USAGE \[([^\]]+)\]',own)]
   if len(usage)!=3 or any(len(u) not in (7,10) for u in usage):raise ValueError('Gun usage missing')
   if sum(map(sum,usage))!=r['reported_shots']:raise ValueError('Shot counts disagree')
   for v in values:
    legacy=next(x for x in r['round_stats'] if x['round']==v['round'])
    if v['shots']!=legacy['shots'] or v['hits']!=legacy['hits']:raise ValueError('Telemetry disagrees')
   r['reactive_selections']=sum(u[9] for u in usage if len(u)>9)
   r['telemetry']=values
 return result

def summary(rows):
 old=diagnose.CONTEXTS
 try:
  diagnose.CONTEXTS=CONTEXTS;result=diagnose.summarize([r for r in rows if r['status']=='complete'])
  for context,totals in result.items():
   totals['valid_series']=sum(r['status']=='complete' and r['context']==context for r in rows)
   totals['valid_rounds']=3*totals['valid_series']
  return result
 finally:diagnose.CONTEXTS=old

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path);p.add_argument('--ecj',type=Path);p.add_argument('--output',type=Path);p.add_argument('--source',type=Path,default=train.ROOT/'robots/equipe/titanprime.java');p.add_argument('--parse-only',action='store_true');p.add_argument('--export',type=Path);p.add_argument('--conservative',action='store_true');a=p.parse_args()
 if a.export:
  a.export.parent.mkdir(parents=True,exist_ok=True);a.export.write_text(candidate_source(a.source.read_text(),a.conservative));return
 if a.home is None or a.output is None:p.error('--home and --output required for battles')
 for k in ('home','ecj','output','source'):
  if getattr(a,k) is not None:setattr(a,k,getattr(a,k).resolve())
 a.output.mkdir(parents=True,exist_ok=True);base=a.source.read_text();candidate=candidate_source(base,a.conservative)
 selection_seeds=() if a.conservative else SELECTION
 validation_seeds=(8123,8353,8597) if a.conservative else VALIDATION
 plan={'baseline_sha256':hashlib.sha256(base.encode()).hexdigest(),'candidate_sha256':hashlib.sha256(candidate.encode()).hexdigest(),'contexts':CONTEXTS,'selection_seeds':selection_seeds,'validation_seeds':validation_seeds,'rule':'More reserved series, preserve each strong context, improve Diamond or strong melee series, <=3 extra skipped turns. Samples are regression only.'}
 if a.conservative:plan['conservative']=True
 if a.parse_only:
  if json.loads((a.output/'plan.json').read_text())!=json.loads(json.dumps(plan)):raise ValueError('Original protocol required')
 else:
  (a.output/'plan.json').write_text(json.dumps(plan,indent=2)+'\n');runner=a.output/'samplerunner.java';runner.write_text(samples.RUNNER);train.compile_java(a,[runner],a.output)
  bo3.install(a,'reactiveanchor',base);attack.install(a,'reactivebase',base);attack.install(a,'reactivenew',candidate)
 selection=evaluate(a,'selection',selection_seeds) if selection_seeds else {};print('SELECTION',{n:defense.rank([x for x in r if x['status']=='complete']) for n,r in selection.items()},flush=True)
 validation=evaluate(a,'validation',validation_seeds);b=validation['reactivebase'];c=validation['reactivenew']
 checks=defense.promotion_checks(b,c) if all(r['status']=='complete' for r in b+c) else {'all_series_valid':False}
 checks.pop('first_rounds_preserved',None)
 checks['target_improvement']=any(sum(bool(r['series_win']) for r in c if r['context']==k)>sum(bool(r['series_win']) for r in b if r['context']==k) for k in ('diamond','strong_melee'))
 report={**plan,'engine':a.engine_version,'selection':selection,'validation':validation,'checks':checks,'proposed_promotion':all(checks.values()),'diagnostic':{n:summary(rows) for n,rows in validation.items()}}
 (a.output/'candidate.java.txt').write_text(candidate);(a.output/'results.json').write_text(json.dumps(report,indent=2)+'\n');print('VALIDATION',{n:defense.rank([x for x in r if x['status']=='complete']) for n,r in validation.items()},checks,flush=True)
if __name__=='__main__':main()
