from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
API = None
ECJ = None
PROBE = r'''package equipe;
import java.awt.geom.Point2D;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class meleeupgradeprobe {
  static final titanprime.titan_utils_BattleField FIELD = new titanprime.titan_utils_BattleField(800, 600);
  static final titanprime.titan_utils_MovementPredictor PREDICTOR = new titanprime.titan_utils_MovementPredictor(FIELD);
  static final ArrayList<titanprime.titan_gfx_RoboGraphic> GRAPHICS = new ArrayList<titanprime.titan_gfx_RoboGraphic>();

  static class Dummy extends titanprime {
    int others;
    Dummy(int initialOthers) { others = initialOthers; }
    public int getOthers() { return others; }
    public double getBattleFieldWidth() { return 800; }
    public double getBattleFieldHeight() { return 600; }
    public long getTime() { return 1; }
    public double getEnergy() { return 100; }
  }

  static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  static titanprime.titan_gun_GunDataManager manager(int enemies) {
    return new titanprime.titan_gun_GunDataManager(enemies, FIELD, PREDICTOR, GRAPHICS, System.out);
  }

  static titanprime.titan_gun_GunEnemy enemy(String name, double x, double y, double heading, double velocity) {
    return new titanprime.titan_gun_GunEnemy(name, 999, 100, new Point2D.Double(x, y),
        0, 1, heading, velocity, 0, FIELD, PREDICTOR, GRAPHICS);
  }

  static void projection() {
    int fixtures = 0;
    for (double heading : new double[] {0, .73, 1.4, 2.8, 5.4}) {
      for (double velocity : new double[] {-8, 0, 6.5, 8}) {
        titanprime.titan_gun_GunDataManager manager = manager(2);
        titanprime.titan_gun_GunEnemy target = enemy("target", 550, 330, .75, 6);
        titanprime.titan_gun_GunEnemy alternate = enemy("alternate", 240, 180, heading, velocity);
        manager.saveEnemy(target.botName, target);
        manager.saveEnemy(alternate.botName, alternate);
        final ArrayList<titanprime.titan_utils_Wave> waves = new ArrayList<titanprime.titan_utils_Wave>();
        Point2D.Double ourNextLocation = new Point2D.Double(405, 305);
        manager.fireNextTickWave(ourNextLocation, target.lastScanState.location, target.botName,
            0, 1, 1.999, 100, 0, .5, 8, 2);
        target.getWaveManager().forAllWaves(new titanprime.titan_utils_WaveManager.AllWaveListener() {
          public void onWave(titanprime.titan_utils_Wave wave) { waves.add(wave); }
        });
        check(waves.size() == 2, "projection_fixture_must_create_real_and_alternate_wave");
        int alternateCount = 0;
        for (titanprime.titan_utils_Wave wave : waves) {
          Point2D.Double expectedTarget = new Point2D.Double(550 + Math.sin(.75) * 6, 330 + Math.cos(.75) * 6);
          check(wave.targetLocation.distance(expectedTarget) < 1e-9, "projection_target_control_changed");
          check(wave.fireTime == 2, "projection_fire_time_control_changed");
          if (wave.altWave) {
            alternateCount++;
            Point2D.Double expectedSource = new Point2D.Double(240 + Math.sin(heading) * velocity,
                180 + Math.cos(heading) * velocity);
            check(wave.sourceLocation.distance(expectedSource) < 1e-9,
                "alternate_source_heading_velocity_order: heading=" + heading + " velocity=" + velocity
                + " expected=" + expectedSource + " actual=" + wave.sourceLocation);
            check(Math.abs(wave.targetDistance - expectedSource.distance(expectedTarget)) < 1e-9,
                "alternate_wave_distance_must_use_projected_source");
          } else {
            check(wave.sourceLocation.distance(ourNextLocation) < 1e-9, "our_wave_source_control_changed");
          }
        }
        check(alternateCount == 1, "projection_fixture_must_create_one_alternate_wave");
        fixtures++;
      }
    }
    System.out.println("ALTERNATE_SOURCE_PROJECTION_PASS fixtures=" + fixtures);
  }

  static void distances(boolean single) {
    titanprime.titan_gun_GunDataManager manager = manager(3);
    manager.saveEnemy("first", enemy("first", 100, 100, 0, 0));
    if (!single) {
      manager.saveEnemy("middle", enemy("middle", 200, 200, 0, 0));
      manager.saveEnemy("last", enemy("last", 700, 500, 0, 0));
    }
    for (Point2D.Double ourLocation : new Point2D.Double[] {
        new Point2D.Double(400, 300), new Point2D.Double(640, 460)}) {
      manager.updateBotDistances(ourLocation);
      for (titanprime.titan_gun_GunEnemy enemy : manager.getAllEnemyData()) {
        double expected = ourLocation.distance(enemy.lastScanState.location);
        check(Math.abs(enemy.distance - expected) < 1e-9,
            (single ? "single_enemy_distance_refresh" : "all_enemy_distance_refresh")
            + ": " + enemy.botName + " expected=" + expected + " actual=" + enemy.distance);
      }
    }
    System.out.println((single ? "SINGLE" : "ALL") + "_ENEMY_DISTANCE_REFRESH_PASS positions=2");
  }

  static void transition() throws Exception {
    Field duelEnemy = titanprime.titan_utils_EnemyDataManager.class.getDeclaredField("_duelEnemy");
    duelEnemy.setAccessible(true);
    Field currentGun = titanprime.titan_gun_Mira.class.getDeclaredField("_currentGun");
    currentGun.setAccessible(true);
    Field perceptualGun = titanprime.titan_gun_Mira.class.getDeclaredField("_perceptualGun");
    perceptualGun.setAccessible(true);
    Method evaluate = titanprime.titan_gun_Mira.class.getDeclaredMethod("evaluateVirtualGuns", titanprime.titan_gun_GunEnemy.class);
    evaluate.setAccessible(true);
    for (int initialOthers : new int[] {1, 3}) {
      Dummy robot = new Dummy(initialOthers);
      titanprime.titan_gun_GunDataManager manager = manager(initialOthers);
      titanprime.titan_gun_GunEnemy target = enemy("survivor", 550, 330, .75, 6);
      manager.saveEnemy(target.botName, target);
      duelEnemy.set(manager, target);
      titanprime.titan_gun_VirtualGunsManager<titanprime.titan_utils_TimestampedFiringAngle> virtualGuns =
          new titanprime.titan_gun_VirtualGunsManager<titanprime.titan_utils_TimestampedFiringAngle>(System.out);
      titanprime.titan_gun_Mira mira = new titanprime.titan_gun_Mira(robot, false, manager,
          virtualGuns, FIELD, GRAPHICS, System.out);
      check(manager.getDuelDataSize() == 0, "transition_fixture_must_have_no_duel_samples");
      Object initialPerceptual = perceptualGun.get(mira);
      check((initialPerceptual != null) == (initialOthers == 1), "transition_fixture_initial_perceptual_state");
      robot.others = 1;
      evaluate.invoke(mira, target);
      @SuppressWarnings("unchecked")
      titanprime.titan_gun_DuelGun<titanprime.titan_utils_TimestampedFiringAngle> selected =
          (titanprime.titan_gun_DuelGun<titanprime.titan_utils_TimestampedFiringAngle>)currentGun.get(mira);
      if (initialOthers == 1) {
        check(selected == initialPerceptual && perceptualGun.get(mira) == initialPerceptual,
            "pure_duel_must_retain_perceptual_fallback");
        System.out.println("PURE_DUEL_PERCEPTUAL_FALLBACK_PASS samples=0");
      } else {
        check(selected != null && virtualGuns.contains(selected), "early_melee_to_duel_must_select_nonnull_virtual_gun");
        mira.initGunViews(target);
        manager.fireNextTickWave(new Point2D.Double(405, 305), target.lastScanState.location,
            target.botName, 0, 1, 1.999, 100, 0, .5, 8, 1);
        check(Double.isFinite(selected.aim(target.lastWaveFired, false)), "transition_selected_gun_must_aim_without_samples");
        System.out.println("EARLY_MELEE_TO_DUEL_NONNULL_GUN_PASS samples=0");
      }
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("one probe name required");
    if (args[0].equals("projection")) projection();
    else if (args[0].equals("distance_multi")) distances(false);
    else if (args[0].equals("distance_single")) distances(true);
    else if (args[0].equals("transition")) transition();
    else throw new IllegalArgumentException("unknown probe " + args[0]);
  }
}
'''


