package appenv.env.boot;

import java.util.Map;
import java.util.Properties;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import appenv.env.AppEnv;
import appenv.util.JsonUtils;

public class BootEntryBuildin extends BootEntry {
	private Properties properties;
	@Override
	public boolean eval(BootstrapAppConf bootstrapAppConf, JsonObject conf) {
		JsonObject obj=JsonUtils.getJsonObject(conf, "properties");
		if (obj==null) return false;
		properties=new Properties();
		for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
			String name=e.getKey();
			String val=JsonUtils.getString(e.getValue());
			if (val==null) {
				String envName = JsonUtils.getString(e.getValue(), "env");
				if (envName!=null) {
					val=System.getenv(envName);
					if (val==null) BootstrapAppConf.logerr("Undefined environment variable "+envName+" in : "+conf);
				}
			}
			properties.put(name, val);
		}
		return true;
	}

	@Override
	public String getAppConfName() {
		return properties==null?null:properties.getProperty("appConf");
	}
	@Override
	public Properties getProperties() {
		return properties;
	}
	public static void main(String[] args) {
		
	}
}
