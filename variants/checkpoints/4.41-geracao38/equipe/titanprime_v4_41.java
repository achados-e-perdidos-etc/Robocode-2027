package equipe;

public class titanprime_v4_41 extends robocode.AdvancedRobot {

  private static final boolean _TC = false;
  private static final boolean _MC = false;
  private static final boolean _LOG_ERRORS = true;

  protected static titan_radar_Radar _radar;
  protected static titan_move_Movimento _move;
  private static titan_gun_Mira _gun;
  protected static titan_gfx_Cores _gfx;
  private static final float BATTLE_HUE = new java.util.Random().nextFloat();
  private static final String DIAGNOSTIC_VERSION = "4.41";
  private static final String ROBOT_DISPLAY_NAME = "Titan Prime " + DIAGNOSTIC_VERSION;
  private static final String DIAGNOSTIC_BATTLE =
      Long.toHexString(new java.util.Random().nextLong());
  private final java.util.Set<String> observedEnemies = new java.util.TreeSet<String>();
  private int initialOpponents, shots, hits, bulletCollisions, wallHits, skipped;
  private long lastRealShot = -100;
  private long decisions, edgeTicks, duelTicks, diagnosticTick;
  private double spent, dealtNominal, receivedNominal, diagnosticEnergy;
  private boolean celebrating, roundReported;
  private int celebrationFrame;
  private int policyShots, policyHits, strongShots;
  private int policyStrongResolved, policyStrongHits;
  private final java.util.Set<robocode.Bullet> policyStrongBullets = new java.util.HashSet<robocode.Bullet>();
  private String policyTarget = "";
  private final java.util.Set<robocode.Bullet> policyBullets = new java.util.HashSet<robocode.Bullet>();
  private int bodyCollisions;
  private double bulletBonuses, observedPassiveDrain;
  private final EnergyBalance energyBalance = new EnergyBalance();
  private final BulletShield shield = new BulletShield(this);

  private void prepareShield() {
    shield.prepare();
  }

  static final class BulletShield {
    final titanprime_v4_41 robot;
    final java.util.Set<robocode.Bullet> ownBullets = new java.util.HashSet<robocode.Bullet>();
    boolean active, disabled, aborted;
    boolean opening = true;
    long activeTicks, attempts, shieldShots, collisions, received;
    long warmupTicks, openingTicks, activationTime = -1, firstReceivedTime = -1, openingReleaseTime = -1;
    long stationarySince = -1, handledFireTime = Long.MIN_VALUE;
    java.awt.geom.Point2D.Double previousLocation, stepTarget;
    double stepHeading;
    int stepSign = 1;
    titan_utils_Wave targetWave;
    ShieldPlan pending;

    BulletShield(titanprime_v4_41 robot) { this.robot = robot; }

    double plannedPower() { return pending == null ? .1 : pending.power; }

    boolean holdNormalFire() { return opening || robot.getTime() <= openingReleaseTime; }

    void receivedHit() {
      if (active) {
        if (firstReceivedTime < 0) firstReceivedTime = robot.getTime();
        if (++received >= 3) stop(true);
      }
    }

    void stop(boolean failure) {
      active = false;
      disabled = true;
      aborted |= failure;
      pending = null;
      targetWave = null;
      stepTarget = null;
      robot.setMaxVelocity(8);
    }

    void prepare() {
      if (disabled || robot.initialOpponents != 1 || robot.getOthers() != 1) {
        opening = false;
        return;
      }
      long now = robot.getTime();
      java.awt.geom.Point2D.Double p = new java.awt.geom.Point2D.Double(robot.getX(), robot.getY());
      boolean stationary = Math.abs(robot.getVelocity()) < 1e-8
          && (previousLocation == null || previousLocation.distanceSq(p) < 1e-12);
      previousLocation = p;
      if (stationary && stepTarget == null) {
        if (stationarySince < 0) stationarySince = now;
      } else stationarySince = -1;
      titan_move_MoveEnemy enemy = _move._moveDataManager.duelEnemy();
      if (opening) {
        titan_utils_Wave firstShot = null;
        if (enemy != null) {
          for (titan_utils_Wave w : enemy._waveManager._waves) {
            if (w.firingWave && !w.altWave && w.fireTime <= now
                && (firstShot == null || w.fireTime < firstShot.fireTime)) firstShot = w;
          }
        }
        int decision = firstShot == null
            ? openingDecision(now, -1, 0, 0, 0, robot.getEnergy(), stationarySince)
            : openingDecision(now, firstShot.fireTime, firstShot.bulletPower(),
                firstShot.sourceLocation.distance(firstShot.targetLocation),
                enemy.distance, robot.getEnergy(), stationarySince);
        if (decision == 3) {
          warmupTicks++;
          return;
        }
        if (decision == 1) {
          openingTicks++;
          robot.setMaxVelocity(0);
          robot.setAhead(0);
          holdPerpendicular(enemy, p);
          return;
        }
        opening = false;
        openingReleaseTime = now;
        if (decision == 2) {
          active = true;
          activationTime = now;
        } else return;
      }
      if (enemy == null || enemy.lastScanState == null) return;
      if (!active) {
        if (robot.getEnergy() <= 35 || enemy.distance < 275
            || enemy.lastScanState.time < now - 1) return;
        boolean eligible = false;
        for (titan_utils_Wave w : enemy._waveManager._waves) {
          long age = now - w.fireTime;
          if (w.firingWave && !w.altWave && !w.processedBulletHit()
              && age >= 0 && age <= 4 && w.bulletPower() >= 2.4
              && w.sourceLocation.distance(w.targetLocation) >= 275) {
            eligible = true;
            break;
          }
        }
        if (!eligible) return;
        active = true;
        activationTime = now;
      }
      activeTicks++;
      if (enemy.energy <= .2 && !hasLiveWave(enemy, p, now)) {
        stop(false);
        return;
      }

      if (stepTarget != null) {
        double remaining = (stepTarget.x - p.x) * Math.sin(stepHeading)
            + (stepTarget.y - p.y) * Math.cos(stepHeading);
        robot.setTurnRightRadians(robocode.util.Utils.normalRelativeAngle(
            stepHeading - robot.getHeadingRadians()));
        robot.setMaxVelocity(2);
        robot.setAhead(remaining);
        if (stepTarget.distanceSq(p) < 1e-8 && Math.abs(robot.getVelocity()) < 1e-8) {
          stepTarget = null;
          stationarySince = now;
          robot.setMaxVelocity(0);
          robot.setAhead(0);
        }
        return;
      }

      robot.setMaxVelocity(0);
      robot.setAhead(0);
      holdPerpendicular(enemy, p);
      if (targetWave != null || stationarySince < 0) return;

      titan_utils_Wave candidate = null;
      for (titan_utils_Wave w : enemy._waveManager._waves) {
        long age = now - w.fireTime;
        if (w.firingWave && !w.altWave && !w.processedBulletHit()
            && w.fireTime > handledFireTime && age >= 0 && age <= 6
            && w.fireTime >= stationarySince + 3 && Math.abs(w.targetVelocity) < .01
            && (candidate == null || w.fireTime < candidate.fireTime)) candidate = w;
      }
      if (candidate == null) return;
      double rayX = candidate.targetLocation.x - candidate.sourceLocation.x;
      double rayY = candidate.targetLocation.y - candidate.sourceLocation.y;
      double rayLength = Math.hypot(rayX, rayY);
      if (!Double.isFinite(rayLength) || rayLength < 1e-9) return;
      double heading = robot.getHeadingRadians();
      java.awt.geom.Point2D.Double next = titan_utils_Matematica.project(p, heading, stepSign * 8);
      double lateral = Math.abs((next.x - candidate.sourceLocation.x) * rayY / rayLength
          - (next.y - candidate.sourceLocation.y) * rayX / rayLength);
      if (lateral < 4 || next.x < 20 || next.y < 20
          || next.x > robot.getBattleFieldWidth() - 20
          || next.y > robot.getBattleFieldHeight() - 20) return;
      targetWave = candidate;
      handledFireTime = candidate.fireTime;
      attempts++;
      stepHeading = heading;
      stepTarget = next;
      robot.setTurnRightRadians(0);
      robot.setMaxVelocity(2);
      robot.setAhead(stepSign * 8);
      stepSign = -stepSign;
      stationarySince = -1;
    }

    void holdPerpendicular(titan_move_MoveEnemy enemy, java.awt.geom.Point2D.Double p) {
      if (enemy == null || enemy.lastScanState == null) {
        robot.setTurnRightRadians(0);
        return;
      }
      double bearing = titan_utils_Matematica.absoluteBearing(p, enemy.lastScanState.location);
      double perpendicular = bearing + Math.PI / 2;
      double opposite = perpendicular + Math.PI;
      if (Math.abs(robocode.util.Utils.normalRelativeAngle(opposite - robot.getHeadingRadians()))
          < Math.abs(robocode.util.Utils.normalRelativeAngle(perpendicular - robot.getHeadingRadians()))) {
        perpendicular = opposite;
      }
      robot.setTurnRightRadians(robocode.util.Utils.normalRelativeAngle(
          perpendicular - robot.getHeadingRadians()));
    }

    static int openingDecision(long now, long fireTime, double power, double shotDistance,
        double currentDistance, double ownEnergy, long stationarySince) {
      if (fireTime < 0) return now < 20 ? 3 : now < 35 ? 1 : 0;
      if (!Double.isFinite(power) || !Double.isFinite(shotDistance)
          || !Double.isFinite(currentDistance) || !Double.isFinite(ownEnergy)) return 0;
      long age = now - fireTime;
      return age >= 0 && age <= 4 && power >= 2.4 && shotDistance >= 275
          && currentDistance >= 275 && ownEnergy > 35 && stationarySince >= 0
          && fireTime >= stationarySince + 3 ? 2 : 0;
    }

    boolean hasLiveWave(titan_move_MoveEnemy enemy, java.awt.geom.Point2D.Double p, long now) {
      for (titan_utils_Wave w : enemy._waveManager._waves) {
        if (w.firingWave && !w.altWave && !w.processedBulletHit()
            && w.sourceLocation.distance(p) - w.distanceTraveled(now)
                > -titan_utils_Wave.MAX_BOT_RADIUS - w.bulletSpeed()) return true;
      }
      return false;
    }

    void aimAndFire(titan_gun_Mira gun) {
      long now = robot.getTime();
      if (stepTarget != null || targetWave == null) {
        titan_move_MoveEnemy enemy = _move._moveDataManager.duelEnemy();
        if (enemy != null) {
          double angle = titan_utils_Matematica.absoluteBearing(
              new java.awt.geom.Point2D.Double(robot.getX(), robot.getY()), enemy.lastScanState.location);
          robot.setTurnGunRightRadians(robocode.util.Utils.normalRelativeAngle(
              angle - robot.getGunHeadingRadians()));
        }
        return;
      }
      if (targetWave.processedBulletHit()) { pending = null; targetWave = null; return; }
      if (pending != null && pending.fireTime == now && robot.getGunHeat() == 0
          && Math.abs(robocode.util.Utils.normalRelativeAngle(
              pending.angle - robot.getGunHeadingRadians())) < 1e-9
          && Math.hypot(robot.getX() - pending.x, robot.getY() - pending.y) < 1e-6
          && Math.abs(robot.getVelocity()) < 1e-8 && robot.getEnergy() > pending.power) {
        robocode.Bullet bullet = gun.setFireBulletLogged(pending.power);
        if (bullet != null) {
          ownBullets.add(bullet);
          shieldShots++;
          titan_gun_GunEnemy enemy = gun._gunDataManager.duelEnemy();
          if (enemy != null) {
            titan_utils_Wave firingWave = enemy._waveManager.getWaveByFireTime(now);
            if (firingWave != null) firingWave.setBulletPower(pending.power);
          }
          gun._gunDataManager.markFiringWaves(now, true);
          pending = null;
          targetWave = null;
          return;
        }
      }
      pending = null;
      if (robot.getGunHeat() > robot.getGunCoolingRate() + 1e-9) return;
      double[] result = solveShieldIntercept(targetWave.sourceLocation.x,
          targetWave.sourceLocation.y, targetWave.targetLocation.x,
          targetWave.targetLocation.y, targetWave.bulletSpeed(),
          now + 1 - targetWave.fireTime, robot.getX(), robot.getY(), .1);
      if (result != null && phaseMargin(result[1]) < .15) {
        for (int i = 1; i <= 40; i++) {
          double[] alternative = solveShieldIntercept(targetWave.sourceLocation.x,
              targetWave.sourceLocation.y, targetWave.targetLocation.x,
              targetWave.targetLocation.y, targetWave.bulletSpeed(),
              now + 1 - targetWave.fireTime, robot.getX(), robot.getY(), .1 + i * .01);
          if (alternative != null && phaseMargin(alternative[1]) > phaseMargin(result[1])) {
            result = alternative;
          }
        }
      }
      if (result == null) { targetWave = null; return; }
      pending = new ShieldPlan(now + 1, robot.getX(), robot.getY(), result[0], result[4]);
      robot.setTurnGunRightRadians(robocode.util.Utils.normalRelativeAngle(
          pending.angle - robot.getGunHeadingRadians()));
    }

    static double phaseMargin(double time) {
      double fraction = time - Math.floor(time);
      return Math.min(fraction, 1 - fraction);
    }

    static double[] solveShieldIntercept(double sx, double sy, double ax, double ay,
        double enemySpeed, double elapsed, double px, double py, double power) {
      double[] inputs = {sx, sy, ax, ay, enemySpeed, elapsed, px, py, power};
      for (double input : inputs) if (!Double.isFinite(input)) return null;
      if (enemySpeed < 11 || enemySpeed > 19.7 || elapsed < 0 || power < .1 || power > .5) return null;
      double length = Math.hypot(ax - sx, ay - sy);
      if (length < 1e-9) return null;
      double dx = (ax - sx) / length, dy = (ay - sy) / length;
      double lateral = Math.abs((px - sx) * dy - (py - sy) * dx);
      if (lateral < 4) return null;
      double bx = sx + enemySpeed * elapsed * dx;
      double by = sy + enemySpeed * elapsed * dy;
      double qx = bx - px, qy = by - py;
      double speed = 20 - 3 * power;
      double a = enemySpeed * enemySpeed - speed * speed;
      double b = 2 * enemySpeed * (qx * dx + qy * dy);
      double c = qx * qx + qy * qy;
      double time = Double.POSITIVE_INFINITY;
      if (Math.abs(a) < 1e-9) {
        if (b < 0) time = -c / b;
      } else {
        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0 || !Double.isFinite(discriminant)) return null;
        double root = Math.sqrt(discriminant);
        double first = (-b - root) / (2 * a), second = (-b + root) / (2 * a);
        if (first > 0) time = first;
        if (second > 0) time = Math.min(time, second);
      }
      if (!Double.isFinite(time) || time <= 0 || speed * time < 60) return null;
      double entry = Double.NEGATIVE_INFINITY, exit = Double.POSITIVE_INFINITY;
      double[] positions = {bx, by}, centers = {px, py};
      double[] velocities = {enemySpeed * dx, enemySpeed * dy};
      for (int axis = 0; axis < 2; axis++) {
        if (Math.abs(velocities[axis]) < 1e-12) {
          if (Math.abs(positions[axis] - centers[axis]) > 18) return null;
        } else {
          double first = (centers[axis] - 18 - positions[axis]) / velocities[axis];
          double second = (centers[axis] + 18 - positions[axis]) / velocities[axis];
          entry = Math.max(entry, Math.min(first, second));
          exit = Math.min(exit, Math.max(first, second));
        }
      }
      if (exit < Math.max(0, entry) || entry <= time + 1) return null;
      double ix = bx + enemySpeed * time * dx, iy = by + enemySpeed * time * dy;
      if (!Double.isFinite(ix) || !Double.isFinite(iy)) return null;
      return new double[] {Math.atan2(ix - px, iy - py), time, ix, iy, power};
    }

    void report() {
      robot.out.println("TITAN_SHIELD round=" + (robot.getRoundNum() + 1)
          + " active_ticks=" + activeTicks + " attempts=" + attempts + " shots=" + shieldShots
          + " collisions=" + collisions + " received=" + received + " aborted=" + aborted
          + " warmup_ticks=" + warmupTicks + " opening_ticks=" + openingTicks + " activation_time=" + activationTime
          + " first_received_time=" + firstReceivedTime);
    }
  }

  static final class ShieldPlan {
    final long fireTime;
    final double x, y, angle, power;
    ShieldPlan(long fireTime, double x, double y, double angle, double power) {
      this.fireTime = fireTime; this.x = x; this.y = y; this.angle = angle; this.power = power;
    }
  }

  private double _maxVelocity = robocode.Rules.MAX_VELOCITY;

  static {
    titan_utils_ErrorLogger.enabled = _LOG_ERRORS;
  }

  public void run() {
    try {
      out.println(ROBOT_DISPLAY_NAME + " | codigo carregado");
      titan_utils_ErrorLogger.init(this);
      initialOpponents = getOthers();
      titan_utils_Wave.deadlineQueries = titan_utils_Wave.deadlineBuilds = titan_utils_Wave.deadlineFilteredIntervals = 0;
      initComponents();
      initColors();

      setAdjustGunForRobotTurn(true);
      setAdjustRadarForGunTurn(true);

      while (!roundFinished) {
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
        _gfx.updatePaintProcessing();
        if (!_TC) {
          _move.execute();
          prepareShield();
        }
        if (!_MC) {
          _gun.execute();
        }
        _radar.execute();
        execute();
      }
    } catch (RuntimeException re) {
      logAndRethrowException(re);
    }
  }

  private void initComponents() {
    if (_radar == null) {
      _radar = new titan_radar_Radar(this, System.out);
    }
    if (_move == null) {
      _move = new titan_move_Movimento(this, System.out);
    }
    if (_gun == null) {
      _gun = new titan_gun_Mira(this, _TC);
      _gun.addFireListener(_move);
    }
    if (_gfx == null) {
      _gfx = new titan_gfx_Cores(this, _radar, _move, _gun, _TC, _MC);
      _gfx.registerPainter("r", _radar);
      _gfx.registerPainter("g", _gun);
      _gfx.registerPainter("m", _move);
    }

    _radar.initRound(this);
    _move.initRound(this);
    _gun.initRound(this);
    loadBattleMemory();
    out.println("TITAN_ROUND_OWNER round=" + (getRoundNum() + 1)
        + " movement=" + (_move.getRobot() == this)
        + " surf=" + (_move._surfMover._robot == this)
        + " max_velocity=" + getMaxVelocity());
  }

  private void initColors() {
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
    if (bullet != null) lastRealShot = getTime();
    if (bullet != null && getOthers() == 1 && !roundReported) {
      policyShots++; policyBullets.add(bullet);
      if (bullet.getPower() > .7000001) policyStrongBullets.add(bullet);
    }
    if (bullet != null && !roundReported) {
      shots++;
      spent += bullet.getPower();
      if (bullet.getPower() > .7000001) strongShots++;
    }
    return bullet;
  }

  public void onScannedRobot(robocode.ScannedRobotEvent e) {
    if (observedEnemies.add(e.getName())) MEMORY_SEEN.put(e.getName(), memoryGeneration + 1);
    if (getOthers() == 1 && !e.getName().equals(policyTarget)) {
      policyTarget = e.getName(); policyShots = policyHits = 0; policyBullets.clear();
      policyStrongResolved = policyStrongHits = 0; policyStrongBullets.clear();
    }
    observedPassiveDrain = energyBalance.observe(e.getTime(), getOthers(), e.getName(),
        getEnergy(), spent, receivedNominal, bulletBonuses, wallHits + bodyCollisions);
    try {
      _radar.onScannedRobot(e);
      if (!_TC) {
        _move.onScannedRobot(e);
      }
      if (!_MC) {
        _gun.onScannedRobot(e);
      }
    } catch (RuntimeException re) {
      logAndRethrowException(re);
    }
  }

  public void onRobotDeath(robocode.RobotDeathEvent e) {
    try {
      _radar.onRobotDeath(e);
      if (!_TC) {
        _move.onRobotDeath(e);
      }
      if (!_MC) {
        _gun.onRobotDeath(e);
      }
    } catch (RuntimeException re) {
      logAndRethrowException(re);
    }
  }

  public void onHitByBullet(robocode.HitByBulletEvent e) {
    shield.receivedHit();
    if (recordDiagnosticEvent(e.getTime())) receivedNominal += robocode.Rules.getBulletDamage(e.getPower());
    try {
      if (!_TC) {
        _move.onHitByBullet(e);
      }
    } catch (RuntimeException re) {
      logAndRethrowException(re);
    }
    if (roundReported && recordDiagnosticEvent(e.getTime())) saveDiagnostic();
  }

  public void onBulletHit(robocode.BulletHitEvent e) {
    shield.ownBullets.remove(e.getBullet());
    settleStrongBullet(e.getBullet(), e.getName().equals(policyTarget));
    if (policyBullets.remove(e.getBullet()) && e.getName().equals(policyTarget)) policyHits++;

    if (recordDiagnosticEvent(e.getTime())) { hits++; dealtNominal += robocode.Rules.getBulletDamage(e.getBullet().getPower()); bulletBonuses += robocode.Rules.getBulletHitBonus(e.getBullet().getPower()); }
    try {
      if (!_TC) {
        _move.onBulletHit(e);
      }
      if (!_MC) {
        _gun.onBulletHit(e);
      }
    } catch (RuntimeException re) {
      logAndRethrowException(re);
    }
    if (roundReported && recordDiagnosticEvent(e.getTime())) saveDiagnostic();
  }

  public void onBulletMissed(robocode.BulletMissedEvent e) {
    shield.ownBullets.remove(e.getBullet());
    settleStrongBullet(e.getBullet(), false);
    policyBullets.remove(e.getBullet());

    if (!_TC) _move.onBulletMissed(e);
  }

  public void onBulletHitBullet(robocode.BulletHitBulletEvent e) {
    if (shield.ownBullets.remove(e.getBullet())) shield.collisions++;
    settleStrongBullet(e.getBullet(), false);
    policyBullets.remove(e.getBullet());

    if (recordDiagnosticEvent(e.getTime())) bulletCollisions++;
    try {
      if (!_TC) {
        _move.onBulletHitBullet(e);
      }
      if (!_MC) {
        _gun.onBulletHitBullet(e);
      }
    } catch (RuntimeException re) {
      logAndRethrowException(re);
    }
    if (roundReported && recordDiagnosticEvent(e.getTime())) saveDiagnostic();
  }

  public void onHitWall(robocode.HitWallEvent e) {
    if (recordDiagnosticEvent(e.getTime())) wallHits++;
    System.out.println("WARNING: I hit a wall (" + getTime() + ").");
    if (roundReported && recordDiagnosticEvent(e.getTime())) saveDiagnostic();
  }

  public void onHitRobot(robocode.HitRobotEvent e) {
    if (recordDiagnosticEvent(e.getTime())) bodyCollisions++;
  }

  public void onWin(robocode.WinEvent e) {
    celebrating = true;
    celebrationFrame = 0;
    reportRound();
    out.println(ROBOT_DISPLAY_NAME + " venceu o round!");
    try {
      if (!_MC) {
        _gun.onWin(e);
      }
      if (!_TC) {
        _move.onWin(e);
      }
    } catch (RuntimeException re) {
      logAndRethrowException(re);
    }
    for (int frame = 0; frame < 64; frame++) {
      celebrate();
      execute();
    }
    saveBattleMemory();
    roundFinished = true;
  }

  public void onDeath(robocode.DeathEvent e) {
    roundFinished = true;
    reportRound();
    try {
      if (!_MC) {
        _gun.onDeath(e);
      }
      if (!_TC) {
        _move.onDeath(e);
      }
    } catch (RuntimeException re) {
      logAndRethrowException(re);
    }
    saveBattleMemory();
  }

  public void onPaint(java.awt.Graphics2D g) {
    try {
      _gfx.onPaint(g);
    } catch (RuntimeException re) {
      logAndRethrowException(re);
    }
  }

  public void onKeyPressed(java.awt.event.KeyEvent e) {
    try {
      _gfx.onKeyPressed(e);
    } catch (RuntimeException re) {
      logAndRethrowException(re);
    }
  }

  public void onSkippedTurn(robocode.SkippedTurnEvent e) {
    if (recordDiagnosticEvent(e.getTime())) skipped++;
    System.out.println("WARNING: Turn skipped at: " + e.getTime());
    if (roundReported && recordDiagnosticEvent(e.getTime())) saveDiagnostic();
  }

  public void onRoundEnded(robocode.RoundEndedEvent e) {
    reportRound();
    if ((getRoundNum() + 1) % 5 == 0 || getRoundNum() + 1 == getNumRounds()) saveBattleMemory();
    roundFinished = true;
  }

  public void onBattleEnded(robocode.BattleEndedEvent e) {
    saveBattleMemory();
    roundFinished = true;
  }

  private void settleStrongBullet(robocode.Bullet bullet, boolean hit) {
    if (policyStrongBullets.remove(bullet)) {
      policyStrongResolved++;
      if (hit) policyStrongHits++;
    }
  }

  private boolean recordDiagnosticEvent(long tick) {
    return !roundReported || tick <= diagnosticTick;
  }

  private void reportRound() {
    if (!roundReported) {
      shield.report();
      out.println("TITAN_ENERGY passive_drain=" + energyBalance.totalDrain + " body_collisions=" + bodyCollisions);
      out.println("TITAN_FIRE_POLICY round=" + (getRoundNum() + 1)
          + " shots=" + policyShots + " hits=" + policyHits);
      out.println("TITAN_POWER_ACCURACY round=" + (getRoundNum() + 1)
          + " strong_resolved=" + policyStrongResolved + " strong_hits=" + policyStrongHits);
      out.println("TITAN_PROJECTILES round=" + (getRoundNum() + 1)
          + " retired_hits=" + _move._moveDataManager.retiredHits
          + " retired_misses=" + _move._moveDataManager.retiredMisses
          + " retired_collisions=" + _move._moveDataManager.retiredCollisions);
      out.println("TITAN_SHADOW_DEADLINE round=" + (getRoundNum() + 1)
          + " queries=" + titan_utils_Wave.deadlineQueries
          + " builds=" + titan_utils_Wave.deadlineBuilds
          + " filtered_intervals=" + titan_utils_Wave.deadlineFilteredIntervals);
      titan_move_MoveEnemy powerData = _move._moveDataManager.duelEnemy();
      out.println("TITAN_CONFIDENCE round=" + (getRoundNum() + 1) + " strong_shots=" + strongShots
          + (powerData == null ? "" : " power_shots=" + java.util.Arrays.toString(powerData.contextShots)
              + " power_hits=" + java.util.Arrays.toString(powerData.contextHits)
              + " context_queries=" + powerData.contextQueries + " rescued_queries=" + powerData.confidenceRescues));
      diagnosticTick = getTime();
      diagnosticEnergy = getEnergy();
      roundReported = true;
      out.println("TITAN_DIAGNOSTIC version=" + DIAGNOSTIC_VERSION
          + " round=" + getRoundNum() + " shots=" + shots + " hits=" + hits
          + " walls=" + wallHits + " skipped=" + skipped);
    }

    saveDiagnostic();
  }

  private void saveDiagnostic() {
    String header = "version,battle,round,rounds,initial_opponents,observed_opponents,ticks,won,energy,decisions,shots,hits,spent,damage_nominal,received_nominal,bullet_collisions,walls,skipped,edge_ticks,duel_ticks,duel_shots_resolved,duel_hits_resolved\n";
    StringBuilder opponents = new StringBuilder();
    for (String name : observedEnemies) {
      if (opponents.length() > 0) opponents.append('|');
      opponents.append(name.replace("\"", "\"\""));
    }
    titan_move_MoveEnemy duelEnemy = _move == null ? null : _move._moveDataManager.duelEnemy();
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

  protected void logAndRethrowException(RuntimeException e) {
    String moreInfo = "getOthers(): " + getOthers() + "\n"
        + "getEnemiesAlive(): " + _gun.getEnemiesAlive() + "\n"
        + "getRoundNum(): " + getRoundNum() + "\n" + "getTime(): " + getTime();
    titan_utils_ErrorLogger.getInstance().logException(e, moreInfo);

    throw e;
  }

  public void setMaxVelocity(double maxVelocity) {
    super.setMaxVelocity(maxVelocity);
    _maxVelocity = maxVelocity;
  }

  public double getMaxVelocity() {
    return _maxVelocity;
  }

public abstract static class titan_KdTree<T> {

    private static final int           bucketSize = 24;

    private final int                  dimensions;
    private final titan_KdTree<T>            parent;

    private final java.util.LinkedList<double[]> locationStack;
    private final Integer              sizeLimit;

    private double[][]                 locations;
    private Object[]                   data;
    private int                        locationCount;

    private titan_KdTree<T>                  left, right;
    private int                        splitDimension;
    private double                     splitValue;

    private double[]                   minLimit, maxLimit;
    private boolean                    singularity;

    private Status                     status;

    private titan_KdTree(int dimensions, Integer sizeLimit) {
        this.dimensions = dimensions;

        this.locations = new double[bucketSize][];
        this.data = new Object[bucketSize];
        this.locationCount = 0;
        this.singularity = true;

        this.parent = null;
        this.sizeLimit = sizeLimit;
        if (sizeLimit != null) {
            this.locationStack = new java.util.LinkedList<double[]>();
        }
        else {
            this.locationStack = null;
        }
    }

    private titan_KdTree(titan_KdTree<T> parent, boolean right) {
        this.dimensions = parent.dimensions;

        this.locations = new double[Math.max(bucketSize, parent.locationCount)][];
        this.data = new Object[Math.max(bucketSize, parent.locationCount)];
        this.locationCount = 0;
        this.singularity = true;

        this.parent = parent;
        this.locationStack = null;
        this.sizeLimit = null;
    }

    public int size() {
        return locationCount;
    }

    public void addPoint(double[] location, T value) {
        titan_KdTree<T> cursor = this;

        while (cursor.locations == null || cursor.locationCount >= cursor.locations.length) {
            if (cursor.locations != null) {
                cursor.splitDimension = cursor.findWidestAxis();
                cursor.splitValue = (cursor.minLimit[cursor.splitDimension] + cursor.maxLimit[cursor.splitDimension]) * 0.5;

                if (cursor.splitValue == Double.POSITIVE_INFINITY) {
                    cursor.splitValue = Double.MAX_VALUE;
                }
                else if (cursor.splitValue == Double.NEGATIVE_INFINITY) {
                    cursor.splitValue = -Double.MAX_VALUE;
                }
                else if (Double.isNaN(cursor.splitValue)) {
                    cursor.splitValue = 0;
                }

                if (cursor.minLimit[cursor.splitDimension] == cursor.maxLimit[cursor.splitDimension]) {
                    double[][] newLocations = new double[cursor.locations.length * 2][];
                    System.arraycopy(cursor.locations, 0, newLocations, 0, cursor.locationCount);
                    cursor.locations = newLocations;
                    Object[] newData = new Object[newLocations.length];
                    System.arraycopy(cursor.data, 0, newData, 0, cursor.locationCount);
                    cursor.data = newData;
                    break;
                }

                if (cursor.splitValue == cursor.maxLimit[cursor.splitDimension]) {
                    cursor.splitValue = cursor.minLimit[cursor.splitDimension];
                }

                titan_KdTree<T> left = new ChildNode(cursor, false);
                titan_KdTree<T> right = new ChildNode(cursor, true);

                for (int i = 0; i < cursor.locationCount; i++) {
                    double[] oldLocation = cursor.locations[i];
                    Object oldData = cursor.data[i];
                    if (oldLocation[cursor.splitDimension] > cursor.splitValue) {

                        right.locations[right.locationCount] = oldLocation;
                        right.data[right.locationCount] = oldData;
                        right.locationCount++;
                        right.extendBounds(oldLocation);
                    }
                    else {

                        left.locations[left.locationCount] = oldLocation;
                        left.data[left.locationCount] = oldData;
                        left.locationCount++;
                        left.extendBounds(oldLocation);
                    }
                }

                cursor.left = left;
                cursor.right = right;
                cursor.locations = null;
                cursor.data = null;
            }

            cursor.locationCount++;
            cursor.extendBounds(location);

            if (location[cursor.splitDimension] > cursor.splitValue) {
                cursor = cursor.right;
            }
            else {
                cursor = cursor.left;
            }
        }

        cursor.locations[cursor.locationCount] = location;
        cursor.data[cursor.locationCount] = value;
        cursor.locationCount++;
        cursor.extendBounds(location);

        if (this.sizeLimit != null) {
            this.locationStack.add(location);
            if (this.locationCount > this.sizeLimit) {
                this.removeOld();
            }
        }
    }

    private final void extendBounds(double[] location) {
        if (minLimit == null) {
            minLimit = new double[dimensions];
            System.arraycopy(location, 0, minLimit, 0, dimensions);
            maxLimit = new double[dimensions];
            System.arraycopy(location, 0, maxLimit, 0, dimensions);
            return;
        }

        for (int i = 0; i < dimensions; i++) {
            if (Double.isNaN(location[i])) {
                minLimit[i] = Double.NaN;
                maxLimit[i] = Double.NaN;
                singularity = false;
            }
            else if (minLimit[i] > location[i]) {
                minLimit[i] = location[i];
                singularity = false;
            }
            else if (maxLimit[i] < location[i]) {
                maxLimit[i] = location[i];
                singularity = false;
            }
        }
    }

    private final int findWidestAxis() {
        int widest = 0;
        double width = (maxLimit[0] - minLimit[0]) * getAxisWeightHint(0);
        if (Double.isNaN(width)) width = 0;
        for (int i = 1; i < dimensions; i++) {
            double nwidth = (maxLimit[i] - minLimit[i]) * getAxisWeightHint(i);
            if (Double.isNaN(nwidth)) nwidth = 0;
            if (nwidth > width) {
                widest = i;
                width = nwidth;
            }
        }
        return widest;
    }

    private void removeOld() {
        double[] location = this.locationStack.removeFirst();
        titan_KdTree<T> cursor = this;

        while (cursor.locations == null) {
            if (location[cursor.splitDimension] > cursor.splitValue) {
                cursor = cursor.right;
            }
            else {
                cursor = cursor.left;
            }
        }

        for (int i = 0; i < cursor.locationCount; i++) {
            if (cursor.locations[i] == location) {
                System.arraycopy(cursor.locations, i + 1, cursor.locations, i, cursor.locationCount - i - 1);
                cursor.locations[cursor.locationCount-1] = null;
                System.arraycopy(cursor.data, i + 1, cursor.data, i, cursor.locationCount - i - 1);
                cursor.data[cursor.locationCount-1] = null;
                do {
                    cursor.locationCount--;
                    cursor = cursor.parent;
                } while (cursor != null && cursor.parent != null);
                return;
            }
        }

    }

    private static enum Status {
        NONE, LEFTVISITED, RIGHTVISITED, ALLVISITED
    }

    public static class Entry<T> {
        public final double distance;
        public final T      value;

        private Entry(double distance, T value) {
            this.distance = distance;
            this.value = value;
        }
    }

    @SuppressWarnings("unchecked")
    public java.util.List<Entry<T>> nearestNeighbor(double[] location, int count, boolean sequentialSorting) {
        titan_KdTree<T> cursor = this;
        cursor.status = Status.NONE;
        double range = Double.POSITIVE_INFINITY;
        ResultHeap resultHeap = new ResultHeap(count);

        do {
            if (cursor.status == Status.ALLVISITED) {

                cursor = cursor.parent;
                continue;
            }

            if (cursor.status == Status.NONE && cursor.locations != null) {

                if (cursor.locationCount > 0) {
                    if (cursor.singularity) {
                        double dist = pointDist(cursor.locations[0], location);
                        if (dist <= range) {
                            for (int i = 0; i < cursor.locationCount; i++) {
                                resultHeap.addValue(dist, cursor.data[i]);
                            }
                        }
                    }
                    else {
                        for (int i = 0; i < cursor.locationCount; i++) {
                            double dist = pointDist(cursor.locations[i], location);
                            resultHeap.addValue(dist, cursor.data[i]);
                        }
                    }
                    range = resultHeap.getMaxDist();
                }

                if (cursor.parent == null) {
                    break;
                }
                cursor = cursor.parent;
                continue;
            }

            titan_KdTree<T> nextCursor = null;
            if (cursor.status == Status.NONE) {

                if (location[cursor.splitDimension] > cursor.splitValue) {

                    nextCursor = cursor.right;
                    cursor.status = Status.RIGHTVISITED;
                }
                else {

                    nextCursor = cursor.left;
                    cursor.status = Status.LEFTVISITED;
                }
            }
            else if (cursor.status == Status.LEFTVISITED) {

                nextCursor = cursor.right;
                cursor.status = Status.ALLVISITED;
            }
            else if (cursor.status == Status.RIGHTVISITED) {

                nextCursor = cursor.left;
                cursor.status = Status.ALLVISITED;
            }

            if (cursor.status == Status.ALLVISITED) {
                if (nextCursor.locationCount == 0
                        || (!nextCursor.singularity && pointRegionDist(location, nextCursor.minLimit,
                                nextCursor.maxLimit) > range)) {
                    continue;
                }
            }

            cursor = nextCursor;
            cursor.status = Status.NONE;
        } while (cursor.parent != null || cursor.status != Status.ALLVISITED);

        java.util.ArrayList<Entry<T>> results = new java.util.ArrayList<Entry<T>>(resultHeap.values);
        if (sequentialSorting) {
            while (resultHeap.values > 0) {
                resultHeap.removeLargest();
                results.add(new Entry<T>(resultHeap.removedDist, (T)resultHeap.removedData));
            }
        }
        else {
            for (int i = 0; i < resultHeap.values; i++) {
                results.add(new Entry<T>(resultHeap.distance[i], (T)resultHeap.data[i]));
            }
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    public java.util.List<Entry<T>> farthestNeighbor(double[] location, int count, boolean sequentialSorting) {
        return farthestNeighbor(location, count, sequentialSorting, Double.POSITIVE_INFINITY);
    }

    public java.util.List<Entry<T>> farthestNeighbor(double[] location, int count, boolean sequentialSorting, double cutoff) {
        titan_KdTree<T> cursor = this;
        cursor.status = Status.NONE;
        double range = Double.NEGATIVE_INFINITY;
        ResultHeap resultHeap = new ReverseResultHeap(count);

        do {
            if (cursor.status == Status.ALLVISITED) {

                cursor = cursor.parent;
                continue;
            }

            if (cursor.status == Status.NONE && cursor.locations != null) {

                if (cursor.locationCount > 0) {
                    if (cursor.singularity) {
                        double dist = pointDist(cursor.locations[0], location);
                        if (dist >= range) {
                            for (int i = 0; i < cursor.locationCount; i++) {
                                resultHeap.addValue(dist, cursor.data[i]);
                            }
                        }
                    }
                    else {
                        for (int i = 0; i < cursor.locationCount; i++) {
                            double dist = pointDist(cursor.locations[i], location);
                            resultHeap.addValue(dist, cursor.data[i]);
                        }
                    }
                    range = resultHeap.getMaxDist();
                    if (range > cutoff)
                        break;
                }

                if (cursor.parent == null) {
                    break;
                }
                cursor = cursor.parent;
                continue;
            }

            titan_KdTree<T> nextCursor = null;
            if (cursor.status == Status.NONE) {

                if (location[cursor.splitDimension] < cursor.splitValue) {

                    nextCursor = cursor.right;
                    cursor.status = Status.RIGHTVISITED;
                }
                else {

                    nextCursor = cursor.left;
                    cursor.status = Status.LEFTVISITED;
                }
            }
            else if (cursor.status == Status.LEFTVISITED) {

                nextCursor = cursor.right;
                cursor.status = Status.ALLVISITED;
            }
            else if (cursor.status == Status.RIGHTVISITED) {

                nextCursor = cursor.left;
                cursor.status = Status.ALLVISITED;
            }

            if (cursor.status == Status.ALLVISITED) {
                if (nextCursor.locationCount == 0
                        || (!nextCursor.singularity &&
                            pointRegionMaxDist(location, nextCursor.minLimit,
                                nextCursor.maxLimit) < range)) {
                    continue;
                }
            }

            cursor = nextCursor;
            cursor.status = Status.NONE;
        } while (cursor.parent != null || cursor.status != Status.ALLVISITED);

        java.util.ArrayList<Entry<T>> results = new java.util.ArrayList<Entry<T>>(resultHeap.values);
        if (sequentialSorting) {
            while (resultHeap.values > 0) {
                resultHeap.removeLargest();
                results.add(new Entry<T>(resultHeap.removedDist, (T)resultHeap.removedData));
            }
        }
        else {
            for (int i = 0; i < resultHeap.values; i++) {
                results.add(new Entry<T>(resultHeap.distance[i], (T)resultHeap.data[i]));
            }
        }

        return results;
    }

    protected abstract double pointDist(double[] p1, double[] p2);

    protected abstract double pointRegionDist(double[] point, double[] min, double[] max);
    protected abstract double pointRegionMaxDist(double[] point, double[] min, double[] max);

    protected double getAxisWeightHint(int i) {
        return 1.0;
    }

    private class ChildNode extends titan_KdTree<T> {
        private ChildNode(titan_KdTree<T> parent, boolean right) {
            super(parent, right);
        }

        protected double pointDist(double[] p1, double[] p2) {
            throw new IllegalStateException();
        }

        protected double pointRegionDist(double[] point, double[] min, double[] max) {
            throw new IllegalStateException();
        }

        protected double pointRegionMaxDist(double[] point, double[] min, double[] max) {
            throw new IllegalStateException();
        }
    }

    public static class WeightedSqrEuclid<T> extends titan_KdTree<T> {
        private double[] weights;

        public WeightedSqrEuclid(int dimensions, Integer sizeLimit) {
            super(dimensions, sizeLimit);
            this.weights = new double[dimensions];
            java.util.Arrays.fill(this.weights, 1.0);
        }

        public void setWeights(double[] weights) {
            this.weights = weights;
        }

        protected double getAxisWeightHint(int i) {
            return weights[i];
        }

        protected double pointDist(double[] p1, double[] p2) {
            double d = 0;

            for (int i = 0; i < p1.length; i++) {
                double diff = (p1[i] - p2[i]) * weights[i];
                if (!Double.isNaN(diff)) {
                    d += diff * diff;
                }
            }

            return d;
        }

        protected double pointRegionDist(double[] point, double[] min, double[] max) {
            double d = 0;

            for (int i = 0; i < point.length; i++) {
                double diff = 0;
                if (point[i] > max[i]) {
                    diff = (point[i] - max[i]) * weights[i];
                }
                else if (point[i] < min[i]) {
                    diff = (point[i] - min[i]) * weights[i];
                }

                if (!Double.isNaN(diff)) {
                    d += diff * diff;
                }
            }

            return d;
        }

        protected double pointRegionMaxDist(double[] point, double[] min, double[] max) {
            double d = 0;

            for (int i = 0; i < point.length; i++) {
                double diff = Math.max(Math.abs(point[i] - min[i]),
                    Math.abs(max[i] - point[i])) * weights[i];

                if (!Double.isNaN(diff)) {
                    d += diff * diff;
                }
            }

            return d;
        }
    }

    public static class SqrEuclid<T> extends titan_KdTree<T> {
        public SqrEuclid(int dimensions, Integer sizeLimit) {
            super(dimensions, sizeLimit);
        }

        protected double pointDist(double[] p1, double[] p2) {
            double d = 0;

            for (int i = 0; i < p1.length; i++) {
                double diff = (p1[i] - p2[i]);
                if (!Double.isNaN(diff)) {
                    d += diff * diff;
                }
            }

            return d;
        }

        protected double pointRegionDist(double[] point, double[] min, double[] max) {
            double d = 0;

            for (int i = 0; i < point.length; i++) {
                double diff = 0;
                if (point[i] > max[i]) {
                    diff = (point[i] - max[i]);
                }
                else if (point[i] < min[i]) {
                    diff = (point[i] - min[i]);
                }

                if (!Double.isNaN(diff)) {
                    d += diff * diff;
                }
            }

            return d;
        }

        protected double pointRegionMaxDist(double[] point, double[] min, double[] max) {
            throw new IllegalStateException();
        }
    }

    public static class WeightedManhattan<T> extends titan_KdTree<T> {
        private double[] weights;

        public WeightedManhattan(int dimensions, Integer sizeLimit) {
            super(dimensions, sizeLimit);
            this.weights = new double[dimensions];
            java.util.Arrays.fill(this.weights, 1.0);
        }

        public void setWeights(double[] weights) {
            this.weights = weights;
        }

        protected double getAxisWeightHint(int i) {
            return weights[i];
        }

        protected double pointDist(double[] p1, double[] p2) {
            double d = 0;

            for (int i = 0; i < p1.length; i++) {
                double diff = (p1[i] - p2[i]);
                if (!Double.isNaN(diff)) {
                    d += ((diff < 0) ? -diff : diff) * weights[i];
                }
            }

            return d;
        }

        protected double pointRegionDist(double[] point, double[] min, double[] max) {
            double d = 0;

            for (int i = 0; i < point.length; i++) {
                double diff = 0;
                if (point[i] > max[i]) {
                    diff = (point[i] - max[i]);
                }
                else if (point[i] < min[i]) {
                    diff = (min[i] - point[i]);
                }

                if (!Double.isNaN(diff)) {
                    d += diff * weights[i];
                }
            }

            return d;
        }

        protected double pointRegionMaxDist(double[] point, double[] min, double[] max) {
            throw new IllegalStateException();
        }
    }

    public static class Manhattan<T> extends titan_KdTree<T> {
        public Manhattan(int dimensions, Integer sizeLimit) {
            super(dimensions, sizeLimit);
        }

        protected double pointDist(double[] p1, double[] p2) {
            double d = 0;

            for (int i = 0; i < p1.length; i++) {
                double diff = (p1[i] - p2[i]);
                if (!Double.isNaN(diff)) {
                    d += (diff < 0) ? -diff : diff;
                }
            }

            return d;
        }

        protected double pointRegionDist(double[] point, double[] min, double[] max) {
            double d = 0;

            for (int i = 0; i < point.length; i++) {
                double diff = 0;
                if (point[i] > max[i]) {
                    diff = (point[i] - max[i]);
                }
                else if (point[i] < min[i]) {
                    diff = (min[i] - point[i]);
                }

                if (!Double.isNaN(diff)) {
                    d += diff;
                }
            }

            return d;
        }

        protected double pointRegionMaxDist(double[] point, double[] min, double[] max) {
            throw new IllegalStateException();
        }
    }

    private static class ResultHeap {
        protected final Object[] data;
        protected final double[] distance;
        protected final int      size;
        protected int            values;
        public Object          removedData;
        public double          removedDist;

        public ResultHeap(int size) {
            this.data = new Object[size];
            this.distance = new double[size];
            this.size = size;
            this.values = 0;
        }

        public void addValue(double dist, Object value) {

            if (values < size) {

                data[values] = value;
                distance[values] = dist;
                upHeapify(values);
                values++;
            }

            else if (dist < distance[0]) {

                data[0] = value;
                distance[0] = dist;
                downHeapify(0);
            }
        }

        public void removeLargest() {
            if (values == 0) {
                throw new IllegalStateException();
            }

            removedData = data[0];
            removedDist = distance[0];
            values--;
            data[0] = data[values];
            distance[0] = distance[values];
            downHeapify(0);
        }

        protected void upHeapify(int c) {
            for (int p = (c - 1) / 2; c != 0 && distance[c] > distance[p]; c = p, p = (c - 1) / 2) {
                Object pData = data[p];
                double pDist = distance[p];
                data[p] = data[c];
                distance[p] = distance[c];
                data[c] = pData;
                distance[c] = pDist;
            }
        }

        protected void downHeapify(int p) {
            for (int c = p * 2 + 1; c < values; p = c, c = p * 2 + 1) {
                if (c + 1 < values && distance[c] < distance[c + 1]) {
                    c++;
                }
                if (distance[p] < distance[c]) {

                    Object pData = data[p];
                    double pDist = distance[p];
                    data[p] = data[c];
                    distance[p] = distance[c];
                    data[c] = pData;
                    distance[c] = pDist;
                }
                else {
                    break;
                }
            }
        }

        public double getMaxDist() {
            if (values < size) {
                return Double.POSITIVE_INFINITY;
            }
            return distance[0];
        }
    }

    private static class ReverseResultHeap extends ResultHeap {
        public ReverseResultHeap(int size) {
            super(size);
        }

        public void addValue(double dist, Object value) {

            if (values < size) {

                data[values] = value;
                distance[values] = dist;
                upHeapify(values);
                values++;
            }

            else if (dist > distance[0]) {

                data[0] = value;
                distance[0] = dist;
                downHeapify(0);
            }
        }

        protected void upHeapify(int c) {
            for (int p = (c - 1) / 2; c != 0 && distance[c] < distance[p]; c = p, p = (c - 1) / 2) {
                Object pData = data[p];
                double pDist = distance[p];
                data[p] = data[c];
                distance[p] = distance[c];
                data[c] = pData;
                distance[c] = pDist;
            }
        }

        protected void downHeapify(int p) {
            for (int c = p * 2 + 1; c < values; p = c, c = p * 2 + 1) {
                if (c + 1 < values && distance[c] > distance[c + 1]) {
                    c++;
                }
                if (distance[p] > distance[c]) {

                    Object pData = data[p];
                    double pDist = distance[p];
                    data[p] = data[c];
                    distance[p] = distance[c];
                    data[c] = pData;
                    distance[c] = pDist;
                }
                else {
                    break;
                }
            }
        }
    }
}

public abstract static class titan_estruturas_dataStructures_BinaryHeap<T> {
    protected static final int defaultCapacity = 64;
    private final int direction;
    private Object[] data;
    private double[] keys;
    private int capacity;
    private int size;

    protected titan_estruturas_dataStructures_BinaryHeap(int capacity, int direction) {
        this.direction = direction;
        this.data = new Object[capacity];
        this.keys = new double[capacity];
        this.capacity = capacity;
        this.size = 0;
    }

    public void offer(double key, T value) {

        if (size >= capacity) {
            capacity *= 2;
            data = java.util.Arrays.copyOf(data, capacity);
            keys = java.util.Arrays.copyOf(keys, capacity);
        }

        data[size] = value;
        keys[size] = key;
        siftUp(size);
        size++;
    }

    protected void removeTip() {
        if (size == 0) {
            throw new IllegalStateException();
        }

        size--;
        data[0] = data[size];
        keys[0] = keys[size];
        data[size] = null;
        siftDown(0);
    }

    protected void replaceTip(double key, T value) {
        if (size == 0) {
            throw new IllegalStateException();
        }

        data[0] = value;
        keys[0] = key;
        siftDown(0);
    }

    @SuppressWarnings("unchecked")
    protected T getTip() {
        if (size == 0) {
            throw new IllegalStateException();
        }

        return (T) data[0];
    }

    protected double getTipKey() {
        if (size == 0) {
            throw new IllegalStateException();
        }

        return keys[0];
    }

    private void siftUp(int c) {
        for (int p = (c - 1) / 2; c != 0 && direction*keys[c] > direction*keys[p]; c = p, p = (c - 1) / 2) {
            Object pData = data[p];
            double pDist = keys[p];
            data[p] = data[c];
            keys[p] = keys[c];
            data[c] = pData;
            keys[c] = pDist;
        }
    }

    private void siftDown(int p) {
        for (int c = p * 2 + 1; c < size; p = c, c = p * 2 + 1) {
            if (c + 1 < size && direction*keys[c] < direction*keys[c + 1]) {
                c++;
            }
            if (direction*keys[p] < direction*keys[c]) {

                Object pData = data[p];
                double pDist = keys[p];
                data[p] = data[c];
                keys[p] = keys[c];
                data[c] = pData;
                keys[c] = pDist;
            } else {
                break;
            }
        }
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return capacity;
    }

    public static final class Max<T> extends titan_estruturas_dataStructures_BinaryHeap<T> implements titan_estruturas_dataStructures_MaxHeap<T> {
        public Max() {
            super(defaultCapacity, 1);
        }
        public Max(int capacity) {
            super(capacity, 1);
        }
        public void removeMax() {
            removeTip();
        }
        public void replaceMax(double key, T value) {
            replaceTip(key, value);
        }
        public T getMax() {
            return getTip();
        }
        public double getMaxKey() {
            return getTipKey();
        }
    }
    public static final class Min<T> extends titan_estruturas_dataStructures_BinaryHeap<T> implements titan_estruturas_dataStructures_MinHeap<T> {
        public Min() {
            super(defaultCapacity, -1);
        }
        public Min(int capacity) {
            super(capacity, -1);
        }
        public void removeMin() {
            removeTip();
        }
        public void replaceMin(double key, T value) {
            replaceTip(key, value);
        }
        public T getMin() {
            return getTip();
        }
        public double getMinKey() {
            return getTipKey();
        }
    }
}

public static class titan_estruturas_dataStructures_IntervalHeap<T> implements titan_estruturas_dataStructures_MinHeap<T>, titan_estruturas_dataStructures_MaxHeap<T> {
    private static final int defaultCapacity = 64;
    private Object[] data;
    private double[] keys;
    private int capacity;
    private int size;

    public titan_estruturas_dataStructures_IntervalHeap() {
        this(defaultCapacity);
    }

    public titan_estruturas_dataStructures_IntervalHeap(int capacity) {
        this.data = new Object[capacity];
        this.keys = new double[capacity];
        this.capacity = capacity;
        this.size = 0;
    }

    public void offer(double key, T value) {

        if (size >= capacity) {
            capacity *= 2;
            data = java.util.Arrays.copyOf(data, capacity);
            keys = java.util.Arrays.copyOf(keys, capacity);
        }

        size++;
        data[size-1] = value;
        keys[size-1] = key;
        siftInsertedValueUp();
    }

    public void removeMin() {
        if (size == 0) {
            throw new IllegalStateException();
        }

        size--;
        data[0] = data[size];
        keys[0] = keys[size];
        data[size] = null;
        siftDownMin(0);
    }

    public void replaceMin(double key, T value) {
        if (size == 0) {
            throw new IllegalStateException();
        }

        data[0] = value;
        keys[0] = key;
        if (size > 1) {

            if (keys[1] < key) {
                swap(0, 1);
            }
            siftDownMin(0);
        }
    }

    public void removeMax() {
        if (size == 0) {
            throw new IllegalStateException();
        } else if (size == 1) {
            removeMin();
            return;
        }

        size--;
        data[1] = data[size];
        keys[1] = keys[size];
        data[size] = null;
        siftDownMax(1);
    }

    public void replaceMax(double key, T value) {
        if (size == 0) {
            throw new IllegalStateException();
        } else if (size == 1) {
            replaceMin(key, value);
            return;
        }

        data[1] = value;
        keys[1] = key;

        if (key < keys[0]) {
            swap(0, 1);
        }
        siftDownMax(1);
    }

    @SuppressWarnings("unchecked")
    public T getMin() {
        if (size == 0) {
            throw new IllegalStateException();
        }

        return (T) data[0];
    }

    @SuppressWarnings("unchecked")
    public T getMax() {
        if (size == 0) {
            throw new IllegalStateException();
        } else if (size == 1) {
            return (T) data[0];
        }

        return (T) data[1];
    }

    public double getMinKey() {
        if (size == 0) {
            throw new IllegalStateException();
        }

        return keys[0];
    }

    public double getMaxKey() {
        if (size == 0) {
            throw new IllegalStateException();
        } else if (size == 1) {
            return keys[0];
        }

        return keys[1];
    }

    private int swap(int x, int y) {
        Object yData = data[y];
        double yDist = keys[y];
        data[y] = data[x];
        keys[y] = keys[x];
        data[x] = yData;
        keys[x] = yDist;
        return y;
    }

    private void siftInsertedValueUp() {
        int u = size-1;
        if (u == 0) {

        }
        else if (u == 1) {

            if  (keys[u] < keys[u-1]) {
                swap(u, u-1);
            }
        }
        else if (u % 2 == 1) {

            int p = (u/2-1)|1;
            if  (keys[u] < keys[u-1]) {
                u = swap(u, u-1);
                if (keys[u] < keys[p-1]) {

                    u = swap(u, p-1);
                    siftUpMin(u);
                }
            } else {
                if (keys[u] > keys[p]) {

                    u = swap(u, p);
                    siftUpMax(u);
                }
            }
        } else {

            int p = (u/2-1)|1;
            if (keys[u] > keys[p]) {

                u = swap(u, p);
                siftUpMax(u);
            } else if (keys[u] < keys[p-1]) {

                u = swap(u, p-1);
                siftUpMin(u);
            }
        }
    }

    private void siftUpMin(int c) {

        for (int p = (c/2-1)&~1; p >= 0 && keys[c] < keys[p]; c = p, p = (c/2-1)&~1) {
            swap(c, p);
        }
    }

    private void siftUpMax(int c) {

        for (int p = (c/2-1)|1; p >= 0 && keys[c] > keys[p]; c = p, p = (c/2-1)|1) {
            swap(c, p);
        }
    }

    private void siftDownMin(int p) {
        for (int c = p * 2 + 2; c < size; p = c, c = p * 2 + 2) {
            if (c + 2 < size && keys[c + 2] < keys[c]) {
                c += 2;
            }
            if (keys[c] < keys[p]) {
                swap(p, c);

                if (c+1 < size && keys[c+1] < keys[c]) {
                    swap(c, c+1);
                }
            } else {
                break;
            }
        }
    }

    private void siftDownMax(int p) {
        for (int c = p * 2 + 1; c <= size; p = c, c = p * 2 + 1) {
            if (c == size) {

                if (keys[c - 1] > keys[p]) {
                    swap(p, c - 1);
                }
                break;
            } else if (c + 2 == size) {

                if (keys[c + 1] > keys[c]) {
                    if (keys[c + 1] > keys[p]) {
                       swap(p, c + 1);
                    }
                    break;
                }
            } else if (c + 2 < size) {

                if (keys[c + 2] > keys[c]) {
                    c += 2;
                }
            }
            if (keys[c] > keys[p]) {
                swap(p, c);

                if (keys[c-1] > keys[c]) {
                    swap(c, c-1);
                }
            } else {
                break;
            }
        }
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return capacity;
    }

    @Override
    public String toString() {
        java.text.DecimalFormat twoPlaces = new java.text.DecimalFormat("0.00");
        StringBuffer str = new StringBuffer(titan_estruturas_dataStructures_IntervalHeap.class.getCanonicalName());
        str.append(", size: ").append(size()).append(" capacity: ").append(capacity());
        int i = 0, p = 2;
        while (i < size()) {
            int x = 0;
            str.append("\t");
            while ((i+x) < size() && x < p) {
                str.append(twoPlaces.format(keys[i + x])).append(", ");
                x++;
            }
            str.append("\n");
            i += x;
            p *= 2;
        }
        return str.toString();
    }

    private boolean validateHeap() {

        for (int i = 0; i < size-1; i+= 2) {
            if (keys[i] > keys[i+1]) return false;
        }

        for (int i = 2; i < size; i++) {
            double maxParent = keys[(i/2-1)|1];
            double minParent = keys[(i/2-1)&~1];
            if (keys[i] > maxParent || keys[i] < minParent) return false;
        }
        return true;
    }
}

public interface titan_estruturas_dataStructures_MaxHeap<T> {
    public int size();
    public void offer(double key, T value);
    public void replaceMax(double key, T value);
    public void removeMax();
    public T getMax();
    public double getMaxKey();
}

public interface titan_estruturas_dataStructures_MinHeap<T> {
    public int size();
    public void offer(double key, T value);
    public void replaceMin(double key, T value);
    public void removeMin();
    public T getMin();
    public double getMinKey();
}

public interface titan_estruturas_dataStructures_trees_thirdGenKD_DistanceFunction {
    public double distance(double[] p1, double[] p2);
    public double distanceToRect(double[] point, double[] min, double[] max);
}

static class titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T> {

    protected int dimensions;
    protected int bucketCapacity;
    protected int size;

    protected double[][] points;
    protected Object[] data;

    protected titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T> left, right;
    protected int splitDimension;
    protected double splitValue;

    protected double[] minBound, maxBound;
    protected boolean singlePoint;

    protected titan_estruturas_dataStructures_trees_thirdGenKD_KdNode(int dimensions, int bucketCapacity) {

        this.dimensions = dimensions;
        this.bucketCapacity = bucketCapacity;
        this.size = 0;
        this.singlePoint = true;

        this.points = new double[bucketCapacity+1][];
        this.data = new Object[bucketCapacity+1];
    }

    public int size() {
        return size;
    }

    public boolean isLeaf() {
        return points != null;
    }

    public void addPoint(double[] point, T value) {
        titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T> cursor = this;
        while (!cursor.isLeaf()) {
            cursor.extendBounds(point);
            cursor.size++;
            if (point[cursor.splitDimension] > cursor.splitValue) {
                cursor = cursor.right;
            }
            else {
                cursor = cursor.left;
            }
        }
        cursor.addLeafPoint(point, value);
    }

    public void addLeafPoint(double[] point, T value) {

        points[size] = point;
        data[size] = value;
        extendBounds(point);
        size++;

        if (size == points.length - 1) {

            if (calculateSplit()) {

                splitLeafNode();
            } else {

                increaseLeafCapacity();
            }
        }
    }

    private boolean checkBounds(double[] point) {
        for (int i = 0; i < dimensions; i++) {
            if (point[i] > maxBound[i]) return false;
            if (point[i] < minBound[i]) return false;
        }
        return true;
    }

    private void extendBounds(double[] point) {
        if (minBound == null) {
            minBound = java.util.Arrays.copyOf(point, dimensions);
            maxBound = java.util.Arrays.copyOf(point, dimensions);
            return;
        }

        for (int i = 0; i < dimensions; i++) {
            if (Double.isNaN(point[i])) {
                if (!Double.isNaN(minBound[i]) || !Double.isNaN(maxBound[i])) {
                    singlePoint = false;
                }
                minBound[i] = Double.NaN;
                maxBound[i] = Double.NaN;
            }
            else if (minBound[i] > point[i]) {
                minBound[i] = point[i];
                singlePoint = false;
            }
            else if (maxBound[i] < point[i]) {
                maxBound[i] = point[i];
                singlePoint = false;
            }
        }
    }

    private void increaseLeafCapacity() {
        points = java.util.Arrays.copyOf(points, points.length*2);
        data = java.util.Arrays.copyOf(data, data.length*2);
    }

    private boolean calculateSplit() {
        if (singlePoint) return false;

        double width = 0;
        for (int i = 0; i < dimensions; i++) {
            double dwidth = (maxBound[i] - minBound[i]);
            if (Double.isNaN(dwidth)) dwidth = 0;
            if (dwidth > width) {
                splitDimension = i;
                width = dwidth;
            }
        }

        if (width == 0) {
            return false;
        }

        splitValue = (minBound[splitDimension] + maxBound[splitDimension]) * 0.5;

        if (splitValue == Double.POSITIVE_INFINITY) {
            splitValue = Double.MAX_VALUE;
        }
        else if (splitValue == Double.NEGATIVE_INFINITY) {
            splitValue = -Double.MAX_VALUE;
        }

        if (splitValue == maxBound[splitDimension]) {
            splitValue = minBound[splitDimension];
        }

        return true;
    }

    private void splitLeafNode() {
        right = new titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T>(dimensions, bucketCapacity);
        left = new titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T>(dimensions, bucketCapacity);

        for (int i = 0; i < size; i++) {
            double[] oldLocation = points[i];
            Object oldData = data[i];
            if (oldLocation[splitDimension] > splitValue) {
                right.addLeafPoint(oldLocation, (T) oldData);
            }
            else {
                left.addLeafPoint(oldLocation, (T) oldData);
            }
        }

        points = null;
        data = null;
    }
}

public static class titan_estruturas_dataStructures_trees_thirdGenKD_KdTree<T> extends titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T> {
    public titan_estruturas_dataStructures_trees_thirdGenKD_KdTree(int dimensions) {
        this(dimensions, 24);
    }

    public titan_estruturas_dataStructures_trees_thirdGenKD_KdTree(int dimensions, int bucketCapacity) {
        super(dimensions, bucketCapacity);
    }

    public titan_estruturas_dataStructures_trees_thirdGenKD_NearestNeighborIterator<T> getNearestNeighborIterator(double[] searchPoint, int maxPointsReturned, titan_estruturas_dataStructures_trees_thirdGenKD_DistanceFunction distanceFunction) {
        return new titan_estruturas_dataStructures_trees_thirdGenKD_NearestNeighborIterator<T>(this, searchPoint, maxPointsReturned, distanceFunction);
    }

    public titan_estruturas_dataStructures_MaxHeap<T> findNearestNeighbors(double[] searchPoint, int maxPointsReturned, titan_estruturas_dataStructures_trees_thirdGenKD_DistanceFunction distanceFunction) {
        titan_estruturas_dataStructures_BinaryHeap.Min<titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T>> pendingPaths = new titan_estruturas_dataStructures_BinaryHeap.Min<titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T>>();
        titan_estruturas_dataStructures_BinaryHeap.Max<T> evaluatedPoints = new titan_estruturas_dataStructures_BinaryHeap.Max<T>();
        int pointsRemaining = Math.min(maxPointsReturned, size());
        pendingPaths.offer(0, this);

        while (pendingPaths.size() > 0 && (evaluatedPoints.size() < pointsRemaining || (pendingPaths.getMinKey() < evaluatedPoints.getMaxKey()))) {
            nearestNeighborSearchStep(pendingPaths, evaluatedPoints, pointsRemaining, distanceFunction, searchPoint);
        }

        return evaluatedPoints;
    }

    @SuppressWarnings("unchecked")
    protected static <T> void nearestNeighborSearchStep (
            titan_estruturas_dataStructures_MinHeap<titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T>> pendingPaths, titan_estruturas_dataStructures_MaxHeap<T> evaluatedPoints, int desiredPoints,
            titan_estruturas_dataStructures_trees_thirdGenKD_DistanceFunction distanceFunction, double[] searchPoint) {

        titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T> cursor = pendingPaths.getMin();
        pendingPaths.removeMin();

        while (!cursor.isLeaf()) {
            titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T> pathNotTaken;
            if (searchPoint[cursor.splitDimension] > cursor.splitValue) {
                pathNotTaken = cursor.left;
                cursor = cursor.right;
            }
            else {
                pathNotTaken = cursor.right;
                cursor = cursor.left;
            }
            double otherDistance = distanceFunction.distanceToRect(searchPoint, pathNotTaken.minBound, pathNotTaken.maxBound);

            if (evaluatedPoints.size() < desiredPoints || otherDistance <= evaluatedPoints.getMaxKey()) {
                pendingPaths.offer(otherDistance, pathNotTaken);
            }
        }

        if (cursor.singlePoint) {
            double nodeDistance = distanceFunction.distance(cursor.points[0], searchPoint);

            if (evaluatedPoints.size() < desiredPoints || nodeDistance <= evaluatedPoints.getMaxKey()) {
                for (int i = 0; i < cursor.size(); i++) {
                    T value = (T) cursor.data[i];

                    if (evaluatedPoints.size() == desiredPoints) {
                        evaluatedPoints.replaceMax(nodeDistance, value);
                    } else {
                        evaluatedPoints.offer(nodeDistance, value);
                    }
                }
            }
        } else {

            for (int i = 0; i < cursor.size(); i++) {
                double[] point = cursor.points[i];
                T value = (T) cursor.data[i];
                double distance = distanceFunction.distance(point, searchPoint);

                if (evaluatedPoints.size() < desiredPoints) {
                    evaluatedPoints.offer(distance, value);
                } else if (distance < evaluatedPoints.getMaxKey()) {
                    evaluatedPoints.replaceMax(distance, value);
                }
            }
        }
    }
}

public static class titan_estruturas_dataStructures_trees_thirdGenKD_NearestNeighborIterator<T> implements java.util.Iterator<T>, Iterable<T> {
    private titan_estruturas_dataStructures_trees_thirdGenKD_DistanceFunction distanceFunction;
    private double[] searchPoint;
    private titan_estruturas_dataStructures_MinHeap<titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T>> pendingPaths;
    private titan_estruturas_dataStructures_IntervalHeap<T> evaluatedPoints;
    private int pointsRemaining;
    private double lastDistanceReturned;

    protected titan_estruturas_dataStructures_trees_thirdGenKD_NearestNeighborIterator(titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T> treeRoot, double[] searchPoint, int maxPointsReturned, titan_estruturas_dataStructures_trees_thirdGenKD_DistanceFunction distanceFunction) {
        this.searchPoint = java.util.Arrays.copyOf(searchPoint, searchPoint.length);
        this.pointsRemaining = Math.min(maxPointsReturned, treeRoot.size());
        this.distanceFunction = distanceFunction;
        this.pendingPaths = new titan_estruturas_dataStructures_BinaryHeap.Min<titan_estruturas_dataStructures_trees_thirdGenKD_KdNode<T>>();
        this.pendingPaths.offer(0, treeRoot);
        this.evaluatedPoints = new titan_estruturas_dataStructures_IntervalHeap<T>();
    }

    @Override
    public boolean hasNext() {
        return pointsRemaining > 0;
    }

    @Override
    public T next() {
        if (!hasNext()) {
            throw new IllegalStateException("NearestNeighborIterator has reached end!");
        }

        while (pendingPaths.size() > 0 && (evaluatedPoints.size() == 0 || (pendingPaths.getMinKey() < evaluatedPoints.getMinKey()))) {
            titan_estruturas_dataStructures_trees_thirdGenKD_KdTree.nearestNeighborSearchStep(pendingPaths, evaluatedPoints, pointsRemaining, distanceFunction, searchPoint);
        }

        pointsRemaining--;
        lastDistanceReturned = evaluatedPoints.getMinKey();
        T value = evaluatedPoints.getMin();
        evaluatedPoints.removeMin();
        return value;
    }

    public double distance() {
        return lastDistanceReturned;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public java.util.Iterator<T> iterator() {
        return this;
    }
}

public static class titan_estruturas_dataStructures_trees_thirdGenKD_SquareEuclideanDistanceFunction implements titan_estruturas_dataStructures_trees_thirdGenKD_DistanceFunction {
    @Override
    public double distance(double[] p1, double[] p2) {
        double d = 0;

        for (int i = 0; i < p1.length; i++) {
            double diff = (p1[i] - p2[i]);
            d += diff * diff;
        }

        return d;
    }

    @Override
    public double distanceToRect(double[] point, double[] min, double[] max) {
        double d = 0;

        for (int i = 0; i < point.length; i++) {
            double diff = 0;
            if (point[i] > max[i]) {
                diff = (point[i] - max[i]);
            }
            else if (point[i] < min[i]) {
                diff = (point[i] - min[i]);
            }
            d += diff * diff;
        }

        return d;
    }
}

public static class titan_gfx_ColoredValueSet {
  protected java.util.ArrayList<ColoredValue> _values;
  protected double _avg;
  protected double _min;
  protected double _stDev;

  public titan_gfx_ColoredValueSet() {
    _values = new java.util.ArrayList<ColoredValue>();
    _avg = 0;
    _min = Double.POSITIVE_INFINITY;
    _stDev = 0;
  }

  public void addValue(double value, double firingAngle) {
    ColoredValue cv = new ColoredValue(value, firingAngle);
    _avg = ((_avg * _values.size()) + cv.value) / (_values.size() + 1);
    _min = Math.min(_min, cv.value);
    _values.add(cv);

    double[] values = new double[_values.size()];
    for (int x = 0; x < _values.size(); x++) {
      values[x] = _values.get(x).value;
    }

    _stDev = titan_utils_Matematica.standardDeviation(values);
  }

  public int colorValue(double value, double avg,
    double stDev, double maxStDev) {

    return (int) titan_utils_Matematica.limit(0, 255 * (value - (avg - maxStDev*stDev))
        / (2*maxStDev*stDev), 255);
  }

  public java.util.ArrayList<ColoredValue> getColoredValues() {
    return _values;
  }

  public class ColoredValue {
    public double value;
    public double firingAngle;

    public ColoredValue(double value, double firingAngle) {
      this.value = value;
      this.firingAngle = firingAngle;
    }

    public java.awt.Color grayToWhiteColor() {
      int cval = Math.max(20, colorValue(value, _avg, _stDev, 3));
      return new java.awt.Color(cval, cval, cval);
    }
  }
}

public static class titan_gfx_Cores {
  private robocode.AdvancedRobot _robot;
  protected static titan_gfx_RoboPainter _radar;
  protected static titan_gfx_RoboPainter _move;
  protected static titan_gfx_RoboPainter _gun;
  private boolean _tcMode;
  private boolean _mcMode;
  private java.util.HashMap<String, titan_gfx_RoboPainter> _paintKeys;

  protected static long _lastPaintRound = 0;
  protected static long _lastPaintTime = -2;
  protected java.util.Vector<titan_gfx_RoboGraphic> _renderables;

  public titan_gfx_Cores(robocode.AdvancedRobot robot, titan_gfx_RoboPainter radar,
      titan_gfx_RoboPainter move, titan_gfx_RoboPainter gun, boolean isTc, boolean isMc) {
    _robot = robot;
    _radar = radar;
    _move = move;
    _gun = gun;
    _tcMode = isTc;
    _mcMode = isMc;
    _paintKeys = new java.util.HashMap<String, titan_gfx_RoboPainter>();
    _renderables = new java.util.Vector<titan_gfx_RoboGraphic>();
  }

  public void registerPainter(String key, titan_gfx_RoboPainter p) {
    _paintKeys.put(key.toLowerCase(), p);
    p.paintOn();
  }

  public void onKeyPressed(java.awt.event.KeyEvent e) {
    char c = e.getKeyChar();
    for (String keyCommand : _paintKeys.keySet()) {
      if (keyCommand.toLowerCase().charAt(0) == c ||
        keyCommand.toUpperCase().charAt(0) == c) {
        titan_gfx_RoboPainter p = _paintKeys.get(keyCommand);
        if (p.paintStatus()) {
          p.paintOff();
        } else {
          p.paintOn();
        }
      }
    }
  }

  public void onPaint(java.awt.Graphics2D g) {
    _lastPaintRound = _robot.getRoundNum();
    _lastPaintTime = _robot.getTime();

    _radar.onPaint(g);
    if (!_tcMode) {
      _move.onPaint(g);
    }
    if (!_mcMode) {
      _gun.onPaint(g);
    }

    if (robocodePaintingOn()) {
      drawMenu();
      for (titan_gfx_RoboGraphic r : _renderables) {
        r.render(g);
      }
      _renderables.clear();
    }
  }

  public void updatePaintProcessing() {
    if (robocodePaintingOn()) {
      _radar.robocodePaintOn();
      _move.robocodePaintOn();
      _gun.robocodePaintOn();
    } else {
      _radar.robocodePaintOff();
      _move.robocodePaintOff();
      _gun.robocodePaintOff();
    }
  }

  protected void drawMenu() {
    int numComponents = _paintKeys.size();
    double height = 20 + (numComponents * 30);
    int x = 0;

    for (String keyCommand : _paintKeys.keySet()) {
      titan_gfx_RoboPainter p = _paintKeys.get(keyCommand);
      _renderables.add(titan_gfx_RoboGraphic.drawText(
          keyCommand.toUpperCase() + ": " + p.paintLabel(), 20,
          height - (x++ * 30),
          (p.paintStatus() ? java.awt.Color.green : java.awt.Color.red)));
    }
  }

  protected boolean robocodePaintingOn() {
    return (_lastPaintRound == _robot.getRoundNum()
        && _robot.getTime() - _lastPaintTime <=1);
  }
}

public abstract static class titan_gfx_RoboGraphic{
  public abstract void render(java.awt.Graphics2D g);

  static class Circle extends titan_gfx_RoboGraphic {
    java.awt.geom.Point2D.Double center;
    double radius;
    java.awt.Color color;

    public Circle(java.awt.geom.Point2D.Double center, double radius, java.awt.Color color) {
      this.center = center;
      this.radius = radius;
      this.color = color;
    }

    public void render(java.awt.Graphics2D g) {
      g.setColor(color);
      g.drawOval((int) Math.round(center.x - radius),
                 (int) Math.round(center.y - radius),
                 (int) Math.round(2 * radius),
                 (int) Math.round(2 * radius));
    }
  }

  static class Dot extends titan_gfx_RoboGraphic {
    java.awt.geom.Point2D.Double point;
    double radius;
    java.awt.Color color;

    public Dot(java.awt.geom.Point2D.Double point, java.awt.Color color, int r) {
      this.point = point;
      this.radius = r;
      this.color = color;
    }

    public void render(java.awt.Graphics2D g) {
      g.setColor(color);
      g.fillOval((int) Math.round(point.x - radius),
                 (int) Math.round(point.y - radius),
                 (int) Math.round(2 * radius),
                 (int) Math.round(2 * radius));
    }
  }

  static class Line extends titan_gfx_RoboGraphic {
    java.awt.geom.Point2D.Double p1, p2;
    java.awt.Color color;

    double radius;
    public Line(java.awt.geom.Point2D.Double p1, java.awt.geom.Point2D.Double p2, java.awt.Color color) {
      this.p1 = p1;
      this.p2 = p2;
      this.color = color;
    }

    public void render(java.awt.Graphics2D g) {
      g.setColor(color);
      g.drawLine((int) Math.round(p1.x),
                 (int) Math.round(p1.y),
                 (int) Math.round(p2.x),
                 (int) Math.round(p2.y));
    }
  }

  static class Text extends titan_gfx_RoboGraphic {
    String text;
    double x, y;
    java.awt.Color color;
    double radius;

    public Text(String text, double x, double y, java.awt.Color color) {
      this.text = text;
      this.x = x;
      this.y = y;
      this.color = color;
    }

    public void render(java.awt.Graphics2D g) {
      g.setColor(color);
      g.drawString(text, (float)x, (float)y);
    }
  }

  public static titan_gfx_RoboGraphic drawLine(
      java.awt.geom.Point2D.Double p1, java.awt.geom.Point2D.Double p2, java.awt.Color color) {
    return new titan_gfx_RoboGraphic.Line(p1, p2, color);
  }

  public static titan_gfx_RoboGraphic drawCircle(
      java.awt.geom.Point2D.Double center, double radius, java.awt.Color color) {
    return new titan_gfx_RoboGraphic.Circle(center, radius, color);
  }

  public static titan_gfx_RoboGraphic drawPoint(java.awt.geom.Point2D.Double p1, java.awt.Color color) {
    return new titan_gfx_RoboGraphic.Dot(p1, color, 3);
  }

  public static titan_gfx_RoboGraphic drawCircleFilled(
      java.awt.geom.Point2D.Double p1, java.awt.Color color, int radius) {
    return new titan_gfx_RoboGraphic.Dot(p1, color, radius);
  }

  public static titan_gfx_RoboGraphic drawText(
      String text, double x, double y, java.awt.Color color) {
    return new titan_gfx_RoboGraphic.Text(text, x, y, color);
  }

  public static titan_gfx_RoboGraphic[] drawRectangle(
      java.awt.geom.Point2D.Double p1, double x,  double y, java.awt.Color color) {
    titan_gfx_RoboGraphic[] lines = new titan_gfx_RoboGraphic[4];
    lines[0] = titan_gfx_RoboGraphic.drawLine(
        new java.awt.geom.Point2D.Double(p1.x - (x / 2), p1.y - (y / 2)),
        new java.awt.geom.Point2D.Double(p1.x + (x / 2), p1.y - (y / 2)), color);
    lines[1] = titan_gfx_RoboGraphic.drawLine(
        new java.awt.geom.Point2D.Double(p1.x - (x / 2), p1.y - (y / 2)),
        new java.awt.geom.Point2D.Double(p1.x - (x / 2), p1.y + (y / 2)), color);
    lines[2] = titan_gfx_RoboGraphic.drawLine(
        new java.awt.geom.Point2D.Double(p1.x - (x / 2), p1.y + (y / 2)),
        new java.awt.geom.Point2D.Double(p1.x + (x / 2), p1.y + (y / 2)), color);
    lines[3] = titan_gfx_RoboGraphic.drawLine(
        new java.awt.geom.Point2D.Double(p1.x + (x / 2), p1.y + (y / 2)),
        new java.awt.geom.Point2D.Double(p1.x + (x / 2), p1.y - (y / 2)), color);
    return lines;
  }

  public static titan_gfx_RoboGraphic[] drawArrowHead(
      java.awt.geom.Point2D.Double p1, double length, double angle, java.awt.Color color) {
    titan_gfx_RoboGraphic[] lines = new titan_gfx_RoboGraphic[2];
    lines[0] = titan_gfx_RoboGraphic.drawLine(
        p1, titan_utils_Matematica.project(p1, angle - 2.7, length), color);
    lines[1] = titan_gfx_RoboGraphic.drawLine(
        p1, titan_utils_Matematica.project(p1, angle + 2.7, length), color);
    return lines;
  }

  public static java.awt.Color riskColor(double risk, double avg,
    double stDev, boolean safestYellow, double maxStDev) {
    if (risk < .0000001 && safestYellow) {
      return java.awt.Color.yellow;
    }

    return new java.awt.Color(
        (int) titan_utils_Matematica.limit(0, 255 * (risk - (avg - maxStDev*stDev))
            / (2*maxStDev*stDev), 255),
        0,
        (int) titan_utils_Matematica.limit(0, 255 * ((avg + maxStDev*stDev) - risk)
            / (2*maxStDev*stDev), 255));
  }
}

public interface titan_gfx_RoboPainter {
  void paintOn();
  void paintOff();
  void robocodePaintOn();
  void robocodePaintOff();
  boolean paintStatus();
  String paintLabel();
  void onPaint(java.awt.Graphics2D g);
}

public static class titan_gun_AntiSurferGun extends titan_gun_DuelGun<titan_utils_TimestampedFiringAngle> {
  private static final int FIRING_ANGLES = 59;

  private final titan_gun_GunDataManager _gunDataManager;
  private titan_utils_BattleField _battleField;
  private java.util.Collection<titan_gfx_RoboGraphic> _renderables;
  private java.util.List<String> _viewNames;
  private titan_utils_DistanceFormula _formula;
  private boolean _is1v1Battle;

  public titan_gun_AntiSurferGun(titan_gun_GunDataManager gunDataManager, titan_utils_BattleField battleField,
      java.util.Collection<titan_gfx_RoboGraphic> renderables) {
    super();
    _gunDataManager = gunDataManager;
    _battleField = battleField;
    _renderables = renderables;
    _viewNames = new java.util.ArrayList<String>();
    _formula = new titan_gun_formulas_AntiSurferFormula();
    _is1v1Battle = (_gunDataManager.getEnemiesTotal() == 1);
  }

  @Override
  public String getLabel() {
    return "Anti-Surfer Gun";
  }

  @Override
  protected double aimInternal(titan_utils_Wave w, boolean painting) {
    titan_gun_GunEnemy gunData = _gunDataManager.getEnemyData(w.botName);
    java.util.List<titan_KdTree.Entry<titan_utils_TimestampedFiringAngle>> nearestNeighbors = null;
    double[] neighborWeights = null;
    for (String viewName : _viewNames) {
      titan_utils_KnnView<titan_utils_TimestampedFiringAngle> view = gunData.views.get(viewName);
      if (view.size() < view.kDivisor) {
        continue;
      }

      java.util.List<titan_KdTree.Entry<titan_utils_TimestampedFiringAngle>> thisNeighbors =
          view.nearestNeighbors(w, true);
      double[] thisWeights = new double[thisNeighbors.size()];
      java.util.Arrays.fill(thisWeights, view.weight);

      if (nearestNeighbors == null) {
        nearestNeighbors = thisNeighbors;
        neighborWeights = thisWeights;
      } else {
        nearestNeighbors.addAll(thisNeighbors);
        int newIndex = neighborWeights.length;
        neighborWeights =
            java.util.Arrays.copyOf(neighborWeights, nearestNeighbors.size());
        for (int x = newIndex; x < neighborWeights.length; x++) {
          neighborWeights[x] = thisWeights[x - newIndex];
        }
      }
    }

    if (nearestNeighbors == null || nearestNeighbors.size() == 0) {
      return w.absBearing;
    }

    int numScans = nearestNeighbors.size();
    Double[] firingAngles = new Double[numScans];
    for (int x = 0; x < numScans; x++) {
      if (_is1v1Battle) {
        double guessFactor = nearestNeighbors.get(x).value.guessFactor;
        firingAngles[x] = robocode.util.Utils.normalRelativeAngle(
            (guessFactor * w.orbitDirection
                * w.preciseEscapeAngle(guessFactor >= 0)));
      } else {
        java.awt.geom.Point2D.Double dispVector =
            nearestNeighbors.get(x).value.displacementVector;
        java.awt.geom.Point2D.Double projectedLocation =
            w.projectLocationFromDisplacementVector(dispVector);
        if (!_battleField.rectangle.contains(projectedLocation)) {
          firingAngles[x] = null;
        } else {
          firingAngles[x] = robocode.util.Utils.normalRelativeAngle(
              w.firingAngleFromTargetLocation(projectedLocation)
              - w.absBearing);
        }
      }
    }

    Double bestAngle = null;
    double bestDensity = Double.NEGATIVE_INFINITY;
    double bandwidth = titan_utils_Matematica.botWidthAimAngle(
        w.sourceLocation.distance(w.targetLocation)) * 2;
    titan_gfx_ColoredValueSet cvs = new titan_gfx_ColoredValueSet();
    double[] realAngles =
        titan_utils_Matematica.generateFiringAngles(FIRING_ANGLES, w.maxEscapeAngle());
    for (int x = 0; x < FIRING_ANGLES; x++) {
      double xFiringAngle = realAngles[x];

      double xDensity = 0;
      for (int y = 0; y < numScans; y++) {
        if (firingAngles[y] == null) {
          continue;
        }

        double yFiringAngle = firingAngles[y];
        double ux = (xFiringAngle - yFiringAngle) / bandwidth;
        xDensity += Math.exp(-0.5 * ux * ux) * neighborWeights[y];
      }

      if (xDensity > bestDensity) {
        bestAngle = xFiringAngle;
        bestDensity = xDensity;
      }

      if (painting) {
        cvs.addValue(xDensity, w.absBearing + xFiringAngle);
      }
    }

    if (bestAngle == null) {
      return w.absBearing;
    }

    if (painting) {
      titan_gun_Mira.drawGunAngles(
          _renderables, w, cvs, w.absBearing + bestAngle, bandwidth);
    }

    return robocode.util.Utils.normalAbsoluteAngle(w.absBearing + bestAngle);
  }

  @Override
  public java.util.List<titan_utils_KnnView<titan_utils_TimestampedFiringAngle>> newDataViews() {
    titan_utils_KnnView<titan_utils_TimestampedFiringAngle> asView =
        new titan_utils_KnnView<titan_utils_TimestampedFiringAngle>(_formula)
            .setK(3)
            .setMaxDataPoints(125)
            .setKDivisor(10)
            .visitsOn()
            .virtualWavesOn()
            .setName("asView1");
    titan_utils_KnnView<titan_utils_TimestampedFiringAngle> asView2 =
        new titan_utils_KnnView<titan_utils_TimestampedFiringAngle>(_formula)
            .setK(3)
            .setMaxDataPoints(400)
            .setKDivisor(10)
            .visitsOn()
            .virtualWavesOn()
            .setName("asView2");
    titan_utils_KnnView<titan_utils_TimestampedFiringAngle> asView3 =
        new titan_utils_KnnView<titan_utils_TimestampedFiringAngle>(_formula)
            .setK(3)
            .setMaxDataPoints(1500)
            .setKDivisor(10)
            .visitsOn()
            .virtualWavesOn()
            .setName("asView3");
    titan_utils_KnnView<titan_utils_TimestampedFiringAngle> asView4 =
        new titan_utils_KnnView<titan_utils_TimestampedFiringAngle>(_formula)
            .setK(3)
            .setMaxDataPoints(4000)
            .setKDivisor(10)
            .visitsOn()
            .virtualWavesOn()
            .setName("asView4");
    java.util.List<titan_utils_KnnView<titan_utils_TimestampedFiringAngle>> views =
        new java.util.ArrayList<titan_utils_KnnView<titan_utils_TimestampedFiringAngle>>();
    views.add(asView);
    views.add(asView2);
    views.add(asView3);
    views.add(asView4);

    for (titan_utils_KnnView<titan_utils_TimestampedFiringAngle> view : views) {
      if (!_viewNames.contains(view.name)) {
        _viewNames.add(view.name);
      }
    }

    return views;
  }
}

public static class titan_gun_Mira implements titan_gfx_RoboPainter {
  private static final boolean ENABLE_DEBUGGING_GRAPHICS = true;
  private static final int KNN_DATA_THRESHOLD = 9;

  private titanprime_v4_41 _robot;
  private boolean _tcMode;
  private titan_gun_GunDataManager _gunDataManager;
  private titan_gun_VirtualGunsManager<titan_utils_TimestampedFiringAngle> _virtualGuns;
  private titan_gun_DuelGun<titan_utils_TimestampedFiringAngle> _perceptualGun;
  private titan_gun_DuelGun<titan_utils_TimestampedFiringAngle> _currentGun;
  private titan_gun_MeleeGun _meleeGun;
  private int _enemiesTotal;
  private double _aimedBulletPower;
  private java.util.List<titan_gun_FireListener> _fireListeners;
  private titan_utils_BattleField _battleField;
  private titan_utils_MovementPredictor _predictor;

  private boolean _startedDuel;
  private java.util.Collection<titan_gfx_RoboGraphic> _renderables;
  private boolean _painting;
  private boolean _robocodePainting;
  private java.io.PrintStream _out;
  private boolean _drawVictory;

  public titan_gun_Mira(titanprime_v4_41 robot, boolean isTcMode) {
    this(robot, isTcMode,
        new titan_utils_BattleField(robot.getBattleFieldWidth(),
            robot.getBattleFieldHeight()),
        new java.util.ArrayList<titan_gfx_RoboGraphic>(), System.out);
  }

  titan_gun_Mira(titanprime_v4_41 robot, boolean isTcMode, titan_utils_BattleField battleField,
      java.util.Collection<titan_gfx_RoboGraphic> renderables, java.io.OutputStream out) {
    this(robot, isTcMode, battleField, new titan_utils_MovementPredictor(battleField),
        renderables, out);
  }

  titan_gun_Mira(titanprime_v4_41 robot, boolean isTcMode, titan_utils_BattleField battleField,
      titan_utils_MovementPredictor predictor, java.util.Collection<titan_gfx_RoboGraphic> renderables,
      java.io.OutputStream out) {
    this(robot, isTcMode,
        new titan_gun_GunDataManager(robot.getOthers(), battleField, predictor,
            renderables, out),
        new titan_gun_VirtualGunsManager<titan_utils_TimestampedFiringAngle>(out), battleField,
        renderables, out);
  }

  titan_gun_Mira(titanprime_v4_41 robot, boolean isTcMode, titan_gun_GunDataManager gunDataManager,
      titan_gun_VirtualGunsManager<titan_utils_TimestampedFiringAngle> virtualGunsManager,
      titan_utils_BattleField battleField, java.util.Collection<titan_gfx_RoboGraphic> renderables,
      java.io.OutputStream out) {
    if (robot == null || gunDataManager == null || virtualGunsManager == null) {
      throw new NullPointerException();
    }

    _robot = robot;
    _tcMode = isTcMode;
    _gunDataManager = gunDataManager;
    _virtualGuns = virtualGunsManager;
    _battleField = battleField;
    _renderables = renderables;
    _out = new java.io.PrintStream(out);
    _predictor = new titan_utils_MovementPredictor(battleField);
    _enemiesTotal = _robot.getOthers();
    _gunDataManager.addListener(_virtualGuns);

    _fireListeners = new java.util.ArrayList<titan_gun_FireListener>();
    _painting = false;
    _robocodePainting = false;

    initGuns();
  }

  void initGuns() {
    titan_gun_DuelGun<titan_utils_TimestampedFiringAngle> mainGun;
    if (_enemiesTotal > 1) {
      _perceptualGun = null;
      mainGun = new titan_gun_MainGun(_gunDataManager, _battleField, _renderables);
      _currentGun = mainGun;
    } else {
      _perceptualGun = new titan_gun_PerceptualGun<titan_utils_TimestampedFiringAngle>();
      _currentGun = _perceptualGun;
      mainGun = new titan_gun_AgrupamentoKnnGun(_gunDataManager, _renderables);
    }
    _virtualGuns.addGun(mainGun);

    titan_gun_DuelGun<titan_utils_TimestampedFiringAngle> antiSurfGun = new titan_gun_AntiSurferGun(
        _gunDataManager, _battleField, _renderables);
    _virtualGuns.addGun(antiSurfGun);
    _virtualGuns.addGun(new TitanNeuralGun(false));
    _virtualGuns.addGun(new TitanNeuralGun(true));

    _meleeGun = new titan_gun_MeleeGun(_gunDataManager, _battleField, _renderables);
  }

  void initGunViews(titan_gun_GunEnemy gunData) {
    for (titan_gun_DuelGun<titan_utils_TimestampedFiringAngle> gun : _virtualGuns.getGuns()) {
      gunData.addViews(gun.newDataViews());
    }
    if (isMelee()) {
      gunData.addViews(_meleeGun.newDataViews());
    }
  }

  public void initRound(titanprime_v4_41 robot) {
    _robot = robot;
    _gunDataManager.initRound();
    _virtualGuns.initRound();

    _startedDuel = false;
    _drawVictory = false;
    _renderables.clear();
  }

  public void execute() {
    _gunDataManager.execute(_robot.getRoundNum(), _robot.getTime(),
        calculateBulletPower(), _robot.getGunHeat(), myLocation(), is1v1(),
        paintStatus());
    if (is1v1()) {
      titan_gun_GunEnemy duelEnemy = _gunDataManager.duelEnemy();
      if (duelEnemy != null) {
        aimAndFire(duelEnemy);
        if (!_startedDuel) {
          _startedDuel = true;
          printCurrentGun(duelEnemy);
        }
      }
    } else {
      aimAndFireAtEveryone();
    }
    if (paintStatus() && _drawVictory) {
      _gunDataManager.drawVictory(_robot.getTime());
      _drawVictory = false;
    }
  }

  private void aimAndFire(titan_gun_GunEnemy gunData) {
    if (gunData != null) {
      if (_robot.shield.active) {
        _robot.shield.aimAndFire(this);
        return;
      }
      if (!_robot.shield.holdNormalFire()) fireIfGunTurned(_aimedBulletPower);

      titan_utils_Wave aimWave = gunData.lastWaveFired;
      _aimedBulletPower = aimWave.bulletPower();
      java.awt.geom.Point2D.Double myNextLocation = _predictor.nextLocation(_robot);
      double firingAngle;
      if (gunData.energy == 0 || ticksUntilGunCool() > 3) {
        firingAngle =
            titan_utils_Matematica.absoluteBearing(myNextLocation, aimWave.targetLocation);
        evaluateVirtualGuns(gunData);
      } else {
        firingAngle = _currentGun.aim(aimWave, paintStatus());
      }
      _robot.setTurnGunRightRadians(robocode.util.Utils.normalRelativeAngle(
          firingAngle - _robot.getGunHeadingRadians()));
    }
  }

  private void aimAndFireAtEveryone() {
    titan_gun_GunEnemy closestBot = _gunDataManager.getClosestLivingBot(myLocation());
    if (closestBot != null) {
      fireIfGunTurned(_aimedBulletPower);
      java.awt.geom.Point2D.Double myNextLocation = _predictor.nextLocation(_robot);
      long ticksUntilFire = ticksUntilGunCool();

      if (ticksUntilFire % 2 == 0 || ticksUntilFire <= 4) {
        _aimedBulletPower = calculateBulletPower();
        double firingAngle = _meleeGun.aimAtEveryone(myNextLocation,
            _robot.getTime(), _robot.getOthers(), _aimedBulletPower,
            closestBot, paintStatus());
        _robot.setTurnGunRightRadians(robocode.util.Utils.normalRelativeAngle(
            firingAngle - _robot.getGunHeadingRadians()));
      }
    }
  }

  protected java.awt.geom.Point2D.Double myLocation() {
    return new java.awt.geom.Point2D.Double(_robot.getX(), _robot.getY());
  }

  double calculateBulletPower() {
    if (_robot.shield.active) return _robot.shield.plannedPower();
    titan_gun_GunEnemy gunData = _gunDataManager.getClosestLivingBot(myLocation());
    double bulletPower = 3;
    if (gunData != null) {
      double myEnergy = _robot.getEnergy();
      if (_tcMode) {
        bulletPower = Math.min(myEnergy, 3);
      } else if (is1v1()) {
        bulletPower = 1.95;

        if (gunData.distance < 150 || gunData.isRammer()) {
          bulletPower = 2.95;
        }

        if (gunData.distance > 325) {
          double powerDownPoint =
              titan_utils_Matematica.limit(35, 63 + ((gunData.energy - myEnergy) * 4), 63);
          if (myEnergy < powerDownPoint) {
            bulletPower = Math.min(bulletPower,
                titan_utils_Matematica.cube(myEnergy / powerDownPoint) * 1.95);
          }
        }

        bulletPower = Math.min(bulletPower, gunData.energy / 4);
        bulletPower = Math.max(bulletPower, 0.1);
        bulletPower = Math.min(bulletPower, myEnergy);
      } else {
        double avgEnemyEnergy = _gunDataManager.getAverageEnergy();

        bulletPower = 2.999;

        int enemiesAlive = _robot.getOthers();
        if (enemiesAlive <= 3) {
          bulletPower = 1.999;
        }

        if (enemiesAlive <= 5 && gunData.distance > 500) {
          bulletPower = 1.499;
        }

        if ((myEnergy < avgEnemyEnergy && enemiesAlive <= 5
                && gunData.distance > 300)
            || gunData.distance > 700) {
          bulletPower = 0.999;
        }

        if (myEnergy < 20 && myEnergy < avgEnemyEnergy) {
          bulletPower =
              Math.min(bulletPower, 2 - ((20 - myEnergy) / 11));
        }

        bulletPower = Math.max(bulletPower, 0.1);
        bulletPower = Math.min(bulletPower, myEnergy);
      }
    }

    if (is1v1() && !_tcMode) {
      titan_move_MoveEnemy firingEnemy = _robot._move == null ? null : _robot._move._moveDataManager.duelEnemy();
      double cap = gunData == null ? .1 : pressureFireCap(gunData.distance,
          _robot.getEnergy(), gunData.energy, firingEnemy == null ? 0 : firingEnemy.lastBulletPower,
          _robot.policyShots, _robot.policyHits, 450);
      bulletPower = comebackPower(bulletPower, cap, _robot.getEnergy(),
          gunData == null ? 0 : gunData.energy);
      bulletPower = Math.min(bulletPower, pressureAccuracyCap(cap,
          _robot.policyStrongResolved, _robot.policyStrongHits));
    }
    return bulletPower;
  }

  private void evaluateVirtualGuns(titan_gun_GunEnemy gunData) {
    int dataPoints = _gunDataManager.getDuelDataSize();
    if (dataPoints < KNN_DATA_THRESHOLD && _perceptualGun != null) {
      _currentGun = _perceptualGun;
    } else {
      if (_perceptualGun != null) {
        _out.println(
            "Disabling " + _perceptualGun.getLabel() + " @ " + _robot.getTime());
        _perceptualGun = null;
      }

      titan_gun_DuelGun<titan_utils_TimestampedFiringAngle> bestGun =
          _virtualGuns.bestGun(gunData.botName);
      if (_currentGun != bestGun) {
        _currentGun = bestGun;
        _out.println("Switching to " + _currentGun.getLabel() + " ("
            + _virtualGuns.getFormattedRating(_currentGun, gunData.botName)
            + ")");
      }
    }
  }

  void fireIfGunTurned(double bulletPower) {
    if (_robot.getGunHeat() == 0 && _robot.getGunTurnRemaining() == 0) {
      robocode.Bullet realBullet = null;
      if (_tcMode) {
        realBullet = _robot.setFireBullet(3);
      } else if (_robot.getEnergy() > bulletPower) {
        realBullet = setFireBulletLogged(bulletPower);
      }

      if (realBullet != null) {
        _gunDataManager.markFiringWaves(_robot.getTime(), is1v1());
      }
    }
  }

  public void onScannedRobot(robocode.ScannedRobotEvent e) {
    String botName = e.getName();
    double absBearing = e.getBearingRadians() + _robot.getHeadingRadians();
    java.awt.geom.Point2D.Double enemyLocation = titan_utils_Matematica.project(
        myLocation(), robocode.util.Utils.normalAbsoluteAngle(absBearing), e.getDistance());

    titan_gun_GunEnemy gunData;
    if (_gunDataManager.hasEnemy(botName)) {
      gunData = _gunDataManager.updateEnemy(
          e, enemyLocation, absBearing, _robot.getRoundNum(), is1v1());
    } else {
      gunData = _gunDataManager.newEnemy(
          e, enemyLocation, absBearing, _robot.getRoundNum(), is1v1());
      initGunViews(gunData);
    }

    _gunDataManager.fireNextTickWave(_predictor.nextLocation(_robot),
        enemyLocation, botName, _robot.getRoundNum(), _robot.getTime(),
        calculateBulletPower(), _robot.getEnergy(), _robot.getGunHeat(),
        _robot.getHeadingRadians(), _robot.getVelocity(), _robot.getOthers());
  }

  public void onRobotDeath(robocode.RobotDeathEvent e) {
    _gunDataManager.onRobotDeath(e);
  }

  public void onWin(robocode.WinEvent e) {
    _drawVictory = true;
    roundOver();
  }

  public void onDeath(robocode.DeathEvent e) {
    roundOver();
  }

  protected void roundOver() {
    if (_tcMode) {
      _out.println("TC score: "
          + (_gunDataManager.getDamageGiven() / (_robot.getRoundNum() + 1)));
    }
    titan_gun_GunEnemy duelEnemy = _gunDataManager.duelEnemy();
    if (is1v1() && duelEnemy != null) {
      _virtualGuns.printGunRatings(duelEnemy.botName);
    }
  }

  public void onBulletHit(robocode.BulletHitEvent e) {
    _gunDataManager.onBulletHit(e, _robot.getTime());
  }

  public void onBulletHitBullet(robocode.BulletHitBulletEvent e) {
    _gunDataManager.onBulletHitBullet(e, _robot.getTime());
  }

  public void onPaint(java.awt.Graphics2D g) {

    if (paintStatus()) {
      for (titan_gfx_RoboGraphic r : _renderables) {
        r.render(g);
      }
      _renderables.clear();
    }
  }

  private long ticksUntilGunCool() {
    return Math.round(Math.ceil(
        _robot.getGunHeat() / _robot.getGunCoolingRate()));
  }

  private boolean is1v1() {
    return (_robot.getOthers() <= 1);
  }

  private boolean isMelee() {
    return (_robot.getOthers() > 1);
  }

  public int getEnemiesAlive() {
    return _robot.getOthers();
  }

  public void addFireListener(titan_gun_FireListener listener) {
    _fireListeners.add(listener);
  }

  private robocode.Bullet setFireBulletLogged(double bulletPower) {
    robocode.Bullet bullet = _robot.setFireBullet(bulletPower);
    if (bullet != null) {
      for (titan_gun_FireListener listener : _fireListeners) {
        titan_gun_FireListener.FiredBullet fired = new titan_gun_FireListener.FiredBullet(_robot.getTime(), myLocation(),
            _robot.getGunHeadingRadians(), bullet.getVelocity());
        fired.realBullet = bullet;
        listener.bulletFired(fired);
      }
    }

    return bullet;
  }

  public void paintOn() {
    _painting = ENABLE_DEBUGGING_GRAPHICS;
  }

  public void paintOff() {
    _renderables.clear();
    _painting = false;
  }

  public void robocodePaintOn() {
    _robocodePainting = true;
  }

  public void robocodePaintOff() {
    _renderables.clear();
    _robocodePainting = false;
  }

  public String paintLabel() {
    return "Gun";
  }

  public boolean paintStatus() {
    return (_painting && _robocodePainting);
  }

  protected static void drawGunAngles(java.util.Collection<titan_gfx_RoboGraphic> renderables,
    titan_utils_Wave wave, titan_gfx_ColoredValueSet cvs, double bestAngle, double bandwidth) {

    double arrowLength = titan_utils_Matematica.limit(
        50, wave.sourceLocation.distance(wave.targetLocation) - 75, 200);

    for (titan_gfx_ColoredValueSet.ColoredValue cv : cvs.getColoredValues()) {
      java.awt.Color c = cv.grayToWhiteColor();
      java.awt.geom.Point2D.Double angleHead = titan_utils_Matematica.project(
          wave.sourceLocation, cv.firingAngle, arrowLength);

      renderables.add(titan_gfx_RoboGraphic.drawLine(wave.sourceLocation, angleHead, c));
      renderables.addAll(java.util.Arrays.asList(
          titan_gfx_RoboGraphic.drawArrowHead(angleHead, 10, cv.firingAngle, c)));
    }

    renderables.add(titan_gfx_RoboGraphic.drawPoint(titan_utils_Matematica.project(
        wave.sourceLocation, bestAngle, arrowLength + 5), java.awt.Color.red));
  }

  private void printCurrentGun(titan_gun_GunEnemy gunData) {
    _out.println("Current gun: " + _currentGun.getLabel() + " ("
        + _virtualGuns.getFormattedRating(_currentGun, gunData.botName)+ ")");
  }

  titanprime_v4_41 getRobot() {
    return _robot;
  }

  titan_gun_VirtualGunsManager<titan_utils_TimestampedFiringAngle> getVirtualGunsManager() {
    return _virtualGuns;
  }

  java.util.Collection<titan_gfx_RoboGraphic> getRenderables() {
    return _renderables;
  }
}

public abstract static class titan_gun_DuelGun<T> {
  private java.util.Map<titan_utils_Wave, Double> _firingAngles;

  public titan_gun_DuelGun() {
    _firingAngles = new java.util.HashMap<titan_utils_Wave, Double>();
  }

  public void clearCache() {
    _firingAngles.clear();
  }

  public double aim(titan_utils_Wave w, boolean painting) {
    if (!_firingAngles.containsKey(w)) {
      _firingAngles.put(w, aimInternal(w, painting));
    }
    return _firingAngles.get(w);
  }

  abstract public String getLabel();

  abstract protected double aimInternal(titan_utils_Wave w, boolean painting);

  abstract public java.util.List<titan_utils_KnnView<T>> newDataViews();
}

public interface titan_gun_FireListener {
  public void bulletFired(FiredBullet bullet);

  public static class FiredBullet {
    public final long fireTime;
    public final java.awt.geom.Point2D.Double sourceLocation;
    public final double firingAngle;
    public final double bulletSpeed;
    public final double dx;
    public final double dy;
    public long deathTime;
    public robocode.Bullet realBullet;

    public FiredBullet(long fireTime, java.awt.geom.Point2D.Double sourceLocation,
        double firingAngle, double bulletSpeed) {
      this.fireTime = fireTime;
      this.sourceLocation = sourceLocation;
      this.firingAngle = firingAngle;
      this.bulletSpeed = bulletSpeed;
      dx = Math.sin(firingAngle) * bulletSpeed;
      dy = Math.cos(firingAngle) * bulletSpeed;
      deathTime = Long.MAX_VALUE;
    }

    public double distanceTraveled(long currentTime) {
      return (currentTime - fireTime) * bulletSpeed;
    }

    public java.awt.geom.Point2D.Double position(long currentTime) {
      return new java.awt.geom.Point2D.Double(
          sourceLocation.x + (dx * (currentTime - fireTime)),
          sourceLocation.y + (dy * (currentTime - fireTime)));
    }
  }
}

public static class titan_gun_GunDataManager extends titan_utils_EnemyDataManager<titan_gun_GunEnemy> {
  static final String WARNING_BULLET_HIT_UNKNOWN =
      "I shot a bot that I never knew existed!";
  static final String WARNING_BULLET_HIT_BULLET_UNKNOWN =
      "I shot a bullet by a bot that I never knew existed!";

  private java.util.List<GunDataListener> _listeners;
  private long _lastBulletFiredTime;
  private java.util.Map<Long, java.util.List<titan_gfx_RoboGraphic>> _victoryGraphics;

  public titan_gun_GunDataManager(int enemiesTotal, titan_utils_BattleField battleField,
      titan_utils_MovementPredictor predictor, java.util.Collection<titan_gfx_RoboGraphic> renderables,
      java.io.OutputStream out) {
    super(enemiesTotal, battleField, predictor, renderables, out);
    _listeners = new java.util.ArrayList<GunDataListener>();
    _victoryGraphics = new java.util.HashMap<Long, java.util.List<titan_gfx_RoboGraphic>>();
  }

  @Override
  public void initRound() {
    super.initRound();
    _lastBulletFiredTime = 0;
    _victoryGraphics.clear();
  }

  public void execute(int currentRound, long currentTime, double bulletPower,
      double currentGunHeat, java.awt.geom.Point2D.Double myLocation, boolean is1v1,
      boolean painting) {
    updateBotDistances(myLocation);
    for (titan_gun_GunEnemy gunData : getAllEnemyData()) {
      if (gunData.alive) {
        gunData.execute(currentTime, _lastBulletFiredTime, bulletPower,
            currentGunHeat, myLocation, is1v1, _enemiesTotal, _listeners,
            painting);
      }
    }
    if (painting && _victoryGraphics.containsKey(currentTime)) {
      for (titan_gfx_RoboGraphic graphic : _victoryGraphics.get(currentTime)) {
        _renderables.add(graphic);
      }
    }
  }

  public titan_gun_GunEnemy newEnemy(robocode.ScannedRobotEvent e, java.awt.geom.Point2D.Double enemyLocation,
      double absBearing, int currentRound, boolean is1v1) {
    if (hasEnemy(e.getName())) {
      throw new IllegalArgumentException(
          "GunEnemy already exists for bot: " + e.getName());
    }

    titan_gun_GunEnemy gunData = new titan_gun_GunEnemy(e.getName(), e.getDistance(),
        e.getEnergy(), enemyLocation, currentRound, e.getTime(),
        e.getHeadingRadians(), e.getVelocity(), absBearing,
        _battleField, _predictor, _renderables);
    gunData.updateTimers(e.getVelocity());
    saveEnemy(e.getName(), gunData);
    if (is1v1) {
      _duelEnemy = gunData;
    }
    return gunData;
  }

  void saveEnemy(String botName, titan_gun_GunEnemy gunData) {
    _enemies.put(botName, gunData);
  }

  public titan_gun_GunEnemy updateEnemy(robocode.ScannedRobotEvent e, java.awt.geom.Point2D.Double enemyLocation,
      double absBearing, int currentRound, boolean is1v1) {
    titan_gun_GunEnemy gunData = getEnemyData(e.getName());
    long timeSinceLastScan = e.getTime() - gunData.lastScanState.time;
    gunData.timeSinceDirectionChange += timeSinceLastScan;
    gunData.timeSinceVelocityChange += timeSinceLastScan;

    gunData.updateTimers(e.getVelocity());
    gunData.distance = e.getDistance();
    gunData.absBearing = absBearing;
    gunData.energy = e.getEnergy();
    gunData.lastScanRound = currentRound;
    gunData.previousVelocity = gunData.lastScanState.velocity;
    gunData.setRobotState(titan_utils_RobotState.newBuilder()
        .setLocation(enemyLocation)
        .setHeading(e.getHeadingRadians())
        .setVelocity(e.getVelocity())
        .setTime(e.getTime())
        .build());
    gunData.timeAliveTogether++;
    if (gunData.advancingVelocity() > 6) {
      gunData.timeMovingAtMe++;
    }
    if (is1v1) {
      _duelEnemy = gunData;
    }
    return gunData;
  }

  public void onBulletHit(robocode.BulletHitEvent e, long currentTime) {
    String botName = e.getName();
    if (hasEnemy(botName)) {
      titan_gun_GunEnemy gunData = getEnemyData(e.getName());
      double bulletDamage = Math.min(
          robocode.Rules.getBulletDamage(e.getBullet().getPower()), gunData.energy);
      gunData.damageGiven += bulletDamage;
      gunData.logBulletHitLocation(e.getBullet());
    } else {
      _out.println(warning() + WARNING_BULLET_HIT_UNKNOWN
          + " (" + botName + ")");
    }
    for (titan_gun_GunEnemy gunData : getAllEnemyData()) {
      titan_utils_Wave hitWave = gunData.processBulletHit(
          e.getBullet(), currentTime, (_enemiesTotal == 1), true);
      if (hitWave != null) {
        hitWave.hitByBullet = true;
      }
    }
  }

  public void onBulletHitBullet(robocode.BulletHitBulletEvent e, long currentTime) {
    String botName = e.getHitBullet().getName();
    for (titan_gun_GunEnemy gunData : getAllEnemyData()) {
      titan_utils_Wave hitWave = gunData.processBulletHit(e.getBullet(), currentTime,
          (_enemiesTotal == 1), botName.equals(gunData.botName));
      if (hitWave != null) {
        hitWave.bulletHitBullet = true;
      }
    }
    if (!hasEnemy(botName) && !botName.equals(e.getBullet().getName())) {
      _out.println(warning() + WARNING_BULLET_HIT_BULLET_UNKNOWN
          + " (" + botName + ")");
    }
  }

  public double getDamageGiven() {
    double damageGiven = 0;
    for (titan_gun_GunEnemy gunData : getAllEnemyData()) {
      damageGiven += gunData.damageGiven;
    }
    return damageGiven;
  }

  public double getAverageEnergy() {
    double totalEnergy = 0;
    int enemiesAlive = 0;
    for (titan_gun_GunEnemy gunData : getAllEnemyData()) {
      if (gunData.alive) {
        totalEnergy += gunData.energy;
        enemiesAlive++;
      }
    }
    return (enemiesAlive == 0) ? 0 : totalEnergy / enemiesAlive;
  }

  public void markFiringWaves(long currentTime, boolean is1v1) {
    _lastBulletFiredTime = currentTime;
    for (titan_gun_GunEnemy gunData : getAllEnemyData()) {
      gunData.markFiringWaves(currentTime, is1v1, _listeners);
    }
  }

  public void fireNextTickWave(java.awt.geom.Point2D.Double myNextLocation,
      java.awt.geom.Point2D.Double targetLocation, String targetName, int currentRound,
      long currentTime, double bulletPower, double myEnergy, double gunHeat,
      double myHeading, double myVelocity, int enemiesAlive) {
    titan_gun_GunEnemy gunData = getEnemyData(targetName);
    titan_utils_RobotState lastScanState = gunData.lastScanState;
    java.awt.geom.Point2D.Double enemyNextLocation = _battleField.translateToField(
        _predictor.nextLocation(
            targetLocation, lastScanState.heading, lastScanState.velocity));
    double accel = titan_utils_Matematica.limit(-robocode.Rules.DECELERATION,
                                  titan_utils_Matematica.accel(lastScanState.velocity,
                                                 gunData.previousVelocity),
                                  robocode.Rules.ACCELERATION);
    long fireTime = currentTime + 1;
    long lastWaveFireTime = gunData.getLastWaveFireTime(gunData.botName);
    titan_utils_RobotStateLog stateLog = gunData.stateLog;
    double dl8t =
        stateLog.getDisplacementDistance(targetLocation, currentTime, 8);
    double dl20t =
        stateLog.getDisplacementDistance(targetLocation, currentTime, 20);
    double dl40t =
        stateLog.getDisplacementDistance(targetLocation, currentTime, 40);

    titan_utils_Wave nextWave = gunData.newGunWave(myNextLocation, enemyNextLocation,
        currentRound, fireTime, _lastBulletFiredTime, bulletPower, myEnergy,
        gunHeat, enemiesAlive, accel, dl8t, dl20t, dl40t, false);
    gunData.lastWaveFired = nextWave;

    for (titan_gun_GunEnemy altGunData : getAllEnemyData()) {
      if (altGunData.alive && !altGunData.botName.equals(targetName)) {
        java.awt.geom.Point2D.Double altNextLocation = _battleField.translateToField(
            _predictor.nextLocation(altGunData.lastScanState.location,
                altGunData.lastScanState.heading,
                altGunData.lastScanState.velocity));
        gunData.newGunWave(altNextLocation, enemyNextLocation, currentRound,
            fireTime, _lastBulletFiredTime, bulletPower, altGunData.energy,
            gunHeat, enemiesAlive, accel, dl8t, dl20t, dl40t, true);
      }
    }

    if (_duelEnemy != null && lastWaveFireTime > 0) {
      for (long time = lastWaveFireTime + 1; time < fireTime; time++) {
        gunData.interpolateGunWave(
            time, currentTime, myHeading, myVelocity, lastScanState);
      }
    }
  }

  public void addListener(GunDataListener listener) {
    _listeners.add(listener);
  }

  public int getEnemiesTotal() {
    return _enemiesTotal;
  }

  public int getDuelDataSize() {
    return _duelEnemy.getWaveBreaks();
  }

  @Override
  protected String getLabel() {
    return "gun";
  }

  public void drawVictory(long startTime) {
    for (titan_gun_GunEnemy gunData : getAllEnemyData()) {
      long hitStartTime = startTime;
      java.awt.geom.Point2D.Double previousHit = null;
      for (java.awt.geom.Point2D.Double hitLocation : gunData.hitLocations) {
        boolean deathBlow = (hitLocation
            == gunData.hitLocations.get(gunData.hitLocations.size() - 1));
        for (int x = 0; x < 7; x++) {
          addVictoryGraphic(hitStartTime + x,
              titan_gfx_RoboGraphic.drawCircleFilled(hitLocation, java.awt.Color.WHITE, 5));
        }
        for (int x = 0; x < (deathBlow ? 5 : 3); x++) {
          for (int y = 0; y < (x + 1) * 3; y++) {
            addVictoryGraphic(hitStartTime + y + x,
                titan_gfx_RoboGraphic.drawCircle(hitLocation, (y + 2) * 5, java.awt.Color.RED));
          }
        }
        if (previousHit != null) {
          for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 8; y++) {
              addVictoryGraphic(hitStartTime - 2 + x + y, titan_gfx_RoboGraphic.drawLine(
                  averagePoints(previousHit, hitLocation, ((double) x) / 3),
                  averagePoints(previousHit, hitLocation, ((double) x + 1) / 3),
                  java.awt.Color.WHITE));
            }
          }
        }
        previousHit = hitLocation;
        hitStartTime += 3;
      }
    }
  }

  private java.awt.geom.Point2D.Double averagePoints(
      java.awt.geom.Point2D.Double p1, java.awt.geom.Point2D.Double p2, double weight) {
    return new java.awt.geom.Point2D.Double(p1.x * (1 - weight) + p2.x * weight,
                              p1.y * (1 - weight) + p2.y * weight);
  }

  private void addVictoryGraphic(long time, titan_gfx_RoboGraphic graphic) {
    if (!_victoryGraphics.containsKey(time)) {
      _victoryGraphics.put(time, new java.util.ArrayList<titan_gfx_RoboGraphic>());
    }
    _victoryGraphics.get(time).add(graphic);
  }
  public interface GunDataListener {
    void on1v1FiringWaveBreak(titan_utils_Wave w, double hitAngle, double tolerance);
    void onMarkFiringWave(titan_utils_Wave w);
  }
}

public static class titan_gun_GunEnemy extends titan_utils_Enemy<titan_utils_TimestampedFiringAngle> {
  private static final double NON_ZERO_VELOCITY_THRESHOLD = 0.1;

  public double previousVelocity;
  public double lastNonZeroVelocity;
  public long timeSinceDirectionChange;
  public long timeSinceVelocityChange;
  public long timeMovingAtMe;
  public titan_utils_Wave lastWaveFired;
  public java.util.List<java.awt.geom.Point2D.Double> hitLocations;
  private int _waveBreaks;
  private java.util.Collection<titan_gfx_RoboGraphic> _renderables;

  public titan_gun_GunEnemy(String botName, double distance, double energy,
      java.awt.geom.Point2D.Double location, int round, long time, double heading,
      double velocity, double absBearing, titan_utils_BattleField battleField,
      titan_utils_MovementPredictor predictor, java.util.Collection<titan_gfx_RoboGraphic> renderables) {
    this(botName, distance, energy, location, round, time, heading, velocity,
        absBearing, battleField, predictor, renderables, new titan_utils_WaveManager());
  }

  titan_gun_GunEnemy(String botName, double distance, double energy,
      java.awt.geom.Point2D.Double location, int round, long time, double heading,
      double velocity, double absBearing, titan_utils_BattleField battleField,
      titan_utils_MovementPredictor predictor, java.util.Collection<titan_gfx_RoboGraphic> renderables,
      titan_utils_WaveManager waveManager) {
    super(botName, location, distance, energy, heading, velocity, absBearing,
        round, time, battleField, predictor, waveManager);

    _waveBreaks = 0;
    _renderables = renderables;
    lastNonZeroVelocity = velocity;
    previousVelocity = 0;
    timeSinceDirectionChange = 0;
    timeSinceVelocityChange = 0;
    timeMovingAtMe = 0;
    lastWaveFired = null;
    hitLocations = new java.util.ArrayList<java.awt.geom.Point2D.Double>();
  }

  @Override
  public void initRound() {
    super.initRound();
    lastNonZeroVelocity = 0;
    previousVelocity = 0;
    timeSinceDirectionChange = 0;
    timeSinceVelocityChange = 0;
    hitLocations.clear();
  }

  public void execute(final long currentTime, long lastBulletFiredTime,
      double bulletPower, double currentGunHeat, java.awt.geom.Point2D.Double myLocation,
      final boolean is1v1, final int enemiesTotal,
      final java.util.List<titan_gun_GunDataManager.GunDataListener> gunDataListeners, final boolean painting) {
    _waveManager.checkCurrentWaves(currentTime,
        newUpdateWaveListener(myLocation, bulletPower, lastBulletFiredTime,
            currentGunHeat, is1v1, gunDataListeners));
    _waveManager.checkActiveWaves(currentTime, lastScanState,
        newWaveBreakListener(
            currentTime, enemiesTotal, is1v1, gunDataListeners, painting));
  }

  void updateWave(titan_utils_Wave w, java.awt.geom.Point2D.Double myLocation, double bulletPower,
      long lastBulletFiredTime, double gunHeat, boolean is1v1,
      java.util.List<titan_gun_GunDataManager.GunDataListener> gunDataListeners) {
    if (!w.altWave) {
      w.gunHeat = gunHeat;
      w.lastBulletFiredTime = lastBulletFiredTime;

      if (lastScanState.time == w.fireTime) {
        w.sourceLocation = myLocation;
        w.targetLocation = lastScanState.location;
        w.absBearing =
            titan_utils_Matematica.absoluteBearing(myLocation, lastScanState.location);
      }
    }
  }

  void processWaveBreak(titan_utils_Wave w, java.util.List<titan_utils_RobotState> waveBreakStates,
      long currentTime, int enemiesTotal,
      java.util.List<titan_gun_GunDataManager.GunDataListener> gunDataListeners) {
    if (waveBreakStates == null || waveBreakStates.isEmpty()) {

      return;
    }
    double guessFactor = 0;
    titan_utils_Wave.Intersection preciseIntersection = null;
    if (w.enemiesAlive == 1) {
      preciseIntersection = w.preciseIntersection(waveBreakStates);
      guessFactor = w.guessFactorPrecise(preciseIntersection.angle);
    }

    titan_utils_RobotState waveBreakState = getMedianWaveBreakState(waveBreakStates);
    java.awt.geom.Point2D.Double dispVector = w.displacementVector(waveBreakState);
    logWave(w, dispVector, guessFactor, currentTime, IS_VISIT);
    _waveBreaks++;

    if (w.enemiesAlive == 1 && w.firingWave && !w.altWave) {
      if (preciseIntersection == null) {
        preciseIntersection = w.preciseIntersection(waveBreakStates);
      }
      double hitAngle = preciseIntersection.angle;
      double tolerance = preciseIntersection.bandwidth;
      for (titan_gun_GunDataManager.GunDataListener listener : gunDataListeners) {
        listener.on1v1FiringWaveBreak(w, hitAngle, tolerance);
      }
    }
  }

  public titan_utils_Wave processBulletHit(robocode.Bullet bullet, long currentTime,
      boolean is1v1Battle, boolean logHitWave) {
    java.awt.geom.Point2D.Double bulletLocation = bulletLocation(bullet);
    titan_utils_Wave hitWave = _waveManager.findClosestWave(bulletLocation, currentTime,
        titan_utils_Wave.FIRING_WAVE, botName, bullet.getPower());
    if (hitWave != null) {
      if (logHitWave) {

        java.awt.geom.Point2D.Double hitVector =
            hitWave.displacementVector(lastScanState.location, currentTime);
        double hitFactor = 0;
        if (is1v1Battle) {
          hitFactor = hitWave.guessFactorPrecise(lastScanState.location);
        }
        logWave(hitWave, hitVector, hitFactor, currentTime, IS_BULLET_HIT);
      }
      return hitWave;
    }
    return null;
  }

  public void logBulletHitLocation(robocode.Bullet bullet) {
    hitLocations.add(bulletLocation(bullet));
  }

  private java.awt.geom.Point2D.Double bulletLocation(robocode.Bullet bullet) {
    return new java.awt.geom.Point2D.Double(bullet.getX(), bullet.getY());
  }

  void logWave(titan_utils_Wave w, java.awt.geom.Point2D.Double dispVector, double guessFactor, long time,
      boolean isVisit) {
    neuralVisit(w, guessFactor, isVisit);
    for (titan_utils_KnnView<titan_utils_TimestampedFiringAngle> view : views.values()) {
      if ((isVisit && view.logVisits || !isVisit && view.logBulletHits)
          && (view.logVirtual || w.firingWave)
          && (view.logMelee || w.enemiesAlive <= 1)) {
        view.logWave(w, new titan_utils_TimestampedFiringAngle(
            w.fireRound, time, guessFactor, dispVector));
      }
    }
  }

  public titan_utils_Wave newGunWave(java.awt.geom.Point2D.Double sourceLocation,
      java.awt.geom.Point2D.Double targetLocation, int fireRound, long fireTime,
      long lastBulletFiredTime, double bulletPower, double myEnergy,
      double gunHeat, int enemiesAlive, double accel, double dl8t, double dl20t,
      double dl40t, boolean altWave) {
    titan_utils_Wave newWave = new titan_utils_Wave(botName, sourceLocation, targetLocation,
        fireRound, fireTime, bulletPower, lastScanState.heading,
        lastScanState.velocity, titan_utils_Matematica.nonZeroSign(lastNonZeroVelocity),
        _battleField, _predictor)
            .setAccel(accel)
            .setDistance(sourceLocation.distance(targetLocation))
            .setDchangeTime(timeSinceDirectionChange)
            .setVchangeTime(timeSinceVelocityChange)
            .setDistanceLast8Ticks(dl8t)
            .setDistanceLast20Ticks(dl20t)
            .setDistanceLast40Ticks(dl40t)
            .setTargetEnergy(energy)
            .setSourceEnergy(myEnergy)
            .setGunHeat(gunHeat)
            .setEnemiesAlive(enemiesAlive)
            .setLastBulletFiredTime(lastBulletFiredTime)
            .setAltWave(altWave);
    newWave.setWallDistances(enemiesAlive <= 1
        ? titan_utils_Wave.WallDistanceStyle.ORBITAL : titan_utils_Wave.WallDistanceStyle.DIRECT);

    if (myEnergy > 0) {
      _waveManager.addWave(newWave);
    }
    return newWave;
  }

  public void markFiringWaves(
      long currentTime, boolean is1v1, java.util.List<titan_gun_GunDataManager.GunDataListener> gunDataListeners) {
    _waveManager.checkCurrentWaves(currentTime,
        newMarkFiringWavesListener(is1v1, gunDataListeners));
  }

  public titan_utils_Wave findSurfableWave(titan_utils_Wave aimWave, int surfIndex) {
    titan_utils_RobotState targetState = titan_utils_RobotState.newBuilder()
        .setLocation(aimWave.targetLocation)
        .setTime(aimWave.fireTime)
        .build();
    return _waveManager.findSurfableWave(
        surfIndex, targetState, titan_utils_Wave.WavePosition.BREAKING_CENTER);
  }

  public void updateTimers(double velocity) {
    if (Math.abs(velocity - lastNonZeroVelocity) > 0.5) {
      timeSinceVelocityChange = 0;
    }
    if (Math.abs(velocity) > NON_ZERO_VELOCITY_THRESHOLD) {
      if (Math.signum(velocity) != Math.signum(lastNonZeroVelocity)) {
        timeSinceDirectionChange = 0;
      }
      lastNonZeroVelocity = velocity;
    }
  }

  public long getLastWaveFireTime(String botName) {
    return _waveManager.getLastFireTime();
  }

  public titan_utils_Wave interpolateGunWave(long fireTime, long currentTime,
      double myHeading, double myVelocity, titan_utils_RobotState enemyState) {
    titan_utils_Wave w = _waveManager.interpolateWaveByFireTime(fireTime, currentTime,
        myHeading, myVelocity, stateLog, _battleField, _predictor);
    if (w.checkWavePosition(enemyState) == titan_utils_Wave.WavePosition.MIDAIR) {
      _waveManager.addWave(w);
      return w;
    }
    return null;
  }

  private titan_utils_RobotState getMedianWaveBreakState(java.util.List<titan_utils_RobotState> waveBreakStates) {
    return waveBreakStates.get(waveBreakStates.size() / 2);
  }

  public double advancingVelocity() {
    return
        -Math.cos(lastScanState.heading - absBearing) * lastScanState.velocity;
  }

  public boolean isRammer() {
    return (((double) timeMovingAtMe) / timeAliveTogether) > 0.5;
  }

  public int getWaveBreaks() {
    return _waveBreaks;
  }

  titan_utils_WaveManager getWaveManager() {
    return _waveManager;
  }

  private titan_utils_WaveManager.CurrentWaveListener newUpdateWaveListener(
      final java.awt.geom.Point2D.Double myLocation, final double bulletPower,
      final long lastBulletFiredTime, final double currentGunHeat,
      final boolean is1v1, final java.util.List<titan_gun_GunDataManager.GunDataListener> gunDataListeners) {
    return new titan_utils_WaveManager.CurrentWaveListener() {
      @Override
      public void onCurrentWave(titan_utils_Wave w) {
        updateWave(w, myLocation, bulletPower, lastBulletFiredTime,
            currentGunHeat, is1v1, gunDataListeners);
      }
    };
  }

  private titan_utils_WaveManager.WaveBreakListener newWaveBreakListener(
      final long currentTime, final int enemiesTotal, final boolean is1v1,
      final java.util.List<titan_gun_GunDataManager.GunDataListener> gunDataListeners, final boolean painting) {
    return new titan_utils_WaveManager.WaveBreakListener() {
      @Override
      public void onWaveBreak(titan_utils_Wave w, java.util.List<titan_utils_RobotState> waveBreakStates) {
        processWaveBreak(
            w, waveBreakStates, currentTime, enemiesTotal, gunDataListeners);
        if (painting && !is1v1) {
          drawDisplacementVector(
              w, currentTime, getMedianWaveBreakState(waveBreakStates));
        }
      }
    };
  }

  private titan_utils_WaveManager.CurrentWaveListener newMarkFiringWavesListener(
      final boolean is1v1, final java.util.List<titan_gun_GunDataManager.GunDataListener> gunDataListeners) {
    return new titan_utils_WaveManager.CurrentWaveListener() {
      @Override
      public void onCurrentWave(titan_utils_Wave w) {
        if (!w.altWave) {
          w.firingWave = true;
          if (is1v1) {
            for (titan_gun_GunDataManager.GunDataListener listener : gunDataListeners) {
              listener.onMarkFiringWave(w);
            }
          }
        }
      }
    };
  }

  private void drawDisplacementVector(
      titan_utils_Wave w, long currentTime, titan_utils_RobotState waveBreakState) {
    java.awt.geom.Point2D.Double dispVector = w.displacementVector(waveBreakState);
    double drawBearing = (w.orbitDirection
        * titan_utils_Matematica.absoluteBearing(titan_utils_Wave.ORIGIN, dispVector))
        + w.effectiveHeading();
    double drawDistance =
        dispVector.distance(titan_utils_Wave.ORIGIN) * (waveBreakState.time - w.fireTime);
    _renderables.add(titan_gfx_RoboGraphic.drawLine(
        titan_utils_Matematica.project(w.targetLocation, w.targetHeading, 25),
        titan_utils_Matematica.project(w.targetLocation, w.targetHeading + Math.PI, 25),
        java.awt.Color.darkGray));
    _renderables.add(titan_gfx_RoboGraphic.drawLine(
        titan_utils_Matematica.project(w.targetLocation, w.targetHeading + (Math.PI / 2), 25),
        titan_utils_Matematica.project(w.targetLocation, w.targetHeading - (Math.PI / 2), 25),
        java.awt.Color.darkGray));
    _renderables.addAll(java.util.Arrays.asList(
        titan_gfx_RoboGraphic.drawArrowHead(
            titan_utils_Matematica.project(w.targetLocation, w.effectiveHeading(), 25),
            10, w.effectiveHeading(), java.awt.Color.darkGray)));
    _renderables.add(
        titan_gfx_RoboGraphic.drawLine(w.targetLocation,
            titan_utils_Matematica.project(w.targetLocation, drawBearing, drawDistance),
        java.awt.Color.red));
    _renderables.add(
        titan_gfx_RoboGraphic.drawCircleFilled(waveBreakState.location, java.awt.Color.red, 4));
    _renderables.add(titan_gfx_RoboGraphic.drawPoint(w.targetLocation, java.awt.Color.red));
    _renderables.add(titan_gfx_RoboGraphic.drawText(
        Long.toString(currentTime-w.fireTime), w.targetLocation.x - 8,
        w.targetLocation.y - 20, java.awt.Color.white));
  }
}

public static class titan_gun_MainGun extends titan_gun_DuelGun<titan_utils_TimestampedFiringAngle> {
  private static final String VIEW_NAME = "Main";
  private static final int K_SIZE = 100;
  private static final int K_DIVISOR = 10;

  private titan_gun_GunDataManager _gunDataManager;
  private titan_utils_BattleField _battleField;
  private java.util.Collection<titan_gfx_RoboGraphic> _renderables;

  public titan_gun_MainGun(titan_gun_GunDataManager gunDataManager, titan_utils_BattleField battleField,
      java.util.Collection<titan_gfx_RoboGraphic> renderables) {
    super();
    _gunDataManager = gunDataManager;
    _battleField = battleField;
    _renderables = renderables;
  }

  @Override
  public String getLabel() {
    return "Main Gun";
  }

  @Override
  protected double aimInternal(titan_utils_Wave w, boolean painting) {
    titan_gun_GunEnemy gunData = _gunDataManager.getEnemyData(w.botName);
    titan_utils_KnnView<titan_utils_TimestampedFiringAngle> view = gunData.views.get(VIEW_NAME);
    if (view.size() == 0) {
      return w.absBearing;
    }

    java.util.List<titan_KdTree.Entry<titan_utils_TimestampedFiringAngle>> nearestNeighbors =
        view.nearestNeighbors(w, true);
    int numScans = nearestNeighbors.size();
    Double[] firingAngles = new Double[numScans];
    for (int x = 0; x < numScans; x++) {
      java.awt.geom.Point2D.Double dispVector =
          nearestNeighbors.get(x).value.displacementVector;
      java.awt.geom.Point2D.Double projectedLocation =
          w.projectLocationFromDisplacementVector(dispVector);
      if (!_battleField.rectangle.contains(projectedLocation)) {
        firingAngles[x] = null;
      } else {
        firingAngles[x] = robocode.util.Utils.normalRelativeAngle(
            w.firingAngleFromTargetLocation(projectedLocation)
                - w.absBearing);
      }
    }

    Double bestAngle = null;
    double bestDensity = Double.NEGATIVE_INFINITY;
    double bandwidth = 2 * titan_utils_Matematica.botWidthAimAngle(
        w.sourceLocation.distance(w.targetLocation));
    titan_gfx_ColoredValueSet cvs = new titan_gfx_ColoredValueSet();

    for (int x = 0; x < numScans; x++) {
      if (firingAngles[x] == null) {
        continue;
      }

      double xFiringAngle = firingAngles[x];
      double xDensity = 0;
      for (int y = 0; y < numScans; y++) {
        if (x == y || firingAngles[y] == null) {
          continue;
        }

        double yFiringAngle = firingAngles[y];
        double ux = (xFiringAngle - yFiringAngle) / bandwidth;
        xDensity += Math.exp(-0.5 * ux * ux);
      }

      if (xDensity > bestDensity) {
        bestAngle = xFiringAngle;
        bestDensity = xDensity;
      }

      if (painting) {
        cvs.addValue(xDensity, w.absBearing + xFiringAngle);
      }
    }

    if (bestAngle == null) {
      return w.absBearing;
    }

    if (painting) {
      titan_gun_Mira.drawGunAngles(
          _renderables, w, cvs, w.absBearing + bestAngle, bandwidth);
    }

    return robocode.util.Utils.normalAbsoluteAngle(w.absBearing + bestAngle);
  }

  @Override
  public java.util.List<titan_utils_KnnView<titan_utils_TimestampedFiringAngle>> newDataViews() {
    java.util.List<titan_utils_KnnView<titan_utils_TimestampedFiringAngle>> views =
        new java.util.ArrayList<titan_utils_KnnView<titan_utils_TimestampedFiringAngle>>();
    views.add(new titan_utils_KnnView<titan_utils_TimestampedFiringAngle>(
        new titan_gun_formulas_MainGunFormula(_gunDataManager.getEnemiesTotal()))
            .setK(K_SIZE)
            .setKDivisor(K_DIVISOR)
            .visitsOn()
            .virtualWavesOn()
            .meleeOn()
            .setName(VIEW_NAME));
    return views;
  }
}

public static class titan_gun_MeleeGun {
  private static final String VIEW_NAME = "Melee";
  private static final int MAX_SCANS = 100;

  private titan_gun_GunDataManager _gunDataManager;
  private titan_utils_BattleField _battleField;
  private java.util.Collection<titan_gfx_RoboGraphic> _renderables;

  public titan_gun_MeleeGun(titan_gun_GunDataManager gunDataManager, titan_utils_BattleField battleField,
      java.util.Collection<titan_gfx_RoboGraphic> renderables) {
    _gunDataManager = gunDataManager;
    _battleField = battleField;
    _renderables = renderables;
  }

  public double aimAtEveryone(java.awt.geom.Point2D.Double myNextLocation,
      long currentTime, int enemiesAlive, double bulletPower,
      titan_gun_GunEnemy closestBot, boolean painting) {
    java.util.List<MeleeFiringAngle> firingAngles = new java.util.ArrayList<MeleeFiringAngle>();

    int kSize = getCommonKsize(enemiesAlive);
    for (titan_gun_GunEnemy gunData : _gunDataManager.getAllEnemyData()) {
      if (gunData.alive && gunData.views.get(VIEW_NAME).size() >= 10
          && gunData.lastWaveFired != null) {
        java.util.List<MeleeFiringAngle> enemyAngles = new java.util.ArrayList<MeleeFiringAngle>();
        titan_utils_Wave aimWave = gunData.lastWaveFired;
        aimWave.setBulletPower(bulletPower);
        titan_utils_KnnView<titan_utils_TimestampedFiringAngle> view = gunData.views.get(VIEW_NAME);
        java.util.List<titan_KdTree.Entry<titan_utils_TimestampedFiringAngle>> nearestNeighbors =
            view.nearestNeighbors(aimWave, true, kSize);

        int numScans = nearestNeighbors.size();
        double totalScanWeight = 0;
        for (int x = 0; x < numScans; x++) {
          titan_KdTree.Entry<titan_utils_TimestampedFiringAngle> entry = nearestNeighbors.get(x);
          double scanWeight = 1 / Math.sqrt(Math.max(1e-9, entry.distance));
          totalScanWeight += scanWeight;
          java.awt.geom.Point2D.Double vector = entry.value.displacementVector;
          MeleeFiringAngle firingAngle = getFiringAngle(
              myNextLocation, currentTime, vector, scanWeight, aimWave);
          if (firingAngle != null) {
            enemyAngles.add(firingAngle);
          }
        }
        for (MeleeFiringAngle enemyAngle : enemyAngles) {
          enemyAngle.scanWeight /= totalScanWeight;
        }
        firingAngles.addAll(enemyAngles);
      }
    }

    Double bestAngle = null;
    double bestDensity = Double.NEGATIVE_INFINITY;
    titan_utils_Wave bestWave = null;
    titan_gfx_ColoredValueSet cvs = new titan_gfx_ColoredValueSet();

    for (MeleeFiringAngle xFiringAngle : firingAngles) {
      double xDensity = 0;
      for (MeleeFiringAngle yFiringAngle : firingAngles) {
        double ux =
            robocode.util.Utils.normalRelativeAngle(xFiringAngle.angle - yFiringAngle.angle)
                / yFiringAngle.bandwidth;
        xDensity += yFiringAngle.scanWeight * Math.exp(-0.5 * ux * ux)
            / yFiringAngle.distance;
      }

      if (xDensity > bestDensity) {
        bestAngle = xFiringAngle.angle;
        bestDensity = xDensity;
        bestWave = xFiringAngle.wave;
      }

      if (painting) {
        cvs.addValue(xDensity, xFiringAngle.angle);
      }
    }

    if (firingAngles.isEmpty() || bestAngle == null) {
      return closestBot.lastWaveFired.absBearing;
    }

    if (painting) {
      double bandwidth = titan_utils_Matematica.botWidthAimAngle(
          myNextLocation.distance(closestBot.lastScanState.location));
      titan_gun_Mira.drawGunAngles(
          _renderables, bestWave, cvs, bestAngle, bandwidth);
    }

    return robocode.util.Utils.normalAbsoluteAngle(bestAngle);
  }

  private int getCommonKsize(int enemiesAlive) {
    int kSize = MAX_SCANS / enemiesAlive;
    for (titan_gun_GunEnemy gunData : _gunDataManager.getAllEnemyData()) {
      if (gunData.alive && gunData.views.get(VIEW_NAME).size() >= 10
          && gunData.lastWaveFired != null) {
        kSize = Math.min(kSize, gunData.views.get(VIEW_NAME).size() / 10);
      }
    }
    return kSize;
  }

  private MeleeFiringAngle getFiringAngle(java.awt.geom.Point2D.Double myNextLocation,
      long currentTime, java.awt.geom.Point2D.Double dispVector, double scanWeight,
      titan_utils_Wave aimWave) {
    java.awt.geom.Point2D.Double projectedLocation = aimWave.projectLocationBlind(
        myNextLocation, dispVector, currentTime);
    if (_battleField.rectangle.contains(projectedLocation)) {
      double distance = myNextLocation.distance(projectedLocation);
      return new MeleeFiringAngle(
          titan_utils_Matematica.absoluteBearing(myNextLocation, projectedLocation),
          distance, titan_utils_Matematica.botWidthAimAngle(distance), scanWeight,
          aimWave);
    }
    return null;
  }

  public java.util.List<titan_utils_KnnView<titan_utils_TimestampedFiringAngle>> newDataViews() {
    java.util.List<titan_utils_KnnView<titan_utils_TimestampedFiringAngle>> views =
        new java.util.ArrayList<titan_utils_KnnView<titan_utils_TimestampedFiringAngle>>();
    titan_utils_KnnView<titan_utils_TimestampedFiringAngle> meleeView =
        new titan_utils_KnnView<titan_utils_TimestampedFiringAngle>(new titan_gun_formulas_MeleeFormula())
            .visitsOn()
            .virtualWavesOn()
            .meleeOn()
            .setName(VIEW_NAME);
    views.add(meleeView);
    return views;
  }

  private static class MeleeFiringAngle {
    public final double angle;
    public final double distance;
    public final double bandwidth;
    public double scanWeight;
    public final titan_utils_Wave wave;

    public MeleeFiringAngle(double angle, double distance, double bandwidth,
        double scanWeight, titan_utils_Wave wave) {
      this.angle = angle;
      this.distance = distance;
      this.bandwidth = bandwidth;
      this.scanWeight = scanWeight;
      this.wave = wave;
    }
  }
}

public static class titan_gun_PerceptualGun<T> extends titan_gun_DuelGun<T> {
  private static final int NUM_POINTS = 1000;
  private static final int WEIGHT_BITS = 7;
  private static final int COORDINATE_BITS = 7;
  private static final int GUESSFACTOR_BITS = 7;
  private static final int NUM_ATTR = 7;

  private static final String SKNN_HEX_STRING =
      "0xD10FA3F5D2E2849DC483EEA6AE53F71275C7CD9BAC3D4CCD79626D0D699C383D962BE9D930EEB61575C8F8617E6CE8DB42E303A7671F932E5250FC090A3DEE5199A217A3D7AF576891E95F6A08B07B94D0C1A12DCAB2741C94BDEDA05B9FB60D2E81ED633B429EC752F566CF698E219D61DBFE646FE6E73A7A0FCBBF504E923F8F0686924D7549E0307C921A0023315CAE5F456AC5DC653B69E8E45DAC55A34E96B4A85EF91F6A958F9BB39859894A86E814D485B08F508C15C8FF623D20D746B8648FAC612A7E436B2FE4435DB8B2DE0CDFB505D69321616B9A28BEB569A7EA92A6EE401CC792BC9893691C865CC1A7230576282C27407D3A80F5B3DA01B6BCA380BCD59C4E7BC8CD9E0FBBED2E331CB2F85800F3837B14A858A2CEA09EB2A94456F7455A3024300E36CAAEE50ADFAA8A5C5EF4A9E89BFD25578991B3DF470CA1EACE3C6042935AC42DE9A6C7ECDB9331111BBF0DBAEC69FB27101E1A19CDFC75842A8D56068AA9441D29510342FD202181AF7D66E8A951D606AF5BA076B8B7EFE940ACCD281379FB11B24B0541C9E4E2FEE196BB6DED84429F0780E3F9DE57EF8F0E1A0AAA086FDF926AA10A256B2BB36BF16CA31BB957E33BDFEFD30011A81286B0683EB17519621ABD3F1229A051ADF5CCB59EB12B8CB102DB2E6E8FB9DFFD2053A0EC91BFB7E6B27AC30C157ABB32EC7FE011C1C41F63078928273466593586F8C74DB1DEFC84801259C2465726734C904FF3E70212B83001BA7C27E700B71DBB441F68D545D8945DDEFEA69A542E2ADD000D5561EC8BE72821E1FB6271B2C7DFCBA1ADBCEA445D9F0028344D262020C4ACE0350EC262A9FD2B82EB53F9B7A77E333802669917689E92FFB6BF4A23006886794F5B939278359B7FFC0BD4A205ABFED4349220FFA0A8A2E28852FE8F3CE72975032434A07A9F119ABAB83FC0A78A4586719BD37A66D3C69132A546DE4648233E6C8524506E60AFD13F919C944A728A72324B670EA01AAE4DCBCD780DDF26E0CA388B3C1537A4161E4093456B459AC999F170A8F0B423DF1921A545270CA6966D095FFE7A613729915B038CE0066033E8010CC2FF611DD8ED6EAA9960F26A482E756BA7C7E3819815FDB9250997DED15907922530BED3AF697DFF36BFA950B0774C4038DD5C6BE1B64DFFF4536AB3CA2E81ACA73455914F802EBD0270D4E40B8B56E907CA134101E12214DDFA87ABB493A2ACB184DC8BE163BF6D021A3C6E72A80BBB90E346DEEE517DBC825C93986ED012D9E8F430329A7B803F8890543BECFE6E4F79804079C4EA4AAADFC0B6453952599CD70E86C1A000E38648D6F970EC2B86446727B93A0394B2114A35BE9876939CB4BD4A74C6D96683AA973A682C6C9DD6248EC3B27370112ABEC2D03975350B539FCEA2907B88B1E94043C5476CB88F9F77DFDD176B5982EEA511F1FEFB74F39AE4B111EFB7D1A4D7FED40E70E0829573D9CEE759AA593763D1D95B1BE9B1405AC89B3FA9A0ED671DF30FADC9D914180EF3577910921BD65F7C5AEC7692FDA99FA6469B64B30E9C5249F65550EAA2B5C6D2EE1AD286DA99C1E10E46ADA00D680D5219FC0C2984A2439A665EC953A86F1E569DCFF99EB6FB30073AC034208AB176C235AF0FD97C7399D311CEB14C3A824E86A444C5B59053D9B899E90BF52FCA73E3775D34FF609860369B5B9CC0FBABF408F08FB488E9FFAF660223E1718B89B6384A381817E26F24FEDA8D11703810E12C393CF4E817E92D27BFA530189F880B0627B3532821E1476F99398E088482E347640CBD4F70FD430D5004FF7B58367D911AC850CB1D3A44DE6D10EEF28BB99F1A26A6BB04D672723A1D20EA9E3CB9115C070116E39846BB9193B23182A20C0DB1AF74EC0F991265F28297B20221A29E58523191BAF37994303819A243B5A109724BB6DC0C4FB74E3EDF8DBCDB68B7F3B50435D9056164158A3C50C00A917CC27A40EAB3A6B26F6808E06787608DEC0A15ADCB9DDAECD10129215B629947C38B7D72DB0671728BA3314121FF99AD8551D87B950F932D594B03F0941F691145D72414BAD0A9F663AF3147B5F702FB881F9F0F4194E2B8B1473A0AB04407530E9F791624B5D8C2810AFF5D8FA91A0B4897A3A32F260FD31B5DBC46E40A52701FCCD350F3E32D716B0E8BE5D22237BC02D4DD03C486D9495FF32121BD169A7455A55283C6C4F024AF1EBFBCAFAFE734F0A4262A333E024CE492456C7BA6BEE18D34E668412C1212DA2E1426E481BC5DF158576A99A16E873058534C2BF063B9E1B529E0DC8830AD9025413A5F46A7B206B74C3E0095881D1293AEABB33DE242B169EED32BF0FA4174FE8FFBB5CF8B513D6B3E6BAAA6F290DAA7E8080C7ED9DA4A1AFA1EC2AAA16928B79B391034D4E4A479F61268E86BFD9F0D4001801DB77EA5430E6820D27219A2A8FCFC309BF13D3467DECB4A6E138380EFEDAD70A6DDDE0255398589AC275DF54B6D41BC24AAD26F36B2B249FDD266FF97C8E9B37AF8007BA88B77609F9A1F81166A2CFDA5EFF151EC103B3E14657003224BF941E5A4F879646E882D1FB8BFD8D07C9651477EA095BF6C0F8C82AE265DCF1E7FC7E79BBE03CFBD14C7250FD0E826088C8FD24906DE9BFCE44D3D90E60CA220B4688A95CCC26BE1E14480481EC63AEAB09B5ADD8AE7598AC31EBAEE4E3EB740EB2D90F11E5848FD5715F616203BD4A6734AE15226ED7E4B62969171BFC4E121723F1914FB91810704F39EF7591EBF887859A1865CD96B901E7D05477004A38D1F803B46457B90339F779C2BA55ACABD1996D6657B54881E7906305409707F8F7F303E8100AE419F7906485B4DADAF1EB74289DAFD05309A59C52E3B73E984113F8AC26FB4D3D8DA3F3809C273E2299B0915043E1B46A8623240A0C30266A75F377ADFA2E7CB0EAC8A4D7B63B90076E49BF7A5275947A62A42BBDC014A067A489AE58C589365389F8C54A60440E09668D3A0990F7018446571CF0F5642278FD50379EB43EC4920475A7802A238C3867F59AB1F6E483B22CD6BDF7B27888A692930437536A148459CF47EE9D8719FC10A1A0F155B0219DF52DC2888118B3C9F4EFF090388B909ADC214382E852DB24CB0B3A3E16DF5D8DCEBFF48B85914F7A72707C122329061C0B23E08D28420D2EA9CABEE858F4C16C187D2588F01C800B44187A277D8A5C0004EB10EC64F93D6F44D58EA18A75C9375BF2955612DB62E83E4A7B06C474BA47CDDD8E5ACDF71713CDAB7999ED27DBFD6D2A8CA5F43BBC8ADEE99D825B8EFC2A29733C66FD4457DDD92DBEEC994D7E9262002D507B2223265E81948CB87174B140DE54DD4BC921450FF8146BCA7F5A29A4BA0C56A425CA3175C2258251C72B0099CC059E53F4C64CE7CB2AFB92341977C46D5C81714A97E65B127D5AB81ECE73E62C50A1CEAC5719009EF231E8916D3116D3C5A45F9ED12AC7EA08D045348F630FBA278C70A579995F60D013A0ACC35CBCF634918A6626C9463FE41FEC62DA7B28BF941EFF117FD623516CFEF73E78DF9E5FB23D729C5098C415E9E07A086EDCE3F243401519BF9B8A58D38E5EEA7ADB45F61D990649E457FF2C0267D01ED846D89B805FFA7B6C26ED93844E1FD1B35E9065670F8BC38669BB5E558986840F0F64EFEAC48442C7DA6202F3D7C3C1E837DEE4D2F162CCE3726BB686BC0F2C263F11D579AFDA90CD2EAC8F35B41A0D24C0B0B390F7BF0A987CF8C2E1D41D46C39A3AD42E31658FEE78A178B3C1A5E1E2E2B731749540A03CA8FEFB66FD2AD05E290ECEA8C02C03D5E31C1BEAD376E3279AACB37624E9419565B927D30DA10B6B12D7504B426F2FE0A2E5CC1195E9E8EE2D7D2766EC3085BD49842D1C8C248CCF281F7B8A3DC4E649A66312812EA5A75728D20B99C7AFC9734831347AC30255D74747AEEE783BAE93C72192FEE234FD4E813C5649F4204A6F9B61B2A11B180C92A871985B3B7DC38662F27EB415D511354577E8820F495B14F0E41F35A645DA9AD585FF95ECD6A86BE02F11F75D1FA3874165326E35D4557BEE3D668ED5A718632BDEF2BC55783A2D1E1E4908A6B4090FF759E8F9B87A896D8E6CC9729B59E92EB4AF1A643D8DF14F156887C2B017356AFF2B476985B588386FEF447C91E6675222F62E1B1688AACACD1A4B7ABCB110741B03FF179C8A34932E7DFA710EC388C8AE982465686CA05FB3DF3D4A6A33B733DB7A447AC4103C13AE7A547AD7213E64E9E597DB9263878AB10C3214E730DE17777D7F7E68FA6078AFBD951054D91FFD814EDFB02CFBA6D8D7D7565DC185126CE9A86E36ED5C2FC5893E3D625DC3B40E0FB8BDE5F334D4E1CA39C35BC47D9C2F0B3AACA180E7EE82CF7A87F9BC564F2D0B8CAC8C052A6B0FF7A180B4E189BB26D2F8C38399D41DA52182155BF21B27C221ADC945012393760391092FB98C9D5D00719D491F18FFF247959B343A8D2F2B300F2E52D97282E993893A4A10C8446BFA4315AE0CEED7427C1F0BB743DF9EFAF9C7C50922903F718311AE6D30F5B27B7B907ACF1846F614FB185DC40F90C57BC3DB85D7A86D2605BB01FDBDB49A09B47AC856A6BD10D91FB22FA66D36935D3D44B70F639003C53D74E6D596A832A56CC6DBB529E902687FBE64C19B2A4C171D57730161C13E8796B4F4826B010B9F5C8B49CE3EB0760924B5332328E0128136636AB0A668C418BB80544D0AFAD56E487F61DF23B20A76333DE5CA17DC47095162E362260E8276702D9445E6E063E7096DF7DA52D7701A13C42943CEA613B317A157E6DAE046485585C99B60EC0519EBFAC54A3B9E736772114A27F0718A68522F7E3C9AABC0A1DA9CB6F5DBA04D8387B18D5F39B42317EF31804A7B92D1C980F687042A265868344C9D3E2E059A4392FF5BACBE467D1D889E677B1A52D8274961809492139638B4B5A51C936AA3FAE6AE0466BEFC218F65DF1FC15097D7BF4F32C9DCADAF6DFACC49E0CF2794A879B1AB4AFCC4BEEF37603985C2394E5B381BF5E2280EBC42FFAA2EAFC065A99DB0538413EC876A50BDFA12AA8FBB024CE0AD330015DEF875C04128B1FE046631F029438671B71C880B03BE9B5F9FBF573216AE1B60042D5C9B4FFBAA5A65383A9AF54BCE840D42ABC274D350B5CFB9EE9C189FB9F4A5163E65A7C276F0DDC9056CE36AEBDFD3FC60C5233B9039FB3A74A818335466F0FDCBE109C89847AACE4BF03F12B74F69A0A2C1451BE1EDDC8CD86F54D6A3BC0F2FC7F7673FC6EA1461CF4ADAFD532663BDD845F1F1B92CEEE7A00071F0E68A6A736E5BD14E42390429CDC2527638A3E7938848EDB6E2BA4D7C299C13F51938D06BC31AC2CFD7BC992BE3866C24F537DC67C497F1185922FF71489214039CAB012C5CDF86B255E5FFEEB47665BCE3F4975EF19395F9FB41DB08FC4AE7DFEFBC326FA3ECA1C8BF9DDF6A0E883F05C68E3204A4233741AC14C3EDFE085A0D326E610116F8A6710FE6353ACFE0A3B3F3D1E134F9C97BFA27681EB501F2F601A507FF125619984BCDB1E375A4EA3224E8E5749278BFAB390D5429B3444E88622168B62019BE960D3E1A9F46578D154DCDC3122A47D1214EF18936A82567694D2F21EC21B52D4F3139EC9094957D51DDA5FC5F5CC852DF47773B6D3BAEDF651EF568B46BCBDBBF5D3EFE25741E536861E94A6DF7CF371B120B3F812BE78B9FACC5E244070A70CE6FCEE2790EEC58A18A24A5244C0021C78A2E33C7A04CF161045AF11B2F3567D17768692050CC9DAC03AB13297EBBD7304E437F5E79332C0E37784D4A7B64B52021918CCE6C945B2764B7BEE68509D57E411E7C8897E5C60ABAE5976352FB78AB378938DC12A11CC0155F1A20987F601AD196E1F2EADA941BCCF087E33A5B89C1FC2D5CF03C51F7518653E93549DDA22FC56EA0560BFCAF2B2458C3942D1B420FC2817FCD4C63E2CC7728D7ED8CD5C2E0235E26447E1387BDD0DEAA942C6BF855A307F7CCA885C8DFCD152E11C628CE1A532163CC6A51CA90EDAD6622B71FD1AFD05613160768B34A77B9D9D0D1E613EB5D93B830E1572A875B52B21130C54B23FA78523A1968F0FFFF7257E608B97C737A09893A3D8E90A31FEA6D71DE5F03A7D435F6DB995F7C32CA7D229CED5C958BE56F8A09F3171AA7818F89AE558F574B7F6BC5F9F21C5C885FC43994C256A5CCBA7A7740A03D1D77AA699805A40E94502BEDD0F51B8D9A28391533E4DEA9D3B2F0382E57764973EF0DE2A2C0C913D3C1BF198BFEC099A4FC42A9E36B6BCE130FAFEF5D20601C45E3E7D3921B14AAA2B119118D1386F976CD4FCAF70F6C4EE5898A6182FAEE50BBC2BE123BCD37ED8AD94208C34E3674B7098F92BEDC5C335902D0F6057A33F1A5F6DE11D3C438D4C3D2CA8EEB5BF684245FDEF3BC7BBFBEB2C828C35C6EF18BD4B2444647059BA7121F495D0AFB39549EA81FC44E6547DF3D07114B408DD366BBCBF4F7C089626B68B5F7364097102A35B7596DA62BEF7B2EA82B11D611EC3C0FDE3239F6243AD167511014CE4427727A8CC74ADB3FC7684FD8E03D0BEDEEFA43CBE65A13E6BFB41F65809CEEA2F112D03EEBEA5BC13A6ADB8D2A72795869CA449F312D155A3AFD13B22776D33B02CC6951D2D8A20F52E2F9CC3F67E860A2CF52FC153D23092BA1BE50B7172A582B70947A909EE92B2AD2E8A9ED6864782983B8CCD9EB4F8053AE8DF5259654A82E33CCD0AFE4571FBD3A7B967DE03BC860AA2D5B5B63B8412900834E826933A2EF178EB3C040EDD678D8479F96EAE8B43C681709F60B789E5191703BEBF65690A28EBF498FA70FC29A045A3CDA262B0F48FECBF317488EBEB66533C1956AE8CB6F138FC831F7F3A4B2CA5E3F3BADA29C5CFA09758038DFE45BF642C0E709F9C589241743964EA5B4ADE59ECAD2885A2854B0B3240DF612C068D9FAB871089A235AD5C54B8B2CF05309FF736262D73940263F1E7B1389EB9C0306552E3172EEBE7BCF7EDC96B399FA297201F1F0EFD807E61871923B76225E02EE71CED20015279975D504C0F0AF5A408307CF60635D474B2B06DBF22F41EDF106D5C3437707FF2CEC48FE071D13967E52C04E6D897C37B0256AA1D535B49871B2F784483FB2BA23DE2AB0A0A3DFE6B0AA4357A7CE7D26423606311F3BAA3254C2C939DE85035EA49CF4A890023B06B9047E4E2F9AB465A87B4D2D17CD2004FF1D0B8A16C14879E9F1661E6434D203E418119DD4E6D8F0B49EE317EB17D18F5921935BFACD5B136B7BC1156E0385F02F004AE036250135AAA7079BE966BE6669E358E70362A148845618CFE8DC5D8170E2B4A8B11D03A20E4D9DC324A106A474CC297BFAC2D3BE9B86479F3C6C708F87685A82DBCB9EE3E5B7013EA567FB3D318818F4D5120B3E14784B1ABD81258200AA65A91FB3015F03F2F00F92F1848FD055617D3407B0CFC6ACB97397782DE96CC79BA615E12BEB1F7AC037139015580170C4BE6133CF70164778A9B065174D5D998B97E0308F6FBB9F8EEEE7A66DE59D0669B02BD490B014186EB0F23AFC4FAC789AEC63D8AC85511670360D4CFDA75B713F450B8CDA9D27C3CC4BEDEAD4F2116F40C589E301F3025A44F726DA9D1EA95F7AA5466D7655333C41D4BA70F731B162185206ACD8BA8F120632C9EA5E6FFE1F6BBE7423929BC6D10A84F22FC478B14180C294A77EFA2B504046AB62830F49F0A5C4A2C706E132B4CE6A362F1A56B6B71AEE35C90710A012C154895B5CA46966FD2D161A6EC1186392897E6786053A0FAF3B2DD8EA2FEE8F2E949D8392C243D40EC1B4C42704147DF42917FB85AA36D5F74633F0E873B55BA3645E065AAD77C82077B0A2AC8E09C5304BD153DF50EEA918798A1213635AF7B33F0095064C0C6BBC53DCA8BA10321FBADC2EA58ECDD3970EDB1FB3D784E4D4A6EF11F9438BAF7B8D0CE4FB7F9B9A48CCA3F19F1A33EDFA24CC7C89CD7DC3295501F00ECCD47ED50E8E92C8341BE92DBBA493CC1A8727F5B2DF67C94DE357D7CCA5E0EE7A676C6A480D8B82B0FFFA9EB042C248B5C0CA5BDC258AC78D22101698A75924D079526A8B737AFC63D352E7106D3DE8A4CD9058BED7A3A9A72E890194A834F0BC7BBE713498DFF1F2DABAE6CF969AC7FF6CB7CD254BBD5710C6C1009B6A919871132E1F66632915F1594838A6C39F58D3412323FBA476602C1D766364521992EF003A5B1D598DD72EF6395373ED285F195EC92D9BC3AC51585188EE314498B7DF9ABF6855F248C180EEFAC986753D47EAF816B04B1223CD162483349EB044FBEC61635EEEAC6806097EAD62A1E593B82D6C07076E1561BB4F161A47FBDB2400379F795B1D7D3F5CE38E0CB640F4248BDDE99FF9398D4BBAD493031BB398B384EB1714716D67086AA1DDEBF4C5686B4403238B55437D574E33B91539C98D1D5FC68C2308F5A93C1E8D27EA9C73915A4E1664B63433768B60F8A0ED7A4E02323E740D64354D039FDEAE234BE22A20FEA025C2E72C559971E044C60FD1C3B9D9DB89CAE47BD3BF3083558AE0078A3BA5C285EA5CFF33BFF23C2770B9B30C3AB21DBF3617BD1B26552D7FCE4C44FF98E2737B987D9CB4289F6E1CC04B756D72DFA2CAF35C2CEF3D9BD64395DEDC1AC013B638CD9138BCE5AAA282D1F97E87664DD6835F8FC9568FDC0F3A2A475C8A71AFCF08E25827A764A9D7E52C58D980E213CE0AF0892A2B9D41E52E5DDB5BAAFB0C4C6D92306EA895212108C57FD27C0386080DA4A791035F89661B39087A2627C96EA6D85600A0D2C191B0A235508D66097F0463827F7879F5125E67F3980FF4946A9D52100C2E6458E0AE60EDF428D5ABAD8051B9647858E3A74924ABF67182CF6C2AD31511AF93639CF1E6BF569D6155E28FD6FB2FC59458E157C9A469571D189EC736106A302BFC0F4012CACBC77EC63EE26F676A382B15134BA2779EADB93A92B6CB820BDFFAAC581654AE53579082E2E7CF181D436E8679E91D076B1C7E2B429E439B35FC4A71487AC52D7E29DFCA7B576FAD4372E1BC59FACE08B9AB68A7905F71F72B80949B43C6A38A9F7DF571F9C55A2796AEFF07CA8B0910A8B0A6BF0535D1A314DD0114CFA2D833288284CD814289253C3C3C3F6426957877E9E11AB38F7DF2A1BC3AC2736D49364FB84A5BC0D98BD77D82A853C2559D58D965875D736DD1DAF692C791F4BCB2F4819C163B7347EF48C4A42C7EBF84EAB49595A9A881D7ACC354889782756EE02F1F22ACF7EE7D26C87B4777C936416F1BC9D72D462295613BA4A19529E677FC7812FF8565DE65ECABA551D55B209F1E786F11EC1F8374C8042214EA3F8F640B08226495944239ADE8C166B715BE807E81EC72B58F6829A02F511955D449F79C3B632CFE8FE87582CFF67CFD3FEC82C47F64F9C8633C84D0C265039A52915C9820A602186BA654AC347998E2456E5C2A54CE147E950B356FD921CC398E2F8381F4A9F94B07C877EE4D38175A308EDDD98B864DD18B6C60F2FC92BAFFAA9C4B411C105893704035C0DB8FC1B3AEC1595A8B13598D7F25D382C234A22B670733AFD80510CED8D5C54701DBA33FC847F514C46FF145D3B768620EE1B845FEC5DEE94455C396AEEE43717939E818D5C620C2851D3BA1C7AF9FF277AC2048D14A571DA43166D680BE7CBDFA4534F8BF3867EE21E82BA10BEE6A784A21F3FD7ABE017F3B5F48DB0AD8EBF8783AD2B84AAC89D62E27BA74678465F18327357443DABC972FBEE96D7C68ECEF2D5F422EC4794CB597E7BA6FD712E06EC82688F3A1B680AD7A704B781456360E36A09F2FA8E6E3856E96FD8AE337A09AD516CB6F8A8986E3FEFFF1FAB4A3F3BD605C10080B867C43D3CCB0382E932591F344F6EA96093B880BBA1B211EF9F00F8459731A187D5FEC9745B4B747FBDF8E847F515E9F25838A4AF9C3020DEDF454E42E7F0928A0";
  private static final titan_utils_genetic_DnaString SKNN_DNA_STRING = new titan_utils_genetic_DnaString(
      knnSpaceSequence(
          NUM_POINTS, NUM_ATTR, WEIGHT_BITS, COORDINATE_BITS, GUESSFACTOR_BITS),
      SKNN_HEX_STRING);
  private static final double[] WEIGHTS =
      weightsFromDnaString(SKNN_DNA_STRING, NUM_ATTR);
  private static final titan_estruturas_dataStructures_trees_thirdGenKD_KdTree<Integer> TREE =
      kdTreeFromDnaString(SKNN_DNA_STRING, WEIGHTS);
  private static final int GUESSFACTORS = power(2, GUESSFACTOR_BITS);

  private titan_estruturas_dataStructures_trees_thirdGenKD_DistanceFunction _distFunc;

  public titan_gun_PerceptualGun() {
    super();
    _distFunc = new titan_estruturas_dataStructures_trees_thirdGenKD_SquareEuclideanDistanceFunction();
  }

  @Override
  public String getLabel() {
    return "Perceptual Gun";
  }

  @Override
  protected double aimInternal(titan_utils_Wave w, boolean painting) {
    double[] p = new double[] {
      Math.abs(w.targetVelocity) / 8.0,
      Math.min(w.targetDistance / w.bulletSpeed(), 90) / 90.0,
      Math.sin(w.targetRelativeHeading),
      (Math.cos(w.targetRelativeHeading) + 1) / 2,
      (w.targetAccel + robocode.Rules.DECELERATION)
          / (robocode.Rules.DECELERATION + robocode.Rules.ACCELERATION),
      Math.min(1.25, w.targetWallDistance) / 1.25,
      Math.min(1.15, w.targetRevWallDistance) / 1.15,
    };
    double[] wp = weightedPoint(p, WEIGHTS);

    int guessFactorIndex = TREE.findNearestNeighbors(wp, 1, _distFunc).getMax();
    double guessFactor;
    if (GUESSFACTORS % 2 == 0) {
      if (guessFactorIndex == (GUESSFACTORS / 2)
          || guessFactorIndex == (GUESSFACTORS / 2 - 1)) {
        guessFactor = 0;
      } else {
        guessFactor = ((double) (guessFactorIndex - (GUESSFACTORS / 2) + 1))
            / (GUESSFACTORS / 2);
      }
    } else {
      guessFactor = ((double) (guessFactorIndex - (GUESSFACTORS / 2)))
          / (GUESSFACTORS / 2);
    }

    return w.absBearing + (w.orbitDirection
        * guessFactor * w.preciseEscapeAngle(guessFactor >= 0));
  }

  public static titan_utils_genetic_DnaSequence knnSpaceSequence(int numPoints, int numAttr,
      int weightBits, int coordBits, int guessFactorBits) {
    titan_utils_genetic_DnaSequence treeSequence = new titan_utils_genetic_DnaSequence();
    for (int x = 0; x < numAttr; x++) {
      treeSequence.addGene(new titan_utils_genetic_DnaSequence.Gene("w" + (x + 1), weightBits));
    }
    for (int x = 0; x < numPoints; x++) {
      for (int y = 0; y < numAttr; y++) {
        treeSequence.addGene(
            new titan_utils_genetic_DnaSequence.Gene("p" + (x + 1) + "x" + (y + 1), coordBits));
      }
      treeSequence.addGene(new titan_utils_genetic_DnaSequence.Gene("r" + (x + 1), guessFactorBits));
    }

    return treeSequence;
  }

  public static titan_estruturas_dataStructures_trees_thirdGenKD_KdTree<Integer> kdTreeFromDnaString(
      titan_utils_genetic_DnaString dnaString, double[] weights) {

    int numAttr = weights.length;
    titan_estruturas_dataStructures_trees_thirdGenKD_KdTree<Integer> tree = new titan_estruturas_dataStructures_trees_thirdGenKD_KdTree<Integer>(numAttr);

    String bitString = dnaString.bitString();
    int numPoints = (bitString.length() - (numAttr * WEIGHT_BITS))
        / ((numAttr * COORDINATE_BITS) + GUESSFACTOR_BITS);
    int coordinateValues = maxValueFromBits(COORDINATE_BITS);

    for (int x = 0; x < numPoints; x++) {
      double[] p = new double[numAttr];
      for (int y = 0; y < numAttr; y++) {
        p[y] = (((double) dnaString.getNumber("p" + (x + 1) + "x" + (y + 1)))
            / coordinateValues) * weights[y];
      }
      tree.addPoint(p, (int) dnaString.getNumber("r" + (x + 1)));
    }

    return tree;
  }

  public static double[] weightsFromDnaString(
      titan_utils_genetic_DnaString dnaString, int numAttr) {
    double[] weights = new double[numAttr];
    for (int x = 0; x < numAttr; x++) {
      weights[x] = ((double) dnaString.getNumber("w" + (x + 1)))
          / maxValueFromBits(WEIGHT_BITS);
    }
    return weights;
  }

  private static int maxValueFromBits(int numBits) {
    return (int) Math.round(titan_utils_Matematica.power(2, numBits) - 1);
  }

  public static int power(int base, int exp) {
    int retVal = 1;
    for (int x = 0; x < exp; x++) {
      retVal *= base;
    }

    return retVal;
  }

  public static double[] weightedPoint(double[] p, double[] weights) {
    double[] wp = new double[p.length];
    for (int x = 0; x < wp.length; x++) {
      wp[x] = p[x] * weights[x];
    }

    return wp;
  }

  @Override
  public java.util.List<titan_utils_KnnView<T>> newDataViews() {
    return new java.util.ArrayList<titan_utils_KnnView<T>>();
  }
}

public static class titan_gun_AgrupamentoKnnGun extends titan_gun_DuelGun<titan_utils_TimestampedFiringAngle> {
  private static final String VIEW_NAME = "AgrupamentoKNN";
  private static final int FIRING_ANGLES = 59;
  private static final int MAX_K_SIZE = 225;
  private static final int K_DIVISOR = 9;

  private titan_gun_GunDataManager _gunDataManager;
  private java.util.Collection<titan_gfx_RoboGraphic> _renderables;
  private int _numFiringAngles;
  private int _maxK;
  private int _kDivisor;
  private titan_utils_DistanceFormula _formula;

  private static final double[] INITIAL_WEIGHTS = new double[]
      {0.94, 10.0, 1.73, 3.7, 3.31, 2.13, 5.51, 1.26, 1.57, 5.51};
  private static final double[] FINAL_WEIGHTS = new double[]
      {4.25, 5.43, 0.16, 4.25, 8.74, 3.39, 4.41, 8.03, 7.24, 4.41};
  private static final int[] FINAL_TIMES = new int[]
      {28920, 23040, 23100, 1740, 16680, 5580, 0, 11280, 21420, 1920};

  public titan_gun_AgrupamentoKnnGun(titan_gun_GunDataManager gunDataManager,
      java.util.Collection<titan_gfx_RoboGraphic> renderables) {
    this(gunDataManager, renderables, FIRING_ANGLES, MAX_K_SIZE, K_DIVISOR);
  }

  protected titan_gun_AgrupamentoKnnGun(titan_gun_GunDataManager gunDataManager,
      java.util.Collection<titan_gfx_RoboGraphic> renderables,
      int numFiringAngles, int maxK, int kDivisor) {
    super();
    _gunDataManager = gunDataManager;
    _renderables = renderables;
    _numFiringAngles = numFiringAngles;
    _maxK = maxK;
    _kDivisor = kDivisor;
    _formula = new titan_gun_formulas_AgrupamentoFormula();
  }

  @Override
  public String getLabel() {
    return "Main Gun";
  }

  @Override
  protected double aimInternal(titan_utils_Wave w, boolean painting) {
    titan_gun_GunEnemy enemyData = _gunDataManager.getEnemyData(w.botName);
    titan_utils_KnnView<titan_utils_TimestampedFiringAngle> view = enemyData.views.get(VIEW_NAME);
    int viewSize = view.size();
    if (enemyData == null || viewSize == 0) {
      return w.absBearing;
    }

    view.setWeights(getWeights(view.size()));
    java.util.List<titan_KdTree.Entry<titan_utils_TimestampedFiringAngle>> nearestNeighbors =
        view.nearestNeighbors(w, true);
    int numScans = nearestNeighbors.size();
    Double[] firingAngles = new Double[numScans];
    double[] weights = new double[numScans];
    for (int x = 0; x < numScans; x++) {
      double guessFactor = nearestNeighbors.get(x).value.guessFactor;
      firingAngles[x] = robocode.util.Utils.normalRelativeAngle(
          (guessFactor * w.orbitDirection
              * w.preciseEscapeAngle(guessFactor >= 0)));
      weights[x] = 1 / Math.sqrt(Math.max(1e-9, nearestNeighbors.get(x).distance));
    }

    double bandwidth = 2 * titan_utils_Matematica.botWidthAimAngle(
        w.sourceLocation.distance(w.targetLocation));
    Double bestAngle = null;
    double bestDensity = Double.NEGATIVE_INFINITY;
    titan_gfx_ColoredValueSet cvs = new titan_gfx_ColoredValueSet();
    double[] realAngles =
        titan_utils_Matematica.generateFiringAngles(_numFiringAngles, w.maxEscapeAngle());

    for (int x = 0; x < _numFiringAngles; x++) {
      double density = 0;
      for (int y = 0; y < numScans; y++) {
        if (firingAngles[y] == null) {
          continue;
        }
        double ux = (realAngles[x] - firingAngles[y]) / bandwidth;
        if (Math.abs(ux) < 1) {
          density += (1 - titan_utils_Matematica.cube(Math.abs(ux))) * weights[y];
        }
      }

      if (density > bestDensity) {
        bestAngle = realAngles[x];
        bestDensity = density;
      }

      if (painting) {
        cvs.addValue(density, w.absBearing + realAngles[x]);
      }
    }

    if (bestAngle == null) {
      return w.absBearing;
    }

    if (painting) {
      titan_gun_Mira.drawGunAngles(
          _renderables, w, cvs, w.absBearing + bestAngle, bandwidth);
    }

    return robocode.util.Utils.normalAbsoluteAngle(w.absBearing + bestAngle);
  }

  private double[] getWeights(int viewSize) {
    double[] newWeights = new double[INITIAL_WEIGHTS.length];
    for (int x = 0; x < newWeights.length; x++) {
      newWeights[x] = INITIAL_WEIGHTS[x]
          + (FINAL_TIMES[x] == 0
              ? 1 : Math.min(1, ((double) (viewSize - 1)) / FINAL_TIMES[x]))
          * (FINAL_WEIGHTS[x] - INITIAL_WEIGHTS[x]);
    }
    return newWeights;
  }

  @Override
  public java.util.List<titan_utils_KnnView<titan_utils_TimestampedFiringAngle>> newDataViews() {
    java.util.List<titan_utils_KnnView<titan_utils_TimestampedFiringAngle>> views =
        new java.util.ArrayList<titan_utils_KnnView<titan_utils_TimestampedFiringAngle>>();
    titan_utils_KnnView<titan_utils_TimestampedFiringAngle> view =
        new titan_utils_KnnView<titan_utils_TimestampedFiringAngle>(_formula)
            .setK(_maxK)
            .setKDivisor(_kDivisor)
            .visitsOn()
            .virtualWavesOn()
            .setName(VIEW_NAME);
    views.add(view);
    return views;
  }
}

public static class titan_gun_VirtualGunsManager<T> implements titan_gun_GunDataManager.GunDataListener {
  private static final double TYPICAL_ANGULAR_BOT_WIDTH = 0.1;
  private static final double TYPICAL_ESCAPE_RANGE = 0.9;

  private java.util.List<titan_gun_DuelGun<T>> _guns;
  private java.util.Map<titan_gun_DuelGun<T>, java.util.Map<String, GunStats>> _gunRatings;
  private java.io.PrintStream _out;

  public titan_gun_VirtualGunsManager(java.io.OutputStream out) {
    _guns = new java.util.ArrayList<titan_gun_DuelGun<T>>();
    _gunRatings = new java.util.HashMap<titan_gun_DuelGun<T>, java.util.Map<String, GunStats>>();
    _out = new java.io.PrintStream(out);
  }

  public void addGun(titan_gun_DuelGun<T> gun) {
    _guns.add(gun);
    _gunRatings.put(gun, new java.util.HashMap<String, GunStats>());
  }

  public java.util.List<titan_gun_DuelGun<T>> getGuns() {
    return _guns;
  }

  public boolean contains(titan_gun_DuelGun<T> gun) {
    return _guns.contains(gun);
  }

  public double getRating(titan_gun_DuelGun<T> gun, String botName) {
    if (_guns.contains(gun) && _gunRatings.get(gun).containsKey(botName)) {
      return _gunRatings.get(gun).get(botName).gunRating();
    }
    return 0;
  }

  public double getFormattedRating(titan_gun_DuelGun<T> gun, String botName) {
    return titan_utils_Matematica.round(getRating(gun, botName) * 100, 2);
  }

  public int getShotsFired(titan_gun_DuelGun<T> gun, String botName) {
    if (_guns.contains(gun) && _gunRatings.get(gun).containsKey(botName)) {
      return _gunRatings.get(gun).get(botName).shotsFired;
    }
    return 0;
  }

  public void fireVirtualBullets(titan_utils_Wave w) {
    for (titan_gun_DuelGun<T> gun : _guns) {
      GunStats stats;
      if (_gunRatings.get(gun).containsKey(w.botName)) {
        stats = _gunRatings.get(gun).get(w.botName);
      } else {
        stats = new GunStats();
        _gunRatings.get(gun).put(w.botName, stats);
      }

      double firingAngle = gun.aim(w, false);
      stats.virtualBullets.put(w, new VirtualBullet(firingAngle));
    }
  }

  public void registerWaveBreak(titan_utils_Wave w, double hitAngle, double tolerance) {
    for (titan_gun_DuelGun<T> gun : _guns) {
      GunStats stats = _gunRatings.get(gun).get(w.botName);
      VirtualBullet vb = stats.virtualBullets.get(w);

      double angularBotWidth = tolerance * 2;
      double hitWeight = (TYPICAL_ANGULAR_BOT_WIDTH / angularBotWidth)
          * (w.escapeAngleRange() / TYPICAL_ESCAPE_RANGE);
      double ux = Math.abs(robocode.util.Utils.normalRelativeAngle(vb.firingAngle - hitAngle))
          / tolerance;
      stats.shotsHit += hitWeight * Math.pow(1.6, -ux);

      stats.shotsFired++;
      stats.virtualBullets.remove(w);
    }
  }

  public void initRound() {
    for (titan_gun_DuelGun<T> gun : _guns) {
      for (GunStats stats : _gunRatings.get(gun).values()) {
        stats.virtualBullets.clear();
      }
      gun.clearCache();
    }
  }

  public titan_gun_DuelGun<T> bestGun(String botName) {
    titan_gun_DuelGun<T> bestGun = null;
    double bestRating = 0;

    for (titan_gun_DuelGun<T> gun : _guns) {
      double rating = 0;
      if (_gunRatings.get(gun).containsKey(botName)) {
        rating = _gunRatings.get(gun).get(botName).gunRating();
      }

      if (bestGun == null || rating > bestRating) {
        bestGun = gun;
        bestRating = rating;
      }
    }

    return bestGun;
  }

  public void printGunRatings(String botName) {
    _out.println("Virtual Gun ratings for " + botName + ":");
    for (titan_gun_DuelGun<T> gun : _guns) {
      if (_gunRatings.get(gun).containsKey(botName)) {
        double rating = _gunRatings.get(gun).get(botName).gunRating();
        _out.println("  " + gun.getLabel() + ": "
            + titan_utils_Matematica.round(rating * 100, 2));
      } else {
        _out.println("WARNING (gun): Never logged any Virtual Guns info for "
            + gun.getLabel());
      }
    }
  }

  @Override
  public void on1v1FiringWaveBreak(titan_utils_Wave w, double hitAngle, double tolerance) {
    registerWaveBreak(w, hitAngle, tolerance);
  }

  @Override
  public void onMarkFiringWave(titan_utils_Wave w) {
    fireVirtualBullets(w);
  }

  java.util.Map<String, double[]> snapshotRatings(String name) {
    java.util.Map<String, double[]> result = new java.util.HashMap<String, double[]>();
    for (titan_gun_DuelGun<T> gun : _guns) {
      GunStats stats = _gunRatings.get(gun).get(name);
      if (stats != null && stats.shotsFired > 0 && Double.isFinite(stats.shotsHit) && stats.shotsHit >= 0) {
        int count = Math.min(128, stats.shotsFired);
        result.put(gun.getLabel(), new double[] {stats.shotsHit * count / stats.shotsFired, count});
      }
    }
    return result;
  }

  void restoreRatings(String name, java.util.Map<String, double[]> ratings) {
    for (titan_gun_DuelGun<T> gun : _guns) {
      double[] values = ratings.get(gun.getLabel());
      if (values == null) continue;
      GunStats stats = new GunStats(); stats.shotsHit = values[0]; stats.shotsFired = (int) values[1];
      _gunRatings.get(gun).put(name, stats);
    }
  }

  private static class GunStats {
    public int shotsFired;
    public double shotsHit;
    public java.util.Map<titan_utils_Wave, VirtualBullet> virtualBullets;

    public GunStats() {
      shotsFired = 0;
      shotsHit = 0;
      virtualBullets = new java.util.HashMap<titan_utils_Wave, VirtualBullet>();
    }

    public double gunRating() {
      return (shotsFired == 0) ? 0 : (shotsHit / shotsFired);
    }
  }

  private static class VirtualBullet {
    public final double firingAngle;

    public VirtualBullet(double firingAngle) {
      this.firingAngle = firingAngle;
    }
  }
}

public static class titan_gun_formulas_AntiSurferFormula extends titan_utils_DistanceFormula {
  public titan_gun_formulas_AntiSurferFormula() {
    weights = new double[]{3, 4, 3, 2, 2, 4, 2, 3, 1};
  }

  public double[] dataPointFromWave(titan_utils_Wave w, boolean aiming) {
    return new double[]{
      (Math.min(91, w.targetDistance / w.bulletSpeed()) / 91),
      (((w.targetVelocitySign * w.targetVelocity) + 0.1) / 8.1),
      (Math.sin(w.targetRelativeHeading)),
      ((Math.cos(w.targetRelativeHeading) + 1) / 2),
      (((w.targetAccel / (w.targetAccel < 0
          ? robocode.Rules.DECELERATION : robocode.Rules.ACCELERATION)) + 1) / 2),
      (Math.min(1.0, w.targetWallDistance)),
      (Math.min(1.0, w.targetRevWallDistance)),
      (Math.min(1.0, ((double) w.targetVchangeTime)
          / (w.targetDistance/w.bulletSpeed())) / 1.0),
      (aiming ? 0 : w.virtuality())
    };
  }
}

public static class titan_gun_formulas_MainGunFormula extends titan_utils_DistanceFormula {
  protected int _enemiesTotal;

  public titan_gun_formulas_MainGunFormula(int enemiesTotal) {
    _enemiesTotal = enemiesTotal;
    weights = new double[]{3, 4, 3, 2, 2, 4, 2, 3, 2, 2};
  }

  public double[] dataPointFromWave(titan_utils_Wave w, boolean aiming) {
    return new double[]{
      (Math.min(91, w.targetDistance / w.bulletSpeed()) / 91),
      (((w.targetVelocitySign * w.targetVelocity) + 0.1) / 8.1),
      (Math.sin(w.targetRelativeHeading)),
      ((Math.cos(w.targetRelativeHeading) + 1) / 2),
      (((w.targetAccel / (w.targetAccel < 0
          ? robocode.Rules.DECELERATION : robocode.Rules.ACCELERATION)) + 1) / 2),
      (Math.min(1.0, w.targetWallDistance)),
      (Math.min(1.0, w.targetRevWallDistance)),
      (Math.min(1.0, ((double) w.targetVchangeTime)
          / (w.targetDistance / w.bulletSpeed())) / 1.0),
      (aiming ? 0 : w.virtuality()),
      (Math.sqrt(((double) (w.enemiesAlive - 1))
          / (Math.max(_enemiesTotal - 1,1))))
    };
  }
}

public static class titan_gun_formulas_MeleeFormula extends titan_utils_DistanceFormula {
  public titan_gun_formulas_MeleeFormula() {
    weights = new double[]{4, 6, 2, 4, 3, 1, 1};
  }

  public double[] dataPointFromWave(titan_utils_Wave w, boolean aiming) {
    return new double[]{
      (Math.min(91, w.targetDistance / w.bulletSpeed()) / 91),
      (((w.targetVelocitySign * w.targetVelocity) + 0.1) / 8.1),
      ((Math.cos(w.targetRelativeHeading) + 1) / 2),
      (Math.min(1.0, w.targetWallDistance)),
      (Math.min(1.0, w.targetRevWallDistance)),
      (w.targetDl8t / 64),
      (w.targetDl20t / 160)
    };
  }
}

public static class titan_gun_formulas_AgrupamentoFormula extends titan_utils_DistanceFormula {
  public titan_gun_formulas_AgrupamentoFormula() {
    weights = new double[]
        {0.34, 10.0, 3.13, 2.58, 8.38, 7.91, 8.67, 5.12, 2.34, 0.38};
  }

  public double[] dataPointFromWave(titan_utils_Wave w, boolean aiming) {
    return new double[] {
      (Math.min(3, w.bulletPower()) / 3),
      (Math.min(91, w.targetDistance / w.bulletSpeed()) / 91),
      (((w.targetVelocitySign * w.targetVelocity) + 0.1) / 8.1),
      (Math.sin(w.targetRelativeHeading)),
      ((Math.cos(w.targetRelativeHeading) + 1) / 2),
      ((w.targetAccel + robocode.Rules.DECELERATION)
          / (robocode.Rules.DECELERATION + robocode.Rules.ACCELERATION)),
      (Math.min(1.25, w.targetWallDistance)),
      (Math.min(1.15, w.targetRevWallDistance)),
      (Math.min(1.0, ((double) w.targetVchangeTime)
          / (w.targetDistance/w.bulletSpeed())) / 1.0),
      (aiming ? 0 : w.virtuality())
    };
  }
}

public static class titan_move_Movimento implements titan_gfx_RoboPainter, titan_gun_FireListener {
  private static final boolean ENABLE_DEBUGGING_GRAPHICS = true;
  private static final int WAVES_TO_SURF = 2;

  private titanprime_v4_41 _robot;
  private titan_move_MoveDataManager _moveDataManager;
  private titan_move_MeleeMover _meleeMover;
  private titan_move_SurfMover _surfMover;

  private java.util.Collection<titan_gfx_RoboGraphic> _renderables;
  private boolean _painting;
  private boolean _robocodePainting;

  public titan_move_Movimento(titanprime_v4_41 robot, java.io.OutputStream out) {
    this(robot, new titan_utils_BattleField(robot.getBattleFieldWidth(),
                                robot.getBattleFieldHeight()),
         new java.util.ArrayList<titan_gfx_RoboGraphic>(), out);
  }

  titan_move_Movimento(titanprime_v4_41 robot, titan_utils_BattleField battleField,
      java.util.Collection<titan_gfx_RoboGraphic> renderables, java.io.OutputStream out) {
    this(robot, battleField, new titan_utils_MovementPredictor(battleField), renderables,
        out);
  }

  titan_move_Movimento(titanprime_v4_41 robot, titan_utils_BattleField battleField,
      titan_utils_MovementPredictor predictor, java.util.Collection<titan_gfx_RoboGraphic> renderables,
      java.io.OutputStream out) {
    this(robot,
        new titan_move_MoveDataManager(robot.getOthers(), battleField, predictor,
            renderables, out),
        new titan_move_MeleeMover(robot, battleField, renderables),
        new titan_move_SurfMover(robot, battleField, renderables, out),
        battleField, renderables, out);
  }

  public titan_move_Movimento(titanprime_v4_41 robot, titan_move_MoveDataManager moveDataManager,
      titan_move_MeleeMover meleeMover, titan_move_SurfMover surfMover, titan_utils_BattleField battleField,
      java.util.Collection<titan_gfx_RoboGraphic> renderables, java.io.OutputStream out) {
    _robot = robot;
    _moveDataManager = moveDataManager;
    _meleeMover = meleeMover;
    _surfMover = surfMover;
    _renderables = renderables;

    _painting = false;
    _robocodePainting = false;
  }

  public void initRound(titanprime_v4_41 robot) {
    _robot = robot;
    _moveDataManager.initRound();
    _meleeMover.initRound(robot, myLocation());
    _surfMover.initRound(robot);
    _renderables.clear();
  }

  public void execute() {
    _moveDataManager.execute(_robot.getRoundNum(), _robot.getTime(),
        myLocation(), _robot.getHeadingRadians(), _robot.getVelocity());
    move();
  }

  private void move() {
    if (is1v1()) {
      _surfMover.move(_moveDataManager.myStateLog().getState(_robot.getTime()),
          _moveDataManager.duelEnemy(), WAVES_TO_SURF, paintStatus());
    } else {
      _meleeMover.move(myLocation(), _moveDataManager.getAllEnemyData(),
          _moveDataManager.getClosestLivingBot(myLocation()), paintStatus());
    }
  }

  public void onScannedRobot(robocode.ScannedRobotEvent e) {
    java.awt.geom.Point2D.Double myLocation = myLocation();
    String botName = e.getName();
    double absBearing = robocode.util.Utils.normalAbsoluteAngle(
        e.getBearingRadians() + _robot.getHeadingRadians());
    java.awt.geom.Point2D.Double enemyLocation =
        titan_utils_Matematica.project(myLocation, absBearing, e.getDistance());

    double previousEnemyEnergy;
    if (_moveDataManager.hasEnemy(botName)) {
      previousEnemyEnergy = _moveDataManager.getEnemyData(botName).energy;
      _moveDataManager.updateEnemy(
          e, enemyLocation, absBearing, _robot.getRoundNum(), is1v1());
    } else {
      previousEnemyEnergy = e.getEnergy();
      _moveDataManager.newEnemy(
          e, enemyLocation, absBearing, _robot.getRoundNum(), is1v1());
    }

    if (is1v1() && _moveDataManager.duelEnemy() != null) {
      _moveDataManager.updateEnemyWaves(myLocation(), previousEnemyEnergy,
          botName, _robot.getRoundNum(), _robot.getTime(), _robot.getEnergy(),
          _robot.getHeadingRadians(), _robot.getVelocity(), WAVES_TO_SURF,
          _robot instanceof titanprime_v4_41 ? ((titanprime_v4_41) _robot).observedPassiveDrain : 0);
    }
  }

  public void onRobotDeath(robocode.RobotDeathEvent e) {
    _moveDataManager.onRobotDeath(e);
  }

  public void onHitByBullet(robocode.HitByBulletEvent e) {
    _moveDataManager.onHitByBullet(
        e, _robot.getRoundNum(), _robot.getTime(), paintStatus());
  }

  public void onBulletHitBullet(robocode.BulletHitBulletEvent e) {
    _moveDataManager.onBulletHitBullet(e, _robot.getRoundNum(),
        _robot.getTime(), paintStatus());
  }

  public void onBulletHit(robocode.BulletHitEvent e) {
    _moveDataManager.onBulletHit(e);
  }

  public void onBulletMissed(robocode.BulletMissedEvent e) {
    if (_moveDataManager.retireFiredBullet(e.getBullet(), e.getTime()))
      { _moveDataManager.retiredMisses++; _moveDataManager.refreshBulletShadows(); }
  }

  public void onWin(robocode.WinEvent e) {
    roundOver();
  }

  public void onDeath(robocode.DeathEvent e) {
    roundOver();
  }

  void roundOver() {
    titan_move_MoveEnemy duelEnemy = _moveDataManager.duelEnemy();
    if (is1v1() && duelEnemy != null) {
      _surfMover.roundOver(duelEnemy);
    }
  }

  public void onPaint(java.awt.Graphics2D g) {
    if (paintStatus()) {
      for (titan_gfx_RoboGraphic renderable : _renderables) {
        renderable.render(g);
      }
      _renderables.clear();
    }
  }

  private java.awt.geom.Point2D.Double myLocation() {
    return new java.awt.geom.Point2D.Double(_robot.getX(), _robot.getY());
  }

  private boolean is1v1() {
    return (_robot.getOthers() <= 1);
  }

  @Override
  public void bulletFired(FiredBullet bullet) {
    _moveDataManager.addFiredBullet(bullet);
  }

  public void paintOn() {
    _painting = ENABLE_DEBUGGING_GRAPHICS;
  }

  public void paintOff() {
    _renderables.clear();
    _painting = false;
  }

  public void robocodePaintOn() {
    _robocodePainting = true;
  }

  public void robocodePaintOff() {
    _renderables.clear();
    _robocodePainting = false;
  }

  public String paintLabel() {
    return "Movement";
  }

  public boolean paintStatus() {
    return (_painting && _robocodePainting);
  }

  titanprime_v4_41 getRobot() {
    return _robot;
  }

  java.util.Collection<titan_gfx_RoboGraphic> getRenderables() {
    return _renderables;
  }
}

public static class titan_move_MeleeMover {
  private static final double CURRENT_DESTINATION_BIAS = 0.8;
  private static final int RECENT_LOCATIONS_TO_STORE = 50;
  private static final long NUM_SLICES_BOT = 64;

  private robocode.AdvancedRobot _robot;
  private titan_utils_BattleField _battleField;
  private java.util.Collection<titan_gfx_RoboGraphic> _renderables;
  private titan_utils_MovementPredictor _predictor;
  private java.util.LinkedList<OldLocation> _recentLocations;
  private Destination _currentDestination;

  public titan_move_MeleeMover(robocode.AdvancedRobot robot, titan_utils_BattleField battleField,
      java.util.Collection<titan_gfx_RoboGraphic> renderables) {
    _robot = robot;
    _battleField = battleField;
    _renderables = renderables;
    _predictor = new titan_utils_MovementPredictor(battleField);
    _recentLocations = new java.util.LinkedList<OldLocation>();
  }

  public void initRound(robocode.AdvancedRobot robot, java.awt.geom.Point2D.Double myLocation) {
    _robot = robot;
    _currentDestination =
        new Destination(myLocation, Double.POSITIVE_INFINITY, 0);
    _recentLocations.clear();
  }

  public void move(java.awt.geom.Point2D.Double myLocation, java.util.Collection<titan_move_MoveEnemy> enemies,
      titan_move_MoveEnemy closestEnemy, boolean painting) {
    if (enemies.isEmpty()) {
      return;
    }
    _robot.setMaxVelocity(8);

    updateRecentLocations(_robot.getTime(), myLocation);
    java.util.List<Destination> destinations =
        generateDestinations(myLocation, enemies, closestEnemy);
    Destination nextDestination =
        getNextDestination(myLocation, destinations);

    double goAngle =
        titan_utils_Matematica.absoluteBearing(myLocation, nextDestination.location);
    titan_utils_Matematica.setBackAsFront(_robot, goAngle);
    _currentDestination = nextDestination;

    if (painting) {
      destinations.add(_currentDestination);
      drawRisks(destinations);
    }
  }

  private void updateRecentLocations(
      long currentTime, java.awt.geom.Point2D.Double myLocation) {
    if (currentTime % 7 == 0) {
      for (int x = 0; x < 5; x++) {
        _recentLocations.addFirst(new OldLocation(
            titan_utils_Matematica.project(myLocation, Math.random() * Math.PI * 2,
                             5 + Math.random() * Math.random() * 200),
            currentTime));
      }
      while (_recentLocations.size() > RECENT_LOCATIONS_TO_STORE) {
        _recentLocations.removeLast();
      }
    }
  }

  private java.util.List<Destination> generateDestinations(java.awt.geom.Point2D.Double myLocation,
      java.util.Collection<titan_move_MoveEnemy> enemies, titan_move_MoveEnemy closestEnemy) {
    java.util.List<Destination> possibleDestinations = new java.util.ArrayList<Destination>();
    possibleDestinations.addAll(
        generatePointsAroundBot(myLocation, enemies, closestEnemy));

    if (myLocation.distance(_currentDestination.location)
            <= myLocation.distance(closestEnemy.lastScanState.location)) {
      double currentGoAngle =
          titan_utils_Matematica.absoluteBearing(myLocation, _currentDestination.location);
      double currentRisk = CURRENT_DESTINATION_BIAS
          * evaluateRisk(enemies, _currentDestination.location,
                         _currentDestination.goAngle);
      _currentDestination = new Destination(
          _currentDestination.location, currentRisk, currentGoAngle);
      possibleDestinations.add(_currentDestination);
    }

    return possibleDestinations;
  }

  private java.util.List<Destination> generatePointsAroundBot(
      java.awt.geom.Point2D.Double myLocation, java.util.Collection<titan_move_MoveEnemy> enemies,
      titan_move_MoveEnemy closestEnemy) {
    java.util.List<Destination> destinations = new java.util.ArrayList<Destination>();
    double distanceToClosestBot =
        myLocation.distance(closestEnemy.lastScanState.location);
    double movementStick =
        Math.min(100 + Math.random() * 100, distanceToClosestBot);

    double sliceSize = (2 * Math.PI) / NUM_SLICES_BOT;
    for (int x = 0; x < NUM_SLICES_BOT; x++) {
      double angle = x * sliceSize;
      java.awt.geom.Point2D.Double dest = _battleField.translateToField(
          titan_utils_Matematica.project(myLocation, angle, movementStick));
      destinations.add(
          new Destination(dest, evaluateRisk(enemies, dest, angle), angle));
    }

    return destinations;
  }

  private double evaluateRisk(java.util.Collection<titan_move_MoveEnemy> enemies,
      java.awt.geom.Point2D.Double destination, double goAngle) {
    double risk = 0;
    for (titan_move_MoveEnemy moveData : enemies) {
      if (moveData.alive) {
        double distanceSq =
            destination.distanceSq(moveData.lastScanState.location);
        risk += titan_utils_Matematica.limit(0.25, moveData.energy / Math.max(.1, _robot.getEnergy()), 4)
            * (1 + Math.abs(Math.cos(moveData.absBearing - goAngle)))
            * moveData.damageFactor
            / (Math.max(1, distanceSq) * (moveData.botsCloser(distanceSq * .8) + 1)
            );
      }
    }

    double randomRisk = 0;
    for (OldLocation oldLocation : _recentLocations) {
      randomRisk += 30.0 / Math.max(1, oldLocation.location.distanceSq(destination));
    }
    risk *= 1 + randomRisk;

    return risk;
  }

  private Destination getNextDestination(
      java.awt.geom.Point2D.Double myLocation, java.util.List<Destination> destinations) {
    Destination nextDestination;
    titan_utils_RobotState currentState = titan_utils_RobotState.newBuilder()
        .setLocation(myLocation)
        .setHeading(_robot.getHeadingRadians())
        .setVelocity(_robot.getVelocity())
        .setTime(_robot.getTime())
        .build();
    while (!destinations.isEmpty()) {
      nextDestination = safestDestination(myLocation, destinations);
      if (!destinations.remove(nextDestination)) break;
      if (!wouldHitWall(currentState, nextDestination)) return nextDestination;
    }

    _robot.setMaxVelocity(0);
    _robot.setAhead(0);
    return new Destination(_battleField.translateToField(myLocation), 0, _robot.getHeadingRadians());
  }

  private Destination safestDestination(
      java.awt.geom.Point2D.Double myLocation, java.util.List<Destination> possibleDestinations) {
    double lowestRisk = Double.POSITIVE_INFINITY;
    Destination safest = null;

    for (Destination destination : possibleDestinations) {
      if (destination.risk < lowestRisk) {
        lowestRisk = destination.risk;
        safest = destination;
      }
    }

    if (safest == null) {
      String error = "No safe destinations found, there must be a bug "
          + "in the risk evaluation.\n"
          + "_myLocation: (" + titan_utils_Matematica.round(myLocation.x, 1) + ", "
          + titan_utils_Matematica.round(myLocation.y, 1) + ")\n"
          + "myEnergy: " + _robot.getEnergy() + "\n"
          + "getOthers(): " + _robot.getOthers();
      titan_utils_ErrorLogger.getInstance().logError(error);

      safest = _currentDestination;
    }

    return safest;
  }

  private boolean wouldHitWall(
      titan_utils_RobotState currentState, Destination destination) {
    long ticksAhead = 5;
    for (int x = 0; x < ticksAhead; x++) {
      currentState = _predictor.nextLocation(currentState, 8.0,
          titan_utils_Matematica.absoluteBearing(currentState.location, destination.location),
          true);
      if (!_battleField.rectangle.contains(currentState.location)) {
        return true;
      }
    }
    return false;
  }

  private void drawRisks(java.util.List<Destination> destinations) {
    double lowestRisk = Double.POSITIVE_INFINITY;
    double highestRisk = Double.NEGATIVE_INFINITY;

    double[] risks = new double[destinations.size()];
    int x = 0;
    for (Destination destination : destinations) {
      risks[x++] = destination.risk;
      if (destination.risk < lowestRisk) {
        lowestRisk = destination.risk;
      }
      if (destination.risk > highestRisk) {
        highestRisk = destination.risk;
      }
    }

    double avg = titan_utils_Matematica.average(risks);
    double stDev = titan_utils_Matematica.standardDeviation(risks);
    for (Destination destination : destinations) {
      _renderables.add(titan_gfx_RoboGraphic.drawCircleFilled(destination.location,
          titan_gfx_RoboGraphic.riskColor(destination.risk - lowestRisk, avg - lowestRisk,
              stDev, true, 1), 2));
    }
  }

  public static class Destination {
    public final java.awt.geom.Point2D.Double location;
    public final double risk;
    public final double goAngle;

    public Destination(java.awt.geom.Point2D.Double location, double risk, double goAngle) {
      this.location = location;
      this.risk = risk;
      this.goAngle = goAngle;
    }
  }

  public class OldLocation {
    public java.awt.geom.Point2D.Double location;
    public long time;

    public OldLocation(java.awt.geom.Point2D.Double l, long t) {
      location = l;
      time = t;
    }
  }
}

public static class titan_move_MoveDataManager extends titan_utils_EnemyDataManager<titan_move_MoveEnemy> {
  private static final double NON_ZERO_VELOCITY_THRESHOLD = 0.1;
  private static final double DIRECTION_CHANGE_THRESHOLD = Math.PI / 2;
  private static final long EARLIEST_FIRE_TIME = 30L;

  private double _previousHeading;
  private double _currentHeading;
  private long _timeSinceReverseDirection;
  private long _timeSinceVelocityChange;
  private double _lastVelocity;
  private double _previousVelocity;
  private double _lastNonZeroVelocity;
  private titan_utils_RobotStateLog _myStateLog;
  private java.util.List<titan_gun_FireListener.FiredBullet> _firedBullets;
  private int retiredHits, retiredMisses, retiredCollisions;

  protected titan_move_MoveDataManager(int enemiesTotal, titan_utils_BattleField battleField,
      titan_utils_MovementPredictor predictor, java.util.Collection<titan_gfx_RoboGraphic> renderables,
      java.io.OutputStream out) {
    super(enemiesTotal, battleField, predictor, renderables, out);
    _previousVelocity = _lastVelocity = _lastNonZeroVelocity = 0;
    _myStateLog = new titan_utils_RobotStateLog();
    _firedBullets = new java.util.ArrayList<titan_gun_FireListener.FiredBullet>();
  }

  public void execute(int round, long time, java.awt.geom.Point2D.Double myLocation,
      double heading, double velocity) {
    _myStateLog.addState(titan_utils_RobotState.newBuilder()
        .setLocation(myLocation)
        .setHeading(heading)
        .setVelocity(velocity)
        .setTime(time)
        .build());
    _previousHeading = _currentHeading;
    if (Math.abs(velocity) > NON_ZERO_VELOCITY_THRESHOLD) {
      _lastNonZeroVelocity = velocity;
    }
    _previousVelocity = velocity;
    _currentHeading = robocode.util.Utils.normalAbsoluteAngle(
        heading + (_lastNonZeroVelocity < 0 ? Math.PI : 0));
    updateBotDistances(myLocation);
    updateDamageFactors();
    removeOldFiredBullets(time);
    if (_duelEnemy != null) {
      _duelEnemy.execute1v1(round, time, myLocation);
    } else {
      updateTimers(velocity);
    }
  }

  private void removeOldFiredBullets(long currentTime) {
    java.util.Iterator<titan_gun_FireListener.FiredBullet> bulletIterator = _firedBullets.iterator();
    while (bulletIterator.hasNext()) {
      titan_gun_FireListener.FiredBullet firedBullet = bulletIterator.next();
      if (!_battleField.rectangle.contains(firedBullet.position(currentTime))) {
        bulletIterator.remove();
      }
    }
  }

  private void updateDamageFactors() {
    if (isMeleeBattle()) {
      for (titan_move_MoveEnemy moveData : getAllEnemyData()) {
        moveData.updateDamageFactor();
      }
    }
  }

  @Override
  public void initRound() {
    super.initRound();
    _currentHeading = _previousHeading = 0;
    _timeSinceReverseDirection = 0;
    _timeSinceVelocityChange = 0;
    _previousVelocity = _lastVelocity = _lastNonZeroVelocity = 0;
    _myStateLog.clear();
    _firedBullets.clear();
    retiredHits = retiredMisses = retiredCollisions = 0;
  }

  public titan_move_MoveEnemy newEnemy(robocode.ScannedRobotEvent e, java.awt.geom.Point2D.Double enemyLocation,
      double absBearing, int currentRound, boolean is1v1) {
    String botName = e.getName();
    titan_move_MoveEnemy moveData = new titan_move_MoveEnemy(botName, e.getDistance(), e.getEnergy(),
        enemyLocation, e.getHeadingRadians(), e.getVelocity(), absBearing,
        currentRound, e.getTime(), _renderables, _battleField, _predictor,
        _out);
    saveEnemy(botName, moveData);
    if (is1v1) {
      _duelEnemy = moveData;
    }
    return moveData;
  }

  void saveEnemy(String botName, titan_move_MoveEnemy moveData) {
    _enemies.put(botName, moveData);
  }

  public titan_move_MoveEnemy updateEnemy(robocode.ScannedRobotEvent e,
      java.awt.geom.Point2D.Double enemyLocation, double absBearing, int currentRound,
      boolean is1v1) {
    titan_move_MoveEnemy moveData = getEnemyData(e.getName());
    moveData.wallHitDamage = getWallHitDamage(e, enemyLocation, moveData);
    moveData.setRobotState(titan_utils_RobotState.newBuilder()
        .setLocation(enemyLocation)
        .setHeading(e.getHeadingRadians())
        .setVelocity(e.getVelocity())
        .setTime(e.getTime())
        .build());
    moveData.energy = e.getEnergy();
    moveData.distance = e.getDistance();
    moveData.absBearing = absBearing;
    moveData.lastScanRound = currentRound;
    if (is1v1) {
      _duelEnemy = moveData;
    }
    return moveData;
  }

  private titan_move_MoveEnemy.WallHitDamage getWallHitDamage(
      robocode.ScannedRobotEvent e, java.awt.geom.Point2D.Double enemyLocation, titan_move_MoveEnemy moveData) {
    titan_utils_RobotState lastState = moveData.lastScanState;
    if (!moveData.isRobot && e.getTime() - lastState.time == 1
        && Math.abs(lastState.velocity - e.getVelocity()) > 2
        && Math.abs(e.getVelocity()) < 0.0001
        && titan_utils_Matematica.distanceToWall(enemyLocation, _battleField) < 0.0001) {
      if (Math.abs(e.getEnergy() - moveData.energy) < 0.0001) {
        moveData.isRobot = true;
      } else {
        double maxSpeed =
            Math.min(8.0, Math.abs(lastState.velocity) + robocode.Rules.ACCELERATION);
        double minSpeed = Math.abs(lastState.velocity) - robocode.Rules.DECELERATION;
        return new titan_move_MoveEnemy.WallHitDamage(
            robocode.Rules.getWallHitDamage(minSpeed), robocode.Rules.getWallHitDamage(maxSpeed));
      }
    }
    return new titan_move_MoveEnemy.WallHitDamage(0, 0);
  }

  public void onHitByBullet(robocode.HitByBulletEvent e, int currentRound,
      long currentTime, boolean painting) {
    String botName = e.getName();
    if (hasEnemy(botName)) {
      titan_move_MoveEnemy moveData = getEnemyData(botName);
      moveData.lastTimeHit = currentTime;
      moveData.damageTaken += robocode.Rules.getBulletDamage(e.getBullet().getPower());
      moveData.totalBulletPower += e.getBullet().getPower();
      moveData.totalTimesHit++;
      moveData.energy += robocode.Rules.getBulletHitBonus(e.getBullet().getPower());
      titan_utils_Wave hitWave = moveData.processBullet(
          e.getBullet(), currentRound, currentTime, painting);
      if (hitWave != null) {
        hitWave.hitByBullet = true;
      }
    } else {
      _out.println(warning() + "A bot shot me that I never knew existed! ("
          + botName + ")");
    }

    if (_duelEnemy != null) {
      _duelEnemy.clearNeighborCache();
    }
  }

  public void onBulletHitBullet(robocode.BulletHitBulletEvent e, int currentRound,
      long currentTime, boolean painting) {
    String botName = e.getHitBullet().getName();
    if (hasEnemy(botName)) {
      titan_move_MoveEnemy moveData = getEnemyData(botName);
      titan_utils_Wave hitWave = moveData.processBullet(
          e.getHitBullet(), currentRound, currentTime, painting);
      if (hitWave != null) {
        hitWave.bulletHitBullet = true;
      }
    } else if (!botName.equals(e.getBullet().getName())) {
      _out.println(warning() + "One of my bullets hit a bullet from a "
          + "bot that I never knew existed! (" + botName + ")");
    }

    removeFiredBullet(e);
    if (_duelEnemy != null) {
      _duelEnemy.resetBulletShadows(_firedBullets);
      _duelEnemy.clearNeighborCache();
    }
  }

  public void onBulletHit(robocode.BulletHitEvent e) {
    if (retireFiredBullet(e.getBullet(), e.getTime())) { retiredHits++; refreshBulletShadows(); }
    String botName = e.getName();
    try {
      titan_move_MoveEnemy moveData = getEnemyData(botName);
      double bulletDamage = robocode.Rules.getBulletDamage(e.getBullet().getPower());
      moveData.energy -= bulletDamage;
      moveData.damageGiven += bulletDamage;
    } catch (NullPointerException npe) {
      _out.println(warning() + "One of my bullets hit a bot that I never "
          + "knew existed! (" + botName + ")");
    }
  }

  public void updateEnemyWaves(java.awt.geom.Point2D.Double myLocation,
      double previousEnemyEnergy, String botName, int currentRound,
      long currentTime, double myEnergy, double myHeading, double myVelocity,
      int wavesToSurf, double passiveDrain) {
    titan_utils_RobotState myRobotState = titan_utils_RobotState.newBuilder()
        .setLocation(myLocation)
        .setHeading(myHeading)
        .setVelocity(myVelocity)
        .setTime(currentTime)
        .build();
    _myStateLog.addState(myRobotState);
    titan_move_MoveEnemy moveData = getEnemyData(botName);
    boolean detectedEnemyBullet = false;
    double energyDrop =
        previousEnemyEnergy - moveData.energy - moveData.wallHitDamage.max - passiveDrain;
    if (energyDrop > 0.0999 && energyDrop < 3.0001
        && moveData.getGunHeat(currentTime) < 0.0001) {
      detectedEnemyBullet = true;
    }

    updateTimers(myVelocity);
    int velocitySign = titan_utils_Matematica.nonZeroSign(
        Math.abs(myVelocity) > NON_ZERO_VELOCITY_THRESHOLD
            ? myVelocity : _lastNonZeroVelocity);
    double accel =
        titan_utils_Matematica.limit(-robocode.Rules.DECELERATION,
                       titan_utils_Matematica.accel(myVelocity, _previousVelocity),
                       robocode.Rules.ACCELERATION);
    double dl8t =
        _myStateLog.getDisplacementDistance(myLocation, currentTime, 8);
    double dl20t =
        _myStateLog.getDisplacementDistance(myLocation, currentTime, 20);
    double dl40t =
        _myStateLog.getDisplacementDistance(myLocation, currentTime, 40);
    double guessedPower = moveData.guessBulletPower(myEnergy);
    long fireTime = currentTime + 1;
    java.awt.geom.Point2D.Double enemyNextLocation = _battleField.translateToField(
        _predictor.nextLocation(moveData.lastScanState));

    moveData.newMoveWave(enemyNextLocation, myLocation,
        titan_utils_Matematica.absoluteBearing(moveData.lastScanState.location, myLocation),
        currentRound, fireTime, guessedPower, myEnergy, myHeading, myVelocity,
        velocitySign, accel, dl8t, dl20t, dl40t, _timeSinceReverseDirection,
        _timeSinceVelocityChange);
    moveData.updateImaginaryWave(
        currentTime, myRobotState, wavesToSurf, _predictor);
    if (detectedEnemyBullet && currentTime > EARLIEST_FIRE_TIME) {
      moveData.updateFiringWave(
          currentTime, energyDrop, _myStateLog, _firedBullets);
    }
  }

  void updateTimers(double velocity) {
    if (Math.abs(robocode.util.Utils.normalRelativeAngle(_currentHeading - _previousHeading))
            > DIRECTION_CHANGE_THRESHOLD) {
      _timeSinceReverseDirection = 0;
    } else {
      _timeSinceReverseDirection++;
    }

    double newVelocity = velocity;
    if (Math.abs(newVelocity - _lastVelocity) > 0.5) {
      _timeSinceVelocityChange = 0;
    } else {
      _timeSinceVelocityChange++;
    }
    _lastVelocity = newVelocity;
  }

  public titan_utils_RobotStateLog myStateLog() {
    return _myStateLog;
  }

  public void addFiredBullet(titan_gun_FireListener.FiredBullet bullet) {
    _firedBullets.add(bullet);
    if (_duelEnemy != null) {
      _duelEnemy.setShadows(bullet);
    }
  }

  private void removeFiredBullet(robocode.BulletHitBulletEvent e) {
    if (retireFiredBullet(e.getBullet(), e.getTime())) retiredCollisions++;
  }

  boolean retireFiredBullet(robocode.Bullet bullet, long time) {
    for (titan_gun_FireListener.FiredBullet fired : _firedBullets) {
      if (fired.realBullet != null && fired.realBullet.equals(bullet)
          && fired.realBullet.getName().equals(bullet.getName())) {
        fired.deathTime = Math.min(fired.deathTime, time);
        return true;
      }
    }
    return false;
  }

  void refreshBulletShadows() {
    if (_duelEnemy != null) {
      _duelEnemy.resetBulletShadows(_firedBullets);
      _duelEnemy.clearNeighborCache();
    }
  }

  @Override
  protected String getLabel() {
    return "move";
  }

  titan_utils_RobotStateLog getMyStateLog() {
    return _myStateLog;
  }

  java.util.List<titan_gun_FireListener.FiredBullet> getFiredBullets() {
    return _firedBullets;
  }
}

public static class titan_move_MoveEnemy extends titan_utils_Enemy<titan_utils_TimestampedGuessFactor> {
  private static final double RECENT_SCANS_HIT_THRESHOLD = 2.5;
  private static final double LIGHT_FLATTENER_HIT_THRESHOLD = 3.0;
  private static final double FLATTENER_HIT_THRESHOLD = 5.9;
  private static final double DECAY_RATE = 1.8;
  private static final double BOT_WIDTH = 36;
  private static final double TYPICAL_ANGULAR_BOT_WIDTH = 0.1;
  private static final double TYPICAL_ESCAPE_RANGE = 0.98;

  public double damageTaken;
  public double lastBulletPower;
  public long lastBulletFireTime;
  public double totalBulletPower;
  public long totalTimesHit;
  public double totalDistance;
  public long lastTimeHit;
  public long lastTimeClosest;
  public boolean avoidBeingTargeted;
  public boolean stayPerpendicular;
  public double damageFactor;

  public int raw1v1ShotsFired;
  public int raw1v1ShotsHit;
  public double weighted1v1ShotsHit;
  final int[] contextShots = new int[3];
  final int[] contextHits = new int[3];
  final double[] contextWeightedHits = new double[3];
  int contextQueries, confidenceRescues;

  final java.util.LinkedList<float[]> memoryHits = new java.util.LinkedList<float[]>();
  final java.util.LinkedList<float[]> memoryVisits = new java.util.LinkedList<float[]>();
  public int raw1v1ShotsFiredThisRound;
  public int raw1v1ShotsHitThisRound;
  public double weighted1v1ShotsHitThisRound;
  public WallHitDamage wallHitDamage;
  public boolean isRobot;
  public titan_KdTree.WeightedSqrEuclid<Double> powerTree;

  private titan_utils_Wave _imaginaryWave;
  private int _imaginaryWaveIndex;

  private java.util.Collection<titan_gfx_RoboGraphic> _renderables;
  private java.io.PrintStream _out;

  public titan_move_MoveEnemy(String botName, double distance, double energy,
      java.awt.geom.Point2D.Double location, double heading, double velocity,
      double absBearing, int round, long time,
      java.util.Collection<titan_gfx_RoboGraphic> renderables, titan_utils_BattleField battleField,
      titan_utils_MovementPredictor predictor, java.io.PrintStream out) {
    super(botName, location, distance, energy, heading, velocity, absBearing,
        round, time, battleField, predictor, new titan_utils_WaveManager());
    _renderables = renderables;
    _out = out;
    damageTaken = 0;
    lastBulletPower = 0;
    totalBulletPower = 0;
    totalTimesHit = 0;
    totalDistance = 500;
    raw1v1ShotsFired = 0;
    weighted1v1ShotsHit = 0;
    avoidBeingTargeted = false;
    stayPerpendicular = false;
    wallHitDamage = new WallHitDamage(0, 0);
    isRobot = false;

    initSurfViews();
    powerTree =
        new titan_KdTree.WeightedSqrEuclid<Double>(BULLET_POWER_WEIGHTS.length, null);
    powerTree.setWeights(BULLET_POWER_WEIGHTS);
    _imaginaryWave = null;
    restoreMoveMemory(this);
  }

  @Override
  public void initRound() {
    super.initRound();
    lastTimeHit = Long.MIN_VALUE;
    lastTimeClosest = Long.MIN_VALUE;
    lastBulletFireTime = 0;
    raw1v1ShotsFiredThisRound = 0;
    contextQueries = 0; confidenceRescues = 0;
    raw1v1ShotsHitThisRound = 0;
    weighted1v1ShotsHitThisRound = 0;
    lastBulletPower = 0;
    _imaginaryWave = null;
    clearNeighborCache();
  }

  public void initSurfViews() {
    titan_utils_KnnView<titan_utils_TimestampedGuessFactor> simple =
        new titan_utils_KnnView<titan_utils_TimestampedGuessFactor>(new titan_move_formulas_SimpleFormula())
            .setWeight(3)
            .setK(25)
            .setKDivisor(5)
            .bulletHitsOn();

    titan_utils_KnnView<titan_utils_TimestampedGuessFactor> normal =
        new titan_utils_KnnView<titan_utils_TimestampedGuessFactor>(new titan_move_formulas_NormalFormula())
            .setWeight(40)
            .setK(20)
            .setKDivisor(5)
            .setHitThreshold(3.0)
            .bulletHitsOn();

    titan_utils_KnnView<titan_utils_TimestampedGuessFactor> recent =
        new titan_utils_KnnView<titan_utils_TimestampedGuessFactor>(new titan_move_formulas_NormalFormula())
            .setWeight(100)
            .setK(1)
            .setMaxDataPoints(1)
            .setHitThreshold(RECENT_SCANS_HIT_THRESHOLD)
            .bulletHitsOn();
    titan_utils_KnnView<titan_utils_TimestampedGuessFactor> recent2 =
        new titan_utils_KnnView<titan_utils_TimestampedGuessFactor>(new titan_move_formulas_NormalFormula())
            .setWeight(100)
            .setK(1)
            .setMaxDataPoints(5)
            .setHitThreshold(RECENT_SCANS_HIT_THRESHOLD)
            .bulletHitsOn();
    titan_utils_KnnView<titan_utils_TimestampedGuessFactor> recent3 =
        new titan_utils_KnnView<titan_utils_TimestampedGuessFactor>(new titan_move_formulas_NormalFormula())
            .setWeight(100)
            .setK(1)
            .setHitThreshold(RECENT_SCANS_HIT_THRESHOLD)
            .setDecayRate(DECAY_RATE)
            .bulletHitsOn();
    titan_utils_KnnView<titan_utils_TimestampedGuessFactor> recent4 =
        new titan_utils_KnnView<titan_utils_TimestampedGuessFactor>(new titan_move_formulas_NormalFormula())
            .setWeight(100)
            .setK(7)
            .setKDivisor(4)
            .setHitThreshold(RECENT_SCANS_HIT_THRESHOLD)
            .setDecayRate(DECAY_RATE)
            .bulletHitsOn();
    titan_utils_KnnView<titan_utils_TimestampedGuessFactor> recent5 =
        new titan_utils_KnnView<titan_utils_TimestampedGuessFactor>(new titan_move_formulas_NormalFormula())
            .setWeight(100)
            .setK(35)
            .setKDivisor(3)
            .setHitThreshold(RECENT_SCANS_HIT_THRESHOLD)
            .setDecayRate(DECAY_RATE)
            .bulletHitsOn();
    titan_utils_KnnView<titan_utils_TimestampedGuessFactor> recent6 =
        new titan_utils_KnnView<titan_utils_TimestampedGuessFactor>(new titan_move_formulas_NormalFormula())
            .setWeight(100)
            .setK(100)
            .setKDivisor(2)
            .setHitThreshold(RECENT_SCANS_HIT_THRESHOLD)
            .setDecayRate(DECAY_RATE)
            .bulletHitsOn();

    titan_utils_KnnView<titan_utils_TimestampedGuessFactor> lightFlattener =
        new titan_utils_KnnView<titan_utils_TimestampedGuessFactor>(new titan_move_formulas_NormalFormula())
            .setWeight(10)
            .setK(50)
            .setMaxDataPoints(1000)
            .setKDivisor(5)
            .setPaddedHitThreshold(LIGHT_FLATTENER_HIT_THRESHOLD)
            .visitsOn();

    titan_utils_KnnView<titan_utils_TimestampedGuessFactor> flattener =
        new titan_utils_KnnView<titan_utils_TimestampedGuessFactor>(new titan_move_formulas_FlattenerFormula())
            .setWeight(50)
            .setK(25)
            .setMaxDataPoints(300)
            .setKDivisor(12)
            .setPaddedHitThreshold(FLATTENER_HIT_THRESHOLD)
            .visitsOn();
    titan_utils_KnnView<titan_utils_TimestampedGuessFactor> flattener2 =
        new titan_utils_KnnView<titan_utils_TimestampedGuessFactor>(new titan_move_formulas_FlattenerFormula())
            .setWeight(500)
            .setK(50)
            .setMaxDataPoints(2000)
            .setKDivisor(14)
            .setPaddedHitThreshold(FLATTENER_HIT_THRESHOLD)
            .setDecayRate(DECAY_RATE)
            .visitsOn();

    addView(simple);
    addView(normal);
    addView(recent);
    addView(recent2);
    addView(recent3);
    addView(recent4);
    addView(recent5);
    addView(recent6);
    addView(lightFlattener);
    addView(flattener);
    addView(flattener2);
  }

  public void execute1v1(
      int currentRound, long currentTime, java.awt.geom.Point2D.Double myLocation) {
    _waveManager.checkActiveWaves(currentTime, titan_utils_RobotState.newBuilder()
            .setLocation(myLocation).setTime(currentTime).build(),
        newWaveBreakListener(currentRound, currentTime));
  }

  public titan_utils_Wave processBullet(
      robocode.Bullet bullet, int currentRound, long currentTime, boolean painting) {
    java.awt.geom.Point2D.Double bulletLocation =
        new java.awt.geom.Point2D.Double(bullet.getX(), bullet.getY());
    String botName = bullet.getName();

    titan_utils_Wave hitWave = _waveManager.findClosestWave(bulletLocation, currentTime,
        titan_utils_Wave.FIRING_WAVE, botName, bullet.getPower());

    if (hitWave == null || !botName.equals(hitWave.botName)) {
      return null;
    }

    double hitGuessFactor = hitWave.guessFactor(bulletLocation);
    rememberWave(hitWave, hitGuessFactor, memoryHits);
    for (titan_utils_KnnView<titan_utils_TimestampedGuessFactor> view : views.values()) {
      if (view.logBulletHits) {
        view.logWave(hitWave, new titan_utils_TimestampedGuessFactor(
            currentRound, currentTime, hitGuessFactor));
      }
    }

    if (painting) {
      drawBulletHit(hitWave, hitGuessFactor, bulletLocation, currentTime);
    }

    return hitWave;
  }

  public double avgBulletPower() {
    if (totalBulletPower == 0) {
      return 3.0;
    }
    return totalBulletPower / totalTimesHit;
  }

  public double minDistanceSq() {
    double min = Double.POSITIVE_INFINITY;
    for (double distanceSq : _botDistancesSq.values()) {
      if (distanceSq < min) {
        min = distanceSq;
      }
    }
    return min;
  }

  public int botsCloser(double distanceSq) {
    int botsCloser = 0;
    for (double botDistanceSq : _botDistancesSq.values()) {
      if (botDistanceSq < distanceSq) {
        botsCloser++;
      }
    }
    return botsCloser;
  }

  public void clearNeighborCache() {
    for (titan_utils_KnnView<titan_utils_TimestampedGuessFactor> view : views.values()) {
      view.clearCache();
    }
  }

  public double getGunHeat(long time) {
    double gunHeat;
    if (time <= 30) {
      gunHeat = Math.max(0, 3.0 - (time * .1));
    } else {
      gunHeat = Math.max(0,
                         robocode.Rules.getGunHeat(lastBulletPower)
                             - ((time - lastBulletFireTime) * .1));
    }
    return titan_utils_Matematica.round(gunHeat, 6);
  }

  private static final double[] BULLET_POWER_WEIGHTS = new double[]{3, 5, 1};
  protected double[] bulletPowerDataPoint(
      double distance, double enemyEnergy, double myEnergy) {
    return new double[] {
      Math.min(distance, 800) / 800,
      Math.min(enemyEnergy, 125) / 125,
      Math.min(myEnergy, 125) / 125
    };
  }

  public void resetBulletShadows(final java.util.List<titan_gun_FireListener.FiredBullet> firedBullets) {
    _waveManager.forAllWaves(new titan_utils_WaveManager.AllWaveListener() {
      @Override
      public void onWave(titan_utils_Wave w) {
        if (w.firingWave) {
          w.rebuildBulletShadows();
        }
      }
    });
  }

  public void setShadows(final titan_gun_FireListener.FiredBullet bullet) {
    _waveManager.forAllWaves(new titan_utils_WaveManager.AllWaveListener() {
      @Override
      public void onWave(titan_utils_Wave w) {
        if (w.firingWave) {
          setShadows(w, bullet);
        }
      }
    });
  }

  public void setShadows(titan_utils_Wave w, titan_gun_FireListener.FiredBullet bullet) {
    long startTime = Math.max(w.fireTime, bullet.fireTime);
    if (!w.processedBulletHit() && startTime < bullet.deathTime
        && w.sourceLocation.distanceSq(bullet.position(startTime))
            > titan_utils_Matematica.square(w.distanceTraveled(startTime))) {
      long time = startTime;
      do {
        time++;
        if (w.sourceLocation.distanceSq(bullet.position(time))
                < titan_utils_Matematica.square(w.distanceTraveled(time))
            && time < bullet.deathTime) {
          addBulletShadows(w, bullet, time);
          break;
        }
      } while (time < bullet.deathTime && _battleField.rectangle.contains(bullet.position(time)));
    }
  }

  private void addBulletShadows(titan_utils_Wave w, titan_gun_FireListener.FiredBullet b, long time) {
    titan_utils_geom_Circle waveCircle1 =
        new titan_utils_geom_Circle(w.sourceLocation, w.distanceTraveled(time - 1));
    titan_utils_geom_Circle waveCircle2 =
        new titan_utils_geom_Circle(w.sourceLocation, w.distanceTraveled(time));
    java.awt.geom.Point2D.Double bulletPoint1 = b.position(time-1);
    java.awt.geom.Point2D.Double bulletPoint2 = b.position(time);
    titan_utils_geom_LineSeg bulletSeg = new titan_utils_geom_LineSeg(bulletPoint1, bulletPoint2);
    java.awt.geom.Point2D.Double[] xPoints1 = waveCircle1.intersects(bulletSeg);
    java.awt.geom.Point2D.Double[] xPoints2 = waveCircle2.intersects(bulletSeg);

    if (xPoints1[0] == null && xPoints2[0] == null) {

      w.castShadow(bulletPoint1, bulletPoint2, b, time);

    } else if (xPoints1[0] != null) {
      if (xPoints2[0] != null) {

        w.castShadow(xPoints2[0], xPoints1[0], b, time);
      } else if (xPoints1[1] != null) {

        java.awt.geom.Point2D.Double intersect1, intersect2;
        if (bulletPoint1.distanceSq(xPoints1[0])
                < bulletPoint1.distanceSq(xPoints1[1])) {
          intersect1 = xPoints1[0];
          intersect2 = xPoints1[1];
        } else {
          intersect1 = xPoints1[1];
          intersect2 = xPoints1[0];
        }
        w.castShadow(bulletPoint1, intersect1, b, time);
        w.castShadow(intersect2, bulletPoint2, b, time);
      } else {

        w.castShadow(bulletPoint1, xPoints1[0], b, time);
      }
    } else if (xPoints2[0] != null) {

      w.castShadow(xPoints2[0], bulletPoint2, b, time);
    }
  }

  public titan_utils_Wave newMoveWave(java.awt.geom.Point2D.Double sourceLocation,
      java.awt.geom.Point2D.Double targetLocation, double absBearing, int fireRound,
      long fireTime, double bulletPower, double myEnergy, double myHeading,
      double myVelocity, int velocitySign, double accel, double dl8t,
      double dl20t, double dl40t, long timeSinceReverseDirection,
      long timeSinceVelocityChange) {
    titan_utils_Wave w = new titan_utils_Wave(botName, sourceLocation, targetLocation,
        fireRound, fireTime, bulletPower, myHeading, myVelocity,
        velocitySign, _battleField, _predictor)
            .setAbsBearing(absBearing)
            .setAccel(accel)
            .setDistance(sourceLocation.distance(targetLocation))
            .setDchangeTime(timeSinceReverseDirection)
            .setVchangeTime(timeSinceVelocityChange)
            .setDistanceLast8Ticks(dl8t)
            .setDistanceLast20Ticks(dl20t)
            .setDistanceLast40Ticks(dl40t)
            .setTargetEnergy(myEnergy)
            .setSourceEnergy(energy);
    _waveManager.addWave(w);
    return w;
  }

  public void updateImaginaryWave(long currentTime, titan_utils_RobotState myRobotState,
      int wavesToSurf, titan_utils_MovementPredictor predictor) {
    _imaginaryWaveIndex = -1;
    for (int x = 0; x < wavesToSurf; x++) {
      if (findSurfableWave(x, myRobotState) == null) {
        _imaginaryWaveIndex = x;
        break;
      }
    }

    double enemyGunHeat = getGunHeat(currentTime);
    if (_imaginaryWaveIndex >= 0 && enemyGunHeat < 0.1000001) {
      clearNeighborCache();
      java.awt.geom.Point2D.Double aimedFromLocation = null;

      if (enemyGunHeat < 0.0000001 && _waveManager.size() >= 2) {
        _imaginaryWave = _waveManager.getWaveByFireTime(currentTime);
        aimedFromLocation = getState(currentTime - 1).location;
      } else {
        _imaginaryWave = _waveManager.getWaveByFireTime(currentTime + 1);
        aimedFromLocation = lastScanState.location;
        java.awt.geom.Point2D.Double sourceLocation = _battleField.translateToField(
            predictor.nextLocation(lastScanState));

        if (sourceLocation.distance(myRobotState.location) < BOT_WIDTH) {
          sourceLocation = lastScanState.location;
        }

        _imaginaryWave.sourceLocation = sourceLocation;
      }

      if (_imaginaryWave != null) {

        _imaginaryWave.targetWallDistance =
            Math.min(1.5, _battleField.orbitalWallDistance(
                aimedFromLocation, _imaginaryWave.targetLocation,
                lastBulletPower, _imaginaryWave.orbitDirection));
        _imaginaryWave.targetRevWallDistance =
            Math.min(1.5, _battleField.orbitalWallDistance(
                aimedFromLocation, _imaginaryWave.targetLocation,
                lastBulletPower, -_imaginaryWave.orbitDirection));
        _imaginaryWave.absBearing = titan_utils_Matematica.absoluteBearing(
            aimedFromLocation, _imaginaryWave.targetLocation);
      }
    }
  }

  public titan_utils_Wave findSurfableWave(int surfWaveIndex, titan_utils_RobotState myRobotState) {
    titan_utils_Wave surfableWave = _waveManager.findSurfableWave(
        surfWaveIndex, myRobotState, titan_utils_Wave.WavePosition.BREAKING_CENTER);
    if (surfableWave == null && _imaginaryWave != null
        && surfWaveIndex == _imaginaryWaveIndex) {
      surfableWave = _imaginaryWave;
    }
    return surfableWave;
  }

  public void updateFiringWave(long currentTime, double bulletPower,
      titan_utils_RobotStateLog myStateLog, java.util.List<titan_gun_FireListener.FiredBullet> firedBullets) {
    long fireTime = currentTime - 1;
    titan_utils_Wave enemyWave = _waveManager.getWaveByFireTime(fireTime);
    if (enemyWave == null) {
      enemyWave = _waveManager.interpolateWaveByFireTime(fireTime, currentTime,
          lastScanState.heading, lastScanState.velocity, myStateLog,
          _battleField, _predictor);
      _out.println("WARNING (move): Wave with fire time " + fireTime
          + " doesn't exist, interpolation "
          + (enemyWave == null ? "failed" : "succeeded") + ".");
      if (enemyWave != null) {
        enemyWave.firingWave = true;
        _waveManager.addWave(enemyWave);
      }
    }
    if (enemyWave != null) {
      java.awt.geom.Point2D.Double aimedFromLocation = getState(currentTime - 2).location;
      enemyWave.sourceLocation = getState(currentTime - 1).location;
      enemyWave.targetLocation = myStateLog.getState(currentTime - 2).location;
      enemyWave.absBearing = titan_utils_Matematica.absoluteBearing(
          aimedFromLocation, enemyWave.targetLocation);
      enemyWave.setBulletPower(bulletPower);

      enemyWave.targetWallDistance = Math.min(1.5,
          _battleField.orbitalWallDistance(aimedFromLocation,
              enemyWave.targetLocation, lastBulletPower,
              enemyWave.orbitDirection));
      enemyWave.targetRevWallDistance = Math.min(1.5,
          _battleField.orbitalWallDistance(aimedFromLocation,
              enemyWave.targetLocation, lastBulletPower,
              -enemyWave.orbitDirection));

      if (_imaginaryWave != null) {
        clearNeighborCache();
      }
      _imaginaryWave = null;

      enemyWave.firingWave = true;
      this.lastBulletPower = bulletPower;
      this.lastBulletFireTime = enemyWave.fireTime;
      for (titan_gun_FireListener.FiredBullet bullet : firedBullets) {
        setShadows(enemyWave, bullet);
      }

      double[] dataPoint =
          bulletPowerDataPoint(
              enemyWave.targetDistance, enemyWave.sourceEnergy,
              enemyWave.targetEnergy);
      powerTree.addPoint(dataPoint, bulletPower);
    }
  }

  public void updateDamageFactor() {
    if (alive) {
      timeAliveTogether++;
      totalDistance += distance;
    }
    damageFactor = (((damageTaken + 10) * totalDistance) / timeAliveTogether);
  }

  public double guessBulletPower(double myEnergy) {
    int numBullets = powerTree.size();
    if (numBullets == 0) {
      return 1.9;
    } else {
      double[] searchPoint = bulletPowerDataPoint(distance, energy, myEnergy);
      java.util.List<titan_KdTree.Entry<Double>> bulletPowers =
          powerTree.nearestNeighbor(searchPoint,
              (int) Math.min(20, Math.ceil(numBullets / 3.0)), false);

      double powerTotal = 0;
      for (titan_KdTree.Entry<Double> entry : bulletPowers) {
        powerTotal += entry.value;
      }

      return titan_utils_Matematica.round(powerTotal / bulletPowers.size(), 6);
    }
  }

  private titan_utils_WaveManager.WaveBreakListener newWaveBreakListener(
      final int currentRound, final long currentTime) {
    return new titan_utils_WaveManager.WaveBreakListener() {
      @Override
      public void onWaveBreak(titan_utils_Wave w, java.util.List<titan_utils_RobotState> waveBreakStates) {
        if (w.firingWave) {
          titan_utils_Wave.Intersection preciseIntersection =
              w.preciseIntersection(waveBreakStates);

          for (titan_utils_KnnView<titan_utils_TimestampedGuessFactor> view : views.values()) {
            if (view.logVisits) {
              double guessFactor = w.guessFactor(preciseIntersection.angle);
              view.logWave(w, new titan_utils_TimestampedGuessFactor(
                  currentRound, currentTime, guessFactor));
            }
          }

          rememberWave(w, w.guessFactor(preciseIntersection.angle), memoryVisits);

          if (!w.bulletHitBullet) {
            raw1v1ShotsFired++;
            raw1v1ShotsFiredThisRound++;
            int powerGroup = powerContext(w.bulletPower());
            contextShots[powerGroup]++;
            if (w.hitByBullet) {
              double angularBotWidth = preciseIntersection.bandwidth * 2;
              double thisHit = (TYPICAL_ANGULAR_BOT_WIDTH / angularBotWidth)
                  * (w.escapeAngleRange() / TYPICAL_ESCAPE_RANGE);
              contextHits[powerGroup]++;
              contextWeightedHits[powerGroup] += thisHit;
              weighted1v1ShotsHit += thisHit;
              weighted1v1ShotsHitThisRound += thisHit;
              raw1v1ShotsHit++;
              raw1v1ShotsHitThisRound++;
            }
          }
        }
      }
    };
  }

  public void drawRawWaves(final long currentTime) {
    _waveManager.forAllWaves(new titan_utils_WaveManager.AllWaveListener() {
      @Override
      public void onWave(titan_utils_Wave w) {
        if (w.firingWave) {
          _renderables.add(titan_gfx_RoboGraphic.drawCircle(w.sourceLocation,
              (currentTime - w.fireTime + 1) * w.bulletSpeed(),
              java.awt.Color.darkGray));
          _renderables.add(titan_gfx_RoboGraphic.drawLine(w.sourceLocation,
              titan_utils_Matematica.project(w.sourceLocation, w.absBearing,
                  w.distanceTraveled(currentTime + 1)), java.awt.Color.darkGray));
          _renderables.add(titan_gfx_RoboGraphic.drawCircle(
              titan_utils_Matematica.project(w.sourceLocation, w.absBearing,
                  w.distanceTraveled(currentTime + 1)),
            5, java.awt.Color.darkGray));
          _renderables.add(titan_gfx_RoboGraphic.drawCircleFilled(
              w.sourceLocation, java.awt.Color.darkGray, 4));
        }
      }
    });
  }

  private void drawBulletHit(titan_utils_Wave hitWave, double hitGuessFactor,
      java.awt.geom.Point2D.Double bulletLocation, long time) {
    _renderables.add(titan_gfx_RoboGraphic.drawLine(hitWave.sourceLocation,
        titan_utils_Matematica.project(hitWave.sourceLocation, hitWave.absBearing,
            hitWave.distanceTraveled(time)),
        java.awt.Color.yellow));
    if (Math.abs(hitGuessFactor) > 0.01) {
      _renderables.add(titan_gfx_RoboGraphic.drawLine(hitWave.sourceLocation,
          bulletLocation, (hitGuessFactor > 0 ? java.awt.Color.blue : java.awt.Color.red)));
    }
  }

  public static class WallHitDamage {
    public final double min;
    public final double max;

    public WallHitDamage(double min, double max) {
      this.min = min;
      this.max = max;
    }
  }
}

public static class titan_move_SurfMover {
  private static final double DESIRED_DISTANCE = 650;
  private static final double WALL_STICK = 160;
  private static final double MEA_WALL_STICK = 100;
  private static final double DISTANCING_DANGER_BASE = 2.5;
  private static final double MAX_ATTACK_ANGLE = Math.PI * .45;
  private static final double BASE_DANGER_FACTOR = 1.0;

  private static final java.awt.Color SHADOW_COLOR = new java.awt.Color(50, 255, 50);
  private static final int WAVES_TO_DRAW = 5;

  private robocode.AdvancedRobot _robot;
  private titan_utils_BattleField _battleField;
  private java.util.Collection<titan_gfx_RoboGraphic> _renderables;
  private java.io.PrintStream _out;

  private SurfOption _lastSurfOption = SurfOption.CLOCKWISE;
  private java.awt.geom.Point2D.Double _lastSurfDestination;
  private java.awt.geom.Point2D.Double _stopDestination;
  private java.util.Map<SurfOption, Double> _surfOptionDangers;
  private java.util.Map<SurfOption, java.awt.geom.Point2D.Double> _surfOptionDestinations;
  private DistanceController _distancer;
  private titan_utils_Wave _lastWaveSurfed;
  private titan_utils_MovementPredictor _predictor;

  public titan_move_SurfMover(robocode.AdvancedRobot robot, titan_utils_BattleField battleField,
      java.util.Collection<titan_gfx_RoboGraphic> renderables, java.io.OutputStream out) {
    _robot = robot;
    _battleField = battleField;
    _renderables = renderables;
    _out = new java.io.PrintStream(out);

    _predictor = new titan_utils_MovementPredictor(battleField);
    _surfOptionDangers = new java.util.HashMap<SurfOption, Double>();
    _surfOptionDestinations = new java.util.HashMap<SurfOption, java.awt.geom.Point2D.Double>();
    _distancer = new DistanceController();
  }

  public void initRound(robocode.AdvancedRobot robot) {
    _robot = robot;
    _lastSurfDestination = null;
    _stopDestination = null;
  }

  public void move(titan_utils_RobotState myRobotState, titan_move_MoveEnemy duelEnemy,
      int wavesToSurf, boolean painting) {
    if (duelEnemy == null) {
      return;
    }

    java.awt.geom.Point2D.Double myLocation = myRobotState.location;
    titan_utils_Wave surfWave = duelEnemy.findSurfableWave(titan_utils_Wave.FIRST_WAVE, myRobotState);

    if (surfWave == null) {
      orbit(myLocation, duelEnemy);
    } else {
      surf(myRobotState, duelEnemy, surfWave, wavesToSurf, painting);
    }
  }

  void orbit(java.awt.geom.Point2D.Double myLocation, titan_move_MoveEnemy duelEnemy) {
    _robot.setMaxVelocity(8);
    titan_utils_RobotState enemyState = duelEnemy.lastScanState;
    double orbitAbsBearing =
        titan_utils_Matematica.absoluteBearing(enemyState.location, myLocation);
    double retreatAngle =
        _distancer.orbitAttackAngle(myLocation.distance(enemyState.location));
    double counterGoAngle = orbitAbsBearing
        + (SurfOption.COUNTER_CLOCKWISE.getDirection()
            * ((Math.PI / 2) + retreatAngle));
    counterGoAngle =
        wallSmoothing(myLocation, counterGoAngle, SurfOption.COUNTER_CLOCKWISE);

    double clockwiseGoAngle = orbitAbsBearing
        + (SurfOption.CLOCKWISE.getDirection()
            * ((Math.PI / 2) + retreatAngle));
    clockwiseGoAngle =
        wallSmoothing(myLocation, clockwiseGoAngle, SurfOption.CLOCKWISE);

    double goAngle;
    if (Math.abs(
            robocode.util.Utils.normalRelativeAngle(clockwiseGoAngle - orbitAbsBearing))
        < Math.abs(
            robocode.util.Utils.normalRelativeAngle(counterGoAngle - orbitAbsBearing))) {
      _lastSurfOption = SurfOption.CLOCKWISE;
      goAngle = clockwiseGoAngle;
    } else {
      _lastSurfOption = SurfOption.COUNTER_CLOCKWISE;
      goAngle = counterGoAngle;
    }

    titan_utils_Matematica.setBackAsFront(_robot, goAngle);
  }

  void surf(titan_utils_RobotState myRobotState, titan_move_MoveEnemy duelEnemy, titan_utils_Wave surfWave,
      int wavesToSurf, boolean painting) {
    if (surfWave != _lastWaveSurfed) {
      duelEnemy.clearNeighborCache();
      _lastWaveSurfed = surfWave;
      _lastSurfDestination = null;
      _stopDestination = null;
    }

    boolean goingClockwise = (_lastSurfOption == SurfOption.CLOCKWISE);
    updateSurfDangers(myRobotState, duelEnemy, wavesToSurf, goingClockwise);
    double counterDanger = _surfOptionDangers.get(SurfOption.COUNTER_CLOCKWISE);
    double stopDanger = _surfOptionDangers.get(SurfOption.STOP);
    double clockwiseDanger = _surfOptionDangers.get(SurfOption.CLOCKWISE);

    java.awt.geom.Point2D.Double surfDestination;
    if (stopDanger <= counterDanger && stopDanger <= clockwiseDanger) {
      if (_stopDestination == null) {
        _stopDestination = _surfOptionDestinations.get(_lastSurfOption);
      }
      surfDestination = _stopDestination;
      _robot.setMaxVelocity(0);
      _lastSurfDestination = null;
    } else {
      _robot.setMaxVelocity(8);
      _lastSurfOption = (clockwiseDanger < counterDanger)
          ? SurfOption.CLOCKWISE : SurfOption.COUNTER_CLOCKWISE;
      surfDestination = _surfOptionDestinations.get(_lastSurfOption);
      _lastSurfDestination = surfDestination;
      _stopDestination = null;
    }

    double goAngle =
        titan_utils_Matematica.absoluteBearing(myRobotState.location, surfDestination);
    goAngle = wallSmoothing(myRobotState.location, goAngle, _lastSurfOption);

    if (painting) {
      duelEnemy.drawRawWaves(_robot.getTime());
      for (int x = 0; x < WAVES_TO_DRAW; x++) {
        drawWaveDangers(myRobotState, duelEnemy, x);
        drawWaveShadows(myRobotState, duelEnemy, x);
      }
    }

    titan_utils_Matematica.setBackAsFront(_robot, goAngle);
  }

  private void updateSurfDangers(titan_utils_RobotState myRobotState, titan_move_MoveEnemy duelEnemy,
      int wavesToSurf, boolean goingClockwise) {
    java.util.List<SurfOption> surfOptions = getSortedSurfOptions();
    double bestSurfDanger = Double.POSITIVE_INFINITY;
    for (SurfOption testOption : surfOptions) {
      double testDanger = checkDanger(myRobotState, duelEnemy,
          myRobotState, testOption, goingClockwise, titan_utils_Wave.FIRST_WAVE,
          wavesToSurf, bestSurfDanger, new titan_utils_RobotStateLog());
      _surfOptionDangers.put(testOption, testDanger);
      bestSurfDanger = Math.min(bestSurfDanger, testDanger);
    }
  }

  java.util.List<SurfOption> getSortedSurfOptions() {
    java.util.List<SurfOption> surfOptions = java.util.Arrays.asList(SurfOption.values());
    for (int x = 0; x < surfOptions.size(); x++) {
      double lowestDanger = getSurfOptionDanger(surfOptions.get(x));
      for (int y = x + 1; y < surfOptions.size(); y++) {
        if (getSurfOptionDanger(surfOptions.get(y)) < lowestDanger) {
          lowestDanger = getSurfOptionDanger(surfOptions.get(y));
          SurfOption temp = surfOptions.get(x);
          surfOptions.set(x, surfOptions.get(y));
          surfOptions.set(y, temp);
        }
      }
    }
    return surfOptions;
  }

  private double getSurfOptionDanger(SurfOption surfOption) {
    if (_surfOptionDangers.containsKey(surfOption)) {
      return _surfOptionDangers.get(surfOption);
    } else {
      return 0;
    }
  }

  double checkDanger(titan_utils_RobotState myRobotState, titan_move_MoveEnemy duelEnemy,
      titan_utils_RobotState startState, SurfOption surfOption,
      boolean previouslyMovingClockwise, int surfWaveIndex, int numWavesToSurf,
      double cutoffDanger, titan_utils_RobotStateLog predictedStateLog) {
    titan_utils_Wave surfWave = duelEnemy.findSurfableWave(surfWaveIndex, myRobotState);
    if (surfWave == null) {
      return 0;
    }

    java.util.List<titan_utils_RobotState> dangerStates = new java.util.ArrayList<titan_utils_RobotState>();
    titan_utils_Wave.WavePosition startWavePosition = surfWave.checkWavePosition(startState);
    if (surfWaveIndex > titan_utils_Wave.FIRST_WAVE
        && startWavePosition != titan_utils_Wave.WavePosition.MIDAIR) {
      dangerStates.addAll(
          replaySurfStates(surfWave, predictedStateLog, startState));
    }

    if (startWavePosition == titan_utils_Wave.WavePosition.GONE && dangerStates.isEmpty()) {
      return 0;
    }

    boolean predictClockwise =
        predictClockwise(surfOption, previouslyMovingClockwise);

    titan_utils_RobotState predictedState = startState;
    titan_utils_RobotState passedState = startState;

    boolean wavePassed = false;
    boolean waveHit = false;
    double maxVelocity;
    SurfOption smoothingSurfOption;
    if (surfOption == SurfOption.STOP) {
      maxVelocity = 0;
      smoothingSurfOption = predictClockwise
          ? SurfOption.CLOCKWISE : SurfOption.COUNTER_CLOCKWISE;
    } else {
      maxVelocity = 8;
      smoothingSurfOption = surfOption;
    }
    java.awt.geom.Point2D.Double surfDestination;
    if (surfWaveIndex == titan_utils_Wave.FIRST_WAVE && surfOption == SurfOption.STOP
        && _stopDestination != null) {
      surfDestination = _stopDestination;
    } else {
      surfDestination = surfDestination(
          surfWave, surfWaveIndex, startState, smoothingSurfOption);
    }
    if (surfWaveIndex == titan_utils_Wave.FIRST_WAVE) {
      _surfOptionDestinations.put(surfOption, surfDestination);
    }

    do {
      if (!waveHit && surfWave.checkWavePosition(
              predictedState, titan_utils_Wave.WavePosition.BREAKING_FRONT)
                  == titan_utils_Wave.WavePosition.BREAKING_FRONT) {
        titan_utils_RobotState dangerState = predictedState;
        do {
          dangerStates.add(dangerState);
          dangerState = predictSurfLocation(
              dangerState, surfDestination, 0, smoothingSurfOption);
        } while (
            surfWave.checkWavePosition(dangerState, true) != titan_utils_Wave.WavePosition.GONE);
        waveHit = true;
      }

      titan_utils_Wave.WavePosition wavePosition =
          surfWave.checkWavePosition(predictedState, true);
      if (wavePosition == titan_utils_Wave.WavePosition.BREAKING_CENTER
          || wavePosition == titan_utils_Wave.WavePosition.GONE) {
        passedState = predictedState;
        wavePassed = true;
      } else {
        predictedStateLog.addState(predictedState);
        predictedState = predictSurfLocation(
            predictedState, surfDestination, maxVelocity, smoothingSurfOption);
      }
    } while (!wavePassed);

    titan_utils_Wave.Intersection intersection = surfWave.preciseIntersection(dangerStates);
    long firstPossibleHit = passedState.time;
    for (titan_utils_RobotState state : dangerStates) firstPossibleHit = Math.min(firstPossibleHit, state.time);
    java.util.List<titan_utils_Wave.BulletShadow> previousShadows = surfWave.forecastShadows;
    surfWave.forecastShadows = surfWave.shadowsBefore(firstPossibleHit);
    double danger;
    try {
    double baseDangerScore =
        contextEnemyHitRate(duelEnemy, surfWave) * BASE_DANGER_FACTOR;
    danger = baseDangerScore + getDangerScore(
        duelEnemy, surfWave, intersection, surfWaveIndex);
    danger *= surfWave.shadowFactor(intersection);
    } finally {
      surfWave.forecastShadows = previousShadows;
    }
    danger *= robocode.Rules.getBulletDamage(surfWave.bulletPower());
    double currentDistanceToWaveSource =
        myRobotState.location.distance(surfWave.sourceLocation);
    double currentDistanceToWave = currentDistanceToWaveSource
        - surfWave.distanceTraveled(_robot.getTime());
    double timeToImpact =
        Math.max(1, currentDistanceToWave / surfWave.bulletSpeed());
    danger /= timeToImpact;

    danger *= distancingDanger(startState.location, passedState.location,
        duelEnemy.lastScanState.location);

    if (surfWaveIndex + 1 < numWavesToSurf) {
      if (danger >= cutoffDanger) return Double.POSITIVE_INFINITY;
      double nextCounterClockwiseDanger = checkDanger(myRobotState, duelEnemy,
          passedState, SurfOption.COUNTER_CLOCKWISE, predictClockwise,
          surfWaveIndex + 1, numWavesToSurf, cutoffDanger,
          (titan_utils_RobotStateLog) predictedStateLog.clone());
      double nextStopDanger = checkDanger(myRobotState, duelEnemy, passedState,
          SurfOption.STOP, predictClockwise, surfWaveIndex + 1, numWavesToSurf,
          cutoffDanger, (titan_utils_RobotStateLog) predictedStateLog.clone());
      double nextClockwiseDanger = checkDanger(myRobotState, duelEnemy,
          passedState, SurfOption.CLOCKWISE, predictClockwise,
          surfWaveIndex + 1, numWavesToSurf, cutoffDanger,
          (titan_utils_RobotStateLog) predictedStateLog.clone());

      danger += Math.min(nextCounterClockwiseDanger,
                         Math.min(nextStopDanger, nextClockwiseDanger));
    }

    return danger;
  }

  private boolean predictClockwise(
      SurfOption surfOption, boolean previouslyMovingClockwise) {
    if (surfOption == SurfOption.STOP) {
      return previouslyMovingClockwise;
    } else {
      return (surfOption == SurfOption.CLOCKWISE);
    }
  }

  java.util.List<titan_utils_RobotState> replaySurfStates(final titan_utils_Wave surfWave,
      titan_utils_RobotStateLog predictedStateLog, titan_utils_RobotState startState) {
    final java.util.List<titan_utils_RobotState> dangerStates = new java.util.ArrayList<titan_utils_RobotState>();
    predictedStateLog.forAllStates(new titan_utils_RobotStateLog.AllStateListener() {
      @Override
      public void onRobotState(titan_utils_RobotState state) {
        titan_utils_Wave.WavePosition pastWavePosition = surfWave.checkWavePosition(state);
        if (pastWavePosition.isBreaking()) {
          dangerStates.add(state);
        }
      }
    });
    return dangerStates;
  }

  private java.awt.geom.Point2D.Double surfDestination(titan_utils_Wave surfWave,
      int surfWaveIndex, titan_utils_RobotState startState, SurfOption surfOption) {
    if (surfWaveIndex == titan_utils_Wave.FIRST_WAVE && _lastSurfOption == surfOption
        && _lastSurfDestination != null) {
      return _lastSurfDestination;
    }

    double attackAngle = _distancer.surfAttackAngle(
        surfWave.sourceLocation.distance(startState.location));
    titan_utils_MaxEscapeTarget meaTarget = _predictor.preciseEscapeAngle(
        surfOption.getDirection(), surfWave.sourceLocation, surfWave.fireTime,
        surfWave.bulletSpeed(), startState, attackAngle, MEA_WALL_STICK);
    return meaTarget.location;
  }

  titan_utils_RobotState predictSurfLocation(titan_utils_RobotState robotState,
      java.awt.geom.Point2D.Double surfDestination, double maxVelocity,
      SurfOption smoothingSurfOption) {
    double goAngle = wallSmoothing(robotState.location,
        titan_utils_Matematica.absoluteBearing(robotState.location, surfDestination),
        smoothingSurfOption);
    return _predictor.nextLocation(robotState, maxVelocity, goAngle, false);
  }

  private double wallSmoothing(java.awt.geom.Point2D.Double startLocation,
      double goAngleRadians, SurfOption surfOption) {
    return _battleField.wallSmoothing(
        startLocation, goAngleRadians, surfOption.getDirection(), WALL_STICK);
  }

  double distancingDanger(java.awt.geom.Point2D.Double startLocation,
      java.awt.geom.Point2D.Double predictedLocation, java.awt.geom.Point2D.Double enemyLocation) {
    double distanceToEnemy = enemyLocation.distance(startLocation);
    double predictedDistanceToEnemy = enemyLocation.distance(predictedLocation);

    double distanceQuotient = distanceToEnemy / predictedDistanceToEnemy;

    return Math.pow(DISTANCING_DANGER_BASE, distanceQuotient)
        / DISTANCING_DANGER_BASE;
  }

  double getDangerScore(titan_move_MoveEnemy duelEnemy, titan_utils_Wave w,
        java.awt.geom.Point2D.Double dangerLocation, int surfWaveIndex) {
    titan_utils_Wave.Intersection intersection = new titan_utils_Wave.Intersection(
        titan_utils_Matematica.absoluteBearing(w.sourceLocation, dangerLocation), 0.05);
    return getDangerScore(duelEnemy, w, intersection, surfWaveIndex);
  }

  double getDangerScore(titan_move_MoveEnemy duelEnemy, titan_utils_Wave w,
      titan_utils_Wave.Intersection intersection, int surfWaveIndex) {
    double dangerAngle = intersection.angle;
    double bandwidth = Math.max(1e-6, intersection.bandwidth);
    double totalDanger = 0;
    double totalScanWeight = 0;
    int enabledSize = 0;
    double hitPercentage = 100 * contextEnemyHitRate(duelEnemy, w);
    double marginOfError = contextEnemyMargin(duelEnemy, w);
    for (titan_utils_KnnView<titan_utils_TimestampedGuessFactor> view : duelEnemy.views.values()) {
      if (view.enabled(hitPercentage, marginOfError)) {
        enabledSize += view.size();
        java.util.List<titan_KdTree.Entry<titan_utils_TimestampedGuessFactor>> nearestNeighbors =
            getNearestNeighbors(view, w, surfWaveIndex);
        java.util.Map<titan_utils_Timestamped, Double> weightMap =
            view.getDecayWeights(nearestNeighbors);

        double density = 0;
        double viewScanWeight = 0;
        int numScans = nearestNeighbors.size();
        for (int x = 0; x < numScans; x++) {
          titan_utils_TimestampedGuessFactor tsgf = nearestNeighbors.get(x).value;
          double scanWeight =
              weightMap.get(tsgf) / Math.sqrt(Math.max(1e-9, nearestNeighbors.get(x).distance));
          double xFiringAngle = titan_utils_Matematica.normalizeAngle(
              w.firingAngle(tsgf.guessFactor), dangerAngle);
          if (!w.shadowed(xFiringAngle)) {
            double ux = (xFiringAngle - dangerAngle) / bandwidth;
            density += scanWeight * Math.pow(2, -Math.abs(ux));
          }
          viewScanWeight += scanWeight;
        }
        totalScanWeight += viewScanWeight * view.weight;
        totalDanger += view.weight * density;
      }
    }

    if (enabledSize == 0) {
      return defaultDanger(w, intersection);
    }

    return totalScanWeight > 0 && Double.isFinite(totalDanger) ? totalDanger / totalScanWeight : defaultDanger(w, intersection);
  }

  private java.util.List<titan_KdTree.Entry<titan_utils_TimestampedGuessFactor>> getNearestNeighbors(
      titan_utils_KnnView<titan_utils_TimestampedGuessFactor> view, titan_utils_Wave w, int surfWaveIndex) {
    if (!view.cachedNeighbors.containsKey(surfWaveIndex)
        || view.cachedNeighborWaves.get(surfWaveIndex) != w) {
      view.cachedNeighbors.put(surfWaveIndex, view.nearestNeighbors(w, false));
      view.cachedNeighborWaves.put(surfWaveIndex, w);
    }
    return view.cachedNeighbors.get(surfWaveIndex);
  }

  private int contextGroup(titan_move_MoveEnemy enemy, titan_utils_Wave wave) {
    if (!POWER_CONTEXT_DEFENSE || enemy == null || wave == null) return -1;
    int group = powerContext(wave.bulletPower());
    if (enemy.contextShots[group] < 16) return -1;
    enemy.contextQueries++;
    return group;
  }

  double contextEnemyHitRate(titan_move_MoveEnemy enemy, titan_utils_Wave wave) {
    int group = contextGroup(enemy, wave);
    double result = group < 0 ? normalizedEnemyHitRate(enemy)
        : enemy.contextWeightedHits[group] / enemy.contextShots[group];
    return Double.isFinite(result) && result >= 0 ? result : 0;
  }

  double contextEnemyMargin(titan_move_MoveEnemy enemy, titan_utils_Wave wave) {
    int group = contextGroup(enemy, wave);
    return group < 0 ? hitPercentageMarginOfError(enemy)
        : weightedHitMargin(enemy.contextShots[group], enemy.contextHits[group], enemy.contextWeightedHits[group]);
  }

  double normalizedEnemyHitRate(titan_move_MoveEnemy enemy) {
    if (enemy == null || enemy.raw1v1ShotsFired <= 0) return 0;
    double value = enemy.weighted1v1ShotsHit / enemy.raw1v1ShotsFired;
    return Double.isFinite(value) && value >= 0 ? value : 0;
  }

  double normalizedEnemyHitPercentage(titan_move_MoveEnemy duelEnemy) {
    return 100 * normalizedEnemyHitRate(duelEnemy);
  }

  double rawEnemyHitPercentage(titan_move_MoveEnemy duelEnemy) {
    return (duelEnemy == null || duelEnemy.raw1v1ShotsFired == 0)
        ? 0 : ((((double) duelEnemy.raw1v1ShotsHit)
            / duelEnemy.raw1v1ShotsFired) * 100.0);
  }

  double hitPercentageMarginOfError(titan_move_MoveEnemy enemy) {
    if (enemy != null && ((enemy.raw1v1ShotsFired == 0 && !enemy.memoryHits.isEmpty())
        || (enemy.raw1v1ShotsFired > 0 && enemy.weighted1v1ShotsHit > enemy.raw1v1ShotsFired))) enemy.confidenceRescues++;
    return enemy == null ? 100 : weightedHitMargin(enemy.raw1v1ShotsFired,
        enemy.raw1v1ShotsHit, enemy.weighted1v1ShotsHit);
  }

  private static double defaultDanger(titan_utils_Wave w, titan_utils_Wave.Intersection intersection) {
    double[] guessFactors = new double[]{0, 0.85};
    double[] weights = new double[]{3, 1};
    double danger = 0;
    for (int x = 0; x < guessFactors.length; x++) {
      double firingAngle = w.firingAngle(guessFactors[x]);
      double ux = (firingAngle
          - titan_utils_Matematica.normalizeAngle(intersection.angle, firingAngle))
              / intersection.bandwidth;
      danger += weights[x] * Math.pow(2, -Math.abs(ux));
    }
    return danger;
  }

  public void roundOver(titan_move_MoveEnemy duelEnemy) {
    _out.println("Enemy normalized hit %: "
        + titan_utils_Matematica.round(normalizedEnemyHitPercentage(duelEnemy), 2) + "\n"
        + "Enemy raw hit %: "
        + titan_utils_Matematica.round(rawEnemyHitPercentage(duelEnemy), 2));
  }

  java.util.Map<SurfOption, Double> getSurfOptionDangers() {
    return _surfOptionDangers;
  }

  private void drawWaveDangers(
      titan_utils_RobotState myRobotState, titan_move_MoveEnemy duelEnemy, int surfWaveIndex) {
    titan_utils_Wave surfWave = duelEnemy.findSurfableWave(surfWaveIndex, myRobotState);
    if (surfWave == null) {
      return;
    }

    int numBins = 51;
    double halfBins = (double)(numBins - 1) / 2;

    double[] gfDangers = new double[numBins];
    boolean[] gfShadowed = new boolean[numBins];
    double minDanger = Double.POSITIVE_INFINITY;

    long currentTime = _robot.getTime();
    for (int x = 0; x <= numBins - 1; x++) {
      double gf = (x - halfBins) / halfBins;
      double bearingOffset =
          surfWave.orbitDirection * (gf * surfWave.maxEscapeAngle());
      double absFiringAngle = surfWave.absBearing + bearingOffset;
      java.awt.geom.Point2D.Double dangerLocation = titan_utils_Matematica.project(
          surfWave.sourceLocation, absFiringAngle,
          surfWave.distanceTraveled(currentTime));
      if (surfWave.shadowed(absFiringAngle)) {
        gfShadowed[x] = true;
        gfDangers[x] = 0;
      } else {
        gfShadowed[x] = false;
        gfDangers[x] = getDangerScore(
            duelEnemy, surfWave, dangerLocation, surfWaveIndex);
      }

      if (gfDangers[x] < minDanger) {
        minDanger = gfDangers[x];
      }
    }

    double avg = titan_utils_Matematica.average(gfDangers);
    double stDev = titan_utils_Matematica.standardDeviation(gfDangers);

    for (int x = 0; x <= numBins - 1; x++) {
      double gf = (x - halfBins) / halfBins;
      double bearingOffset =
          surfWave.orbitDirection * (gf * surfWave.maxEscapeAngle());
      java.awt.geom.Point2D.Double drawLocation = titan_utils_Matematica.project(
          surfWave.sourceLocation, surfWave.absBearing + bearingOffset,
          surfWave.distanceTraveled(currentTime + 1) - 15);

      java.awt.Color binColor = titan_gfx_RoboGraphic.riskColor(
          gfDangers[x] - minDanger, avg - minDanger, stDev, false, 1);
      _renderables.add(titan_gfx_RoboGraphic.drawCircleFilled(drawLocation,
          gfShadowed[x] ? SHADOW_COLOR : binColor, 2));
    }
  }

  private void drawWaveShadows(
      titan_utils_RobotState myRobotState, titan_move_MoveEnemy duelEnemy, int surfWaveIndex) {
    titan_utils_Wave surfWave = duelEnemy.findSurfableWave(surfWaveIndex, myRobotState);
    if (surfWave == null) {
      return;
    }

    long currentTime = _robot.getTime();
    for (titan_utils_Wave.BulletShadow shadow : surfWave.shadows) {
      java.awt.geom.Point2D.Double p1 = titan_utils_Matematica.project(surfWave.sourceLocation,
          shadow.minAngle, surfWave.distanceTraveled(currentTime + 1));
      java.awt.geom.Point2D.Double p2 = titan_utils_Matematica.project(surfWave.sourceLocation,
          shadow.maxAngle, surfWave.distanceTraveled(currentTime + 1));
      _renderables.add(titan_gfx_RoboGraphic.drawLine(p1, p2, SHADOW_COLOR));
    }
  }

  enum SurfOption {
    COUNTER_CLOCKWISE(-1),
    STOP(0),
    CLOCKWISE(1);

    private int _direction;

    private SurfOption(int direction) {
      _direction = direction;
    }

    public int getDirection() {
      return _direction;
    }
  }

  private static class DistanceController {
    public double surfAttackAngle(double currentDistance) {
      return attackAngle(currentDistance, 0.6);
    }

    public double orbitAttackAngle(double currentDistance) {
      return attackAngle(currentDistance, 1.65);
    }

    private double attackAngle(
        double currentDistance, double offsetMultiplier) {
      double distanceFactor =
          (currentDistance - DESIRED_DISTANCE) / DESIRED_DISTANCE;
      return titan_utils_Matematica.limit(-MAX_ATTACK_ANGLE,
                            distanceFactor * offsetMultiplier,
                            MAX_ATTACK_ANGLE);
    }
  }
}

public static class titan_move_formulas_FlattenerFormula extends titan_utils_DistanceFormula {
  public titan_move_formulas_FlattenerFormula() {
    weights = new double[]{3, 4, 3, 5, 1, 4, 3, 3, 2, 2, 2, 2};
  }

  @Override
  public double[] dataPointFromWave(titan_utils_Wave w, boolean aiming) {
    return new double[]{
      (Math.min(91, w.targetDistance / w.bulletSpeed()) / 91),
      (((w.targetVelocitySign * w.targetVelocity) + 0.1) / 8.1),
      (Math.sin(w.targetRelativeHeading)),
      ((Math.cos(w.targetRelativeHeading) + 1) / 2),
      (((w.targetAccel / (w.targetAccel < 0
          ? robocode.Rules.DECELERATION : robocode.Rules.ACCELERATION)) + 1) / 2),
      (Math.min(1.0, w.targetWallDistance) / 1.0),
      (Math.min(1.0, w.targetRevWallDistance) / 1.0),
      (Math.min(1, ((double) w.targetVchangeTime)
          / (w.targetDistance/w.bulletSpeed()))),
      (w.targetDl8t / 64),
      (w.targetDl20t / 160),
      (w.targetDl40t / 320),
      (w.bulletPower() / 3),
    };
  }
}

public static class titan_move_formulas_NormalFormula extends titan_utils_DistanceFormula {
  public titan_move_formulas_NormalFormula() {
    weights = new double[]{4, 3, 3, 3, 2, 4, 1, 3, 2, 2};
  }

  @Override
  public double[] dataPointFromWave(titan_utils_Wave w, boolean aiming) {
    return new double[]{
      (Math.min(91, w.targetDistance / w.bulletSpeed()) / 91),
      (((w.targetVelocitySign * w.targetVelocity) + 0.1) / 8.1),
      (Math.sin(w.targetRelativeHeading)),
      ((Math.cos(w.targetRelativeHeading) + 1) / 2),
      (((w.targetAccel / (w.targetAccel < 0
          ? robocode.Rules.DECELERATION : robocode.Rules.ACCELERATION)) + 1) / 2),
      (Math.min(1.0, w.targetWallDistance) / 1.0),
      (Math.min(1.0, w.targetRevWallDistance) / 1.0),
      (Math.min(1, w.targetVchangeTime / (w.targetDistance/w.bulletSpeed()))),
      (w.targetDl8t / 64),
      (w.bulletPower() / 3)
    };
  }
}

public static class titan_move_formulas_SimpleFormula extends titan_utils_DistanceFormula {
  public titan_move_formulas_SimpleFormula() {
    weights = new double[]{1, 1, 1};
  }

  @Override
  public double[] dataPointFromWave(titan_utils_Wave w, boolean aiming) {
    return new double[]{
      (Math.min(91, w.targetDistance / w.bulletSpeed()) / 91),
      ((w.lateralVelocity() + 0.1) / 8.1),
      (((w.targetAccel / (w.targetAccel < 0
          ? robocode.Rules.DECELERATION : robocode.Rules.ACCELERATION)) + 1) / 2)
    };
  }
}

public static class titan_radar_Radar implements titan_gfx_RoboPainter {
  private static final boolean ENABLE_DEBUGGING_GRAPHICS = true;
  private static final double MAX_RADAR_TRACKING_AMOUNT = Math.PI / 4;
  private static final long BOT_NOT_FOUND = -1;
  private static final double BOT_WIDTH = 36;

  private titanprime_v4_41 _robot;
  private java.util.Map<String, RadarScan> _scans;
  private java.awt.geom.Point2D.Double _myLocation;
  private String _targetName = null;
  private boolean _lockMode = false;
  private long _resetTime;
  private java.awt.geom.Point2D.Double _centerField;
  private int _radarDirection;
  private double _lastRadarHeading;
  private titan_utils_MovementPredictor _predictor;
  private int _startDirection;

  private java.util.List<titan_gfx_RoboGraphic> _renderables;
  private boolean _painting;
  private boolean _robocodePainting;
  private java.io.PrintStream _out;

  public titan_radar_Radar(titanprime_v4_41 robot, java.io.OutputStream out) {
    _robot = robot;
    _out = new java.io.PrintStream(out);
    _scans = new java.util.HashMap<String, RadarScan>();
    _centerField = new java.awt.geom.Point2D.Double(_robot.getBattleFieldWidth() / 2,
                                      _robot.getBattleFieldHeight() / 2);
    _predictor = new titan_utils_MovementPredictor(
        new titan_utils_BattleField(_robot.getBattleFieldWidth(),
                        _robot.getBattleFieldHeight()));
    _radarDirection = 1;
    _renderables = new java.util.ArrayList<titan_gfx_RoboGraphic>();
    _painting = false;
    _robocodePainting = false;
  }

  public void initRound(titanprime_v4_41 robot) {
    _myLocation = new java.awt.geom.Point2D.Double(_robot.getX(), _robot.getY());
    _robot = robot;
    _scans.clear();
    _lockMode = false;
    _resetTime = 0;
    _lastRadarHeading = _robot.getRadarHeadingRadians();
    _renderables.clear();
    _startDirection = getStartRadarDirection();
  }

  public void execute() {
    _myLocation = new java.awt.geom.Point2D.Double(_robot.getX(), _robot.getY());
    checkScansIntegrity();
    if (_robot.getOthers() == 1 && !_lockMode && !_scans.isEmpty()) {
      setRadarLock((String)_scans.keySet().toArray()[0]);
    }
    directRadar();

    _lastRadarHeading = _robot.getRadarHeadingRadians();
    _myLocation = _predictor.nextLocation(_robot);
  }

  public void onScannedRobot(robocode.ScannedRobotEvent e) {
    java.awt.geom.Point2D.Double enemyLocation = titan_utils_Matematica.project(_myLocation,
        e.getBearingRadians() + _robot.getHeadingRadians(), e.getDistance());

    _scans.put(e.getName(), new RadarScan(_robot.getTime(), enemyLocation));
  }

  public void onRobotDeath(robocode.RobotDeathEvent e) {
    _scans.remove(e.getName());
    if (_targetName != null && _targetName.equals(e.getName())) {
      _lockMode = false;
    }
  }

  public void onPaint(java.awt.Graphics2D g) {
    if (paintStatus()) {
      drawLastKnownBotLocations();
      for (titan_gfx_RoboGraphic r : _renderables) {
        r.render(g);
      }
      _renderables.clear();
    }
  }

  public void directRadar() {
    if (_lockMode && !_scans.containsKey(_targetName)) {
      _out.println("WARNING: Radar locked onto dead or non-existent bot, "
          + "releasing lock.");
      _lockMode = false;
    }

    double radarTurnAmount;
    if (_lockMode &&
      _scans.get(_targetName).lastScanTime == _robot.getTime()) {
      radarTurnAmount = robocode.util.Utils.normalRelativeAngle(titan_utils_Matematica.absoluteBearing(
              _myLocation, _scans.get(_targetName).lastLocation)
          - _robot.getRadarHeadingRadians());
      _radarDirection = titan_utils_Matematica.nonZeroSign(radarTurnAmount);
      radarTurnAmount += _radarDirection * (MAX_RADAR_TRACKING_AMOUNT / 2);
    } else {
      _radarDirection = nextRadarDirection();
      radarTurnAmount = _radarDirection * MAX_RADAR_TRACKING_AMOUNT;
    }
    _robot.setTurnRadarRightRadians(radarTurnAmount);
  }

  public void setRadarLock(String botName) {
    if (_scans.containsKey(botName)) {
      _targetName = botName;
      _lockMode = true;
    }
  }

  public void releaseRadarLock() {
    _lockMode = false;
  }

  public long minTicksToScan(String botName) {
    if (!_scans.containsKey(botName)) { return BOT_NOT_FOUND; }

    double absBearing =
        titan_utils_Matematica.absoluteBearing(_myLocation, _scans.get(botName).lastLocation);
    double shortestAngleToScan = Math.abs(robocode.util.Utils.normalRelativeAngle(
        absBearing - _robot.getRadarHeadingRadians()));
    long minTicks = Math.round(
        Math.ceil(shortestAngleToScan / MAX_RADAR_TRACKING_AMOUNT));

    return minTicks;
  }

  private int getStartRadarDirection() {
    return directionToBearing(
        titan_utils_Matematica.absoluteBearing(_myLocation, _centerField));
  }

  private int nextRadarDirection() {
    if (_scans.isEmpty() || _scans.size() < _robot.getOthers()) {
      return _startDirection;
    }

    String stalestBot = findStalestBotName();
    java.awt.geom.Point2D.Double radarTarget;
    if (minTicksToScan(stalestBot) == 4) {
      radarTarget = _centerField;
    } else {
      radarTarget = _scans.get(findStalestBotName()).lastLocation;
    }

    double absBearingRadarTarget =
        titan_utils_Matematica.absoluteBearing(_myLocation, radarTarget);

    if (justScannedThatSpot(absBearingRadarTarget)) {
      return _radarDirection;
    }

    return directionToBearing(absBearingRadarTarget);
  }

  private int directionToBearing(double bearing) {
    if (robocode.util.Utils.normalRelativeAngle(
            bearing - _robot.getRadarHeadingRadians()) > 0) {
      return 1;
    } else {
      return -1;
    }
  }

  private String findStalestBotName() {
    long oldestTime = Long.MAX_VALUE;
    String botName = null;

    for (String name : _scans.keySet()) {
      if (_scans.get(name).lastScanTime < oldestTime) {
        oldestTime = _scans.get(name).lastScanTime;
        botName = name;
      }
    }

    return botName;
  }

  private void checkScansIntegrity() {
    if (_scans.size() != _robot.getOthers() &&
      _robot.getTime() - _resetTime > 25 &&
      _robot.getOthers() > 0) {
      _scans.clear();
      _lockMode = false;
      _resetTime = _robot.getTime();
      _out.println("WARNING: Radar integrity failure detected (time = "
          + _resetTime + "), resetting.");
    }
  }

  public boolean justScannedThatSpot(double absBearing) {
    if ((titan_utils_Matematica.nonZeroSign(robocode.util.Utils.normalRelativeAngle(
            absBearing - _lastRadarHeading)) == _radarDirection)
        && (titan_utils_Matematica.nonZeroSign(robocode.util.Utils.normalRelativeAngle(
            _robot.getRadarHeadingRadians() - absBearing))
                == _radarDirection)) {
      return true;
    } else {
      return false;
    }
  }

  public void drawLastKnownBotLocations() {
    for (RadarScan scan : _scans.values()) {
      _renderables.addAll(java.util.Arrays.asList(
          titan_gfx_RoboGraphic.drawRectangle(
              scan.lastLocation, BOT_WIDTH, BOT_WIDTH, java.awt.Color.gray)));
    }
  }

  public void paintOn() {
    _painting = ENABLE_DEBUGGING_GRAPHICS;
  }

  public void paintOff() {
    _renderables.clear();
    _painting = false;
  }

  public void robocodePaintOn() {
    _robocodePainting = true;
  }

  public void robocodePaintOff() {
    _renderables.clear();
    _robocodePainting = false;
  }

  public String paintLabel() {
    return "Radar";
  }

  public boolean paintStatus() {
    return (_painting && _robocodePainting);
  }

  public class RadarScan {
    public final long lastScanTime;
    public final java.awt.geom.Point2D.Double lastLocation;

    public RadarScan(long lastScanTime, java.awt.geom.Point2D.Double lastLocation) {
      this.lastScanTime = lastScanTime;
      this.lastLocation = lastLocation;
    }
  }
}

public static class titan_utils_BattleField {
  private static final double HALF_PI = Math.PI / 2;

  public final java.awt.geom.Rectangle2D.Double rectangle;
  public final double width;
  public final double height;

  public titan_utils_BattleField(double width, double height) {
    rectangle = new java.awt.geom.Rectangle2D.Double(18, 18, width - 36, height - 36);
    this.width = width;
    this.height = height;
  }

  public java.awt.geom.Point2D.Double translateToField(java.awt.geom.Point2D.Double p) {
    return new java.awt.geom.Point2D.Double(titan_utils_Matematica.limit(18, p.x, width - 18),
                              titan_utils_Matematica.limit(18, p.y, height - 18));
  }

  public double orbitalWallDistance(java.awt.geom.Point2D.Double sourceLocation,
      java.awt.geom.Point2D.Double targetLocation, double bulletPower, int direction) {
    double absBearing =
        titan_utils_Matematica.absoluteBearing(sourceLocation, targetLocation);
    double distance = sourceLocation.distance(targetLocation);
    double maxEscapeAngle = Math.asin(8.0/(20-3.0*bulletPower));

    double wallDistance = 2.0;
    for (int x = 0; x < 200; x++) {
      if (!rectangle.contains(
          sourceLocation.x + (Math.sin(absBearing +
              (direction*(x/100.0)*maxEscapeAngle))*distance),
          sourceLocation.y + (Math.cos(absBearing +
              (direction*(x/100.0)*maxEscapeAngle))*distance))) {
        wallDistance = x/100.0;
        break;
      }
    }

    return wallDistance;
  }

  public double directToWallDistance(java.awt.geom.Point2D.Double targetLocation,
      double distance, double heading, double bulletPower) {
    int bulletTicks = titan_utils_Matematica.bulletTicksFromPower(distance, bulletPower);
    double wallDistance = 2.0;
    double sinHeading = Math.sin(heading);
    double cosHeading = Math.cos(heading);
    for (int x = 0; x < 2 * bulletTicks; x++) {
      if (!rectangle.contains(
              targetLocation.x + (sinHeading * 8.0 * x),
              targetLocation.y + (cosHeading * 8.0 * x))) {
        wallDistance = ((double) x)/bulletTicks;
        break;
      }
    }

    return wallDistance;
  }

  public double wallSmoothing(java.awt.geom.Point2D.Double startLocation, double startAngle,
      int orientation, double wallStick) {
    double wallDistanceX = Math.min(startLocation.x - 18,
                                    width - startLocation.x - 18);
    double wallDistanceY = Math.min(startLocation.y - 18,
                                    height - startLocation.y - 18);

    if (wallDistanceX > wallStick && wallDistanceY > wallStick) {
      return startAngle;
    }

    double angle = startAngle;
    double testX = startLocation.x + (Math.sin(angle) * wallStick);
    double testY = startLocation.y + (Math.cos(angle) * wallStick);
    double testDistanceX =
        Math.min(testX - 18, width - testX - 18);
    double testDistanceY =
        Math.min(testY - 18, height - testY - 18);

    double adjacent = 0;
    int g = 0;

    while ((testDistanceX < 0 || testDistanceY < 0) && g++ < 25) {
      if (testDistanceY < 0 && testDistanceY < testDistanceX) {

        angle = (testY < 18) ? Math.PI : 0;
        adjacent = wallDistanceY;
      } else if (testDistanceX < 0 && testDistanceX <= testDistanceY) {

        angle = (testX < 18) ? (3 * HALF_PI) : HALF_PI;
        adjacent = wallDistanceX;
      }

      if (adjacent < 0) {
        if (-adjacent > wallStick) {
          wallStick += -adjacent;
        }
        angle += Math.PI -
            orientation * (Math.abs(Math.acos(-adjacent/wallStick)) - 0.0005);
      } else {
        angle +=
            orientation * (Math.abs(Math.acos(adjacent/wallStick)) + 0.0005);
      }
      testX = startLocation.x + (Math.sin(angle) * wallStick);
      testY = startLocation.y + (Math.cos(angle) * wallStick);
      testDistanceX = Math.min(testX - 18, width - testX - 18);
      testDistanceY = Math.min(testY - 18, height - testY - 18);
    }

    return angle;
  }
}

public static class titan_utils_Matematica {
  private static final double HALF_PI = Math.PI / 2;
  private static final double TWO_PI = Math.PI * 2;

  public static java.awt.geom.Point2D.Double project(java.awt.geom.Point2D.Double sourceLocation,
      double angle, double length) {
    return project(sourceLocation, Math.sin(angle), Math.cos(angle), length);
  }

  public static java.awt.geom.Point2D.Double project(java.awt.geom.Point2D.Double sourceLocation,
      double sinAngle, double cosAngle, double length) {
    return new java.awt.geom.Point2D.Double(sourceLocation.x + sinAngle * length,
        sourceLocation.y + cosAngle * length);
  }

  public static double absoluteBearing(java.awt.geom.Point2D.Double sourceLocation,
       java.awt.geom.Point2D.Double target) {
    return Math.atan2(target.x - sourceLocation.x,
        target.y - sourceLocation.y);
  }

  public static int nonZeroSign(double d) {
    if (d < 0) { return -1; }
    return 1;
  }

  public static double square(double d) {
    return d * d;
  }

  public static double cube(double d) {
    return d * d * d;
  }

  public static double power(double d, int exp) {
    double r = 1;
    for (int x = 0; x < exp; x++) {
      r *= d;
    }
    return r;
  }

  public static double limit(double min, double value, double max) {
    return Math.max(min, Math.min(value, max));
  }

  public static int limit(int min, int value, int max) {
    return Math.max(min, Math.min(value, max));
  }

  public static double botWidthAimAngle(double distance) {
    return Math.abs(18.0/distance);
  }

  public static int bulletTicksFromPower(double distance, double power) {
    return (int) Math.ceil(distance / (20 - (3 * power)));
  }

  public static int bulletTicksFromSpeed(double distance, double speed) {
    return (int) Math.ceil(distance / speed);
  }

  public static void setBackAsFront(robocode.AdvancedRobot robot, double goAngle) {
    double angle =
        robocode.util.Utils.normalRelativeAngle(goAngle - robot.getHeadingRadians());

    if (Math.abs(angle) > HALF_PI) {
      if (angle < 0) {
        robot.setTurnRightRadians(Math.PI + angle);
      } else {
        robot.setTurnLeftRadians(Math.PI - angle);
      }
      robot.setBack(100);
    } else {
      if (angle < 0) {
        robot.setTurnLeftRadians(-1 * angle);
      } else {
        robot.setTurnRightRadians(angle);
      }
      robot.setAhead(100);
    }
  }

  public static double rollingAverage(double previousValue, double newValue,
      double depth) {
    return ((previousValue * depth) + newValue) / (depth + 1);
  }

  public static double round(double d, int i) {
    long powerTen = 1;
    for (int x = 0; x < i; x++) {
      powerTen *= 10;
    }
    return ((double) Math.round(d * powerTen)) / powerTen;
  }

  public static double standardDeviation(double[] values) {
    double avg = average(values);
    double sumSquares = 0;
    for (int x = 0; x < values.length; x++) {
      sumSquares += square(avg - values[x]);
    }
    return Math.sqrt(sumSquares / values.length);
  }

  public static double average(double[] values) {
    double sum = 0;
    for (int x = 0; x < values.length; x++) {
      sum += values[x];
    }
    return (sum / values.length);
  }

  public static double accel(double velocity, double previousVelocity) {
    double accel = velocity - previousVelocity;
    if (previousVelocity == 0.0) {
      accel = Math.abs(accel);
    } else {
      accel *= Math.signum(previousVelocity);
    }
    return accel;
  }

  public static double distancePointToBot(
      java.awt.geom.Point2D.Double sourceLocation, titan_utils_RobotState robotState) {
    return distancePointToBot(
        sourceLocation, robotState.location, robotState.botSides());
  }

  public static double distancePointToBot(java.awt.geom.Point2D.Double sourceLocation,
      java.awt.geom.Point2D.Double botLocation, java.util.List<java.awt.geom.Line2D.Double> botSides) {
    if (sourceLocation.x > botLocation.x - 18 &&
        sourceLocation.x < botLocation.x + 18 &&
        sourceLocation.y > botLocation.y - 18 &&
        sourceLocation.y < botLocation.y + 18) {
      return 0;
    } else {
      double distance = Double.POSITIVE_INFINITY;
      for (java.awt.geom.Line2D.Double side : botSides) {
        distance = Math.min(distance, side.ptSegDist(sourceLocation));
      }
      return distance;
    }
  }

  public static double normalizeAngle(double angle, double reference) {
    double normDiff = reference - angle;
    while (Math.abs(normDiff) > Math.PI) {
      angle += Math.signum(normDiff) * TWO_PI;
      normDiff = reference - angle;
    }
    return angle;
  }

  public static double[] generateFiringAngles(int numAngles,
      double maxEscapeAngle) {
    int gfZero = (numAngles-1)/2;
    double[] firingAngles = new double[numAngles];
    for (int x = 0; x < numAngles; x++) {
      firingAngles[x] = (((double)(x - gfZero)) / gfZero)
          * maxEscapeAngle;
    }
    return firingAngles;
  }

  public static double distanceToWall(
      java.awt.geom.Point2D.Double enemyLocation, titan_utils_BattleField battleField) {
    return Math.abs(Math.min(
        Math.min(enemyLocation.x - 18,
                 battleField.width - 18 - enemyLocation.x),
        Math.min(enemyLocation.y - 18,
                 battleField.height - 18 - enemyLocation.y)));
  }

  public static double marginOfError(double probability, int numDataPoints) {
    return 1.96 * Math.sqrt(probability * (1 - probability) / numDataPoints);
  }
}

abstract public static class titan_utils_DistanceFormula {
  public double[] weights;
  abstract public double[] dataPointFromWave(titan_utils_Wave w, boolean aiming);

  public double[] dataPointFromWave(titan_utils_Wave w) {
    return dataPointFromWave(w, false);
  }
}

public static class titan_utils_Enemy<T extends titan_utils_Timestamped> {

  protected static final double DEFAULT_ENERGY = 100;
  protected static final boolean IS_BULLET_HIT = false;
  protected static final boolean IS_VISIT = true;

  public String botName;
  public titan_utils_RobotState lastScanState;
  public double distance;
  public double absBearing;
  public double energy;
  public boolean alive;
  public int lastScanRound;
  public titan_utils_RobotStateLog stateLog;
  public double damageGiven;
  public long timeAliveTogether;

  public java.util.Map<String, titan_utils_KnnView<T>> views;
  protected java.util.Map<String, Double> _botDistancesSq;
  protected titan_utils_WaveManager _waveManager;
  protected titan_utils_BattleField _battleField;
  protected titan_utils_MovementPredictor _predictor;

  protected titan_utils_Enemy(String botName, java.awt.geom.Point2D.Double location, double distance,
      double energy, double heading, double velocity, double absBearing,
      int round, long time, titan_utils_BattleField battleField,
      titan_utils_MovementPredictor predictor, titan_utils_WaveManager waveManager) {
    this.botName = botName;
    this.absBearing = absBearing;
    this.distance = distance;
    this.energy = energy;
    this.lastScanRound = round;
    timeAliveTogether = 1;
    _battleField = battleField;
    _predictor = predictor;
    _waveManager = waveManager;

    damageGiven = 0;
    alive = true;
    views = new java.util.HashMap<String, titan_utils_KnnView<T>>();
    stateLog = new titan_utils_RobotStateLog();
    setRobotState(titan_utils_RobotState.newBuilder()
        .setLocation(location)
        .setHeading(heading)
        .setVelocity(velocity)
        .setTime(time)
        .build());
    _botDistancesSq = new java.util.HashMap<String, Double>();
  }

  public void initRound() {
    energy = DEFAULT_ENERGY;
    distance = 1000;
    alive = true;
    stateLog.clear();
    _waveManager.initRound();
    clearDistancesSq();
  }

  public void addViews(java.util.List<titan_utils_KnnView<T>> views) {
    for (titan_utils_KnnView<T> view : views) {
      addView(view);
    }
  }

  public void addView(titan_utils_KnnView<T> view) {
    views.put(view.name, view);
  }

  public void setRobotState(titan_utils_RobotState robotState) {
    lastScanState = robotState;
    stateLog.addState(robotState);
  }

  public titan_utils_RobotState getState(long time) {
    return stateLog.getState(time);
  }

  public double getBotDistanceSq(String name) {
    if (!_botDistancesSq.containsKey(name)) {
      return Double.NaN;
    }
    return _botDistancesSq.get(name);
  }

  public void setBotDistanceSq(String name, double distance) {
    _botDistancesSq.put(name, distance);
  }

  public void removeDistanceSq(String botName) {
    _botDistancesSq.remove(botName);
  }

  public void clearDistancesSq() {
    _botDistancesSq.clear();
  }
}

public abstract static class titan_utils_EnemyDataManager<T extends titan_utils_Enemy<? extends titan_utils_Timestamped>> {
  static final String WARNING_ROBOT_DEATH_UNKNOWN =
      "A bot died that I never knew existed!";
  protected T _duelEnemy;
  protected int _enemiesTotal;
  protected java.io.PrintStream _out;
  protected java.util.Map<String, T> _enemies;
  protected titan_utils_BattleField _battleField;
  protected titan_utils_MovementPredictor _predictor;
  protected java.util.Collection<titan_gfx_RoboGraphic> _renderables;

  protected titan_utils_EnemyDataManager(int enemiesTotal, titan_utils_BattleField battleField,
      titan_utils_MovementPredictor predictor, java.util.Collection<titan_gfx_RoboGraphic> renderables,
      java.io.OutputStream out) {
    _duelEnemy = null;
    _enemiesTotal = enemiesTotal;
    _battleField = battleField;
    _predictor = predictor;
    _renderables = renderables;
    _out = new java.io.PrintStream(out);
    _enemies = new java.util.HashMap<String, T>(enemiesTotal);
  }

  public boolean hasEnemy(String botName) {
    return _enemies.containsKey(botName);
  }

  public T getEnemyData(String botName) {
    return _enemies.get(botName);
  }

  public java.util.Collection<T> getAllEnemyData() {
    return java.util.Collections.unmodifiableCollection(_enemies.values());
  }

  public void initRound() {
    for (T enemyData : getAllEnemyData()) {
      enemyData.initRound();
    }
    _duelEnemy = null;
  }

  public void onRobotDeath(robocode.RobotDeathEvent e) {
    try {
      getEnemyData(e.getName()).alive = false;
    } catch (NullPointerException npe) {
      _out.println(warning() + WARNING_ROBOT_DEATH_UNKNOWN);
    }
  }

  protected void updateBotDistances(java.awt.geom.Point2D.Double myLocation) {
    if (_enemiesTotal > 1) {
      for (T enemyData : getAllEnemyData()) {
        if (enemyData.alive) enemyData.distance = myLocation.distance(enemyData.lastScanState.location);
      }
    }
    if (_enemies.size() > 1) {
      String[] botNames = getBotNames();
      for (int x = 0; x < botNames.length; x++) {
        T enemyData1 = getEnemyData(botNames[x]);
        for (int y = x + 1; y < botNames.length; y++) {
          T enemyData2 = getEnemyData(botNames[y]);
          if (enemyData1.alive && enemyData2.alive) {
            double distanceSq = enemyData1.lastScanState.location.distanceSq(
                enemyData2.lastScanState.location);
            enemyData1.setBotDistanceSq(botNames[y], distanceSq);
            enemyData2.setBotDistanceSq(botNames[x], distanceSq);
          } else {
            if (!enemyData1.alive) {
              enemyData2.removeDistanceSq(botNames[x]);
            }
            if (!enemyData2.alive) {
              enemyData1.removeDistanceSq(botNames[y]);
            }
          }

        }
      }
    }
  }

  private String[] getBotNames() {
    String[] botNames = new String[_enemies.size()];
    _enemies.keySet().toArray(botNames);
    return botNames;
  }

  public T getClosestLivingBot(java.awt.geom.Point2D.Double location) {
    T closestEnemy = null;
    double closestDistance = Double.POSITIVE_INFINITY;
    for (T enemyData : getAllEnemyData()) {
      if (enemyData.alive) {
        double thisDistance =
            location.distanceSq(enemyData.lastScanState.location);
        if (thisDistance < closestDistance) {
          closestEnemy = enemyData;
          closestDistance = thisDistance;
        }
      }
    }
    return closestEnemy;
  }

  public T duelEnemy() {
    return _duelEnemy;
  }

  protected boolean isMeleeBattle() {
    return (_enemiesTotal > 1);
  }

  protected String warning() {
    return "WARNING (" + getLabel() + "): ";
  }

  abstract protected String getLabel();
}

public static class titan_utils_ErrorLogger {
  private static final String ERROR_LOG = "error.log";
  private static final int MAX_ERROR_SIZE = 5000;
  private static final int CLEAR_SIZE = 50000;

  public static boolean enabled = false;
  private static titan_utils_ErrorLogger _errorLogger;
  private robocode.AdvancedRobot _robot;
  private java.io.File _outFile = null;

  private titan_utils_ErrorLogger(robocode.AdvancedRobot robot) {
    _robot = robot;
    _outFile = robot.getDataFile(ERROR_LOG);
  }

  public static void init(robocode.AdvancedRobot robot) {
    if (_errorLogger == null) {
      _errorLogger = new titan_utils_ErrorLogger(robot);
    } else {
      _errorLogger.setRobot(robot);
    }
  }

  private void setRobot(robocode.AdvancedRobot robot) {
    _robot = robot;
  }

  public void logException(Exception e, String moreInfo) {
    try {
      clearQuota();
      robocode.RobocodeFileWriter rfw = getFileWriter(true);
      java.io.PrintWriter pw = new java.io.PrintWriter(rfw);
      String timestamp =
          java.text.DateFormat.getInstance().format(java.util.Calendar.getInstance().getTime());
      pw.append(timestamp + "\n");
      e.printStackTrace(pw);
      if (moreInfo != null && !moreInfo.equals("")) {
        pw.append("\n");
        pw.append(moreInfo);
        pw.append("\n");
      }
      pw.append("----");
      pw.append("\n");
      pw.close();
      rfw.close();
    } catch (java.io.IOException ioe) {
      ioe.printStackTrace();
    }
  }

  private void clearQuota() throws java.io.IOException {
    if (_outFile.exists() && _robot.getDataQuotaAvailable() < CLEAR_SIZE) {
      String errorFile = readErrorFile();
      errorFile = errorFile.substring(Math.min(CLEAR_SIZE, errorFile.length()));
      robocode.RobocodeFileWriter rfw = getFileWriter(false);
      rfw.write(errorFile);
      rfw.close();
    }
  }

  public void logError(String output) {
    if (enabled && output.length() < MAX_ERROR_SIZE) {
      try {
        clearQuota();
        System.out.println("ERROR:");
        System.out.println("  " + output.replaceAll("[\r\n]+", "\n  "));

        robocode.RobocodeFileWriter rfw = getFileWriter(true);
        String timestamp =
            java.text.DateFormat.getInstance().format(java.util.Calendar.getInstance().getTime());
        rfw.append(timestamp + "\n");
        rfw.append(output);
        rfw.append("\n\n");
        rfw.close();
      } catch (java.io.IOException ioe) {
        ioe.printStackTrace();
      }
    }
  }

  private robocode.RobocodeFileWriter getFileWriter(boolean append)
      throws java.io.IOException {
    if (_outFile.exists()) {
      return new robocode.RobocodeFileWriter(_outFile.getAbsolutePath(), append);
    } else {
      return new robocode.RobocodeFileWriter(_outFile);
    }
  }

  private String readErrorFile() throws java.io.IOException {
    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(_outFile));
    String line = null;
    StringBuilder sb = new StringBuilder();
    String newLine = System.getProperty("line.separator");
    while((line = reader.readLine()) != null) {
      sb.append(line);
      sb.append(newLine);
    }
    reader.close();
    return sb.toString();
  }

  public static titan_utils_ErrorLogger getInstance() {
    if (_errorLogger == null) {
      throw new NullPointerException(
          "You must initialize ErrorLogger before using it.");
    }
    return _errorLogger;
  }
}

public static class titan_utils_Interpolator {
  private final long fireTime;
  private final long beforeTime;
  private final long afterTime;
  private final double weight1;
  private final double weight2;

  public titan_utils_Interpolator(long fireTime, long beforeTime, long afterTime) {
    if (fireTime <= beforeTime || fireTime >= afterTime
        || beforeTime >= afterTime) {
      throw new IllegalArgumentException(
          "Time values must be: beforeTime < fireTime < afterTime");
    }
    this.fireTime = fireTime;
    this.beforeTime = beforeTime;
    this.afterTime = afterTime;
    double rawWeight1 = Math.abs(afterTime - fireTime);
    double rawWeight2 = Math.abs(beforeTime - fireTime);
    this.weight1 = rawWeight1 / (rawWeight1 + rawWeight2);
    this.weight2 = rawWeight2 / (rawWeight1 + rawWeight2);
  }

  public double avg(double val1, double val2) {
    return (val1 * weight1) + (val2 * weight2);
  }

  public java.awt.geom.Point2D.Double getLocation(
      java.awt.geom.Point2D.Double location1, java.awt.geom.Point2D.Double location2) {
    return new java.awt.geom.Point2D.Double(
        avg(location1.x, location2.x), avg(location1.y, location2.y));
  }

  public double getHeading(double heading1, double heading2) {
    return avg(heading1, titan_utils_Matematica.normalizeAngle(heading2, heading1));
  }

  public long getTimer(long val1, long val2) {
    long newVal = val2 - (afterTime - fireTime);
    if (newVal < 0) {
      newVal = val1 + (fireTime - beforeTime);
    }
    return newVal;
  }
}

public static class titan_utils_KnnView<T> {
  private static int _nameIndex = 0;
  public double weight;
  public titan_utils_DistanceFormula formula;
  public int kSize;
  public int kDivisor;
  public int maxDataPoints;
  public boolean logBulletHits;
  public boolean logVisits;
  public boolean logVirtual;
  public boolean logMelee;
  public double hitThreshold;
  public double paddedHitThreshold;
  public double decayRate;
  public String name;

  private titan_KdTree.WeightedSqrEuclid<T> _tree;
  public java.util.Map<Integer, java.util.List<titan_KdTree.Entry<T>>> cachedNeighbors;
  public final java.util.Map<Integer, titan_utils_Wave> cachedNeighborWaves =
      new java.util.HashMap<Integer, titan_utils_Wave>();

  public static final double NO_DECAY = 0;

  public titan_utils_KnnView(titan_utils_DistanceFormula formula) {
    this.formula = formula;
    weight = 1;
    kSize = 1;
    kDivisor = 1;
    logBulletHits = false;
    logVisits = false;
    logVirtual = false;
    logMelee = false;
    hitThreshold = 0;
    paddedHitThreshold = 0;
    maxDataPoints = 0;
    decayRate = NO_DECAY;
    name = Long.toString(Math.round(Math.random() * 10000000))
        + "-" + _nameIndex++;
    initTree();
    cachedNeighbors = new java.util.HashMap<Integer, java.util.List<titan_KdTree.Entry<T>>>();
  }

  private void initTree() {
    _tree = new titan_KdTree.WeightedSqrEuclid<T>(
        formula.weights.length, maxDataPoints == 0 ? null : maxDataPoints);
    _tree.setWeights(formula.weights);
  }

  public titan_utils_KnnView<T> setFormula(titan_utils_DistanceFormula formula) {
    this.formula = formula;
    initTree();
    return this;
  }

  public titan_utils_KnnView<T> setWeight(double weight) {
    this.weight = weight;
    return this;
  }

  public titan_utils_KnnView<T> setK(int kSize) {
    this.kSize = kSize;
    return this;
  }

  public titan_utils_KnnView<T> setKDivisor(int kDivisor) {
    this.kDivisor = kDivisor;
    return this;
  }

  public titan_utils_KnnView<T> bulletHitsOn() {
    logBulletHits = true;
    return this;
  }

  public titan_utils_KnnView<T> visitsOn() {
    logVisits = true;
    return this;
  }

  public titan_utils_KnnView<T> virtualWavesOn() {
    logVirtual = true;
    return this;
  }

  public titan_utils_KnnView<T> meleeOn() {
    logMelee = true;
    return this;
  }

  public titan_utils_KnnView<T> setHitThreshold(double hitThreshold) {
    this.hitThreshold = hitThreshold;
    return this;
  }

  public titan_utils_KnnView<T> setPaddedHitThreshold(double paddedHitThreshold) {
    this.paddedHitThreshold = paddedHitThreshold;
    return this;
  }

  public titan_utils_KnnView<T> setMaxDataPoints(int maxDataPoints) {
    this.maxDataPoints = maxDataPoints;
    initTree();
    return this;
  }

  public titan_utils_KnnView<T> setDecayRate(double decayRate) {
    this.decayRate = decayRate;
    return this;
  }

  public titan_utils_KnnView<T> setName(String name) {
    this.name = name;
    return this;
  }

  public double[] logWave(titan_utils_Wave w, T value) {
    double[] dataPoint = formula.dataPointFromWave(w);
    return logDataPoint(dataPoint, value);
  }

  protected double[] logDataPoint(double[] dataPoint, T value) {
    _tree.addPoint(dataPoint, value);
    return dataPoint;
  }

  public void clearCache() {
    cachedNeighbors.clear();
    cachedNeighborWaves.clear();
  }

  public boolean enabled(double hitPercentage, double marginOfError) {
    return (size() > 0 && (hitPercentage >= hitThreshold)
        && (Math.max(0, hitPercentage - marginOfError) >= paddedHitThreshold));
  }

  public int size() {
    return _tree.size();
  }

  public java.util.List<titan_KdTree.Entry<T>> nearestNeighbors(titan_utils_Wave w, boolean aiming) {
    return nearestNeighbors(
        w, aiming, titan_utils_Matematica.limit(1, size() / kDivisor, kSize));
  }

  public java.util.List<titan_KdTree.Entry<T>> nearestNeighbors(titan_utils_Wave w, boolean aiming, int k) {
    double[] wavePoint = formula.dataPointFromWave(w, aiming);
    return _tree.nearestNeighbor(wavePoint, k, false);
  }

  public void setWeights(double[] weights) {
    formula.weights = weights;
    _tree.setWeights(weights);
  }

  public java.util.Map<titan_utils_Timestamped, Double> getDecayWeights(
      java.util.List<? extends titan_KdTree.Entry<? extends titan_utils_Timestamped>> timestampedEntries) {
    java.util.Map<titan_utils_Timestamped, Double> weightMap = new java.util.HashMap<titan_utils_Timestamped, Double>();
    int numScans = timestampedEntries.size();
    if (decayRate == titan_utils_KnnView.NO_DECAY) {
      for (titan_KdTree.Entry<? extends titan_utils_Timestamped> entry : timestampedEntries) {
        weightMap.put(entry.value, 1.0);
      }
    } else {
      titan_utils_Timestamped[] sorted = new titan_utils_Timestamped[numScans];
      for (int x = 0; x < numScans; x++) {
        sorted[x] = timestampedEntries.get(x).value;
      }
      java.util.Arrays.sort(sorted);
      for (int x = 0; x < numScans; x++) {
        weightMap.put(sorted[x],
            1.0 / titan_utils_Matematica.power(decayRate, numScans - x - 1));
      }
    }
    return weightMap;
  }
}

public static class titan_utils_MaxEscapeTarget {
  public final double angle;
  public final java.awt.geom.Point2D.Double location;
  public final long time;
  public final boolean hitWall;

  public titan_utils_MaxEscapeTarget(
      double angle, java.awt.geom.Point2D.Double location, long time, boolean hitWall) {
    this.angle = angle;
    this.location = location;
    this.time = time;
    this.hitWall = hitWall;
  }
}

public static class titan_utils_MovementPredictor {
  private static final int PRECISE_MEA_ITERATIONS = 3;
  private static final double FULL_SPEED = 8.0;
  private static final double TWO_PI = Math.PI * 2;
  private static final double HALF_PI = Math.PI / 2;
  private static final double QUARTER_PI = Math.PI / 4;

  private final titan_utils_BattleField _battleField;
  private final java.awt.geom.Rectangle2D.Double _rectangle;

  public titan_utils_MovementPredictor(titan_utils_BattleField battleField) {
    _battleField = battleField;
    _rectangle = _battleField.rectangle;
  }

  public titan_utils_RobotState predict(titan_utils_RobotState startState, double distance, double turn,
      double maxVelocity, long ticks, boolean ignoreWalls) {
    titan_utils_RobotState state = startState;
    for (long x = 0; x < ticks; x++) {
      double nextHeading = state.heading;
      double maxTurnRate = Math.abs(robocode.Rules.getTurnRateRadians(state.velocity));
      if (Math.abs(turn) < maxTurnRate) {
        nextHeading += turn;
        turn = 0;
      } else {
        double turnAmount = maxTurnRate * Math.signum(turn);
        nextHeading += turnAmount;
        turn -= turnAmount;
      }

      double nextVelocity =
          getNewVelocity(state.velocity, distance, maxVelocity);
      distance -= nextVelocity;

      java.awt.geom.Point2D.Double nextLocation =
          titan_utils_Matematica.project(state.location, nextHeading, nextVelocity);
      if (!ignoreWalls && !_rectangle.contains(nextLocation)) {
        adjustForWalls(nextLocation, nextHeading);
      }
      state = titan_utils_RobotState.newBuilder()
          .setLocation(nextLocation)
          .setHeading(nextHeading)
          .setVelocity(nextVelocity)
          .setTime(state.time + 1)
          .build();
    }

    return state;
  }

  private void adjustForWalls(java.awt.geom.Point2D.Double location, double heading) {
    double xOut = Math.min(0, _rectangle.getMaxX() - location.x);
    double yOut = Math.min(0, _rectangle.getMaxY() - location.y);
    if (xOut == 0) {
      xOut = Math.max(0, _rectangle.getMinX() - location.x);
    }
    if (yOut == 0) {
      yOut = Math.max(0, _rectangle.getMinY() - location.y);
    }

    double xOffset = xOut;
    double yOffset = yOut;

    while (heading < 0) {
      heading += TWO_PI;
    }
    if (heading % (QUARTER_PI) != 0) {
      double tanHeading = Math.tan(heading);
      if (Math.abs(xOut) > 0) {
        yOffset = xOut / tanHeading;
      }
      if (Math.abs(yOut) > 0) {
        xOffset = yOut * tanHeading;
      }
      if (Math.abs(yOut) > Math.abs(yOffset)) {
        yOffset = yOut;
      }
      if (Math.abs(xOut) > Math.abs(xOffset)) {
        xOffset = xOut;
      }
    }

    location.x += xOffset;
    location.y += yOffset;
  }

  private double getNewVelocity(
      double velocity, double distance, double maxVelocity) {
    if (distance < 0) {

      return -getNewVelocity(-velocity, -distance, maxVelocity);
    }

    final double goalVel;
    if (distance == Double.POSITIVE_INFINITY) {
      goalVel = maxVelocity;
    } else {
      goalVel = Math.min(getMaxVelocity(distance), maxVelocity);
    }

    if (velocity >= 0) {
      return titan_utils_Matematica.limit(velocity - robocode.Rules.DECELERATION,
                            goalVel,
                            velocity + robocode.Rules.ACCELERATION);
    } else {
      return titan_utils_Matematica.limit(velocity - robocode.Rules.ACCELERATION,
                            goalVel,
                            velocity + maxDecel(-velocity));
    }
  }

  private double getMaxVelocity(double distance) {
    final double decelTime =  Math.max(1,Math.ceil(
        (Math.sqrt((4*2/robocode.Rules.DECELERATION)*distance + 1) - 1)/2));

    final double decelDist =
        (decelTime / 2.0) * (decelTime-1)
        * robocode.Rules.DECELERATION;

    return ((decelTime - 1) * robocode.Rules.DECELERATION)
        + ((distance - decelDist) / decelTime);
  }

  private double maxDecel(double velocity) {
    velocity = Math.abs(velocity);
    if (velocity > robocode.Rules.DECELERATION) {
      return robocode.Rules.DECELERATION;
    } else {
      double tickFractionDecel = velocity / robocode.Rules.DECELERATION;
      double tickFractionAccel = 1 - tickFractionDecel;
      return (tickFractionDecel * robocode.Rules.DECELERATION)
           + (tickFractionAccel * robocode.Rules.ACCELERATION);
    }
  }

  public titan_utils_RobotState nextRobotState(titanprime_v4_41 robot) {
    titan_utils_RobotState newRobotState = predict(
        titan_utils_RobotState.newBuilder()
            .setLocation(new java.awt.geom.Point2D.Double(robot.getX(), robot.getY()))
            .setHeading(robot.getHeadingRadians())
            .setVelocity(robot.getVelocity())
            .setTime(robot.getTime())
            .build(),
        robot.getDistanceRemaining(), robot.getTurnRemainingRadians(),
        robot.getMaxVelocity(), 1, false);
    return newRobotState;
  }

  public java.awt.geom.Point2D.Double nextLocation(titanprime_v4_41 robot) {
    return nextRobotState(robot).location;
  }

  public java.awt.geom.Point2D.Double nextLocation(titan_utils_RobotState robotState) {
    return nextLocation(
        robotState.location, robotState.heading, robotState.velocity);
  }

  public java.awt.geom.Point2D.Double nextLocation(java.awt.geom.Point2D.Double botLocation,
      double heading, double velocity) {
    return new java.awt.geom.Point2D.Double(
        botLocation.x + (Math.sin(heading) * velocity),
        botLocation.y + (Math.cos(heading) * velocity));
  }

  public titan_utils_RobotState nextPerpendicularLocation(titan_utils_RobotState robotState,
      double absBearing, int orientation, double attackAngle,
      boolean ignoreWallHits) {
    return nextPerpendicularWallSmoothedLocation(
        robotState, absBearing, 8.0, attackAngle, orientation, 0, ignoreWallHits);
  }

  public titan_utils_RobotState nextPerpendicularWallSmoothedLocation(titan_utils_RobotState robotState,
      double absBearing, double maxVelocity, double attackAngle,
      int orientation, double wallStick, boolean ignoreWallHits) {
    double goAngle = robocode.util.Utils.normalRelativeAngle(
        absBearing + (orientation * (HALF_PI + attackAngle)));
    if (wallStick != 0) {
      goAngle = _battleField.wallSmoothing(
          robotState.location, goAngle, orientation, wallStick);
    }

    return nextLocation(robotState, maxVelocity, goAngle, ignoreWallHits);
  }

  public titan_utils_RobotState nextLocation(titan_utils_RobotState robotState, double maxVelocity,
      double goAngle, boolean ignoreWallHits) {
    double futureTurn = robocode.util.Utils.normalRelativeAngle(goAngle - robotState.heading);
    double futureDistance;
    if (Math.abs(futureTurn) > HALF_PI) {
      futureTurn = futureTurn - (Math.signum(futureTurn) * Math.PI);
      futureDistance = -1000;
    } else {
      futureDistance = 1000;
    }

    return predict(
        robotState, futureDistance, futureTurn, maxVelocity, 1, ignoreWallHits);
  }

  public double escapeAngleRange(java.awt.geom.Point2D.Double sourceLocation,
      long fireTime, double bulletSpeed, titan_utils_RobotState startState,
      double wallStick) {
    return preciseEscapeAngle(1, sourceLocation, fireTime, bulletSpeed,
        startState, wallStick).angle
        + preciseEscapeAngle(-1, sourceLocation, fireTime, bulletSpeed,
            startState, wallStick).angle;
  }

  public titan_utils_MaxEscapeTarget preciseEscapeAngle(int predictDirection,
      java.awt.geom.Point2D.Double sourceLocation, long fireTime, double bulletSpeed,
      titan_utils_RobotState startState, double wallStick) {
    return preciseEscapeAngle(predictDirection, sourceLocation, fireTime,
        bulletSpeed, startState, 0, wallStick);
  }

  public titan_utils_MaxEscapeTarget preciseEscapeAngle(int predictDirection,
      java.awt.geom.Point2D.Double sourceLocation, long fireTime, double bulletSpeed,
      titan_utils_RobotState startState, double attackAngle, double wallStick) {
    double absBearing =
        titan_utils_Matematica.absoluteBearing(sourceLocation, startState.location);

    titan_utils_MaxEscapeTarget straightEscapeTarget = straightPreciseEscapeAngle(
        predictDirection, absBearing, sourceLocation, fireTime, bulletSpeed,
        startState, attackAngle);

    titan_utils_MaxEscapeTarget bestSmoothingTarget = null;
    if (straightEscapeTarget.hitWall) {
      bestSmoothingTarget = smoothingPreciseEscapeAngle(predictDirection,
          absBearing, sourceLocation, fireTime, bulletSpeed, startState,
          attackAngle, wallStick, PRECISE_MEA_ITERATIONS);
    }

    if (bestSmoothingTarget != null
        && bestSmoothingTarget.angle > straightEscapeTarget.angle) {
      return bestSmoothingTarget;
    } else {
      return straightEscapeTarget;
    }
  }

  titan_utils_MaxEscapeTarget straightPreciseEscapeAngle(int predictDirection,
      double absBearing, java.awt.geom.Point2D.Double sourceLocation, long fireTime,
      double bulletSpeed, titan_utils_RobotState startState, double attackAngle) {
    double straightEscapeAngle = 0;
    titan_utils_RobotState predictedState = startState;
    boolean hitWall = false;
    boolean wavePassed = false;
    do {
      predictedState = nextPerpendicularLocation(
          predictedState, absBearing, predictDirection, attackAngle, true);
      if (!_rectangle.contains(predictedState.location)) {
        hitWall = true;
      } else if (wavePassed(
          sourceLocation, fireTime, bulletSpeed, predictedState)) {
        wavePassed = true;
      }
    } while (!hitWall && !wavePassed);

    java.awt.geom.Point2D.Double meaLocation =
        _battleField.translateToField(predictedState.location);
    straightEscapeAngle = predictDirection * robocode.util.Utils.normalRelativeAngle(
        titan_utils_Matematica.absoluteBearing(sourceLocation, meaLocation) - absBearing);
    return new titan_utils_MaxEscapeTarget(
        straightEscapeAngle, meaLocation, predictedState.time, hitWall);
  }

  titan_utils_MaxEscapeTarget smoothingPreciseEscapeAngle(int predictDirection,
      double absBearing, java.awt.geom.Point2D.Double sourceLocation, long fireTime,
      double bulletSpeed, titan_utils_RobotState startState, double attackAngle,
      double wallStick, int iterations) {
    titan_utils_MaxEscapeTarget bestSmoothingTarget = null;
    boolean wavePassed = false;
    bestSmoothingTarget =
        new titan_utils_MaxEscapeTarget(0, startState.location, startState.time, false);

    double goAngle = absBearing + (predictDirection * (HALF_PI + attackAngle));
    goAngle = _battleField.wallSmoothing(
        startState.location, goAngle, predictDirection, wallStick);
    for (int x = 0; x < iterations; x++) {
      wavePassed = false;
      titan_utils_RobotState predictedState = startState;
      do {

        predictedState =
            nextLocation(predictedState, FULL_SPEED, goAngle, true);
        if (wavePassed(
                sourceLocation, fireTime, bulletSpeed, predictedState)) {
          wavePassed = true;
        } else {
          goAngle = _battleField.wallSmoothing(
              predictedState.location, goAngle, predictDirection, wallStick);
        }
      } while (!wavePassed);

      java.awt.geom.Point2D.Double predictedLocation =
          _battleField.translateToField(predictedState.location);
      double thisSmoothingEscapeAngle =
          predictDirection * robocode.util.Utils.normalRelativeAngle(
              titan_utils_Matematica.absoluteBearing(sourceLocation, predictedLocation) - absBearing);
      if (thisSmoothingEscapeAngle > bestSmoothingTarget.angle) {
        bestSmoothingTarget = new titan_utils_MaxEscapeTarget(thisSmoothingEscapeAngle,
            predictedLocation, predictedState.time, false);
      }
      if (x + 1 < iterations) {
        goAngle = titan_utils_Matematica.absoluteBearing(startState.location, predictedLocation);
      }
    }

    return bestSmoothingTarget;
  }

  private boolean wavePassed(java.awt.geom.Point2D.Double sourceLocation,
      long fireTime, double bulletSpeed, titan_utils_RobotState enemyState) {
    double threshold = bulletSpeed * (enemyState.time - fireTime) + bulletSpeed;
    if (enemyState.location.distanceSq(sourceLocation)
            < titan_utils_Matematica.square(threshold) * Math.signum(threshold)) {
      return true;
    } else {
      return false;
    }
  }
}

public static class titan_utils_RobotState {
  private static final double BOT_HALF_WIDTH = 18;

  public final java.awt.geom.Point2D.Double location;
  public final double heading;
  public final double velocity;
  public final long time;
  public final boolean interpolated;
  private java.util.List<java.awt.geom.Point2D.Double> _botCorners;
  private java.awt.geom.Rectangle2D.Double _botRectangle;
  private java.util.List<java.awt.geom.Line2D.Double> _botSides;

  private titan_utils_RobotState(java.awt.geom.Point2D.Double location, double heading, double velocity,
      long time, boolean interpolated) {
    this.location = location;
    this.heading = heading;
    this.velocity = velocity;
    this.time = time;
    this.interpolated = interpolated;

    _botCorners = null;
    _botRectangle = null;
    _botSides = null;
  }

  public java.util.List<java.awt.geom.Point2D.Double> botCorners() {
    if (_botCorners == null) {
      _botCorners = new java.util.ArrayList<java.awt.geom.Point2D.Double>();
      _botCorners.add(new java.awt.geom.Point2D.Double(location.x - BOT_HALF_WIDTH,
                                         location.y - BOT_HALF_WIDTH));
      _botCorners.add(new java.awt.geom.Point2D.Double(location.x - BOT_HALF_WIDTH,
                                         location.y + BOT_HALF_WIDTH));
      _botCorners.add(new java.awt.geom.Point2D.Double(location.x + BOT_HALF_WIDTH,
                                         location.y - BOT_HALF_WIDTH));
      _botCorners.add(new java.awt.geom.Point2D.Double(location.x + BOT_HALF_WIDTH,
                                         location.y + BOT_HALF_WIDTH));
    }
    return _botCorners;
  }

  public java.awt.geom.Rectangle2D.Double botRectangle() {
    if (_botRectangle == null) {
      _botRectangle =
          new java.awt.geom.Rectangle2D.Double(location.x - 18, location.y - 18, 36, 36);
    }
    return _botRectangle;
  }

  public java.util.List<java.awt.geom.Line2D.Double> botSides() {
    if (_botSides == null) {
      _botSides = new java.util.ArrayList<java.awt.geom.Line2D.Double>();
      _botSides.add(new java.awt.geom.Line2D.Double(location.x - 18,
          location.y - 18, location.x + 18, location.y - 18));
      _botSides.add(new java.awt.geom.Line2D.Double(location.x + 18,
          location.y - 18, location.x + 18, location.y + 18));
      _botSides.add(new java.awt.geom.Line2D.Double(location.x + 18,
          location.y + 18, location.x - 18, location.y + 18));
      _botSides.add(new java.awt.geom.Line2D.Double(location.x - 18,
          location.y + 18, location.x - 18, location.y - 18));
    }
    return _botSides;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {
    private java.awt.geom.Point2D.Double location;
    private double heading;
    private double velocity;
    private long time;
    private boolean interpolated;

    public Builder() {
      location = null;
      heading = 0;
      velocity = 0;
      time = -1;
      interpolated = false;
    }

    public Builder setLocation(java.awt.geom.Point2D.Double location) {
      this.location = location;
      return this;
    }

    public Builder setHeading(double heading) {
      this.heading = heading;
      return this;
    }

    public Builder setVelocity(double velocity) {
      this.velocity = velocity;
      return this;
    }

    public Builder setTime(long time) {
      this.time = time;
      return this;
    }

    public Builder setInterpolated(boolean interpolated) {
      this.interpolated = interpolated;
      return this;
    }

    public titan_utils_RobotState build() {
      return new titan_utils_RobotState(location, heading, velocity, time, interpolated);
    }
  }
}

public static class titan_utils_RobotStateLog implements Cloneable {
  private java.util.Map<Long, titan_utils_RobotState> _robotStates;

  public titan_utils_RobotStateLog() {
    _robotStates = new java.util.HashMap<Long, titan_utils_RobotState>();
  }

  public void clear() {
    _robotStates.clear();
  }

  public void addState(titan_utils_RobotState state) {
    _robotStates.put(state.time, state);
  }

  public titan_utils_RobotState getState(long time) {
    return getState(time, true);
  }

  public titan_utils_RobotState getState(long time, boolean interpolate) {
    if (_robotStates.containsKey(time)) {
      titan_utils_RobotState robotState = _robotStates.get(time);
      return (interpolate || !robotState.interpolated) ? robotState : null;
    } else if (interpolate) {
      titan_utils_RobotState beforeState = null;
      titan_utils_RobotState afterState = null;
      for (titan_utils_RobotState state : _robotStates.values()) {
        if (!state.interpolated) {
          if (state.time < time) {
            if (beforeState == null || state.time > beforeState.time) {
              beforeState = state;
            }
          }
          if (state.time > time) {
            if (afterState == null || state.time < afterState.time) {
              afterState = state;
            }
          }
        }
      }

      if (beforeState == null || afterState == null) {
        return null;
      }

      titan_utils_Interpolator interpolator =
          new titan_utils_Interpolator(time, beforeState.time, afterState.time);
      titan_utils_RobotState interpolatedRobotState = titan_utils_RobotState.newBuilder()
          .setLocation(interpolator.getLocation(
              beforeState.location, afterState.location))
          .setHeading(
              interpolator.getHeading(beforeState.heading, afterState.heading))
          .setVelocity(
              interpolator.avg(beforeState.velocity, afterState.velocity))
          .setTime(time)
          .setInterpolated(true)
          .build();
      _robotStates.put(time, interpolatedRobotState);
      return interpolatedRobotState;
    } else {
      return null;
    }
  }

  public double getDisplacementDistance(
      java.awt.geom.Point2D.Double location, long currentTime, long ticksAgo) {
    titan_utils_RobotState pastState = getState(currentTime - ticksAgo);
    pastState = (pastState == null) ? getOldestState() : pastState;
    return location.distance(pastState.location);
  }

  private titan_utils_RobotState getOldestState() {
    return _robotStates.get(java.util.Collections.min(_robotStates.keySet()));
  }

  public void forAllStates(AllStateListener listener) {
    for (titan_utils_RobotState state : _robotStates.values()) {
      listener.onRobotState(state);
    }
  }

  public int size() {
    return _robotStates.size();
  }

  public Object clone() {
    titan_utils_RobotStateLog newLog = new titan_utils_RobotStateLog();
    for (Long time : _robotStates.keySet()) {
      newLog.addState(_robotStates.get(time));
    }
    return newLog;
  }

  public interface AllStateListener {
    void onRobotState(titan_utils_RobotState state);
  }
}

abstract public static class titan_utils_Timestamped implements Comparable<titan_utils_Timestamped>  {
  public final int round;
  public final long time;

  public titan_utils_Timestamped(int roundNum, long time) {
    this.round = roundNum;
    this.time = time;
  }

  @Override
  public int compareTo(titan_utils_Timestamped that) {
    if (this.round < that.round) {
      return -1;
    } else if (this.round == that.round) {
      if (this.time < that.time) {
        return -1;
      } else if (this.time == that.time) {
        return 0;
      } else {
        return 1;
      }
    } else {
      return 1;
    }
  }
}

public static class titan_utils_TimestampedFiringAngle extends titan_utils_Timestamped {
  public final double guessFactor;
  public final java.awt.geom.Point2D.Double displacementVector;

  public titan_utils_TimestampedFiringAngle(int roundNum, long time, double guessFactor,
      java.awt.geom.Point2D.Double displacementVector) {
    super(roundNum, time);
    this.guessFactor = guessFactor;
    this.displacementVector = displacementVector;
  }
}

public static class titan_utils_TimestampedGuessFactor extends titan_utils_Timestamped {
  public double guessFactor;

  public titan_utils_TimestampedGuessFactor(int roundNum, long tick, double guessFactor) {
    super(roundNum, tick);
    this.guessFactor = guessFactor;
  }
}

public static class titan_utils_Wave implements Cloneable {
  public static final double PRECISE_MEA_WALL_STICK = 120;
  public static final java.awt.geom.Point2D.Double ORIGIN = new java.awt.geom.Point2D.Double(0, 0);
  public static final double MAX_BOT_RADIUS = 18 / Math.cos(Math.PI / 4);
  public static final int CLOCKWISE = 1;
  public static final int COUNTERCLOCKWISE = -1;
  public static final boolean FIRING_WAVE = true;
  public static final boolean SURFABLE_WAVE = true;
  public static final boolean ANY_WAVE = false;
  public static final boolean POSITIVE_GUESSFACTOR = true;
  public static final boolean NEGATIVE_GUESSFACTOR = false;
  public static final double ANY_BULLET_POWER = -1;
  public static final int FIRST_WAVE = 0;
  protected static final double BOT_HALF_WIDTH = 18;
  protected static final double TWO_PI = Math.PI * 2;

  public String botName;
  public java.awt.geom.Point2D.Double sourceLocation;
  public java.awt.geom.Point2D.Double targetLocation;
  public double absBearing;
  public int fireRound;
  public long fireTime;
  private double _bulletPower;
  private double _bulletSpeed;
  private double _maxEscapeAngle;
  public int orbitDirection;
  public double targetHeading;
  public double targetRelativeHeading;
  public double targetVelocity;
  protected titan_utils_BattleField _battleField;
  protected titan_utils_MovementPredictor _predictor;

  public boolean hitByBullet;
  public boolean bulletHitBullet;
  public boolean firingWave;
  public boolean altWave;

  public double targetAccel;
  public int targetVelocitySign;
  public double targetDistance;
  public double targetDistanceToNearestBot;
  public long targetDchangeTime;
  public long targetVchangeTime;
  public double targetWallDistance;
  public double targetRevWallDistance;
  public double targetDl8t;
  public double targetDl20t;
  public double targetDl40t;
  public double targetEnergy;
  public double sourceEnergy;
  public double gunHeat;
  public int enemiesAlive;
  public long lastBulletFiredTime;

  public java.util.List<BulletShadow> shadows;
  private java.util.List<TrackedShadow> shadowHistory;
  private java.util.List<BulletShadow> forecastShadows;
  private final java.util.Map<Long, java.util.List<BulletShadow>> shadowDeadlineCache =
      new java.util.HashMap<Long, java.util.List<BulletShadow>>();
  public static long deadlineQueries, deadlineBuilds, deadlineFilteredIntervals;

  public java.util.List<BulletShadow> shadowsBefore(long beforeTime) {
    deadlineQueries++;
    java.util.List<BulletShadow> cached = shadowDeadlineCache.get(beforeTime);
    if (cached != null) return cached;
    deadlineBuilds++;
    java.util.List<BulletShadow> result = new java.util.ArrayList<BulletShadow>();
    if (shadowHistory != null) for (TrackedShadow shadow : shadowHistory) {
      if (shadow.crossing >= beforeTime) { deadlineFilteredIntervals++; continue; }
      if (shadow.bullet == null || shadow.crossing < shadow.bullet.deathTime)
        mergeShadowInto(result, shadow.min, shadow.max);
    }
    if (shadowDeadlineCache.size() >= 32) shadowDeadlineCache.clear();
    shadowDeadlineCache.put(beforeTime, result);
    return result;
  }

  private java.util.List<BulletShadow> comparisonShadows() {
    return forecastShadows == null ? shadows : forecastShadows;
  }


  private static class TrackedShadow {
    final double min, max;
    final titan_gun_FireListener.FiredBullet bullet;
    final long crossing;
    TrackedShadow(double min, double max, titan_gun_FireListener.FiredBullet bullet, long crossing) {
      this.min = min; this.max = max; this.bullet = bullet; this.crossing = crossing;
    }
  }

  protected Double _cachedPositiveEscapeAngle = null;
  protected Double _cachedNegativeEscapeAngle = null;
  public boolean usedNegativeSmoothingMea = false;
  public boolean usedPositiveSmoothingMea = false;

  protected titan_utils_Wave() {

  }

  public titan_utils_Wave(String botName,
      java.awt.geom.Point2D.Double sourceLocation,
      java.awt.geom.Point2D.Double targetLocation,
      int fireRound,
      long fireTime,
      double bulletPower,
      double targetHeading,
      double targetVelocity,
      int targetVelocitySign,
      titan_utils_BattleField battleField,
      titan_utils_MovementPredictor predictor) {
    this.botName = botName;
    this.sourceLocation = sourceLocation;
    this.targetLocation = targetLocation;
    this.fireRound = fireRound;
    this.fireTime = fireTime;
    setBulletPower(bulletPower);
    this.targetHeading = targetHeading;
    this.targetVelocity = targetVelocity;
    this.targetVelocitySign = targetVelocitySign;
    _battleField = battleField;
    _predictor = predictor;
    absBearing = titan_utils_Matematica.absoluteBearing(sourceLocation, targetLocation);

    double relativeHeading = robocode.util.Utils.normalRelativeAngle(
        effectiveHeading() - titan_utils_Matematica.absoluteBearing(sourceLocation, targetLocation));
    orbitDirection = (relativeHeading < 0) ? COUNTERCLOCKWISE : CLOCKWISE;
    targetRelativeHeading = Math.abs(relativeHeading);

    hitByBullet = false;
    bulletHitBullet = false;
    firingWave = false;
    altWave = false;
    shadows = new java.util.ArrayList<BulletShadow>();
  }

  public titan_utils_Wave setAbsBearing(double absBearing) {
    this.absBearing = absBearing;
    return this;
  }

  public titan_utils_Wave setBulletPower(double power) {
    _bulletPower = power;
    _bulletSpeed = (20 - (3 * power));
    _maxEscapeAngle = Math.asin(8.0 / _bulletSpeed);
    clearCachedPreciseEscapeAngles();
    return this;
  }

  public titan_utils_Wave setAccel(double accel) {
    this.targetAccel = accel;
    return this;
  }

  public titan_utils_Wave setDistance(double distance) {
    this.targetDistance = distance;
    return this;
  }

  public titan_utils_Wave setDistanceToNearestBot(double distanceToNearestBot) {
    this.targetDistanceToNearestBot = distanceToNearestBot;
    return this;
  }

  public titan_utils_Wave setDchangeTime(long dChangeTime) {
    this.targetDchangeTime = dChangeTime;
    return this;
  }

  public titan_utils_Wave setVchangeTime(long vChangeTime) {
    this.targetVchangeTime = vChangeTime;
    return this;
  }

  public titan_utils_Wave setDistanceLast8Ticks(double dl8t) {
    this.targetDl8t = dl8t;
    return this;
  }

  public titan_utils_Wave setDistanceLast20Ticks(double dl20t) {
    this.targetDl20t = dl20t;
    return this;
  }

  public titan_utils_Wave setDistanceLast40Ticks(double dl40t) {
    this.targetDl40t = dl40t;
    return this;
  }

  public titan_utils_Wave setTargetEnergy(double energy) {
    this.targetEnergy = energy;
    return this;
  }

  public titan_utils_Wave setSourceEnergy(double energy) {
    this.sourceEnergy = energy;
    return this;
  }

  public titan_utils_Wave setGunHeat(double gunHeat) {
    this.gunHeat = gunHeat;
    return this;
  }

  public titan_utils_Wave setEnemiesAlive(int enemiesAlive) {
    this.enemiesAlive = enemiesAlive;
    return this;
  }

  public titan_utils_Wave setLastBulletFiredTime(long lastBulletFiredTime) {
    this.lastBulletFiredTime = lastBulletFiredTime;
    return this;
  }

  public titan_utils_Wave setAltWave(boolean altWave) {
    this.altWave = altWave;
    return this;
  }

  private titan_utils_Wave setFiringWave(boolean firingWave) {
    this.firingWave = firingWave;
    return this;
  }

  private titan_utils_Wave setHitByBullet(boolean hitByBullet) {
    this.hitByBullet = hitByBullet;
    return this;
  }

  private titan_utils_Wave setBulletHitBullet(boolean bulletHitBullet) {
    this.bulletHitBullet = bulletHitBullet;
    return this;
  }

  public double bulletPower() {
    return _bulletPower;
  }

  public double bulletSpeed() {
    return _bulletSpeed;
  }

  public double maxEscapeAngle() {
    return _maxEscapeAngle;
  }

  public double effectiveHeading() {
    return robocode.util.Utils.normalAbsoluteAngle(targetHeading
        + (targetVelocitySign == 1 ? 0 : Math.PI));
  }

  public double distanceTraveled(long currentTime) {
    return (currentTime - fireTime) * _bulletSpeed;
  }

  public double lateralVelocity() {
    return Math.sin(targetRelativeHeading)
        * (targetVelocitySign * targetVelocity);
  }

  public boolean processedBulletHit() {
    return hitByBullet || bulletHitBullet;
  }

  public void setWallDistances(WallDistanceStyle style) {
    switch (style) {
      case ORBITAL:
        targetWallDistance = orbitalWallDistance(orbitDirection);
        targetRevWallDistance =  orbitalWallDistance(-orbitDirection);
        break;
      case DIRECT:
        targetWallDistance = directToWallDistance(true);
        targetRevWallDistance = directToWallDistance(false);
        break;
      case PRECISE_MEA:
        targetWallDistance =
            preciseEscapeAngle(POSITIVE_GUESSFACTOR) / _maxEscapeAngle;
        targetRevWallDistance =
            preciseEscapeAngle(NEGATIVE_GUESSFACTOR) / _maxEscapeAngle;
        break;
    }
  }

  private double orbitalWallDistance(int orientation) {
    return Math.min(1.5,
        _battleField.orbitalWallDistance(
            sourceLocation, targetLocation, bulletPower(), orientation));
  }

  private double directToWallDistance(boolean forward) {
    return Math.min(1.5,
        _battleField.directToWallDistance(targetLocation,
            sourceLocation.distance(targetLocation),
            effectiveHeading() + (forward ? 0 : Math.PI), bulletPower()));
  }

  public double virtuality() {
    long timeSinceLastBullet = (fireTime - lastBulletFiredTime);
    long timeToNextBullet = Math.round(Math.ceil(gunHeat * 10));

    if (firingWave) {
      return 0;
    } else if (lastBulletFiredTime > 0) {
      return Math.min(timeSinceLastBullet, timeToNextBullet) / 8.0;
    } else {
      return Math.min(1, timeToNextBullet / 8.0);
    }
  }

  public double firingAngle(double guessFactor) {
    return absBearing + (guessFactor * orbitDirection * _maxEscapeAngle);
  }

  public double firingAngleFromTargetLocation(java.awt.geom.Point2D.Double firingTarget) {
    return robocode.util.Utils.normalAbsoluteAngle(
        titan_utils_Matematica.absoluteBearing(sourceLocation, firingTarget));
  }

  public java.awt.geom.Point2D.Double displacementVector(titan_utils_RobotState waveBreakState) {
    return displacementVector(waveBreakState.location, waveBreakState.time);
  }

  public java.awt.geom.Point2D.Double displacementVector(
      java.awt.geom.Point2D.Double botLocation, long time) {
    double vectorBearing = robocode.util.Utils.normalRelativeAngle(
        titan_utils_Matematica.absoluteBearing(targetLocation, botLocation) - effectiveHeading());
    double vectorDistance =
        targetLocation.distance(botLocation) / (time - fireTime);
    return titan_utils_Matematica.project(
        ORIGIN, vectorBearing * orbitDirection, vectorDistance);
  }

  public java.awt.geom.Point2D.Double projectLocationFromDisplacementVector(
      java.awt.geom.Point2D.Double dispVector) {
    return projectLocation(sourceLocation, dispVector, 0);
  }

  public java.awt.geom.Point2D.Double projectLocationBlind(java.awt.geom.Point2D.Double myNextLocation,
      java.awt.geom.Point2D.Double dispVector, long currentTime) {
    return
        projectLocation(myNextLocation, dispVector, currentTime - fireTime + 1);
  }

  java.awt.geom.Point2D.Double projectLocation(java.awt.geom.Point2D.Double firingLocation,
      java.awt.geom.Point2D.Double dispVector, long extraTicks) {
    double dispAngle = effectiveHeading()
        + (titan_utils_Matematica.absoluteBearing(ORIGIN, dispVector) * orbitDirection);
    double dispDistance = ORIGIN.distance(dispVector);

    java.awt.geom.Point2D.Double projectedLocation = targetLocation;
    long bulletTicks = -1;
    long prevBulletTicks = -1;
    long prevPrevBulletTicks;
    double daSin = Math.sin(dispAngle);
    double daCos = Math.cos(dispAngle);

    do {
      prevPrevBulletTicks = prevBulletTicks;
      prevBulletTicks = bulletTicks;
      bulletTicks = titan_utils_Matematica.bulletTicksFromSpeed(
          firingLocation.distance(projectedLocation), _bulletSpeed) - 1;
      projectedLocation = titan_utils_Matematica.project(targetLocation,
          daSin, daCos, (bulletTicks + extraTicks) * dispDistance);
    } while (bulletTicks != prevBulletTicks
             && bulletTicks != prevPrevBulletTicks);

    return projectedLocation;
  }

  public double guessFactor(java.awt.geom.Point2D.Double targetLocation) {
    double bearingToTarget = titan_utils_Matematica.absoluteBearing(sourceLocation, targetLocation);
    return guessFactor(bearingToTarget);
  }

  public double guessFactor(double bearingToTarget) {
    return guessAngle(bearingToTarget) / _maxEscapeAngle;
  }

  public double guessAngle(java.awt.geom.Point2D.Double targetLocation) {
    double bearingToTarget =
        titan_utils_Matematica.absoluteBearing(sourceLocation, targetLocation);
    return guessAngle(bearingToTarget);
  }

  public double guessAngle(double bearingToTarget) {
    return orbitDirection
        * robocode.util.Utils.normalRelativeAngle(bearingToTarget - absBearing);
  }

  public double guessFactorPrecise(java.awt.geom.Point2D.Double targetLocation) {
    double newBearingToTarget = titan_utils_Matematica.absoluteBearing(sourceLocation, targetLocation);
    return guessFactorPrecise(newBearingToTarget);
  }

  public double guessFactorPrecise(double newBearingToTarget) {
    double guessAngle = orbitDirection
        * robocode.util.Utils.normalRelativeAngle(newBearingToTarget - absBearing);
    double maxEscapeAngle = preciseEscapeAngle(guessAngle >= 0);
    return guessAngle / maxEscapeAngle;
  }

  public double preciseEscapeAngle(boolean guessFactorSign) {
    if (guessFactorSign) {
      if (_cachedPositiveEscapeAngle == null) {
        _cachedPositiveEscapeAngle =
            calculatePreciseEscapeAngle(guessFactorSign).angle;
      }
      return _cachedPositiveEscapeAngle;
    } else {
      if (_cachedNegativeEscapeAngle == null) {
        _cachedNegativeEscapeAngle =
            calculatePreciseEscapeAngle(guessFactorSign).angle;
      }
      return _cachedNegativeEscapeAngle;
    }
  }

  public double escapeAngleRange() {
    return (preciseEscapeAngle(POSITIVE_GUESSFACTOR)
        + preciseEscapeAngle(NEGATIVE_GUESSFACTOR));
  }

  public titan_utils_MaxEscapeTarget calculatePreciseEscapeAngle(
      boolean positiveGuessFactor) {
    titan_utils_RobotState startState = titan_utils_RobotState.newBuilder()
        .setLocation((java.awt.geom.Point2D.Double) targetLocation.clone())
        .setHeading(targetHeading)
        .setVelocity(targetVelocity)
        .setTime(fireTime)
        .build();
    return _predictor.preciseEscapeAngle(
        orbitDirection * (positiveGuessFactor ? 1 : -1), sourceLocation,
        fireTime, _bulletSpeed, startState, PRECISE_MEA_WALL_STICK);
  }

  public void clearCachedPreciseEscapeAngles() {
    _cachedPositiveEscapeAngle = null;
    _cachedNegativeEscapeAngle = null;
  }

  public boolean shadowed(double firingAngle) {
    for (BulletShadow shadow : comparisonShadows()) {
      firingAngle = titan_utils_Matematica.normalizeAngle(firingAngle, shadow.minAngle);
      if (firingAngle >= shadow.minAngle && firingAngle <= shadow.maxAngle) {
        return true;
      }
    }
    return false;
  }

  public void castShadow(java.awt.geom.Point2D.Double p1, java.awt.geom.Point2D.Double p2) {
    castShadow(titan_utils_Matematica.absoluteBearing(sourceLocation, p1),
               titan_utils_Matematica.absoluteBearing(sourceLocation, p2));
  }

  private void castShadow(double shadowAngle1, double shadowAngle2) {
    castTrackedShadow(shadowAngle1, shadowAngle2, null, 0);
  }

  public void castShadow(java.awt.geom.Point2D.Double p1, java.awt.geom.Point2D.Double p2,
      titan_gun_FireListener.FiredBullet bullet, long crossing) {
    castTrackedShadow(titan_utils_Matematica.absoluteBearing(sourceLocation, p1),
        titan_utils_Matematica.absoluteBearing(sourceLocation, p2), bullet, crossing);
  }

  private void castTrackedShadow(double angle1, double angle2,
      titan_gun_FireListener.FiredBullet bullet, long crossing) {
    angle1 = titan_utils_Matematica.normalizeAngle(angle1, absBearing);
    angle2 = titan_utils_Matematica.normalizeAngle(angle2, angle1);
    double min = Math.min(angle1, angle2), max = Math.max(angle1, angle2);
    if (shadowHistory == null) shadowHistory = new java.util.ArrayList<TrackedShadow>();
    shadowHistory.add(new TrackedShadow(min, max, bullet, crossing));
    shadowDeadlineCache.clear();
    mergeShadow(min, max);
  }

  public void rebuildBulletShadows() {
    if (shadowHistory == null) return;
    shadowDeadlineCache.clear();
    shadows.clear();
    java.util.Iterator<TrackedShadow> iterator = shadowHistory.iterator();
    while (iterator.hasNext()) {
      TrackedShadow shadow = iterator.next();
      if (shadow.bullet == null || shadow.crossing < shadow.bullet.deathTime)
        mergeShadow(shadow.min, shadow.max);
      else iterator.remove();
    }
  }

  private void mergeShadow(double min, double max) {
    mergeShadowInto(shadows, min, max);
  }

  private static void mergeShadowInto(java.util.List<BulletShadow> target, double min, double max) {
    target.add(new BulletShadow(min, max));

    java.util.Set<BulletShadow> deadShadows = new java.util.HashSet<BulletShadow>();
    for (BulletShadow shadow1 : target) {
      if (!deadShadows.contains(shadow1)) {
        for (BulletShadow shadow2 : target) {
          if (shadow1 != shadow2 && !deadShadows.contains(shadow2)
              && shadow1.overlaps(shadow2)) {
            shadow1.minAngle = Math.min(shadow1.minAngle,
                titan_utils_Matematica.normalizeAngle(shadow2.minAngle, shadow1.minAngle));
            shadow1.maxAngle = Math.max(shadow1.maxAngle,
                titan_utils_Matematica.normalizeAngle(shadow2.maxAngle, shadow1.maxAngle));
            deadShadows.add(shadow2);
          }
        }
      }
    }
    for (BulletShadow deadShadow : deadShadows) {
      target.remove(deadShadow);
    }
  }

  public double shadowFactor(titan_utils_Wave.Intersection intersection) {
    double min = intersection.angle - intersection.bandwidth;
    double max = intersection.angle + intersection.bandwidth;
    double factor = 1;
    for (BulletShadow shadow : comparisonShadows()) {
      double shadowMin = titan_utils_Matematica.normalizeAngle(shadow.minAngle, min);
      double shadowMax = titan_utils_Matematica.normalizeAngle(shadow.maxAngle, shadowMin);
      if (shadowMin <= min && shadowMax >= max) {
        return 0;
      } else if (shadowMin >= min && shadowMin <= max) {
        factor -= (Math.min(max, shadowMax) - shadowMin) / (max - min);
      } else if (shadowMax >= min && shadowMax <= max) {
        factor -= (shadowMax - Math.max(min, shadowMin)) / (max - min);
      }
    }

    return factor;
  }

  public Intersection preciseIntersection(java.util.List<titan_utils_RobotState> waveBreakStates) {
    if (waveBreakStates == null || waveBreakStates.size() == 0) {
      return null;
    }

    java.util.ArrayList<Double> aimAngles = new java.util.ArrayList<Double>();
    for (titan_utils_RobotState waveBreakState : waveBreakStates) {
      java.util.List<java.awt.geom.Point2D.Double> corners = waveBreakState.botCorners();
      titan_utils_geom_Circle waveStart = new titan_utils_geom_Circle(sourceLocation.x, sourceLocation.y,
          _bulletSpeed * (waveBreakState.time - fireTime));
      titan_utils_geom_Circle waveEnd = new titan_utils_geom_Circle(sourceLocation.x, sourceLocation.y,
          _bulletSpeed * (waveBreakState.time - fireTime + 1));

      for (java.awt.geom.Point2D.Double corner : corners) {
        if (waveEnd.contains(corner) && !waveStart.contains(corner)) {
          aimAngles.add(titan_utils_Matematica.absoluteBearing(sourceLocation, corner));
        }
      }

      for (java.awt.geom.Line2D.Double side : waveBreakState.botSides()) {
        titan_utils_geom_LineSeg seg = new titan_utils_geom_LineSeg(side.x1, side.y1, side.x2, side.y2);
        java.awt.geom.Point2D.Double[] intersects = waveStart.intersects(seg);
        for (int x = 0; x < intersects.length; x++) {
          if (intersects[x] != null) {
            aimAngles.add(titan_utils_Matematica.absoluteBearing(sourceLocation, intersects[x]));
          }
        }
        intersects = waveEnd.intersects(seg);
        if (intersects != null) {
          for (int x = 0; x < intersects.length; x++) {
            if (intersects[x] != null) {
              aimAngles.add(titan_utils_Matematica.absoluteBearing(sourceLocation, intersects[x]));
            }
          }
        }
      }
    }

    double normalizeReference = aimAngles.get(0);
    double minAngle = normalizeReference;
    double maxAngle = normalizeReference;

    for (double thisAngle : aimAngles) {
      thisAngle = titan_utils_Matematica.normalizeAngle(thisAngle, normalizeReference);
      maxAngle = Math.max(maxAngle, thisAngle);
      minAngle = Math.min(minAngle, thisAngle);
    }

    double centerAngle = (maxAngle + minAngle) / 2;
    double bandwidth = maxAngle - centerAngle;

    return new Intersection(centerAngle, bandwidth);
  }

  public WavePosition checkWavePosition(titan_utils_RobotState currentState) {
    return checkWavePosition(currentState, false, null);
  }

  public WavePosition checkWavePosition(
      titan_utils_RobotState currentState, boolean skipMidair) {
    return checkWavePosition(currentState, skipMidair, null);
  }

  public WavePosition checkWavePosition(
      titan_utils_RobotState currentState, WavePosition maxPosition) {
    return checkWavePosition(currentState, false, maxPosition);
  }

  public WavePosition checkWavePosition(
      titan_utils_RobotState currentState, boolean skipMidair, WavePosition maxPosition) {
    java.awt.geom.Point2D.Double location = currentState.location;
    double enemyDistSq = sourceLocation.distanceSq(location);
    double endBulletDistance = distanceTraveled(currentState.time + 1);
    if (!skipMidair && (maxPosition == WavePosition.MIDAIR
            || enemyDistSq > titan_utils_Matematica.square(endBulletDistance + MAX_BOT_RADIUS)
            || titan_utils_Matematica.distancePointToBot(sourceLocation, currentState)
                > endBulletDistance)) {
      return WavePosition.MIDAIR;
    } else if (maxPosition == WavePosition.BREAKING_FRONT
               || enemyDistSq > titan_utils_Matematica.square(endBulletDistance)) {
      return WavePosition.BREAKING_FRONT;
    } else {
      if (maxPosition == WavePosition.BREAKING_CENTER){
        return WavePosition.BREAKING_CENTER;
      }
      java.util.List<java.awt.geom.Point2D.Double> corners = currentState.botCorners();
      double startBulletDistance = distanceTraveled(currentState.time);
      for (java.awt.geom.Point2D.Double corner : corners) {
        if (corner.distanceSq(sourceLocation) > titan_utils_Matematica.square(startBulletDistance)) {
          return WavePosition.BREAKING_CENTER;
        }
      }
      return WavePosition.GONE;
    }
  }

  public Object clone() {
    return new titan_utils_Wave(botName, sourceLocation, targetLocation,
        fireRound, fireTime, bulletPower(), targetHeading, targetVelocity,
        targetVelocitySign, _battleField, _predictor)
            .setAbsBearing(absBearing)
            .setAccel(targetAccel)
            .setDistance(targetDistance)
            .setDchangeTime(targetDchangeTime)
            .setVchangeTime(targetVchangeTime)
            .setDistanceLast8Ticks(targetDl8t)
            .setDistanceLast20Ticks(targetDl20t)
            .setDistanceLast40Ticks(targetDl40t)
            .setTargetEnergy(targetEnergy)
            .setSourceEnergy(sourceEnergy)
            .setAltWave(altWave)
            .setFiringWave(firingWave)
            .setHitByBullet(hitByBullet)
            .setBulletHitBullet(bulletHitBullet)
            .setEnemiesAlive(enemiesAlive)
            .setLastBulletFiredTime(lastBulletFiredTime);
  }

  public enum WavePosition {

    MIDAIR(0, false),

    BREAKING_FRONT(1, true),

    BREAKING_CENTER(2, true),

    GONE(3, false);

    private final int _index;
    private final boolean _breaking;

    private WavePosition(int index, boolean breaking) {
      _index = index;
      _breaking = breaking;
    }

    public int getIndex() {
      return _index;
    }

    public boolean isBreaking() {
      return _breaking;
    }
  }

  public static class Intersection {
    public final double angle;
    public final double bandwidth;

    public Intersection(double angle, double bandwidth) {
      this.angle = angle;
      this.bandwidth = bandwidth;
    }
  }

  public static class BulletShadow {
    public double minAngle;
    public double maxAngle;

    public BulletShadow(double minAngle, double maxAngle) {
      this.minAngle = minAngle;
      this.maxAngle = maxAngle;
    }

    public boolean overlaps(BulletShadow that) {
      double thatMinAngle = titan_utils_Matematica.normalizeAngle(that.minAngle, minAngle);
      double thatMaxAngle = titan_utils_Matematica.normalizeAngle(that.maxAngle, thatMinAngle);
      return overlaps(that.minAngle) || overlaps(that.maxAngle)
          || (thatMinAngle <= minAngle && thatMaxAngle >= maxAngle);
    }

    private boolean overlaps(double angle) {
      angle = titan_utils_Matematica.normalizeAngle(angle, minAngle);
      return minAngle <= angle && maxAngle >= angle;
    }
  }

  public enum WallDistanceStyle {
    ORBITAL,
    DIRECT,
    PRECISE_MEA
  }
}

public static class titan_utils_WaveManager {
  private static final int WAVE_MATCH_THRESHOLD = 50;
  private static final double COOLING_RATE = 0.1;
  private static final double MAX_GUN_HEAT = 1.6;

  private java.util.List<titan_utils_Wave> _waves;
  private java.util.Map<titan_utils_Wave, titan_utils_RobotStateLog> _stateLogs;

  public titan_utils_WaveManager() {
    _waves = new java.util.ArrayList<titan_utils_Wave>();
    _stateLogs = new java.util.HashMap<titan_utils_Wave, titan_utils_RobotStateLog>();
  }

  public void initRound() {
    _waves.clear();
    _stateLogs.clear();
  }

  public void addWave(titan_utils_Wave wave) {
    _waves.add(wave);
  }

  public void checkCurrentWaves(
      long currentTime, CurrentWaveListener listener) {
    for (titan_utils_Wave w : _waves) {
      if (w.fireTime == currentTime) {
        listener.onCurrentWave(w);
      }
    }
  }

  public void forAllWaves(AllWaveListener listener) {
    for (titan_utils_Wave w : _waves) {
      listener.onWave(w);
    }
  }

  public void checkActiveWaves(
      long currentTime, titan_utils_RobotState lastScanState, WaveBreakListener listener) {
    if (lastScanState.time == currentTime) {
      java.util.Iterator<titan_utils_Wave> wavesIterator = _waves.iterator();
      while (wavesIterator.hasNext()) {
        titan_utils_Wave w = wavesIterator.next();
        addRobotState(w, lastScanState);
        if (w.checkWavePosition(lastScanState) == titan_utils_Wave.WavePosition.GONE) {
          java.util.List<titan_utils_RobotState> waveBreakStates = getWaveBreakStates(w, currentTime);
          listener.onWaveBreak(w, waveBreakStates);
          wavesIterator.remove();
        }
      }
    }
  }

  void addRobotState(titan_utils_Wave w, titan_utils_RobotState state) {
    titan_utils_RobotStateLog stateLog;
    if (_stateLogs.containsKey(w)) {
      stateLog = _stateLogs.get(w);
    } else {
      stateLog = new titan_utils_RobotStateLog();
      _stateLogs.put(w, stateLog);
    }
    stateLog.addState(state);
  }

  java.util.List<titan_utils_RobotState> getWaveBreakStates(titan_utils_Wave w, long currentTime) {
    java.util.List<titan_utils_RobotState> waveBreakStates = new java.util.ArrayList<titan_utils_RobotState>();
    if (_stateLogs.containsKey(w)) {
      titan_utils_RobotStateLog log = _stateLogs.get(w);
      for (long time = w.fireTime; time < currentTime; time++) {
        titan_utils_RobotState state = log.getState(time);
        if (state != null && w.checkWavePosition(state).isBreaking()) {
          waveBreakStates.add(state);
        }
      }
    }
    return waveBreakStates;
  }

  public titan_utils_Wave findClosestWave(java.awt.geom.Point2D.Double targetLocation, long currentTime,
      boolean onlyFiring, String botName, double bulletPower) {
    double closestDistance = Double.POSITIVE_INFINITY;
    titan_utils_Wave closestWave = null;

    for (titan_utils_Wave w : _waves) {
      if (!w.altWave && (!onlyFiring || w.firingWave)
          && (bulletPower == titan_utils_Wave.ANY_BULLET_POWER
              || Math.abs(bulletPower - w.bulletPower()) < 0.001)
          && (botName == null || botName.equals(w.botName)
              || botName.equals(""))) {
        double targetDistanceSq = w.sourceLocation.distanceSq(targetLocation);
        double waveDistanceTraveled = w.distanceTraveled(currentTime);
        if (targetDistanceSq < titan_utils_Matematica.square(
                waveDistanceTraveled + WAVE_MATCH_THRESHOLD)
            && targetDistanceSq > titan_utils_Matematica.square(
                Math.max(0, waveDistanceTraveled - WAVE_MATCH_THRESHOLD))) {
          double distanceFromTargetToWave =
              Math.sqrt(targetDistanceSq) - waveDistanceTraveled;
          if (Math.abs(distanceFromTargetToWave) < closestDistance) {
            closestDistance = Math.abs(distanceFromTargetToWave);
            closestWave = w;
          }
        }
      }
    }

    return closestWave;
  }

  public titan_utils_Wave findSurfableWave(
      int surfIndex, titan_utils_RobotState targetState, titan_utils_Wave.WavePosition unsurfablePosition) {
    if (surfIndex < 0) return null;

    java.util.List<titan_utils_Wave> first = new java.util.ArrayList<titan_utils_Wave>();
    java.util.List<Double> times = new java.util.ArrayList<Double>();
    for (titan_utils_Wave w : _waves) {
      if (!w.firingWave || w.processedBulletHit()
          || w.checkWavePosition(targetState, unsurfablePosition).getIndex()
              >= unsurfablePosition.getIndex()) continue;
      double time = Math.max(0, w.sourceLocation.distance(targetState.location)
          - w.distanceTraveled(targetState.time)) / w.bulletSpeed();
      if (!Double.isFinite(time)) continue;
      int position = 0;
      while (position < times.size() && times.get(position) <= time) position++;
      if (position <= surfIndex) {
        first.add(position, w); times.add(position, time);
        if (first.size() > surfIndex + 1) {
          first.remove(first.size() - 1); times.remove(times.size() - 1);
        }
      }
    }
    return first.size() > surfIndex ? first.get(surfIndex) : null;
  }

  public titan_utils_Wave getPastWave(int x) {
    return _waves.get(_waves.size() - 1 - x);
  }

  public titan_utils_Wave getWaveByFireTime(long fireTime) {
    for (titan_utils_Wave w : _waves) {
      if (w.fireTime == fireTime) {
        return w;
      }
    }
    return null;
  }

  public titan_utils_Wave interpolateWaveByFireTime(long fireTime, long currentTime,
      double sourceHeading, double sourceVelocity, titan_utils_RobotStateLog stateLog,
      titan_utils_BattleField battleField, titan_utils_MovementPredictor predictor) {
    titan_utils_Wave beforeWave = null;
    titan_utils_Wave afterWave = null;
    for (titan_utils_Wave w : _waves) {
      if (!w.altWave) {
        if (w.fireTime < fireTime) {
          if (beforeWave == null || w.fireTime > beforeWave.fireTime) {
            beforeWave = w;
          }
        }
        if (w.fireTime > fireTime) {
          if (afterWave == null || w.fireTime < afterWave.fireTime) {
            afterWave = w;
          }
        }
      }
    }

    if (beforeWave == null && afterWave == null) {
      return null;
    } else if (beforeWave == null) {
      return interpolateWave(afterWave, fireTime - afterWave.fireTime,
          sourceHeading, sourceVelocity, battleField);
    } else if (afterWave == null) {
      return interpolateWave(beforeWave, fireTime - beforeWave.fireTime,
          sourceHeading, sourceVelocity, battleField);
    } else {
      return interpolateWave(
          beforeWave, afterWave, fireTime, stateLog, battleField, predictor);
    }
  }

  private titan_utils_Wave interpolateWave(titan_utils_Wave baseWave1, titan_utils_Wave baseWave2, long fireTime,
      titan_utils_RobotStateLog stateLog, titan_utils_BattleField battleField,
      titan_utils_MovementPredictor predictor) {
    titan_utils_Interpolator interpolator = new titan_utils_Interpolator(
        fireTime, baseWave1.fireTime, baseWave2.fireTime);

    java.awt.geom.Point2D.Double sourceLocation = interpolator.getLocation(
        baseWave1.sourceLocation, baseWave2.sourceLocation);
    java.awt.geom.Point2D.Double targetLocation = interpolator.getLocation(
        baseWave1.targetLocation, baseWave2.targetLocation);
    double bulletPower =
        interpolator.avg(baseWave1.bulletPower(), baseWave2.bulletPower());
    double targetHeading = interpolator.getHeading(
        baseWave1.targetHeading, baseWave2.targetHeading);
    double targetVelocity =
        interpolator.avg(baseWave1.targetVelocity, baseWave2.targetVelocity);
    int targetVelocitySign;
    if (titan_utils_Matematica.nonZeroSign(targetVelocity)
            == titan_utils_Matematica.nonZeroSign(baseWave1.targetVelocity)) {
      targetVelocitySign = baseWave1.targetVelocitySign;
    } else {
      targetVelocitySign = baseWave2.targetVelocitySign;
    }

    double targetAccel =
        interpolator.avg(baseWave1.targetAccel, baseWave2.targetAccel);
    long targetDchangeTime = interpolator.getTimer(
        baseWave1.targetDchangeTime, baseWave2.targetDchangeTime);
    long targetVchangeTime = interpolator.getTimer(
        baseWave1.targetVchangeTime, baseWave2.targetVchangeTime);
    double targetDl8t =
        stateLog.getDisplacementDistance(targetLocation, fireTime, 8);
    double targetDl20t =
        stateLog.getDisplacementDistance(targetLocation, fireTime, 20);
    double targetDl40t =
        stateLog.getDisplacementDistance(targetLocation, fireTime, 40);
    double targetEnergy =
        interpolator.avg(baseWave1.targetEnergy, baseWave2.targetEnergy);
    double sourceEnergy =
        interpolator.avg(baseWave1.sourceEnergy, baseWave2.sourceEnergy);

    long lastBulletFiredTime = baseWave1.lastBulletFiredTime;
    double gunHeat =
        baseWave1.gunHeat - ((fireTime - baseWave1.fireTime) * COOLING_RATE);
    if (gunHeat <= 0) {
      lastBulletFiredTime = baseWave1.fireTime
          + (long) Math.ceil(baseWave1.gunHeat / COOLING_RATE);
      gunHeat = (baseWave2.gunHeat
              + ((baseWave2.fireTime - fireTime) * COOLING_RATE))
          % MAX_GUN_HEAT;
    }

    return new titan_utils_Wave(baseWave1.botName, sourceLocation, targetLocation,
        baseWave1.fireRound, fireTime, bulletPower, targetHeading,
        targetVelocity, targetVelocitySign, battleField, predictor)
            .setAbsBearing(
                titan_utils_Matematica.absoluteBearing(sourceLocation, targetLocation))
            .setAccel(targetAccel)
            .setDistance(sourceLocation.distance(targetLocation))
            .setDchangeTime(targetDchangeTime)
            .setVchangeTime(targetVchangeTime)
            .setDistanceLast8Ticks(targetDl8t)
            .setDistanceLast20Ticks(targetDl20t)
            .setDistanceLast40Ticks(targetDl40t)
            .setTargetEnergy(targetEnergy)
            .setSourceEnergy(sourceEnergy)
            .setGunHeat(gunHeat)
            .setEnemiesAlive(baseWave2.enemiesAlive)
            .setLastBulletFiredTime(lastBulletFiredTime);
  }

  private titan_utils_Wave interpolateWave(titan_utils_Wave baseWave, long timeOffset,
      double sourceHeading, double sourceVelocity, titan_utils_BattleField battleField) {
    titan_utils_Wave w = (titan_utils_Wave) baseWave.clone();
    w.sourceLocation = battleField.translateToField(
        titan_utils_Matematica.project(baseWave.sourceLocation, sourceHeading,
            sourceVelocity * timeOffset));
    w.targetLocation = battleField.translateToField(
        titan_utils_Matematica.project(baseWave.targetLocation, baseWave.targetHeading,
            baseWave.targetVelocity * timeOffset));
    w.absBearing = titan_utils_Matematica.absoluteBearing(w.sourceLocation, w.targetLocation);
    w.fireTime = w.fireTime + timeOffset;
    w.targetDistance = w.sourceLocation.distance(w.targetLocation);
    return w;
  }

  public long getLastFireTime() {
    long lastFireTime = -1;
    for (titan_utils_Wave w : _waves) {
      if (!w.altWave && w.fireTime > lastFireTime) {
        lastFireTime = w.fireTime;
      }
    }
    return lastFireTime;
  }

  public int size() {
    return _waves.size();
  }

  public interface CurrentWaveListener {
    void onCurrentWave(titan_utils_Wave w);
  }

  public interface WaveBreakListener {
    void onWaveBreak(titan_utils_Wave w, java.util.List<titan_utils_RobotState> waveBreakStates);
  }

  public interface AllWaveListener {
    void onWave(titan_utils_Wave w);
  }
}

public static class titan_utils_genetic_DnaSequence {
  protected java.util.ArrayList<Gene> _geneLayout;
  protected java.util.HashMap<String, Gene> _geneMap;
  protected int _length = 0;

  public titan_utils_genetic_DnaSequence(java.util.ArrayList<Gene> genes) {
    _geneLayout = new java.util.ArrayList<Gene>();
    _geneMap = new java.util.HashMap<String, Gene>();

    if (genes != null) {
      for (Gene g : genes) {
        addGene((Gene)g.clone());
      }
    }
  }

  public titan_utils_genetic_DnaSequence() {
    this(null);
  }

  public void addGene(Gene gene) {

    _geneMap.put(gene.name, gene);
    _geneLayout.add(gene);
    gene.position = _length;
    _length += gene.size;
  }

  public Gene getGene(String name) {
    return _geneMap.get(name);
  }

  public int length() {
    return _length;
  }

  public java.util.ArrayList<Gene> getGeneLayout() {
    return _geneLayout;
  }

  public static class Gene implements Cloneable {
    public final String name;
    public final GeneType type;
    public final long max;
    public final boolean negatives;
    public final int size;
    public int position;

    public Gene(String name, GeneType type, long max, boolean negatives) {
      this(name, type, max, negatives, type.getSize());
    }

    public Gene(String name, GeneType type, long max,
        boolean negatives, int size) {
      if (type == GeneType.NUMBER && size == 0) {
        throw new IllegalArgumentException(
            "Must specify a size for GeneType.NUMBER!");
      }
      this.name = new String(name);
      this.type = type;
      this.max = max;
      this.negatives = negatives;
      this.size = size;
      position = 0;
    }

    public Gene(String name, GeneType type) {
      this(name, type, Long.MAX_VALUE);
    }

    public Gene(String name, GeneType type, long max) {
      this(name, type, max, false);
    }

    public Gene(String name, GeneType type, boolean negatives) {
      this(name, type, Long.MAX_VALUE, negatives);
    }

    public Gene(String name, int numBits) {
      this(name, numBits, Long.MAX_VALUE);
    }

    public Gene(String name, int numBits, long max) {
      this(name, GeneType.NUMBER, max, false, numBits);
    }

    public Object clone() {
      return new Gene(name, type, max, negatives);
    }

    public boolean equals(Object o) {
      if (!(o instanceof Gene)) {
        return false;
      }

      Gene that = (Gene)o;

      if (this.name.equals(that.name) && this.type == that.type
          && this.position == that.position && this.max == that.max
          && this.negatives == that.negatives
          && this.size == that.size) {
        return true;
      } else {
        return false;
      }
    }
  }

  public enum GeneType {
    BIT(1),
    BYTE(8),
    SHORT(16),
    INTEGER(32),
    LONG(64),
    FLOAT(32),
    DOUBLE(64),
    NUMBER(0);

    private int _size;

    private GeneType(int size) {
      _size = size;
    }

    public int getSize() {
      return _size;
    }
  }
}

public static class titan_utils_genetic_DnaString implements Cloneable {

  protected titan_utils_genetic_DnaSequence _dnaSequence;
  protected java.util.BitSet _bits;
  protected int _sourceType;

  public static final int RANDOM = 0;
  public static final int MUTATED = 1;
  public static final int CROSSED = 2;
  public static final int SEEDED = 3;
  public static final int SURVIVED = 4;
  public static final String[] sourceTypes =
      new String[]{"Random", "Mutated", "Crossed", "Seeded", "Survived"};

  public titan_utils_genetic_DnaString(titan_utils_genetic_DnaSequence dnaSequence, java.util.BitSet bits, int sourceType) {
    _dnaSequence = dnaSequence;
    if (bits == null) {
      _bits = new java.util.BitSet(dnaSequence.length());
    } else {
      _bits = (java.util.BitSet)bits.clone();
    }
    _sourceType = sourceType;
  }

  public titan_utils_genetic_DnaString(titan_utils_genetic_DnaSequence dnaSequence, String numString, int sourceType) {
    this(dnaSequence, parseString(numString, dnaSequence.length()), sourceType);
  }

  public titan_utils_genetic_DnaString(titan_utils_genetic_DnaSequence dnaSequence, java.util.BitSet bits) {
    this(dnaSequence, bits, SEEDED);
  }

  public titan_utils_genetic_DnaString(titan_utils_genetic_DnaSequence dnaSequence, String numString) {
    this(dnaSequence, numString, SEEDED);
  }

  public titan_utils_genetic_DnaString(titan_utils_genetic_DnaSequence dnaSequence) {
    this(dnaSequence, new java.util.BitSet(dnaSequence.length()));
  }

  public titan_utils_genetic_DnaSequence getDnaSequence() {
    return _dnaSequence;
  }

  public java.util.BitSet getBitSet() {
    return _bits;
  }

  public int getSourceType() {
    return _sourceType;
  }

  public String getSourceString() {
    return sourceTypes[_sourceType];
  }

  public void setSourceType(int sourceType) {
    _sourceType = sourceType;
  }

  public void setBit(String geneName, boolean value) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.BIT);
    _bits.set(g.position, value);
  }

  public void setByte(String geneName, byte value) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.BYTE);
    set(g.negatives ? g.position : g.position + 1, value,
        g.negatives ? g.size : g.size - 1, g.negatives);
  }

  public void setShort(String geneName, short value) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.SHORT);
    set(g.negatives ? g.position : g.position + 1, value,
        g.negatives ? g.size : g.size - 1, g.negatives);
  }

  public void setInt(String geneName, int value) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.INTEGER);
    set(g.negatives ? g.position : g.position + 1, value,
        g.negatives ? g.size : g.size - 1, g.negatives);
  }

  public void setLong(String geneName, long value) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.LONG);
    set(g.negatives ? g.position : g.position + 1, value,
        g.negatives ? g.size : g.size - 1, g.negatives);
  }

  public void setFloat(String geneName, float value) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.FLOAT);
    set(g.position, Float.floatToIntBits(value), g.size, true);
  }

  public void setDouble(String geneName, double value) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.DOUBLE);
    set(g.position, Double.doubleToLongBits(value), g.size, true);
  }

  public void setNumber(String geneName, long value) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.NUMBER);
    set(g.position, value, g.size, g.negatives);
  }

  private void set(int position, long value, int numBits, boolean negatives) {
    int x = 0;
    long pow = 2;
    while (x < (negatives ? numBits - 1 : numBits)) {
      long rem = value % pow;
      while (rem < 0) {
        rem += pow;
      }
      boolean bit = ((rem >> x) == 1);
      _bits.set(position + numBits - 1 - x, bit);
      x++;
      pow *= 2;
    }
    if (negatives) {
      _bits.set(position, (value < 0));
    }
  }

  public boolean getBit(String geneName) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.BIT);
    return _bits.get(g.position);
  }

  public byte getByte(String geneName) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.BYTE);
    long value = get(g.negatives ? g.position : g.position + 1,
                     g.negatives ? g.size : g.size - 1, g.negatives);
    if (g.max != Long.MAX_VALUE) {
      value = (long) (value % (g.max + 1));
    }
    return (byte) value;
  }

  public short getShort(String geneName) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.SHORT);
    long value = get(g.negatives ? g.position : g.position + 1,
                     g.negatives ? g.size : g.size - 1, g.negatives);
    if (g.max != Long.MAX_VALUE) {
      value = (long) (value % (g.max + 1));
    }
    return (short) value;
  }

  public int getInt(String geneName) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.INTEGER);
    long value = get(g.negatives ? g.position : g.position + 1,
                     g.negatives ? g.size : g.size - 1, g.negatives);
    if (g.max != Long.MAX_VALUE)
      value = (long) (value % (g.max + 1));
    return (int) value;
  }

  public long getLong(String geneName) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.LONG);
    long value = get(g.negatives ? g.position : g.position + 1,
                     g.negatives ? g.size : g.size - 1, g.negatives);
    if (g.max != Long.MAX_VALUE) {
      value = (long) (value % (g.max + 1));
    }
    return value;
  }

  public long getNumber(String geneName) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.NUMBER);
    long value = get(g.position, g.size, g.negatives);
    if (g.max != Long.MAX_VALUE) {
      value = (long) (value % (g.max + 1));
    }
    return value;
  }

  public float getFloat(String geneName) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.FLOAT);
    float f = Float.intBitsToFloat((int) (get(g.position, g.size, true)));
    if (!g.negatives && f < 0) {
      f *= -1;
    }
    if (g.max != Long.MAX_VALUE) {
      f = f % g.max;
    }
    return (float) f;
  }

  public double getDouble(String geneName) {
    titan_utils_genetic_DnaSequence.Gene g = _dnaSequence.getGene(geneName);
    checkGeneType(g, titan_utils_genetic_DnaSequence.GeneType.DOUBLE);
    double d = Double.longBitsToDouble(((get(g.position, g.size, true))));
    if (!g.negatives && d < 0) {
      d *= -1;
    }
    if (g.max != Long.MAX_VALUE) {
      d = d % g.max;
    }
    return d;
  }

  public long get(int position, int numBits, boolean negatives) {
    java.util.BitSet bits = _bits.get(position, position + numBits);
    long value = 0;
    long pow = 1;
    for (int x = 0; x < (negatives ? numBits - 1 : numBits); x++) {
      boolean bit = bits.get(numBits - x - 1);
      if (bit) {
        value += pow;
      }
      pow *= 2;
    }

    if (negatives && bits.get(0)) {
      value -= pow;
    }

    return value;
  }

  private void checkGeneType(titan_utils_genetic_DnaSequence.Gene g, titan_utils_genetic_DnaSequence.GeneType type) {
    if (g.type != type) {
      throw new GeneTypeException("Gene " + g.name + " is not a "
          + type.name().toLowerCase() + "!");
    }
  }

  public static titan_utils_genetic_DnaString mutate(titan_utils_genetic_DnaString string, double mutationRate) {
    titan_utils_genetic_DnaSequence sequence = string.getDnaSequence();
    boolean altered = false;
    java.util.BitSet bits = (java.util.BitSet)string.getBitSet().clone();
    for (int x = 0; x < sequence.length(); x++) {
      if (Math.random() < mutationRate) {
        bits.flip(x);
        altered = true;
      }
    }
    return new titan_utils_genetic_DnaString(sequence, bits,
      (altered ? MUTATED : string.getSourceType()));
  }

  public static titan_utils_genetic_DnaString random(titan_utils_genetic_DnaSequence dnaSequence) {
    java.util.BitSet bits = new java.util.BitSet(dnaSequence.length());
    for (int x = 0; x < dnaSequence.length(); x++) {
      if (Math.random() < 0.5) {
        bits.set(x);
      }
    }
    return new titan_utils_genetic_DnaString(dnaSequence, bits, RANDOM);
  }

  public String bitString() {
    StringBuilder sb = new StringBuilder();
    for (int x = 0; x < _dnaSequence.length(); x++) {
      if (_bits.get(x)) {
        sb.append("1");
      } else {
        sb.append("0");
      }
    }
    return sb.toString();
  }

  public String hexString() {
    StringBuilder sb = new StringBuilder();
    sb.append("0x");
    for (int x = 0; x < _dnaSequence.length(); x += 4) {
      int i = 0;
      int bitVal = 8;
      for (int y = 0; y < Math.min(4, _dnaSequence.length() - x); y++) {
        if (_bits.get(x + y)) {
          i += bitVal;
        }
        bitVal /= 2;
      }
      sb.append(Integer.toHexString(i).toUpperCase());
    }
    return sb.toString();
  }

  public String seedString() {
    return "seed.add(new DnaString(dnaSequence, \"" + hexString() + "\", " +
      _sourceType + "));";
  }

  public static java.util.BitSet parseString(String numString, int length) {
    if (numString.length() > 2 &&
      numString.substring(0, 2).equals("0x")) {
      return parseHexString(numString, length);
    } else {
      return parseBitString(numString);
    }
  }

  public static java.util.BitSet parseBitString(String bitString) {
    java.util.BitSet bits = new java.util.BitSet(bitString.length());
    char[] bitChars = bitString.toCharArray();
    for (int x = 0; x < bitChars.length; x++) {
      if (bitChars[x] == '1') {
        bits.set(x);
      }
    }
    return bits;
  }

  public static java.util.BitSet parseHexString(String hexString, int length) {
    StringBuilder sb = new StringBuilder();
    char[] hexChars = hexString.toCharArray();
    for (int x = 2; x < hexChars.length; x++) {
      int i = Integer.parseInt(Character.toString(hexChars[x]), 16);
      sb.append((i >> 3));
      sb.append(((i >> 2) & 0x1));
      sb.append(((i >> 1) & 0x1));
      sb.append((i & 0x1));
    }

    while (sb.length() > length) {
      sb.deleteCharAt(sb.length()-1);
    }

    return parseBitString(sb.toString());
  }

  public Object clone() {
    return new titan_utils_genetic_DnaString(_dnaSequence, _bits, _sourceType);
  }

  public boolean equals(Object o) {
    if (!(o instanceof titan_utils_genetic_DnaString)) {
      return false;
    }

    titan_utils_genetic_DnaString that = (titan_utils_genetic_DnaString)o;

    if (this.getDnaSequence() != that.getDnaSequence()) {
      return false;
    }

    if (this.getBitSet().equals(that.getBitSet())) {
      return true;
    }

    boolean same = true;
    java.util.ArrayList<titan_utils_genetic_DnaSequence.Gene> geneLayout = getDnaSequence().getGeneLayout();
    for (titan_utils_genetic_DnaSequence.Gene g : geneLayout) {
      switch (g.type) {
        case BIT:
          same = same && (this.getBit(g.name) == that.getBit(g.name));
          break;
        case BYTE:
          same = same && (this.getByte(g.name) == that.getByte(g.name));
          break;
        case SHORT:
          same = same && (this.getShort(g.name) == that.getShort(g.name));
          break;
        case INTEGER:
          same = same && (this.getInt(g.name) == that.getInt(g.name));
          break;
        case LONG:
          same = same && (this.getLong(g.name) == that.getLong(g.name));
          break;
        case FLOAT:
          same = same && (Math.abs(this.getFloat(g.name)
              - that.getFloat(g.name)) < 0.0000001);
          break;
        case DOUBLE:
          same = same && (Math.abs(this.getDouble(g.name)
              - that.getDouble(g.name)) < 0.00000000001);
        case NUMBER:
          same = same && (this.getNumber(g.name) == that.getNumber(g.name));
          break;
      }

      if (!same) {
        break;
      }
    }

    return same;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    java.util.ArrayList<titan_utils_genetic_DnaSequence.Gene> geneLayout = _dnaSequence.getGeneLayout();
    boolean newLine = false;
    for (titan_utils_genetic_DnaSequence.Gene g : geneLayout) {
      if (newLine) {
        sb.append("\n");
      }
      newLine = true;

      sb.append(g.name);
      sb.append(": ");
      switch (g.type) {
        case BIT:
          sb.append(getBit(g.name)); break;
        case BYTE:
          sb.append(getByte(g.name)); break;
        case SHORT:
          sb.append(getShort(g.name)); break;
        case INTEGER:
          sb.append(getInt(g.name)); break;
        case LONG:
          sb.append(getLong(g.name)); break;
        case FLOAT:
          sb.append(getFloat(g.name)); break;
        case DOUBLE:
          sb.append(getDouble(g.name)); break;
      }
    }

    return sb.toString();
  }

  public class GeneTypeException extends RuntimeException {
    private static final long serialVersionUID = -881296837849502009L;
    public GeneTypeException(String message) {
      super(message);
    }
  }
}

public static class titan_utils_geom_Circle {
    public double h;
    public double k;
    public double r;

    public titan_utils_geom_Circle(double x, double y, double r) {
        this.h = x;
        this.k = y;
        this.r = r;
    }

    public titan_utils_geom_Circle(java.awt.geom.Point2D.Double p, double r) {
        this(p.x, p.y, r);
    }

    public java.awt.geom.Point2D.Double[] intersects(titan_utils_geom_LineSeg seg) {
        return intersects(seg, false);
    }

    public java.awt.geom.Point2D.Double[] intersects(titan_utils_geom_LineSeg seg, boolean inverted) {
        double a = (seg.m * seg.m) + 1;
        double b = 2 * ((seg.b * seg.m) - (k * seg.m) - h);
        double c = (h * h) + (k * k) + (seg.b * seg.b) - (2 * seg.b * k) - (r * r);

        java.awt.geom.Point2D.Double[] solutions = new java.awt.geom.Point2D.Double[]{null, null};
        int i = 0;

        if (a == Double.POSITIVE_INFINITY) {
            titan_utils_geom_LineSeg invSeg = new titan_utils_geom_LineSeg(seg.y1, seg.x1, seg.y2, seg.x2);
            titan_utils_geom_Circle invCircle = new titan_utils_geom_Circle(k, h, r);
            java.awt.geom.Point2D.Double[] invSolutions =
                invCircle.intersects(invSeg, true);

            for (int x = 0; x < invSolutions.length; x++) {
                if (invSolutions[x] != null) {
                    double t = invSolutions[x].x;
                    invSolutions[x].x = invSolutions[x].y;
                    invSolutions[x].y = t;
                }
            }

            return invSolutions;
        }

        double discrim = (b * b) - (4 * a * c);
        if (discrim < 0) {
            return solutions;
        }

        double sqrtDiscrim = Math.sqrt(discrim);
        double x1 = (-b + sqrtDiscrim) / (2 * a);
        double y1 = (seg.m * x1) + seg.b;

        if (x1 > seg.xMin && x1 < seg.xMax) {
            solutions[i++] = new java.awt.geom.Point2D.Double(x1, y1);
        }

        if (sqrtDiscrim > 0) {
            double x2 = (-b - sqrtDiscrim) / (2 * a);
            double y2 = (seg.m * x2) + seg.b;
            if (x2 > seg.xMin && x2 < seg.xMax) {
                solutions[i++] = new java.awt.geom.Point2D.Double(x2, y2);
            }
        }

        return solutions;
    }

    public boolean contains(java.awt.geom.Point2D.Double p) {
        double z = titan_utils_Matematica.square(p.x - h) + titan_utils_Matematica.square(p.y - k);
        return (z < r * r);
    }
}

public static class titan_utils_geom_LineSeg {
  public double m;
  public double b;
  public double xMin;
  public double xMax;
  public double yMin;
  public double yMax;
  public double x1;
  public double y1;
  public double x2;
  public double y2;

  public titan_utils_geom_LineSeg(double x1, double y1, double x2, double y2) {
    if (x1 == x2) {
      m = Double.POSITIVE_INFINITY;
      b = Double.NaN;
      xMin = xMax = x1;
    } else {
      m = (y2 - y1) / (x2 - x1);
      b = y1 - (m * x1);
      xMin = Math.min(x1, x2);
      xMax = Math.max(x1, x2);
    }
    yMin = Math.min(y1, y2);
    yMax = Math.max(y1, y2);

    this.x1 = x1;
    this.y1 = y1;
    this.x2 = x2;
    this.y2 = y2;
  }

  public titan_utils_geom_LineSeg(java.awt.geom.Point2D.Double p1, java.awt.geom.Point2D.Double p2) {
    this(p1.x, p1.y, p2.x, p2.y);
  }

  public java.awt.geom.Point2D.Double intersects(titan_utils_geom_LineSeg seg) {
    if (m == seg.m || (b == Double.NaN && seg.b == Double.NaN)) {
      return null;
    } else if (b == Double.NaN || seg.b == Double.NaN) {
      titan_utils_geom_LineSeg seg1 = new titan_utils_geom_LineSeg(y1, x1, y2, x2);
      titan_utils_geom_LineSeg seg2 = new titan_utils_geom_LineSeg(seg.y1, seg.x1, seg.y2, seg.x2);
      java.awt.geom.Point2D.Double inverse = seg1.intersects(seg2);
      return new java.awt.geom.Point2D.Double(inverse.y, inverse.x);
    }

    double x = (seg.b - this.b) / (this.m - seg.m);
    double y = m * x + b;
    return new java.awt.geom.Point2D.Double(x, y);
  }
}

  private static final boolean EXPORT_NEURAL_SAMPLES = false;
  private static final String NEURAL_INITIAL = "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";
  private static final java.util.Map<String, TitanNeuralPair> NEURAL_MODELS =
      new java.util.HashMap<String, TitanNeuralPair>();

  private static TitanNeuralPair neuralFor(String name) {
    TitanNeuralPair pair = NEURAL_MODELS.get(name);
    if (pair == null) { pair = new TitanNeuralPair(); NEURAL_MODELS.put(name, pair); }
    return pair;
  }

  private static double nnClamp(double lo, double v, double hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  private static double[] neuralFeatures(titan_utils_Wave w) {
    double flight = Math.max(1, w.targetDistance / w.bulletSpeed());
    return new double[] {
      w.bulletPower() / 3, Math.min(1.5, flight / 70),
      w.targetVelocity * w.targetVelocitySign / 8,
      Math.sin(w.targetRelativeHeading), Math.cos(w.targetRelativeHeading),
      (w.targetAccel + 2) / 3,
      nnClamp(0, w.targetWallDistance, 1.5), nnClamp(0, w.targetRevWallDistance, 1.5),
      Math.min(1.5, w.targetVchangeTime / flight), Math.min(1.5, w.targetDchangeTime / flight),
      Math.min(1.5, w.targetDl8t / 64), Math.min(1.5, w.targetDl20t / 160),
      Math.min(1.5, w.targetDl40t / 320)
    };
  }

  private static void neuralVisit(titan_utils_Wave w, double gf, boolean visit) {
    if (!visit || w.enemiesAlive != 1 || w.altWave || (!w.firingWave && w.fireTime % 6 != 0)) return;
    if (!Double.isFinite(gf)) return;
    double[] features = neuralFeatures(w);
    for (double f : features) if (!Double.isFinite(f)) return;
    gf = nnClamp(-1, gf, 1);
    double sigma = nnClamp(.045, 18 / Math.max(36, w.targetDistance)
        / Math.max(.08, w.preciseEscapeAngle(gf >= 0)), .22);
    TitanNeuralPair pair = neuralFor(w.botName);
    double weight = w.firingWave ? 1 : .06;
    pair.slow.learn(features, gf, sigma, .016 * weight);
    pair.fast.learn(features, gf, sigma, .065 * weight);
    if (w.firingWave && pair.realSamples < Long.MAX_VALUE) pair.realSamples++;
    if (EXPORT_NEURAL_SAMPLES && w.firingWave) {
      StringBuilder line = new StringBuilder("NEURAL_SAMPLE ");
      line.append(w.fireRound).append(' ').append(gf).append(' ').append(sigma).append(' ');
      for (int i = 0; i < features.length; i++) {
        if (i > 0) line.append(','); line.append(features[i]);
      }
      System.out.println(line);
    }
  }

  private static class TitanNeuralPair {
    final TitanNeural slow = new TitanNeural(47017);
    final TitanNeural fast = new TitanNeural(47017);
    long realSamples;
  }

  private static class TitanNeural {
    static final int INPUTS = 13, HIDDEN = 20, OUTPUTS = 61;
    final double[][] first = new double[HIDDEN][INPUTS + 1];
    final double[][] second = new double[OUTPUTS][HIDDEN + 1];
    TitanNeural(long seed) {
      java.util.Random random = new java.util.Random(seed);
      for (double[] row : first) for (int i = 0; i < INPUTS; i++) row[i] = random.nextGaussian() * .22;
      for (double[] row : second) for (int i = 0; i < HIDDEN; i++) row[i] = random.nextGaussian() * .05;
      if (!NEURAL_INITIAL.isEmpty()) load(NEURAL_INITIAL);
    }
    double[] hidden(double[] input) {
      double[] h = new double[HIDDEN];
      for (int i = 0; i < HIDDEN; i++) {
        double value = first[i][INPUTS];
        for (int j = 0; j < INPUTS; j++) value += first[i][j] * input[j];
        h[i] = Math.tanh(value);
      }
      return h;
    }
    double[] distribution(double[] h) {
      double[] y = new double[OUTPUTS]; double max = -Double.MAX_VALUE, sum = 0;
      for (int i = 0; i < OUTPUTS; i++) {
        double value = second[i][HIDDEN];
        for (int j = 0; j < HIDDEN; j++) value += second[i][j] * h[j];
        max = Math.max(max, y[i] = value);
      }
      for (int i = 0; i < OUTPUTS; i++) sum += y[i] = Math.exp(y[i] - max);
      for (int i = 0; i < OUTPUTS; i++) y[i] /= sum;
      return y;
    }
    double[] predict(double[] input) { return distribution(hidden(input)); }
    void learn(double[] input, double gf, double sigma, double rate) {
      double[] h = hidden(input), output = distribution(h), target = new double[OUTPUTS];
      double[] dh = new double[HIDDEN]; double total = 0;
      for (int i = 0; i < OUTPUTS; i++) {
        double z = ((i / 30.0 - 1) - gf) / sigma;
        total += target[i] = Math.exp(-.5 * z * z);
      }
      for (int i = 0; i < OUTPUTS; i++) {
        double delta = output[i] - target[i] / total;
        for (int j = 0; j < HIDDEN; j++) dh[j] += delta * second[i][j];
        for (int j = 0; j < HIDDEN; j++) second[i][j] -= rate * delta * h[j];
        second[i][HIDDEN] -= rate * delta;
      }
      for (int i = 0; i < HIDDEN; i++) {
        double gradient = nnClamp(-2, dh[i] * (1 - h[i] * h[i]), 2);
        for (int j = 0; j < INPUTS; j++) first[i][j] -= rate * gradient * input[j];
        first[i][INPUTS] -= rate * gradient;
      }
    }
    void load(String packed) {
      try {
        java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(
            java.util.Base64.getDecoder().decode(packed)));
        if (in.readInt() != 0x4E4E4731 || in.readInt() != INPUTS || in.readInt() != HIDDEN || in.readInt() != OUTPUTS)
          throw new java.io.IOException("neural dimensions");
        for (double[] row : first) for (int i = 0; i < row.length; i++) row[i] = in.readFloat();
        for (double[] row : second) for (int i = 0; i < row.length; i++) row[i] = in.readFloat();
        in.close();
      } catch (java.io.IOException e) { throw new IllegalArgumentException(e); }
    }
  }

  private static int neuralAngularIndex(double[] probabilities, double positiveEscape,
      double negativeEscape, double angularWidth) {
    double[] offsets = new double[probabilities.length];
    for (int i = 0; i < offsets.length; i++) {
      double factor = i / 30.0 - 1;
      offsets[i] = factor * (factor >= 0 ? positiveEscape : negativeEscape);
    }
    double best = -1;
    int selected = 30;
    for (int i = 0; i < offsets.length; i++) {
      double score = 0;
      for (int j = 0; j < offsets.length; j++) {
        double delta = Math.abs(offsets[i] - offsets[j]) / angularWidth;
        if (delta < 1) score += probabilities[j] * (1 - delta);
      }
      if (score > best) { best = score; selected = i; }
    }
    return selected;
  }

  private static class TitanNeuralGun extends titan_gun_DuelGun<titan_utils_TimestampedFiringAngle> {
    final boolean recent;
    TitanNeuralGun(boolean recent) { this.recent = recent; }
    public String getLabel() { return recent ? "Titan Neural Recent" : "Titan Neural General"; }
    protected double aimInternal(titan_utils_Wave w, boolean painting) {
      TitanNeuralPair pair = neuralFor(w.botName);
      double[] probabilities = (recent ? pair.fast : pair.slow).predict(neuralFeatures(w));
      double angularWidth = Math.max(.04 * w.maxEscapeAngle(),
          18 / Math.max(36, w.targetDistance));
      int index = neuralAngularIndex(probabilities, w.preciseEscapeAngle(true),
          w.preciseEscapeAngle(false), angularWidth);
      double gf = index / 30.0 - 1;
      return robocode.util.Utils.normalAbsoluteAngle(w.absBearing
          + w.orbitDirection * gf * w.preciseEscapeAngle(gf >= 0));
    }
    public java.util.List<titan_utils_KnnView<titan_utils_TimestampedFiringAngle>> newDataViews() {
      return java.util.Collections.emptyList();
    }
  }

  private static String[] embeddedMemoryChunks() {
    return new String[] {
      "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"
    };
  }

  private static final int MEMORY_MAGIC = 0x54504D31;
  private static final int MEMORY_SCHEMA = 2;
  private static final int MEMORY_LIMIT = 70000;
  private static final int MEMORY_EXPANDED_LIMIT = 8 * 1024 * 1024;
  private static final java.util.Map<String, MemoryRecord> MEMORY_RECORDS = new java.util.HashMap<String, MemoryRecord>();
  private static final java.util.Map<String, Long> MEMORY_SEEN = new java.util.HashMap<String, Long>();
  private static boolean memoryLoaded;
  private static long memoryGeneration;
  private volatile boolean roundFinished;
  private boolean memorySaved;

  private static class MemoryRecord {
    String name;
    long seen;
    TitanNeuralPair neural;
    java.util.Map<String, double[]> guns = new java.util.HashMap<String, double[]>();
    java.util.List<float[]> hits = new java.util.ArrayList<float[]>();
    java.util.List<float[]> visits = new java.util.ArrayList<float[]>();
    int shots, timesHit;
    double weightedHits;
  }

  private static class MemorySnapshot {
    long generation;
    java.util.Map<String, MemoryRecord> records = new java.util.HashMap<String, MemoryRecord>();
  }

  private static void rememberWave(titan_utils_Wave w, double gf, java.util.LinkedList<float[]> samples) {
    double[] raw = {w.bulletPower(), w.targetDistance, w.targetVelocity, w.targetVelocitySign,
        w.targetRelativeHeading, w.targetAccel, w.targetWallDistance, w.targetRevWallDistance,
        w.targetVchangeTime, w.targetDchangeTime, w.targetDl8t, w.targetDl20t, w.targetDl40t, gf};
    float[] sample = new float[raw.length];
    for (int i = 0; i < raw.length; i++) {
      sample[i] = (float) raw[i];
      if (!Float.isFinite(sample[i]) || Math.abs(sample[i]) > 1000000) return;
    }
    samples.addLast(sample);
    if (samples.size() > 24) {
      int[] counts = new int[3];
      for (float[] item : samples) counts[powerStratum(item[0])]++;
      int largest = Math.max(counts[0], Math.max(counts[1], counts[2]));
      java.util.Iterator<float[]> iterator = samples.iterator();
      while (iterator.hasNext()) {
        float[] item = iterator.next();
        if (counts[powerStratum(item[0])] == largest) { iterator.remove(); break; }
      }
    }
  }

  private static int powerStratum(float power) {
    return power < .3f ? 0 : power < 1.5f ? 1 : 2;
  }

  private static void restoreMoveMemory(titan_move_MoveEnemy enemy) {
    MemoryRecord saved = MEMORY_RECORDS.get(enemy.botName);
    if (saved == null) return;
    replaySamples(enemy, saved.hits, true);
    replaySamples(enemy, saved.visits, false);
    enemy.memoryHits.addAll(saved.hits);
    enemy.memoryVisits.addAll(saved.visits);
    enemy.raw1v1ShotsFired = saved.shots;
    enemy.raw1v1ShotsHit = saved.timesHit;
    enemy.weighted1v1ShotsHit = saved.weightedHits;
  }

  private static void replaySamples(titan_move_MoveEnemy enemy, java.util.List<float[]> samples, boolean hit) {
    int time = 0;
    for (float[] a : samples) {
      titan_utils_BattleField field = new titan_utils_BattleField(800, 600);
      titan_utils_Wave w = new titan_utils_Wave(enemy.botName,
          new java.awt.geom.Point2D.Double(400, 100), new java.awt.geom.Point2D.Double(400, 100 + a[1]),
          -1, time, a[0], a[4], a[2], (int) a[3], field, new titan_utils_MovementPredictor(field));
      w.targetDistance = a[1]; w.targetRelativeHeading = a[4]; w.targetAccel = a[5];
      w.targetWallDistance = a[6]; w.targetRevWallDistance = a[7];
      w.targetVchangeTime = (long) a[8]; w.targetDchangeTime = (long) a[9];
      w.targetDl8t = a[10]; w.targetDl20t = a[11]; w.targetDl40t = a[12];
      for (titan_utils_KnnView<titan_utils_TimestampedGuessFactor> view : enemy.views.values()) {
        if (hit ? view.logBulletHits : view.logVisits)
          view.logWave(w, new titan_utils_TimestampedGuessFactor(-1, time, a[13]));
      }
      time++;
    }
  }

  private void loadBattleMemory() {
    if (memoryLoaded) return;
    memoryLoaded = true;
    MemorySnapshot seed = readEmbeddedMemory();
    MemorySnapshot local = null;
    for (String name : new String[] {"memoria-a.dat", "memoria-b.dat"}) {
      MemorySnapshot candidate = readMemory(getDataFile(name));
      if (candidate != null && (local == null || candidate.generation > local.generation)) local = candidate;
    }
    MemorySnapshot combined = combineMemory(seed, local);
    memoryGeneration = combined.generation;
    MEMORY_RECORDS.putAll(combined.records);
    for (MemoryRecord record : combined.records.values()) {
      NEURAL_MODELS.put(record.name, record.neural);
      MEMORY_SEEN.put(record.name, record.seen);
      _gun._virtualGuns.restoreRatings(record.name, record.guns);
    }
    out.println("TITAN_MEMORY embedded=" + (seed == null ? 0 : seed.records.size())
        + " local=" + (local == null ? 0 : local.records.size())
        + " loaded=" + MEMORY_RECORDS.size() + " generation=" + memoryGeneration);
  }

  private static MemorySnapshot combineMemory(MemorySnapshot seed, MemorySnapshot local) {
    MemorySnapshot combined = new MemorySnapshot();
    if (seed != null) {
      combined.generation = seed.generation;
      combined.records.putAll(seed.records);
    }
    if (local != null) {
      combined.generation = Math.max(combined.generation, local.generation);
      combined.records.putAll(local.records);
    }
    return combined;
  }

  private MemorySnapshot readEmbeddedMemory() {
    try {
      StringBuilder encoded = new StringBuilder();
      for (String chunk : embeddedMemoryChunks()) encoded.append(chunk);
      if (encoded.length() > ((MEMORY_LIMIT + 2) / 3) * 4) return null;
      return decodeBattleMemory(java.util.Base64.getDecoder().decode(encoded.toString()));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private MemorySnapshot readMemory(java.io.File file) {
    if (!file.exists()) return null;
    try {
      if (file.length() < 64 || file.length() > MEMORY_LIMIT) return null;
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
      try (java.io.InputStream input = new java.io.FileInputStream(file)) {
        byte[] buffer = new byte[2048]; int count;
        while ((count = input.read(buffer)) != -1) {
          if (bytes.size() + count > MEMORY_LIMIT) return null;
          bytes.write(buffer, 0, count);
        }
      }
      return decodeBattleMemory(bytes.toByteArray());
    } catch (java.io.IOException | IllegalArgumentException e) {
      return null;
    }
  }

  private MemorySnapshot decodeBattleMemory(byte[] data) {
    if (data == null || data.length < 64 || data.length > MEMORY_LIMIT) return null;
    try {
      java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(data, 0, data.length - 8);
      java.io.DataInputStream checksum = new java.io.DataInputStream(new java.io.ByteArrayInputStream(data, data.length - 8, 8));
      if (checksum.readLong() != crc.getValue()) return null;
      int schema = ((data[4] & 255) << 24) | ((data[5] & 255) << 16) | ((data[6] & 255) << 8) | (data[7] & 255);
      byte[] payload = java.util.Arrays.copyOf(data, data.length - 8);
      if (schema == 2) {
        java.io.ByteArrayOutputStream expanded = new java.io.ByteArrayOutputStream();
        expanded.write(data, 0, 56);
        try (java.io.InputStream compressed = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(data, 56, data.length - 64))) {
          byte[] buffer = new byte[2048]; int count;
          while ((count = compressed.read(buffer)) != -1) {
            if (expanded.size() + count > MEMORY_EXPANDED_LIMIT) return null;
            expanded.write(buffer, 0, count);
          }
        }
        payload = expanded.toByteArray();
      } else if (schema != 1) return null;
      java.io.DataInputStream input = new java.io.DataInputStream(new java.io.ByteArrayInputStream(payload));
      if (input.readInt() != MEMORY_MAGIC) return null;
      input.readInt();
      if (input.readInt() != TitanNeural.INPUTS || input.readInt() != TitanNeural.HIDDEN
          || input.readInt() != TitanNeural.OUTPUTS) return null;
      double width = input.readDouble(), height = input.readDouble(), cooling = input.readDouble();
      if (!Double.isFinite(width) || !Double.isFinite(height) || !Double.isFinite(cooling)
          || Math.abs(width - getBattleFieldWidth()) > .01
          || Math.abs(height - getBattleFieldHeight()) > .01
          || Math.abs(cooling - getGunCoolingRate()) > .000001) return null;
      MemorySnapshot snapshot = new MemorySnapshot(); snapshot.generation = input.readLong();
      if (snapshot.generation < 0) return null;
      int size = input.readInt(); if (size < 0 || size > payload.length / 12500) return null;
      for (int i = 0; i < size; i++) {
        MemoryRecord record = new MemoryRecord(); record.name = input.readUTF();
        if (record.name.isEmpty() || record.name.length() > 160 || snapshot.records.containsKey(record.name)) return null;
        record.seen = input.readLong(); record.neural = new TitanNeuralPair();
        record.neural.realSamples = input.readLong();
        if (record.neural.realSamples < 0) return null;
        readNetwork(input, record.neural.slow); readNetwork(input, record.neural.fast);
        int guns = input.readInt(); if (guns < 0 || guns > 8) return null;
        for (int g = 0; g < guns; g++) {
          String label = input.readUTF(); double hits = input.readDouble(); int shots = input.readInt();
          if (label.length() > 80 || !Double.isFinite(hits) || hits < 0 || shots < 0 || shots > 128) return null;
          record.guns.put(label, new double[] {hits, shots});
        }
        record.hits = readSamples(input); record.visits = readSamples(input);
        record.shots = input.readInt(); record.timesHit = input.readInt(); record.weightedHits = input.readDouble();
        if (record.shots < 0 || record.shots > 128 || record.timesHit < 0 || record.timesHit > record.shots
            || !Double.isFinite(record.weightedHits) || record.weightedHits < 0) return null;
        snapshot.records.put(record.name, record);
      }
      if (input.available() != 0) return null;
      return snapshot;
    } catch (java.io.IOException | IllegalArgumentException e) {
      return null;
    }
  }

  private static void readNetwork(java.io.DataInputStream input, TitanNeural net) throws java.io.IOException {
    for (double[][] matrix : new double[][][] {net.first, net.second})
      for (double[] row : matrix) for (int i = 0; i < row.length; i++) {
        float value = input.readFloat();
        if (!Float.isFinite(value) || Math.abs(value) > 10000) throw new java.io.IOException("pesos invalidos");
        row[i] = value;
      }
  }

  private static java.util.List<float[]> readSamples(java.io.DataInputStream input) throws java.io.IOException {
    int count = input.readInt(); if (count < 0 || count > 24) throw new java.io.IOException("amostras invalidas");
    java.util.List<float[]> values = new java.util.ArrayList<float[]>();
    for (int n = 0; n < count; n++) {
      float[] sample = new float[14];
      for (int i = 0; i < sample.length; i++) {
        sample[i] = input.readFloat();
        if (!Float.isFinite(sample[i]) || Math.abs(sample[i]) > 1000000) throw new java.io.IOException("amostra invalida");
      }
      if (sample[0] < .099 || sample[0] > 3.001 || sample[1] < 0 || Math.abs(sample[2]) > 8.001
          || Math.abs(sample[3]) != 1) throw new java.io.IOException("contexto invalido");
      values.add(sample);
    }
    return values;
  }

  private void saveBattleMemory() {
    if (!memoryLoaded || memorySaved) return;
    try {
      java.util.Set<String> names = new java.util.HashSet<String>(MEMORY_RECORDS.keySet());
      names.addAll(NEURAL_MODELS.keySet()); names.addAll(observedEnemies);
      java.util.List<MemoryRecord> records = new java.util.ArrayList<MemoryRecord>();
      for (String name : names) {
        if (name.isEmpty() || name.length() > 160) continue;
        MemoryRecord old = MEMORY_RECORDS.get(name), record = new MemoryRecord(); record.name = name;
        record.seen = MEMORY_SEEN.containsKey(name) ? MEMORY_SEEN.get(name) : old == null ? 0 : old.seen;
        record.neural = NEURAL_MODELS.get(name);
        if (record.neural == null) record.neural = old == null ? new TitanNeuralPair() : old.neural;
        record.guns = _gun._virtualGuns.snapshotRatings(name);
        if (record.guns.isEmpty() && old != null) record.guns = old.guns;
        if (_move._moveDataManager.hasEnemy(name)) {
          titan_move_MoveEnemy enemy = _move._moveDataManager.getEnemyData(name);
          record.hits.addAll(enemy.memoryHits); record.visits.addAll(enemy.memoryVisits);
          int shots = Math.min(128, enemy.raw1v1ShotsFired); double scale = shots / (double) Math.max(1, enemy.raw1v1ShotsFired);
          record.shots = shots; record.timesHit = Math.min(shots, (int) Math.round(enemy.raw1v1ShotsHit * scale));
          record.weightedHits = enemy.weighted1v1ShotsHit * scale;
        } else if (old != null) { record.hits = old.hits; record.visits = old.visits; record.shots = old.shots; record.timesHit = old.timesHit; record.weightedHits = old.weightedHits; }
        if (record.neural.realSamples > 0 || !record.hits.isEmpty() || !record.visits.isEmpty()) records.add(record);
      }
      java.util.Collections.sort(records, new java.util.Comparator<MemoryRecord>() {
        public int compare(MemoryRecord a, MemoryRecord b) { int rank = Long.compare(b.seen, a.seen); return rank != 0 ? rank : a.name.compareTo(b.name); }
      });
      if (records.isEmpty()) return;
      long next = memoryGeneration + 1; java.io.File file = getDataFile((next & 1) == 1 ? "memoria-a.dat" : "memoria-b.dat");
      java.io.File diagnostic = getDataFile("diagnostico.csv");
      long reserve = 4096 + Math.max(0, 48000 - diagnostic.length());
      long budget = Math.min(MEMORY_LIMIT, file.length() + getDataQuotaAvailable() - reserve);
      int low = 1, high = records.size(), retained = 0; byte[] data = null;
      while (low <= high) {
        int middle = low + (high - low) / 2;
        byte[] candidate = encodeMemory(records.subList(0, middle), next);
        if (candidate.length <= budget) { retained = middle; data = candidate; low = middle + 1; }
        else high = middle - 1;
      }
      if (data == null) { out.println("TITAN_MEMORY skipped=quota"); return; }
      records = new java.util.ArrayList<MemoryRecord>(records.subList(0, retained));
      try (java.io.OutputStream output = new robocode.RobocodeFileOutputStream(file)) { output.write(data); }
      memoryGeneration = next; memorySaved = true;
      MEMORY_RECORDS.clear(); for (MemoryRecord record : records) MEMORY_RECORDS.put(record.name, record);
      out.println("TITAN_MEMORY saved=" + records.size() + " generation=" + next + " bytes=" + data.length);
    } catch (java.io.IOException | IllegalArgumentException e) { out.println("TITAN_MEMORY skipped=write"); }
  }

  private byte[] encodeMemory(java.util.List<MemoryRecord> records, long generation) throws java.io.IOException {
    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    java.io.DataOutputStream output = new java.io.DataOutputStream(bytes);
    output.writeInt(MEMORY_MAGIC); output.writeInt(MEMORY_SCHEMA);
    output.writeInt(TitanNeural.INPUTS); output.writeInt(TitanNeural.HIDDEN); output.writeInt(TitanNeural.OUTPUTS);
    output.writeDouble(getBattleFieldWidth()); output.writeDouble(getBattleFieldHeight()); output.writeDouble(getGunCoolingRate());
    output.writeLong(generation); output.writeInt(records.size());
    for (MemoryRecord record : records) {
      output.writeUTF(record.name); output.writeLong(record.seen); output.writeLong(record.neural.realSamples);
      for (TitanNeural net : new TitanNeural[] {record.neural.slow, record.neural.fast})
        for (double[][] matrix : new double[][][] {net.first, net.second})
          for (double[] row : matrix) for (double value : row) {
            float packed = (float) value;
            if (!Float.isFinite(packed) || Math.abs(packed) > 10000) throw new java.io.IOException("peso invalido");
            output.writeFloat(packed);
          }
      output.writeInt(record.guns.size());
      for (java.util.Map.Entry<String, double[]> entry : record.guns.entrySet()) {
        output.writeUTF(entry.getKey()); output.writeDouble(entry.getValue()[0]); output.writeInt((int) entry.getValue()[1]);
      }
      for (java.util.List<float[]> samples : java.util.Arrays.asList(record.hits, record.visits)) {
        output.writeInt(samples.size()); for (float[] sample : samples) for (float value : sample) output.writeFloat(value);
      }
      output.writeInt(record.shots); output.writeInt(record.timesHit); output.writeDouble(record.weightedHits);
    }
    output.flush(); byte[] raw = bytes.toByteArray(); output.close();
    java.io.ByteArrayOutputStream packed = new java.io.ByteArrayOutputStream(); packed.write(raw, 0, 56);
    try (java.util.zip.GZIPOutputStream compressed = new java.util.zip.GZIPOutputStream(packed)) { compressed.write(raw, 56, raw.length - 56); }
    java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(packed.toByteArray());
    java.io.DataOutputStream trailer = new java.io.DataOutputStream(packed); trailer.writeLong(crc.getValue()); trailer.close();
    return packed.toByteArray();
  }

  static class EnergyBalance {
    long lastTime = -1, streak;
    String lastEnemy;
    double lastEnergy, lastSpent, lastReceived, lastBonus, totalDrain;
    int lastCollisions;
    double observe(long time, int others, String enemy, double energy, double spent,
        double received, double bonus, int collisions) {
      if (time == lastTime) return 0;
      long elapsed = time - lastTime;
      double result = 0;
      double residual = lastEnergy - energy - (spent - lastSpent)
          - (received - lastReceived) + (bonus - lastBonus);
      if (lastTime >= 0 && others == 1 && enemy.equals(lastEnemy) && elapsed > 0 && elapsed <= 4
          && energy > .1 && collisions == lastCollisions && Math.abs(residual - .1 * elapsed) < .00001) {
        streak += elapsed;
        if (streak >= 3) { result = .1 * elapsed; totalDrain += result; }
      } else streak = 0;
      lastTime = time; lastEnemy = enemy; lastEnergy = energy; lastSpent = spent;
      lastReceived = received; lastBonus = bonus; lastCollisions = collisions;
      return result;
    }
  }

  private static final boolean CALIBRATE_STRONG_SHOTS = true;

  static double comebackPower(double current, double cap, double energy, double enemyEnergy) {
    if (!Double.isFinite(current) || !Double.isFinite(cap) || !Double.isFinite(energy)
        || !Double.isFinite(enemyEnergy) || energy < 0 || enemyEnergy < 0) return .1;
    double selected = Math.min(current, cap);
    if (cap > .7) selected = Math.max(selected, Math.min(cap, energy / 12));
    selected = Math.min(selected, enemyEnergy / 4);
    return Math.min(energy, Math.max(.1, selected));
  }

  static double pressureAccuracyCap(double cap, int resolved, int hits) {
    if (!CALIBRATE_STRONG_SHOTS) return cap;
    if (!Double.isFinite(cap)) return .1;
    if (resolved < 0 || hits < 0 || hits > resolved) return Math.min(cap, .7);
    if (cap > .7 && resolved >= 6 && (hits + 1.0) / (resolved + 8.0) < .18)
      return .7;
    return cap;
  }

  static double pressureFireCap(double distance, double energy, double enemyEnergy,
      double enemyPower, int shots, int hits, double range) {
    double baseline = precisionFireCap(distance, energy, shots, hits, .7);
    if (!Double.isFinite(enemyEnergy) || !Double.isFinite(enemyPower)
        || !Double.isFinite(range) || range < 100 || range > 525
        || !Double.isFinite(distance) || !Double.isFinite(energy)
        || distance < 0 || distance > range || energy < 8 || enemyEnergy <= 8
        || enemyPower < 1.5 || enemyPower > 3 || shots < 20 || hits < 0 || hits > shots) return baseline;
    double precision = (hits + 1.0) / (shots + 8.0);
    return precision >= .21 ? 1.95 : baseline;
  }

  private static final boolean POWER_CONTEXT_DEFENSE = true;

  static int powerContext(double power) { return power < .3 ? 0 : power < 1.5 ? 1 : 2; }

  static double weightedHitMargin(int shots, int hits, double weightedHits) {
    if (shots <= 0 || hits < 0 || hits > shots || !Double.isFinite(weightedHits) || weightedHits < 0) return 100;
    double estimate = weightedHits / shots;
    if (shots < 4 || hits == 0) return 100 * estimate;
    double probability = hits / (double) shots, z = 1.96, z2 = z * z;
    double lower = (probability + z2 / (2.0 * shots)
        - z * Math.sqrt(probability * (1 - probability) / shots + z2 / (4.0 * shots * shots)))
        / (1 + z2 / shots);
    return 100 * Math.max(0, estimate - weightedHits / hits * Math.max(0, lower));
  }

  static double precisionFireCap(double distance, double energy, int shots, int hits, double maximum) {
    if (!Double.isFinite(distance) || !Double.isFinite(energy) || distance < 0
        || hits < 0 || hits > shots || !Double.isFinite(maximum) || maximum < .1 || maximum > 3) return .1;
    double precision = (hits + 1.0) / (shots + 8.0);
    if (distance >= 525 || energy < 25 || shots < 10 || precision < .18) return .1;
    return maximum;
  }

}
