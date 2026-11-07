#!/usr/bin/env python3
"""Checks numericos; gera o auxiliar Java somente em uma pasta temporaria.
Uso: python tests/core_checks.py --api /caminho/robocode/libs/robocode.jar
Sem javac: acrescente --ecj /caminho/ecj.jar.
"""
import argparse
import pathlib
import subprocess
import tempfile

CORE = r"""
package equipe;
public class corechecks {
 static void check(boolean pass,String what){if(!pass)throw new AssertionError(what);}
 static void near(double a,double b,String what){check(Math.abs(a-b)<1e-8,what);}
 public static void main(String[] args){
  near(titanprime.nextVelocity(0,100),1,"acceleration");
  near(titanprime.nextVelocity(8,0),6,"braking");
  near(titanprime.nextVelocity(-8,100),-6,"reverse braking");
  near(titanprime.nextVelocity(-1,100),0.5,"fractional reversal");
  for(int d=0;d<200;d++)for(int v=-8;v<=8;v++){double n=titanprime.nextVelocity(v,d);check(Double.isFinite(n)&&Math.abs(n)<=8,"speed bound");}
  for(double d:new double[]{0,249,250,449,450,1000})for(double v:new double[]{0,2,4,6,8})for(double a:new double[]{-1,0,1}){int s=titanprime.segment(d,v,a);check(s>=0&&s<titanprime.SEGMENTS,"segment bounds");}
  titanprime.Wave w=new titanprime.Wave();w.speed=14;w.direction=1;w.bearing=0;w.x=100;w.y=100;w.time=10;
  near(w.radius(20),140,"wave radius");near(w.radius(5),0,"wave before fire");
  near(titanprime.guessFactor(w,100,500),0,"head on GF");
  java.awt.geom.Point2D.Double p=titanprime.project(100,100,Math.asin(8.0/14),400);
  near(titanprime.guessFactor(w,p.x,p.y),1,"escape GF");w.direction=-1;near(titanprime.guessFactor(w,p.x,p.y),-1,"mirror GF");
  titanprime.Brain one=new titanprime.Brain(),two=new titanprime.Brain();
  for(int i=0;i<100;i++)titanprime.learn(one.surfGlobal,-0.8,1,0.96);
  for(int i=0;i<150;i++)titanprime.learn(one.surfGlobal,0.8,1,0.96);
  int peak=0;for(int i=0;i<titanprime.BINS;i++){check(two.surfGlobal[i]==0,"isolated enemies");if(one.surfGlobal[i]>one.surfGlobal[peak])peak=i;}
  check(peak>titanprime.BINS*0.8,"adapts to changed pattern");
  near(titanprime.threatPressure(2,0),2,"recent attacker pressure");
  near(titanprime.threatPressure(2,80),1,"threat half life");
  near(titanprime.threatPressure(2,321),0,"threat expiry");
  near(titanprime.threatPressure(20,0),4,"threat bound");
  near(titanprime.flattenAmount(7,3),0,"no flattening on tiny sample");
  near(titanprime.flattenAmount(20,0),0,"no flattening without hits");
  check(titanprime.flattenAmount(20,6)>titanprime.flattenAmount(20,2),"adaptive flattening");
  check(titanprime.flattenAmount(10,10)<=0.30,"bounded flattening");
  double[] visits=new double[titanprime.BINS];
  double[] uniform=titanprime.normalizedVisits(visits);near(uniform[0],1.0/titanprime.BINS,"smoothed empty visits");
  titanprime.learn(visits,0.7,1,0.99);double[] vd=titanprime.normalizedVisits(visits);double vs=0;
  for(double value:vd){check(Double.isFinite(value)&&value>0,"positive visit density");vs+=value;}
  near(vs,1,"normalized visit profile");check(vd[titanprime.gfBin(0.7)]>vd[titanprime.gfBin(-0.7)],"repeated angle penalized");
  titanprime.Neural net=new titanprime.Neural("");
  double[] left=new double[12],right=new double[12];left[0]=-1;right[0]=1;
  double old=net.first[0][0],before=net.predict(left)[titanprime.gfBin(-0.7)];
  for(int i=0;i<1200;i++){net.observe(left,-0.7);net.observe(right,0.7);}
  double[] lp=net.predict(left),rp=net.predict(right);double sum=0;
  for(double z:lp){check(Double.isFinite(z)&&z>=0,"finite neural probabilities");sum+=z;}
  near(sum,1,"softmax sum");check(net.first[0][0]!=old,"hidden weights learn");
  check(lp[titanprime.gfBin(-0.7)]>before*2,"loss improvement");
  check(titanprime.bestNeuralFactor(lp,0.05)<-0.4&&titanprime.bestNeuralFactor(rp,0.05)>0.4,"conditional neural learning");
  titanprime.Neural restored=new titanprime.Neural(net.pack());
  check(restored.samples==net.samples,"model samples persist");
  double[] restoredP=restored.predict(right);for(int i=0;i<rp.length;i++)check(Math.abs(rp[i]-restoredP[i])<1e-6,"model roundtrip");
  boolean rejected=false;try{new titanprime.Neural("broken");}catch(IllegalArgumentException e){rejected=true;}check(rejected,"reject corrupt model");
  System.out.println("CORE CHECKS PASSED: physics, waves, statistics, neural backpropagation, replay and model roundtrip");
 }
}
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api", required=True, type=pathlib.Path)
    parser.add_argument("--ecj", type=pathlib.Path)
    args = parser.parse_args()
    source = pathlib.Path(__file__).resolve().parents[1] / "robots/equipe/titanprime.java"
    with tempfile.TemporaryDirectory(prefix="titan-checks-") as directory:
        helper = pathlib.Path(directory) / "corechecks.java"
        helper.write_text(CORE, encoding="utf-8")
        compiler = ["java", "-jar", str(args.ecj.resolve()), "-8", "-nowarn"] if args.ecj else ["javac", "-source", "8", "-target", "8"]
        subprocess.run(compiler + ["-encoding", "UTF-8", "-cp", str(args.api.resolve()), "-d", directory, str(source), str(helper)], check=True)
        import os
        subprocess.run(["java", "-cp", directory + os.pathsep + str(args.api.resolve()), "equipe.corechecks"], check=True)


if __name__ == "__main__":
    main()
