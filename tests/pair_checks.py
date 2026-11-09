#!/usr/bin/env python3
"""Parada intencional versus bloqueio, cooldown e predicao de obstrucoes."""
import argparse,subprocess,tempfile,sys,os
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import pair
JAVA=r'''
package equipe;
public class pairchecks {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static class Probe extends titanprime {public long getTime(){return 100;}public double getX(){return 400;}public double getY(){return 100;}}
 public static void main(String[] args)throws Exception {
  titanprime.MobilityRecovery stopped=new titanprime.MobilityRecovery();
  for(int t=0;t<20;t++)check(!stopped.observe(t,100,100,0,false),"intentional stops must not escape");
  titanprime.MobilityRecovery blocked=new titanprime.MobilityRecovery();boolean fired=false;
  for(int t=0;t<=8;t++)fired|=blocked.observe(t,100,100,0,true);
  check(fired&&blocked.recoveries==1&&blocked.active(10),"genuine blockage triggers recovery");
  for(int t=9;t<40;t++)blocked.observe(t,100,100,0,true);
  check(blocked.recoveries==1,"recovery cooldown");
  titanprime.MobilityRecovery moving=new titanprime.MobilityRecovery();for(int t=0;t<30;t++)check(!moving.observe(t,100+t,100,1,true),"moving robot not stuck");
  titanprime.MobilityRecovery contacts=new titanprime.MobilityRecovery();contacts.contact(0,0,0);contacts.contact(4,0,0);contacts.contact(8,0,0);check(contacts.observe(8,100,100,0,false),"repeated contacts trigger escape");
  check(titanprime.rayObstacle(100,0,400,18),"obstacle ahead");check(!titanprime.rayObstacle(-100,0,400,18),"behind shooter not obstacle");check(!titanprime.rayObstacle(410,0,400,18),"beyond target not obstacle");check(!titanprime.rayObstacle(100,30,400,18),"off ray not obstacle");
  Probe robot=new Probe();java.lang.reflect.Field width=titanprime.class.getDeclaredField("width"),height=titanprime.class.getDeclaredField("height"),enemies=titanprime.class.getDeclaredField("enemies");width.setAccessible(true);height.setAccessible(true);enemies.setAccessible(true);width.setDouble(robot,800);height.setDouble(robot,600);
  @SuppressWarnings("unchecked") java.util.Map<String,titanprime.Enemy> map=(java.util.Map<String,titanprime.Enemy>)enemies.get(robot);
  titanprime.Enemy target=new titanprime.Enemy("target",new titanprime.Brain()),other=new titanprime.Enemy("other",new titanprime.Brain());target.x=400;target.y=500;target.time=100;other.x=400;other.y=300;other.time=100;map.put(target.name,target);map.put(other.name,other);
  java.lang.reflect.Method first=titanprime.class.getDeclaredMethod("firstBlocker",titanprime.Enemy.class,double.class,double.class,double.class);first.setAccessible(true);
  check(first.invoke(robot,target,0.0,400.0,14.0)==other,"stationary blocker detected");
  other.heading=Math.PI/2;other.velocity=8;check(first.invoke(robot,target,0.0,400.0,14.0)==null,"blocker moving off line predicted clear");
  other.velocity=0;other.time=90;check(first.invoke(robot,target,0.0,400.0,14.0)==null,"stale observation not used to block");
  System.out.println("PAIR CHECKS PASSED: intended stops, contact recovery, cooldown and moving blockers");
 }
}
'''
def main():
 p=argparse.ArgumentParser();p.add_argument('--api',type=Path,required=True);p.add_argument('--ecj',type=Path,required=True);a=p.parse_args()
 with tempfile.TemporaryDirectory(prefix='titan-pair-') as d:
  root=Path(d);(root/'titanprime.java').write_text(pair.profile((pair.train.ROOT/'robots/equipe/titanprime.java').read_text(),'both'));(root/'pairchecks.java').write_text(JAVA)
  subprocess.run(['java','-jar',str(a.ecj),'-8','-nowarn','-encoding','UTF-8','-cp',str(a.api),'-d',d,str(root/'titanprime.java'),str(root/'pairchecks.java')],check=True)
  subprocess.run(['java','-cp',d+os.pathsep+str(a.api),'equipe.pairchecks'],check=True)
if __name__=='__main__':main()
