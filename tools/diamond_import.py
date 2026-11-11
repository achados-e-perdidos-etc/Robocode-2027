#!/usr/bin/env python3
"""Rebuild the licensed single Java source from the pinned upstream tree.

Usage: python tools/diamond_import.py --upstream /path/Diamond-2d1c6a... --output /tmp/titan-import
Origin and unchanged third-party notices are retained in the generated source.
"""
from pathlib import Path
import re,json,hashlib,argparse
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--upstream',required=True,type=Path)
parser.add_argument('--output',required=True,type=Path)
args=parser.parse_args()
root=args.upstream.resolve()
out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
lex=re.compile(r'(/\*[\s\S]*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\')')
def code_only(s):return ''.join(p if i%2==0 else ' ' for i,p in enumerate(lex.split(s)))
files={}
for p in root.rglob('*.java'):
 rel=p.relative_to(root)
 if any(x in str(rel) for x in ['Test.java','Tests.java','/test/']):continue
 s=p.read_text();pkg=re.search(r'^package ([\w.]+);',s,re.M).group(1);fqn=pkg+'.'+p.stem
 files[fqn]={'path':p,'pkg':pkg,'source':s,'flat':'titanprime' if fqn=='voidious.Diamond' else fqn.replace('.','_')}
def resolve(q):
 for key in sorted(files,key=len,reverse=True):
  if q==key or q.startswith(key+'.'):return key,files[key]['flat']+q[len(key):]
 return None,q
pending=['voidious.Diamond'];used=set()
while pending:
 fqn=pending.pop()
 if fqn in used:continue
 used.add(fqn);f=files[fqn];s=f['source'];code=code_only(s)
 deps=set()
 for imp in re.findall(r'^import (?:static )?([\w.]+);',code,re.M):
  dep,_=resolve(imp)
  if dep:deps.add(dep)
 for token in set(re.findall(r'\b\w+\b',code)):
  if f['pkg']+'.'+token in files:deps.add(f['pkg']+'.'+token)
 pending+=list(deps-used)
manifest=[];bodies={}
for fqn in sorted(used):
 f=files[fqn];s=f['source'];aliases={k.rsplit('.',1)[1]:v['flat'] for k,v in files.items() if v['pkg']==f['pkg']}
 for imp in re.findall(r'^import (?:static )?([\w.]+);',s,re.M):
  dep,name=resolve(imp);aliases[imp.rsplit('.',1)[1]]=name
 s=re.sub(r'^(?:package|import) [^;]+;\s*','',s,flags=re.M)
 def transform(part):
  part=re.sub(r'\b(?:voidious|ags)(?:\.\w+)+',lambda m:resolve(m.group())[1],part)
  return re.sub(r'(?<![\w.$])([A-Za-z_$][\w$]*)',lambda m:aliases.get(m.group(),m.group()),part)
 parts=lex.split(s);s=''.join(transform(p) if i%2==0 else p for i,p in enumerate(parts))
 if fqn!='voidious.Diamond':
  s,n=re.subn(r'(?m)^((?:(?:public|abstract|final)\s+)*)class\s+'+re.escape(f['flat'])+r'\b',lambda m:m.group(1)+'static class '+f['flat'],s,count=1)
  if n==0 and not re.search(r'public (?:interface|enum) '+f['flat'],s):raise ValueError('Unmatched declaration '+fqn)
 bodies[fqn]=s
 manifest.append({'path':str(f['path'].relative_to(root)),'sha256':hashlib.sha256(f['source'].encode()).hexdigest(),'nested_name':f['flat']})
