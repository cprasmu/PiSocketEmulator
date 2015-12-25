import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
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
 * @author Peter Rasmussen November 2015
 * @version 1.0.1
 */

public class OrviboSocketEmulator implements TimeoutReciever{
	
	private static  InetAddress 	serverIp; 
	private InetAddress broadcastAddress;
	private static final String 	PAD16			= "                ";
	private static final String 	PAD40			= "                                        ";
	private static final byte [] 	PORT10K 		= {0x10,0x27};
	private DatagramSocket  		scktServer ; 		// For receiving data
	private InetAddress 			localIP; 			// Get our local IP address	
	private Timer 					heartbeatSender  = null;
	private Timer 					broadcastSender  = null;
	//Properties
	private Properties 				properties 		= new Properties(); 
	public static final String 		PROP_PASWD  	= "password";
	public static final String 		PROP_GATWY  	= "gateway";
	public static final String 		PROP_TIMZN  	= "timezone";
	public static final String 		PROP_DAYST 		= "dst";
	public static final String 		PROP_DNAME  	= "deviceName";
	public static final String 		PROP_DISCO  	= "discoverable";
	public static final String 		PROP_STATE  	= "state";
	public static final String 		PROP_NETIF  	= "networkInterface";
	public static final String 		PROP_OPPIN  	= "outputPin";
	public static final String 		PROP_USSVR  	= "useServer";
	private static String 			deviceName 		= "PISocket";
	private static String 			gateway 		= "192.168.1.254";
	private static String 			password 		= "888888";
	private byte 					state 			= 1;
	private byte[] 					receiveData 	= new byte[256];
    private byte[] 					mac 			= new byte[6];
    private byte[] 					macRev 			= new byte[6];
    private int 					timezone 		= 0;
    private int 					dst 			= 0;
    private byte 					discoverable 	= (byte)1;
    private boolean 				useServer 		= false;
    private static 		InetAddress NTP_SERVER;
    
	private Thread listener;
	private String networkInterface 				= "wlan0";

	private static final int 		port 			= 10000; // The port we'll connect on
	private static final byte [] 	twenties 		= {0x20, 0x20, 0x20, 0x20, 0x20, 0x20}; // this appears at the end of a few packets we send, so put it here for shortness of code
	private static final byte [] 	zeros 			= {0,0,0,0,0,0};
	
	private static String 			serverDomain 	= "vicenter.orvibo.com";
	
