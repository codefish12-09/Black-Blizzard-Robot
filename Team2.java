package team2;
        import robocode.*;
        import java.awt.*;
        import java.awt.geom.*;
        import java.util.*;
        import java.util.List;
        import robocode.util.*;

//import java.awt.Color;

// API help : https://robocode.sourceforge.io/docs/robocode/robocode/Robot.html

/**
 * BlackBlizzard - a robot by (your name here)
 */

public class Team2 extends AdvancedRobot {
    //initial

    static Boolean hithithit = false;
    boolean movingForward;
    team2.enemyState enemy = new team2.enemyState();
    //pattern match
    private static final int MAX_PATTERN_LENGTH = 30;
    private static Map<String, int[]> matcher = new HashMap<String, int[]>(40000);
    private static String enemyHistory;
    //predict
    private static double FIRE_POWER = 3;
    private static double FIRE_SPEED = Rules.getBulletSpeed(FIRE_POWER);
    private static List<Point2D.Double> predictions = new ArrayList<Point2D.Double>();
    //move
    static final double BASE_MOVEMENT = 180;
    static final double BASE_TURN = Math.PI / 1.5;//120°
    static double movement;

    public void run() {
        setAdjustGunForRobotTurn(true);//车炮分离
        setAdjustRadarForGunTurn(true);//枪和雷达分离
        setBodyColor(Color.WHITE);
        setGunColor(Color.WHITE);
        setRadarColor(Color.BLACK);
        setScanColor(Color.WHITE);
        enemyHistory = "";
        movement = Double.POSITIVE_INFINITY;
        setTurnRadarRight(400);//雷达扫描角度
        do {
            scan();
            if (getDistanceRemaining() == 0) {//返回剩余距离
                setAhead(movement = -movement);
                setTurnRightRadians(BASE_TURN);//身体向右转120°
                hithithit = false;
            }
        } while (true);
    }


    public void onHitWall(HitWallEvent e) {
        if (Math.abs(movement) > BASE_MOVEMENT) {
            movement = BASE_MOVEMENT;
        }
    }

    //当有机器人死亡
    public void onRobotDeath(RobotDeathEvent e) {
        setTurnRadarRight(400);
    }

    //当机器人被炮弹击中
    public void onHitByBullet(HitByBulletEvent e)
    {
        setTurnRadarRight(400);
        if(getX()>150&&getY()>150&&enemy.x-getX()>150&&enemy.y-getY()>150)
        {
            double dist=150;
            double a=normalizeBearing(90 - (getHeading() - e.getHeading()));
            if(Math.abs(a)>Math.PI/2)
            {
                a=normalizeBearing(a+Math.PI);
            }
            setTurnRight(a);
            setAhead(dist);
            dist *= -1;
        }
    }
    public double normalizeBearing( double angle )
    {
        if ( angle < -Math.PI )
            angle += 2*Math.PI;
        if ( angle > Math.PI )
            angle -= 2*Math.PI;
        return angle;
    }


    //当机器人击中另一个机器人
    public void onHitRobot(HitRobotEvent e) {
        if (hithithit == false) {
            double absoluteBearing = e.getBearingRadians() + getHeadingRadians();//偏移量
            turnRadarRightRadians(Utils.normalRelativeAngle(absoluteBearing - getRadarHeadingRadians()));//
            hithithit = true;
        }
    }

    //当扫描到一个机器人
    public void onScannedRobot(ScannedRobotEvent e) {
        //updata
        enemy.update(e, this);//更新敌人信息
        //fire
        if (getGunTurnRemaining() == 0 && getEnergy() > 1) {
            smartFire();
        }
        //track
        trackHim();
        // memorize
        if (enemy.thisStep == (char) -1) {
            return;
        }
        record(enemy.thisStep);
        enemyHistory = (char) enemy.thisStep + enemyHistory;
        // aim
        predictions.clear();
        Point2D.Double myP = new Point2D.Double(getX(), getY());
        Point2D.Double enemyP = project(myP, enemy.absoluteBearing, e.getDistance());
        String pattern = enemyHistory;
        for (double d = 0; d < myP.distance(enemyP); d += FIRE_SPEED) {
            int nextStep = predict(pattern);
            enemy.decode(nextStep);
            enemyP = project(enemyP, enemy.headingRadian, enemy.velocity);
            predictions.add(enemyP);
            pattern = (char) nextStep + pattern;
        }

        enemy.absoluteBearing = Math.atan2(enemyP.x - myP.x, enemyP.y - myP.y);
        double gunTurn = enemy.absoluteBearing - getGunHeadingRadians();
        setTurnGunRightRadians(Utils.normalRelativeAngle(gunTurn));
    }

