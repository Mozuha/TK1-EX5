import java.util.concurrent.ConcurrentLinkedQueue;

public class Process {
	int id;
	int port;
	Integer airplane = 10;
	static ServerProperties serverProperties;
	boolean isStateRecorded = false, isSendMarker = false, isFirstTime = false, isFirstMarkerReceived = false, isMarkerCompleted = true;
	boolean isSendingMarkers = false;
	int processState = 0;
	int channels[];
	boolean isChannelReserved[];
	long startTime = 0;
	ConcurrentLinkedQueue<String> receivedMsgs;

	Process(Integer id) {
		serverProperties = ServerProperties.getServerPropertiesObject();
		this.id = id;
		this.port = ServerProperties.processId.get(id);
		this.channels = new int[ServerProperties.numberOfProcesses * (ServerProperties.numberOfProcesses - 1)];
		this.isChannelReserved = new boolean[ServerProperties.numberOfProcesses * (ServerProperties.numberOfProcesses - 1)];
	}
}
