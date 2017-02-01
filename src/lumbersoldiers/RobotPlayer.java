package lumbersoldiers;
import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

public strictfp class RobotPlayer {
	private static final byte CHANNEL_SOLDIER = 4;
	private static final byte CHANNEL_SCOUT = 3; //bits: NA NA #alive #tomake
	private static final byte CHANNEL_COMG = 2; //ID of com gardener
	private static final byte CHANNEL_G = 1; //bits (MSB->LSB): NA, NA, NA, #alive

	static final float TWO_PI = (float) (Math.PI * 2.);
	static RobotController rc;
	static Direction[] gridDirectionList = new Direction[4];
	static Direction goingDir;
	static Random rand = new Random();
	
	static final int MAX_GARDENERS = 4;
	static final int MAX_LJ = 8;
	@SuppressWarnings("unused")
	public static void run(RobotController rc) throws GameActionException {
		RobotPlayer.rc = rc;
		initDirList();
		rand = new Random(rc.getID());
		goingDir = randomDirection();
		switch (rc.getType()) {
		case ARCHON:
			runArchon();
			break;
		case GARDENER:
			runGardener();
			break;
		case SCOUT:
			runScout();
		case SOLDIER:
			runSoldier();
			break;
		case LUMBERJACK:
			runLumberjack();
			break;
		}
	}

	public static Direction randomGridDirection(){
		return gridDirectionList[rand.nextInt(4)];
	}
	public static void initDirList(){
		for(int i=0;i<4;i++){
			float radians = (float)(-Math.PI + 2*Math.PI*((float)i)/4);
			gridDirectionList[i]=new Direction(radians);
			//System.out.println("made new direction "+gridDirectionList[i]);
		}
	}
	public static void runArchon() {
		int spawnedGardeners = 0;
		int round = rc.getRoundNum();
		while(true){
			float currentBank = rc.getTeamBullets();
			try{
				Direction dir = randomDirection();
				if(currentBank >= (7500 +(round*(12.5/3))))
					rc.donate(currentBank);

				//Broadcast Logic:
				//Broadcast only after generating X reads/writes's to the team array, 
				//so as to not disclose location every turn.
				//if(Clock.getBytecodeNum() 

				//Gardener logic:
				//Try to create a gardener at the next opportunity if there aren't enough alive
				if(rc.isBuildReady() && spawnedGardeners < MAX_GARDENERS) {
					rc.hireGardener(dir);
					spawnedGardeners++;
				}
				goingDir = randomDirection();
            	while(!rc.canMove(goingDir)){
            		goingDir = goingDir.rotateRightRads(TWO_PI/6);
            	}
            	rc.move(goingDir);
				//System.out.println("bytecode usage is "+Clock.getBytecodeNum());
				Clock.yield();
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	public static void runGardener(){
		int maxSoldiers = 4;
		int maxLumbers = 2;
		int maxScouts = 2;
		int soldiers = 0, lumbers = 0, scouts = 0;
		
		int spawnRound = rc.getRoundNum();
        while(true){
        	int round = rc.getRoundNum();
        	int n = rand.nextInt(50);
        	Direction east = Direction.getEast();
        	boolean startedCultivating = false;
        	float currentBank = rc.getTeamBullets();
            try{
            	if(currentBank >= (7500 +(round*(12.5/3))))
					rc.donate(currentBank);
            	RobotInfo[] teammatesNear = rc.senseNearbyRobots((float) 4.0, rc.getTeam());
                if (((round-spawnRound) > 50 && teammatesNear.length < 2) || startedCultivating){
                	tryToWater();
                	tryToPlant();
                	startedCultivating = true;
                	//make scouts first
                	/*if (rc.canBuildRobot(RobotType.SCOUT, east)  && scouts < maxScouts){
                		rc.buildRobot(RobotType.SCOUT, east);
                		scouts ++;
                	}
                	//randomly make soldier or lumberjack
                	else if (rc.canBuildRobot(RobotType.SOLDIER, east) && soldiers < maxSoldiers && n > 20){
                		rc.buildRobot(RobotType.SOLDIER, east);
                		soldiers ++;
                	}
                	else if (rc.canBuildRobot(RobotType.LUMBERJACK, east) && lumbers < maxLumbers){
                		rc.buildRobot(RobotType.LUMBERJACK, east);
                		lumbers ++;
                	}*/
                	// If cant make any other robots, just plant a tree east and keep farming
                	if (rc.canPlantTree(east)){
                		rc.plantTree(east);
                	}
                }
                else if (!startedCultivating) {
                	goingDir = randomDirection();
                	while(!rc.canMove(goingDir)){
                		goingDir = goingDir.rotateRightRads(TWO_PI/6);
                	}
                	rc.move(goingDir);
                }
                Clock.yield();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }

	static void runScout() {
		TreeInfo currentTree = null;
		Direction lastMovedDirection = randomDirection();
		while(true) {
			try {
				MapLocation currentLoc = rc.getLocation();

				//Hide on large neutral trees if in sight
				TreeInfo[] nearbyTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
				TreeInfo bestTree = null;
				for(TreeInfo ti : nearbyTrees) { //get biggest tree
					/*if(rc.canShake(ti.ID)){
						try {
							rc.shake(ti.ID);
						} catch (GameActionException e) {
							e.printStackTrace();
						}
					}*/
					if(ti.getRadius() > bestTree.getRadius())
						bestTree = ti;
				}
				if(bestTree.getRadius() > RobotType.SCOUT.bodyRadius+2) { //big tree found
					currentTree = bestTree;
				}
				//Move towards tree if need to:
				if(currentTree != null && !rc.isCircleOccupiedExceptByThisRobot(currentLoc, currentTree.getRadius()-0.1f)) {
					tryMove(currentLoc.directionTo(currentTree.getLocation()));
				}
				else if(currentTree == null) {
					tryMove(getRandomDirTargeted((short) lastMovedDirection.getAngleDegrees(), (short) 30));
				}
				//TODO: save Map locations seen
				Clock.yield();
			} catch (GameActionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	static void runSoldier() throws GameActionException {
		System.out.println("I'm an soldier!");
		Team enemy = rc.getTeam().opponent();
		Direction lastMovedDirection = randomDirection(); //change to set initial direction

		while (true) {

			////////Monitor Low Health Soldier\\\\\\\
			float health = rc.getHealth();
			if(health <= 0.10 * rc.getType().maxHealth) {
				BulletInfo[] nearbyBullets = rc.senseNearbyBullets(3);
				float maxDamageTotal = 0;
				for(BulletInfo bi : nearbyBullets)
					maxDamageTotal += bi.damage;

				if(health <= maxDamageTotal) { //about to die
					sendDistress();
				}
			}
			////////\\\\\\\

			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {
				MapLocation myLocation = rc.getLocation();

				// See if there are any nearby enemy robots
				RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

				// If there are some...
				if (robots.length > 0) {
					MapLocation enemyLocation = robots[0].getLocation();
					Direction toEnemy = myLocation.directionTo(enemyLocation);
					
					// And we have enough bullets, and haven't attacked yet this turn...
					if (rc.canFireSingleShot()) {
						// ...Then fire a bullet in the direction of the enemy.
						rc.fireSingleShot(rc.getLocation().directionTo(robots[0].location));
					}
					
					// Movements
					if (Math.random() < 0.5) {
						if(tryMove(toEnemy))
							lastMovedDirection = toEnemy;
					}
					else { 
						//try to move in enemy direction with 30 degree possible spread on either side
						tryMove(getRandomDirTargeted((short) toEnemy.getAngleDegrees(), (short) 15) );
					}
					
					//check if we saw an archon
					for(RobotInfo rob : robots){
						if(rob.type.equals(RobotType.ARCHON)){
							MapLocation archonLocation = rob.getLocation();
							//TODO: Broadcast archon location
						}
					}
				}
				else {
					//no enemies bigger spread
					tryMove(getRandomDirTargeted((short) lastMovedDirection.getAngleDegrees(), (short) 30) );
				}


			} catch (Exception e) {
				System.out.println("Soldier Exception");
				e.printStackTrace();
			}

			Clock.yield();
		}
	}

	static void runLumberjack() throws GameActionException {
		System.out.println("I'm a lumberjack!");
		Team myTeam = rc.getTeam();
		Team enemy = myTeam.opponent();
		MapLocation myLocation = rc.getLocation();
		// The code you want your robot to perform every round should be in this loop
		while (true) {
            try {
                RobotInfo[] bots = rc.senseNearbyRobots();
                for (RobotInfo b : bots) {
                    if (b.getTeam() == enemy && rc.canStrike()) {
                        rc.strike();
                        Direction chase = rc.getLocation().directionTo(b.getLocation());
                        if (rc.canMove(chase)) {
                            rc.move(chase);
                        }
                        break;
                    }
                }
                TreeInfo[] trees = rc.senseNearbyTrees();
                for (TreeInfo t : trees) {
                    if (t.team != myTeam && rc.canChop(t.getLocation())) {
                        rc.chop(t.getLocation());
                        break;
                    }
                    if (! rc.hasMoved()){
                    	rc.move(t.getLocation());
                    }
                }
                if (! rc.hasAttacked() && ! rc.hasMoved()) {
                    wander();
                }
                Clock.yield();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	}
	
	public static void wander() throws GameActionException {
        try {
            Direction dir = randomDirection();
            if (rc.canMove(dir)) {
                rc.move(dir);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	static boolean sendDistress() {
		System.out.println("distress");
		try {
			if(rc.getType().equals(RobotType.GARDENER)) {
				byte[] data = toBytes(rc.readBroadcast(CHANNEL_G));
				System.out.println("Gardener data:" + Arrays.toString(data));
				data[3]--;
				System.out.println("Gardener data:" + Arrays.toString(data));
				rc.broadcast(CHANNEL_G, toInt(data));
			}
			else if(rc.getType().equals(RobotType.SCOUT)) {
				byte[] data = toBytes(rc.readBroadcast(CHANNEL_SCOUT));
				data[2]--;
				rc.broadcast(CHANNEL_SCOUT, toInt(data));
			}
			else if(rc.getType().equals(RobotType.SOLDIER)) {
				int numSold = rc.readBroadcast(CHANNEL_SOLDIER);
				rc.broadcast(CHANNEL_SOLDIER, numSold-1);
			}
			return true;
		} catch (GameActionException e) {
			e.printStackTrace();
		}
		return false;
	}


	/**
	 * Returns a random Direction.
	 * @return a random Direction
	 */
	static Direction randomDirection() {
		return new Direction((float)Math.random() * TWO_PI);
	}

	static Direction getRandomDirTargeted(short angleDeg, short spreadDeg) {
		float r = (float) Math.random();
		if(r > .5)
			r*= -1;

		return new Direction( (angleDeg + r * spreadDeg) * TWO_PI);
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
	 * @return the ID of the current lead gardener, or 0 if something bad happened
	 */
	public static int getCommunicationGardener() {
		try {
			int channelData = rc.readBroadcast(CHANNEL_COMG);
			if(channelData == 0) {
				rc.broadcast(CHANNEL_COMG, rc.getID());
				return rc.getID();
			}
			else return channelData;
		} catch (GameActionException e) {
			System.out.println(e.getMessage());
		}
		return 0;
	}


	/**
	 * Waters the weakest tree in sight if it can
	 * @return the number of trees nearby that can be watered
	 * @throws GameActionException if the robot
	 */
	public static short tryToWater(TreeInfo[] nearbyTrees) throws GameActionException{
		//TreeInfo[] waterableNearbyTrees = new TreeInfo[0];
		short ans = 0;
		if(rc.canWater()) { //5
			int weakestTreeID = 0;
			float weakestTreeHealth = GameConstants.BULLET_TREE_MAX_HEALTH;
			if(nearbyTrees.length > 0)  {
				for(TreeInfo ti : nearbyTrees) {
					if(rc.canWater(ti.getID())&&ti.health < (GameConstants.BULLET_TREE_MAX_HEALTH - GameConstants.WATER_HEALTH_REGEN_RATE)) {
						ans++;
						if(ti.getHealth() < weakestTreeHealth) {
							weakestTreeID = ti.getID();
							weakestTreeHealth = ti.getHealth();
						}
					}
				}
				if(weakestTreeHealth<GameConstants.BULLET_TREE_MAX_HEALTH-GameConstants.WATER_HEALTH_REGEN_RATE) {
					rc.water(weakestTreeID);
				}
			}
		}
		return ans;
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
			Direction d = randomDirection();
			if(rc.canBuildRobot(type,d)) {
				try {
					rc.buildRobot(type,d);
				} catch (GameActionException e) { 
					e.printStackTrace();
				}//will never trigger
				return true;
			}
		}
		//}
		return false;
	}

	public static boolean tryToPlant() throws GameActionException{
		boolean planted = false;
		//plant 5 trees and leave a hole open facing east to spawn soldiers and lumberjacks and scouts
		if(rc.hasTreeBuildRequirements()) {
			for (int i = 1; i < 6; i++) {
				float mod = 60*i;
				//only plant trees in a circle around location
				Direction dir = Direction.getEast().rotateRightDegrees(mod);
				if (rc.canPlantTree(dir)){
					rc.plantTree(dir);
					planted = true;
				}
			}
		}
		return false;
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
