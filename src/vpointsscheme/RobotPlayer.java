package vpointsscheme;
import battlecode.common.*;

import java.util.Random;

/**
 * Created by Max_Inspiron15 on 1/10/2017.
 */
public strictfp class RobotPlayer {
    static RobotController rc;
    static Direction[] dirList = new Direction[4];
    static Direction goingDir;
    static Random rand;
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        initDirList();
        rand = new Random(rc.getID());
        goingDir = randomDir();
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
        }
    }
    public static Direction randomDir(){
        return dirList[rand.nextInt(4)];
    }
    public static void initDirList(){
        for(int i=0;i<4;i++){
            float radians = (float)(-Math.PI + 2*Math.PI*((float)i)/4);
            dirList[i]=new Direction(radians);
            System.out.println("made new direction "+dirList[i]);
        }
    }
    public static void runArchon() {
        while(true){
            try{
            	if(rc.getTeamBullets()>=10000){
            		rc.donate(rc.getTeamBullets());
            	}
                //TODO count gardeners
                //try to build gardeners
                //can you build a gardener?
                tryToBuild(RobotType.GARDENER,RobotType.GARDENER.bulletCost);
                //System.out.println("bytecode usage is "+Clock.getBytecodeNum());
                Clock.yield();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    public static void runGardener(){
        while(true){
            try{
                //first try to plant trees
                tryToPlant();
                //now try to water trees
                tryToWater();
                //move around
                if(rc.canMove(goingDir)){
                    rc.move(goingDir);
                }else{
                    goingDir = randomDir();
                }
                Clock.yield();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    public static void runSoldier(){

    }
    public static void runLumberjack(){

    }
    public static void tryToWater() throws GameActionException{
        if(rc.canWater()) {
            TreeInfo[] nearbyTrees = rc.senseNearbyTrees();
            for (int i = 0; i < nearbyTrees.length; i++)
                if(nearbyTrees[i].getHealth()<GameConstants.BULLET_TREE_MAX_HEALTH-GameConstants.WATER_HEALTH_REGEN_RATE) {
                    if (rc.canWater(nearbyTrees[i].getID())) {
                        rc.water(nearbyTrees[i].getID());
                        break;
                    }
                }
        }
    }
    public static void tryToBuild(RobotType type, int moneyNeeded) throws GameActionException{
        //try to build gardeners
        //can you build a gardener?
        if(rc.getTeamBullets()>moneyNeeded) {//have enough bullets. assuming we haven't built already.
            for (int i = 0; i < 4; i++) {
                if(rc.canBuildRobot(type,dirList[i])){
                    rc.buildRobot(type,dirList[i]);
                    break;
                }
            }
        }
    }
    public static void tryToPlant() throws GameActionException{
        //try to build gardeners
        //can you build a gardener?
        if(rc.getTeamBullets()>GameConstants.BULLET_TREE_COST) {//have enough bullets. assuming we haven't built already.
            for (int i = 0; i < 4; i++) {
                //only plant trees on a sub-grid
                MapLocation p = rc.getLocation().add(dirList[i],GameConstants.GENERAL_SPAWN_OFFSET+GameConstants.BULLET_TREE_RADIUS+rc.getType().bodyRadius);
                if(modGood(p.x,6,0.2f)&&modGood(p.y,6,0.2f)) {
                    if (rc.canPlantTree(dirList[i])) {
                        rc.plantTree(dirList[i]);
                        break;
                    }
                }
            }
        }
    }
    public static boolean modGood(float number,float spacing, float fraction){
        return (number%spacing)<spacing*fraction;
    }
}