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
 * TitanPrime 3.7 - Robocode classico, Java 8+, um unico arquivo fonte.
 * Implementacao propria das tecnicas documentadas em:
 * https://robowiki.net/wiki/Wave_Surfing_Tutorial (Voidious / ABC)
 * https://jkflying.net/posts/understanding_drussgt/ (Julian Kent)
 * https://github.com/Voidious/Diamond (duelo versus melee)
 * Nao incorpora codigo dos robos acima. Redes pequenas aprendem por backpropagation.
 * Memoria estatistica por adversario, preservada entre rounds da batalha;
 * nao grava arquivos nem promete persistencia entre batalhas independentes.
 */
public class titanprime extends AdvancedRobot {
    static final int BINS = 47, SEGMENTS = 24, HISTORY = 256;
    static final double TWO_PI = Math.PI * 2;
    // Genes: surfing, distancia, taxa neural, mistura neural, repulsao, potencia,
    // fases e escalas dos pesos iniciais. Otimizados fora do round por torneios.
    static final double[] GENOME = {1.2334466, 441.2925895, 0.026321, 0.3713866, 18000, 1.0094116, 1.2035665, 0.9761274, -0.0066714, 1.0544508};
    static final String PRETRAINED_GUN = "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";
    static final String PRETRAINED_SURF = "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";
    static final boolean EXPORT_LEARNING = false;
    static final boolean USE_THREATS = false;
    static final boolean USE_FLATTENER = true;
    static final boolean USE_KNN_GUN = true;
    static final boolean USE_VISIT_WAVES = true;
    static final boolean USE_EXTENDED_GUNS = false;
    static final boolean USE_FINISHER = true;
    // Um sorteio independente por batalha; statics permanecem entre rounds.
    private static final float BATTLE_HUE = new java.util.Random().nextFloat();
    private static final Neural GENERAL_GUN = new Neural(PRETRAINED_GUN);
    private static final Neural GENERAL_SURF = new Neural(PRETRAINED_SURF);
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
    private long lastVirtualWave = -100;
    private int virtualWaves;
    private final int[] gunUsage = new int[10];
    private boolean finishing;
    private double engagementDistance = GENOME[1];
    private Enemy solutionTarget;
    private double firingSolution;
    private boolean showVision = true, roundReported;
    private boolean celebrating;
    private int celebrationFrame;
    private final Telemetry telemetry = new Telemetry();

