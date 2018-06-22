package com.dma.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Servlet implementation class GetTablesServlet
 */
@WebServlet("/GetDBMDFromCache")
public class GetDatabaseMetaDatasFromCache extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public GetDatabaseMetaDatasFromCache() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		Connection con = null;
		Map<String, Object> result = new HashMap<String, Object>();
		String schema = "";

		try {
			
			String realPath = getServletContext().getRealPath("/");
			System.out.println("realPath=" + realPath);
			
			String fileName = realPath + "/res/dbmd.json";
			System.out.println("fileName=" + fileName);			
			File file = new File(fileName);
			
			if(file.exists()){
				
				System.out.println("Load Database Meta Datas from cache...");
				
				BufferedReader br = new BufferedReader(new FileReader(file));
				
				ObjectMapper mapper = new ObjectMapper();
		        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
				result = mapper.readValue(br, new TypeReference<Map<String, Object>>(){});

				request.getSession().setAttribute("dbmd", result);
				
				if(br != null){br.close();}
			}
			    
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write(Tools.toJSON(result));
				
			
		}
		catch (Exception e){
			e.printStackTrace(System.err);
		}		
		
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		doGet(request, response);
	}

}
