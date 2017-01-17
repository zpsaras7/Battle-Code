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
		byte numGardenersCreated = 0;
		byte minNumGardeners = 10;
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
							gardenerStateBytes[3] < minNumGardeners || 
							Math.random() < .15) { //added additional slower creation of gardeners as game may progress
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
			while(true){
				if(amntToWaitFor == Short.MAX_VALUE) {
					amntToWaitFor = (short) GameConstants.BULLET_TREE_COST;
					double r = Math.random();
					if(r < .33)
						amntToWaitFor = (short) RobotType.SOLDIER.bulletCost;
				}
				try{
					if(rc.getTeamBullets() >= amntToWaitFor) {
						if(tryToPlant()) {
							amntToWaitFor = Short.MAX_VALUE;
							numTreesPlanted++;
						}
						if(tryToBuild(RobotType.SOLDIER)) {
							amntToWaitFor = Short.MAX_VALUE;

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
	public static void runSoldier(){

	}
	public static void runLumberjack(){

	}
	
	public static void runScout() {
		while(true) {
			int 
			
			Clock.yield();
		}
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