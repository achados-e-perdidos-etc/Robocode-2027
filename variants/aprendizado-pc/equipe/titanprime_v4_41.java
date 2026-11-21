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
  private static final String ROBOT_DISPLAY_NAME = "titanprime" + DIAGNOSTIC_VERSION;
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
