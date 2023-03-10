
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;


public class RequestHandler implements Runnable {

  private DatagramPacket receivePacket;

  private InetSocketAddress inetSocketAddress;

  private Helper helper;


  public RequestHandler(DatagramPacket receivePacket, InetSocketAddress inetSocketAddress) {
    helper = new Helper();
    this.receivePacket = receivePacket;
    this.inetSocketAddress = inetSocketAddress;
  }

  private void handleReadRequest(DatagramSocket socket, DatagramPacket packet) throws IOException {
    //DatagramSocket socket = new DatagramSocket();
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
        System.out.println("Sending block: " + blockNumber);
        try {
          socket.setSoTimeout(1); // set timeout to 50ms
          DatagramPacket ackPacket = new DatagramPacket(new byte[4], 4); // create ack packet
          socket.receive(ackPacket); // receive ack packet
          if (ackPacket.getData()[0] == 0 && ackPacket.getData()[1] == 4 &&
              ackPacket.getData()[2] == buffer[2] && ackPacket.getData()[3] == buffer[3]) {
            ackReceived = true; // ack received
            System.out.println("Received ack for block " + blockNumber);
          } else {
            System.out.println("ACK not received for block " + blockNumber + " ,retransmitting block...");
            //retryCount++; // increment retry count
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
      blockNumber++;
    }
    System.out.println("File transfer complete.\n");
    fileInputStream.close();
    socket.close();
  }


  private void handleWriteRequest(DatagramSocket socket, DatagramPacket packet) throws IOException {
    String filename = helper.extractFilename(packet);
    if (filename == null) {
      sendErrorPacket(socket, packet, Server.ERR_FNF, "Invalid filename.");
      System.out.println("Invalid filename.");
      return;
    }
    File file = new File(Server.WRITEDIR + filename);
    if (file.exists()) {
      sendErrorPacket(socket, packet, Server.ERR_FNF, "File already exists.");
      System.out.println("File already exists.");
      return;
    }
    FileOutputStream fileOutputStream = helper.openOutputStream(file);
    if (fileOutputStream == null) {
      sendErrorPacket(socket, packet, Server.ERR_FNF, "Could not open file.");
      System.out.println("Could not open file.");
      return;
    }
    short blockNumber = 0;
    while (true) {
      DatagramPacket dataPacket = readAndWriteData(socket, helper.ackPacket(blockNumber++), blockNumber);
      if (dataPacket == null) {
        helper.handleIncompleteFile(file, fileOutputStream);
      }
      assert dataPacket != null;
      byte[] data = helper.extractDataFromPacket(dataPacket);
      if (data == null) {
        System.err.println("Invalid data packet received. Aborting transfer.");
        break;
      }
      if (!helper.writeDataToFile(data, fileOutputStream)) {
        System.err.println("Could not write to file. Aborting transfer.");
        break;
      }
      if (helper.isLastPacket(dataPacket)) {  // last packet
        helper.sendLastAckPacket(socket, blockNumber);
        System.out.println("All done writing file.");
        fileOutputStream.close();
        break;
      }
    }
    helper.closeOutputStream(fileOutputStream);
  }


  private short getData(DatagramPacket data) {
    byte[] buf = data.getData();
    short opcode = (short) ((buf[0] << 8) | (buf[1] & 0xFF));
    if (opcode == Server.OP_ERR) {
      System.err.println("Client is dead. Closing connection.");
      parseError(buf);
      return -1;
    }
    return (short) ((buf[2] << 8) | (buf[3] & 0xFF));
  }

  private DatagramPacket readAndWriteData(DatagramSocket socket, DatagramPacket ack, short block) {
    int retryCount = 0;
    //byte[] rec = new byte[BUFSIZE];
    //DatagramPacket receiver = new DatagramPacket(rec, rec.length);
    while (retryCount < 6) {
      try {
        System.out.println("Sending ACK for block: " + block);
        socket.send(ack);
        socket.setSoTimeout((int) Math.pow(2, retryCount++) * 1000);
        byte[] buffer = new byte[Server.BUFSIZE];
        DatagramPacket dataPacket = new DatagramPacket(buffer, buffer.length);
        socket.receive(dataPacket);
        short blockNum = getData(dataPacket);
        if (blockNum == block) {
          return dataPacket;
        } else if (blockNum == -1) {
          return null;
        } else {
          System.out.println("Duplicate ACK received. Retrying...");
          retryCount = 0;
        }
      } catch (SocketTimeoutException e) {
        System.out.println("Timeout. Retrying...");
      } catch (IOException e) {
        System.err.println("IO Error. Aborting transfer.");
        break;
      } finally {
        helper.resetTimeout(socket);
      }
    }
    System.err.println("Max number of retries reached. Aborting transfer.");
    return null;
  }

  private void parseError(byte[] data) {
    ByteBuffer buffer = ByteBuffer.wrap(data);
    short errCode = buffer.getShort();
    int msgStart = 4;
    int msgEnd = msgStart;
    while (data[msgEnd] != 0) {
      msgEnd++;
    }
    String msg = new String(data, msgStart, msgEnd - msgStart);
    if (errCode > 7) errCode = 0;
    System.err.println(Server.errorCodes[errCode] + ": " + msg);
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
      DatagramSocket socket = new DatagramSocket();
      socket.connect(inetSocketAddress);
      byte[] buf = receivePacket.getData();
      if (receivePacket.getLength() < 2) {
        sendErrorPacket(socket, receivePacket, 4, "Illegal TFTP operation.");
      }
      ByteBuffer wrap= ByteBuffer.wrap(buf);
      short opcode = wrap.getShort();
      //byte[] buffer = new byte[516];
      //DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
      if (opcode == 1) {
        handleReadRequest(socket, receivePacket);
      }
      if (opcode == 2) {
        handleWriteRequest(socket, receivePacket);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
