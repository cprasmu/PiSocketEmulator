import java.util.HashMap;
import java.util.Map;


/**
 * 
 */

/**
 * @author peterrasmussen
 *
 */
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