def checked_process(command, timeout):
    return subprocess.run(command, capture_output=True, text=True, timeout=timeout)


def test_source(name, source):
    record = {'name': name, 'source': str(source),
              'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest()}
    with tempfile.TemporaryDirectory(prefix=name + '-') as temporary:
        directory = Path(temporary)
        classes = directory / 'classes'
        classes.mkdir()
        probe = directory / 'meleeupgradeprobe.java'
        probe.write_text(PROBE)
        compiler = ['java', '-jar', str(ECJ), '-8', '-nowarn'] if ECJ else ['javac', '--release', '8']
        build = checked_process(compiler + ['-encoding', 'UTF-8', '-cp', str(API),
                                 '-d', str(classes), str(source), str(probe)], 90)
        if build.returncode:
            raise AssertionError(name + ' failed Java 8 compilation:\n' + build.stdout + build.stderr)
        record['compiled_java8'] = True
        record['probes'] = []
        failures = {'projection': 'alternate_source_heading_velocity_order',
                    'distance_multi': 'all_enemy_distance_refresh',
                    'distance_single': 'single_enemy_distance_refresh',
                    'transition': 'early_melee_to_duel_must_select_nonnull_virtual_gun'}
        for probe_name, expected_failure in failures.items():
            result = checked_process(['java', '-cp', str(classes) + os.pathsep + str(API),
                                      'equipe.meleeupgradeprobe', probe_name], 30)
            case = {'probe': probe_name, 'returncode': result.returncode,
                    'stdout': result.stdout, 'stderr': result.stderr}
            record['probes'].append(case)
            if name == 'baseline':
                if result.returncode == 0 or expected_failure not in result.stderr:
                    raise AssertionError('baseline did not reproduce ' + expected_failure + ': ' + str(case))
            elif result.returncode:
                raise AssertionError('candidate failed ' + probe_name + ': ' + str(case))
    return record


def main():
    global API, ECJ
    parser = argparse.ArgumentParser(description='Regression checks for melee wave projection, opponent distances and safe duel transition.')
    parser.add_argument('--api', type=Path, required=True)
    parser.add_argument('--ecj', type=Path)
    parser.add_argument('--source', type=Path, default=ROOT / 'robots/equipe/titanprime.java')
    parser.add_argument('--baseline', type=Path, help='Optional old source; its known defects must reproduce.')
    parser.add_argument('--output', type=Path, required=True)
    options = parser.parse_args()
    API, ECJ = options.api.resolve(), options.ecj.resolve() if options.ecj else None
    reports = []
    if options.baseline:
        reports.append(test_source('baseline', options.baseline.resolve()))
    reports.append(test_source('candidate', options.source.resolve()))
    options.output.parent.mkdir(parents=True, exist_ok=True)
    options.output.write_text(json.dumps({'passed': True, 'sources': reports,
        'scope': 'Actual production methods and Java8 compilation; combat validation remains separate.'}, indent=2) + '\n')
    print('MELEE_UPGRADE_PRODUCTION_CHECKS_PASS')


if __name__ == '__main__':
    main()
