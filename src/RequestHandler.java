import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;


public class RequestHandler implements Runnable {

  private DatagramPacket receivePacket;


  public RequestHandler(DatagramPacket receivePacket) {
    this.receivePacket = receivePacket;
  }

  private void handleReadRequest(DatagramPacket packet) throws IOException {
    DatagramSocket socket = new DatagramSocket();
    String filename = new String(packet.getData(), 2, packet.getLength() - 2);
    // removing the null bytes
    int nullIndex = filename.indexOf('\0');
    if (nullIndex >= 0) {
      filename = filename.substring(0, nullIndex);
    }
    File file = new File(Server.READDIR + filename);
    if (!file.exists()) {
      sendErrorPacket(socket, packet, Server.ERR_FNF, "File not found");
      return;
    }
    FileInputStream fileInputStream = new FileInputStream(file);
    //fileInputStream.read();
    //fileInputStream.close();
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
      DatagramPacket response = new DatagramPacket(buffer, bytesRead + 4, packet.getAddress(), packet.getPort()); // create packet
      boolean ackReceived = false; // flag to check if ack is received
      int retryCount = 0; //
      while (!ackReceived && retryCount < 6) {
        socket.send(response);
        try {
          socket.setSoTimeout(1); // set timeout to 50ms
          DatagramPacket ackPacket = new DatagramPacket(new byte[4], 4); // create ack packet
          socket.receive(ackPacket); // receive ack packet
          if (ackPacket.getData()[0] == 0 && ackPacket.getData()[1] == 4 &&
              ackPacket.getData()[2] == buffer[2] && ackPacket.getData()[3] == buffer[3]) {
            ackReceived = true; // ack received
          }
        } catch (SocketTimeoutException e) {
          // timeout occurred, retransmit the packet
          System.out.println("Timeout occurred, retransmitting packet...");
          retryCount++; // increment retry count
        }
      }
      if (!ackReceived) {
        // max retries reached, send error packet and return
        sendErrorPacket(socket, packet, 3, "Timeout occurred during transfer.");
        fileInputStream.close();
        socket.close();
        return;
      }
      System.out.println("Sending...");
      System.out.println("Sent block " + blockNumber);
      blockNumber++;
    }
    System.out.println("File transfer complete.\n");
    fileInputStream.close();
    socket.close();
  }

  private void handleWriteRequest(DatagramPacket packet) throws IOException {
    DatagramSocket socket = new DatagramSocket();
    String filename = new String(packet.getData(), 2, packet.getLength() - 2);
    // removing the null bytes
    int nullIndex = filename.indexOf('\0');
    if (nullIndex >= 0) {
      filename = filename.substring(0, nullIndex);
    }
    File file = new File(Server.READDIR + filename);
    if (!file.exists()) {
      sendErrorPacket(socket, packet, Server.ERR_FNF, "File not found");
      return;
    }
    FileOutputStream fileOutputStream = new FileOutputStream(file);
    byte[] buffer = new byte[516];// 512 bytes for data + 4 bytes for header
    buffer[0] = 0; // data packet
    buffer[1] = 2; // write request
    buffer[2] = 0; // block number
    buffer[3] = 0; // block number
    DatagramPacket response = new DatagramPacket(buffer, 4, packet.getAddress(), packet.getPort()); // create packet
    //socket.send(response);
    int blockNumber = 1;
    boolean lastPacketReceived = false;
    while (!lastPacketReceived) {
      socket.send(response);
      socket.setSoTimeout(1); // set timeout to 50ms
      DatagramPacket dataPacket = new DatagramPacket(new byte[516], 516);
      socket.receive(dataPacket);
      byte[] data = dataPacket.getData();
      if (data[1] != 3) {
        sendErrorPacket(socket, packet, 4, "Illegal TFTP operation.");
        fileOutputStream.close();
        socket.close();
        return;
      }
      int bytesRead = dataPacket.getLength() - 4;
      fileOutputStream.write(data, 4, bytesRead);
      buffer[2] = (byte) (blockNumber >> 8); // block number
      buffer[3] = (byte) (blockNumber & 0xFF); // block number
      response.setData(buffer);
      socket.send(response);
      if (bytesRead < 512) {
        lastPacketReceived = true;
      }
      blockNumber++;
    }
    System.out.println("File transfer complete.\n");
    fileOutputStream.close();
    socket.close();
  }



  private void sendErrorPacket(DatagramSocket socket, DatagramPacket packet, int errorCode, String errorMessage) throws IOException {
    //DatagramSocket socket = new DatagramSocket();
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
      }
      if (opcode == 2) {
        handleWriteRequest(receivePacket);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
