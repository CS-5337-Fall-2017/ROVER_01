package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import MapSupport.Coord;
import MapSupport.MapTile;
import MapSupport.ScanMap;
import common.Rover;
import communicationInterface.Communication;
import communicationInterface.RoverDetail;
import communicationInterface.ScienceDetail;
import enums.RoverConfiguration;
import enums.RoverDriveType;
import enums.RoverMode;
import enums.RoverToolType;
import enums.Science;
import enums.Terrain;
import rover_logic.Astar;

/*
 * The seed that this program is built on is a chat program example found here:
 * http://cs.lmu.edu/~ray/notes/javanetexamples/ Many thanks to the authors for
 * publishing their code examples
 */

/**
 * 
 * @author rkjc
 * 
 *         ROVER_01 is intended to be a basic template to start building your
 *         rover on Start by refactoring the class name to match your rovers
 *         name. Then do a find and replace to change all the other instances of
 *         the name "ROVER_01" to match your rovers name.
 * 
 *         The behavior of this robot is a simple travel till it bumps into
 *         something, sidestep for a short distance, and reverse direction,
 *         repeat.
 * 
 *         This is a terrible behavior algorithm and should be immediately
 *         changed.
 *
 */

public class ROVER_01 extends Rover {

	/**
	 * Runs the client
	 */
	public static void main(String[] args) throws Exception {
		ROVER_01 client;
		// if a command line argument is present it is used
		// as the IP address for connection to RoverControlProcessor instead of
		// localhost

		if (!(args.length == 0)) {
			client = new ROVER_01(args[0]);
		} else {
			client = new ROVER_01();
		}

		client.run();
	}

	public ROVER_01() {
		// constructor
		System.out.println("ROVER_01 rover object constructed");
		rovername = "ROVER_01";
	}

	public ROVER_01(String serverAddress) {
		// constructor
		System.out.println("ROVER_01 rover object constructed");
		rovername = "ROVER_01";
		SERVER_ADDRESS = serverAddress;
	}

