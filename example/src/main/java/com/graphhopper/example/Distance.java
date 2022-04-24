package com.graphhopper.example;

import java.util.Arrays;
import java.util.Objects;

public class Distance {
	
	private double distance;
	private String srcId;
	private String dstId;
	
	public Distance(String srcId, String dstId, double distance) {
		super();
		this.srcId = srcId;
		this.dstId = dstId;
		this.distance = distance;
	}

	public double getDistance() {
		return distance;
	}
	public void setDistance(double distance) {
		this.distance = distance;
	}
	
	public String getSrcId() {
		return srcId;
	}

	public void setSrcId(String srcId) {
		this.srcId = srcId;
	}

	public String getDstId() {
		return dstId;
	}

	public void setDstId(String dstId) {
		this.dstId = dstId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(dstId, srcId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Distance other = (Distance) obj;
		return Objects.equals(dstId, other.dstId) && Objects.equals(srcId, other.srcId);
	}

	@Override
	public String toString() {
		return "Distance [distance=" + distance + ", srcId=" + srcId + ", dstId=" + dstId + "]";
	}
	
}
