package appenv.env;

import java.util.Date;
import java.util.List;

import appenv.util.Utils;

public class BasicLogger {
	
	public String formatedDate() {return Utils.formatLocalDateTime(new Date());}
	
	public void logerr(List<String> frames, String msg, Throwable e) {
		logerr(frames, msg, e, false);
	}
	public void logerr(List<String> frames, String msg, Throwable e, boolean escalate) {
		StringBuilder sb=new StringBuilder(formatedDate());
		sb.append(": ");
		if (msg!=null) sb.append(msg+"\n");
		if (frames!=null && frames.size()>0) {
			sb.append("Location:");
			appendFrames(sb,frames);
			//+frames+" ");
		}
		sb.append(e==null?"": ("Error: "+Utils.getStackTrace(e)));
		String out=sb.toString();
		if (out.endsWith("\n")) {
			System.err.print(out);
			System.err.flush();
		} else System.err.println(out);
	}

	protected void appendFrames(StringBuilder sb, List<String> frames) {
		for (String f : frames) {
			sb.append("\t").append(f).append("\n");
		}
	}

	public void logout(String msg) {
		System.out.println(msg);
		
	}
}
