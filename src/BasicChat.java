import java.io.IOException;
import java.net.Socket;
import java.util.List;


public class BasicChat extends WebSocketThread {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5278161706554989479L;
	private String msg;
	private String name;

	public BasicChat(Socket s) throws IOException {
		super(s);
	}

	@Override
	protected void input(String msg) {
		if (name == null) {
			name = msg;
		} else {
			this.msg = msg;
		}
	}

	@Override
	protected void output(List<WebSocketThread> list) {
		for (WebSocketThread c : list) {
			BasicChat t = (BasicChat) c;
			if (t.msg != null) {
				byte[] m = new byte[t.msg.length() + t.name.length() + 1];
				int ind = 0;
				for(int i = 0; i < t.name.length(); i++) {
					m[ind++] = (byte) (t.name.charAt(i));
				}
				m[ind++] = (byte) '\n';
				for(int i = 0; i < t.msg.length(); i++) {
					m[ind++] = (byte) (t.msg.charAt(i));
				}
				send(m);
			}
		}
	}

}
