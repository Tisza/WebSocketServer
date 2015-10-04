import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

/**
 * Runs a basic server.
 */
public class SimpleServer {
	public static ServerSocket socket;
	public static final int PORT = 3616;
	
	public static void main(String[] args) {
		try {
			socket = new ServerSocket(PORT);
			ForkJoinPool fjp = new ForkJoinPool();
			System.out.println("Socket Opened on: " + PORT);
			while(true) {
				System.out.println("Accepted.");
				Socket s = socket.accept();
				System.out.println("Accepting connection...");
				try {
					ForkJoinTask t = new BasicChat(s);
					fjp.execute(t);
				} catch (IOException e) {
					System.out.println("Unable to open connection streams.");
				}
			}
			
		} catch (IOException e) {
			System.out.println("Failed to open port.");
			System.exit(1);
		}
		
	}
}
