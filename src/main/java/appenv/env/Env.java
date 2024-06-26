package appenv.env;

import com.google.gson.JsonObject;

public class Env {
	final Integer id;
	final String name;
	final JsonObject meta;
	public Env(Integer id, String name, JsonObject fallbackMeta) {
		this.id=id;
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

}
