import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPin;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.RaspiPin;

/**
 * OrviboSocket Emulator
 * @author cprasmu@gmail.com
 * @version 1.0.0
 */

public class OrviboSocketEmulator implements TimeoutReciever{
	
	private static final String MAGIC_KEY = "6864";
	
	private final  GpioController gpio = GpioFactory.getInstance();
	private GpioPinDigitalOutput piPin ;
	
	private static  InetAddress serverIp; 
	private static final String PAD16="                ";
	private static final String PAD40="                                        ";
	private static final byte [] PORT10K =	{0x10,0x27};
	
	
	public enum OrviboCmd {
		
		MSG_COUNT_DOWN	("cd","Count Down"),
		MSG_SUB_CNF 	("cl","Subscription response received"),
		MSG_CLK_SYNC 	("cs","Clock Sync"),
		MSG_STATE_CNG	("dc","Change State"),
		MSG_DI 			("di","AllOne Button Press"),
		MSG_REG_SVR 	("dl","Register Server"),
		MSG_DN 			("dn","DN - 646E"),
		MSG_HEARTBEAT 	("hb","Heartbeat"),
		MSG_IR_CODE 	("ic","IR Control Command"),
		MSG_IR 			("ir","Check IR command"),
	    MSG_BTN_PRESS 	("ls","BTN-PRESS 6C73"),
		MSG_MOD_PASS 	("mp","Modify Password"),
		MSG_QUERY_ALL 	("qa","Query All"),
		MSG_DISCOVER	("qg","Discover Device"),
		MSG_STATE_CNF	("sf","State Confirm"),
		MSG_READ_TABLE 	("rt","Read Table"),
		MSG_MOD_TABLE	("tm","Modify Table"),
		MSG_UL			("ul","UL 756C"),
		MSG_UR			("ur","UR 7572");
		
		private String command;
		private String description;
		
		 OrviboCmd(String command,String description){
			this.command = command;
			this.description = description;
		}
		
		public String toString(){
			return this.command + " : " + this.description;
		}
		
		public static OrviboCmd valueOf(byte [] bytes,int offset){	
			 byte [] head = {bytes[offset],bytes[offset+1]};
			 return OrviboCmd.fromString(new String( head));
		}
		
		private static final Map<String, OrviboCmd> fromString = new HashMap<>();
	    static {
	        for (OrviboCmd cmd : values()) {
	            fromString.put(cmd.command, cmd);
	        }
	    }

	    public static OrviboCmd fromString(String rep) {
	        return fromString.get(rep);
	    }
	}
	
	private DatagramSocket  scktServer ; 		// For receiving data
	private InetAddress 	localIP; 			// Get our local IP address
	private InetAddress 	broadcastip; 		// Where we'll send our "discovery" packet
	private Timer heartbeatSender  = null;
	
	private byte state = 1;
	private byte[] receiveData 	= new byte[256];
    private byte[] mac 			= new byte[6];
    private byte[] macRev 		= new byte[6];
    private int timezone = 0;
    private int dst = 0;
    private byte discoverable = (byte)1;
    private Properties properties = new Properties(); 
	private Thread listener;
	private String networkInterface = "wlan0";
	private boolean useServer = false;
	
	private static final int port = 10000; // The port we'll connect on
	private static final byte [] twenties = {0x20, 0x20, 0x20, 0x20, 0x20, 0x20}; // this appears at the end of a few packets we send, so put it here for shortness of code
	private static final byte [] zeros = {0,0,0,0,0,0};
	private static String password 		= "888888";
	private static String deviceName 	= "PISocket";
	private static String serverDomain 	= "vicenter.orvibo.com";
	private static String gateway 		= "192.168.2.1";
	private static String unknowMsg     = "com.orvibo.InfraredRemote";
	private static String unknowDest	= "52.28.25.255";
	private static int    unknownPort 	= 47820;
	
	private boolean allOne = false;
	private volatile HashMap<String,SocketClient> subscribers = new HashMap<String,SocketClient>();
	private Pin outputPin=null;
	
	//private int ioPin = 8;
	
