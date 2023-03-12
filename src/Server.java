import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;


public class Server {

  public static final int TFTPPORT = 69;
  public static final int BUFSIZE = 516;
  public static final String READDIR = "read/";
  public static final String WRITEDIR = "write/";

  public static final short OP_RRQ = 1;
  public static final short OP_WRQ = 2;
  public static final short OP_DAT = 3;
  public static final short OP_ACK = 4;

  public static final String[] errorCodes = {"Not defined", "File not found.", "Access violation.",
      "Disk full or allocation exceeded.", "Illegal TFTP operation.",
      "Unknown transfer ID.", "File already exists.",
      "No such user."};

  /**
   * Main method for the server.
   * Creates the server socket and waits for the client to connect.
   * @param args are the program arguments.
   */
  public static void main(String[] args) {
    argsCheck(args);
    try (DatagramSocket serverSocket = new DatagramSocket(Integer.parseInt(args[0]))) {
      // ready to receive messages
      byte[] receiveData = new byte[BUFSIZE];
      System.out.println("Server has successfully started. Listening on the port " + args[0] + "...");
      URL url = Server.class.getResource("Server.class");
      assert url != null;
      String path = url.getPath();
      System.out.println("Detailed file path: " + path + "\n");
      while (true) {
        try {
          DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
          serverSocket.receive(receivePacket);
          receivePacket.setLength(receiveData.length);
          String request = "";
          if (receivePacket.getData()[1] == 1) {
            request = "READ";
          }
          if (receivePacket.getData()[1] == 2) {
            request = "WRITE";
          }
          System.out.println("Incoming request from " + receivePacket.getAddress() + " on port " + receivePacket.getPort());
          System.out.println("Request type: " + request);
          ErrorHandler errorHandler = new ErrorHandler();
          Helper helper = new Helper();
          InetSocketAddress clientAddress = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
          RequestHandler requestHandler = new RequestHandler(receivePacket, clientAddress, errorHandler, helper);
          Thread thread = new Thread(requestHandler);
          thread.start();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * Checks if the array is filled with zeros.
   * @param var0 is the array.
   * @return true if the array is filled with zeros, false otherwise.
   */
  static boolean integerCheck(String var0) {
    try {
      Integer.parseInt(var0);
      return true;
    } catch (NumberFormatException var2) {
      return false;
    }
  }

  /**
   * Checks if the name of the folder is not restricted.
   *
   * @param folderName is the name of the folder.
   * @return true if the name is not restricted, false otherwise.
   */
  static boolean checkIfNameIsNotRestricted(String folderName) {
    return !folderName.equals(".idea") && !folderName.equals("out") && !folderName.equals("src");
  }


  /**
   * Checks if the public folder exists.
   * @param folderName is the name of the folder.
   * @return true if the folder exists, false otherwise.
   */
  static boolean checkIfPublicFolderExists(String folderName) {
    boolean exist = false;
    File currentDir = new File(folderName);
    if (currentDir.exists()) {
      exist = true;
    }
    return exist;
  }

  /**
   * Checks the program arguments.
   *
   * @param args are the program arguments, port number and a name of the folder.
   */
  static void argsCheck(String[] args) {
    if (args.length != 2) {
      System.err.println("There must be two program arguments, the listening port and a relative folder path");
      System.exit(1);
    } else if (!integerCheck(args[0])) {
      System.err.println("error, the port number is not an integer value");
      System.exit(1);
    } else if (args[0].length() > 5) {
      System.err.println("error, the port number is longer than 5");
      System.exit(1);
    } else if (!checkIfPublicFolderExists(args[1])) {
      System.err.println("The folder provided does not exist!");
      System.exit(1);
    } else if (!checkIfNameIsNotRestricted(args[1])) {
      System.out.println("The name of the folder provided has a restricted access");
      System.exit(1);
    }
  }


}
