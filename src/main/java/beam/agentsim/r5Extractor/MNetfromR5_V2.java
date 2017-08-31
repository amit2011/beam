package beam.agentsim.r5Extractor;

import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;
import com.conveyal.r5.streets.EdgeStore;
import com.conveyal.r5.transit.TransportNetwork;
import com.vividsolutions.jts.geom.Coordinate;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.transformations.GeotoolsTransformation;

import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 *
 *
 * Created by Andrew A. Campbell on 6/7/17.
 */
public class MNetfromR5_V2 {
	private static final Logger log = Logger.getLogger(MNetfromR5_V2.class);

	private TransportNetwork r5Network = null;  // R5 mNetowrk
	private Network mNetowrk = null;  // MATSim mNetowrk
	//TODO - the CRS should be settable not hard coded
	private String fromCRS = "EPSG:4326";  // WGS84
	private String toCRS = "EPSG:26910";  // UTM10N
	GeotoolsTransformation tranform = new GeotoolsTransformation(this.fromCRS, this.toCRS);
	private String osmFile;

	private HashMap<Coord, Id<Node>> nodeMap = new HashMap<>();  // Maps x,y Coord to node ID
	private int nodeId = 0;  // Current new MATSim network Node ids
	private EnumSet<EdgeStore.EdgeFlag> modeFlags = EnumSet.noneOf(EdgeStore.EdgeFlag.class); // modes to keep

	/**
	 *
	 * @param r5NetPath Path to R5 network.dat file.
	 * @param modeFlags EnumSet defining the modes to be included in the network. See
	 *                     com.conveyal.r5.streets.EdgeStore.EdgeFlag for EdgeFlag definitions.
	 */
	public MNetfromR5_V2(String r5NetPath, String osmPath, EnumSet<EdgeStore.EdgeFlag> modeFlags){
		this.osmFile = osmPath;
		File netFile = new File(r5NetPath);
		log.info("Found R5 Transport Network file, loading....");
		try {
			this.r5Network = TransportNetwork.read(netFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.modeFlags = modeFlags;
		this.mNetowrk = NetworkUtils.createNetwork();
		buildMNet();
	}

	/**
	 * Defaults to only using automobiles for the network.
	 * @param r5NetPath
	 */
	public MNetfromR5_V2(String r5NetPath, String osmPath) {
		this(r5NetPath, osmPath, EnumSet.of(EdgeStore.EdgeFlag.ALLOWS_CAR));
	}

	private void buildMNet() {
		// Load the OSM file for retrieving the number of lanes, which is not stored in the R5 network
		OSM osm = new OSM(this.osmFile);
		Map<Long, Way> ways = osm.ways;
		EdgeStore.Edge cursor = r5Network.streetLayer.edgeStore.getCursor();  // Iterator of edges in R5 network
		OsmToMATSim OTM = new OsmToMATSim(this.mNetowrk, this.tranform, true);
		while (cursor.advance()) {
			// Check if this edge permits any of the desired modes.
			EnumSet<EdgeStore.EdgeFlag> flags = cursor.getFlags();
			flags.retainAll(this.modeFlags);
			if (flags.isEmpty()) {
				log.info("EDGE SKIPPED - no allowable modes");
				continue;
			}
			// Convert flags to strings that the MATSim network will recognize.
			HashSet<String> flagStrings = new HashSet<>();
			for (EdgeStore.EdgeFlag eF : flags) {
				flagStrings.add(flagToString(eF));
			}
			////
			//Add the edge and its nodes
			////
			long osmID = cursor.getOSMID();  // id of edge in the OSM db
			Way way = ways.get(osmID);
//			int lanes = Integer.valueOf(way.getTag("lanes"));
//			Integer idx = cursor.getEdgeIndex();
			//TODO - length needs to be transformed to output CRS
			double length = cursor.getLengthM();
//			double speed = cursor.getSpeedMs();
			// Get start and end coordinates for the edge
			Coordinate tempFromCoord = cursor.getGeometry().getCoordinate();
			Coord fromCoord = transformCRS(new Coord(tempFromCoord.x, tempFromCoord.y));  // MATSim coord
			Coordinate[] tempCoords = cursor.getGeometry().getCoordinates();
			Coordinate tempToCoord = tempCoords[tempCoords.length - 1];
			Coord toCoord = transformCRS(new Coord(tempToCoord.x, tempToCoord.y));
			// Add R5 start and end nodes to the MATSim network
			// Grab existing nodes from mNetwork if they already exist, else make new ones and add to mNetwork
			Node fromNode = this.getOrMakeNode(fromCoord);
			Node toNode = this.getOrMakeNode(toCoord);
			// TODO - implications of having null way values still not clear to me. Can we really just skip them?
			// Make and add the link (only if way exists)

			if (way != null) {
				System.out.println(way.getTag("highway"));
				OTM.createLink(way, osmID, fromNode, toNode, length, flagStrings);
			}
		}
	}

	public void setTransformCRS(String to, String from){
		this.toCRS = to;
		this.fromCRS = from;
	}

	public void writeMNet(String mNetPath){
		NetworkWriter nw = new NetworkWriter(this.mNetowrk);
		nw.write(mNetPath);
	}


	/**
	 * Checks whether we already have a MATSim Node at the Coord. If so, returns that Node. If not, makes and adds
	 * a new Node to the network.
	 * @param coord
	 * @return
	 */
	private Node getOrMakeNode(Coord coord){
		Node dummyNode;
		Id<Node> id;
		if (this.nodeMap.containsKey(coord)){  // node already exists.
//			log.info("NODE ALREADY EXISTS");
			id = this.nodeMap.get(coord);
			dummyNode = this.mNetowrk.getNodes().get(id);
		} else { // need to make new fromID and node and increment the nodeId
			id = Id.createNodeId(this.nodeId);
			this.nodeId++;
			dummyNode = NetworkUtils.createAndAddNode(mNetowrk, id, coord);
			this.nodeMap.put(coord, id);
		}
		return dummyNode;
	}

	/*
	Tranforms from WGS84 to UTM 26910
	/TODO - this helper is not needed now that we have this.transform. But setTransform() needs to be updated
	 */
	private Coord transformCRS(Coord coord){
		GeotoolsTransformation tranform = new GeotoolsTransformation(this.fromCRS, this.toCRS);
		return tranform.transform(coord);
	}

	/**
	 * Returns the corresponding mode string for the EdgeFlag. See com.conveyal.r5.streets.EdgeStore.EdgeFlag and
	 * org.matsim.api.core.v01.TransportMode for definitions.
	 * @param flag EdgeFlag describing link travel modes.
	 * @return
	 */
	//TODO - we should probably make the cases settable for the case that we want to use custom modes in the MATSim net
	private String flagToString(EdgeStore.EdgeFlag flag){
		String out = null;
		switch (flag){
			case ALLOWS_PEDESTRIAN:
				out = "walk";
				break;
			case ALLOWS_BIKE:
				out = "bike";
				break;
			case ALLOWS_CAR:
				out = "car";
				break;
		}
		try {
			return out;
		}
		catch (NullPointerException exec) {
			log.error("No matching flag");
			exec.printStackTrace();
		}
		return out;
	}


	// TODO - delete this local class if we don't need to use link to/from nodes as keys in a Map
	/**
	 * Class for using tuples of MATSim Coords as keys for a Map
	 */
	public class CoordKey {

		private final Coord fromCoord;
		private final Coord toCoord;

		public CoordKey(Coord fromCoord, Coord toCoord) {
			this.fromCoord = fromCoord;
			this.toCoord = toCoord;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof CoordKey)) return false;
			CoordKey key = (CoordKey) o;
			return fromCoord == key.fromCoord && toCoord == key.toCoord;
		}

		@Override
		public int hashCode() {
			int result = Integer.valueOf(fromCoord.toString());
			result = 31 * result + Integer.valueOf(toCoord.toString());
			return result;
		}
	}

