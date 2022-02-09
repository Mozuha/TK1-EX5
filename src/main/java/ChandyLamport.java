import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.DefaultListModel;

public class ChandyLamport implements Runnable {
  Process process;
  private static ServerProperties serverProperties = null;
  private Scanner scan = new Scanner(System.in);
  private int MIN_AMOUNT = 1;
  private int MAX_AMOUNT = 5;
  private int id, port;
  private static String hostUrl = "127.0.0.1";
  private boolean snapshotStarted = false;
  public int numMarkerReceived = 0;
  private boolean isInitializer = false;
  public static List<Integer> processStates = new ArrayList<>(Collections.nCopies(3, 0));
  public static List<ConcurrentLinkedQueue<String>> fifoStates = new ArrayList<>(Collections.nCopies(6, new ConcurrentLinkedQueue<String>()));
  private String[] receiveChannels, sendChannels;
  DefaultListModel<String> historyListModel;

  public ChandyLamport(int id, Process process, boolean isInitializer, DefaultListModel<String> historyListModel) {
    serverProperties = ServerProperties.getServerPropertiesObject();
    this.id = id;
    this.process = process;
    this.port = ServerProperties.processId.get(id);
    this.isInitializer = isInitializer;
    this.historyListModel = historyListModel;
    this.receiveChannels = retrieveReceiveChannel(id).split(",");
    this.sendChannels = retrieveSendChannel(id).split(",");
  }

  /**
   * channel ids
   * P1 -> P2 = 0
   * P2 -> P1 = 1
   * P2 -> P3 = 2
   * P3 -> P2 = 3
   * P3 -> P1 = 4
   * P1 -> P3 = 5
   */
  private int retrieveChannel(int sourceProcess, int destProcess) {
    if (sourceProcess == 1)
      return destProcess == 2 ? 0 : 5;
    else if (sourceProcess == 2)
      return destProcess == 1 ? 1 : 2;
    else if (sourceProcess == 3)
      return destProcess == 2 ? 3 : 4;
    return -1;
  }

  private String retrieveReceiveChannel(int receiverProcess) {
    switch (receiverProcess) {
      case 1:
        return "1,4";
      case 2:
        return "0,3";
      case 3:
        return "2,5";
      default:
        return "Invalid.";
    }
  }

  private String retrieveSendChannel(int senderProcess) {
    switch (senderProcess) {
      case 1:
        return "0,5";
      case 2:
        return "1,2";
      case 3:
        return "3,4";
      default:
        return "Invalid.";
    }
  }

  private int retrieveSrcProcess(int channelId) {
    switch (channelId) {
      case 0:
      case 5:
        return 1;
      case 1:
      case 2:
        return 2;
      case 3:
      case 4:
        return 3;
      default:
        return -1;
    }
  }

  private int retrieveDestProcess(int channelId) {
    switch (channelId) {
      case 1:
      case 4:
        return 1;
      case 0:
      case 3:
        return 2;
      case 2:
      case 5:
        return 3;
      default:
        return -1;
    }
  }

