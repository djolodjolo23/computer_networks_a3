import static server.TFTPServer.BUFSIZE;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;


public class RequestHandler implements Runnable {

  private DatagramPacket receivePacket;

  private InetSocketAddress inetSocketAddress;


  public RequestHandler(DatagramPacket receivePacket, InetSocketAddress inetSocketAddress) {
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
    //DatagramSocket socket = new DatagramSocket();
    String filename = new String(packet.getData(), 2, packet.getLength() - 2).split("\0")[0];
    File inputFile = new File(filename);
    if (!inputFile.exists()) {
      sendErrorPacket(socket, packet, Server.ERR_FNF, "File not found");
      System.out.println("File not found");
      return;
    }
    File outputFile = new File(Server.WRITEDIR + filename);
    FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
    byte[] buffer = new byte[516];// 512 bytes for data + 4 bytes for header
    buffer[0] = 0; // data packet
    buffer[1] = 2; // write request
    buffer[2] = 0; // block number
    buffer[3] = 0; // block number
    DatagramPacket response = new DatagramPacket(buffer, 4, packet.getAddress(), packet.getPort()); // ready to receive
    socket.send(response);
    socket.setSoTimeout(1000);
    socket.receive(response);
    DatagramPacket dataPacket = new DatagramPacket(new byte[516], 516);
    //byte[] datatest = buffer;
    int blockNumber = 1;
    boolean lastPacketReceived = false;
    while (!lastPacketReceived) {
      //.send(ackPacket(blockNumber++));
      socket.setSoTimeout(1000); // set timeout to 50ms
      //DatagramPacket dataPacket = new DatagramPacket(new byte[516], 516);
      socket.receive(dataPacket);
      byte[] data = dataPacket.getData();
      if (data[1] == 5) {
        sendErrorPacket(socket, packet, 4, "Illegal TFTP operation.");
        fileOutputStream.close();
        System.out.println("Illegal TFTP operation.");
        socket.close();
        return;
      }
      int bytesRead = dataPacket.getLength() - 4;
      fileOutputStream.write(data, 4, bytesRead);
      buffer[2] = (byte) ((blockNumber >> 8) & 0xFF); // block number
      buffer[3] = (byte) (blockNumber & 0xFF); // block number
      if (bytesRead < 512) {
        lastPacketReceived = true;
      }
      DatagramPacket ackPacket = new DatagramPacket(buffer, 4, packet.getAddress(), packet.getPort());
      System.out.println("Sending ack for block " + blockNumber);
      socket.send(ackPacket);
      blockNumber++;
    }
    System.out.println("File transfer complete.\n");
    fileOutputStream.close();
    socket.close();
  }

  private void write(DatagramSocket socket, DatagramPacket packet) throws IOException {
    String filename = new String(packet.getData(), 2, packet.getLength() - 2);
    // removing the null bytes
    int nullIndex = filename.indexOf('\0');
    if (nullIndex >= 0) {
      filename = filename.substring(0, nullIndex);
    }
    File file = new File(Server.WRITEDIR + filename);
    if (file.exists()) {
      sendErrorPacket(socket, packet, Server.ERR_FNF, "File already exists.");
      System.out.println("File already exists.");
      return;
    }
    FileOutputStream fileOutputStream;
    try {
      fileOutputStream = new FileOutputStream(file);
    } catch (FileNotFoundException e) {
      sendErrorPacket(socket, packet, Server.ERR_FNF, "File not found");
      System.out.println("File not found");
      return;
    }
    short blockNumber = 0;
    while (true) {
      DatagramPacket dataPacket = readAndWriteData(socket, ackPacket(blockNumber++), blockNumber);
      if (dataPacket == null) {
        try {
          fileOutputStream.close();
        } catch (IOException e) {
          System.err.println("Could not close file. Meh.");
        }
        System.out.println("Deleting incomplete file.");
        file.delete();
        break;
      } else {
        byte[] data = dataPacket.getData();
        try {
          fileOutputStream.write(data, 4, dataPacket.getLength() - 4);
          System.out.println(dataPacket.getLength() - 4 + " bytes written to file.");
        } catch (IOException e) {
          System.err.println("Could not write to file. Meh.");
        }
        if (dataPacket.getLength()-4 < 512) {
          try {
            socket.send(ackPacket(blockNumber));
          } catch (IOException e1) {
            try {
              socket.send(ackPacket(blockNumber));
            } catch (IOException ignored) {
            }
          }
          System.out.println("All done writing file.");
          try {
            fileOutputStream.close();
          } catch (IOException e) {
            System.err.println("Could not close file. Meh.");
          }
          break;
        }
      }
    }
  }

  private DatagramPacket ackPacket(short block) {
    ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
    buffer.putShort(Server.OP_ACK);
    buffer.putShort(block);
    return new DatagramPacket(buffer.array(), 4);
  }


  public static short getData(DatagramPacket data) {
    ByteBuffer buffer = ByteBuffer.wrap(data.getData());
    short opcode = buffer.getShort();
    if (opcode == Server.OP_ERR) {
      System.err.println("Client is dead. Closing connection.");
      parseError(buffer);
      return -1;
    }

    return buffer.getShort();
  }

  private DatagramPacket readAndWriteData(DatagramSocket socket, DatagramPacket ack, short block) {
    int retryCount = 0;
    byte[] rec = new byte[BUFSIZE];
    DatagramPacket receiver = new DatagramPacket(rec, rec.length);

    while (retryCount < 6) {
      try {
        System.out.println("Sending ACK for block: " + block);
        socket.send(ack);

        socket.setSoTimeout((int) Math.pow(2, retryCount++) * 1000);
        socket.receive(receiver);

        short blockNum = getData(receiver);

        if (blockNum == block) {
          return receiver;
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
        try {
          socket.setSoTimeout(0);
        } catch (SocketException e) {
          System.err.println("Error resetting timeout.");
        }
      }
    }

    System.err.println("Max number of retries reached. Aborting transfer.");
    return null;
  }

  private static void parseError(ByteBuffer buffer) {

    short errCode = buffer.getShort();

    byte[] buf = buffer.array();
    for (int i = 4; i < buf.length; i++) {
      if (buf[i] == 0) {
        String msg = new String(buf, 4, i - 4);
        if (errCode > 7) errCode = 0;
        System.err.println(Server.errorCodes[errCode] + ": " + msg);
        break;
      }
    }

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
        write(socket, receivePacket);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
