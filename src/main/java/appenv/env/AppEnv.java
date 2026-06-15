package appenv.env;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Future;

import com.google.gson.JsonObject;

import appenv.db.DB;
import appenv.etc.DictionaryWord;
import appenv.susbsystems.ProcessMaintenance;
import appenv.task.TaskQueueBackend;
import appenv.task.TaskQueueClient;
import appenv.util.EncodingUtils;
import appenv.util.Utils;

public class AppEnv {
	private static AppConfOverride defaultAppConfOverride=null;
	
	
	public static void presetAppConfName(String confName) {
		if (defaultAppConfOverride==null) defaultAppConfOverride=new AppConfOverride();
		defaultAppConfOverride.presetConfName(confName);
	}
	public static void presetBootstrapResource(String boot) {
		if (defaultAppConfOverride==null) defaultAppConfOverride=new AppConfOverride();
		defaultAppConfOverride.presetBootstrapResource(boot);
	}	

	
	private static class Holder {
		final static AppScope appScope=AppScope.createNonDefaultAppScope(defaultAppConfOverride);
	}
	public static AppScope getAppScope() {
		try {
			return Holder.appScope;
		} catch (Throwable e) {
			return Utils.<AppScope>rethrowRuntimeException(e);
		}		
	}
	
	
	
	/*
	 * should never be called from inside SubSystem::init ever! Will wait for itself....
	 */
	public static void ready() {getAppScope().ready();}
	public static void destroy() {getAppScope().destroy();}
	public static void stop() {getAppScope().stop();}
	public static boolean isStopped() {return getAppScope().isStopped();}
	
	

	public static boolean hasDB() {return getAppScope().hasDB();}
	public static DB db() {return getAppScope().getDB();}
	
	
	public static DB db(String dbName) {return getAppScope().getDB(dbName);}

	
	public static Long systemProcessId() {return getAppScope().getSystemProcessId();}
	public static boolean subSystemRegistered(String name) {return getAppScope().hasSubSystem(name);}
	public static <S> S subSystem(String name) {return getAppScope().getSubSystem(name);}
	public static Set<String> subSystemNames() {return getAppScope().getSubSystemNames();}
	
	public static AppConf appConf() {return getAppScope().getAppConf();}
	public static Integer confId() {return appConf().getId();}
	public static String confName() {return getAppScope().getAppConf().getName();}

	public static Integer envTypeId() {return appConf().getEnvTypeId();}
	public static String envTypeName() {return getAppScope().getAppConf().getEnvType();}
	
	
	public static Properties bootstrapProperties() {return getAppScope().getBootstrapProperties();}
	
	public static Integer clusterMemberId() {return getAppScope().getClusterMemberId();}
	public static Long newId(String name) throws SQLException, InterruptedException {return getAppScope().newId(name);}
	public static JsonObject conf() {return getAppScope().getConfiguration();}
	
	public static DictionaryWord word(Number id) {return getAppScope().getDictionaryWord(id);}
	public static DictionaryWord word(String word) {return getAppScope().getDictionaryWord(word);}
	public static Future<Boolean> hasWord(String word) {return getAppScope().getHasWord(word);}
	public static Future<Boolean> hasWord(Number id) {return getAppScope().getHasWord(id);}
	public static boolean wordCached(String word) {return getAppScope().getWordCached(word);}
	public static boolean wordCached(Number id) {return getAppScope().getWordCached(id);}
	
	public static DictionaryWord word(String base,Number id) {return getAppScope().getDictionaryWord(base,id);}
	public static DictionaryWord word(String base, String word) {return getAppScope().getDictionaryWord(base,word);}
	public static Future<Boolean> hasWord(String base, String word) {return getAppScope().getHasWord(base, word);}
	public static Future<Boolean> hasWord(String base, Number id) {return getAppScope().getHasWord(base, id);}
	public static boolean wordCached(String base, String word) {return getAppScope().getWordCached(base, word);}
	public static boolean wordCached(String base, Number id) {return getAppScope().getWordCached(base, id);}
	
	public static long getTime() {return getAppScope().getTime();}
	

	public static byte[] encryptAES(byte[] value, int off, int len) {return getAppScope().encryptAES(value, off, len);}
	public static byte[] decryptAES(byte[] value, int off, int len) {return getAppScope().decryptAES(value, off, len);}
	public static byte[] encryptAES(byte[] value) {return getAppScope().encryptAES(value);}
	public static byte[] decryptAES(byte[] value) {return getAppScope().decryptAES(value);}
	
