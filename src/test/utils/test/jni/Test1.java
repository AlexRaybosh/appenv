package test.jni;

import appenv.jni.API;
import appenv.util.Utils;

public class Test1 {

	public static void main(String[] args) throws Exception {
		API.testNative();
		System.out.println(Utils.getCodeMarker());
	}

}
