import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class RequestHandler implements Runnable {

  private DatagramPacket receivePacket;
  private DatagramSocket serverSocket;
  private InetAddress clientIPAddress;
  private int clientPort;
  private byte[] sendData;

  public RequestHandler(DatagramPacket receivePacket, byte[] sendData, DatagramSocket serverSocket, InetAddress clientIPAddress, int clientPort) {
    this.receivePacket = receivePacket;
    this.sendData = sendData;
    this.serverSocket = serverSocket;
    this.clientIPAddress = clientIPAddress;
    this.clientPort = clientPort;
  }

  private void handleReadRequest(DatagramPacket packet) throws IOException {
    String filename = new String(packet.getData(), 2, packet.getLength() - 2);
    String name = filename.replace("octet", "");
    File file = new File("read/demo.txt");
    FileInputStream fileInputStream = new FileInputStream(file);
    byte[] data = new byte[fileInputStream.available()];
    fileInputStream.read(data);
    fileInputStream.close();
    DatagramSocket socket = new DatagramSocket();
    int blockNumber = 1;
    int offset = 0;
    while (offset < data.length) {
      int length = Math.min(512, data.length - offset);
      byte[] buffer = new byte[length + 4];
      buffer[0] = 0;
      buffer[1] = 3;
      buffer[2] = (byte) (blockNumber >> 8);
      buffer[3] = (byte) (blockNumber & 0xFF);
      System.arraycopy(data, offset, buffer, 4, length);
      DatagramPacket response = new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort());
      socket.send(response);
      offset += length;
      blockNumber++;
    }
  }

  @Override
  public void run() {
    try {
      byte[] buffer = new byte[516];
      DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      if (packet.getData()[1] == 1) {
        handleReadRequest(packet);
      } else if (packet.getData()[1] == 2) {
        //handleWriteRequest(packet);
      }
      handleReadRequest(receivePacket);
      serverSocket.receive(packet);
      System.out.println("Received packet from " + receivePacket.getAddress().getHostAddress() + ":" + receivePacket.getPort());
      if (buffer[1] == 1) {
        System.out.println("Read request");
      } else if (buffer[1] == 2) {
        System.out.println("Write request");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
