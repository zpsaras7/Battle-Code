package treestreestrees;
import battlecode.common.*;

import java.util.Random;

public strictfp class RobotPlayer {
	private static final byte CHANNEL_SCOUT = 3;
	private static final byte CHANNEL_COMG = 2;
	private static final byte CHANNEL_G = 1; //bits (MSB->LSB): NA, NA, NA, #alive
	

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
		int numGardenersCreated = 0;
		int minNumGardeners = 10;
		while(true){
			float currentBank = rc.getTeamBullets();
			try{
				if(currentBank >= 10000)
					rc.donate(10000);

				//Gardener logic:
				//Try to create a gardener at the next opportunity if there aren't enough alive
				if(rc.isBuildReady()) {
					byte[] gardenerStateBytes = toBytes(rc.readBroadcast(CHANNEL_G));
					if(numGardenersCreated < minNumGardeners || 
							gardenerStateBytes[3] < minNumGardeners) {
						if(tryToBuild(RobotType.GARDENER)) {
							numGardenersCreated++;
							gardenerStateBytes[3] =  (byte) (gardenerStateBytes[3] + 1);
							rc.broadcast(CHANNEL_G, toInt(gardenerStateBytes));
						}
					}
				}
				//System.out.println("bytecode usage is "+Clock.getBytecodeNum());
				Clock.yield();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	public static void runGardener(){
		short amntToWaitFor = Short.MAX_VALUE; //unset
		boolean comm = isCommunicationGardener();

		if(comm) {
			while(true){
				//If about to die, broadcast that:
				float health = rc.getHealth();

				BulletInfo[] nearbyBullets = rc.senseNearbyBullets(3);
				float maxDamageTotal = 0;
				for(BulletInfo bi : nearbyBullets)
					maxDamageTotal += bi.damage;

				//uncomment the following to probably save on bytecode
				//if(health <= 0.10 * RobotType.GARDENER.maxHealth && nearbyBullets.length > 0) {
				if(health <= maxDamageTotal) {
					//about to die, reset the channel:
					try {
						rc.broadcast(CHANNEL_COMG, 0);
						comm = false;
					} catch (GameActionException e) {}
				}

				//If need more scouts and not busy, make a scout:
				if(amntToWaitFor == Short.MAX_VALUE) {
					int scoutChannelData;
					try {
						scoutChannelData = rc.readBroadcast(CHANNEL_SCOUT);
						if( (byte)scoutChannelData > (byte) 0) {
							amntToWaitFor = (short) RobotType.SCOUT.bulletCost;
							scoutChannelData--;
							rc.broadcast(CHANNEL_SCOUT, scoutChannelData);
						}
					} catch (GameActionException e) {}
				}
				else if(rc.getTeamBullets() >= amntToWaitFor) {
					if(tryToBuild(RobotType.SCOUT)) {
						amntToWaitFor = Short.MAX_VALUE;
					}
				}
				else {
					//TODO: Check if scouts have said where other gardeners are:
					try {
						tryToWater();

						//TODO: move closer to neighboring robots if possible
						/*
						if(rc.canMove(goingDir)){
							rc.move(goingDir);
						}
						else {
							goingDir = randomDir();
						}
						 */
					} catch (GameActionException e) {
						e.printStackTrace();
					}
				}
				//Done:
				Clock.yield(); 
			}
		}
		else {
			int numTreesPlanted = 0;
			RobotType typeToMake = null;
			while(true){
				if(amntToWaitFor == Short.MAX_VALUE) {
					amntToWaitFor = (short) RobotType.SOLDIER.bulletCost;
					double r = Math.random();
					if(r < .30){
						typeToMake = RobotType.SOLDIER;
					}
					else if (r>.3 && r<.5){
						typeToMake = RobotType.LUMBERJACK;
					}
				}
				try{
					if(rc.getTeamBullets() >= amntToWaitFor) {
						if(typeToMake == null){
							if(tryToPlant()) {
								amntToWaitFor = Short.MAX_VALUE;
								numTreesPlanted++;
							}
						}
						if(typeToMake == RobotType.SOLDIER){
							if(tryToBuild(typeToMake)) {
								amntToWaitFor = Short.MAX_VALUE;
							}
						}
						if(typeToMake == RobotType.LUMBERJACK){
							if(tryToBuild(typeToMake)){
								amntToWaitFor = Short.MAX_VALUE;
							}
						}
					}
					tryToWater();
					//move around
					if(rc.canMove(goingDir)){
						rc.move(goingDir);
					}
					else {
						goingDir = randomDir();
					}
				} catch(GameActionException e){
					e.printStackTrace();
				}
				finally { Clock.yield(); }
			}
		}
	}
	
	static void runSoldier() throws GameActionException {
        System.out.println("I'm an soldier!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                MapLocation myLocation = rc.getLocation();

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                // If there are some...
                if (robots.length > 0) {
                    // And we have enough bullets, and haven't attacked yet this turn...
                    if (rc.canFireSingleShot()) {
                        // ...Then fire a bullet in the direction of the enemy.
                        rc.fireSingleShot(rc.getLocation().directionTo(robots[0].location));
                    }
                    else if (Math.random() < 0.5) {
                    	MapLocation myLocation1 = rc.getLocation();
                        MapLocation enemyLocation = robots[0].getLocation();
                        Direction toEnemy = myLocation1.directionTo(enemyLocation);
                        tryMove(toEnemy);
                    }
                    else {
                    	tryMove(randomDirection());
                    }
                }
                else {tryMove(randomDirection());}

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if(robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // No close robots, so search for robots within sight radius
                    robots = rc.senseNearbyRobots(-1,enemy);

                    // If there is a robot, move towards it
                    if(robots.length > 0) {
                        MapLocation myLocation = rc.getLocation();
                        MapLocation enemyLocation = robots[0].getLocation();
                        Direction toEnemy = myLocation.directionTo(enemyLocation);

                        tryMove(toEnemy);
                    } else {
                        // Move Randomly
                        tryMove(randomDirection());
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns a random Direction
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float)Math.random() * 2 * (float)Math.PI);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir,20,3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir The intended direction of movement
     * @param degreeOffset Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while(currentCheck<=checksPerSide) {
            // Try the offset of the left side
            if(rc.canMove(dir.rotateLeftDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset*currentCheck));
                return true;
            }
            // Try the offset on the right side
            if(rc.canMove(dir.rotateRightDegrees(degreeOffset*currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset*currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

	/**
	 * Can be called to determine if a Gardener
	 * should be broadcasting or quiet.
	 * 
	 * This method may also make this robot a new communication gardener 
	 * and update the team array accordingly. 
	 * 
	 * Currently uses modulo of robot ID
	 * TODO: Read/Write to shared space 
	 * 
	 * @return true if this Gardener is a (newly-formed) Communication Gardener;
	 * i.e. a Communication Gardener will already know if it is one
	 */
	public static boolean isCommunicationGardener() {
		try {
			if(rc.readBroadcast(CHANNEL_COMG) == 0) {
				rc.broadcast(CHANNEL_COMG, rc.getID());
				return true;
			}
		} catch (GameActionException e) {}
		return false;
	}

	public static boolean tryToWater() throws GameActionException{
		if(rc.canWater()) {
			TreeInfo[] nearbyTrees = rc.senseNearbyTrees();
			for (int i = 0; i < nearbyTrees.length; i++)
				if(nearbyTrees[i].getHealth()<GameConstants.BULLET_TREE_MAX_HEALTH-GameConstants.WATER_HEALTH_REGEN_RATE) {
					if (rc.canWater(nearbyTrees[i].getID())) {
						rc.water(nearbyTrees[i].getID());
						return true;
					}
				}
		}
		return false;
	}

	/**
	 * Gardeners should call this method when trying to create robots.
	 * It ensures there are enough bullets, this robot is not cooling down, 
	 * and that the robot is being made in a clear direction.
	 * @param type Type of Robot to try to build.
	 * @return
	 * @throws GameActionException
	 */
	public static boolean tryToBuild(RobotType type) {
		//if(rc.getTeamBullets() > type.bulletCost &&
		//		rc.isBuildReady()) {//have enough bullets. assuming we haven't built already.
		for (int i = 0; i < 4; i++) {
			if(rc.canBuildRobot(type,dirList[i])) {
				try {
					rc.buildRobot(type,dirList[i]);
				} catch (GameActionException e) { }//will never trigger
				return true;
			}
		}
		//}
		return false;
	}

	public static boolean tryToPlant() throws GameActionException{
		//try to build gardeners
		//can you build a gardener?
		if(rc.hasTreeBuildRequirements()) {
			for (int i = 0; i < 4; i++) {
				//only plant trees on a sub-grid
				MapLocation p = rc.getLocation().add(dirList[i],GameConstants.GENERAL_SPAWN_OFFSET+GameConstants.BULLET_TREE_RADIUS+rc.getType().bodyRadius);
				if(modGood(p.x,6,0.2f)&&modGood(p.y,6,0.2f)) {
					if (rc.canPlantTree(dirList[i])) {
						rc.plantTree(dirList[i]);
						return true;
					}
				}
			}
		}
		return false;
	}
	public static boolean modGood(float number,float spacing, float fraction){
		return (number%spacing)<spacing*fraction;
	}

	public static byte[] toBytes(int i) {
		byte[] result = new byte[4];

		result[0] = (byte) (i >> 24);
		result[1] = (byte) (i >> 16);
		result[2] = (byte) (i >> 8);
		result[3] = (byte) (i /*>> 0*/);

		return result;
	}

	public static int toInt(byte[] b) {
		return b[0] << 24 | (b[1] & 0xFF) << 16 | (b[2] & 0xFF) << 8 | (b[3] & 0xFF);
	}

}