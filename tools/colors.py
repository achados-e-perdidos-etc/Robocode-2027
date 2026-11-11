#!/usr/bin/env python3
"""Confere paleta por batalha e comemoracao no motor real, sem interface grafica."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import train

RUNNER = r'''
import java.io.File;
import java.util.*;
import robocode.control.*;
import robocode.control.events.*;
import robocode.control.snapshot.*;
public class colorrunner extends BattleAdaptor {
 int battle,round,ticks,base; boolean won,failed; double lastVelocity;
 Set<Integer> combat=new HashSet<Integer>(),victory=new HashSet<Integer>();
 Set<Long> body=new HashSet<Long>(),gun=new HashSet<Long>();
 public void onBattleError(BattleErrorEvent e){failed=true;System.out.println("ERROR\t"+e.getError());}
 public void onRoundStarted(RoundStartedEvent e){round=e.getRound();ticks=0;won=false;base=0;combat.clear();victory.clear();body.clear();gun.clear();}
 public void onTurnEnded(TurnEndedEvent e){
  ticks++;
  for(IRobotSnapshot r:e.getTurnSnapshot().getRobots())if(r.getName().startsWith("treino.coloraudit")){
   String output=r.getOutputStreamSnapshot();
   if(output!=null&&output.contains("Titan Prime venceu o round!"))won=true;
   if(ticks==5)base=r.getBodyColor();
   if(ticks>=5){
    if(won){victory.add(r.getBodyColor());body.add(Math.round(r.getBodyHeading()*1000));gun.add(Math.round(r.getGunHeading()*1000));}
    else combat.add(r.getBodyColor());
   }
   lastVelocity=r.getVelocity();
  }
 }
 public void onRoundEnded(RoundEndedEvent e){
  System.out.println("COLOR\t"+battle+"\t"+round+"\t"+base+"\t"+combat.size()+"\t"+victory.size()+"\t"+body.size()+"\t"+gun.size()+"\t"+won+"\t"+lastVelocity);
 }
 public void onBattleCompleted(BattleCompletedEvent e){for(robocode.BattleResults r:e.getSortedResults())System.out.println("RESULT\t"+battle+"\t"+r.getTeamLeaderName()+"\t"+r.getFirsts());}
 public static void main(String[] args){
  RobocodeEngine engine=new RobocodeEngine(new File(args[0]));
  System.out.println("VERSION\t"+engine.getVersion());
  colorrunner listener=new colorrunner();engine.addBattleListener(listener);
  RobotSpecification[] own=engine.getLocalRepository("treino.coloraudit*"),other=engine.getLocalRepository("sample.SittingDuck");
  if(own.length!=1||other.length!=1)throw new IllegalStateException("Missing exact participants");
  for(int i=0;i<3;i++){
   listener.battle=i;
   RobotSetup[] positions={new RobotSetup(200.0,300.0,90.0),new RobotSetup(600.0,300.0,270.0)};
   engine.runBattle(new BattleSpecification(new BattlefieldSpecification(800,600),3,450,0.1,100,false,new RobotSpecification[]{own[0],other[0]},positions),true);
  }
  engine.close();System.exit(listener.failed?1:0);
 }
}
'''


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',required=True,type=Path);p.add_argument('--ecj',type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--source',type=Path,default=train.ROOT/'robots/equipe/titanprime.java');a=p.parse_args()
    for key in ('home','ecj','output'):
        if getattr(a,key) is not None:setattr(a,key,getattr(a,key).resolve())
    a.output.mkdir(parents=True,exist_ok=True)
    source=a.source.read_text()
    canonical='public class titanprime extends robocode.AdvancedRobot' in source
    if canonical:
        path=a.home/'robots/equipe/titanprime.java';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source)
        train.compile_java(a,[path],a.home/'robots')
    else:
        train.install(a,'coloraudit',train.variant(source,'coloraudit',export=False))
    runner=a.output/'colorrunner.java';runner.write_text(RUNNER.replace('treino.coloraudit','equipe.titanprime') if canonical else RUNNER)
    train.compile_java(a,[runner],a.output)
    log=a.output/'colors.log'
    with log.open('w') as f:subprocess.run(['java','-Xmx768m','-Djava.awt.headless=true',*train.JAVA_OPTIONS,'-cp',str(a.output)+os.pathsep+str(a.home/'libs/*'),'colorrunner',str(a.home)],cwd=a.home,stdout=f,stderr=subprocess.STDOUT,check=True,timeout=600)
    text=log.read_text()
    if 'Exception' in text or 'ERROR\t' in text:raise ValueError('Engine error; inspect '+str(log))
    rows=[]
    for b,r,base,combat,victory,body,gun,won,velocity in re.findall(r'COLOR\t(\d+)\t(\d+)\t(-?\d+)\t(\d+)\t(\d+)\t(\d+)\t(\d+)\t(true|false)\t([^\n]+)',text):
        rows.append({'battle':int(b),'round':int(r),'base_rgb':int(base),'combat_colors':int(combat),'victory_colors':int(victory),'body_headings':int(body),'gun_headings':int(gun),'won':won=='true','final_velocity':float(velocity)})
    assert len(rows)==9 and {(r['battle'],r['round']) for r in rows}=={(b,r) for b in range(3) for r in range(3)}
    assert all(r['won'] and r['combat_colors']==1 and r['victory_colors']>=10 and r['body_headings']>=5 and r['gun_headings']>=5 and r['final_velocity']==0 for r in rows)
    palettes=[{r['base_rgb'] for r in rows if r['battle']==b} for b in range(3)]
    assert all(len(palette)==1 for palette in palettes)
    assert len(set(next(iter(p)) for p in palettes))==3
    report={'purpose':'Cosmetic integration check against passive SittingDuck; not a competitive benchmark','engine':re.findall(r'VERSION\t([^\n]+)',text)[0],'source_sha256':hashlib.sha256(source.encode()).hexdigest(),'battles':3,'rounds':rows,'checks_passed':True,'palette_note':'Random per battle; a future draw can repeat a color by chance.'}
    (a.output/'colors-results.json').write_text(json.dumps(report,indent=2)+'\n')
    print('COLORS PASSED: three battle palettes, stable across rounds, nine wins with animated colors and body/gun spins')


if __name__=='__main__':main()