    public void run() {
        width = getBattleFieldWidth();
        height = getBattleFieldHeight();
        battleColors();
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
            if (celebrating) {
                celebrate();
                execute();
                continue;
            }
            rememberSelf();
            expireWaves();
            chooseTarget();
            if (!roundReported) telemetry.decision(target != null);
            radar();
            move();
            aimAndFire();
            execute();
        }
    }

    private void battleColors() {
        setColors(Color.getHSBColor(BATTLE_HUE, 0.78f, 0.90f),
                Color.getHSBColor(BATTLE_HUE + 0.12f, 0.70f, 0.55f),
                Color.getHSBColor(BATTLE_HUE + 0.50f, 0.70f, 1.0f));
        setBulletColor(Color.getHSBColor(BATTLE_HUE + 0.50f, 0.70f, 1.0f));
        setScanColor(Color.getHSBColor(BATTLE_HUE, 0.45f, 1.0f));
    }

    public void onWin(WinEvent e) {
        celebrating = true;
        celebrationFrame = 0;
        out.println("Titan Prime venceu o round!");
    }

    private void celebrate() {
        float hue = BATTLE_HUE + celebrationFrame++ * 0.035f;
        setColors(Color.getHSBColor(hue, 0.90f, 1.0f),
                Color.getHSBColor(hue + 0.33f, 0.90f, 1.0f),
                Color.getHSBColor(hue + 0.66f, 0.90f, 1.0f));
        setMaxVelocity(0);
        setAhead(0);
        setTurnRight(10);
        setTurnGunLeft(20);
        setTurnRadarRight(45);
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
        e.sentry = scan.isSentryRobot();
        long now = getTime(), gap = now - e.time;
        if (!celebrating && !roundReported && !e.sentry)
            telemetry.scan(e.time >= 0 ? gap : -1);
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
                w.features = features(observed.x, observed.y, observed.heading,
                        observed.velocity, 0, w.bearing,
                        distance(w.x, w.y, observed.x, observed.y), w.speed, w.direction, 0);
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
        if (USE_EXTENDED_GUNS) e.brain.motion.observe(getRoundNum(), now, e.heading, e.velocity);
        resolveGunWaves(e);
    }

    private void chooseTarget() {
        Enemy best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Enemy e : enemies.values()) {
            // Sentinelas sao ameacas, mas nao disputam a vitoria.
            if (!e.alive || e.sentry || getTime() - e.time > 32) continue;
            double d = distance(getX(), getY(), e.x, e.y);
            double score = d * (0.65 + Math.min(100, e.energy) / 180.0)
                    * (1 + (getTime() - e.time) / 20.0);
            if (e == target) score *= 0.82; // Evita trocar o canhao sem necessidade.
            if (score < bestScore) { bestScore = score; best = e; }
        }
        target = best;
        if (USE_FINISHER) {
            finishing = finisherAllowed();
            double desired = finishing ? 180 : GENOME[1];
            engagementDistance += clamp(-4, desired - engagementDistance, 4);
        }
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
            if (USE_FLATTENER && !w.visited
                    && w.radius(getTime()) >= distance(w.x, w.y, getX(), getY())) {
                recordPass(w, getX(), getY());
            }
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
            // Inferencia neural ocorre uma vez por onda/plano, nao por rota/tick.
            for (Wave w : threats) {
                w.neuralDanger = neuralPrediction(w.owner.brain.surfNet, GENERAL_SURF, w.features);
                w.flatten = USE_FLATTENER ? flattenAmount(w.owner.brain.passed, w.owner.brain.taken) : 0;
                if (w.flatten > 0) w.visitDanger = normalizedVisits(w.owner.brain.visits);
            }
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
                    int bin = gfBin(gf);
                    double maturity = Math.min(1, (w.owner.brain.surfNet.samples
                            + GENERAL_SURF.samples * 0.15) / 40.0);
                    double mix = GENOME[3] * maturity;
                    density = density * (1 - mix) + w.neuralDanger[bin] * mix;
                    if (w.flatten > 0) density = density * (1 - w.flatten) + w.visitDanger[bin] * w.flatten;
                    // Head-on como prior; os impactos reais passam a dominar.
                    double prior = 0.12 + 0.38 * Math.exp(-gf * gf * 9);
                    double learned = Math.min(0.88, w.owner.brain.surfSamples / 12.0);
                    risk += (prior * (1 - learned) + density * learned * 5)
                            * Rules.getBulletDamage(w.power) * w.confidence * GENOME[0] / (1 + tick * 0.025);
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
            if (USE_THREATS) weight *= 1 + 0.4 * forecast.pressure;
            risk += weight * GENOME[4] / Math.max(900, d * d);
            if (USE_THREATS && forecast.pressure > 0) {
                double dx = x - getX(), dy = y - getY();
                double ex = p.x - getX(), ey = p.y - getY();
                double lateral = Math.min(1, Math.abs(dx * ey - dy * ex)
                        / Math.max(1, Math.hypot(dx, dy) * Math.hypot(ex, ey)));
                risk += 0.12 * forecast.pressure * (1 - lateral) * (1 - lateral);
            }
            if (d < 55) risk += 25;
            if (USE_FINISHER && finishing && getOthers() > 1 && e == target)
                risk += Math.abs(d - engagementDistance) / 1800;
            if (getOthers() == 1) {
                risk += Math.abs(d - (USE_FINISHER ? engagementDistance : GENOME[1])) / 1600;
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
            forecast.pressure = USE_THREATS ? threatPressure(e.threatScore, getTime() - e.threatTime) : 0;
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
        double[] angles = new double[USE_EXTENDED_GUNS ? 10 : USE_KNN_GUN ? 7 : 5];
        angles[0] = direct;
        angles[1] = intercept(e, speed, false);
        angles[2] = intercept(e, speed, true);
        double gf = bestGuessFactor(e.brain, seg);
        angles[3] = direct + e.direction * gf * Math.asin(8 / speed);
        double[] input = features(current.x, current.y, e.heading, e.velocity,
                e.acceleration, direct, d, speed, e.direction, e.turnRate);
        double[] distribution = neuralPrediction(e.brain.gunNet, GENERAL_GUN, input);
        angles[4] = direct + e.direction * bestNeuralFactor(distribution,
                Math.atan2(18, d) / Math.asin(8 / speed)) * Math.asin(8 / speed);
        if (USE_KNN_GUN) {
            double[] visits = e.brain.visitGun.predict(input), real = e.brain.realGun.predict(input);
            double width = Math.atan2(18, d) / Math.asin(8 / speed);
            angles[5] = direct + e.direction * bestNeuralFactor(visits, width) * Math.asin(8 / speed);
            angles[6] = direct + e.direction * bestNeuralFactor(real, width) * Math.asin(8 / speed);
        }
        int motionMode = USE_EXTENDED_GUNS ? e.brain.motion.mode() : 0;
        boolean patternReady = false, responseReady = false;
        if (USE_EXTENDED_GUNS) {
            if (getTime() - e.extraTime >= 4 || e.extraTime < 0 || Math.abs(e.extraSpeed - speed) > 0.01) {
                e.extraTime = getTime(); e.extraSpeed = speed;
                e.extra[0] = physicalAim(e, speed, -1);
                int continuation = e.brain.motion.match();
                e.patternReady = continuation >= 0;
                e.extra[1] = e.patternReady ? physicalAim(e, speed, continuation) : angles[2];
                e.responseReady = getOthers() == 1 && e.brain.response.size >= 8;
                e.extra[2] = e.responseReady ? responseAim(e, current, input, speed, direct) : angles[2];
            }
            patternReady = e.patternReady; responseReady = e.responseReady;
            angles[7] = e.extra[0]; angles[8] = e.extra[1]; angles[9] = e.extra[2];
        }
        // Sem historico, usa preditiva circular. Depois, competicao de miras virtuais.
        int selected = 2;
        double best = -1;
        for (int i = 0; i < angles.length; i++) {
            if (i == 6 && !USE_VISIT_WAVES) continue;
            if (i == 8 && !patternReady || i == 9 && !responseReady) continue;
            double prior = i == 2 ? 0.22 : i == 1 ? 0.20 : 0.14;
            double score = (e.brain.gunHits[i] + prior * 8) / (e.brain.gunTrials[i] + 8);
            if (i == 3 && e.brain.gunSamples < 8) score *= 0.5;
            if (i == 4 && e.brain.gunNet.samples + GENERAL_GUN.samples < 20) score *= 0.5;
            if (i == 5 && e.brain.visitGun.size < 8) score *= 0.5;
            if (i == 6 && e.brain.realGun.size < 8) score *= 0.5;
            if (USE_EXTENDED_GUNS) {
                double trials = e.brain.contextTrials[motionMode][i];
                if (trials >= 8) {
                    double contextual = (e.brain.contextHits[motionMode][i] + prior * 8) / (trials + 8);
                    double weight = Math.min(0.45, trials / 80);
                    score = score * (1 - weight) + contextual * weight;
                }
                if (e.brain.gunTrials[i] < 8) {
                    if (motionMode == 2 && i == 1 || motionMode == 3 && i == 2) score += 0.025;
                }
            }
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
        // Ondas sem bala observam visitas; somente ondas de tiros avaliam as miras.
        Bullet bullet = null;
        if (getGunHeat() == 0 && Math.abs(error) <= Math.atan2(14, d)
                && getEnergy() > power + reserve()) bullet = setFireBullet(power);
        boolean realShot = bullet != null;
        if (!realShot && (!USE_VISIT_WAVES || outgoing.size() >= 96
                || getTime() - lastVirtualWave < (getOthers() == 1 ? 4 : 8))) return;
        if (realShot) {
            shots++;
            if (!celebrating && !roundReported)
                telemetry.shot(bullet, bullet.getPower(), d, getTime() - e.time);
            gunUsage[selected]++;
            solutionTarget = null;
            fired.put(bullet, e.brain);
        } else {
            virtualWaves++;
            lastVirtualWave = getTime();
        }
        {
            GunWave w = new GunWave();
            w.firing = realShot;
            w.motionMode = motionMode; w.duel = getOthers() == 1;
            w.startX = current.x; w.startY = current.y;
            w.owner = e; w.x = getX(); w.y = getY();
            w.time = getTime(); w.speed = speed;
            w.power = power; w.bearing = direct; w.direction = e.direction;
            w.segment = seg; w.angles = angles;
            w.features = input;
            w.previousX = current.x; w.previousY = current.y; w.previousTime = getTime();
            if (outgoing.size() < 96) outgoing.add(w);
        }
    }

    private boolean finisherAllowed() {
        if (target == null || !target.alive || target.sentry || getTime() - target.time > 2
                || target.energy <= 0 || target.energy > 12 || getEnergy() < 25
                || distance(getX(), getY(), target.x, target.y) > 600) return false;
        int known = 0;
        for (Enemy other : enemies.values()) if (other.alive && !other.sentry && getTime() - other.time <= 16) known++;
        if (known < getOthers()) return false;
        for (Enemy other : enemies.values()) if (other != target && other.alive && !other.sentry
                && (getTime() - other.time > 16 || distance(getX(), getY(), other.x, other.y) < 350)) return false;
        for (Wave wave : incoming) if ((distance(wave.x, wave.y, getX(), getY()) - wave.radius(getTime())) / wave.speed < 15)
            return false;
        return true;
    }

    // Projecao fisica condicionada ao padrao observado; nao conhece comandos futuros.
    private double physicalAim(Enemy e, double speed, int continuation) {
        MotionState state = new MotionState(e.x, e.y, e.heading, e.velocity);
        for (int age = 0; age < Math.min(8, getTime() - e.time); age++)
            state.step(e.turnRate, clamp(-8, state.velocity + e.acceleration, 8), width, height);
        for (int tick = 1; tick <= 96; tick++) {
            double turn = e.turnRate, desired = clamp(-8, state.velocity + e.acceleration, 8);
            if (continuation >= 0) {
                int slot = (continuation + tick) % MotionLog.CAPACITY;
                turn = e.brain.motion.turn[slot]; desired = e.brain.motion.velocity[slot];
            }
            state.step(turn, desired, width, height);
            if (tick * speed >= distance(getX(), getY(), state.x, state.y)) break;
        }
        return angle(getX(), getY(), state.x, state.y);
    }

    private double responseAim(Enemy e, Point2D.Double current, double[] input, double speed, double direct) {
        double[] vector = e.brain.response.predict(input);
        double dx = vector[0] * e.direction * Math.cos(direct) + vector[1] * Math.sin(direct);
        double dy = -vector[0] * e.direction * Math.sin(direct) + vector[1] * Math.cos(direct);
        double flight = current.distance(getX(), getY()) / speed, px = current.x, py = current.y;
        for (int i = 0; i < 6; i++) {
            px = clamp(18, current.x + dx * flight, width - 18);
            py = clamp(18, current.y + dy * flight, height - 18);
            flight = distance(getX(), getY(), px, py) / speed;
        }
        double proposed = normal(angle(getX(), getY(), px, py) - direct);
        double left = escapeOffset(e, speed, direct, -1), right = escapeOffset(e, speed, direct, 1);
        return direct + clamp(Math.min(left, right), proposed, Math.max(left, right));
    }

    // Estimativa assimetrica de escape com velocidade, giro e paredes.
    private double escapeOffset(Enemy e, double speed, double direct, int side) {
        MotionState state = new MotionState(e.x, e.y, e.heading, e.velocity);
        for (int t = 1; t <= 96; t++) {
            double desiredHeading = angle(getX(), getY(), state.x, state.y) + side * Math.PI / 2;
            for (int smooth = 0; smooth < 32; smooth++) {
                Point2D.Double point = project(state.x, state.y, desiredHeading, 100);
                if (inside(point.x, point.y, 20)) break;
                desiredHeading += side * 0.05;
            }
            double turn = normal(desiredHeading - state.heading);int drive = 1;
            if (Math.abs(turn) > Math.PI / 2) {turn = normal(turn + Math.PI);drive = -1;}
            state.step(turn, 8 * drive, width, height);
            if (t * speed >= distance(getX(), getY(), state.x, state.y)) break;
        }
        return normal(angle(getX(), getY(), state.x, state.y) - direct);
    }

    static class MotionState {
        double x,y,heading,velocity;
        MotionState(double x,double y,double h,double v){this.x=x;this.y=y;heading=h;velocity=v;}
        static double advanceVelocity(double velocity,double goal) {
            if (velocity * goal >= 0) {
                double limit = Math.abs(goal) > Math.abs(velocity) ? 1 : 2;
                return velocity + clamp(-limit,goal-velocity,limit);
            }
            double direction = sign(goal), next = nextVelocity(velocity * direction, 1000000) * direction;
            return goal > velocity ? Math.min(goal,next) : Math.max(goal,next);
        }
        void step(double turn,double goal,double width,double height) {
            double limit = Rules.getTurnRateRadians(velocity);
            heading = normal(heading + clamp(-limit,turn,limit));
            velocity = advanceVelocity(velocity,clamp(-8,goal,8));
            double nx=x+Math.sin(heading)*velocity,ny=y+Math.cos(heading)*velocity;
            x=clamp(18,nx,width-18);y=clamp(18,ny,height-18);
            if(x!=nx||y!=ny)velocity=0;
        }
    }

    static class MotionLog {
        static final int CAPACITY=512, FUTURE=96, PREFIX=8;
        final double[] velocity=new double[CAPACITY],turn=new double[CAPACITY];
        final long[] tick=new long[CAPACITY];final int[] round=new int[CAPACITY];
        int seen;double lastHeading;
        void observe(int r,long time,double heading,double v) {
            if(seen>0){int prev=(seen-1)%CAPACITY;if(round[prev]==r&&tick[prev]==time)return;}
            int slot=seen%CAPACITY,prev=(seen-1+CAPACITY)%CAPACITY;
            turn[slot]=seen>0&&round[prev]==r&&tick[prev]+1==time?normal(heading-lastHeading):Double.NaN;
            velocity[slot]=v;round[slot]=r;tick[slot]=time;lastHeading=heading;seen++;
        }
        boolean contiguous(int start,int end) {
            if(start<Math.max(0,seen-CAPACITY)||end>=seen)return false;
            for(int i=start+1;i<=end;i++)if(!Double.isFinite(turn[i%CAPACITY]))return false;
            return true;
        }
        int mode() {
            if(seen<13||!contiguous(seen-13,seen-1))return 0;
            double speed=0,mean=0,square=0;int reversals=0;
            for(int i=seen-12;i<seen;i++){
                int j=i%CAPACITY,p=(i-1)%CAPACITY;speed+=Math.abs(velocity[j]);mean+=turn[j];square+=turn[j]*turn[j];
                if(velocity[j]*velocity[p]<0||turn[j]*turn[p]<-0.0001)reversals++;
            }
            speed/=12;mean/=12;square=square/12-mean*mean;
            if(speed<0.4)return 1;
            if(reversals>=2)return 4;
            if(Math.abs(mean)<0.007&&square<0.0002)return 2;
            if(Math.abs(mean)>0.01&&square<0.0003)return 3;
            return 0;
        }
        int match() {
            if(seen<PREFIX+FUTURE+2||!contiguous(seen-PREFIX,seen-1))return -1;
            int best=-1;double bestError=1.5;
            int first=Math.max(PREFIX,seen-CAPACITY+PREFIX);
            for(int end=first;end+FUTURE<seen-PREFIX;end+=3){
                if(!contiguous(end-PREFIX+1,end+FUTURE))continue;
                double error=0;
                for(int k=0;k<PREFIX;k++){
                    int a=(seen-1-k)%CAPACITY,b=(end-k)%CAPACITY;
                    double dv=(velocity[a]-velocity[b])/2,dt=(turn[a]-turn[b])/0.035;
                    error+=(dv*dv+dt*dt)/PREFIX;
                }
                if(error<bestError){bestError=error;best=end;}
            }
            return best;
        }
    }

    static class ResponseBank {
        static final int CAPACITY=256;
        final double[][] features=new double[CAPACITY][];
        final double[] lateral=new double[CAPACITY],forward=new double[CAPACITY];
        int seen,size;
        void observe(double[] input,double lat,double fwd){
            int i=seen%CAPACITY;double length=Math.hypot(lat,fwd),scale=length>8?8/length:1;
            features[i]=input.clone();lateral[i]=lat*scale;forward[i]=fwd*scale;seen++;size=Math.min(CAPACITY,size+1);
        }
        double[] view(double[] input,int window,int k){
            k=Math.min(k,Math.min(size,window));double[] distances=new double[k];int[] indices=new int[k];
            java.util.Arrays.fill(distances,Double.POSITIVE_INFINITY);
            for(int age=0;age<Math.min(size,window);age++){
                int i=(seen-1-age)%CAPACITY;double d=0;
                for(int f=0;f<12;f++){double delta=input[f]-features[i][f];d+=delta*delta*GunContext.WEIGHTS[f];}
                for(int j=0;j<k;j++)if(d<distances[j]){
                    for(int n=k-1;n>j;n--){distances[n]=distances[n-1];indices[n]=indices[n-1];}
                    distances[j]=d;indices[j]=i;break;
                }
            }
            double lat=0,fwd=0,total=0;
            for(int j=0;j<k;j++){double weight=1/(0.05+distances[j]);lat+=lateral[indices[j]]*weight;fwd+=forward[indices[j]]*weight;total+=weight;}
            return total>0?new double[]{lat/total,fwd/total}:new double[2];
        }
        double[] predict(double[] input){double[] all=view(input,256,8),recent=view(input,32,4);return new double[]{all[0]*0.65+recent[0]*0.35,all[1]*0.65+recent[1]*0.35};}
    }

    private double reserve() { return getOthers() > 1 ? 2.5 : 0.6; }

    private double firePower(Enemy e, double d) {
        double accuracy = (e.brain.actualHits + 2.0) / (e.brain.actualShots + 8.0);
        double power = d < 150 ? 2.9 : d < 350 ? 2.3 : 1.6;
        power *= GENOME[5];
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
                    brain.gunNet.observe(w.features, gf);
                    GENERAL_GUN.observe(w.features, gf);
                    if (USE_KNN_GUN) {
                        brain.visitGun.observe(w.features, gf);
                        if (w.firing) brain.realGun.observe(w.features, gf);
                    }
                    if (USE_EXTENDED_GUNS && w.firing && w.duel) {
                        double elapsed = Math.max(1, distance(w.x, w.y, x, y) / w.speed);
                        double dx = x - w.startX, dy = y - w.startY;
                        double lateralResponse = (dx * Math.cos(w.bearing) - dy * Math.sin(w.bearing)) * w.direction / elapsed;
                        double forwardResponse = (dx * Math.sin(w.bearing) + dy * Math.cos(w.bearing)) / elapsed;
                        brain.response.observe(w.features, lateralResponse, forwardResponse);
                    }
                    double actual = angle(w.x, w.y, x, y);
                    double tolerance = Math.atan2(21, distance(w.x, w.y, x, y));
                    if (w.firing) for (int gun = 0; gun < w.angles.length; gun++) {
                        brain.gunTrials[gun] = brain.gunTrials[gun] * 0.98 + 1;
                        brain.gunHits[gun] = brain.gunHits[gun] * 0.98
                                + (Math.abs(normal(w.angles[gun] - actual)) < tolerance ? 1 : 0);
                        if (USE_EXTENDED_GUNS) {
                            brain.contextTrials[w.motionMode][gun] = brain.contextTrials[w.motionMode][gun] * 0.98 + 1;
                            brain.contextHits[w.motionMode][gun] = brain.contextHits[w.motionMode][gun] * 0.98
                                    + (Math.abs(normal(w.angles[gun] - actual)) < tolerance ? 1 : 0);
                        }
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

    private double matchBullet(Bullet bullet, double x, double y, String shooter, boolean bodyHit) {
        Wave best = null;
        double error = Double.POSITIVE_INFINITY;
        for (Wave w : incoming) {
            if (!w.owner.name.equals(shooter) || Math.abs(w.speed - bullet.getVelocity()) > 0.1) continue;
            double delta = Math.abs(w.radius(getTime()) - distance(w.x, w.y, x, y));
            if (delta < error && delta < w.speed * 2.5 + 18) { error = delta; best = w; }
        }
        if (best != null) {
            if (bodyHit && !celebrating && !roundReported)
                telemetry.matchConfidence += best.confidence;
            if (USE_FLATTENER && bodyHit && best.confidence >= 0.8) {
                recordPass(best, getX(), getY());
                best.owner.brain.taken = Math.min(best.owner.brain.passed, best.owner.brain.taken + 1);
            }
            double gf = guessFactor(best, x, y);
            learn(best.owner.brain.surf[best.segment], gf, 1, 0.96);
            learn(best.owner.brain.surfGlobal, gf, 1, 0.98);
            best.owner.brain.surfSamples++;
            best.owner.brain.surfNet.observe(best.features, gf);
            GENERAL_SURF.observe(best.features, gf);
            incoming.remove(best);
            wavesMatched++;
            return error;
        }
        return -1;
    }

    public void onHitByBullet(HitByBulletEvent e) {
        Enemy shooter = enemies.get(e.getName());
        if (shooter != null) {
            shooter.energy += Rules.getBulletHitBonus(e.getPower());
            shooter.threatScore = Math.min(4, threatPressure(shooter.threatScore,
                    getTime() - shooter.threatTime) + Rules.getBulletDamage(e.getPower()) / 8);
            shooter.threatTime = getTime();
        }
        double radialError = matchBullet(e.getBullet(), e.getBullet().getX(), e.getBullet().getY(), e.getName(), true);
        if (!celebrating && !roundReported) telemetry.received(e.getPower(), radialError);
        lastPlan = -100;
    }

    public void onBulletHit(BulletHitEvent e) {
        hits++;
        if (!celebrating && !roundReported) telemetry.hit(e.getBullet(), e.getBullet().getPower());
        Enemy victim = enemies.get(e.getName());
        if (victim != null) victim.energy -= Rules.getBulletDamage(e.getBullet().getPower());
        finishShot(e.getBullet(), true);
    }

    public void onBulletMissed(BulletMissedEvent e) { finishShot(e.getBullet(), false); }

    public void onBulletHitBullet(BulletHitBulletEvent e) {
        Bullet other = e.getHitBullet();
        matchBullet(other, other.getX(), other.getY(), other.getName(), false);
        finishShot(e.getBullet(), false);
    }

    // Registro de tiros reais e separado das ondas virtuais, que podem passar antes do impacto.
    private final Map<Bullet, Brain> fired = new HashMap<Bullet, Brain>();

    private void finishShot(Bullet bullet, boolean hit) {
        telemetry.release(bullet);
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

    public void onDeath(DeathEvent e) { reportRound(); }

    public void onRoundEnded(RoundEndedEvent e) { reportRound(); }

    private void reportRound() {
        if (roundReported) return;
        roundReported = true;
        out.println("TitanPrime round=" + getRoundNum() + " shots=" + shots + " hits=" + hits
                + " waves=" + wavesDetected + " matched=" + wavesMatched
                + " walls=" + wallHits + " skipped=" + skipped + " virtual=" + virtualWaves);
        out.println("GUN_USAGE " + java.util.Arrays.toString(gunUsage));
        out.println(telemetry.line(getRoundNum()));
        for (Enemy enemy : enemies.values()) {
            out.println("learn " + enemy.name + " gun=" + enemy.brain.gunSamples
                    + " surf=" + enemy.brain.surfSamples + " neuralGun=" + enemy.brain.gunNet.samples
                    + " passed=" + (int) enemy.brain.passed + " taken=" + (int) enemy.brain.taken);
        }
        if (EXPORT_LEARNING && getRoundNum() == getNumRounds() - 1) {
            out.println("MODEL_GUN " + GENERAL_GUN.pack());
            out.println("MODEL_SURF " + GENERAL_SURF.pack());
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
        g.drawString("Titan Prime | " + (getOthers() == 1 ? "DUEL SURF" : "MELEE RISK+WAVES")
                + " | waves " + incoming.size() + " | neural " + GENERAL_GUN.samples + " | V: visao", 15, 20);
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

    // Inspirado nos conceitos de ThreatTracker e visitas do Titan enviado pelo
    // usuario. Implementacao propria, com confianca de ondas e limites de CPU.
    static double threatPressure(double score, long age) {
        return age > 320 ? 0 : clamp(0, score, 4) * Math.pow(0.5, Math.max(0, age) / 80.0);
    }

    static double flattenAmount(double passed, double hits) {
        if (passed < 8 || hits < 2) return 0;
        return 0.30 * clamp(0, (hits / passed - 0.08) / 0.16, 1);
    }

    static double[] normalizedVisits(double[] visits) {
        double total = 0.25 * BINS;
        for (double value : visits) total += value;
        double[] density = new double[BINS];
        for (int i = 0; i < BINS; i++) density[i] = (visits[i] + 0.25) / total;
        return density;
    }

    private void recordPass(Wave w, double x, double y) {
        if (w.visited || w.confidence < 0.8) return;
        w.visited = true;
        Brain b = w.owner.brain;
        b.passed = b.passed * 0.98 + 1;
        b.taken *= 0.98;
        learn(b.visits, guessFactor(w, x, y), 1, 0.99);
    }

    private double[] features(double x, double y, double heading, double velocity,
            double acceleration, double bearing, double dist, double speed, int direction,
            double turnRate) {
        Point2D.Double forward = project(x, y, bearing + direction * Math.PI / 2, 120);
        Point2D.Double reverse = project(x, y, bearing - direction * Math.PI / 2, 120);
        return new double[] {clamp(0, dist / 800, 1),
                Math.abs(velocity * Math.sin(heading - bearing)) / 8,
                velocity * Math.cos(heading - bearing) / 8,
                clamp(-1, acceleration / 2, 1),
                clamp(-1, turnRate * direction / (Math.PI / 18), 1),
                clamp(0, wallDistance(x, y) / 200, 1),
                clamp(-1, wallDistance(forward.x, forward.y) / 200, 1),
                clamp(-1, wallDistance(reverse.x, reverse.y) / 200, 1),
                clamp(0, dist / speed / 70, 1),
                Math.abs(velocity) / 8, clamp(0, (getOthers() - 1) / 7.0, 1),
                clamp(0, speed / 20, 1)};
    }

    static int gfBin(double gf) {
        return (int) Math.round((clamp(-1, gf, 1) + 1) * (BINS - 1) / 2);
    }

    private static double[] neuralPrediction(Neural local, Neural general, double[] input) {
        double[] a = local.predict(input), b = general.predict(input);
        double localWeight = Math.min(0.85, local.samples / 100.0);
        for (int i = 0; i < BINS; i++) a[i] = a[i] * localWeight + b[i] * (1 - localWeight);
        return a;
    }

    static double bestNeuralFactor(double[] distribution, double halfWidth) {
        int best = BINS / 2;
        double bestMass = -1;
        // Probabilidade de acertar uma largura de robo, em vez de apenas o pico.
        int radius = Math.max(1, Math.min(6, (int) Math.ceil(halfWidth * (BINS - 1) / 2)));
        for (int i = 0; i < BINS; i++) {
            double mass = 0;
            for (int j = Math.max(0, i - radius); j <= Math.min(BINS - 1, i + radius); j++) {
                mass += distribution[j];
            }
            if (mass > bestMass + 1e-10 || (Math.abs(mass - bestMass) < 1e-10
                    && Math.abs(i - BINS / 2) < Math.abs(best - BINS / 2))) {
                bestMass = mass; best = i;
            }
        }
        return (best - (BINS - 1) / 2.0) / ((BINS - 1) / 2.0);
    }

    /** MLP 12 -> 16 tanh -> 47 softmax. SGD + replay limitado, sem bibliotecas. */
    static class Neural {
        static final int INPUTS = 12, HIDDEN = 16, REPLAY = 96;
        final double[][] first = new double[HIDDEN][INPUTS + 1];
        final double[][] second = new double[BINS][HIDDEN + 1];
        final double[][] replay = new double[REPLAY][];
        final double[] targets = new double[REPLAY];
        int samples;

        Neural(String packed) {
            for (int h = 0; h < HIDDEN; h++) for (int i = 0; i <= INPUTS; i++) {
                first[h][i] = 0.35 * Math.sin((h + 1) * (i + 3) * 1.17 + GENOME[6]) * GENOME[7];
            }
            for (int k = 0; k < BINS; k++) for (int h = 0; h <= HIDDEN; h++) {
                second[k][h] = 0.03 * Math.cos((h + 4) * (k + 1) * 0.73 + GENOME[8]) * GENOME[9];
            }
            if (!packed.isEmpty()) unpack(packed);
        }

        private double[] hidden(double[] input) {
            if (input == null || input.length != INPUTS) throw new IllegalArgumentException("neural input");
            double[] result = new double[HIDDEN];
            for (int h = 0; h < HIDDEN; h++) {
                double sum = first[h][INPUTS];
                for (int i = 0; i < INPUTS; i++) sum += first[h][i] * input[i];
                result[h] = Math.tanh(sum);
            }
            return result;
        }

        private double[] output(double[] hidden) {
            double[] p = new double[BINS];
            double max = -Double.MAX_VALUE, sum = 0;
            for (int k = 0; k < BINS; k++) {
                double value = second[k][HIDDEN];
                for (int h = 0; h < HIDDEN; h++) value += second[k][h] * hidden[h];
                p[k] = value; max = Math.max(max, value);
            }
            for (int k = 0; k < BINS; k++) { p[k] = Math.exp(p[k] - max); sum += p[k]; }
            for (int k = 0; k < BINS; k++) p[k] /= sum;
            return p;
        }

        double[] predict(double[] input) { return output(hidden(input)); }

        void observe(double[] input, double gf) {
            if (input == null) return;
            train(input, gf);
            // O replay ocupa memoria limitada. Um exemplo antigo por observacao.
            int index = samples % REPLAY;
            int older = (samples * 37 + 11) % REPLAY;
            if (replay[older] != null) train(replay[older], targets[older]);
            replay[index] = input.clone(); targets[index] = gf;
            samples++;
        }

        private void train(double[] input, double gf) {
            double[] h = hidden(input), p = output(h), dh = new double[HIDDEN];
            double center = (clamp(-1, gf, 1) + 1) * (BINS - 1) / 2;
            double[] label = new double[BINS];
            double sum = 0;
            for (int k = 0; k < BINS; k++) { label[k] = Math.exp(-Math.pow((k - center) / 1.5, 2) / 2); sum += label[k]; }
            double rate = GENOME[2];
            for (int k = 0; k < BINS; k++) {
                double delta = clamp(-1, p[k] - label[k] / sum, 1);
                for (int j = 0; j < HIDDEN; j++) {
                    dh[j] += delta * second[k][j];
                    second[k][j] = clamp(-4, second[k][j] - rate * (delta * h[j] + 0.00001 * second[k][j]), 4);
                }
                second[k][HIDDEN] = clamp(-4, second[k][HIDDEN] - rate * delta, 4);
            }
            for (int j = 0; j < HIDDEN; j++) {
                double delta = clamp(-1, dh[j] * (1 - h[j] * h[j]), 1);
                for (int i = 0; i < INPUTS; i++) {
                    first[j][i] = clamp(-4, first[j][i] - rate * (delta * input[i] + 0.00001 * first[j][i]), 4);
                }
                first[j][INPUTS] = clamp(-4, first[j][INPUTS] - rate * delta, 4);
            }
        }

        String pack() {
            try {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream data = new java.io.DataOutputStream(bytes);
                data.writeInt(0x4e4d5333); data.writeInt(samples);
                for (double[] row : first) for (double value : row) data.writeFloat((float) value);
                for (double[] row : second) for (double value : row) data.writeFloat((float) value);
                data.close();
                return java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        }

        private void unpack(String packed) {
            try {
                byte[] bytes = java.util.Base64.getDecoder().decode(packed);
                java.io.DataInputStream data = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes));
                if (bytes.length != 4036 || data.readInt() != 0x4e4d5333) throw new java.io.IOException("model format");
                samples = data.readInt();
                if (samples < 0 || samples > 10000000) throw new java.io.IOException("model samples");
                for (double[] row : first) for (int i = 0; i < row.length; i++) row[i] = checkedWeight(data.readFloat());
                for (double[] row : second) for (int i = 0; i < row.length; i++) row[i] = checkedWeight(data.readFloat());
                data.close();
            } catch (java.io.IOException e) { throw new IllegalArgumentException("Invalid TitanPrime model", e); }
        }

        private double checkedWeight(double value) throws java.io.IOException {
            if (!Double.isFinite(value) || Math.abs(value) > 4.001) throw new java.io.IOException("model weight");
            return value;
        }
    }

    static class Telemetry {
        long decisions, noTarget, scans, scanGaps, gapSum, gapMax;
        long shots, hits, aimAgeSum, aimAgeMax, received, matched, unmatched, unknownHitRange;
        double spent, dealtNominal, receivedNominal, hitBonusNominal;
        double matchErrorSum, matchErrorMax, matchConfidence;
        final long[] shotsByRange = new long[3], hitsByRange = new long[3];
        final Map<Object, Integer> shotRanges = new HashMap<Object, Integer>();
        void decision(boolean targetAvailable) { decisions++; if (!targetAvailable) noTarget++; }
        void scan(long gap) {
            scans++;
            if (gap > 0) { scanGaps++; gapSum += gap; gapMax = Math.max(gapMax, gap); }
        }
        static int range(double distance) { return distance < 200 ? 0 : distance < 400 ? 1 : 2; }
        void shot(Object bullet, double power, double distance, long age) {
            shots++; spent += power;
            aimAgeSum += age; aimAgeMax = Math.max(aimAgeMax, age);
            int bin = range(distance); shotsByRange[bin]++;
            if (shotRanges.size() < 64) shotRanges.put(bullet, bin);
        }
        void hit(Object bullet, double power) {
            hits++; dealtNominal += Rules.getBulletDamage(power);
            hitBonusNominal += Rules.getBulletHitBonus(power);
            Integer bin = shotRanges.get(bullet);
            if (bin == null) unknownHitRange++; else hitsByRange[bin]++;
        }
        void release(Object bullet) { shotRanges.remove(bullet); }
        void received(double power, double error) {
            received++; receivedNominal += Rules.getBulletDamage(power);
            if (Double.isFinite(error) && error >= 0) {
                matched++; matchErrorSum += error; matchErrorMax = Math.max(matchErrorMax, error);
            } else unmatched++;
        }
        String line(int round) {
            return "TELEMETRY v=1 round=" + round + " decisions=" + decisions + " no_target=" + noTarget
                    + " scans=" + scans + " gap_count=" + scanGaps + " gap_sum=" + gapSum + " gap_max=" + gapMax
                    + " shots=" + shots + " hits=" + hits + " aim_age_sum=" + aimAgeSum + " aim_age_max=" + aimAgeMax
                    + " spent=" + spent + " dealt_nominal=" + dealtNominal + " received_nominal=" + receivedNominal
                    + " bonus_nominal=" + hitBonusNominal + " received=" + received + " body_matched=" + matched
                    + " body_unmatched=" + unmatched + " error_sum=" + matchErrorSum + " error_max=" + matchErrorMax
                    + " confidence_sum=" + matchConfidence + " unknown_hit_range=" + unknownHitRange
                    + " shots_range=" + shotsByRange[0] + "," + shotsByRange[1] + "," + shotsByRange[2]
                    + " hits_range=" + hitsByRange[0] + "," + hitsByRange[1] + "," + hitsByRange[2];
        }
    }

    // KNN de mira por adversario: visitas e respostas a tiros usam bancos separados.
    static class GunContext {
        static final int CAPACITY = 256;
        static final double[] WEIGHTS = {2, 5, 1, 1, 1, 0.5, 2, 2, 1, 1, 0.5, 1};
        final double[][] inputs = new double[CAPACITY][];
        final double[] factors = new double[CAPACITY];
        final long[] stamps = new long[CAPACITY];
        int size;
        long seen;
        void observe(double[] input, double factor) {
            int slot = (int) (seen % CAPACITY);
            inputs[slot] = input.clone(); factors[slot] = clamp(-1, factor, 1); stamps[slot] = seen++;
            size = Math.min(CAPACITY, size + 1);
        }
        double[] predict(double[] input) {
            int k = Math.min(size, Math.max(3, (int) Math.sqrt(size)));
            int[] neighbors = new int[k];
            double[] distances = new double[k];
            java.util.Arrays.fill(distances, Double.POSITIVE_INFINITY);
            for (int i = 0; i < size; i++) {
                double distance = 0;
                for (int j = 0; j < WEIGHTS.length; j++) {
                    double d = inputs[i][j] - input[j]; distance += d * d * WEIGHTS[j];
                }
                for (int j = 0; j < k; j++) if (distance < distances[j]) {
                    for (int n = k - 1; n > j; n--) {distances[n] = distances[n - 1]; neighbors[n] = neighbors[n - 1];}
                    distances[j] = distance; neighbors[j] = i; break;
                }
            }
            double[] density = new double[BINS];
            java.util.Arrays.fill(density, 0.001);
            for (int n = 0; n < k; n++) {
                int i = neighbors[n];
                double weight = Math.pow(0.995, seen - 1 - stamps[i]) / (0.05 + distances[n]);
                for (int b = 0; b < BINS; b++) {
                    double delta = (2.0 * b / (BINS - 1) - 1 - factors[i]) / 0.10;
                    density[b] += weight / (1 + delta * delta);
                }
            }
            double sum = 0; for (double d : density) sum += d;
            for (int i = 0; i < BINS; i++) density[i] /= sum;
            return density;
        }
    }

    static class Brain {
        final double[][] gun = new double[SEGMENTS][BINS], surf = new double[SEGMENTS][BINS];
        final double[] gunGlobal = new double[BINS], surfGlobal = new double[BINS];
        final double[] gunTrials = new double[10], gunHits = new double[10];
        final double[][] contextTrials = new double[5][10], contextHits = new double[5][10];
        final MotionLog motion = new MotionLog();
        final ResponseBank response = new ResponseBank();
        final GunContext visitGun = new GunContext(), realGun = new GunContext();
        final double[] visits = new double[BINS];
        double passed, taken;
        final Neural gunNet = new Neural(""), surfNet = new Neural("");
        int gunSamples, surfSamples;
        double actualShots, actualHits;
    }
    static class Enemy {
        final String name;
        final Brain brain;
        double x, y, heading, velocity, energy, turnRate, acceleration;
        double threatScore;
        long threatTime;
        long time = -1, nextFire;
        int direction = 1;
        boolean alive = true, sentry, patternReady, responseReady;
        long extraTime = -100;
        double extraSpeed;
        final double[] extra = new double[3];
        Enemy(String name, Brain brain) { this.name = name; this.brain = brain; }
    }
    static class Forecast {
        final Enemy enemy;
        double pressure;
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
        double[] features, neuralDanger;
        double[] visitDanger;
        double flatten;
        boolean visited;
        int direction, segment;
        long time;
        double radius(long now) { return Math.max(0, now - time) * speed; }
    }
    static class GunWave extends Wave {
        boolean firing, duel;
        int motionMode;
        double startX, startY;
        double[] angles;
        double previousX, previousY;
        long previousTime;
    }
}
