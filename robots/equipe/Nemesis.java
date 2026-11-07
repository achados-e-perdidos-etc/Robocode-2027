package equipe;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import robocode.*;
import robocode.util.Utils;

/**
 * Nemesis 2.0 - Robocode classico, Java 8+, um unico arquivo fonte.
 * Implementacao propria das tecnicas documentadas em:
 * https://robowiki.net/wiki/Wave_Surfing_Tutorial (Voidious / ABC)
 * https://jkflying.net/posts/understanding_drussgt/ (Julian Kent)
 * https://github.com/Voidious/Diamond (duelo versus melee)
 * Nao incorpora codigo dos robos acima. Nao e uma rede neural.
 * Memoria estatistica por adversario, preservada entre rounds da batalha;
 * nao grava arquivos nem promete persistencia entre batalhas independentes.
 */
public class Nemesis extends AdvancedRobot {
    static final int BINS = 47, SEGMENTS = 24, HISTORY = 256;
    static final double TWO_PI = Math.PI * 2;
    // Statics sao preservados pelo Robocode entre rounds, dentro da batalha.
    private static final Map<String, Brain> MEMORY = new HashMap<String, Brain>();
    private final Map<String, Enemy> enemies = new HashMap<String, Enemy>();
    private final List<Wave> incoming = new ArrayList<Wave>();
    private final List<GunWave> outgoing = new ArrayList<GunWave>();
    private final Self[] history = new Self[HISTORY];
    private final List<Forecast> vision = new ArrayList<Forecast>();
    private Enemy target;
    private Point2D.Double destination, lastDestination;
    private double width, height;
    private int skipped, wallHits, shots, hits, wavesDetected, wavesMatched;
    private long lastPlan = -100;
    private long solutionTime = -100;
    private Enemy solutionTarget;
    private double firingSolution;
    private boolean showVision = true;

