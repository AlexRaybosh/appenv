package appenv.env;

public class AppConfOverride {
	private String presetConfName=null;
	private String presetBootstrapResource=null;
	
	public AppConfOverride withPresetEnv(String name) {
		AppConfOverride n=new AppConfOverride();
		n.presetBootstrapResource=presetBootstrapResource;
		n.presetConfName=name;
		return n;
	}
	public AppConfOverride withPresetBootstrapResource(String bs) {
		AppConfOverride n=new AppConfOverride();
		n.presetBootstrapResource=bs;
		n.presetConfName=presetConfName;
		return n;
	}
	
	public void presetConfName(String name) {
		presetConfName=name;
	}
	public void presetBootstrapResource(String boot) {
		presetBootstrapResource=boot;
	}	
	public String getPresetConfName() {
		return presetConfName;
	}
	public String getPresetBootstrapResource() {
		return presetBootstrapResource;
	}
	
}
