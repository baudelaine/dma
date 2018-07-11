package com.dma.web;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
    		String cognosSchema = null;
    		try{
    			cognosSchema = (String) ic.lookup("CognosSchema");
    		}
    		catch(NamingException e){
    			cognosSchema = "";
    		}
    		String cognosCatalog = null;
    		try{
    			cognosCatalog = (String) ic.lookup("CognosCatalog");
    		}
    		catch(NamingException e){
    			cognosSchema = "";
    		}
    		try{
    			cognosSchema = (String) ic.lookup("CognosSchema");
    		}
    		catch(NamingException e){
    			cognosSchema = "";
    		}
    		String cognosDefaultLocale = (String) ic.lookup("CognosDefaultLocale"); 
    		String cognosLocales = (String) ic.lookup("CognosLocales");
    		String schema = null;
    		try{
    			schema = (String) ic.lookup("DBSchema");
    		}
    		catch(NamingException e){
    			schema = "";
    		}
    		String dbEngine = (String) ic.lookup("DBEngine");
    		
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
			s.setAttribute("cognosCatalog", cognosCatalog);
			s.setAttribute("cognosDefaultLocale", cognosDefaultLocale);
			s.setAttribute("cognosLocales", cognosLocales);

			String aliasesQuery = "";
			
			switch(dbEngine.toUpperCase()){
			
				case "DB2":
					aliasesQuery = "SELECT base_tabname, tabname FROM syscat.tables WHERE type = 'A' AND owner = 'DB2INST1'";
					break;
				
				case "ORA":
					aliasesQuery = "SELECT table_name, synonym_name FROM user_synonyms"; // or maybe all_synonyms
					break;
					
				case "SQLSRV":
					aliasesQuery = "SELECT PARSENAME(base_object_name,1), name FROM sys.synonyms";
					break;
			}
			
			Map<String, String> tableAliases = new HashMap<String, String>();
			try{
				PreparedStatement stmt = con.getMetaData().getConnection().prepareStatement(aliasesQuery);
	            ResultSet rst = stmt.executeQuery();
	            while (rst.next()) {
	                String table = rst.getString(1);
	                String alias = rst.getString(2);
	                tableAliases.put(alias, table);
	            }			
				System.out.println("tableAliases=" + tableAliases);
				s.setAttribute("tableAliases", tableAliases);
			    
				if(rst != null){rst.close();}
			}
			catch(Exception e){
				//Ignore error if no alias in database
			}

			
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

