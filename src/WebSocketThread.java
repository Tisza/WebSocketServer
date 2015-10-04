import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;


/**
 * superclass for a swarm based client-socket response thread.
 *
 */
public abstract class WebSocketThread extends RecursiveAction {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2833717157053303120L;
	private Socket s;
	protected InputStream i;
	protected OutputStream o;
	private static Set<WebSocketThread> swarm;
	private static ReadWriteLock l;
	private Date lastUpdate;
	private Date current; 
	private String message;
	private boolean alive;
	
	public WebSocketThread(Socket s) throws IOException {
		this.s = s;
		alive = true;
		i = s.getInputStream();
		o = s.getOutputStream();
		
		lastUpdate = new Date();
		current = new Date();
		
		String str = "";
		String key = "";
		System.out.println();
		System.out.println();
		while(i.available() == 0) {
			// spin until we get our key...
		}
		while(i.available() > 0) { // gets the client's key
			char c = (char) i.read();
			if (c != '\n')
				str += c;
			else {
				System.out.println(str);
					if (str.toLowerCase().contains("sec-websocket-key")) {
						key = str.split(": ")[1]; // substring 19?
						key = key.substring(0, key.length() - 1);
						str = "";
					} else {
						str = "";
					}
			}
		} // it is the key?
		if (!key.equals("")) { // magic number
			key = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
			byte[] bt = new byte[key.length()];
			for(int i = 0; i < key.length(); i++) {
				bt[i] = (byte) key.charAt(i);
			}
			try { // hash it
				MessageDigest md = MessageDigest.getInstance("SHA-1");
				byte[] out = md.digest(bt);
				String back = Base64.encode(out); // encode it.
				String handshake = "";
				handshake += "HTTP/1.1 101 Switching Protocols\r\n";
				handshake += "Upgrade: websocket\r\n";
				handshake += "Connection: Upgrade\r\n";
				handshake += "Sec-WebSocket-Accept: " + back + "\r\n\r\n";
				// write it out
				byte[] res = new byte[handshake.length()];
				for(int i = 0; i < handshake.length(); i++) {
					res[i] = (byte) handshake.charAt(i);
				}
				o.write(res); // respond. We're now open!
			} catch (NoSuchAlgorithmException e) {
				// something went wrong.
				System.out.println("Handshake fail, cannot SHA-1");
			}
			
		} else {
			alive = false;
			System.out.println("Failed to find key.");
		}
		if (swarm == null) { // first thread!
			if (l == null) { // first first thread for sure!
				l = new ReentrantReadWriteLock();
				// potential issue with two locks being created, however is
				// unlikely given these are made on first client connect, which
				// is unlikely to be the same cycle.
			}
			Lock lock = l.writeLock();
			lock.lock();
			swarm = new HashSet<WebSocketThread>();
			swarm.add(this);
			lock.unlock();
		} else {
			Lock lock = l.writeLock();
			lock.lock();
			swarm.add(this);
			lock.unlock();
		}
		
	}
	
	/**
	 * Keeps the client up to date on current scores...
	 */
	@Override
	protected void compute() {
		while(alive) {
			Lock lock = l.readLock();
			lock.lock();
			List<WebSocketThread> list = new ArrayList<WebSocketThread>();
			Date cur = current;
			for(WebSocketThread c : swarm) {
				if (!c.equals(this)) {
					if (current.before(c.lastUpdate)){
						if (cur.before(c.lastUpdate)){
							cur = c.lastUpdate;
						}
						list.add(c);
					}
				}
			}
			lock.unlock();
			current = cur;
			if (list.size() != 0) {
				output(list);
			}
			int bt;
			boolean fin;
			try {
				if (i.available() > 0) {
					if (message == null) {
						message = "";
					}
					bt = reverseBytes(i.read());
					fin = ((bt & 0x1) == 1 ? true : false);
					if ((bt >> 4) == 0x1) {
						System.out.println("closing.");
						alive = false;
						byte[] close = new byte[2];
						close[0] = (byte) (0x1 | (0x8 << 4));
						close[1] = 0;
						o.write(close);
					}
					bt = i.read();
					if ((bt & 0x80) >> 7 != 1) {
						System.out.println("Client didn't mask?");
					}
					bt = bt & 0x7f;
					long len = bt;
					if (bt == 126) {
						len = (i.read() << 8) | i.read();
					} 
					if (bt == 127) {
						len = (i.read() << 56) | (i.read() << 48)
								| (i.read() << 40) | (i.read() << 32) |
								(i.read() << 24) | (i.read() << 16) |
								(i.read() << 8) | i.read();
					}
					int[] mask = new int[4];
					for(int j = 0; j < 4; j++) {
						mask[j] = i.read();
					}
					
					for(long j = 0; j < len; j++) {
						message += (char) (i.read() ^ mask[(int) (j % 4L)]);
					}
					
					if(fin) {
						input(message);
						message = null;
						lastUpdate = new Date();
					}
				}
			} catch (IOException e) {
				// this should never happen but even if it does it we'll catch
				// it next time around...
			}
		}
		try {
			s.close();
			Lock lock = l.writeLock();
			lock.lock();
			swarm.remove(this);
			lock.unlock();
			System.out.println("Connection closed.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Thread's client has sent a msg
	 * @param msg
	 */
	protected abstract void input(String msg);
	
	/**
	 * Another Thread(s) have updated data
	 * @param list
	 */
	protected abstract void output(List<WebSocketThread> list);
	
	private int reverseBytes(int a) {
		return (a & 0x1) << 7 | (a & 0x2) << 5 | (a & 0x4) << 3 | (a & 0x8) << 1
				| (a & 0x10) >> 1 | (a & 0x20) >> 3 | (a & 0x40) >> 5 | (a & 0x80) >> 7;
	}
	
	public boolean send(byte[] data) {
		int len = data.length;
		int totalBytes = len + 2; //(header)
		int code = len;
		if (len > 125) {
			totalBytes = len + 4; // extended length;
			code = 126;
			if (len > (Math.pow(2, 16) - 1)) {
				totalBytes = len + 10; // max length;
				code = 127;
			}
		}
		byte[] out = new byte[totalBytes];
		out[0] = (byte) 0x81;
		out[1] = (byte) code;
		int payload = 2;
		if (code == 126) {
			payload = 4;
		} else if (code == 127) {
			payload = 10;
			
		}
		for(int j = 2; j < payload; j++) {
			out[j] = (byte) (len & (0xff << (2 * (j - 2))));
		}
		for(int j = 0; j < data.length; j++) {
			out[payload + j] = data[j];
		}
		try {
			o.write(out);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

}
