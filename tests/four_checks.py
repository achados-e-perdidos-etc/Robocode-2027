#!/usr/bin/env python3
"""Verifica modelos condicionais, previsoes congeladas, energia e risco geometrico."""
import argparse,os,subprocess,sys,tempfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import four,diagnose
JAVA=r'''
package equipe;
public class fourchecks {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void near(double a,double b,String m){check(Math.abs(a-b)<1e-8,m);}
 static class Probe extends titanprime {
  double energy=100;int others=1;
  public long getTime(){return 10;}public double getEnergy(){return energy;}
  public int getOthers(){return others;}public double getGunCoolingRate(){return 0.1;}
 }
 static titanprime.GunWave wave(titanprime.Enemy e,boolean firing,double[] a,double[] b){
  titanprime.GunWave w=new titanprime.GunWave();w.owner=e;w.firing=firing;w.duel=true;
  w.features=new double[12];w.angles=new double[10];w.speed=14;w.direction=1;w.time=0;
  w.previousTime=9;w.previousX=0;w.previousY=100;w.localForecast=a.clone();w.generalForecast=b.clone();return w;
 }
 public static void main(String[] args)throws Exception {
  titanprime.SurfMemory memory=new titanprime.SurfMemory();double[] left=new double[12],right=new double[12];left[1]=0.1;right[1]=0.9;
  for(int i=0;i<40;i++){memory.observe(left,-0.6,1);memory.observe(right,0.6,1);}
  double[] a=memory.predict(left),b=memory.predict(right);double sum=0;
  for(double v:a){check(v>0&&Double.isFinite(v),"finite surf density");sum+=v;}
  near(sum,1,"normalized surf danger");check(a[titanprime.gfBin(-0.6)]>a[titanprime.gfBin(0.6)]*5,"conditional danger left");
  check(b[titanprime.gfBin(0.6)]>b[titanprime.gfBin(-0.6)]*5,"conditional danger right");
  double support=memory.support;double[] unfamiliar=right.clone();unfamiliar[0]=1;memory.predict(unfamiliar);check(memory.support<support,"unfamiliar situations have less support");
  left[1]=1;near(memory.inputs[0][1],0.1,"context snapshot copied");
  int before=memory.seen;memory.observe(right,0.2,0);check(memory.seen==before,"untrusted observations rejected");
  for(int i=0;i<600;i++)memory.observe(right,-0.3,1);check(memory.size==192,"bounded defensive memory");
  check(memory.predict(right)[titanprime.gfBin(-0.3)]>memory.predict(right)[titanprime.gfBin(0.6)]*5,"adapts after old samples expire");
  check(titanprime.crossfirePenalty(100,0,-100,0)>titanprime.crossfirePenalty(100,0,100,0),"opposite attackers more dangerous");
  near(titanprime.crossfirePenalty(100,0,-100,0),titanprime.crossfirePenalty(0,100,0,-100),"rotation invariance");
  check(titanprime.wallEscapeLanes(400,300,800,600)==8&&titanprime.wallEscapeLanes(20,20,800,600)<8,"corner has fewer escape lanes");
  check(titanprime.positionUncertainty(8,30)>titanprime.positionUncertainty(1,10),"stale forecasts less certain");
  check(titanprime.positionUncertainty(1000,100)==120,"uncertainty bounded");
  double[] good=new double[47],flat=new double[47];java.util.Arrays.fill(flat,1.0/47);java.util.Arrays.fill(good,0.2/46);good[23]=0.8;
  titanprime.PredictiveTrust better=new titanprime.PredictiveTrust(),worse=new titanprime.PredictiveTrust();near(better.weight(0),0,"cold trust");
  for(int i=0;i<40;i++){better.observe(good,flat,0);worse.observe(flat,good,0);}
  check(better.weight(100)>0.85&&worse.weight(100)<0.85,"pre-outcome predictive skill adjusts trust");
  for(int i=0;i<160;i++)worse.observe(good,flat,0);check(worse.weight(100)>0.85,"trust adapts to changed accuracy");
  check(better.weight(100)<=0.90,"trust bounded");
  titanprime.PowerModel power=new titanprime.PowerModel();
  check(power.choose(3,450,100,100,0.03,0.1)<power.choose(3,450,100,100,0.8,0.1),"poor accuracy conserves energy");
  for(double baseline:new double[]{0.1,1,1.6,2.9})for(double energy:new double[]{1,10,100})for(double accuracy:new double[]{0.03,0.3,0.9}){
   double p=power.choose(baseline,450,energy,100,accuracy,0.1);check(Double.isFinite(p)&&p>=0.1&&p<=3,"legal finite firepower");
  }
  for(int i=0;i<40;i++)power.observe(2,1,true);
  check(power.probability(1,2,0.1)>power.probability(1,0,0.1),"accuracy conditioned by range");
  Probe robot=new Probe();titanprime.Brain brain=new titanprime.Brain();titanprime.Enemy enemy=new titanprime.Enemy("test",brain);enemy.y=100;enemy.time=10;
  java.lang.reflect.Field f=titanprime.class.getDeclaredField("outgoing");f.setAccessible(true);
  @SuppressWarnings("unchecked") java.util.List<titanprime.GunWave> waves=(java.util.List<titanprime.GunWave>)f.get(robot);
  java.lang.reflect.Method resolve=titanprime.class.getDeclaredMethod("resolveGunWaves",titanprime.Enemy.class);resolve.setAccessible(true);
  waves.add(wave(enemy,false,good,flat));resolve.invoke(robot,enemy);check(brain.gunTrust.count==0,"virtual visits do not calibrate firing trust");
  waves.add(wave(enemy,true,good,flat));resolve.invoke(robot,enemy);check(brain.gunTrust.count==1,"real wave calibrates trust");
  near(brain.gunTrust.localLoss,-Math.log(good[22]+good[23]+good[24]),"frozen firing forecast, not resolution-time model");
  java.lang.reflect.Method fire=titanprime.class.getDeclaredMethod("firePower",titanprime.Enemy.class,double.class);fire.setAccessible(true);
  enemy.energy=0.2;double lethal=(Double)fire.invoke(robot,enemy,500.0);check(lethal<=0.1000001,"lethal power cap preserved");
  enemy.energy=100;robot.energy=3;robot.others=2;double reserved=(Double)fire.invoke(robot,enemy,500.0);
  check(reserved>=0.1&&reserved<=robot.energy-2.5,"melee energy reserve preserved");
  robocode.Bullet bullet=new robocode.Bullet(0,0,0,2,"self","enemy",true,1);
  java.lang.reflect.Field fired=titanprime.class.getDeclaredField("fired"),telemetry=titanprime.class.getDeclaredField("telemetry");fired.setAccessible(true);telemetry.setAccessible(true);
  @SuppressWarnings("unchecked") java.util.Map<robocode.Bullet,titanprime.Brain> shots=(java.util.Map<robocode.Bullet,titanprime.Brain>)fired.get(robot);
  titanprime.Telemetry t=(titanprime.Telemetry)telemetry.get(robot);shots.put(bullet,brain);t.shot(bullet,2,300,0);
  java.lang.reflect.Method finish=titanprime.class.getDeclaredMethod("finishShot",robocode.Bullet.class,boolean.class);finish.setAccessible(true);finish.invoke(robot,bullet,true);finish.invoke(robot,bullet,true);
  check(brain.powerModel.seen==1,"resolved real bullet trains power only once");
  shots.put(bullet,brain);finish.invoke(robot,bullet,false);check(brain.powerModel.seen==1,"unknown shot range not invented");
  System.out.println(new titanprime.Telemetry().line(7));
  System.out.println("FOUR CHECKS PASSED: conditional surfing, crossfire, uncertainty, firepower, frozen trust and real bullet accounting");
 }
}
'''
def main():
 p=argparse.ArgumentParser();p.add_argument('--api',required=True,type=Path);p.add_argument('--ecj',type=Path);a=p.parse_args()
 source=four.profile((four.train.ROOT/'robots/equipe/titanprime.java').read_text(),'all')
 with tempfile.TemporaryDirectory(prefix='titan-four-') as d:
  root=Path(d);(root/'titanprime.java').write_text(source);(root/'fourchecks.java').write_text(JAVA)
  compiler=['java','-jar',str(a.ecj.resolve()),'-8','-nowarn'] if a.ecj else ['javac','-source','8','-target','8']
  subprocess.run(compiler+['-encoding','UTF-8','-cp',str(a.api.resolve()),'-d',d,str(root/'titanprime.java'),str(root/'fourchecks.java')],check=True)
  output=subprocess.check_output(['java','-cp',d+os.pathsep+str(a.api.resolve()),'equipe.fourchecks'],text=True)
 line=next(l for l in output.splitlines() if l.startswith('TELEMETRY '));assert diagnose.parse_telemetry(line,rounds=8)['round']==7
 try:diagnose.parse_telemetry(line)
 except ValueError:pass
 else:raise AssertionError('Legacy three-round decoder changed')
 print(output.splitlines()[-1])
if __name__=='__main__':main()