	/**
	 * 
	 * The Rover Main instantiates and runs the rover as a runnable thread
	 * 
	 */
	private void run() throws IOException, InterruptedException 
	{
		// Make a socket for connection to the RoverControlProcessor
		Socket socket = null;
		try
		{
			socket = new Socket(SERVER_ADDRESS, PORT_ADDRESS);

			// sets up the connections for sending and receiving text from the
			// RCP
			receiveFrom_RCP = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			sendTo_RCP = new PrintWriter(socket.getOutputStream(), true);

			// Need to allow time for the connection to the server to be
			// established
			
			sleepTime = 300;
			Communication communication = new Communication("http://localhost:2681/api", rovername, "open_secret");
			/*
			 * After the rover has requested a connection from the RCP this loop
			 * waits for a response. The first thing the RCP requests is the
			 * rover's name once that has been provided, the connection has been
			 * established and the program continues
			 */
			while (true)
			{
				String line = receiveFrom_RCP.readLine();
				if (line.startsWith("SUBMITNAME")) 
				{
					// This sets the name of this instance of a swarmBot for
					// identifying the thread to the server
					sendTo_RCP.println(rovername);
					break;
				}
			}

			/**
			 * ### Setting up variables to be used in the Rover control loop ###
			 * add more as needed
			 */
			int stepCount = 0;

			boolean northBlocked = false;
			boolean southBlocked = false;
			boolean goingEast = false;
			boolean switching = false;

			/*boolean elseblock = false;
			boolean stuck = false; */// just means it did not change locations
									// between requests,
									// could be velocity limit or obstruction
									// etc.
			boolean blocked = false;
			//boolean goingWest = false;
			// might or might not have a use for this
			String[] cardinals = new String[4];
			cardinals[0] = "N";
			cardinals[1] = "E";
			cardinals[2] = "S";
			cardinals[3] = "W";
			String currentDir = cardinals[0];
			char dirChar=' ';
			/**
			 * ### Retrieve static values from RoverControlProcessor (RCP) ###
			 * These are called from outside the main Rover Process Loop because
			 * they only need to be called once
			 */

			// **** get equipment listing ****
			equipment = getEquipment();
			System.out.println(rovername + " equipment list results " + equipment + "\n");

			// **** Request START_LOC Location from SwarmServer **** this might
			// be dropped as it should be (0, 0)
			/*startLocation = getStartLocation();
			System.out.println(rovername + " START_LOC " + startLocation);*/

			// **** Request TARGET_LOC Location from SwarmServer ****
			/*targetLocation = getTargetLocation();
			System.out.println(rovername + " TARGET_LOC " + targetLocation);
*/
			// **** Define the communication parameters and open a connection to
			// the
			// SwarmCommunicationServer restful service through the
			// Communication.java class interface
			/*
			 * String url = "http://localhost:2681/api"; //
			 * <---------------------- this will have to be changed if multiple
			 * servers are needed String corp_secret = "gz5YhL70a2"; // not
			 * currently used - for future implementation
			 * 
			 * Communication com = new Communication(url, rovername,
			 * corp_secret);
			 */
			Astar aStar = new Astar();
			int xsc=-1;
			int ysc=-1;
			RoverMode roverMode = RoverMode.EXPLORE;

			/**
			 * #### Rover controller process loop #### This is where all of the
			 * rover behavior code will go
			 * 
			 */
			while (true) { // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

				// **** Request Rover Location from RCP ****
				currentLoc = getCurrentLocation();
				System.out.println(rovername + " currentLoc at start: " + currentLoc);

				// after getting location set previous equal current to be able
				// to check for stuckness and blocked later
				//previousLoc = currentLoc;

				// ***** do a SCAN *****
				// gets the scanMap from the server based on the Rover current
				// location
				scanMap = doScan();

				// prints the scanMap to the Console output for debug purposes
				scanMap.debugPrintMap();

				// ***** after doing a SCAN post scan data to the communication
				// server ****
				// This sends map data to the Communications server which stores
				// it as a global map.
				// This allows other rover's to access a history of the terrain
				// this rover has moved over.

				 RoverDetail roverDetail = new RoverDetail();
				 
				 System.out.println("do com.postScanMapTiles(currentLoc, scanMapTiles)");
				 System.out.println("post message: " + communication.postScanMapTiles(currentLoc, scanMap.getScanMap()));
				 System.out.println("done com.postScanMapTiles(currentLoc, scanMapTiles)");
				 //communication.postScanMapTiles(currentLoc, scanMap.getScanMap());

				// RoverDetail roverDetail = new RoverDetail();
				// ***** get TIMER time remaining *****
				timeRemaining = getTimeRemaining();
				
				MapTile[][] scanMapTiles = scanMap.getScanMap();
				int centerIndex = (scanMap.getEdgeSize() - 1) / 2;
				
				if(scanMapTiles[centerIndex][centerIndex].getScience()==Science.ORGANIC)
				{
				gatherScience(getCurrentLocation());
				}
				
				for(int i=0;i<scanMapTiles.length;i++)
				{
					for (int j = 0; j < scanMapTiles.length; j++) 
					{
						if(scanMapTiles[i][j].getScience()==Science.ORGANIC	&&  scanMapTiles[i][j].getTerrain() != Terrain.ROCK
								&& scanMapTiles[i][j].getTerrain() != Terrain.SAND
								&& scanMapTiles[i][j].getTerrain() != Terrain.NONE)
						{
							xsc=i;
							ysc=j;
							break;
						}
					}
				}
				
				

				
				if(xsc!=-1)	
				{
						
						RoverConfiguration roverConfiguration = RoverConfiguration.valueOf(rovername);
						RoverDriveType driveType = RoverDriveType.valueOf(roverConfiguration.getMembers().get(0));
						RoverToolType tool1 = RoverToolType.getEnum(roverConfiguration.getMembers().get(1));
						RoverToolType tool2 = RoverToolType.getEnum(roverConfiguration.getMembers().get(2));
						
						aStar.addScanMap(doScan(), getCurrentLocation(), tool1, tool2);
						dirChar =aStar.findPath(getCurrentLocation(), new Coord(getCurrentLocation().xpos-3+xsc,getCurrentLocation().ypos-3 +ysc),
								driveType );

						
						
						System.out.println("from astar dirChar is: " + dirChar);
						// deciding direction based on the response from astar
						if (dirChar == 'S') {
							System.out.println("moving South, because I'm directed to go: " + dirChar);
							moveSouth();
						}
						if (dirChar == 'W') {
							System.out.println("moving West, because I'm directed to go: " + dirChar);
							moveWest();

						}
						if (dirChar == 'E') {
							System.out.println("moving East, because I'm directed to go: " + dirChar);
							moveEast();

						}
						if (dirChar == 'N') {
							System.out.println("moving North, because I'm directed to go: " + dirChar);
							moveNorth();

						}

						if (dirChar == 'U'  ) 
						{
							System.out.println("moving North, because I'm directed to go: " + dirChar);
							roverDetail.setRoverMode( RoverMode.EXPLORE );
							xsc=-1;
						
						}
					}
					
					else
					{
							
							if (southBlocked && northBlocked)
							{
								blocked = false;
								goingEast = !goingEast;
								switching = true;
							}

					if (blocked) {
 						if (stepCount > 0) {
							stepCount -= 1;

							if (switching) {
								if (!(scanMapTiles[centerIndex][centerIndex - 1].getHasRover()
										|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.ROCK
										|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.SAND
										|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.NONE)) {
									moveNorth();
								} else {

									switching = false;
								}
							}
							if (!(scanMapTiles[centerIndex][centerIndex + 1].getHasRover()
									|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.ROCK
									|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.SAND
									|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.NONE)) {
								moveSouth();
							} else {

								switching = true;
							}
						}

						else {
							blocked = false;
							// reverses direction after being blocked and side
							// stepping
							goingEast = !goingEast;
						}

					} else {
						
						if (goingEast) {
							// check scanMap to see if path is blocked to the
							// south
							// (scanMap may be old data by now)
							if (scanMapTiles[centerIndex - 1][centerIndex].getHasRover()
									|| scanMapTiles[centerIndex - 1][centerIndex].getTerrain() == Terrain.ROCK
									|| scanMapTiles[centerIndex - 1][centerIndex].getTerrain() == Terrain.SAND
									|| scanMapTiles[centerIndex - 1][centerIndex].getTerrain() == Terrain.NONE) {
								blocked = true;
								stepCount = 7;
								if (scanMapTiles[centerIndex][centerIndex + 1].getHasRover()
										|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.ROCK
										|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.SAND
										|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.NONE) {
									southBlocked = true;
								} else {
									southBlocked = false;
								}
								if (scanMapTiles[centerIndex][centerIndex - 1].getHasRover()
										|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.ROCK
										|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.SAND
										|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.NONE) {
									northBlocked = true;
								} else {
									northBlocked = false;
								}
							} else {
								moveWest();
							}

						} else {
							// check scanMap to see if path is blocked to the
							// north
							// (scanMap may be old data by now)
							if (scanMapTiles[centerIndex + 1][centerIndex].getHasRover()
									|| scanMapTiles[centerIndex + 1][centerIndex].getTerrain() == Terrain.ROCK
									|| scanMapTiles[centerIndex + 1][centerIndex].getTerrain() == Terrain.SAND
									|| scanMapTiles[centerIndex + 1][centerIndex].getTerrain() == Terrain.NONE) {
								blocked = true;
								stepCount = 7;

								if (scanMapTiles[centerIndex][centerIndex + 1].getHasRover()
										|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.ROCK
										|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.SAND
										|| scanMapTiles[centerIndex][centerIndex + 1].getTerrain() == Terrain.NONE) {
									southBlocked = true;
								} else {
									southBlocked = false;
								}
								if (scanMapTiles[centerIndex][centerIndex - 1].getHasRover()
										|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.ROCK
										|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.SAND
										|| scanMapTiles[centerIndex][centerIndex - 1].getTerrain() == Terrain.NONE) {
									northBlocked = true;
								} else {
									northBlocked = false;
								}
							} else {
								moveEast();
							}
						}
					}
				}
					
				// another call for current location

				// end bracket for
															// ELSE/DEFAULT
															// MOVEMENT

				// END following else portion is for when scienceDetail is not
				// found, this is our default movement

				// -----> posting details of ROVER-02 in communication server
				sendRoverDetail(roverDetail.getRoverMode());
				currentLoc = getCurrentLocation();
				//communication.postScanMapTiles(currentLoc, scanMap.getScanMap());
				communication.postScanMapTiles(currentLoc, scanMap.getScanMap());

				// test for stuckness
				//stuck = currentLoc.equals(previousLoc);

				// this is the Rovers HeartBeat, it regulates how fast the Rover
				// cycles through the control loop
				Thread.sleep(sleepTime);

				System.out.println("ROVER_01 ------------ end process control loop --------------");
			} // ***** END of Rover control While(true) loop *****

			// This catch block hopefully closes the open socket connection to
			// the server
		} 
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		finally
		{
			if (socket != null)
			{
				try 
				{
					socket.close();
				}
				catch (IOException e)
				{
					System.out.println("ROVER_01 problem closing socket");
				}
			}
		}

	}
} // END of Rover run thread

// ####################### Additional Support Methods
// #############################
