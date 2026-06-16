package appenv.env.boot;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.google.gson.JsonObject;

import appenv.util.JsonUtils;
import appenv.util.Legacy;
import appenv.util.Utils;

public class BootEntryShellEval extends BootEntry {
	private Properties properties;
	@Override
	public boolean eval(BootstrapAppConf bootstrapAppConf, JsonObject conf) {
		String shellArgument=JsonUtils.getString(conf, "shellArgument");
		if (Utils.isEmpty(shellArgument)) return false;
		if (BootEntry.isWindows) {
			BootstrapAppConf.logerr("skipping " +conf+ ", windows detected");
			return false;
		}
		boolean abortOnExecutionError=JsonUtils.getBool(conf, "abortOnExecutionError");

		ProcessBuilder pb=new ProcessBuilder(new String[] {bootstrapAppConf.shell, "-c", shellArgument} );
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
				byte[] bytes= Legacy.jdk9_readAllBytes(in);
				errMsg=Utils.trim(new String(Legacy.jdk9_readAllBytes(err), StandardCharsets.UTF_8));
				if (!Utils.isEmpty(errMsg)) errMsg=": "+errMsg;
				properties=new Properties();
				properties.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
			} finally {
				exit=process.waitFor();
			}
		} catch (Exception e) {
			if (abortOnExecutionError) return Utils.rethrowRuntimeException(shellArgument+" in "+conf+" failed to execute: "+e.getMessage(),e);
			else if (bootstrapAppConf.logErrors) BootstrapAppConf.logerr(shellArgument+" in "+conf+" failed to execute: "+e.getMessage());
			return false;
		}
		if (exit!=0) {
			if (abortOnExecutionError) throw new RuntimeException(shellArgument+" in "+conf+" failed to execute"+errMsg);
			else if (bootstrapAppConf.logErrors) BootstrapAppConf.logerr(shellArgument+" in "+conf+" failed to execute"+errMsg);
			return false;
		}

		if (properties!=null && properties.size()==0 && bootstrapAppConf.logErrors) BootstrapAppConf.logerr(false, "Empty result from "+conf);
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
