import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;


public class SocketClient {

	
	private final InetAddress ipAddress;
	private final int port;
	private volatile TimerTask timerTask;
	private Timer timer = new Timer();
	private final TimeoutReciever parent;
	private final long millisec;
	
	public SocketClient (TimeoutReciever parent,InetAddress ipAddress,int port,final long millisec){
		
		this.parent = parent;
		this.ipAddress = ipAddress;
		this.port = port;
		this.millisec = millisec;
		this.resetTimeout();
	}
	
	public void resetTimeout(){
		if (timerTask!=null) {
			timerTask.cancel();
		}
		
		if (timer !=null) {
			try {
				timer.schedule(timerTask(), millisec);
			} catch (Exception ex) {
				
				
			}
		}
	}
	
	private TimerTask timerTask() {
		return timerTask = new TimerTask() {
			@Override
			public void run() {
				timerFinished();
			}
		};
	}
	
	public void timerFinished() {
		timer.cancel();
		timerTask.cancel();
		parent.timeout(this);
	}
	
	public String toString() {
		return ipAddress.getHostAddress() + ":" + port;
	}
	
	public InetAddress getInetAddress(){
		return ipAddress;
	}
	public int getPort() {
		return port;
	}
}