	private void addUpdateClient(InetAddress ipAddress,int port) {
		System.out.println("AddUpdate " + ipAddress);
		synchronized(subscribers){
			String key = ipAddress + ":" + port;
			if (!subscribers.containsKey(key)){
				System.out.println("Adding " + key);
				subscribers.put(key, new SocketClient(this,ipAddress,port,45000));
			} else {
				if (subscribers.containsKey(key)){
					System.out.println("Updating " + key);
					subscribers.get(key).resetTimeout();
				}
			}
		}
	}
	
	public String printStatus() {
		
		return "Name \t : " + deviceName +
				"\tPassword \t : " + password +
				"\tState \t : " + state +
				"\tTimezone \t : " + timezone +
				"\tDST \t : " + dst +
				"\tDiscoverable \t : " + discoverable ;
	}
	
	public void saveProperties(){
		
		File f = new File("orvibo.properties");
		
		if(!f.exists()){
			try {
				f.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		properties.setProperty("passwoprd", 	password);
		properties.setProperty("deviceName", 	deviceName);
		properties.setProperty("gateway", 		gateway);
		properties.setProperty("timezone", 		""+timezone);
		properties.setProperty("dst", 			""+dst);
		properties.setProperty("discoverable", 	""+discoverable);
		properties.setProperty("state", 		""+state);
		properties.setProperty("networkInterface", networkInterface);
		properties.setProperty("outputPin", 	outputPin.getName());
		
		try {
			properties.store(new FileWriter("orvibo.properties"),"Modified: "+ new Date());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void loadProperties(){
		
		File f = new File("orvibo.properties");
		
		if(!f.exists()){
			try {
				f.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		try {
			properties.load(new FileReader("orvibo.properties"));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		password 		= properties.getProperty("passwoprd", 	password);
		deviceName 		= properties.getProperty("deviceName", 	deviceName);
		gateway 		= properties.getProperty("gateway", 	gateway);
		timezone 		= Integer.parseInt(properties.getProperty("timezone", 		"" + timezone));
		dst 			= Integer.parseInt(properties.getProperty("dst", 			"" + dst));
		discoverable 	= (byte) Integer.parseInt(properties.getProperty("discoverable", 	"" + discoverable));
		state 			= (byte) Integer.parseInt(properties.getProperty("state", 		"" + state));
		networkInterface = properties.getProperty("networkInterface", networkInterface);
		useServer 		= Boolean.parseBoolean(properties.getProperty("useServer", "false"));
		
		outputPin 		= RaspiPin.getPinByName(properties.getProperty("outputPin","GPIO 8"));
		
		System.out.println("" + outputPin.toString());
		
	}
	

	
	public OrviboSocketEmulator() throws Exception{
		
		try {

			loadProperties();
			saveProperties();
			
			serverIp 	= InetAddress.getByName("vicenter.orvibo.com");
			localIP 	= InetAddress.getLocalHost();
			broadcastip = InetAddress.getByName("255.255.255.255");
			
			System.out.println("Loacal IP : " + localIP);
	
		//	NetworkInterface network = NetworkInterface.getByInetAddress(localIP);
			NetworkInterface network = NetworkInterface.getByName(networkInterface);
	
			mac = network.getHardwareAddress();
			
			macRev[5]	= mac[0];
			macRev[4]	= mac[1];
			macRev[3]	= mac[2];
			macRev[2]	= mac[3];
			macRev[1]	= mac[4];
			macRev[0]	= mac[5];
			
			scktServer 	= new DatagramSocket(port);
			listener 	= new Thread(new Listener());
			listener.start();
			
			if (useServer) {
				heartbeatSender = new Timer("Heartbeat Timer",true); 
				heartbeatSender.schedule(new SenderTask(), 30000,30000);
				registerWithServer(InetAddress.getByName(serverDomain));
			}

			setupPins(outputPin);
			switchRelay(state==1);
			
		} catch (UnknownHostException e) {e.printStackTrace();}
		
	}
	
	private class SenderTask extends TimerTask {
		
        public void run() {
        	try{
        		registerWithServer(InetAddress.getByName(serverDomain));
    		}
    		catch (Exception ex){
    			ex.printStackTrace();
    		}
        }
    }
	
	private void sendMessage(byte [] message, InetAddress iPAddress) { 
		sendMessage( message,  iPAddress, port);
	}
	
	private void sendMessage(byte [] message, InetAddress iPAddress,int port) { 
		
		long len =  message.length+4;
		
		byte len1 = (byte) ( (len /256) & 0xff);
		byte len2 = (byte) ((len)& 0xff);

		byte [] tmp  = new byte[]{0x68,0x64,len1,len2};
		tmp = Utils.concat(tmp, message);
		
		DatagramPacket sendPacket = new DatagramPacket(tmp, tmp.length, iPAddress, port);
		try {
			System.err.println("TX [" + OrviboCmd.valueOf(message,0) + "] TO " + iPAddress.getHostAddress() + "\t : " + Utils.bytesToHex(tmp));
			scktServer.send(sendPacket);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String [] args){
		try {
			OrviboSocketEmulator s = new OrviboSocketEmulator();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void queryAllResposne(InetAddress ipAddress){
		
		byte [] start 	=   {0x71, 0x61, 0x00};
		byte [] tmp		=	Utils.concat(start,	mac);
		tmp				=	Utils.concat(tmp,	twenties);
		tmp				=	Utils.concat(tmp,	macRev);
		tmp				=	Utils.concat(tmp,	twenties);
		byte [] end  	=   {0x53, 0x4f, 0x43, 0x30, 0x30, 0x32};
		byte [] endAllone  	=    {0x49, 0x52, 0x44, 0x30, 0x30, 0x35};

		byte [] timeData =  Utils.longToBytes(Utils.getTimeSince1900());
		byte [] end2 	=   {timeData[7], timeData[6],  timeData[5],timeData[4], state};
		
		if (allOne){
			tmp				=	Utils.concat(tmp,endAllone);
		} else {
			tmp				=	Utils.concat(tmp,end);
		}
		tmp				=	Utils.concat(tmp,end2);
		
		sendMessage(tmp,ipAddress);
	}
	
	public void discoverResponse(InetAddress ipAddress){
		
		byte [] start 	=   {0x71, 0x67,00};
		byte [] tmp		=	Utils.concat(start,	mac);
		tmp				=	Utils.concat(tmp,	twenties);
		tmp				=	Utils.concat(tmp,	macRev);
		tmp				=	Utils.concat(tmp,	twenties);
		byte [] end 	=   {0x53,0x4f,0x43,0x30,0x30,0x32};
		byte [] endAllone  	= {0x49, 0x52, 0x44, 0x30, 0x30, 0x35};
		//byte [] end  	=   {0x49, 0x52, 0x44, 0x30, 0x30, 0x35};
		//byte [] end2 = {(byte)0xd3,(byte)0xef,0x02,(byte)0xda,state};
		byte [] timeData =  Utils.longToBytes(Utils.getTimeSince1900());
		byte [] end2 	=   {timeData[7], timeData[6],  timeData[5],timeData[4], state};
		
		if (allOne){
			tmp			=	Utils.concat(tmp,endAllone);
		} else {
			tmp			=	Utils.concat(tmp,end);
		}
		tmp				=	Utils.concat(tmp,end2);

		sendMessage(tmp,ipAddress);
	}

	/*
	public void subscribeUnknown(InetAddress ipAddress, byte[] macAddr){
		byte [] start = {0x63, 0x6c};
		byte [] tmp	=	Utils.concat(start,macAddr);
		tmp		=	Utils.concat(tmp,twenties);
		tmp		=	Utils.concat(tmp,(password + PAD16).substring(0, 12).getBytes());
		tmp		=	Utils.concat(tmp,(unknowMsg + PAD40).substring(0, 40).getBytes());
		
		sendMessage(tmp,ipAddress);
	}
	*/
	
	public void subscribeResponse(InetAddress ipAddress){

		byte [] start 	=   {0x63, 0x6c};
		byte [] tmp		=	Utils.concat(start,mac);
		tmp				=	Utils.concat(tmp,twenties);
		tmp				=	Utils.concat(tmp,zeros);
		sendMessage(tmp,ipAddress);
	}
	
	public void registerWithServer(InetAddress ipAddress){

		byte [] start	=   {0x64, 0x6c};
		byte [] tmp		=	Utils.concat(start,mac);
		tmp				=	Utils.concat(tmp,twenties);
		byte [] end1 	=   {0x64,0,0,0};
		tmp				=	Utils.concat(tmp,end1);
		tmp				=	Utils.concat(tmp,macRev);
		tmp				=	Utils.concat(tmp,twenties);
		sendMessage(tmp,ipAddress);
	}
	
	public void disccoverRequest(InetAddress ipAddress, byte[] macAddr){
	
		byte [] start 	=   {0x71, 0x67};
		byte [] tmp		=	Utils.concat(start,macAddr);
		tmp				=	Utils.concat(tmp,twenties);
		tmp				=	Utils.concat(tmp,zeros);
		sendMessage(tmp,ipAddress);
	}
	
	public void readTable(InetAddress ipAddress,byte [] rxbytes) {
		
		int table = rxbytes[22];
		
		//68:64:00:24:72:74:ac:cf:23:35:45:98:20:20:20:20:20:20:02:00:00:00:00:01:00:01:00:00:06:00:04:00:04:00:02:00
		//68:64:00:a8:72:74:ac:cf:23:35:45:98:20:20:20:20:20:20:02:00:00:00:00:04:00:01:00:00:8a:00:01:00:43:25:ac:cf:23:35:45:98:20:20:20:20:20:20:98:45:35:23:cf:ac:20:20:20:20:20:20:38:38:38:38:38:38:20:20:20:20:20:20:53:6f:63:6b:65:74:20:20:20:20:20:20:20:20:20:20:04:00:20:00:00:00:10:00:00:00:05:00:00:00:10:27:2a:79:6f:d0:10:27:76:69:63:65:6e:74:65:72:2e:6f:72:76:69:62:6f:2e:63:6f:6d:20:20:20:20:20:20:20:20:20:20:20:20:20:20:20:20:20:20:20:20:20:c0:a8:01:c8:c0:a8:01:01:ff:ff:ff:00:01:01:00:00:00:ff:00:00
		//68:64:00:A8:72:74:A8:20:66:3A:34:20:20:20:20:20:20:20:02:00:00:00:00;04:00:01:00:00:8A:00:01:00:43:25:A8:20:66:3A:34:20:20:20:20:20:20:20:20:34:3A:66:20:A8:20:20:20:20:20:20:38:38:38:38:38:38:20:20:20:20:20:20:53:6F:63:6B:65:74:31:20:20:20:20:20:20:20:20:20:04:00:20:00:00:00:10:00:00:00:05:00:00:00:10:27:2A:79:6F:D0:10:27:76:69:63:65:6E:74:65:72:2E:6F:727669626F2E636F6D202020202020202020202020202020202020202020C0A801 C8 C0 A8 01 01 FF FF FF 00 01 01 00 00 00 FF 00 00
		//68 64 00 A5 74 6D A8 20 66 3A 34 20 20 20 20 20 20 20000000000400018A0001004325A820663A342020202020202020343A6620A82020202020203838383838382020202020205261737069536F632020202020202020040020000000100000000500000010272A796FD01027766963656E7465722E6F727669626F2E636F6D202020202020202020202020202020202020202020                                                                                                         C0 A8 01 50 C0 A8 02 01 FF FF FF 00 01 00 00 00 00 FF 00 00
		
		byte [] start 	=   {0x72, 0x74};
		byte [] tmp		=	Utils.concat(start,	mac);
		tmp				=	Utils.concat(tmp,	twenties);
		
		if (table==1){
			//TABLE 1
			//	byte [] end1 =   {2,0,0,0,0,1,0,1,0,0,6,0,4,0,4,0,2,0};
			byte [] end1 =   {2,0,0,0,0,1,0,1,0,0,6,0,4,0,4,0,2,0};
			//6864001D7274A820663A3420202020202020 00 00 00 00 01 000000000000
			//686400247274A820663A3420202020202020 02 00 00 00 00 010001000006 00040004000200
			//6864002C7274ACCF232419C0202020202020 02 00 00 00 00 010001000006 00040004001700 0600030003000200
			tmp		=	Utils.concat(tmp,end1);
			
		} else if (table==3){
			//TABLE 3
			/*
			 *  02 00                                                           - Record ID Little Endian = 02
                00 00 00                                                        - ??? Unknown ???
                03                                                              - Table Number
                00 01 00 00                                                     - ??? Unknown ???
                1C 00                                                           - Record Length Little Endian = 28bytes
                01 00                                                           - Record Number Little Endian = 1
                E2 72 80 00 63 0E 00 00 00 5C DE 16 00 A0 19 00                 - ??? Unknown ???
                01 00                                                           - Power state = on (00 = off, 01 = on)
                DE 07                                                           - Year Little Endian = 2014
                07                                                              - Month = 7
                0D                                                              - Day = 13
                10                                                              - Hour - 2? = 18 = 6pm
                00                                                              - Minute = 00
                00                                                              - Second = 00
                FF                 
			 */
			tmp		=	Utils.concat(tmp,new byte []{2,0}); 	// Record ID Little Endian = 02
			tmp		=	Utils.concat(tmp,new byte []{0,0,0}); 	// Unknown
			tmp		=	Utils.concat(tmp,new byte []{0x03});	// Table Number
			tmp		=	Utils.concat(tmp,new byte []{0,1,0,0}); // ??? Unknown ???
			tmp		=	Utils.concat(tmp,new byte []{0x1c,0});  // Record Length Little Endian = 28bytes
			tmp		=	Utils.concat(tmp,new byte []{1,0}); 	// Record Number Little Endian = 1
			tmp		=	Utils.concat(tmp,new byte []{(byte)0xE2 ,0x72 ,(byte)0x80, 0 ,0x63 ,0x0E, 0, 0, 0, 0x5C ,(byte)0xDE ,0x16, 00, (byte)0xA0, 0x19, 00}); //??? Unknown ???
			tmp		=	Utils.concat(tmp,new byte []{state,0}); // Power state = on (00 = off, 01 = on)
			
			//int year = Calendar.getInstance().get(Calendar.YEAR);
			//int month = Calendar.getInstance().get(Calendar.MONTH);
			//int date = Calendar.getInstance().get(Calendar.DATE);
			
			//ByteBuffer.allocate(2).putInt(year).array();
			
			tmp		=	Utils.concat(tmp,new byte []{(byte)0xDE ,7,7,0x0d,0x10,0,0,(byte)0xff,0x1C ,0,2,0,(byte)0xE2,0x72,(byte) 0x80,0,0x71,0x0F,0, 0, 0x50, 0x72, (byte) 0xD2, 0x16, 00 ,(byte) 0xA0, 0x19, 0,0,0,(byte)0xde,07,7,0x0d,0x13,0,0,(byte)0xff});
			
//			byte [] end1 =   {2,0,0,0,0,3,0,1,0,0,0x1c,0,1,0,(byte)0xE2 ,0x72 ,(byte)0x80, 0 ,0x63 ,0x0E, 0, 0, 0, 0x5C ,(byte)0xDE ,0x16, 00, (byte)0xA0, 0x19, 00,state,0,(byte)0xDE ,7,7,0x0d,0x10,0,0,(byte)0xff,0x1C ,0,2,0,(byte)0xE2,0x72,(byte) 0x80,0,0x71,0x0F,0, 0, 0x50, 0x72, (byte) 0xD2, 0x16, 00 ,(byte) 0xA0, 0x19, 0,0,0,(byte)0xde,07,7,0x0d,0x13,0,0,(byte)0xff};
			//tmp		=	Utils.concat(tmp,end1);
			
		} else {
			
			//TABLE 4
			try {
				byte[] end2 =  {2,0,0,0,0,4,0,1,0,0,(byte)0x8a,0,1,0,0x43,0x25};
				tmp			=	Utils.concat(tmp,end2);
				tmp			=	Utils.concat(tmp,mac);
				tmp			=	Utils.concat(tmp,twenties);
				tmp			=	Utils.concat(tmp,macRev);
				tmp			=	Utils.concat(tmp,twenties);
				tmp			=	Utils.concat(tmp,(password + PAD16).substring(0, 12).getBytes());
				tmp			=	Utils.concat(tmp,(deviceName + PAD16).substring(0, 16).getBytes());
	
				byte [] end4 =  {0x01,0x00};//byte [] end4 =  {0x05,0x02};
				//byte [] end5 =  {0x20,0x00,0x00,0x00,0x10,0x00,0x00,0x00,0x05,0x00,0x00,0x00};
				byte [] end5 =  {0x30,0x00,0x00,0x00,0x10,0x00,0x00,0x00,0x05,0x00,0x00,0x00};
				byte [] end10 =  {(byte)0xff,(byte)0xff,(byte)0xff,0x00}; //netmask
				byte [] end11 =  {1,discoverable,(byte)dst,(byte)timezone,0,(byte)0xff,0,0};
	 
				tmp		=	Utils.concat(tmp,end4);
				tmp		=	Utils.concat(tmp,end5);
				tmp		=	Utils.concat(tmp,PORT10K);
				tmp		=	Utils.concat(tmp,InetAddress.getByName(serverDomain).getAddress());
				tmp		=	Utils.concat(tmp,PORT10K);
				tmp		=	Utils.concat(tmp,(serverDomain + PAD40).substring(0, 40).getBytes());
				tmp		=	Utils.concat(tmp,InetAddress.getLocalHost().getAddress());
				tmp		=	Utils.concat(tmp,InetAddress.getByName(gateway).getAddress());
				tmp		=	Utils.concat(tmp,end10);
				tmp		=	Utils.concat(tmp,end11);
			} catch(Exception ex){
				System.err.println(ex.toString());
				return ;
			}
		}
																																																																														//TS TZ
		sendMessage(tmp,ipAddress);
	}
	
	public void modifyTable(InetAddress ipAddress,byte[] rxbytes) {
		
		int table = rxbytes[22];
		
		System.out.println("Mod Table!");
		if (table==1) {
			
		} else if (table==3) {
			//68 64 00 37 74 6D A8 20 66 3A 34 20 20 20 20 20 20 20 00 00 00 00 0300001C001158202020202020202020202020202020200000DF070B1D111437FF
		} else if (table==4) {
			String newDeviceName=   "";
			for (int i=67;i<85;i++){
				newDeviceName = newDeviceName + (char) rxbytes[i];
			}
			
			newDeviceName = newDeviceName.trim();
			
			if (!deviceName.equals(newDeviceName)) {
				deviceName = newDeviceName;
				System.out.println("DeviceName : " + deviceName );
			}
			
			if (rxbytes[158]!=discoverable){
				discoverable = rxbytes[158];
				System.out.println("discoverable : " + discoverable );
			}
			
			if (rxbytes[157]!=(byte)dst){
				
				dst=rxbytes[157];
				System.out.println("dst : " + dst );
			}
			
			if (rxbytes[160]!=(byte)timezone){
				timezone=rxbytes[160];
				System.out.println("timezone : " + timezone );
			}
		
		}
		
		modifyTableResponse(ipAddress);
		saveProperties();
	}
	
	public void setupPins(Pin outputPin) {
		
		gpio.shutdown();
		GpioPin gppin=null;
		
		for(GpioPin apin: gpio.getProvisionedPins()){
			gppin = apin;
		}
		
		if (gppin!=null){
			gpio.unprovisionPin(gppin);
		}
		
		piPin = gpio.provisionDigitalOutputPin(outputPin, "OUTPUT1");
		
	}
	
	
	public void switchRelay(boolean state){

			 if (state==true) {
				 piPin.low();
			 } else {
				 piPin.high();
			 } 
	}
	
	
	public void changeState(InetAddress ipAddress,byte[] rxbytes) {

		//6864001E6463A820663A342020202020202000000000CD01 00 2A 004EA3E9
	    //686400176463A820663A34202020202020200000000001

		if (rxbytes.length==30){
			int sw = rxbytes[25];
			state = rxbytes[24];
			System.out.println("Switch : " + sw + " \t State " + state);
			byte [] req= {rxbytes[22],rxbytes[23],rxbytes[24],rxbytes[25],rxbytes[26],rxbytes[27],rxbytes[28],rxbytes[29]};
		} else {
			state = rxbytes[22];
		}

		for(String key:subscribers.keySet()){
			SocketClient sc = subscribers.get(key);
			System.out.println("Sending state "+state+" to subscriber " + sc.getInetAddress().getHostAddress() + ":" + sc.getPort());	
			powerResponse(sc.getInetAddress(),(byte)0);
			powerResponseConfirm(sc.getInetAddress(), sc.getPort());
		}
	
		switchRelay(state==1);
	
		/////powerResponse(ipAddress,(byte)0);
		/////powerResponseConfirm(ipAddress, port);
	}
	
	public void powerResponse(InetAddress ipAddress, byte state){
		
		byte [] start 	=   {0x64, 0x63};
		byte [] tmp		=	Utils.concat(start,mac);
		tmp				=	Utils.concat(tmp,twenties);
		byte[] end 		=   {0,0,0,0,state}; 
		tmp				=	Utils.concat(tmp,end);
		sendMessage(tmp,ipAddress);
	}
	
	
	
	public void changePassword(InetAddress ipAddress,byte[] rxbytes) {
		byte [] oldpass = {rxbytes[22], rxbytes[23],rxbytes[24],rxbytes[25],rxbytes[26],rxbytes[27] ,rxbytes[28], rxbytes[29],rxbytes[30],rxbytes[31],rxbytes[32],rxbytes[33]};
		byte [] newpass = {rxbytes[34], rxbytes[35],rxbytes[36],rxbytes[37],rxbytes[38],rxbytes[39] ,rxbytes[40], rxbytes[41],rxbytes[42],rxbytes[43],rxbytes[44],rxbytes[45]};
		
		String tmp = (new String(oldpass)).trim();
		if (password.equals(tmp)){
			password = (new String(newpass)).trim();
			System.out.println("Password Changed");
		}
		//TODO passwordChangeResponse(iPAddress,mac,(byte)0);
		modifyTableResponse(ipAddress);
	}
	
	
	/*
	public void powerResponseAllOne(InetAddress ipAddress, byte[] macAddr,byte state,byte[] reqId){
		
		byte [] start 	=   {0x64, 0x63};
		byte [] tmp		=	Utils.concat(start,macAddr);
		tmp				=	Utils.concat(tmp,twenties);
		byte[] end 		=   {0,0,0,0}; 
		tmp				=	Utils.concat(tmp,end);
		tmp				=	Utils.concat(tmp,reqId);
		sendMessage(tmp,ipAddress);
	}
	*/
	public void powerResponseConfirm(InetAddress ipAddress){
		powerResponseConfirm( ipAddress, port);
	}
	
	public void powerResponseConfirm(InetAddress ipAddress,int port){
		
		byte [] start 	=   {0x73, 0x66};
		byte [] tmp		=	Utils.concat(start,mac);
		tmp				=	Utils.concat(tmp,twenties);
		byte[] end 		=   {0,0,0,0,state}; 
		tmp				=	Utils.concat(tmp,end);
		sendMessage(tmp,ipAddress,port);
	}
	/*
	public void powerResponseConfirmAllOne(InetAddress ipAddress,byte[] reqId){
		
		byte [] start 	=   {0x73, 0x66};
		byte [] tmp		=	Utils.concat(start,mac);
		tmp				=	Utils.concat(tmp,twenties);
		byte[] end 		=   {0,0,0,0}; 
		tmp				=	Utils.concat(tmp,end);
		tmp				=	Utils.concat(tmp,reqId);
		//6864001E6463A820663A3420202020202020 00 00 00 00 B647012C0028F1F9
		sendMessage(tmp,ipAddress);
	}
	*/
	public void heartbeatResponse(InetAddress ipAddress,byte token){
		//System.out.println("Heartbeat : " + token);
		byte [] start 	=   {0x68, 0x62};
		byte [] tmp		=	Utils.concat(start,mac);
		tmp				=	Utils.concat(tmp,twenties);
		byte[] end 		=   {token,0,0,state}; 
		tmp				=	Utils.concat(tmp,end);
		sendMessage(tmp,ipAddress);
	}
	
	
	public void modifyTableResponse(InetAddress ipAddress){
		
		//68:64:00:17:74:6d:ac:cf:23:35:45:98:20:20:20:20:20:20:00:00:00:00:00
		byte [] start 	=   {0x74, 0x6d};
		byte [] tmp		=	Utils.concat(start,mac);
		tmp				=	Utils.concat(tmp,twenties);
		byte[] end 		=   {0,0,0,0,0};
		tmp				=	Utils.concat(tmp,end);
		sendMessage(tmp,ipAddress);
	}
	
	public void learningModeResponse(InetAddress ipAddress, byte[] mac){
		//68 64 00 18 6c 73 AC CF 23 2A 5F FA 20 20 20 20 20 20 01 00 00 00 00 00
		
		byte [] start 	=   {0x74, 0x6d};
		byte [] tmp		=	Utils.concat(start,mac);
		tmp				=	Utils.concat(tmp,twenties);
		byte[] end 		=   {1,0,0,0,0,0};
		tmp				=	Utils.concat(tmp,end);
		sendMessage(tmp,ipAddress);
	}
	
	public void passwordChangeResponse(InetAddress ipAddress, byte[] macAddr,byte token){

		saveProperties();
	}
	
	public void clockSync(InetAddress ipAddress,byte [] rxbytes){
		
		byte [] start = {0x63, 0x73};
		byte [] tmp	=	Utils.concat(start,mac);
		tmp			=	Utils.concat(tmp,twenties);
		byte [] data = {00, 00, 00, 00,rxbytes[25], rxbytes[24], rxbytes[23], rxbytes[22]};
		System.out.println(Utils.getDatefromSecsSince1900(Utils.bytesToLong(data)));
		System.out.println(new Date());
		tmp			=	Utils.concat(tmp,data);
		sendMessage(tmp,ipAddress);
	}

	private class Listener implements Runnable {
		
		public void run() {
			while(true){
				try {
					
					DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
					scktServer.receive(receivePacket);    
					InetAddress iPAddress = receivePacket.getAddress();
					int rxPort = receivePacket.getPort();
				   
					if (!iPAddress.equals(localIP) && (receiveData[0]==0x68) && (receiveData[1]==0x64) && (receivePacket.getLength()>4)) {
					   
						byte[] rxbytes = new byte [receivePacket.getLength()];
						System.arraycopy(receiveData, 0, rxbytes, 0, receivePacket.getLength());
						OrviboCmd cmd = OrviboCmd.valueOf(rxbytes,4);
					   
						switch (cmd) {
					  
							case MSG_QUERY_ALL :
					
								if (rxbytes.length==6) {
									queryAllResposne(iPAddress);
								}
								break;
								
					   		case MSG_READ_TABLE :
					   			readTable(iPAddress,rxbytes);
					   			break;
					   			
					   		case MSG_MOD_TABLE:
					   			modifyTable(iPAddress,rxbytes); 
					   			break;
					
					   		case MSG_SUB_CNF:
					   			addUpdateClient(iPAddress,rxPort);
					   			System.out.println("Subscribe!");
					   			subscribeResponse(iPAddress);
					   			break;
					   			
					   		case MSG_STATE_CNG:      	
					   			changeState(iPAddress,rxbytes);
					   			break;
					   			
					   		case MSG_STATE_CNF:
					   			break;
					   			
					   		case MSG_REG_SVR:
					   			System.out.println("Register Acknowledged");
					   			break;
					   			
					   		case MSG_DISCOVER:
					   			byte [] rxMaca = {rxbytes[6], rxbytes[7],rxbytes[8],rxbytes[9],rxbytes[10],rxbytes[11]};
					   			String tmp1 = new String(rxMaca);
					   			String tmp2 = new String(mac);
					   			
					   			if (tmp1.equals(tmp2)) {
					   				discoverResponse(iPAddress);
					   			}
					   			
					   			break;
					   			
					   		case MSG_HEARTBEAT:
					   			heartbeatResponse(iPAddress,rxbytes[18]);
					   			break;
					   			
					   		case MSG_MOD_PASS:    			
					   			changePassword(iPAddress,rxbytes);
					   			break;
					   				                   			
					   		case MSG_CLK_SYNC:
					   			clockSync(iPAddress,rxbytes);
					   			break;
					   			
					   		default :
					   			System.out.println("RX ["+cmd+ "] FROM " + iPAddress.getHostAddress() + ":"+ port +"\t : " + Utils.bytesToHex(rxbytes));
					   
					       }
				   } 
			        
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public void timeout(SocketClient caller) {
		synchronized(subscribers){
			if(subscribers.containsKey(caller.toString())){
				System.out.println("Removing "+caller);
				subscribers.remove(caller.toString());
			}
		}
	}
}
