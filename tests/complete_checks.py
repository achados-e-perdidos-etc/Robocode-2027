#!/usr/bin/env python3
"""Padroes reais, continuidade, fisica, respostas a tiros e seguranca de finalizacao."""
import argparse,os,subprocess,sys,tempfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import complete
JAVA=r'''
package equipe;
public class completechecks {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void near(double a,double b,String m){check(Math.abs(a-b)<1e-8,m);}
 static class Probe extends titanprime {
  int others=1;double energy=100;
  public long getTime(){return 1000;}
  public double getEnergy(){return energy;}
  public int getOthers(){return others;}
  public double getX(){return 400;}
  public double getY(){return 300;}
 }
 public static void main(String[] args)throws Exception {
  near(titanprime.MotionState.advanceVelocity(0,8),1,"acceleration");
  near(titanprime.MotionState.advanceVelocity(8,0),6,"braking");
  near(titanprime.MotionState.advanceVelocity(1,-8),-0.5,"fractional reversal");
  titanprime.MotionState state=new titanprime.MotionState(400,300,0,8);state.step(1,8,800,600);
  near(state.heading,Math.toRadians(4),"speed dependent turn limit");
  state=new titanprime.MotionState(782,300,Math.PI/2,8);state.step(0,8,800,600);near(state.x,782,"wall clamp");near(state.velocity,0,"wall stop");
  titanprime.MotionLog linear=new titanprime.MotionLog(),circle=new titanprime.MotionLog(),still=new titanprime.MotionLog(),reverse=new titanprime.MotionLog();
  for(int t=0;t<220;t++){
   linear.observe(0,t,0,4);circle.observe(0,t,t*0.04,6);still.observe(0,t,0,0);reverse.observe(0,t,0,Math.sin(t*0.7)*3);
  }
  check(linear.mode()==2&&circle.mode()==3&&still.mode()==1&&reverse.mode()==4,"movement classification");
  check(linear.match()>=0&&circle.match()>=0,"sequence matching with known continuation");
  linear.observe(0,1000,0,4);check(linear.mode()==0&&linear.match()==-1,"gap never invented as a pattern");
  linear.observe(1,0,0,4);check(linear.mode()==0,"round boundary invalidates continuity");
  titanprime.ResponseBank bank=new titanprime.ResponseBank();double[] left=new double[12],right=new double[12];left[1]=0.1;right[1]=0.9;
  for(int i=0;i<40;i++){bank.observe(left,-6,1);bank.observe(right,6,1);}
  check(bank.predict(left)[0]<-5&&bank.predict(right)[0]>5,"conditional post-shot response");
  left[1]=1;near(bank.features[0][1],0.1,"response owns feature copy");
  for(int i=0;i<600;i++)bank.observe(right,20,20);
  check(bank.size==256,"bounded response bank");check(Math.hypot(bank.predict(right)[0],bank.predict(right)[1])<=8.000001,"physical response speed bound");
  Probe bot=new Probe();titanprime.Enemy target=new titanprime.Enemy("test",new titanprime.Brain());target.x=600;target.y=300;target.time=1000;target.energy=8;
  java.lang.reflect.Field targets=titanprime.class.getDeclaredField("target");targets.setAccessible(true);targets.set(bot,target);
  java.lang.reflect.Field enemies=titanprime.class.getDeclaredField("enemies");enemies.setAccessible(true);
  @SuppressWarnings("unchecked") java.util.Map<String,titanprime.Enemy> map=(java.util.Map<String,titanprime.Enemy>)enemies.get(bot);map.put(target.name,target);
  java.lang.reflect.Method finish=titanprime.class.getDeclaredMethod("finisherAllowed");finish.setAccessible(true);
  check((Boolean)finish.invoke(bot),"eligible weak target");bot.energy=20;check(!(Boolean)finish.invoke(bot),"low own energy blocks chase");bot.energy=100;bot.others=2;
  check(!(Boolean)finish.invoke(bot),"unseen enemies block chase");
  titanprime.Enemy other=new titanprime.Enemy("other",new titanprime.Brain());other.x=410;other.y=300;other.time=1000;map.put(other.name,other);
  check(!(Boolean)finish.invoke(bot),"nearby third party blocks chase");bot.others=1;map.remove(other.name);target.energy=20;check(!(Boolean)finish.invoke(bot),"recovered target exits finisher");
  System.out.println("COMPLETE CHECKS PASSED: sequence matching, gaps, classifier, physical prediction, response models and finisher safety");
 }
}
'''
def main():
 p=argparse.ArgumentParser();p.add_argument('--api',required=True,type=Path);p.add_argument('--ecj',type=Path);a=p.parse_args()
 source=complete.profile((Path(__file__).resolve().parents[1]/'robots/equipe/titanprime.java').read_text(),(True,True))
 with tempfile.TemporaryDirectory(prefix='titan-complete-') as d:
  q=Path(d);(q/'titanprime.java').write_text(source);(q/'completechecks.java').write_text(JAVA)
  compiler=['java','-jar',str(a.ecj.resolve()),'-8','-nowarn'] if a.ecj else ['javac','-source','8','-target','8']
  subprocess.run(compiler+['-encoding','UTF-8','-cp',str(a.api.resolve()),'-d',d,str(q/'titanprime.java'),str(q/'completechecks.java')],check=True)
  subprocess.run(['java','-cp',d+os.pathsep+str(a.api.resolve()),'equipe.completechecks'],check=True)
if __name__=='__main__':main()