main=bodies.pop('voidious.Diamond').rstrip();assert main.endswith('}')
comment='''// Altered source: Titan Prime 4.0 integrates the open-source Diamond engine.
// Original engine by Voidious; kd-tree by Rednaxela. Original notices retained.
// Packaging, telemetry, celebration and robustness adaptations by this project.
'''
combined='package equipe;\n\n'+comment+main[:-1]+'\n'+'\n'.join(bodies[k] for k in sorted(bodies))+'\n}\n'
(out/'canonical/equipe').mkdir(parents=True,exist_ok=True);(out/'canonical/equipe/titanprime.java').write_text(combined)
(out/'flatten-manifest.json').write_text(json.dumps({'upstream_commit':'2d1c6a2b911cf077c5f13d2baa45da7d18d6babb','files':manifest,'output_sha256':hashlib.sha256(combined.encode()).hexdigest()},indent=2)+'\n')
print('Merged',len(used),'sources;',len(combined),'characters')

# Titan adaptations, separate from the upstream package transformation.
p=out/'canonical/equipe/titanprime.java'
s=p.read_text()

def change(old,new,count=1):
    global s
    assert s.count(old)==count,(old[:90],s.count(old),count)
    s=s.replace(old,new)

change('  private static double _randColors = Math.random();', '''  private static final float BATTLE_HUE = new java.util.Random().nextFloat();
  private static final String DIAGNOSTIC_VERSION = "4.0";
  private static final String DIAGNOSTIC_BATTLE =
      Long.toHexString(new java.util.Random().nextLong());
  private final java.util.Set<String> observedEnemies = new java.util.TreeSet<String>();
  private int initialOpponents, shots, hits, bulletCollisions, wallHits, skipped;
  private long decisions, edgeTicks, duelTicks, diagnosticTick;
  private double spent, dealtNominal, receivedNominal, diagnosticEnergy;
  private boolean celebrating, roundReported;
  private int celebrationFrame;''')
change('      initComponents();\n      initColors();','      initialOpponents = getOthers();\n      initComponents();\n      initColors();')
change('      while (true) {\n        _gfx.updatePaintProcessing();','''      while (true) {
        if (celebrating) {
          celebrate();
          execute();
          continue;
        }
        if (!roundReported) {
          decisions++;
          double wallDistance = Math.min(Math.min(getX(), getBattleFieldWidth() - getX()),
              Math.min(getY(), getBattleFieldHeight() - getY()));
          if (wallDistance < 80) edgeTicks++;
          if (getOthers() == 1) duelTicks++;
        }
        _gfx.updatePaintProcessing();''')
a=s.index('  private void initColors() {');b=s.index('  public void onScannedRobot(',a)
s=s[:a]+'''  private void initColors() {
    setColors(java.awt.Color.getHSBColor(BATTLE_HUE, .78f, .90f),
        java.awt.Color.getHSBColor(BATTLE_HUE + .12f, .70f, .55f),
        java.awt.Color.getHSBColor(BATTLE_HUE + .50f, .70f, 1f));
    setBulletColor(java.awt.Color.getHSBColor(BATTLE_HUE + .50f, .70f, 1f));
    setScanColor(java.awt.Color.getHSBColor(BATTLE_HUE, .45f, 1f));
  }

  private void celebrate() {
    float hue = BATTLE_HUE + celebrationFrame++ * .035f;
    setColors(java.awt.Color.getHSBColor(hue, .90f, 1f),
        java.awt.Color.getHSBColor(hue + .33f, .90f, 1f),
        java.awt.Color.getHSBColor(hue + .66f, .90f, 1f));
    setMaxVelocity(0);
    setAhead(0);
    setTurnRight(10);
    setTurnGunLeft(20);
    setTurnRadarRight(45);
  }

  @Override
  public robocode.Bullet setFireBullet(double power) {
    robocode.Bullet bullet = super.setFireBullet(power);
    if (bullet != null && !roundReported) {
      shots++;
      spent += bullet.getPower();
    }
    return bullet;
  }

'''+s[b:]
change('  public void onScannedRobot(robocode.ScannedRobotEvent e) {\n    try {','  public void onScannedRobot(robocode.ScannedRobotEvent e) {\n    observedEnemies.add(e.getName());\n    try {',count=1)
change('  public void onHitByBullet(robocode.HitByBulletEvent e) {\n    try {','  public void onHitByBullet(robocode.HitByBulletEvent e) {\n    if (!roundReported) receivedNominal += robocode.Rules.getBulletDamage(e.getPower());\n    try {')
change('  public void onBulletHit(robocode.BulletHitEvent e) {\n    try {','  public void onBulletHit(robocode.BulletHitEvent e) {\n    if (!roundReported) { hits++; dealtNominal += robocode.Rules.getBulletDamage(e.getBullet().getPower()); }\n    try {')
change('  public void onBulletHitBullet(robocode.BulletHitBulletEvent e) {\n    try {','  public void onBulletHitBullet(robocode.BulletHitBulletEvent e) {\n    if (!roundReported) bulletCollisions++;\n    try {')
change('  public void onHitWall(robocode.HitWallEvent e) {\n    System.out.println','  public void onHitWall(robocode.HitWallEvent e) {\n    if (!roundReported) wallHits++;\n    System.out.println')
change('  public void onWin(robocode.WinEvent e) {\n    try {','  public void onWin(robocode.WinEvent e) {\n    celebrating = true;\n    celebrationFrame = 0;\n    reportRound();\n    out.println("Titan Prime venceu o round!");\n    try {')
change('  public void onDeath(robocode.DeathEvent e) {\n    try {','  public void onDeath(robocode.DeathEvent e) {\n    reportRound();\n    try {')
change('  public void onSkippedTurn(robocode.SkippedTurnEvent e) {\n    System.out.println','  public void onSkippedTurn(robocode.SkippedTurnEvent e) {\n    if (!roundReported) skipped++;\n    System.out.println')

