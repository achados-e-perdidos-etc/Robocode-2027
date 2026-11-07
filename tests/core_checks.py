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
public class CoreChecks {
 static void check(boolean pass,String what){if(!pass)throw new AssertionError(what);}
 static void near(double a,double b,String what){check(Math.abs(a-b)<1e-8,what);}
 public static void main(String[] args){
  near(Nemesis.nextVelocity(0,100),1,"acceleration");
  near(Nemesis.nextVelocity(8,0),6,"braking");
  near(Nemesis.nextVelocity(-8,100),-6,"reverse braking");
  near(Nemesis.nextVelocity(-1,100),0.5,"fractional reversal");
  for(int d=0;d<200;d++)for(int v=-8;v<=8;v++){double n=Nemesis.nextVelocity(v,d);check(Double.isFinite(n)&&Math.abs(n)<=8,"speed bound");}
  for(double d:new double[]{0,249,250,449,450,1000})for(double v:new double[]{0,2,4,6,8})for(double a:new double[]{-1,0,1}){int s=Nemesis.segment(d,v,a);check(s>=0&&s<Nemesis.SEGMENTS,"segment bounds");}
  Nemesis.Wave w=new Nemesis.Wave();w.speed=14;w.direction=1;w.bearing=0;w.x=100;w.y=100;w.time=10;
  near(w.radius(20),140,"wave radius");near(w.radius(5),0,"wave before fire");
  near(Nemesis.guessFactor(w,100,500),0,"head on GF");
  java.awt.geom.Point2D.Double p=Nemesis.project(100,100,Math.asin(8.0/14),400);
  near(Nemesis.guessFactor(w,p.x,p.y),1,"escape GF");w.direction=-1;near(Nemesis.guessFactor(w,p.x,p.y),-1,"mirror GF");
  Nemesis.Brain one=new Nemesis.Brain(),two=new Nemesis.Brain();
  for(int i=0;i<100;i++)Nemesis.learn(one.surfGlobal,-0.8,1,0.96);
  for(int i=0;i<150;i++)Nemesis.learn(one.surfGlobal,0.8,1,0.96);
  int peak=0;for(int i=0;i<Nemesis.BINS;i++){check(two.surfGlobal[i]==0,"isolated enemies");if(one.surfGlobal[i]>one.surfGlobal[peak])peak=i;}
  check(peak>Nemesis.BINS*0.8,"adapts to changed pattern");
  System.out.println("CORE CHECKS PASSED: motion limits, wave timing, GF normalization, segments, online adaptation, enemy isolation");
 }
}
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api", required=True, type=pathlib.Path)
    parser.add_argument("--ecj", type=pathlib.Path)
    args = parser.parse_args()
    source = pathlib.Path(__file__).resolve().parents[1] / "robots/equipe/Nemesis.java"
    with tempfile.TemporaryDirectory(prefix="nemesis-checks-") as directory:
        helper = pathlib.Path(directory) / "CoreChecks.java"
        helper.write_text(CORE, encoding="utf-8")
        compiler = ["java", "-jar", str(args.ecj.resolve()), "-8", "-nowarn"] if args.ecj else ["javac", "-source", "8", "-target", "8"]
        subprocess.run(compiler + ["-encoding", "UTF-8", "-cp", str(args.api.resolve()), "-d", directory, str(source), str(helper)], check=True)
        import os
        subprocess.run(["java", "-cp", directory + os.pathsep + str(args.api.resolve()), "equipe.CoreChecks"], check=True)


if __name__ == "__main__":
    main()
