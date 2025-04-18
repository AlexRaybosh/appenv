package appenv.task;

import java.security.SecureRandom;
import java.text.ParseException;

import appenv.env.AppEnv;

import appenv.util.EncodingUtils;
import appenv.util.Utils;

public class T {

	static void test(String dt) throws ParseException {
		long ms=Utils.parseLocalDateTime(dt).getTime();
		String prefix= Long.toString( ms >>> 14, 36);
		if (prefix.length()<6) {
			// pad with 0
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 6; i++) {
			    sb.append('0');
			}
			prefix=sb.substring(prefix.length()) + prefix;
		}
		prefix=prefix.substring(0, 6);
		
		System.out.println(dt+" - "+ms+" - '"+prefix+"'");
	}
	
	public static void main(String[] args) throws Exception {
		//test("1970-01-01");
		//test("2000-01-01");
		for (int i=0;i<100;++i) {
			System.out.println(AppEnv.createUniqueKey());
	        /*SecureRandom ng = SecureRandomHolder.numberGenerator;
	        byte[] randomBytes = new byte[8];
	        ng.nextBytes(randomBytes);
	        long l=EncodingUtils.byteArrayToLong(randomBytes);
	        l=Long.MIN_VALUE/2;
			String prefix= Long.toString( l, 36);
			System.out.println(prefix);
			*/
		}
		/*
		for (int y=1970;y<1971;++y) {
			for (int m=1;m<=12;++m) {
				String mm=String.format("%02d",m);
				for (int d=1; d<=28;++d) {
					String dd=String.format("%02d",d);
					for (int h=0; h<24;++h) {
						String hh=String.format("%02d",h);
						for (int n=0; n<=59;++n) {
							String nn=String.format("%02d",n);
							for (int s=0;s<60;++s) {
								String ss=String.format("%02d",s);
								//test(""+y+"-"+mm+"-"+dd+" "+hh+":"+nn+":"+ss);
							}
							test(""+y+"-"+mm+"-"+dd+" "+hh+":"+nn+":00");
						}
						
					}
					
				}
				
			}
			
		}*/

	}
    private static class SecureRandomHolder {
        static final SecureRandom numberGenerator = new SecureRandom();
    }
}