	/**
	 * This class is based off of MATSim's OsmNetworkReader. Particularly, it is used to generate all the link
	 * attributes in the MATSim network based on the OSM way's tags the same way OsmNetworkReader does.
	 */
	public class OsmWayToMATSim {

	}

	/**
	 * Input args:
	 * 0 - path to directory with data containers needed to build the TransportNetwork (osm, gtfs ...)
	 * 1 - path to OSM mapdb file
	 * 2 - output path for MATSim network
	 * 3 - [OPTIONAL] comma-separated list of EdgeFlag enum names. See com.conveyal.r5.streets.EdgeStore.EdgeFlag for
	 * EdgeFlag definitions.
	 * @param args
	 */
	public static void main(String[] args) {
		String inFolder = args[0];
		String osmPath = args[1];
		String mNetPath = args[2];
		// If mode flags passed, use the constructor with the modeFlags parameter
		MNetfromR5_V2 mn = null;
		if (args.length > 3){
			String[] flagStrings = args[3].trim().split(",");
			EnumSet<EdgeStore.EdgeFlag> modeFlags = EnumSet.noneOf(EdgeStore.EdgeFlag.class);
			for (String f: flagStrings){
				modeFlags.add(EdgeStore.EdgeFlag.valueOf(f));
			}
			System.out.println("USING MODE FLAGS HURRAY!!!!!!!!!");
			mn = new MNetfromR5_V2(inFolder, osmPath, modeFlags);
		}
		// otherwise use the default constructor
		else {
			mn = new MNetfromR5_V2(inFolder, osmPath);
		}

//		mn.buildMNet();
		log.info("Finished building network.");
//		NetworkCleaner nC = new NetworkCleaner();
//		log.info("Running NetowrkCleaner");
//		nC.run(mn.mNetowrk);
		log.info("Number of links:" + mn.mNetowrk.getLinks().size());
		log.info("Number of nodes: " + mn.mNetowrk.getNodes().size());
		mn.writeMNet(mNetPath);
	}

}

