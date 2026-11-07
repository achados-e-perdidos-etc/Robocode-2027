package equipe;

import java.awt.Color;
import robocode.*;
import robocode.util.Utils;

/**
 * Base para batalhas 1 contra 1 no Robocode classico.
 * Mira linear e movimento orbital: evoluir com resultados de batalhas.
 */
public class Sentinela extends AdvancedRobot {
    private int direction = 1;
    private long lastScan = -100;
    private double previousEnergy = Double.NaN;
    private String target;
    private double targetDistance;

    public void run() {
        setColors(new Color(25, 40, 70), new Color(0, 180, 210), Color.WHITE);
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);
        while (true) {
            if (getTime() - lastScan > 4) {
                setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            }
            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        // Mantem um alvo enquanto ele esta sendo observado.
        if (target != null && !target.equals(e.getName())
                && getTime() - lastScan < 12 && e.getDistance() > targetDistance * 0.75) {
            return;
        }
        boolean sameTarget = e.getName().equals(target);
        double bearing = getHeadingRadians() + e.getBearingRadians();
        if (sameTarget && !Double.isNaN(previousEnergy)) {
            double drop = previousEnergy - e.getEnergy();
            // Quedas de energia tambem podem vir de colisoes ou outros tiros.
            if (drop >= 0.1 && drop <= 3.0) direction = -direction;
        }
        target = e.getName();
        previousEnergy = e.getEnergy();
        targetDistance = e.getDistance();
        lastScan = getTime();

        double radarError = Utils.normalRelativeAngle(bearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(2 * radarError + Math.copySign(0.02, radarError));

        // Orbita e ajusta a distancia para aproximadamente 300 pixels.
        double moveAngle = bearing + Math.PI / 2
                + Math.max(-0.45, Math.min(0.45, (e.getDistance() - 300) / 500.0));
        double desired = Utils.normalRelativeAngle(moveAngle - getHeadingRadians());
        setTurnRightRadians(Math.tan(desired));
        double travel = direction * (Math.cos(desired) >= 0 ? 1 : -1);
        if (nearWallAhead(travel)) travel = -travel;
        setAhead(140 * travel);
        setMaxVelocity(Math.abs(getTurnRemaining()) > 30 ? 4 : 8);

        double power = Math.min(2.4, Math.max(0.5, 500 / e.getDistance()));
        power = Math.min(power, Math.max(0.1, getEnergy() / 12));
        double speed = Rules.getBulletSpeed(power);
        double x = getX() + Math.sin(bearing) * e.getDistance();
        double y = getY() + Math.cos(bearing) * e.getDistance();
        double enemyVx = Math.sin(e.getHeadingRadians()) * e.getVelocity();
        double enemyVy = Math.cos(e.getHeadingRadians()) * e.getVelocity();
        int ticks = 0;
        while (++ticks * speed < Math.hypot(x - getX(), y - getY()) && ticks < 200) {
            x = Math.max(18, Math.min(getBattleFieldWidth() - 18, x + enemyVx));
            y = Math.max(18, Math.min(getBattleFieldHeight() - 18, y + enemyVy));
        }
        double aim = Math.atan2(x - getX(), y - getY());
        double gunError = Utils.normalRelativeAngle(aim - getGunHeadingRadians());
        setTurnGunRightRadians(gunError);
        if (getGunHeat() == 0 && Math.abs(gunError) < Math.atan2(18, e.getDistance())
                && getEnergy() > power + 0.5) {
            setFire(power);
        }
    }

    private boolean nearWallAhead(double travel) {
        double x = getX() + Math.sin(getHeadingRadians()) * travel * 75;
        double y = getY() + Math.cos(getHeadingRadians()) * travel * 75;
        return x < 45 || y < 45
                || x > getBattleFieldWidth() - 45 || y > getBattleFieldHeight() - 45;
    }

    public void onHitWall(HitWallEvent e) {
        direction = -direction;
        setBack(100);
    }

    public void onHitRobot(HitRobotEvent e) {
        direction = -direction;
        setBack(100);
    }

    public void onRobotDeath(RobotDeathEvent e) {
        if (e.getName().equals(target)) {
            target = null;
            previousEnergy = Double.NaN;
            lastScan = -100;
        }
    }
}
