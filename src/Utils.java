import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Date;


public class Utils {
	
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	private static final long timeRef = 2208988008l +794l;
	
	 static String bytesToHex(byte[] bytes) {
		
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
	
	 static String getHexString(byte[] b) throws Exception {
		
		  String result = "";
		  for (int i=0; i < b.length; i++) {
		    result +=
		          Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
		  }
		  return result;
	}

	
	 static byte[] concat(byte[] a, byte[] b) {
		
		   int aLen = a.length;
		   int bLen = b.length;
		   byte[] c= new byte[aLen+bLen];
		   System.arraycopy(a, 0, c, 0, aLen);
		   System.arraycopy(b, 0, c, aLen, bLen);
		   return c;
		   
	}
	
	 static InetAddress getIPv4LocalNetMask(InetAddress ip) throws SocketException {
		
		int netPrefix = NetworkInterface.getByInetAddress(ip).getInterfaceAddresses().get(0).getNetworkPrefixLength();

	    try {
	        // Since this is for IPv4, it's 32 bits, so set the sign value of
	        // the int to "negative"...
	        int shiftby = (1<<31);
	        // For the number of bits of the prefix -1 (we already set the sign bit)
	        for (int i=netPrefix-1; i>0; i--) {
	            // Shift the sign right... Java makes the sign bit sticky on a shift...
	            // So no need to "set it back up"...
	            shiftby = (shiftby >> 1);
	        }
	        // Transform the resulting value in xxx.xxx.xxx.xxx format, like if
	        /// it was a standard address...
	        String maskString = Integer.toString((shiftby >> 24) & 255) + "." + Integer.toString((shiftby >> 16) & 255) + "." + Integer.toString((shiftby >> 8) & 255) + "." + Integer.toString(shiftby & 255);
	        // Return the address thus created...
	        return InetAddress.getByName(maskString);
	    }
	        catch(Exception e){e.printStackTrace();
	    }
	    // Something went wrong here...
	    return null;
	}
	
	static byte[] longToBytes(long l) {
	    byte[] result = new byte[8];
	    for (int i = 7; i >= 0; i--) {
	        result[i] = (byte)(l & 0xFF);
	        l >>= 8;
	    }
	    return result;
	}

   static long bytesToLong(byte[] b) {
		
	    long result = 0;
	    for (int i = 0; i < 8; i++) {
	        result <<= 8;
	        result |= (b[i] & 0xFF);
	    }
	    return result;
	}
	
	static long getTimeSince1900() {
		
		return (System.currentTimeMillis() /1000l) + timeRef ;
		
	}
	
    static Date getDatefromBytes(byte [] data){
		
		long time = ((long)data[4] * (256*256*256)) + ((long)data[3] * (256*256)) + ((long)data[2] * (256) ) + ((long)data[1]);
		long jTime = time - timeRef;
		return new Date(jTime * 1000l);
		
	}
	
	 static Date getDatefromSecsSince1900(long secs){
		
		long jTime = secs - timeRef;
		return new Date(jTime * 1000l);
		
	}
}
