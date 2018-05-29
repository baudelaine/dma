package com.dma.web;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import javax.sql.DataSource;

/**
 * Application Lifecycle Listener implementation class SessionListener
 *
 */
@WebListener
public class SessionListener implements HttpSessionListener {

	// Value as to match the value in server.xml
	String jndiName = "";
	String schema = "";
	String query = "";
	boolean withRecCount = false;
	Map<String, QuerySubject> query_subjects = new HashMap<String, QuerySubject>();
	
    /**
     * Default constructor. 
     */
    public SessionListener() {
        // TODO Auto-generated constructor stub
    }

	/**
     * @see HttpSessionListener#sessionCreated(HttpSessionEvent)
     */
    public void sessionCreated(HttpSessionEvent arg0)  { 
         // TODO Auto-generated method stub
		System.out.println("Session " + arg0.getSession().getId() + " has been created...");
    	HttpSession s = arg0.getSession();
    	InitialContext ic = (InitialContext) s.getServletContext().getAttribute("ic");
    	
    	try {
    		
    		String cognosFolder = (String) ic.lookup("CognosFolder"); 
    		String cognosModelsPath = (String) ic.lookup("CognosModelsPath"); 
    		String cognosDispatcher= (String) ic.lookup("CognosDispatcher");
    		String cognosLogin = (String) ic.lookup("CognosLogin");
    		String cognosPassword = (String) ic.lookup("CognosPassword");
    		String cognosNamespace = (String) ic.lookup("CognosNamespace");
    		String cognosDataSource = (String) ic.lookup("CognosDataSource"); 
    		String cognosSchema = (String) ic.lookup("CognosSchema"); 
    		String cognosDefaultLocale = (String) ic.lookup("CognosDefaultLocale"); 
    		String cognosLocales = (String) ic.lookup("CognosLocales");
    		
    		String dbEngine = (String) ic.lookup("DBEngine");
    		String schema = (String) ic.lookup("DBSchema");
    		
    		switch(dbEngine.toUpperCase()){
    		
    			case "ORA":
    				jndiName = "jdbc/ORA";
    				query = (String) ic.lookup("TestORAConnection");
    				break;
    				
    			case "DB2":
    				jndiName = "jdbc/DB2";
    				query = (String) ic.lookup("TestDB2Connection");
    				break;

    			case "DB2400":
    				jndiName = "jdbc/DB2400";
    				query = (String) ic.lookup("TestDB2400Connection");
    				break;

    			case "SQLSRV":
    				jndiName = "jdbc/SQLSRV";
    				query = (String) ic.lookup("TestSQLSRVConnection");
    				break;

    			case "MYSQL":
    				jndiName = "jdbc/MYSQL";
    				query = (String) ic.lookup("TestMYSQLConnection");
    				break;

    			case "PGSQL":
    				jndiName = "jdbc/PGSQL";
    				query = (String) ic.lookup("TestPGSQLConnection");
    				break;
    				
    		}
    		
			Connection con = null;
//			if(dbEngine.equalsIgnoreCase("DB2400")){
//				Class.forName("com.ibm.as400.access.AS400JDBCDriver");
//				System.out.println("com.ibm.as400.access.AS400JDBCDriver loaded successfully !!!");
//				AS400JDBCConnection con400 = (AS400JDBCConnection) DriverManager.getConnection("jdbc:as400:");
				
//				AS400JDBCDataSource datasource = new AS400JDBCDataSource("172.16.2.70");
//				  datasource.setUser("IBMIIC");
//				  datasource.setPassword("spcspc");
//				  datasource.setDatabaseName("S6514BFA");
//				  con = datasource.getConnection();
//			}
//			else{
//			}
				
			DataSource ds = (DataSource) ic.lookup(jndiName);
			con = ds.getConnection();
			
			
			s.setAttribute("con", con);
			s.setAttribute("dbEngine", dbEngine);
			s.setAttribute("jndiName", jndiName);
			s.setAttribute("schema", schema);
			s.setAttribute("query", query);
			s.setAttribute("query_subjects", query_subjects);
			s.setAttribute("cognosFolder", cognosFolder); 
			s.setAttribute("cognosModelsPath", cognosModelsPath);
			s.setAttribute("cognosDispatcher", cognosDispatcher); 
			s.setAttribute("cognosLogin", cognosLogin); 
			s.setAttribute("cognosPassword", cognosPassword); 
			s.setAttribute("cognosNamespace", cognosNamespace); 
			s.setAttribute("cognosDataSource", cognosDataSource);
			s.setAttribute("cognosSchema", cognosSchema);
			s.setAttribute("cognosDefaultLocale", cognosDefaultLocale);
			s.setAttribute("cognosLocales", cognosLocales);
			System.out.println("SessionId " + s.getId() + " is now connected to " + jndiName + " using shema " + schema);
			
			withRecCount = (Boolean) ic.lookup("WithRecCount");
			s.setAttribute("withRecCount", withRecCount);
			
		} catch (NamingException | SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 

    }

	/**
     * @see HttpSessionListener#sessionDestroyed(HttpSessionEvent)
     */
    public void sessionDestroyed(HttpSessionEvent arg0)  { 
         // TODO Auto-generated method stub
    	HttpSession s = arg0.getSession();
    	InitialContext ic = (InitialContext) s.getServletContext().getAttribute("ic");
    	try {
    		query_subjects.clear();
			DataSource ds = (DataSource) ic.lookup(jndiName);
			Connection con = ds.getConnection();
			con.close();
			System.out.println("Connection to " + jndiName + " has been closed...");
			
		} catch (NamingException | SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}    	
		System.out.println("Session " + arg0.getSession().getId() + " has been destroyed...");
    }
	
}

