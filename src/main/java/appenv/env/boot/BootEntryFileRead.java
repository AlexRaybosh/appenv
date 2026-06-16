package appenv.env.boot;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import com.google.gson.JsonObject;

import appenv.util.JsonUtils;
import appenv.util.Utils;

public class BootEntryFileRead extends BootEntry {
	private Properties properties;
	@Override
	public boolean eval(BootstrapAppConf bootstrapAppConf, JsonObject conf) {
		String fileName=JsonUtils.getString(conf, "file");
		if (fileName==null) return false;
		Path path=Paths.get(fileName);
		boolean abortOnFileMissing=JsonUtils.getBool(conf, "abortOnFileMissing");
		boolean abortOnLoadError=JsonUtils.getBool(conf, "abortOnLoadError");
		

		if (!Files.exists(path)) {
			if (abortOnFileMissing) throw new RuntimeException(fileName+" in "+conf+" is missing");
			else if (bootstrapAppConf.logErrors) BootstrapAppConf.logerr(false, fileName+" in "+conf+" is missing");
			return false;
		}
		
		try {
			properties=new Properties();
			properties.load(new StringReader(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)));
		} catch (Exception e) {
			if (abortOnLoadError) Utils.rethrowRuntimeException(fileName+" in "+conf+" failed to read", e);
			else if (bootstrapAppConf.logErrors) BootstrapAppConf.logerr(fileName+" in "+conf+" failed to read: "+e.getMessage());
			return false;
		}
		return true;
	}



	@Override
	public String getAppConfName() {
		return properties.getProperty("appConf");
	}
	@Override
	public Properties getProperties() {
		return properties;
	}
}
