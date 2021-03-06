package com.meidusa.venus;

import java.io.IOException;
import java.util.Properties;

public class VenusMetaInfo {
	private final static Properties buildInfo = new Properties();

	/**
	 * venus 版本号
	 */
	public static final String VENUS_VERSION = buildInfo.getProperty("version");

	static{
		try {
			buildInfo.load(VenusMetaInfo.class.getResourceAsStream("/build/build.properties"));
		} catch (IOException e) {
		}
	}

	public static void main(String[] args) {
		System.out.println(VenusMetaInfo.VENUS_VERSION);
	}
}
