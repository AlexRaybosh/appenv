package appenv.env.boot;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import appenv.env.AppScope;
import appenv.util.JsonUtils;
import appenv.util.Legacy;
import appenv.util.Utils;

public class BootEntryResourceExec extends BootEntry {
	private Properties properties;
	
	private static String readPropsProvider(String name) {
		InputStream is=null;
		try {
			is=AppScope.class.getClassLoader().getResourceAsStream(name);
			if (is==null) is=AppScope.class.getClassLoader().getResourceAsStream("/"+name);
			if (is==null) return null;
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			Utils.rethrowRuntimeException(e);
		} finally {
			Utils.close(is);
		}
	}
	@Override
	public boolean eval(BootstrapAppConf bootstrapAppConf, JsonObject conf) {
		String fileName=JsonUtils.getString(conf, "file");
		if (fileName==null) return false;
		Path path=Paths.get(fileName);
		boolean abortOnFileMissing=JsonUtils.getBool(conf, "abortOnFileMissing");
		boolean abortOnFileNotExecutable=JsonUtils.getBool(conf, "abortOnFileNotExecutable");
		boolean abortOnExecutionError=JsonUtils.getBool(conf, "abortOnExecutionError");
		if (BootEntry.isWindows) {
			BootstrapAppConf.logerr("skipping "+conf+", windows detected");
			return false;
		}

		if (!Files.exists(path)) {
			//
			if (abortOnFileMissing) throw new RuntimeException(fileName+" in bootstrap "+conf+" missing");
			else if (bootstrapAppConf.logErrors) BootstrapAppConf.logerr(false, fileName+" in bootstrap "+conf+" missing");
			return false;
		}
		if (!Files.isExecutable(path)) {
			//
			if (abortOnFileNotExecutable) throw new RuntimeException("Bootstrap "+fileName+" in "+conf+" is not executable");
			else if (bootstrapAppConf.logErrors) BootstrapAppConf.logerr(false, "Bootstrap "+fileName+" in "+conf+" is not executable");
			return false;
		}
		
		List<String> lst=new ArrayList<>(Arrays.asList(fileName));
		for (JsonElement argObj : JsonUtils.getJsonArrayIterable(conf, "args")) {
			String str=JsonUtils.getString(argObj);
			if (str!=null) lst.add(str);
		}
		
		ProcessBuilder pb=new ProcessBuilder(lst.toArray(new String[lst.size()]));
		pb.redirectInput(new File("/dev/null"));
		pb.redirectOutput(Redirect.PIPE);
		pb.redirectError(Redirect.PIPE);
		String errMsg="";
		int exit=-1;
		try {
			Process process=pb.start();
			try (
				OutputStream out = process.getOutputStream();
				InputStream in=process.getInputStream();
				InputStream err=process.getErrorStream();
			) {
				out.close();
				byte[] bytes = Legacy.jdk9_readAllBytes(in);
				errMsg=new String(Legacy.jdk9_readAllBytes(err), StandardCharsets.UTF_8);
				errMsg=Utils.trim(errMsg);
				if (!Utils.isEmpty(errMsg)) errMsg=": "+errMsg;
				properties=new Properties();
				properties.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
			} finally {
				exit=process.waitFor();
			}
		} catch (Exception e) {
			if (abortOnExecutionError) return Utils.rethrowRuntimeException(errMsg,e);
			else if (bootstrapAppConf.logErrors) BootstrapAppConf.logerr(fileName+" in " +conf+ " failed to execute: "+e.getMessage());
			return false;
		}
		if (exit!=0) {
			if (abortOnExecutionError) throw new RuntimeException(fileName+" in " +conf+ " failed to execute"+errMsg);
			else if (bootstrapAppConf.logErrors) BootstrapAppConf.logerr(fileName+" in " +conf+ " failed to execute"+errMsg);
			return false;
		}
		if (properties!=null && properties.size()==0 && bootstrapAppConf.logErrors) BootstrapAppConf.logerr("Empty result from "+conf);
		return properties!=null && properties.size()>0;
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