	private static String 			unknowDomain     = "com.orvibo.InfraredRemote";
	private static String 			unknowDest		= "52.28.25.255";
	private static int    			unknownPort 	= 47820;
	private boolean 				modeAllOne 		= false;
	private volatile HashMap<String,SocketClient> subscribers = new HashMap<String,SocketClient>();
	//GPIO
	private final  GpioController 	gpio 			= GpioFactory.getInstance();
	private ArrayList<GpioPinDigitalOutput> outputPins;
	
	
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
	
	
	public static String getNetworkAddress(String interfaceName){
		
		Enumeration<NetworkInterface> networkInterfaces;
		try {
			networkInterfaces = NetworkInterface.getNetworkInterfaces();
		
			while (networkInterfaces.hasMoreElements())
			{
			    NetworkInterface networkInterface = (NetworkInterface) networkInterfaces.nextElement();
	
			    List<InterfaceAddress> ia = networkInterface.getInterfaceAddresses();
			    
			    if (networkInterface.getDisplayName().equals(interfaceName)) {
			    	for(int i=0;i<ia.size();i++){
			    		InterfaceAddress address = ia.get(i);
			    		if (address.getAddress() instanceof Inet4Address){
			    			System.err.println("Using address : " + address.getAddress().getHostAddress() );
			    			return address.getAddress().getHostAddress();
			    		}
			    	}
			    }
			}
		
		} catch (SocketException e) {
			e.printStackTrace();
		} 
		
		System.err.println("Failed to get address!");
		
		return null;
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
		
		if (!f.exists()) {
			try {
				f.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		properties.setProperty(PROP_PASWD, 	password);
		properties.setProperty(PROP_DNAME, 	deviceName);
		properties.setProperty(PROP_GATWY, 	gateway);
		properties.setProperty(PROP_TIMZN, 	""+timezone);
		properties.setProperty(PROP_DAYST, 	""+dst);
		properties.setProperty(PROP_DISCO, 	""+discoverable);
		properties.setProperty(PROP_STATE, 	""+state);
		properties.setProperty(PROP_NETIF,  networkInterface);
		
		for (int i=1; i<9; i++) {
			if (properties.contains(PROP_OPPIN + i)){
				properties.setProperty(PROP_OPPIN + i, outputPins.get(i-1).getName());
			}
		}
		
		try {
			properties.store(new FileWriter("orvibo.properties"),"Modified: "+ new Date());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public void loadProperties(){
		
		File f = new File("orvibo.properties");
		
		if(!f.exists()){
			try {
				f.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		try {
			properties.load(new FileReader("orvibo.properties"));
		} catch (FileNotFoundException e) {
			System.err.println("Can't find properties file!");
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		password 		= 	properties.getProperty(PROP_PASWD ,	password);
		deviceName 		=   properties.getProperty(PROP_DNAME, 	deviceName);
		gateway 		=   properties.getProperty(PROP_GATWY, 	gateway);
		timezone 		= Integer.parseInt( properties.getProperty(PROP_TIMZN, 		"" + timezone));
		dst 			= Integer.parseInt( properties.getProperty(PROP_DAYST, 		"" + dst));
		discoverable 	= (byte) Integer.parseInt(properties.getProperty(PROP_DISCO,"" + discoverable));
		state 			= (byte) Integer.parseInt(properties.getProperty(PROP_STATE,"" + state));
		networkInterface = 			properties.getProperty(PROP_NETIF, networkInterface);
		useServer 		= Boolean.parseBoolean(   properties.getProperty(PROP_USSVR, "false"));
		
		resetGPIO();
	
		for (int i=1; i<9; i++) {
			if (properties.containsKey(PROP_OPPIN + i)){
				Pin pin = RaspiPin.getPinByName(properties.getProperty(PROP_OPPIN + i,"GPIO " + (10-i)));
				System.out.println("Found Pin : " + pin.getName());
				GpioPinDigitalOutput piPin = gpio.provisionDigitalOutputPin(pin, pin.getName());
				outputPins.add(piPin);
			}
		}
	}
	
	
	public OrviboSocketEmulator() throws Exception{
		
		try {

			outputPins = new ArrayList<GpioPinDigitalOutput>();
			
			loadProperties();
			saveProperties();
			
			serverIp 	= InetAddress.getByName("vicenter.orvibo.com");
			localIP 	= InetAddress.getByName(getNetworkAddress(networkInterface));	
			NTP_SERVER  = InetAddress.getByName("ntp.apple.com");
			NetworkInterface network = NetworkInterface.getByName(networkInterface);
			
			mac = network.getHardwareAddress();
			
			byte[] bc = localIP.getAddress();
			bc[3]=(byte) 255;
			
			broadcastAddress = InetAddress.getByAddress(bc);
	
			
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
				heartbeatSender.schedule(new ServerTask(), 0,30000);
			}
			
			broadcastSender = new Timer("Broadcast Timer",true); 
			broadcastSender.schedule(new BroadcastTask(), 0,3000);
			
			switchRelay((state==1), 0);
			
		} catch (UnknownHostException e) {e.printStackTrace();}
		
	}
	
	private class ServerTask extends TimerTask {
		
        public void run() {
        	try{
        		registerWithServer(InetAddress.getByName(serverDomain));
        		addUpdateClient(InetAddress.getByName(serverDomain), 10000);
    		}
    		catch (Exception ex){
    			ex.printStackTrace();
    		}
        }
    }
	
	private class BroadcastTask extends TimerTask {
		
        public void run() {
        	try{
        		queryAllResposne(broadcastAddress);
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
		
		if (modeAllOne){
			tmp			=	Utils.concat(tmp,endAllone);
		} else {
			tmp			=	Utils.concat(tmp,end);
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
		byte [] timeData =  Utils.longToBytes(Utils.getTimeSince1900());
		byte [] end2 	=   {timeData[7], timeData[6],  timeData[5],timeData[4], state};
		
		if (modeAllOne){
			tmp			=	Utils.concat(tmp,endAllone);
		} else {
			tmp			=	Utils.concat(tmp,end);
		}
		tmp				=	Utils.concat(tmp,end2);

		sendMessage(tmp,ipAddress);
	}

	/*
	public void subscribeAlternte(InetAddress ipAddress, byte[] macAddr){
		byte [] start = {0x63, 0x6c};
		byte [] tmp	=	Utils.concat(start,macAddr);
		tmp		=	Utils.concat(tmp,twenties);
		tmp		=	Utils.concat(tmp,(password + PAD16).substring(0, 12).getBytes());
		tmp		=	Utils.concat(tmp,(unknowMsg + PAD40).substring(0, 40).getBytes());
		
		sendMessage(tmp,ipAddress);
	}
	*/
	
	private void sendNTPRequest() {
		//dst port 123
		//db00 04fa 00 00 00 00 00 01 03 fe 000000000000000000000000000000000000000000000000000000000000000000000000
		byte [] start = {(byte)0xdb, 0x00,0x04,(byte)0xfa, 0x00 ,0x00 ,0x00 ,0x00 ,0x00,0x01 ,0x03 ,(byte)0xfe ,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
		sendMessage(start,NTP_SERVER,123);
	}
	
	//broadcast message every 3 secs?
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
		
		byte [] start 	=   {0x72, 0x74};
		byte [] tmp		=	Utils.concat(start,	mac);
		tmp				=	Utils.concat(tmp,	twenties);
		
		if (table==1){
			//TABLE 1
			byte [] end1 =   {02,00,0,0,0,1,0,(byte)(state+1),0,0,6,0,4,0,4,0,2,0};
			tmp		=	Utils.concat(tmp,end1);
			
		} else if (table==3){
			//TABLE 3
			tmp		=	Utils.concat(tmp,new byte []{2,0}); 	// Record ID Little Endian = 02
			tmp		=	Utils.concat(tmp,new byte []{0,0,0}); 	// Unknown
			tmp		=	Utils.concat(tmp,new byte []{0x03});	// Table Number
			tmp		=	Utils.concat(tmp,new byte []{0,(byte)(state+1),0,0}); // ??? Unknown ???
			tmp		=	Utils.concat(tmp,new byte []{0x1c,0});  // Record Length Little Endian = 28bytes
			tmp		=	Utils.concat(tmp,new byte []{1,0}); 	// Record Number Little Endian = 1
			tmp		=	Utils.concat(tmp,new byte []{(byte)0xE2 ,0x72 ,(byte)0x80, 0 ,0x63 ,0x0E, 0, 0, 0, 0x5C ,(byte)0xDE ,0x16, 00, (byte)0xA0, 0x19, 00}); //??? Unknown ???
			tmp		=	Utils.concat(tmp,new byte []{state,0}); // Power state = on (00 = off, 01 = on)
			tmp		=	Utils.concat(tmp,new byte []{(byte)0xDE ,7,7,0x0d,0x10,0,0,(byte)0xff,0x1C ,0,2,0,(byte)0xE2,0x72,(byte) 0x80,0,0x71,0x0F,0, 0, 0x50, 0x72, (byte) 0xD2, 0x16, 00 ,(byte) 0xA0, 0x19, 0,0,0,(byte)0xde,07,7,0x0d,0x13,0,0,(byte)0xff});
			
		} else {
			
			//TABLE 4
			try {
				byte[] end2 =  {1,0,0,0,0,4,0,1,0,0,(byte)0x8a,0,1,0,0x43,0x25};
				tmp			=	Utils.concat(tmp,end2);
				tmp			=	Utils.concat(tmp,mac);
				tmp			=	Utils.concat(tmp,twenties);
				tmp			=	Utils.concat(tmp,macRev);
				tmp			=	Utils.concat(tmp,twenties);
				tmp			=	Utils.concat(tmp,(password + PAD16).substring(0, 12).getBytes());
				tmp			=	Utils.concat(tmp,(deviceName + PAD16).substring(0, 16).getBytes());
	
				byte [] end4 =  {0x04,0x00};//byte [] end4 =  {0x05,0x02}; - is this really the Icon?
				byte [] end5 =  {0x20,0x00,0x00,0x00,0x10,0x00,0x00,0x00,0x05,0x00,0x00,0x00};
				byte [] end10 =  {(byte)0xff,(byte)0xff,(byte)0xff,0x00}; //netmask
				byte [] end11 =  {1,discoverable,(byte)dst,(byte)timezone,0,(byte)0xff,0,0,0,0,0,0}; //4 bytes missing?
				tmp		=	Utils.concat(tmp,end4);
				tmp		=	Utils.concat(tmp,end5);
				tmp		=	Utils.concat(tmp,PORT10K);
				tmp		=	Utils.concat(tmp,InetAddress.getByName(serverDomain).getAddress());
				tmp		=	Utils.concat(tmp,PORT10K);
				tmp		=	Utils.concat(tmp,(serverDomain + PAD40).substring(0, 40).getBytes());
				tmp		=	Utils.concat(tmp,InetAddress.getByName(getNetworkAddress(networkInterface)).getAddress());
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
			//TODO: implement Table 1 if necessary
		} else if (table==3) {
			//TODO: implement Table 3 if necessary
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
	
	public void resetGPIO() {
		
		gpio.shutdown();
		GpioPin gppin=null;
		
		for (GpioPin apin: gpio.getProvisionedPins()){
			gppin = apin;
			if (gppin!=null){
				gpio.unprovisionPin(gppin);
			}
		}
	}
	
	public void switchRelay(boolean state,int pinIndex) {
		
		if (outputPins.isEmpty()) {
			System.out.println("Simulate switch pim :" +pinIndex + " , value : " + state);
			return;
		}

		if (state==true) {
			outputPins.get(pinIndex).low();
		} else {
			outputPins.get(pinIndex).high();
		} 
	}
	
	public void updateClients() {
		for(String key:subscribers.keySet()){
			SocketClient sc = subscribers.get(key);
			System.out.println("Sending state "+state+" to subscriber " + sc.getInetAddress().getHostAddress() + ":" + sc.getPort());	
			powerResponse(sc.getInetAddress(),(byte)0);
			powerResponseConfirm(sc.getInetAddress(), sc.getPort());
		}
	}
	
	public void changeState(InetAddress ipAddress,byte[] rxbytes) {

		//00000000CD01002A004EA3E9 // AllOne
	    //0000000001 //S20
		int sw 	= 0;
		
		if (rxbytes.length == 30) {
			sw 	= (rxbytes[25] - 0x2a);
			state = rxbytes[24];
			System.out.println("Switch : " + sw + " \t State " + state);
		} else {
			state = rxbytes[22];
		}

		updateClients();
		switchRelay((state==1), sw);

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
				   
					if (!iPAddress.equals(localIP) && (receiveData[0]==0x68) && (receiveData[1]==0x64) && (receivePacket.getLength()>4)) {
					   
						byte[] rxbytes = new byte [receivePacket.getLength()];
						System.arraycopy(receiveData, 0, rxbytes, 0, receivePacket.getLength());
						OrviboCmd cmd = OrviboCmd.valueOf(rxbytes,4);
						System.out.println("RX ["+ cmd.toString() + "] FROM "+ iPAddress + ":" +receivePacket.getPort() + "\t"  + Utils.bytesToHex(rxbytes));
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
					   			addUpdateClient(iPAddress, receivePacket.getPort());
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
}
