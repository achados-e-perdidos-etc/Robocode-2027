#!/usr/bin/env python3
"""Verifica fisica, KNN e recuperacao de parede da base 4.x, sem batalhas artificiais."""
import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PROBE = r'''
package equipe;
import java.awt.geom.Point2D;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import equipe.titanprime.voidious_utils_BattleField;
import equipe.titanprime.voidious_utils_MovementPredictor;
import equipe.titanprime.voidious_utils_RobotState;
import equipe.titanprime.voidious_move_MeleeMover;
import equipe.titanprime.ags_utils_KdTree;
public class eliteprobe {
  static void check(boolean ok, String why) {
    if (!ok) throw new AssertionError(why);
  }
  static class Dummy extends robocode.AdvancedRobot {
    double max=8, ahead=100;
    public double getHeadingRadians(){return Math.PI*1.5;}
    public double getVelocity(){return 8;}
    public long getTime(){return 1;}
    public double getEnergy(){return 100;}
    public int getOthers(){return 2;}
    public java.io.File getDataFile(String name){return new java.io.File(System.getProperty("java.io.tmpdir"),name);}
    public void setMaxVelocity(double value){max=value;}
    public void setAhead(double value){ahead=value;}
  }
  public static void main(String[] args) throws Exception {
    voidious_utils_MovementPredictor predictor = new voidious_utils_MovementPredictor(
        new voidious_utils_BattleField(1000,1000));
    int physical=0;
    for (String line:Files.readAllLines(Paths.get(args[0]))) {
      String[] fields=line.split(","); double[] v=new double[fields.length];
      for (int i=0;i<v.length;i++)v[i]=Double.parseDouble(fields[i]);
      voidious_utils_RobotState start=voidious_utils_RobotState.newBuilder()
          .setLocation(new Point2D.Double(v[0],v[1])).setHeading(v[2])
          .setVelocity(v[3]).setTime((long)v[4]).build();
      voidious_utils_RobotState end=predictor.predict(start,v[5],v[6],v[7],(long)(v[8]-v[4]),false);
      double angle=robocode.util.Utils.normalRelativeAngle(end.heading-v[11]);
      check(Math.abs(end.location.x-v[9])<=.01 && Math.abs(end.location.y-v[10])<=.01
          && Math.abs(angle)<=.01 && end.time==(long)v[8],"captured physics case "+physical);
      physical++;
    }
    Random rng=new Random(90173);
    ags_utils_KdTree<Integer> tree=new ags_utils_KdTree.SqrEuclid<Integer>(5,400);
    double[][] points=new double[900][5];
    for(int i=0;i<points.length;i++){
      for(int j=0;j<5;j++)points[i][j]=rng.nextDouble();
      tree.addPoint(points[i],i);
    }
    for(int q=0;q<100;q++){
      final double[] point=new double[5];
      for(int j=0;j<5;j++)point[j]=rng.nextDouble();
      List<Integer> brute=new ArrayList<Integer>();
      for(int i=500;i<900;i++)brute.add(i);
      Collections.sort(brute,new Comparator<Integer>(){
        public int compare(Integer a,Integer b){
          return Double.compare(dist(points[a],point),dist(points[b],point));
        }
      });
      Set<Integer> expected=new HashSet<Integer>(brute.subList(0,13));
      Set<Integer> actual=new HashSet<Integer>();
      for(ags_utils_KdTree.Entry<Integer> e:tree.nearestNeighbor(point,13,false))actual.add(e.value);
      check(expected.equals(actual),"KNN vs exhaustive search after FIFO eviction, query "+q);
    }
    Dummy robot=new Dummy();
    voidious_move_MeleeMover mover=new voidious_move_MeleeMover(robot,
        new voidious_utils_BattleField(800,600),new ArrayList<titanprime.voidious_gfx_RoboGraphic>());
    Point2D.Double nearWall=new Point2D.Double(19,300);
    mover.initRound(robot,nearWall);
    Method choose=voidious_move_MeleeMover.class.getDeclaredMethod("getNextDestination",Point2D.Double.class,List.class);
    choose.setAccessible(true);
    List<voidious_move_MeleeMover.Destination> candidates=new ArrayList<voidious_move_MeleeMover.Destination>();
    for(int i=0;i<64;i++)candidates.add(new voidious_move_MeleeMover.Destination(new Point2D.Double(19,300+i),i,0));
    Object result=choose.invoke(mover,nearWall,candidates);
    check(result!=null && robot.max==0 && robot.ahead==0 && candidates.isEmpty(),"all paths unsafe: bounded braking fallback");
    titanprime.voidious_utils_ErrorLogger.init(robot);
    titanprime.voidious_utils_ErrorLogger.enabled=false;
    robot.max=8;robot.ahead=100;
    candidates.add(new voidious_move_MeleeMover.Destination(new Point2D.Double(19,300),Double.NaN,0));
    result=choose.invoke(mover,nearWall,candidates);
    check(result!=null && robot.max==0 && robot.ahead==0,"unrankable candidate: fallback still terminates");
    System.out.println("PHYSICS="+physical+" KNN=100 BOUNDED_WALL_FALLBACK=2");
  }
  static double dist(double[] a,double[] b){double d=0;for(int j=0;j<a.length;j++)d+=(a[j]-b[j])*(a[j]-b[j]);return d;}
}
'''


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--api',required=True,type=Path)
    p.add_argument('--ecj',type=Path)
    p.add_argument('--source',type=Path,default=ROOT/'robots/equipe/titanprime.java')
    p.add_argument('--output',type=Path)
    a=p.parse_args()
    data=json.loads((ROOT/'tests/elite-physics.json').read_text())
    with tempfile.TemporaryDirectory() as tmp:
        temp=Path(tmp);classes=temp/'classes';classes.mkdir()
        probe=temp/'eliteprobe.java'
        probe_code=PROBE
        if 'class titan_utils_BattleField' in a.source.read_text():
            for before,after in [('voidious_','titan_'),('ags_utils_KdTree','titan_KdTree'),('ags_utils_','titan_estruturas_'),('DiamondWhoosh','Movimento'),('DiamondFist','Mira'),('DiamondEyes','Radar'),('DiamondColors','Cores')]:
                probe_code=probe_code.replace(before,after)
        probe.write_text(probe_code)
        fixture=temp/'physics.csv'
        with fixture.open('w') as f:csv.writer(f).writerows(data['cases'])
        compiler=['java','-jar',str(a.ecj),'-8','-nowarn'] if a.ecj else ['javac','--release','8']
        subprocess.run(compiler+['-encoding','UTF-8','-cp',str(a.api.resolve()),'-d',str(classes),str(a.source.resolve()),str(probe)],check=True,timeout=90)
        result=subprocess.run(['java','-cp',str(classes)+os.pathsep+str(a.api.resolve()),'equipe.eliteprobe',str(fixture)],text=True,capture_output=True,check=True,timeout=30)
        major=int.from_bytes((classes/'equipe/titanprime.class').read_bytes()[6:8],'big')
        assert major==52
        report=dict(passed=True,source_sha256=hashlib.sha256(a.source.read_bytes()).hexdigest(),
                    api_sha256=hashlib.sha256(a.api.read_bytes()).hexdigest(),
                    captured_physics_cases=len(data['cases']),knn_exhaustive_comparisons=100,
                    bounded_wall_fallback=True,unrankable_candidates_terminate=True,class_major=major,
                    output=result.stdout.strip(),reference=data['origin'])
        if a.output:a.output.write_text(json.dumps(report,indent=2)+'\n')
        print(json.dumps(report,indent=2))


if __name__=='__main__':main()
