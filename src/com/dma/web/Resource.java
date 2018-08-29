package com.dma.web;

public class Resource {

	String jndiName = "";
	String dbName = "";
	String dbEngine = "";
	String description = "";

	public String getJndiName() {
		return jndiName;
	}
	public void setJndiName(String jndiName) {
		this.jndiName = jndiName;
	}
	public String getDbName() {
		return dbName;
	}
	public void setDbName(String dbName) {
		this.dbName = dbName;
	}
	public String getDbEngine() {
		return dbEngine;
	}
	public void setDbEngine(String dbEngine) {
		this.dbEngine = dbEngine;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	
}
