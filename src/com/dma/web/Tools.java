package com.dma.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Tools {

	public final static String toJSON(Object o){
		try{
		ObjectMapper mapper = new ObjectMapper();
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		StringWriter sw = new StringWriter();
		String jsonResult = null;
		mapper.writeValue(sw, o);
		sw.flush();
		jsonResult = sw.toString();
		sw.close();
		return jsonResult;
		}
		catch(Exception e){
			e.printStackTrace(System.err);
		}
		return null;
	}
	
	public final static Map<String, Object> fromJSON(InputStream is){
		Map<String, Object>	map = new HashMap<String, Object>();
		
		try{
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(isr);
	        ObjectMapper mapper = new ObjectMapper();
	        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
			map = mapper.readValue(br, new TypeReference<Map<String, Object>>(){});
			return map;
		}
		catch(Exception e){
			e.printStackTrace(System.err);
		}
		
        return null;
	}
	
	public final static List<Resource> getResources(){
		
		try{
			
			String wlpHome = System.getenv("WLP_HOME");
			String wlpSrvName = System.getenv("WLP_SRV_NAME");
			
			Path cnfwlpPath = Paths.get(wlpHome +  "/usr/servers/" + wlpSrvName + "/server.xml");
			
			if(!Files.exists(cnfwlpPath)){
				return null;
			}
			
			File file = cnfwlpPath.toFile();
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(file);
			
			XPathFactory xfact = XPathFactory.newInstance();
			XPath xpath = xfact.newXPath();
			
			NodeList nodeList = (NodeList) xpath.evaluate("/server/dataSource", document, XPathConstants.NODESET);
			
			List<Resource> rs = new ArrayList<Resource>();
			for(int index = 0; index < nodeList.getLength(); index++){
				Node node = nodeList.item(index);
				Resource r = new Resource();
				r.setJndiName(getAttrValue(node, "jndiName"));
				NodeList childNodes = node.getChildNodes();
				for(int i = 0; i < childNodes.getLength(); i++){
					Node childNode = childNodes.item(i);
					if(childNode.getNodeName().equalsIgnoreCase("jdbcDriver")){
						r.setDbEngine(getAttrValue(childNode, "libraryRef").split("Lib")[0]);
					}
					if(childNode.getNodeName().toLowerCase().startsWith("properties")){
						r.setDbName(getAttrValue(childNode, "databaseName"));
					}
				}
				
				rs.add(r);
			}
			
			for(Resource r: rs){
				r.setDescription(r.getDbName() + " (" + r.getJndiName() + " - " + r.getDbEngine() + ")");
			}
			
			return rs;
			
		}
		catch(Exception e){
			e.printStackTrace(System.err);
		}
		
		return null;
		
	}

	static private String getAttrValue(Node node,String attrName) {
	    if ( ! node.hasAttributes() ) return "";
	    NamedNodeMap nmap = node.getAttributes();
	    if ( nmap == null ) return "";
	    Node n = nmap.getNamedItem(attrName);
	    if ( n == null ) return "";
	    return n.getNodeValue();
	}
	
	static private String getTextContent(Node parentNode,String childName) {
	    NodeList nlist = parentNode.getChildNodes();
	    for (int i = 0 ; i < nlist.getLength() ; i++) {
		    Node n = nlist.item(i);
		    String name = n.getNodeName();
		    if ( name != null && name.equals(childName) ) return n.getTextContent();
	    }
	    return "";
	}	
	
}