	public static byte[] encryptPrivateRSA(byte[] value, int off, int len) {return getAppScope().encryptPrivateRSA(value, off, len);}	
	public static byte[] decryptPublicRSA(byte[] value, int off, int len) {return getAppScope().decryptPublicRSA(value, off, len);}
	public static byte[] signSHA256PrivateRSA(byte[] value, int off, int len) {return getAppScope().signSHA256PrivateRSA(value, off, len);}
	public static byte[] encryptPrivateRSA(byte[] value) {return getAppScope().encryptPrivateRSA(value);}	
	public static byte[] decryptPublicRSA(byte[] value) {return getAppScope().decryptPublicRSA(value);}
	public static byte[] signSHA256PrivateRSA(byte[] value) {return getAppScope().signSHA256PrivateRSA(value);}
	
	
	
	
	public static void reloadConfiguration() {getAppScope().reloadConfiguration();}	
	public static void reloadSubSystems() {getAppScope().reloadSubSystems();}
	


	public static String timeUniquePrefix(){
		String prefix= Long.toString( System.currentTimeMillis() >>> 14, 36);
		if (prefix.length()<6) {
			// pad with 0
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 6; i++) {
			    sb.append('0');
			}
			prefix=sb.substring(prefix.length()) + prefix;
		}
		return prefix.substring(0, 6);
	}
    private static class SecureRandomHolder {
        static final SecureRandom numberGenerator = new SecureRandom();
        static final String hostnamePrefix=initHostnamePrefix();
        static String initHostnamePrefix() {
        	String ch=Utils.getCanonicalHostName();
        	MessageDigest md5;
        	try {
	            md5=MessageDigest.getInstance("MD5");
	        } catch (NoSuchAlgorithmException nsae) {
	            throw new InternalError("MD5 not supported", nsae);
	        }
        	byte[] m=md5.digest(ch.getBytes(StandardCharsets.US_ASCII));
        	String v1=Long.toString(EncodingUtils.byteArrayToLong(m), 36);
        	if (v1.charAt(0)=='-') v1=v1.substring(1);
        	if (v1.length()>2) v1=v1.substring(v1.length()-2);
        	return v1;
        }
    }
	public static String createUniqueKey() {
        SecureRandom ng = SecureRandomHolder.numberGenerator;
        byte[] randomBytes = new byte[8];
        StringBuilder v=new StringBuilder();
        v.append(timeUniquePrefix());
        v.append(SecureRandomHolder.hostnamePrefix);
        while (v.length()<32) {
            ng.nextBytes(randomBytes);
            String t=Long.toString(EncodingUtils.byteArrayToLong(randomBytes), 36);
            if (t.charAt(0)=='-') t=t.substring(1);
            v.append(t);
        }
        if (v.length()>32) return v.substring(0,32);
		return v.toString();
	}

	public static TaskQueueBackend taskQueueBackend() {return getAppScope().getTaskQueueBackend();}
	public static TaskQueueClient taskQueueClient() {return getAppScope().getTaskQueueClient();}
	
	static volatile BasicLogger logger=new BasicLogger();
	public static void setLogger(BasicLogger bl) {
		if (bl==null) throw new RuntimeException("Logger can't be null");
		logger=bl;
	}
	
	public static void logout(String msg) {
		logger.logout(msg);
	}
	public static void logerr(String msg, Throwable e) {
		logerr(msg, e, false);
	}
	public static void logerr(String msg, Throwable e, boolean escalate) {
		logerr(true, msg, e);
	}
	public static void logerr(boolean showFrames, String msg, Throwable e) {
		logerr(showFrames, msg, e, false);
	}
	public static void logerr(boolean showFrames, String msg, Throwable e, boolean escalate) {
		try {
			List<String> frames = Utils.getErrorFrames();
			if (frames.size()>0) frames.remove(0);
			logger.logerr(frames, msg, e);
		} catch (Throwable t) {
			Utils.rethrowRuntimeException("Failed to log oringal error with msg: "+msg+(e==null?"":"; and exception "+Utils.getStackTrace(e)), t);
		}
	}
	public static void logerr(String msg) {
		logerr(true, msg);
	}
	public static void logerr(String msg, boolean escalate) {
		logerr(true, msg, escalate);
	}
	public static void logerr(boolean showFrames, String msg) {
		logerr(showFrames, msg, false);
	}
	public static void logerr(boolean showFrames, String msg, boolean escalate) {
		try {
			if (showFrames) {
				List<String> frames = Utils.getErrorFrames();
				if (frames.size()>0) frames.remove(0);
				logger.logerr(frames, msg, null, escalate);
			} else {
				logger.logerr(Collections.emptyList(), msg, null, escalate);
			}
		} catch (Throwable t) {
			throw new RuntimeException("Failed to log oringal error with msg: "+msg);
		}
	}

	public static void setAppVersion(String version) throws SQLException, InterruptedException {getAppScope().setAppVersion(version);}
	public static String getAppVersion() {return getAppScope().getAppVersion();}

}