    public void run() {
        width = getBattleFieldWidth();
        height = getBattleFieldHeight();
        setColors(new Color(25, 20, 45), new Color(190, 40, 220), Color.CYAN);
        setBulletColor(Color.CYAN);
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);
        // Compensa danos/bonus antes de processar a energia do novo scan.
        setEventPriority("BulletHitEvent", 80);
        setEventPriority("HitByBulletEvent", 80);
        setEventPriority("BulletHitBulletEvent", 80);
        setEventPriority("HitRobotEvent", 70);
        setEventPriority("ScannedRobotEvent", 10);
        rememberSelf();
        while (true) {
            rememberSelf();
            expireWaves();
            chooseTarget();
            radar();
            move();
            aimAndFire();
            execute();
        }
    }

    private void rememberSelf() {
        history[(int) (getTime() % HISTORY)] = new Self(getTime(), getX(), getY(),
                getHeadingRadians(), getVelocity());
    }

    private Self selfAt(long tick) {
        if (tick >= 0) {
            Self s = history[(int) (tick % HISTORY)];
            if (s != null && s.time == tick) return s;
        }
        return new Self(getTime(), getX(), getY(), getHeadingRadians(), getVelocity());
    }

    public void onScannedRobot(ScannedRobotEvent scan) {
        Enemy e = enemies.get(scan.getName());
        if (e == null) {
            Brain brain = MEMORY.get(scan.getName());
            if (brain == null) {
                if (MEMORY.size() >= 64) MEMORY.clear(); // Limite de memoria.
                brain = new Brain();
                MEMORY.put(scan.getName(), brain);
            }
            e = new Enemy(scan.getName(), brain);
            e.nextFire = (long) Math.ceil(3 / getGunCoolingRate());
            enemies.put(e.name, e);
        }
        long now = getTime(), gap = now - e.time;
        double abs = getHeadingRadians() + scan.getBearingRadians();
        Point2D.Double point = project(getX(), getY(), abs, scan.getDistance());
        if (e.time >= 0 && gap > 0) {
            double drop = e.energy - scan.getEnergy(); // Energia ja corrigida nos eventos.
            boolean wallStop = Math.abs(e.velocity) > 2 && scan.getVelocity() == 0
                    && wallDistance(point.x, point.y) < 20;
            // Em melee ha danos de terceiros que o nosso radar nao consegue distinguir.
            if (!wallStop && gap <= 8 && drop >= 0.0999 && drop <= 3.0001
                    && now - 1 >= e.nextFire - 1) {
                double power = clamp(0.1, drop, 3);
                long fireTime = now - 1;
                Point2D.Double origin = predict(e, Math.max(0, gap - 1), false);
                Self observed = selfAt(now - 2); // Dados disponiveis ao atirador.
                Wave w = new Wave();
                w.owner = e;
                w.x = origin.x; w.y = origin.y; w.time = fireTime;
                w.power = power; w.speed = Rules.getBulletSpeed(power);
                w.bearing = angle(w.x, w.y, observed.x, observed.y);
                double lateral = observed.velocity * Math.sin(observed.heading - w.bearing);
                w.direction = sign(lateral);
                w.segment = segment(distance(w.x, w.y, observed.x, observed.y),
                        Math.abs(lateral), 0);
                w.confidence = (getOthers() == 1 ? 1.0 : 0.45) / (1 + (gap - 1) * 0.15);
                if (incoming.size() < 64) incoming.add(w);
                wavesDetected++;
                e.nextFire = fireTime + (long) Math.ceil((1 + power / 5) / getGunCoolingRate());
            }
            e.turnRate = clamp(-Math.PI / 18,
                    normal(scan.getHeadingRadians() - e.heading) / gap, Math.PI / 18);
            e.acceleration = (scan.getVelocity() - e.velocity) / gap;
        }
        e.x = point.x; e.y = point.y;
        e.heading = scan.getHeadingRadians(); e.velocity = scan.getVelocity();
        e.energy = scan.getEnergy(); e.time = now; e.alive = true;
        double lateral = e.velocity * Math.sin(e.heading - abs);
        if (Math.abs(lateral) > 0.1) e.direction = sign(lateral);
        resolveGunWaves(e);
    }

    private void chooseTarget() {
        Enemy best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Enemy e : enemies.values()) {
            if (!e.alive || getTime() - e.time > 32) continue;
            double d = distance(getX(), getY(), e.x, e.y);
            double score = d * (0.65 + Math.min(100, e.energy) / 180.0)
                    * (1 + (getTime() - e.time) / 20.0);
            if (e == target) score *= 0.82; // Evita trocar o canhao sem necessidade.
            if (score < bestScore) { bestScore = score; best = e; }
        }
        target = best;
    }

    private void radar() {
        if (getOthers() == 1 && target != null && getTime() - target.time <= 2) {
            double error = normal(angle(getX(), getY(), target.x, target.y) - getRadarHeadingRadians());
            setTurnRadarRightRadians(error * 2 + sign(error) * 0.025);
        } else {
            // Em melee varrer todos impede que inimigos fora do alvo fiquem invisiveis.
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }

    private void expireWaves() {
        for (Iterator<Wave> it = incoming.iterator(); it.hasNext();) {
            Wave w = it.next();
            if (w.radius(getTime()) > distance(w.x, w.y, getX(), getY()) + 55) it.remove();
        }
        for (Iterator<GunWave> it = outgoing.iterator(); it.hasNext();) {
            GunWave w = it.next();
            if (!w.owner.alive || getTime() - w.time > 180) it.remove();
        }
    }

    /** Go-to wave surfing: simula rotas e avalia cruzamentos de ate 4 ondas. */
    private void move() {
        int interval = getOthers() > 1 ? 3 : 2;
        if (destination == null || getTime() - lastPlan >= interval) {
            updateVision();
            List<Wave> threats = nearestWaves();
            List<Point2D.Double> candidates = new ArrayList<Point2D.Double>();
            // Uma opcao de frear tambem pode evitar o proximo tiro.
            candidates.add(project(getX(), getY(), getHeadingRadians(), getVelocity() * 2));
            if (destination != null) candidates.add(destination);
            for (int ring = 0; ring < 2; ring++) {
                double radius = ring == 0 ? 95 : 185;
                for (int i = 0; i < 16; i++) {
                    Point2D.Double p = project(getX(), getY(), i * TWO_PI / 16, radius);
                    if (inside(p.x, p.y, 36)) candidates.add(p);
                }
            }
            double best = Double.POSITIVE_INFINITY;
            Point2D.Double chosen = null;
            for (Point2D.Double p : candidates) {
                if (!inside(p.x, p.y, 20)) continue;
                double risk = routeRisk(p, threats);
                if (risk < best) { best = risk; chosen = p; }
            }
            if (chosen != null) {
                lastDestination = destination;
                destination = chosen;
            } else {
                destination = new Point2D.Double(width / 2, height / 2);
            }
            lastPlan = getTime();
        }
        double d = distance(getX(), getY(), destination.x, destination.y);
        double turn = normal(angle(getX(), getY(), destination.x, destination.y) - getHeadingRadians());
        int drive = 1;
        if (Math.abs(turn) > Math.PI / 2) { turn = normal(turn + Math.PI); drive = -1; }
        setMaxVelocity(8);
        setTurnRightRadians(turn);
        setAhead(d * drive);
    }

    private List<Wave> nearestWaves() {
        List<Wave> result = new ArrayList<Wave>();
        for (Wave w : incoming) {
            double eta = (distance(w.x, w.y, getX(), getY()) - w.radius(getTime())) / w.speed;
            if (eta < -2) continue;
            int index = 0;
            while (index < result.size()) {
                Wave v = result.get(index);
                double other = (distance(v.x, v.y, getX(), getY()) - v.radius(getTime())) / v.speed;
                if (eta < other) break;
                index++;
            }
            result.add(index, w);
            if (result.size() > 4) result.remove(4);
        }
        return result;
    }

    private double routeRisk(Point2D.Double goal, List<Wave> threats) {
        double x = getX(), y = getY(), heading = getHeadingRadians(), velocity = getVelocity();
        boolean[] crossed = new boolean[threats.size()];
        double risk = 0, spatial = 0;
        int samples = 0;
        for (int tick = 1; tick <= 100; tick++) {
            double dist = distance(x, y, goal.x, goal.y);
            double turn = normal(angle(x, y, goal.x, goal.y) - heading);
            int drive = 1;
            if (Math.abs(turn) > Math.PI / 2) { turn = normal(turn + Math.PI); drive = -1; }
            double limit = Rules.getTurnRateRadians(velocity);
            heading = normal(heading + clamp(-limit, turn, limit));
            velocity = nextVelocity(velocity * drive, dist) * drive;
            x += Math.sin(heading) * velocity;
            y += Math.cos(heading) * velocity;
            if (!inside(x, y, 18)) { risk += 15; x = clamp(18, x, width - 18); y = clamp(18, y, height - 18); velocity = 0; }
            for (int j = 0; j < threats.size(); j++) {
                Wave w = threats.get(j);
                if (!crossed[j] && w.radius(getTime() + tick) >= distance(w.x, w.y, x, y) - 18) {
                    crossed[j] = true;
                    double gf = guessFactor(w, x, y);
                    double density = danger(w.owner.brain, w.segment, gf);
                    // Head-on como prior; os impactos reais passam a dominar.
                    double prior = 0.12 + 0.38 * Math.exp(-gf * gf * 9);
                    double learned = Math.min(0.88, w.owner.brain.surfSamples / 12.0);
                    risk += (prior * (1 - learned) + density * learned * 5)
                            * Rules.getBulletDamage(w.power) * w.confidence / (1 + tick * 0.025);
                }
            }
            if (tick % 10 == 0) {
                spatial += positionRisk(x, y, heading, tick);
                samples++;
            }
        }
        risk += spatial / Math.max(1, samples);
        if (lastDestination != null) risk += 0.08 * Math.exp(-goal.distanceSq(lastDestination) / 10000);
        // Desempate favorece movimento, mas nao proibe a frenagem durante uma onda.
        risk += 0.12 / (1 + distance(getX(), getY(), goal.x, goal.y) / 40);
        return risk;
    }

    private double positionRisk(double x, double y, double heading, int future) {
        double risk = 9 / (wallDistance(x, y) + 15);
        for (Forecast forecast : vision) {
            Enemy e = forecast.enemy;
            Point2D.Double p = forecast.positions[future / 10 - 1];
            double d = distance(x, y, p.x, p.y);
            double weight = 0.6 + Math.min(100, e.energy) / 100;
            risk += weight * 18000 / Math.max(900, d * d);
            if (d < 55) risk += 25;
            if (getOthers() == 1) {
                risk += Math.abs(d - 430) / 1600;
                risk += 0.12 * Math.abs(Math.cos(heading - angle(p.x, p.y, x, y)));
            }
        }
        return risk;
    }

    private void updateVision() {
        vision.clear();
        for (Enemy e : enemies.values()) {
            if (!e.alive || getTime() - e.time > 48) continue;
            Forecast forecast = new Forecast(e);
            for (int i = 0; i < 10; i++) {
                forecast.positions[i] = predict(e,
                        Math.min(16, getTime() - e.time + (i + 1) * 10), true);
            }
            vision.add(forecast);
        }
    }

    // Mesma regra de aceleracao/frenagem do motor, incluindo inversao fracionaria.
    static double nextVelocity(double velocity, double distance) {
        double decelTime = Math.max(1, Math.ceil((Math.sqrt(4 * distance + 1) - 1) / 2));
        double decelDistance = decelTime * (decelTime - 1);
        double goal = Math.min(8, (decelTime - 1) * 2 + (distance - decelDistance) / decelTime);
        if (velocity >= 0) return Math.max(velocity - 2, Math.min(goal, velocity + 1));
        double decel = Math.min(1, -velocity / 2);
        double maxDecel = decel * 2 + (1 - decel);
        return Math.max(velocity - 1, Math.min(goal, velocity + maxDecel));
    }

    private void aimAndFire() {
        if (target == null || getTime() - target.time > 8) return;
        Enemy e = target;
        Point2D.Double current = predict(e, getTime() - e.time, true);
        double d = distance(getX(), getY(), current.x, current.y);
        double power = firePower(e, d);
        double speed = Rules.getBulletSpeed(power);
        double direct = angle(getX(), getY(), current.x, current.y);
        double lateral = Math.abs(e.velocity * Math.sin(e.heading - direct));
        int seg = segment(d, lateral, e.acceleration);
        double[] angles = new double[4];
        angles[0] = direct;
        angles[1] = intercept(e, speed, false);
        angles[2] = intercept(e, speed, true);
        double gf = bestGuessFactor(e.brain, seg);
        angles[3] = direct + e.direction * gf * Math.asin(8 / speed);
        // Sem historico, usa preditiva circular. Depois, competicao de miras virtuais.
        int selected = 2;
        double best = -1;
        for (int i = 0; i < angles.length; i++) {
            double prior = i == 2 ? 0.22 : i == 1 ? 0.20 : 0.14;
            double score = (e.brain.gunHits[i] + prior * 8) / (e.brain.gunTrials[i] + 8);
            if (i == 3 && e.brain.gunSamples < 8) score *= 0.5;
            if (score > best) { best = score; selected = i; }
        }
        double aim = angles[selected];
        // Mantem a solucao por no maximo dois ticks quando o canhao esfria.
        // Evita perseguir indefinidamente previsoes que alternam a cada scan.
        if (getGunHeat() == 0) {
            if (solutionTarget == e && getTime() - solutionTime <= 2) {
                aim = firingSolution;
            } else {
                firingSolution = aim;
                solutionTarget = e;
                solutionTime = getTime();
            }
        } else {
            solutionTarget = null;
        }
        double error = normal(aim - getGunHeadingRadians());
        setTurnGunRightRadians(error);
        if (getTime() % 25 == 0) setDebugProperty("gun", "mode=" + selected
                + " error=" + (int) Math.toDegrees(error) + " heat=" + getGunHeat()
                + " age=" + (getTime() - e.time) + " energy=" + getEnergy());
        if (getGunHeat() > 0 || Math.abs(error) > Math.atan2(14, d)
                || getEnergy() <= power + reserve()) return;
        // O tiro sai ANTES da rotacao do canhao: testa o angulo atual acima.
        Bullet bullet = setFireBullet(power);
        if (bullet != null) {
            shots++;
            solutionTarget = null;
            fired.put(bullet, e.brain);
            GunWave w = new GunWave();
            w.owner = e; w.x = getX(); w.y = getY();
            w.time = getTime(); w.speed = bullet.getVelocity();
            w.power = bullet.getPower(); w.bearing = direct; w.direction = e.direction;
            w.segment = seg; w.angles = angles;
            w.previousX = current.x; w.previousY = current.y; w.previousTime = getTime();
            if (outgoing.size() < 96) outgoing.add(w);
        }
    }

    private double reserve() { return getOthers() > 1 ? 2.5 : 0.6; }

    private double firePower(Enemy e, double d) {
        double accuracy = (e.brain.actualHits + 2.0) / (e.brain.actualShots + 8.0);
        double power = d < 150 ? 2.9 : d < 350 ? 2.3 : 1.6;
        if (e.brain.actualShots > 12 && accuracy < 0.16) power *= 0.7;
        if (getOthers() > 1 && d > 350) power *= 0.8;
        power = Math.min(power, getEnergy() / (getEnergy() < 15 ? 18 : 10));
        // Evita gastar mais energia que o necessario para eliminar o alvo.
        double lethal = e.energy <= 4 ? e.energy / 4 : (e.energy + 2) / 6;
        power = Math.min(power, lethal + 0.02);
        power = Math.min(power, getEnergy() - reserve() - 0.05);
        return clamp(0.1, power, 3);
    }

    private double intercept(Enemy e, double speed, boolean circular) {
        double age = getTime() - e.time;
        Point2D.Double p = predict(e, age, circular);
        double heading = e.heading + (circular ? e.turnRate * age : 0);
        for (int tick = 1; tick < 140; tick++) {
            if (tick * speed >= distance(getX(), getY(), p.x, p.y)) break;
            if (circular) heading += e.turnRate;
            Point2D.Double next = project(p.x, p.y, heading, e.velocity);
            if (!inside(next.x, next.y, 18)) break;
            p = next;
        }
        return angle(getX(), getY(), p.x, p.y);
    }

    private Point2D.Double predict(Enemy e, double ticks, boolean circular) {
        double x = e.x, y = e.y, heading = e.heading;
        for (int i = 0; i < Math.min(48, ticks); i++) {
            if (circular) heading += e.turnRate;
            Point2D.Double p = project(x, y, heading, e.velocity);
            if (!inside(p.x, p.y, 18)) break;
            x = p.x; y = p.y;
        }
        return new Point2D.Double(x, y);
    }

    private void resolveGunWaves(Enemy e) {
        for (Iterator<GunWave> it = outgoing.iterator(); it.hasNext();) {
            GunWave w = it.next();
            if (w.owner != e) continue;
            double d = distance(w.x, w.y, e.x, e.y);
            if (w.radius(getTime()) >= d - 18) {
                // Interpola entre scans: reduz erro quando o radar varre outros inimigos.
                long gap = getTime() - w.previousTime;
                double x = e.x, y = e.y;
                boolean trustworthy = gap >= 0 && gap <= 10;
                if (gap > 0 && trustworthy) {
                    for (int t = 1; t <= gap; t++) {
                        double part = (double) t / gap;
                        x = w.previousX + (e.x - w.previousX) * part;
                        y = w.previousY + (e.y - w.previousY) * part;
                        if (w.radius(w.previousTime + t) >= distance(w.x, w.y, x, y) - 18) break;
                    }
                }
                if (trustworthy) {
                    Brain brain = e.brain;
                    double gf = guessFactor(w, x, y);
                    learn(brain.gun[w.segment], gf, 1, 0.992);
                    learn(brain.gunGlobal, gf, 1, 0.996);
                    brain.gunSamples++;
                    double actual = angle(w.x, w.y, x, y);
                    double tolerance = Math.atan2(21, distance(w.x, w.y, x, y));
                    for (int gun = 0; gun < 4; gun++) {
                        brain.gunTrials[gun] = brain.gunTrials[gun] * 0.98 + 1;
                        brain.gunHits[gun] = brain.gunHits[gun] * 0.98
                                + (Math.abs(normal(w.angles[gun] - actual)) < tolerance ? 1 : 0);
                    }
                }
                it.remove();
            } else {
                w.previousX = e.x; w.previousY = e.y; w.previousTime = getTime();
            }
        }
    }

    private static double bestGuessFactor(Brain brain, int segment) {
        int best = BINS / 2;
        double value = -1;
        for (int i = 0; i < BINS; i++) {
            double score = brain.gun[segment][i] + brain.gunGlobal[i] * 0.3;
            if (score > value) { best = i; value = score; }
        }
        return value > 0 ? (best - BINS / 2.0 + 0.5) / (BINS / 2) : 0;
    }

    static int segment(double distance, double lateral, double acceleration) {
        int d = distance < 250 ? 0 : distance < 450 ? 1 : 2;
        int v = Math.min(3, (int) (lateral / 2.1));
        return (d * 4 + v) * 2 + (acceleration < -0.1 ? 1 : 0);
    }

    static double guessFactor(Wave w, double x, double y) {
        return clamp(-1, normal(angle(w.x, w.y, x, y) - w.bearing)
                * w.direction / Math.asin(8 / w.speed), 1);
    }

    static void learn(double[] buffer, double gf, double weight, double decay) {
        double center = (clamp(-1, gf, 1) + 1) * (BINS - 1) / 2;
        for (int i = 0; i < BINS; i++) {
            double delta = (i - center) / 1.8;
            buffer[i] = buffer[i] * decay + weight / (1 + delta * delta);
        }
    }

    private static double danger(Brain brain, int segment, double gf) {
        int bin = (int) Math.round((clamp(-1, gf, 1) + 1) * (BINS - 1) / 2);
        double sum = 0;
        for (int i = 0; i < BINS; i++) sum += brain.surf[segment][i] + brain.surfGlobal[i] * 0.4;
        if (sum < 0.001) return 1.0 / BINS;
        return (brain.surf[segment][bin] + brain.surfGlobal[bin] * 0.4) / sum;
    }

    private void matchBullet(Bullet bullet, double x, double y, String shooter) {
        Wave best = null;
        double error = Double.POSITIVE_INFINITY;
        for (Wave w : incoming) {
            if (!w.owner.name.equals(shooter) || Math.abs(w.speed - bullet.getVelocity()) > 0.1) continue;
            double delta = Math.abs(w.radius(getTime()) - distance(w.x, w.y, x, y));
            if (delta < error && delta < w.speed * 2.5 + 18) { error = delta; best = w; }
        }
        if (best != null) {
            double gf = guessFactor(best, x, y);
            learn(best.owner.brain.surf[best.segment], gf, 1, 0.96);
            learn(best.owner.brain.surfGlobal, gf, 1, 0.98);
            best.owner.brain.surfSamples++;
            incoming.remove(best);
            wavesMatched++;
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
        Enemy shooter = enemies.get(e.getName());
        if (shooter != null) shooter.energy += Rules.getBulletHitBonus(e.getPower());
        matchBullet(e.getBullet(), e.getBullet().getX(), e.getBullet().getY(), e.getName());
        lastPlan = -100;
    }

    public void onBulletHit(BulletHitEvent e) {
        hits++;
        Enemy victim = enemies.get(e.getName());
        if (victim != null) victim.energy -= Rules.getBulletDamage(e.getBullet().getPower());
        finishShot(e.getBullet(), true);
    }

    public void onBulletMissed(BulletMissedEvent e) { finishShot(e.getBullet(), false); }

    public void onBulletHitBullet(BulletHitBulletEvent e) {
        Bullet other = e.getHitBullet();
        matchBullet(other, other.getX(), other.getY(), other.getName());
        finishShot(e.getBullet(), false);
    }

    // Registro de tiros reais e separado das ondas virtuais, que podem passar antes do impacto.
    private final Map<Bullet, Brain> fired = new HashMap<Bullet, Brain>();

    private void finishShot(Bullet bullet, boolean hit) {
        Brain brain = fired.remove(bullet);
        if (brain != null) {
            brain.actualShots = brain.actualShots * 0.99 + 1;
            brain.actualHits = brain.actualHits * 0.99 + (hit ? 1 : 0);
        }
    }

    public void onHitRobot(HitRobotEvent e) {
        Enemy enemy = enemies.get(e.getName());
        if (enemy != null) enemy.energy -= 0.6;
        destination = project(getX(), getY(), getHeadingRadians() + e.getBearingRadians(), -100);
        destination.x = clamp(36, destination.x, width - 36);
        destination.y = clamp(36, destination.y, height - 36);
        lastPlan = getTime();
    }

    public void onHitWall(HitWallEvent e) { wallHits++; destination = null; }
    public void onSkippedTurn(SkippedTurnEvent e) { skipped++; }
    public void onRobotDeath(RobotDeathEvent e) {
        Enemy enemy = enemies.get(e.getName());
        if (enemy != null) enemy.alive = false;
        if (enemy == target) target = null;
        // Tiros de um inimigo morto continuam no ar.
        lastPlan = -100;
    }

    public void onRoundEnded(RoundEndedEvent e) {
        out.println("Nemesis round=" + getRoundNum() + " shots=" + shots + " hits=" + hits
                + " waves=" + wavesDetected + " matched=" + wavesMatched
                + " walls=" + wallHits + " skipped=" + skipped);
        for (Enemy enemy : enemies.values()) {
            out.println("learn " + enemy.name + " gun=" + enemy.brain.gunSamples
                    + " surf=" + enemy.brain.surfSamples);
        }
    }

    public void onKeyPressed(java.awt.event.KeyEvent e) {
        if (e.getKeyCode() == java.awt.event.KeyEvent.VK_V) showVision = !showVision;
    }

    public void onPaint(Graphics2D g) {
        if (!showVision) return;
        g.setColor(new Color(255, 65, 100, 100));
        for (Wave w : incoming) {
            double r = w.radius(getTime());
            g.drawOval((int) (w.x - r), (int) (w.y - r), (int) (r * 2), (int) (r * 2));
        }
        for (Enemy e : enemies.values()) {
            if (!e.alive) continue;
            g.setColor(e == target ? Color.YELLOW : Color.GRAY);
            g.drawRect((int) e.x - 22, (int) e.y - 22, 44, 44);
            g.drawString(e.name + " E=" + (int) e.energy + " age=" + (getTime() - e.time),
                    (int) e.x + 25, (int) e.y);
        }
        if (destination != null) {
            g.setColor(Color.CYAN);
            g.drawLine((int) getX(), (int) getY(), (int) destination.x, (int) destination.y);
            g.drawOval((int) destination.x - 6, (int) destination.y - 6, 12, 12);
        }
        g.setColor(Color.WHITE);
        g.drawString("Nemesis | " + (getOthers() == 1 ? "DUEL SURF" : "MELEE RISK+WAVES")
                + " | waves " + incoming.size() + " | V: visao", 15, 20);
    }

    private boolean inside(double x, double y, double margin) {
        return x >= margin && y >= margin && x <= width - margin && y <= height - margin;
    }
    private double wallDistance(double x, double y) {
        return Math.min(Math.min(x, width - x), Math.min(y, height - y));
    }
    static Point2D.Double project(double x, double y, double heading, double distance) {
        return new Point2D.Double(x + Math.sin(heading) * distance, y + Math.cos(heading) * distance);
    }
    static double angle(double x, double y, double tx, double ty) { return Math.atan2(tx - x, ty - y); }
    static double distance(double x, double y, double tx, double ty) { return Math.hypot(tx - x, ty - y); }
    static double normal(double a) { return Utils.normalRelativeAngle(a); }
    static double clamp(double min, double value, double max) { return Math.max(min, Math.min(value, max)); }
    static int sign(double value) { return value < 0 ? -1 : 1; }

    static class Brain {
        final double[][] gun = new double[SEGMENTS][BINS], surf = new double[SEGMENTS][BINS];
        final double[] gunGlobal = new double[BINS], surfGlobal = new double[BINS];
        final double[] gunTrials = new double[4], gunHits = new double[4];
        int gunSamples, surfSamples;
        double actualShots, actualHits;
    }
    static class Enemy {
        final String name;
        final Brain brain;
        double x, y, heading, velocity, energy, turnRate, acceleration;
        long time = -1, nextFire;
        int direction = 1;
        boolean alive = true;
        Enemy(String name, Brain brain) { this.name = name; this.brain = brain; }
    }
    static class Forecast {
        final Enemy enemy;
        final Point2D.Double[] positions = new Point2D.Double[10];
        Forecast(Enemy enemy) { this.enemy = enemy; }
    }
    static class Self {
        final long time;
        final double x, y, heading, velocity;
        Self(long time, double x, double y, double heading, double velocity) {
            this.time = time; this.x = x; this.y = y; this.heading = heading; this.velocity = velocity;
        }
    }
    static class Wave {
        Enemy owner;
        double x, y, bearing, speed, power, confidence = 1;
        int direction, segment;
        long time;
        double radius(long now) { return Math.max(0, now - time) * speed; }
    }
    static class GunWave extends Wave {
        double[] angles;
        double previousX, previousY;
        long previousTime;
    }
}
