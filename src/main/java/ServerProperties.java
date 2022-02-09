import java.util.HashMap;

public class ServerProperties {
  static ServerProperties ServerPropertiesObject = null;

  static HashMap<Integer, Integer> processId = new HashMap<>();
  static Integer[] ports = { 27780, 27781, 27782 };
  static final int numberOfProcesses = ports.length;

  private ServerProperties() {
    int number = 0;
    for (Integer port : ports)
      processId.put(++number, port); // 1: 27780, 2: 27781, 3: 27782
  }

  static ServerProperties getServerPropertiesObject() {
    if (ServerPropertiesObject == null)
      new ServerProperties();
    return ServerPropertiesObject;
  }
}
