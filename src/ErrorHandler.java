import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ErrorHandler {

  public static final short OP_ERR = 5;
  public static final short ERR_LOST = 0;
  public static final short ERR_FNF = 1;
  public static final short ERR_ACCESS = 2;
  public static final short ERR_EXISTS = 6;


  /**
   * Sends an error packet to the client.
   *
   * @param socket The socket to send the error packet on.
   * @param packet The packet to send the error packet to.
   * @param errorCode The error code to send.
   * @param errorMessage The error message to send.
   * @throws IOException If an I/O error occurs.
   */
  private void sendErrorPacket(
      DatagramSocket socket, DatagramPacket packet, int errorCode, String errorMessage) throws IOException {
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

  /**
   * Handles an invalid filename error.
   *
   * @param socket The socket to send the error packet on.
   * @param packet The packet to send the error packet to.
   * @throws IOException  If an I/O error occurs.
   */
  private void handleInvalidFilename(DatagramSocket socket, DatagramPacket packet) throws IOException {
    sendErrorPacket(socket, packet, ERR_FNF, "Invalid filename.");
    System.err.println("Invalid filename.");
  }

  private void handleFileAlreadyExists(DatagramSocket socket, DatagramPacket packet) throws IOException {
    sendErrorPacket(socket, packet, ERR_EXISTS, "File already exists, cannot be written to.");
    System.err.println("File already exists, cannot be written to.");
  }

  private void handleLostConnection(DatagramSocket socket, DatagramPacket packet) throws IOException {
    sendErrorPacket(socket, packet, ERR_LOST, "Lost connection during transfer.");
    System.err.println("Lost connection during transfer.");
  }

  private void handleAccessViolation(DatagramSocket socket, DatagramPacket packet) throws IOException {
    sendErrorPacket(socket, packet, ERR_ACCESS, "Access violation.");
    System.err.println("IO error while writing data. Access violation.");
  }

  private void handleIllegalTFTPOperation(DatagramSocket socket, DatagramPacket packet) throws IOException {
    sendErrorPacket(socket, packet, 4, "Illegal TFTP operation.");
    System.err.println("Illegal TFTP operation.");
  }

  private void handleUnknownError(DatagramSocket socket, DatagramPacket packet) throws IOException {
    sendErrorPacket(socket, packet, 0, "Unknown error.");
    System.err.println("Unknown error.");
  }

  private void handleTimeoutError(DatagramSocket socket, DatagramPacket packet) throws IOException {
    sendErrorPacket(socket, packet, OP_ERR, "Timeout error.");
    System.err.println("Timeout error.");
  }

  public void handle(DatagramSocket socket, DatagramPacket packet, int errorCode) throws IOException {
    switch (errorCode) {
      case 0 -> handleLostConnection(socket, packet);
      case 1 -> handleInvalidFilename(socket, packet);
      case 2 -> handleAccessViolation(socket, packet);
      case 4 -> handleIllegalTFTPOperation(socket, packet);
      case 5 -> handleTimeoutError(socket, packet);
      case 6 -> handleFileAlreadyExists(socket, packet);
      default -> handleUnknownError(socket, packet);
    }
  }

}
