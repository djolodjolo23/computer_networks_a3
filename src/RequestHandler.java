
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;


public class RequestHandler implements Runnable {

  private DatagramPacket receivePacket;

  private InetSocketAddress inetSocketAddress;

  private final Helper helper;

  private final ErrorHandler errorHandler;


  /**
   * Constructor
   *
   * @param receivePacket packet received from client
   * @param inetSocketAddress socket address of client
   * @param errorHandler error handler
   * @param helper helper class
   */
  public RequestHandler(DatagramPacket receivePacket, InetSocketAddress inetSocketAddress,
      ErrorHandler errorHandler, Helper helper) {
    this.errorHandler = errorHandler;
    this.helper = helper;
    this.receivePacket = receivePacket;
    this.inetSocketAddress = inetSocketAddress;
  }

  /**
   * Handles the read request
   *
   * @param socket socket to send and receive packets
   * @throws IOException if an I/O error occurs
   */
  private void handleReadRequest(DatagramSocket socket) throws IOException {
    String filename = helper.extractFilename(receivePacket);
    File file = new File(Server.READDIR + filename);
    if (!file.exists()) {
      errorHandler.handle(socket, receivePacket, 1);
      return;
    }
    FileInputStream fileInputStream = new FileInputStream(file);
    byte[] buffer = new byte[516]; // 512 bytes for data + 4 bytes for header
    int blockNumber = 1;
    int bytesRead;
    while (true) {
      bytesRead = fileInputStream.read(buffer, 4, 512); // read 512 bytes of data
      if (bytesRead == -1) {
        break;
      }
      buffer[0] = 0; // data packet
      buffer[1] = 3; // data packet
      buffer[2] = (byte) (blockNumber >> 8); // block number
      buffer[3] = (byte) (blockNumber & 0xFF); // block number
      DatagramPacket response = new DatagramPacket(buffer, bytesRead + 4, receivePacket.getAddress(), receivePacket.getPort()); // create packet
      boolean ackReceived = false; // flag to check if ack is received
      int retryCount = 0; //
      int retransmit = 0;
      while (!ackReceived && retryCount < 6) {
        socket.send(response);
        System.out.println("Sending block: " + blockNumber);
        try {
          socket.setSoTimeout(5000);
          DatagramPacket ackPacket = new DatagramPacket(new byte[4], 4); // create ack packet
          socket.receive(ackPacket); // receive ack packet
          if (ackPacket.getData()[0] == 0 && ackPacket.getData()[1] == 4 &&
              ackPacket.getData()[2] == buffer[2] && ackPacket.getData()[3] == buffer[3]) {
            ackReceived = true; // ack received
            System.out.println("Received ack for block " + blockNumber);
          } else {
            System.out.println("ACK not received for block " + blockNumber + " ,retransmitting block...");
            retransmit++;
            if (retransmit == 6) {
              errorHandler.handle(socket, receivePacket, 0);
              fileInputStream.close();
              socket.close();
              return;
            }
          }
        } catch (SocketTimeoutException e) {
          System.out.println("Timeout occurred, retransmitting packet...");
          retryCount++;
        }
      }
      if (!ackReceived) {
        errorHandler.handle(socket, receivePacket, 0);
        fileInputStream.close();
        socket.close();
        return;
      }
      blockNumber++;
    }
    System.out.println("Read request completed.\n");
    fileInputStream.close();
    socket.close();
  }

  /**
   * Handles the write request
   *
   * @param socket socket to send and receive packets
   * @param clientAddress client address
   * @param clientPort client port
   */
  private void handleWriteRequest(DatagramSocket socket, InetAddress clientAddress, int clientPort) throws IOException {
    String fileName = helper.extractFilename(receivePacket);
    String folderName = Server.WRITEDIR;
    File folder = new File(folderName);
    if (!folder.exists()) {
      folder.mkdir();
    }
    File file = new File(folder, fileName);
    if (file.exists()) {
      errorHandler.handle(socket, receivePacket, 6);
      return;
    }
    int blockNumber = 1;
    boolean lastBlock = false;
    try (FileOutputStream stream = new FileOutputStream(file)) {
      byte[] initialAckData = new byte[4];
      initialAckData[0] = 0;
      initialAckData[1] = 4;
      initialAckData[2] = 0;
      initialAckData[3] = 0;
      DatagramPacket initialAckPacket = new DatagramPacket(initialAckData, initialAckData.length, clientAddress, clientPort);
      socket.send(initialAckPacket);
      System.out.println("Sent initial ack!");
      while (!lastBlock) {
        byte[] blockData = new byte[516];
        DatagramPacket packet = new DatagramPacket(blockData, blockData.length);
        socket.receive(packet);
        InetAddress senderAddress = packet.getAddress();
        int senderPort = packet.getPort();
        short fetchedBlockNumber = helper.getData(packet);
        System.out.println("Received data packet with block number: " + fetchedBlockNumber);
        if (senderAddress.equals(clientAddress) && senderPort == clientPort && fetchedBlockNumber == blockNumber) {
          int dataLength = packet.getLength() - 4;
          try {
            stream.write(blockData, 4, dataLength); // TODO: add + 1 to dataLength for exception handling
          } catch (IndexOutOfBoundsException e) {
            errorHandler.handle(socket, receivePacket, 2);
            return;
          }
          byte[] ackData = new byte[4];
          ackData[0] = 0;
          ackData[1] = 4;
          ackData[2] = (byte) ((blockNumber >> 8) & 0xFF);
          ackData[3] = (byte) (blockNumber & 0xFF);
          DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
          socket.send(ackPacket);
          System.out.println("Sent ack for block " + blockNumber);
          blockNumber++;
          if (dataLength < 512) {
            lastBlock = true;
            System.out.println("Write request completed.\n");
          }
        } else if (fetchedBlockNumber < blockNumber) {
          System.out.println("Received duplicate packet with block number " + fetchedBlockNumber);
        } else {
          System.out.println("Received out of order packet with block number " + fetchedBlockNumber);
          errorHandler.handle(socket, packet, 4);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }




  @Override
  public void run() {
    try {
      DatagramSocket socket = new DatagramSocket();
      socket.connect(inetSocketAddress);
      byte[] buf = receivePacket.getData();
      if (receivePacket.getLength() < 2) {
        errorHandler.handle(socket, receivePacket, 4);
      }
      ByteBuffer wrap= ByteBuffer.wrap(buf);
      short opcode = wrap.getShort();
      if (opcode == 1) {
        handleReadRequest(socket);
      }
      if (opcode == 2) {
        handleWriteRequest(socket, inetSocketAddress.getAddress(), receivePacket.getPort());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
