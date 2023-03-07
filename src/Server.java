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
  public static final String WRITEDIR = "/write/";
  public static final short OP_RRQ = 1;
  public static final short OP_WRQ = 2;
  public static final short OP_DAT = 3;
  public static final short OP_ACK = 4;
  public static final short OP_ERR = 5;
  public static final short ERR_LOST = 0;
  public static final short ERR_FNF = 1;
  public static final short ERR_ACCESS = 2;
  public static final short ERR_EXISTS = 6;
  public static String mode = "octet";

  public static void main(String[] args) {

    //argsCheck(args);
    try (DatagramSocket serverSocket = new DatagramSocket(69)) {
      // ready to receive messages
      byte[] receiveData = new byte[BUFSIZE];
      byte[] sendData = new byte[BUFSIZE];
      System.out.println("Server has successfully started. Listening on the port " + TFTPPORT + "...");
      URL url = Server.class.getResource("Server.class");
      assert url != null;
      String path = url.getPath();
      System.out.println("Detailed file path: " + path + "\n");
      while (true) {
        // client that's accepted
        // can accept multiple connections since in while(true) loop
        try {
          //DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
          // connection established
          // check if all array elements are 0 or not
          DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
          serverSocket.receive(receivePacket);
          //if (!isFilledWithZeros(receiveData)) {
            //serverSocket.receive(new DatagramPacket(receiveData, receiveData.length));
            InetAddress IPAddress = receivePacket.getAddress();
            int port = receivePacket.getPort();
            RequestHandler requestHandler = new RequestHandler(receivePacket, sendData, serverSocket, IPAddress, port);
            Thread thread = new Thread(requestHandler);
            thread.start();
            thread.join();
            //}
        } catch (IOException | InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void argsCheck(String[] args) {
    if (args.length != 2) {
      System.out.println("Usage: java Server <port> <file>");
      System.exit(1);
    }
  }

  public static boolean isFilledWithZeros(byte[] array) {
    for (int i = 0; i < array.length; i++) {
      if (array[i] != 0) {
        return false;
      }
    }
    return true;
  }

  private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
    DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
    try {
      socket.receive(receivePacket);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return new InetSocketAddress(receivePacket.getAddress(),receivePacket.getPort());
  }

}
