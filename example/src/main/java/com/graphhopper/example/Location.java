package com.graphhopper.example;

public class Location {
	
	private String id;
	private double lat;
	private double lon;
	
	public Location(String id, double lat, double lon) {
		super();
		this.id = id;
		this.lat = lat;
		this.lon = lon;
	}



	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}



	public double getLat() {
		return lat;
	}



	public void setLat(double lat) {
		this.lat = lat;
	}



	public double getLon() {
		return lon;
	}



	public void setLon(double lon) {
		this.lon = lon;
	}



	@Override
	public String toString() {
		return "Location [id=" + id + ", lat=" + lat + ", lon=" + lon + "]";
	}
	
	

}