diagnostic=r'''
  public void onRoundEnded(robocode.RoundEndedEvent e) {
    reportRound();
  }

  private void reportRound() {
    if (!roundReported) {
      diagnosticTick = getTime();
      diagnosticEnergy = getEnergy();
      roundReported = true;
      out.println("TITAN_DIAGNOSTIC version=" + DIAGNOSTIC_VERSION
          + " round=" + getRoundNum() + " shots=" + shots + " hits=" + hits
          + " walls=" + wallHits + " skipped=" + skipped);
    }
    // RoundEndedEvent can precede WinEvent; replace this round's record on victory.
    saveDiagnostic();
  }

  private void saveDiagnostic() {
    String header = "version,battle,round,rounds,initial_opponents,observed_opponents,ticks,won,energy,decisions,shots,hits,spent,damage_nominal,received_nominal,bullet_collisions,walls,skipped,edge_ticks,duel_ticks,duel_shots_resolved,duel_hits_resolved\n";
    StringBuilder opponents = new StringBuilder();
    for (String name : observedEnemies) {
      if (opponents.length() > 0) opponents.append('|');
      opponents.append(name.replace("\"", "\"\""));
    }
    voidious_move_MoveEnemy duelEnemy = _move == null ? null : _move._moveDataManager.duelEnemy();
    int resolved = duelEnemy == null ? 0 : duelEnemy.raw1v1ShotsFiredThisRound;
    int resolvedHits = duelEnemy == null ? 0 : duelEnemy.raw1v1ShotsHitThisRound;
    String rowKey = DIAGNOSTIC_VERSION + "," + DIAGNOSTIC_BATTLE + "," + (getRoundNum() + 1) + ",";
    String row = rowKey + getNumRounds() + "," + initialOpponents + ",\"" + opponents + "\","
        + diagnosticTick + "," + (celebrating ? 1 : 0) + "," + diagnosticEnergy
        + "," + decisions + "," + shots + "," + hits + "," + spent + "," + dealtNominal
        + "," + receivedNominal + "," + bulletCollisions + "," + wallHits + "," + skipped
        + "," + edgeTicks + "," + duelTicks + "," + resolved + "," + resolvedHits + "\n";
    try {
      java.io.File file = getDataFile("diagnostico.csv");
      String old = "";
      if (file.exists() && file.length() <= 48000) {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream input = new java.io.FileInputStream(file)) {
          byte[] bytes = new byte[2048]; int count;
          while ((count = input.read(bytes)) != -1) buffer.write(bytes, 0, count);
        }
        String text = new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        if (text.startsWith(header)) old = text.substring(header.length());
      }
      StringBuilder kept = new StringBuilder();
      for (String entry : old.split("\\n"))
        if (!entry.isEmpty() && !entry.startsWith(rowKey)) kept.append(entry).append('\n');
      old = kept.toString();
      long budget = Math.min(48000L, file.length() + getDataQuotaAvailable());
      byte[] bytes = (header + old + row).getBytes(java.nio.charset.StandardCharsets.UTF_8);
      while (bytes.length > budget && !old.isEmpty()) {
        int newline = old.indexOf('\n');
        old = newline < 0 ? "" : old.substring(newline + 1);
        bytes = (header + old + row).getBytes(java.nio.charset.StandardCharsets.UTF_8);
      }
      if (bytes.length > budget) throw new java.io.IOException("sem cota para diagnostico");
      try (java.io.OutputStream output = new robocode.RobocodeFileOutputStream(file)) { output.write(bytes); }
    } catch (java.io.IOException e) {
      out.println("DIAGNOSTIC_ERROR " + e.getMessage());
    } catch (SecurityException e) {
      out.println("DIAGNOSTIC_ERROR acesso ao arquivo: " + e.getMessage());
    }
  }

'''
change('  protected void logAndRethrowException(RuntimeException e) {',diagnostic+'  protected void logAndRethrowException(RuntimeException e) {')