    public void smartFire() {
        //FIRE_POWER =3;
        FIRE_POWER = Math.min(Math.min(getEnergy() / 6d, 1000d / enemy.distance), enemy.energy / 3d);
        FIRE_SPEED = Rules.getBulletSpeed(FIRE_POWER);
        setFire(FIRE_POWER);
    }

    public void trackHim() {
        double RadarOffset;
        RadarOffset = Utils.normalRelativeAngle(enemy.absoluteBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(RadarOffset * 2);
    }

    private void record(int thisStep) {
        int maxLength = Math.min(MAX_PATTERN_LENGTH, enemyHistory.length());
        for (int i = 0; i <= maxLength; ++i) {
            String pattern = enemyHistory.substring(0, i);
            int[] frequencies = matcher.get(pattern);
            if (frequencies == null) {
                // frequency tables need to hold 21 possible dh values times 17 possible v values
                frequencies = new int [21 * 17];
                matcher.put(pattern, frequencies);
            }
            ++frequencies[thisStep];
        }

    }

    private int predict(String pattern) {
        int[] frequencies = null;
        for (int patternLength = Math.min(pattern.length(), MAX_PATTERN_LENGTH); frequencies == null; --patternLength) {
            frequencies = matcher.get(pattern.substring(0, patternLength));
        }
        int nextTick = 0;
        for (int i = 1; i < frequencies.length; ++i) {
            if (frequencies[nextTick] < frequencies[i]) {
                nextTick = i;
            }
        }
        return nextTick;
    }

    private static Point2D.Double project(Point2D.Double p, double angle,
                                          double distance) {
        double x = p.x + distance * Math.sin(angle);
        double y = p.y + distance * Math.cos(angle);
        return new Point2D.Double(x, y);
    }
}

//**ENEMY_CLASS**///
class enemyState {

    public double headingRadian = 0.0D;
    public double bearingRadian = 0.0D;
    public double distance = 0.0D;
    public double absoluteBearing = 0.0D;
    public double x = 0.0D;
    public double y = 0.0D;
    public double velocity = 0.0D;
    public double energy = 100.0D;
    //addition
    public double lastEnemyHeading = 0;
    public int thisStep = 0;

    //the currently data is important, we should get it when we use it.
    public void update(ScannedRobotEvent e, AdvancedRobot me) {
        headingRadian = e.getHeadingRadians();
        bearingRadian = e.getBearingRadians();
        distance = e.getDistance();
        absoluteBearing = bearingRadian + me.getHeadingRadians();
        x = me.getX() + Math.sin(absoluteBearing) * distance;
        y = me.getY() + Math.cos(absoluteBearing) * distance;
        velocity = e.getVelocity();
        energy = e.getEnergy();
        //addition
        thisStep = encode(headingRadian - lastEnemyHeading, velocity);
        lastEnemyHeading = headingRadian;
    }

    public static int encode(double dh, double v) {
        if (Math.abs(dh) > Rules.MAX_TURN_RATE_RADIANS) {
            return (char) -1;
        }
        //取正
        //-10<toDegrees(dh)<10 ; -8<v<8 ;
        //so we add with 10 and 8
        int dhCode = (int) Math.rint(Math.toDegrees(dh)) + 10;
        int vCode = (int) Math.rint(v + 8);
        return (char) (17 * dhCode + vCode);
    }

    public void decode(int symbol) {
        headingRadian += Math.toRadians(symbol / 17 - 10);
        velocity = symbol % 17 - 8;
    }
}
