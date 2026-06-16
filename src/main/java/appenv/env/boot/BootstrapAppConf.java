package appenv.env.boot;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import appenv.db.DB;
import appenv.env.AppScope;
import appenv.env.AppConf;
import appenv.env.AppEnv;
import appenv.util.JsonUtils;
import appenv.util.Legacy;
import appenv.util.Utils;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class BootstrapAppConf {
	String appConfName;
	String envType;
	Properties properties;
	String shell;
	boolean abortOnError=false;
	boolean logErrors;
	private String dburl;
	private String dbuser;
	private String dbpassword;
	DB db;
	AppConf appConf;
	Future<AppSec> appSecFuture;
	boolean disableDB=false;
	private boolean bootstrapIsFile;
	private String lookupDir;
	
	public final boolean isDBDisabled() {
		return disableDB;
	}
	
	private void add(String name, Map<String, JsonObject> allConfigs, JsonObject conf) {
		if (conf==null) return;
		if (allConfigs.containsKey(name)) return;
		allConfigs.put(name, conf);
		for (JsonElement entry : JsonUtils.getJsonArrayIterable(conf, "include")) {
			String inc=JsonUtils.getString(entry);
			if (Utils.isEmpty(inc)) {
				logerr("Configuration "+name+" contains invalid include entry "+entry+", skipping it");
				continue;
			}
			if (allConfigs.containsKey(inc)) continue;
			boolean a1=addIncluded(name, allConfigs, entry, inc);
			boolean a2=addIncluded(name, allConfigs, entry, inc+"."+envType);
			if (!a1 && !a2) {
				throw new RuntimeException("Configuration "+name+" contains invalid include entry "+entry+", skipping it");				
			}
		}
/*		
		for (Entry<String, JsonObject> e : allConfigs.entrySet()) {
			System.out.println(e.getKey()+" - "+e.getValue());
		}
		System.out.println("-----------");
*/		
		conf.remove("include");
	}

	private boolean addIncluded(String name, Map<String, JsonObject> allConfigs, JsonElement entry, String inc) {
		try {
			String incName=lookupDir==null?inc:(lookupDir+"/"+inc);
			JsonObject incConfig=null;
			if (bootstrapIsFile) {
				incConfig=readBuildinJsonConfigFile(incName+".json");
				if (incConfig==null) incConfig=readBuildinJsonConfigFile(incName);//inc+".json");					
			} else {
				incConfig=readBuildinJsonConfig(incName+".json");//inc+".json");
				if (incConfig==null) incConfig=readBuildinJsonConfig(incName);
			}
			
			if (incConfig!=null) {
				add(inc, allConfigs, incConfig);
				return true;
			}
			else {
				return false;
			}
		} catch (Exception e) {
			String err="Configuration "+name+" contains invalid include entry "+entry+", failed to read it: "+e.getMessage();
			logerr(err);
			throw new RuntimeException(err);
		}
	}


	void init(AppScope appScope) throws Exception {
		JsonObject appenvLibConfig = readBuildinJsonConfig("appenv-lib-defaults.json");
		String bootstrap="bootstrap.json";
		JsonObject bootstrapConfig=null;
		if (appScope.getPresetBootstrapResource()!=null) {
			bootstrap=appScope.getPresetBootstrapResource();
			bootstrapConfig=readBuildinJsonConfig(bootstrap);
			if (bootstrapConfig==null) {
				//maybe its a resource directory
				if (!bootstrap.endsWith("/")) bootstrap+="/bootstrap.json";
				else bootstrap+="bootstrap.json";
				bootstrapConfig=readBuildinJsonConfig(bootstrap);
				if (bootstrapConfig==null) {
					// maybe it a file
					bootstrap=appScope.getPresetBootstrapResource();
					bootstrapConfig=readBuildinJsonConfigFile(bootstrap);
					if (bootstrapConfig==null) {
						if (!bootstrap.endsWith("/")) bootstrap+="/bootstrap.json";
						else bootstrap+="bootstrap.json";
						bootstrapConfig=readBuildinJsonConfigFile(bootstrap);
						if (bootstrapConfig==null) throw new RuntimeException("Failed to locate any reasonable bootstrap, last tried: "+bootstrap);
					}
					bootstrapIsFile=true;
				}
				
				
			}
		} else {
			bootstrapConfig=readBuildinJsonConfig(bootstrap);
			
		}
		int dirEnd=bootstrap.lastIndexOf('/');	
		if (dirEnd>0) {
			lookupDir=bootstrap.substring(0, dirEnd);
		}

		shell = JsonUtils.getString(bootstrapConfig, "bootstrap", "shell");
		if (Utils.isEmpty(shell)) shell="/bin/bash";

		String overrideConfNameFromEnvironmentVariable=JsonUtils.getString(bootstrapConfig, "bootstrap", "overrideConfNameFromEnvironmentVariable");
		String overrideEnvTypeFromEnvironmentVariable=JsonUtils.getString(bootstrapConfig, "bootstrap", "overrideEnvTypeFromEnvironmentVariable");
		if (!Utils.isEmpty(overrideEnvTypeFromEnvironmentVariable)) {
			String v=System.getenv(overrideEnvTypeFromEnvironmentVariable);
			if (!Utils.isEmpty(v)) envType=v;
		}
		
		String abortOnErrorIfShellEval=JsonUtils.getString(bootstrapConfig, "bootstrap", "abortOnErrorIfShellEval");
		logErrors=JsonUtils.getBool(bootstrapConfig, "bootstrap","logErrors");
		abortOnError=JsonUtils.getBool(bootstrapConfig, "bootstrap","abortOnError");

		if (!abortOnError && !Utils.isEmpty(abortOnErrorIfShellEval)) {
			abortOnError=checkShellEval(shell,abortOnErrorIfShellEval);
		}	
		
		
		BootEntry entry=null;
		for (JsonElement bobj : JsonUtils.getJsonArrayIterable(bootstrapConfig, "bootstrap", "entries")) {
			entry=BootEntry.create(bobj);
			if (entry==null) continue;
			try {
				boolean eval=entry.eval(BootstrapAppConf.this, JsonUtils.getJsonObject(bobj));
				if (eval) 
					break;
			} catch (Exception e) {
				if (logErrors) logerr(false, e.getMessage());
				if (abortOnError) throw e;
			}
		}
		if (entry!=null) {
			appConfName=entry.getAppConfName();
			properties=entry.getProperties();
		}
		if (Utils.isEmpty(envType)) {
			envType=properties==null?null:properties.getProperty("envType");
		}
		if (Utils.isEmpty(envType)) envType="localdev";
		
		if (!Utils.isEmpty(appScope.getPresetConfName())) {
			appConfName=appScope.getPresetConfName();
		} else {
			if (!Utils.isEmpty(overrideConfNameFromEnvironmentVariable)) {
				String v=System.getenv(overrideConfNameFromEnvironmentVariable);
				if (!Utils.isEmpty(v)) appConfName=v;
			}
			if (appConfName==null) appConfName="undefined";		
		}

		if (bootstrapConfig==null) {
			throw new RuntimeException("./boostrap.json resource/file on classpath or cwd is missing or unreasonable empty");
		}
		if (properties==null) properties=new Properties();

		Map<String,JsonObject> allConfigs=new LinkedHashMap<>();
		//allConfigs.put("bootstrap", bootstrapConfig);
		add("bootstrap", allConfigs, bootstrapConfig);
/*		for (Entry<String, JsonObject> e : allConfigs.entrySet()) {
			System.out.println(e.getKey()+JsonUtils.prettyPrint(e.getValue())+"\n\n");
		}
*/		
		JsonObject[] arr=allConfigs.values().<JsonObject>toArray(new JsonObject[allConfigs.size()]);
		bootstrapConfig=JsonUtils.combine(arr);
		
		JsonObject myConf = JsonUtils.getJsonObject(bootstrapConfig, "appConf", appConfName);
		Set<String> extAppNames=new HashSet<>();
		if (appConfName!=null) extAppNames.add(appConfName);
		myConf=handleExtended(extAppNames,bootstrapConfig,myConf);
		JsonObject defConf= JsonUtils.getJsonObject(bootstrapConfig, "defaults");
		JsonObject realConf=JsonUtils.combine(appenvLibConfig, defConf, myConf);
		
		dburl=(String)properties.get("dburl");
		dbuser=(String)properties.get("dbuser");
		dbpassword=(String)properties.get("dbpassword");
		
		
		String disableDatabaseEnvironmentVariable=JsonUtils.getString(bootstrapConfig, "bootstrap", "disableDatabaseEnvironmentVariable");
		if (!Utils.isEmpty(disableDatabaseEnvironmentVariable)) {
			String v=System.getenv(disableDatabaseEnvironmentVariable);
			
			if (!Utils.isEmpty(v) && !"0".equals(v) && !v.startsWith("n") && !v.startsWith("N")) {
				dburl=null;
				disableDB=true;
			}
		}
		
		if (!Utils.isEmpty(dburl) && JsonUtils.getJsonObject(realConf, "database", "core")!=null ) {
			db=DB.create(dburl, dbuser, dbpassword);
		}
		appConf=initAppConf(db,realConf);//AppConf.init(db, appConfName , envConf); 
		if (db!=null && JsonUtils.getJsonObject(appConf.getConfiguration(), "database", "core")==null) {
			db.close();
			db=null;
		}
		if (db!=null) {
			db=reinit(db,appConf.getConfiguration(), "core", dburl,  dbuser, dbpassword);
		}

		appSecFuture=AppScope.getExecutorService().submit(new Callable<AppSec>() {
			public AppSec call() throws Exception {
				return  new AppSec(appScope, properties, appConf);
			}
		});
		
	}


	private JsonObject handleExtended(Set<String> extAppNames, JsonObject bootstrapConfig, JsonObject myConf) {
		if (myConf==null) return null;
		JsonObject conf=myConf;
		for (JsonElement e : JsonUtils.getJsonArrayIterable(myConf, "extends")) {
			String name=JsonUtils.getString(e);
			if (name==null) continue;
			if (extAppNames.contains(name)) continue;
			extAppNames.add(name);
			JsonObject child = JsonUtils.getJsonObject(bootstrapConfig, "appConf", name);
			if (child==null) continue;
			child=handleExtended(extAppNames, bootstrapConfig, child);
			conf=JsonUtils.combine(child,conf);
		}
		return conf;
	}

	private AppConf initAppConf(DB db, JsonObject conf) throws SQLException, InterruptedException {
		Integer id=null, envTypeId=null;
		if (db!=null) {
			Boolean enableDBAppConf = JsonUtils.getBoolean(conf, "enableDatabaseAppConf");
			if (enableDBAppConf==null) {
				logerr("enableDatabaseAppConf json application property is not defined, assume it is true, and pick the env configuration up from the database");
				enableDBAppConf=true;
			}
			if (enableDBAppConf) {
				envTypeId=Utils.toInteger(db.selectSingle("select id from env_type where name=?",false,envType));
				id=Utils.toInteger(db.selectSingle("select id from app_conf a where a.name=? and a.env_type_id=?",false,appConfName,envTypeId));
				if (id!=null) for (Object str : db.selectFirstColumn("select meta from app_conf_entry where app_conf_id=? order by position",false,id)) {
					String cmetaStr=Utils.toString(str);
					if (!Utils.isEmpty(cmetaStr)) {
						JsonObject cmeta=JsonUtils.parseJsonObject(cmetaStr);
						if (cmeta!=null) {
							conf=JsonUtils.combine(conf,cmeta);
						}
					}
				}
			}
		}
		return new AppConf(id,appConfName,envTypeId, envType, conf);
	}



	public static DB reinit(DB db, JsonObject envConf, String name, String dburl, String dbuser, String dbpassword) {
		Integer socketTimeout=JsonUtils.getInteger(null,envConf, "database", name, "urlParams", "socketTimeout");
		if (socketTimeout!=null) {
			String newurl=dburl;
			Matcher m=Pattern.compile("(.*\\W)socketTimeout=(\\d+)(.*)").matcher(dburl);
			if (m.matches()) {
				String prefix=m.group(1);
				int urlTimeout=Integer.parseInt(m.group(2));
				String postfix=m.group(3);
				if (urlTimeout!=socketTimeout.intValue()) {
					newurl=prefix+"socketTimeout="+socketTimeout+postfix;
				}
			} else {
				newurl=dburl+"&socketTimeout="+socketTimeout;
			}
			if (!newurl.equals(dburl)) {
				db.close();
				dburl=newurl;
				db=DB.create(dburl, dbuser, dbpassword);
			}
		}
		return db;
	}

	private JsonObject readBuildinJsonConfig(String name) {
		InputStream is=null;
		try {
			is=AppScope.class.getClassLoader().getResourceAsStream(name);
			if (is==null) is=AppScope.class.getClassLoader().getResourceAsStream("/"+name);
			if (is==null) return null;
			JsonObject conf = JsonUtils.parseJsonObject(is);
			return conf;
		} catch (Exception e) {
			return Utils.rethrowRuntimeException(e);
		} finally {
			Utils.close(is);
		}
	}
	private JsonObject readBuildinJsonConfigFile(String name) {
		try {
			byte[] b=Files.readAllBytes(new File(name).toPath());
			JsonObject conf = JsonUtils.parseJsonObject(new String(b,StandardCharsets.UTF_8));
			return conf;
		} catch (Exception e) {
			return null;
		} 
	}


	public boolean checkShellEval(String shell, String expr) {
		if (Utils.isEmpty(expr)) return false;
		List<String> lst=new ArrayList<>();
		lst.add(shell==null?"/bin/bash":shell);
		lst.add("-c");
		lst.add(expr);
		ProcessBuilder pb=new ProcessBuilder(lst.toArray(new String[lst.size()]));
		pb.redirectInput(new File("/dev/null"));
		pb.redirectOutput(Redirect.PIPE);
		pb.redirectError(Redirect.PIPE);
		
		
		int exit=-1;
		try {
			Process process=pb.start();
			try (
				OutputStream out = process.getOutputStream();
				InputStream in=process.getInputStream();
				InputStream err=process.getErrorStream();
			) {
				out.close();
				Legacy.jdk9_readAllBytes(in);
				String errMsg=Utils.trim(new String(Legacy.jdk9_readAllBytes(err), StandardCharsets.UTF_8));
				if (logErrors && !Utils.isEmpty(errMsg)) logerr(false, errMsg);
			} finally {
				exit=process.waitFor();
			}
		} catch (Exception e) {
			return false;
		}
		return exit==0;
	}
	public static void logerr(String msg) {
		logerr(true, msg);
	}
	public static void logerr(boolean showFrames, String msg) {
		if (Utils.isEmpty(msg)) return;
		AppEnv.logerr(showFrames, "BOOTSTRAP: "+msg);
	}
	public final Properties getProperties() {
		return properties;
	}

	public static BootstrapAppConf bootstrap(AppScope appScope) throws Exception {
		BootstrapAppConf bootstrapAppConf=new BootstrapAppConf();
		bootstrapAppConf.init(appScope);
		return bootstrapAppConf;
	}
	
	public final DB getDB() {
		return db;
	}

	public final AppConf getAppConf() {
		return appConf;
	}


	public static void logerr(String msg, Exception e) {
		if (Utils.isEmpty(msg) && e==null) return;
		AppEnv.logerr("BOOTSTRAP:"+ (msg==null?"":msg) , e);
	}


	public final Future<AppSec> getAppSecFuture() {
		return appSecFuture;
	}
}