# Non-duel robustness: finite risk even at zero energy / overlapping observations.
change('moveData.energy / _robot.getEnergy(), 4)','moveData.energy / Math.max(.1, _robot.getEnergy()), 4)')
change('/ (distanceSq * (moveData.botsCloser(distanceSq * .8) + 1)','/ (Math.max(1, distanceSq) * (moveData.botsCloser(distanceSq * .8) + 1)')
change('randomRisk += 30.0 / oldLocation.location.distanceSq(destination);','randomRisk += 30.0 / Math.max(1, oldLocation.location.distanceSq(destination));')
change('''    do {
      nextDestination = safestDestination(myLocation, destinations);
      destinations.remove(nextDestination);
    } while (wouldHitWall(currentState, nextDestination));
    return nextDestination;''','''    while (!destinations.isEmpty()) {
      nextDestination = safestDestination(myLocation, destinations);
      if (!destinations.remove(nextDestination)) break;
      if (!wouldHitWall(currentState, nextDestination)) return nextDestination;
    }
    // Every candidate is unsafe: brake for one tick, then replan. Never spin forever.
    _robot.setMaxVelocity(0);
    _robot.setAhead(0);
    return new Destination(_battleField.translateToField(myLocation), 0, _robot.getHeadingRadians());''')
change('''    if (enemies.isEmpty()) {
      return;
    }

    updateRecentLocations''','''    if (enemies.isEmpty()) {
      return;
    }
    _robot.setMaxVelocity(8);

    updateRecentLocations''')
change('errorFile = errorFile.substring(CLEAR_SIZE, errorFile.length() - 1);','errorFile = errorFile.substring(Math.min(CLEAR_SIZE, errorFile.length()));')

# Keep the original license notices inside the class, with no introductory header.
declaration='public class titanprime extends robocode.AdvancedRobot {'
prefix=s[len('package equipe;\n\n'):s.index(declaration)]
s='package equipe;\n\n'+s[s.index(declaration):].replace(declaration,declaration+'\n'+prefix,1)
dest=out/'candidate/equipe/titanprime.java';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(s)
(out/'adaptation.json').write_text(json.dumps({'source_sha256':hashlib.sha256(s.encode()).hexdigest(),'version':'4.0','changes':['single-source licensed competitive engine','battle palette and victory celebration','bounded persistent CSV observer','finite melee risk','bounded melee fallback','safe error quota truncation']},indent=2)+'\n')
print(dest,hashlib.sha256(s.encode()).hexdigest())
