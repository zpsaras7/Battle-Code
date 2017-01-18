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
	static Random rand;
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
		byte numGardenersCreated = 0;
		byte minNumGardeners = 5;
		byte timer = 0;
		while(true){
			float currentBank = rc.getTeamBullets();
			try{
				if(currentBank >= 10000)
					rc.donate(10000);

				//Broadcast Logic:
				//Broadcast only after generating X reads/writes's to the team array, 
				//so as to not disclose location every turn.
				//if(Clock.getBytecodeNum() 

				//Gardener logic:
				//Try to create a gardener at the next opportunity if there aren't enough alive
				if(rc.isBuildReady()) {
					byte[] gardenerStateBytes = toBytes(rc.readBroadcast(CHANNEL_G));
					if(numGardenersCreated < minNumGardeners || 
							gardenerStateBytes[3] < minNumGardeners || 
							(currentBank >= 300 && Math.random() < .15)) { //added additional slower creation of gardeners as game may progress
						if(tryToBuild(RobotType.GARDENER)) { //builds robot if it can
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
		int comm = getCommunicationGardener();
		int numGardeners = 1;

		int numTreesPlanted = 0;
		RobotType typeToMake = null;

		while(true) {
			try {
				if(rc.getID() == comm) {
					////////Monitor Health Lead Gardener\\\\\\\
					float health = rc.getHealth();

					if(health <= 0.10 * RobotType.GARDENER.maxHealth) {
						BulletInfo[] nearbyBullets = rc.senseNearbyBullets(3);
						float maxDamageTotal = 0;
						for(BulletInfo bi : nearbyBullets)
							maxDamageTotal += bi.damage;

						//uncomment the following to probably save on bytecode
						//if(health <= 0.10 * RobotType.GARDENER.maxHealth && nearbyBullets.length > 0) {
						if(health <= maxDamageTotal) { //about to die
							//first try to make a nearby gardener into the lead gardener:
							RobotInfo[] nearbyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
							for(RobotInfo ri : nearbyRobots) {
								if(ri.getType().equals(RobotType.GARDENER)) {
									try {
										rc.broadcast(CHANNEL_COMG, ri.getID());
										comm = 0;
										break;
									} catch (GameActionException e) {
										e.printStackTrace();
									}
								}
							}
							if(comm != 0) {
								//couldn't find a nearby gardener; next one that asks is lead
								try {
									rc.broadcast(CHANNEL_COMG, 0);
								} catch (GameActionException e) {
									e.printStackTrace();
								}
							}
						}
					}
					////////\\\\\\\

					////////Scout Production (Lead Gardener) \\\\\\\
					//If need more scouts and not busy, make a scout:
					if(amntToWaitFor == Short.MAX_VALUE) {
						byte[] scoutChannelData;
						try {
							scoutChannelData = toBytes(rc.readBroadcast(CHANNEL_SCOUT));
							if( scoutChannelData[3] > 0) {
								amntToWaitFor = (short) RobotType.SCOUT.bulletCost;
								scoutChannelData[3]--;
								rc.broadcast(CHANNEL_SCOUT, toInt(scoutChannelData));
							}
						} catch (GameActionException e) {
							e.printStackTrace();
						}
					}
					else if(rc.getTeamBullets() >= amntToWaitFor) {
						if(tryToBuild(RobotType.SCOUT)) {
							amntToWaitFor = Short.MAX_VALUE;
						}
					}
					////////\\\\\\\
					else {
						//TODO: Check if scouts have said where other gardeners are:
						try {
							TreeInfo[]nearbyTrees = rc.senseNearbyTrees();
							tryToWater(nearbyTrees);
	
							//move closer to neighboring robots if possible
							RobotInfo[] nearbyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
							if(nearbyRobots.length >= 2) { //more than archon 
								float avgX = nearbyRobots[0].getLocation().x;
								float avgY = nearbyRobots[0].getLocation().y;
								for(int i = 1; i < 10; i++)  {
									avgX+= nearbyRobots[i].getLocation().x;
									avgY+= nearbyRobots[i].getLocation().y;
								}
								avgX = avgX / nearbyRobots.length;
								avgY = avgY / nearbyRobots.length;
								tryMove(rc.getLocation().directionTo(new MapLocation(avgX, avgY)));
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					//Done:
					Clock.yield(); 
				}
				else { //regular gardener:
					try {
						numGardeners = rc.readBroadcast(CHANNEL_G);
					} catch (GameActionException e1) {System.out.println(e1.getMessage());}

					//If about to die, broadcast:
					try {
						float health = rc.getHealth();
						if(health <= 0.10 * rc.getType().maxHealth) {
							BulletInfo[] nearbyBullets = rc.senseNearbyBullets(3);
							float maxDamageTotal = 0;
							for(BulletInfo bi : nearbyBullets)
								maxDamageTotal += bi.damage;

							if(health <= maxDamageTotal) {
								sendDistress();
							}
						}
					} catch(Exception e) {
						System.out.println("monitoring exception " + e.getMessage());
					}

					try {
						//assign new goal if none:
						if(amntToWaitFor == Short.MAX_VALUE) {
							double r = Math.random();
							if(r < .2){
								typeToMake = RobotType.SOLDIER;
								amntToWaitFor = (short) RobotType.SOLDIER.bulletCost;
							}
							else if (r>=.2 && r<.4){
								typeToMake = RobotType.LUMBERJACK;
								amntToWaitFor = (short) RobotType.LUMBERJACK.bulletCost;
							}
							else {
								typeToMake = null;
								amntToWaitFor = (short) GameConstants.BULLET_TREE_COST;
							}
						}
					}catch(Exception e) {
						System.out.println("gaol assignment exception " + e.getMessage());
					}

					//check goal met:
					try{
						if(rc.getTeamBullets() >= amntToWaitFor) {
							if(typeToMake == null){
								if(tryToPlant()) {
									amntToWaitFor = Short.MAX_VALUE;
									numTreesPlanted++;
								}
							}
							else if(tryToBuild(typeToMake)) {
								amntToWaitFor = Short.MAX_VALUE;
								typeToMake = null;
							}

							//if(amntToWaitFor == Short.MAX_VALUE)
							//	typeToMake=null;
						}
					} catch(GameActionException e){
						e.printStackTrace();
					} catch(NullPointerException e) {
						//e.printStackTrace();
						System.out.println("goal met exception " + e.getMessage());
					}

					try {
						TreeInfo[]nearbyTrees = rc.senseNearbyTrees();
						tryToWater(nearbyTrees);
						
						// If any nearby trees have less than half health, don't move and keep watering
						for (TreeInfo t : nearbyTrees){
							if (t.health <= t.maxHealth/2){
								Clock.yield();
							}
						}
						
						Direction randDirection = getRandomDirTargeted((short) rc.getLocation().directionTo(
								rc.getInitialArchonLocations(rc.getTeam().opponent())[0]).getAngleDegrees(), (short) 55);
						if(rc.canMove(randDirection)){ //try to move away from archon 
							if(tryMove(randDirection))
								Clock.yield();
						} 
						else if(Math.random() < .75) { //try a different random 
							if(tryMove(randomDirection()))
								Clock.yield();
						}
					} catch(GameActionException e){
						e.printStackTrace();
					} catch(NullPointerException e) {
						//e.printStackTrace();
						System.out.println("move & water exception " + e.getMessage());
					}

					//Done:
					try {
						Clock.yield();
					} catch(NullPointerException e) {
						//e.printStackTrace();
						System.out.println("clock" + e.getMessage());
					}
				}
			}
			catch(NullPointerException e) {
				System.out.println(e.getMessage());
			}
		}
	}

	static void runScout() {
		TreeInfo currentTree = null;
		Direction lastMovedDirection = randomDirection();
		while(true) {
			MapLocation currentLoc = rc.getLocation();

			//Hide on large neutral trees if in sight
			TreeInfo[] nearbyTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
			TreeInfo bestTree = null;
			for(TreeInfo ti : nearbyTrees) { //get biggest tree
				if(ti.getRadius() > bestTree.getRadius())
					bestTree = ti;
			}
			if(bestTree.getRadius() > RobotType.SCOUT.bodyRadius+2) { //big tree found
				currentTree = bestTree;
			}

			try {
				//Move towards tree if need to:
				if(currentTree != null && !rc.isCircleOccupiedExceptByThisRobot(currentLoc, currentTree.getRadius()-0.1f)) {
					tryMove(currentLoc.directionTo(currentTree.getLocation()));
				}
				else if(currentTree == null) {
					tryMove(getRandomDirTargeted((short) lastMovedDirection.getAngleDegrees(), (short) 30));
				}
			} catch (GameActionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}


			//TODO: save Map locations seen
			Clock.yield();
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

			// Try/catch blocks stop unhandled exceptions, which cause your robot to explode
			try {

				// See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
				RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);
				ArrayList<RobotInfo> inStrikingRange = new ArrayList<RobotInfo>();
				float strikingRange = RobotType.LUMBERJACK.bodyRadius+GameConstants.LUMBERJACK_STRIKE_RADIUS;
				for(RobotInfo rob : robots){
					if(myLocation.isWithinDistance(rob.location, strikingRange)){
						inStrikingRange.add(rob);
					}
				}
				
				//look for nearby trees that are not ours to chop
				TreeInfo[] nearbyTrees = rc.senseNearbyTrees(strikingRange);
				for(TreeInfo tree : nearbyTrees){
					if(!tree.team.equals(myTeam)){
						if(rc.canChop(tree.ID))
							rc.chop(tree.ID);
							break;
					}
				}
				

				if(inStrikingRange.size() > 0 && !rc.hasAttacked()) {
					// Use strike() to hit all nearby robots and move towards one
					rc.strike();
					tryMove(myLocation.directionTo(inStrikingRange.get(0).getLocation()));
				} else {
					// If there is a robot in sight, move towards it
					if(robots.length > 0) {
						
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
					if(rc.canWater(ti.getID())) {
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
		//try to build gardeners
		//can you build a gardener?
		if(rc.hasTreeBuildRequirements()) {
			for (int i = 0; i < 4; i++) {
				//only plant trees on a sub-grid
				MapLocation p = rc.getLocation().add(gridDirectionList[i],GameConstants.GENERAL_SPAWN_OFFSET+GameConstants.BULLET_TREE_RADIUS+rc.getType().bodyRadius);
				if(modGood(p.x,6,0.2f)&&modGood(p.y,6,0.2f)) {
					if (rc.canPlantTree(gridDirectionList[i])) {
						rc.plantTree(gridDirectionList[i]);
						System.out.println("planted");
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