  @Override
  public void run() {
    while (numMarkerReceived < 2) {
      String threadName = Thread.currentThread().getName();

      if (threadName.equals("local")) {
        if (isInitializer || process.isFirstMarkerReceived) {
          // record current process state
          process.processState = process.airplane;
          processStates.set(id - 1, process.airplane);
          historyListModel.addElement(process.airplane.toString());

          // record receive channel's FIFO as the empty FIFO
          fifoStates.set(Integer.parseInt(receiveChannels[0]), new ConcurrentLinkedQueue<String>());
          fifoStates.set(Integer.parseInt(receiveChannels[1]), new ConcurrentLinkedQueue<String>());
          process.receivedMsgs = new ConcurrentLinkedQueue<String>();

          process.isStateRecorded = true;

          // prepare marker to send
          String marker0 = String.format("Marker: H%d -> H%d", id,
              retrieveDestProcess(Integer.parseInt(sendChannels[0])));
          String marker1 = String.format("Marker: H%d -> H%d", id,
              retrieveDestProcess(Integer.parseInt(sendChannels[1])));
          ConcurrentLinkedQueue<String> fifo0 = fifoStates.get(Integer.parseInt(sendChannels[0]));
          ConcurrentLinkedQueue<String> fifo1 = fifoStates.get(Integer.parseInt(sendChannels[1]));
          fifo0.add(marker0);
          fifo1.add(marker1);
          fifoStates.set(Integer.parseInt(sendChannels[0]), fifo0);
          fifoStates.set(Integer.parseInt(sendChannels[1]), fifo1);
          historyListModel.addElement(marker0);
          historyListModel.addElement(marker1);

          isInitializer = false;
          process.isFirstMarkerReceived = false;
        }

        if (process.isStateRecorded) {
          // check received messages and apply changes
          if (!process.receivedMsgs.isEmpty()) {
            String msg;
            do {
              msg = process.receivedMsgs.poll();
            } while (msg != null && !msg.contains(String.format("H%d", id))); // is this msg for me?
            if (msg != null) {
              int transferredAirplane = msg.charAt(msg.indexOf("(") + 1) - 0;
              process.airplane += transferredAirplane;
            }
          }

          // generate message
          Random rand = new Random();
          int randomAmount = rand.nextInt(MAX_AMOUNT) + MIN_AMOUNT; // 1 ~ 5
          int currentAmount = process.airplane;

          int randomProcess;
          do {
            randomProcess = rand.nextInt(ServerProperties.numberOfProcesses) + 1; // 1 ~ 3
          } while (randomProcess == id);

          if ((currentAmount - randomAmount) >= 0) {
            process.airplane -= randomAmount;

            String msg = String.format("Transfer: H%d -> H%d (%d)", id, randomProcess, randomAmount);
            ConcurrentLinkedQueue<String> fifo0 = fifoStates.get(Integer.parseInt(sendChannels[0]));
            ConcurrentLinkedQueue<String> fifo1 = fifoStates.get(Integer.parseInt(sendChannels[1]));
            fifo0.add(msg);
            fifo1.add(msg);
            fifoStates.set(Integer.parseInt(sendChannels[0]), fifo0);
            fifoStates.set(Integer.parseInt(sendChannels[1]), fifo1);
            historyListModel.addElement(msg);
          }

          try {
            Thread.sleep((rand.nextInt(4) + 1) * 1000); // (1 ~ 4) * 1000
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }

      if (threadName.equals("receiveChannel")) {
        if (process.isStateRecorded) {
          try (DatagramSocket serverSocket = new DatagramSocket(port)) {
            byte[] data = new byte[1024];

            while (numMarkerReceived < 2) {
              DatagramPacket packet = new DatagramPacket(data, data.length);
              serverSocket.receive(packet);
              String msg = new String(Arrays.copyOf(packet.getData(), packet.getLength()), "UTF-8");
              if (msg.contains("Marker")) {
                numMarkerReceived++;
                process.isFirstMarkerReceived = true;
              } else {
                int sourceProcess = msg.charAt(msg.indexOf("-") - 2) - 0;
                ConcurrentLinkedQueue<String> receiveFifo = fifoStates.get(retrieveChannel(sourceProcess, id));
                receiveFifo.add(msg);
                fifoStates.set(retrieveChannel(sourceProcess, id), receiveFifo);
                process.receivedMsgs.add(msg);
              }
            }

            serverSocket.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }

      if (threadName.equals("sendChannel")) {
        if (process.isStateRecorded) {
          try (DatagramSocket clientSocket = new DatagramSocket()) {
            if (!fifoStates.get(Integer.parseInt(sendChannels[0])).isEmpty()) {
              int destProcess = retrieveDestProcess(Integer.parseInt(sendChannels[0]));
              ConcurrentLinkedQueue<String> sendFifo = fifoStates.get(Integer.parseInt(sendChannels[0]));
              String msg = sendFifo.poll();
              byte[] data = msg.getBytes("UTF-8");
              DatagramPacket packet = new DatagramPacket(data, data.length,
                  new InetSocketAddress("localhost", ServerProperties.processId.get(destProcess)));
              clientSocket.send(packet);
              historyListModel.addElement(msg);
            }

            if (!fifoStates.get(Integer.parseInt(sendChannels[1])).isEmpty()) {
              int destProcess = retrieveDestProcess(Integer.parseInt(sendChannels[1]));
              ConcurrentLinkedQueue<String> sendFifo = fifoStates.get(Integer.parseInt(sendChannels[1]));
              String msg = sendFifo.poll();
              byte[] data = msg.getBytes("UTF-8");
              DatagramPacket packet = new DatagramPacket(data, data.length,
                  new InetSocketAddress("localhost", ServerProperties.processId.get(destProcess)));
              clientSocket.send(packet);
              historyListModel.addElement(msg);
            }

            if (numMarkerReceived == 2)
              clientSocket.close();
          } catch (NumberFormatException | SocketException | UnsupportedEncodingException e) {
            e.printStackTrace();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }
  }

}
