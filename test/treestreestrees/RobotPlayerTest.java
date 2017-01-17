package treestreestrees;

import static org.junit.Assert.*;
import org.junit.Test;

public class RobotPlayerTest {

	byte[][] byteTests = {{0,0,0,0},
			{0,0,0,15},
			{0,22,0,0},
			{1, 2, 3, 4}, 
			{126, -92, 57, 40}, 
			{-3, -54, -10, -5}};
	
	@Test
	public void testByteConversion() {
		for(byte[] expected : byteTests) {
			byte[] got = RobotPlayer.toBytes(RobotPlayer.toInt(expected));
			for(int i = 0; i < expected.length; i++)
				assertEquals(expected[i], got[i]);
		}
	}
	
	@Test
	public void testLSB() { //make sure that (byte) i corresponds to LSB
		for(byte[] test : byteTests) {
			int got = RobotPlayer.toInt(test);
			assertEquals(test[3], (byte) got); 
		}
	}
	
}
