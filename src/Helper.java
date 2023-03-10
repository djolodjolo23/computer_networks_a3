
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Helper {

  public Helper() {

  }

  String extractFilename(DatagramPacket packet) {
    String filename = new String(packet.getData(), 2, packet.getLength() - 2);
    int nullIndex = filename.indexOf('\0');
    return nullIndex >= 0 ? filename.substring(0, nullIndex) : null;
  }

  public void resetTimeout(DatagramSocket socket) {
    try {
      socket.setSoTimeout(0);
    } catch (SocketException e) {
      System.err.println("Error has occurred while resetting timeout.");
    }
  }

  public FileOutputStream openOutputStream(File file) {
    try {
      return new FileOutputStream(file);
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  public void handleIncompleteFile(File file, FileOutputStream fileOutputStream) {
    try {
      fileOutputStream.close();
      System.out.println("Deleting incomplete file.");
      file.delete();
    } catch (IOException e) {
      System.err.println("Could not close file. Meh.");
    }
  }

  public byte[] extractDataFromPacket(DatagramPacket packet) {
    byte[] data = packet.getData();
    int dataLength = packet.getLength() - 4;
    if (dataLength < 0 || dataLength > 512) {
      return null;
    }
    return Arrays.copyOfRange(data, 4, packet.getLength());
  }

  public boolean writeDataToFile(byte[] data, FileOutputStream fileOutputStream) {
    try {
      fileOutputStream.write(data);
      System.out.printf("%d bytes written to file.%n", data.length);
      return true;
    } catch (IOException e) {
      System.err.println("Error writing data to file: " + e.getMessage());
      return false;
    }
  }

  public boolean isLastPacket(DatagramPacket packet) {
    return packet.getLength() - 4 < 512;
  }

  public void sendLastAckPacket(DatagramSocket socket, short blockNumber) throws IOException {
    socket.send(ackPacket(blockNumber));
  }

  public DatagramPacket ackPacket(short block) {
    byte[] data = new byte[4];
    ByteBuffer buffer = ByteBuffer.wrap(data);
    buffer.putShort(Server.OP_ACK);
    buffer.putShort(block);
    return new DatagramPacket(data, data.length);
  }

  public void closeOutputStream(FileOutputStream fileOutputStream) {
    try {
      fileOutputStream.close();
    } catch (IOException e) {
      System.err.println("Output stream could not be closed.");
    }
  }

}
