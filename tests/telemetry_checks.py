#!/usr/bin/env python3
"""Confere dano nominal, faixas de tiro, memoria limitada e consistencia do CSV."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import diagnose

JAVA=r'''
package equipe;
public class telemetrychecks {
 static void check(boolean x,String m){if(!x)throw new AssertionError(m);}
 static void near(double a,double b,String m){check(Math.abs(a-b)<1e-8,m);}
 public static void main(String[] args){
  titanprime.Telemetry t=new titanprime.Telemetry();
  t.decision(false);t.decision(true);t.scan(-1);t.scan(0);t.scan(3);t.scan(1);
  check(t.decisions==2&&t.noTarget==1&&t.scans==4&&t.scanGaps==2&&t.gapSum==4&&t.gapMax==3,"scan and decision accounting");
  check(titanprime.Telemetry.range(199.9)==0&&titanprime.Telemetry.range(200)==1&&titanprime.Telemetry.range(400)==2,"distance boundaries");
  Object a=new Object(),b=new Object();t.shot(a,2,250,0);t.shot(b,3,600,4);
  t.hit(a,2);t.release(a);t.hit(b,3);t.release(b);
  check(t.shots==2&&t.hits==2&&t.shotsByRange[1]==1&&t.hitsByRange[2]==1&&t.aimAgeMax==4&&t.aimAgeSum==4,"shot lifetime and range accounting");
  near(t.spent,5,"energy spent");near(t.dealtNominal,26,"nominal damage, not actual energy delta");near(t.hitBonusNominal,15,"nominal bonus");
  t.received(2,13);t.received(3,-1);
  check(t.received==2&&t.matched==1&&t.unmatched==1,"match/unmatched partition");near(t.receivedNominal,26,"received nominal damage");near(t.matchErrorSum,13,"matched error");
  check(t.shotRanges.isEmpty(),"completed bullets released");
  System.out.println(t.line(0));
  titanprime.Telemetry bounded=new titanprime.Telemetry();Object[] bullets=new Object[70];
  for(int i=0;i<70;i++){bullets[i]=new Object();bounded.shot(bullets[i],1,100,0);}
  check(bounded.shotRanges.size()==64,"tracking memory bounded");bounded.hit(bullets[69],1);
  check(bounded.unknownHitRange==1,"unknown range recorded explicitly");
  for(Object bullet:bullets)bounded.release(bullet);check(bounded.shotRanges.isEmpty(),"bounded map cleans up");
  check(new titanprime.Telemetry().received==0,"round state isolated");
 }
}
'''


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--api',required=True,type=Path);p.add_argument('--ecj',type=Path);a=p.parse_args()
    source=Path(__file__).resolve().parents[1]/'robots/equipe/titanprime.java'
    with tempfile.TemporaryDirectory(prefix='titan-telemetry-') as d:
        helper=Path(d)/'telemetrychecks.java';helper.write_text(JAVA)
        compiler=['java','-jar',str(a.ecj.resolve()),'-8','-nowarn'] if a.ecj else ['javac','-source','8','-target','8']
        subprocess.run(compiler+['-encoding','UTF-8','-cp',str(a.api.resolve()),'-d',d,str(source),str(helper)],check=True)
        output=subprocess.check_output(['java','-cp',d+os.pathsep+str(a.api.resolve()),'equipe.telemetrychecks'],text=True)
    line=next(s for s in output.splitlines() if s.startswith('TELEMETRY '));row=diagnose.parse_telemetry(line)
    assert row['body_match_rate']==0.5 and row['aim_age_mean']==2 and row['scan_gap_mean']==2
    assert row['no_target_decision_fraction']==0.5 and row['dealt_nominal']==26
    for bad in [line.replace('body_matched=1','body_matched=9'),line.replace('received_nominal=26.0','received_nominal=NaN'),line.replace('v=1','v=2'),line+' shots=2']:
        try:diagnose.parse_telemetry(bad)
        except ValueError:pass
        else:raise AssertionError('Malformed telemetry accepted')
    assert diagnose.ratio(0,0) is None
    print('TELEMETRY CHECKS PASSED: nominal damage, event matching, ranges, cleanup, schema and missing-data handling')


if __name__=='__main__':main()
