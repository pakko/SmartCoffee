package com.sc.util;


public class Constants {
	
	public static final String separator = "/";
	public static String CurrentDir = Constants.class.getResource("/").getPath();

	public static final String DefaultConfigFile = CurrentDir + separator + "default.properties";

	public static final String LocationCollectionName = "location";
	public static final String OrderCollectionName = "order";
	public static final String MapsCollectionName = "maps";
	public static final String OrderLocationCollectionName = "orderlocation";
	
	public static final String LocationDeleteFLag = "0";
	public static final String EmptyCardFlag = "0";

	public enum OrderUpdateStatus {
        NOTUSED, ADD, DELETE, UPDATE, ORDER_WITH_SAME_CARD, ORDER_WITH_NO_CARD, CARD_WITH_MULTI_LOCATION, LOCATION_WITH_MULTI_CARD;
    }

}
