package appenv.env;

import com.google.gson.JsonObject;

public class AppConf {
	@Override
	public String toString() {
		return "AppConf [id=" + id + ", name=" + name + ", envTypeId=" + envTypeId + ", envTypeName=" + envTypeName
				+ ", meta=" + meta + "]";
	}

	final Integer id;
	final String name;
	final JsonObject meta;
	final Integer envTypeId;
	final String envTypeName;
	
	public AppConf(Integer id, String name,Integer envTypeId, String envTypeName, JsonObject fallbackMeta) {
		this.id=id;
		this.envTypeId=envTypeId;
		this.envTypeName=envTypeName;
		this.name=name;
		this.meta=fallbackMeta;
	}
	public final Integer getId() {
		return id;
	}
	public final String getName() {
		return name;
	}

	public final JsonObject getConfiguration() {
		return meta;
	}
	public final Integer getEnvTypeId() {
		return envTypeId;
	}
	public final String getEnvType() {
		return envTypeName;
	}


}
