#!/usr/bin/env python3
"""Verifica geometria da hitbox, KNN condicionado e prioridade das series."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import tempfile
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import defense

JAVA = r'''
package equipe;
public class defensechecks {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void near(double a,double b,String m){check(Math.abs(a-b)<1e-7,m);}
 public static void main(String[] args){
  titanprime.Wave w=new titanprime.Wave();w.x=0;w.y=0;w.speed=14;w.direction=1;w.bearing=0;
  check(titanprime.rectangleWaveSpan(w,0,200,0,100)==null,"wave before rectangle");
  check(titanprime.rectangleWaveSpan(w,0,200,250,264)==null,"wave after rectangle");
  double[] span=titanprime.rectangleWaveSpan(w,0,200,190,204);
  check(span!=null&&span[0]<0&&span[1]>0,"rectangle angular width");
  w.direction=-1;double[] reverse=titanprime.rectangleWaveSpan(w,0,200,190,204);
  near(span[0],-reverse[1],"mirror lower");near(span[1],-reverse[0],"mirror upper");
  w.bearing=Math.PI-0.001;w.direction=1;
  span=titanprime.rectangleWaveSpan(w,-0.1,-200,190,204);
  check(span!=null&&span[0]<0&&span[1]>0,"angle wrap around pi");
  java.util.Random rng=new java.util.Random(2027);int points=0;
  for(int c=0;c<1000;c++){
   double x=100+rng.nextDouble()*500,y=100+rng.nextDouble()*400;
   double center=titanprime.angle(0,0,x,y),distance=Math.hypot(x,y);
   w.bearing=center+(rng.nextDouble()-0.5)*0.8;w.direction=c%2==0?1:-1;
   double inner=distance-40+rng.nextDouble()*80,outer=inner+14;
   span=titanprime.rectangleWaveSpan(w,x,y,inner,outer);
   for(int a=-100;a<=100;a++)for(double r:new double[]{inner,(inner+outer)/2,outer}){
    double angle=center+a*0.002,px=Math.sin(angle)*r,py=Math.cos(angle)*r;
    if(Math.abs(px-x)<=18&&Math.abs(py-y)<=18){
     points++;check(span!=null,"sampled intersection not missed");
     double gf=titanprime.normal(angle-w.bearing)*w.direction/Math.asin(8.0/w.speed);
     check(gf>=span[0]-1e-7&&gf<=span[1]+1e-7,"sampled angle inside envelope");
    }
   }
  }
  check(points>1000,"nonempty geometry sampling");
  double[] prefix=new double[titanprime.BINS+1];for(int i=1;i<prefix.length;i++)prefix[i]=i;
  near(titanprime.intervalDensity(prefix,-0.8,0.4),1,"uniform density unaffected by span");
  near(titanprime.intervalDensity(prefix,0.1,0.1),1,"zero width finite");
  titanprime.SurfContext context=new titanprime.SurfContext();
  double[] left=new double[12],right=new double[12];left[1]=0.1;right[1]=0.9;
  for(int i=0;i<30;i++){context.observe(left,-0.7);context.observe(right,0.7);}
  double[] l=context.predict(left),r=context.predict(right);double sum=0;
  for(double p:l){check(Double.isFinite(p)&&p>0,"positive finite KNN");sum+=p;}near(sum,1,"KNN normalized");
  check(l[titanprime.gfBin(-0.7)]>l[titanprime.gfBin(0.7)]*5,"left context learned");
  check(r[titanprime.gfBin(0.7)]>r[titanprime.gfBin(-0.7)]*5,"right context learned");
  double old=context.inputs[0][1];left[1]=1;near(context.inputs[0][1],old,"sample owns feature copy");
  for(int i=0;i<500;i++)context.observe(right,0.2);
  check(context.size==192&&context.seen==560,"bounded replay ring");
  double[] recent=context.predict(right);
  check(recent[titanprime.gfBin(0.2)]>recent[titanprime.gfBin(0.7)]*5,"old pattern replaced by recent samples");
  titanprime.SurfContext other=new titanprime.SurfContext();check(other.size==0,"enemy contexts isolated");
  System.out.println("DEFENSE CHECKS PASSED: "+points+" geometric samples, mirrored intervals, conditional KNN and bounded memory");
 }
}
'''


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--api',required=True,type=Path);p.add_argument('--ecj',type=Path);a=p.parse_args()
    source=(Path(__file__).resolve().parents[1]/'robots/equipe/titanprime.java').read_text()
    source=defense.prepare_template(source)
    with tempfile.TemporaryDirectory(prefix='titan-defense-') as d:
        root=Path(d);(root/'titanprime.java').write_text(source);(root/'defensechecks.java').write_text(JAVA)
        compiler=['java','-jar',str(a.ecj.resolve()),'-8','-nowarn'] if a.ecj else ['javac','-source','8','-target','8']
        subprocess.run(compiler+['-encoding','UTF-8','-cp',str(a.api.resolve()),'-d',d,str(root/'titanprime.java'),str(root/'defensechecks.java')],check=True)
        subprocess.run(['java','-cp',d+os.pathsep+str(a.api.resolve()),'equipe.defensechecks'],check=True)
    win={'series_win':True,'first_round_win':True,'wins':2,'score_share':0.1,'skipped':0,'context':'duel'}
    loss={**win,'series_win':False,'wins':1,'score_share':0.99}
    assert defense.rank([win])>defense.rank([loss])
    assert not defense.promotion_checks([win],[{**win,'score_share':0.99}])['more_series_won']
    assert not defense.promotion_checks([win],[loss])['no_context_series_regression']
    print('DEFENSE GATES PASSED: points do not replace BO3 wins')


if __name__=='__main__':main()
