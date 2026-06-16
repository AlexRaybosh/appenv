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
			return Utils.rethrowRuntimeException(e);
		} finally {
			Utils.close(is);
		}
	}
	
	@Override
	public boolean eval(BootstrapAppConf bootstrapAppConf, JsonObject conf) {
		String fileName=JsonUtils.getString(conf, "file");
		if (fileName==null) return false;
		if (BootEntry.isWindows) {
			BootstrapAppConf.logerr("skipping "+conf+", windows detected");
			return false;
		}
		boolean abortOnExecutionError=JsonUtils.getBool(conf, "abortOnExecutionError");
		String skipOnShellEvalError=JsonUtils.getString(conf, "skipOnShellEvalError");
		if (!Utils.isEmpty(skipOnShellEvalError)) {
			boolean su=bootstrapAppConf.checkShellEval(bootstrapAppConf.shell, skipOnShellEvalError);
			if (!su) return false;
		}
		

		String resProvider=readPropsProvider(fileName);
		if (resProvider==null) throw new RuntimeException("Resource: "+fileName+" not found");
				
		ProcessBuilder pb=new ProcessBuilder(new String[] {bootstrapAppConf.shell, "-c", resProvider});
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
