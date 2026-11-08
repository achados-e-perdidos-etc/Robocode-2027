#!/usr/bin/env python3
"""Verifica KNN ofensivo e a separacao entre visitas e avaliacao de tiros reais."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import attack

JAVA=r'''
package equipe;
public class attackchecks {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void near(double a,double b,String m){check(Math.abs(a-b)<1e-8,m);}
 static class Probe extends titanprime {public long getTime(){return 10;}}
 static titanprime.GunWave wave(titanprime.Enemy e,boolean firing){
  titanprime.GunWave w=new titanprime.GunWave();w.owner=e;w.firing=firing;w.speed=14;w.direction=1;w.time=0;w.segment=0;
  w.features=new double[12];w.angles=new double[7];w.previousTime=9;w.previousX=0;w.previousY=100;return w;
 }
 public static void main(String[] args)throws Exception {
  titanprime.GunContext model=new titanprime.GunContext();double[] left=new double[12],right=new double[12];left[1]=0.1;right[1]=0.9;
  for(int i=0;i<40;i++){model.observe(left,-0.6);model.observe(right,0.6);}
  double[] l=model.predict(left),r=model.predict(right);double sum=0;
  for(double p:l){check(p>0&&Double.isFinite(p),"finite positive density");sum+=p;}
  near(sum,1,"normalized KNN");
  check(l[titanprime.gfBin(-0.6)]>l[titanprime.gfBin(0.6)]*5,"conditional left targeting");
  check(r[titanprime.gfBin(0.6)]>r[titanprime.gfBin(-0.6)]*5,"conditional right targeting");
  check(titanprime.bestNeuralFactor(l,0.08)<-0.4&&titanprime.bestNeuralFactor(r,0.08)>0.4,"body-width aim chooses correct mode");
  double original=model.inputs[0][1];left[1]=1;near(model.inputs[0][1],original,"copied features");
  for(int i=0;i<600;i++)model.observe(right,-0.2);
  check(model.size==256&&model.seen==680,"bounded history");
  r=model.predict(right);check(r[titanprime.gfBin(-0.2)]>r[titanprime.gfBin(0.6)]*5,"adapts to new pattern");
  Probe robot=new Probe();titanprime.Brain brain=new titanprime.Brain();
  titanprime.Enemy enemy=new titanprime.Enemy("test",brain);enemy.x=0;enemy.y=100;enemy.time=10;
  java.lang.reflect.Field outgoing=titanprime.class.getDeclaredField("outgoing");outgoing.setAccessible(true);
  @SuppressWarnings("unchecked") java.util.List<titanprime.GunWave> waves=(java.util.List<titanprime.GunWave>)outgoing.get(robot);
  java.lang.reflect.Method resolve=titanprime.class.getDeclaredMethod("resolveGunWaves",titanprime.Enemy.class);resolve.setAccessible(true);
  waves.add(wave(enemy,false));resolve.invoke(robot,enemy);
  check(brain.gunSamples==1&&brain.visitGun.size==1&&brain.realGun.size==0,"virtual visit trains only visit context");
  for(double trials:brain.gunTrials)near(trials,0,"no virtual-shot precision inflation");
  waves.add(wave(enemy,true));resolve.invoke(robot,enemy);
  check(brain.gunSamples==2&&brain.visitGun.size==2&&brain.realGun.size==1,"real shot trains both contexts");
  for(double trials:brain.gunTrials)near(trials,1,"only real waves grade guns");
  check(waves.isEmpty(),"resolved waves removed");
  System.out.println("ATTACK CHECKS PASSED: conditioned KNN, bounded memory, body-width aim, visits vs real-shot accounting");
 }
}
'''


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--api',required=True,type=Path);p.add_argument('--ecj',type=Path);a=p.parse_args()
    src=(Path(__file__).resolve().parents[1]/'robots/equipe/titanprime.java').read_text()
    template=attack.profile(src,(True,True))
    with tempfile.TemporaryDirectory(prefix='titan-attack-') as d:
        root=Path(d);(root/'titanprime.java').write_text(template);(root/'attackchecks.java').write_text(JAVA)
        compiler=['java','-jar',str(a.ecj.resolve()),'-8','-nowarn'] if a.ecj else ['javac','-source','8','-target','8']
        subprocess.run(compiler+['-encoding','UTF-8','-cp',str(a.api.resolve()),'-d',d,str(root/'titanprime.java'),str(root/'attackchecks.java')],check=True)
        subprocess.run(['java','-cp',d+os.pathsep+str(a.api.resolve()),'equipe.attackchecks'],check=True)
    for flag in ('USE_KNN_GUN','USE_VISIT_WAVES'):assert flag in template


if __name__=='__main__':main()
