import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class RequestHandler implements Runnable {

  private DatagramPacket receivePacket;
  private DatagramSocket serverSocket;
  private InetAddress clientIPAddress;
  private int clientPort;

  private Timer timer;
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
    // removing the null bytes
    int nullIndex = filename.indexOf('\0');
    if (nullIndex >= 0) {
      filename = filename.substring(0, nullIndex);
    }
    File file = new File(Server.READDIR + filename);
    if (!file.exists()) {
      sendErrorPacket(packet, Server.ERR_FNF, "File not found");
      return;
    }
    DatagramSocket socket = new DatagramSocket();
    FileInputStream fileInputStream = new FileInputStream(file);
    byte[] buffer = new byte[516]; // 512 bytes for data + 4 bytes for header
    int blockNumber = 1;
    int bytesRead = 0;
    boolean timeOut = false;
    while (true) {
      bytesRead = fileInputStream.read(buffer, 4, 512); // read 512 bytes of data
      if (bytesRead == -1) {
        break;
      }
      buffer[0] = 0;
      buffer[1] = 3;
      buffer[2] = (byte) (blockNumber >> 8);
      buffer[3] = (byte) (blockNumber & 0xFF);
      DatagramPacket response = new DatagramPacket(buffer, bytesRead + 4, packet.getAddress(), packet.getPort());
      boolean ackReceived = false;
      int retryCount = 0;
      while (!ackReceived && retryCount < 3) {
        socket.send(response);
        try {
          // by lowering the timeout, retransmission can be triggered from the debugger
          socket.setSoTimeout(3);
          DatagramPacket ackPacket = new DatagramPacket(new byte[4], 4);
          socket.receive(ackPacket);
          if (ackPacket.getData()[0] == 0 && ackPacket.getData()[1] == 4 &&
              ackPacket.getData()[2] == buffer[2] && ackPacket.getData()[3] == buffer[3]) {
            ackReceived = true;
          }
        } catch (SocketTimeoutException e) {
          // timeout occurred, retransmit the packet
          retryCount++;
        }
      }
      if (!ackReceived) {
        // max retries reached, send error packet and return
        sendErrorPacket(packet, 3, "Timeout occurred during transfer");
        fileInputStream.close();
        socket.close();
        return;
      }
      blockNumber++;
    }
    fileInputStream.close();
    socket.close();
  }

  /*
  private void startTimer() {
    timer = new Timer();
    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        handleTimeout();
      }
    }, 1000);
  }

  private void stopTimer() {
    if (timer != null) {
      timer.cancel();
      timer = null;
    }
  }

   */




  private void sendErrorPacket(DatagramPacket packet, int errorCode, String errorMessage) throws IOException {
    DatagramSocket socket = new DatagramSocket();
    byte[] errorBuffer = new byte[512];
    errorBuffer[0] = 0;
    errorBuffer[1] = 5;
    errorBuffer[2] = 0;
    errorBuffer[3] = (byte) errorCode;
    byte[] errorMessageBytes = errorMessage.getBytes();
    System.arraycopy(errorMessageBytes, 0, errorBuffer, 4, errorMessageBytes.length);
    errorBuffer[4 + errorMessageBytes.length] = 0;
    DatagramPacket response = new DatagramPacket(errorBuffer, errorBuffer.length, packet.getAddress(), packet.getPort());
    socket.send(response);
  }


  @Override
  public void run() {
    try {
      byte[] buf = receivePacket.getData();
      ByteBuffer wrap= ByteBuffer.wrap(buf);
      short opcode = wrap.getShort();
      //byte[] buffer = new byte[516];
      //DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      if (opcode == 1) {
        handleReadRequest(receivePacket);
      } else if (opcode == 2) {
        //handleWriteRequest(packet);
      }
      //handleReadRequest(receivePacket);
      serverSocket.receive(receivePacket);
      System.out.println("Received packet from " + receivePacket.getAddress().getHostAddress() + ":" + receivePacket.getPort());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
