package com.dma.web;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.RecursiveToStringStyle;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import com.dma.web.Field;
import com.dma.web.QuerySubject;
import com.dma.web .Relation;
import com.dma.properties.ConfigProperties;
import com.dma.svc.CognosSVC;
import com.dma.svc.FactorySVC;
import com.dma.web.RelationShip;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Servlet implementation class GetSelectionsServlet
 */
@WebServlet(name = "SendQuerySubjects", urlPatterns = { "/SendQuerySubjects" })
public class SendQuerySubjectsServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	Map<String, Integer> gRefMap;
	List<RelationShip> rsList;
	Map<String, QuerySubject> query_subjects;
	Map<String, String> labelMap;
	CognosSVC csvc = new CognosSVC();
	FactorySVC fsvc = new FactorySVC(csvc);

    /**
     * @see HttpServlet#HttpServlet()
     */
    public SendQuerySubjectsServlet() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	@SuppressWarnings("unchecked")
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		
		Map<String, Object> parms = Tools.fromJSON(request.getInputStream());
		
		String projectName = (String) parms.get("projectName");
		String data = (String) parms.get("data");
		
		ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        List<QuerySubject> qsList = Arrays.asList(mapper.readValue(data, QuerySubject[].class));
        
        query_subjects = new HashMap<String, QuerySubject>();
        Map<String, Integer> recurseCount = new HashMap<String, Integer>();
        
        for(QuerySubject qs: qsList){
        	query_subjects.put(qs.get_id(), qs);
        	recurseCount.put(qs.getTable_alias(), 0);
        }
        
        request.getSession().setAttribute("query_subjects", query_subjects);
        
		query_subjects = (Map<String, QuerySubject>) request.getSession().getAttribute("query_subjects");
		
		System.out.println("query_subjects.size=" + query_subjects.size());
		
		Map<String, Object> result = new HashMap<String, Object>();
		
		// START SETUP COGNOS ENVIRONMENT
		
		Path cognosModelsPath = Paths.get((String) request.getSession().getAttribute("cognosModelsPath"));
		if(!Files.isWritable(cognosModelsPath)){
			result.put("status", "KO");
			result.put("message", "cognosModelsPath '" + cognosModelsPath + "' not writeable." );
			result.put("troubleshooting", "Check that '" + cognosModelsPath + "' exists on server and is writable.");
		}
		
		Path projectPath = Paths.get(cognosModelsPath + "/" + projectName);
		
		if(Files.exists(projectPath)){
			Files.walk(Paths.get(projectPath.toString()))
            .map(Path::toFile)
            .sorted((o1, o2) -> -o1.compareTo(o2))
            .forEach(File::delete);
		}
		
		Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxrwx");
		FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(perms);
		Files.createDirectories(projectPath, attrs);
		
		Path zip = Paths.get(getServletContext().getRealPath("/res/model.zip"));
		if(!Files.exists(zip)){
			result.put("status", "KO");
			result.put("message", "Generic model '" + zip + "' not found." );
			result.put("troubleshooting", "Check that '" + zip + "' exists on server.");
		}
		
		
		BufferedOutputStream dest = null;
		int BUFFER = Long.bitCount(Files.size(zip));
		ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip))); 
		ZipEntry entry;
		while((entry = zis.getNextEntry()) != null) {
			System.out.println("Extracting: " + entry);
            int count;
            byte datas[] = new byte[BUFFER];
            // write the files to the disk
            FileOutputStream fos = new FileOutputStream(projectPath + "/" + entry.getName());
            dest = new BufferedOutputStream(fos, BUFFER);
            while ((count = zis.read(datas, 0, BUFFER)) 
              != -1) {
               dest.write(datas, 0, count);
            }
            dest.flush();
            dest.close();
        }
		zis.close();
		
		Path cpf = Paths.get(projectPath + "/model.cpf");
		Path renamedCpf = Paths.get(projectPath + "/" + projectName + ".cpf"); 
		if(Files.exists(cpf)){
			Files.move(cpf, renamedCpf);
			result.put("status", "OK");
			result.put("message", renamedCpf + " found in " + projectPath + ".");
			result.put("troubleshooting", "");
		}
		
		// END SETUP COGNOS ENVIRONMENT

		if(((String) result.get("status")).equalsIgnoreCase("OK")){

			try{
				
		        //start();
				csvc.logon();
				String modelName = projectName;
				csvc.openModel(modelName);
				fsvc.setLocale();
				
				//IICInitNameSpace();
				fsvc.createNamespace("PHYSICAL", "Model");
				fsvc.createNamespace("PHYSICALUSED", "Model");
				fsvc.createNamespace("AUTOGENERATION", "Model");
				fsvc.createNamespace("FINAL", "AUTOGENERATION");
				fsvc.createNamespace("REF", "AUTOGENERATION");
				fsvc.createNamespace("DATA", "AUTOGENERATION");
				
				//Import();
				fsvc.DBImport("PHYSICAL", "DEV", "DANONE");
				
				gRefMap = new HashMap<String, Integer>();
				
				rsList = new ArrayList<RelationShip>();
	
				labelMap = new HashMap<String, String>();
				
				for(Entry<String, QuerySubject> query_subject: query_subjects.entrySet()){
					
					if (query_subject.getValue().getType().equalsIgnoreCase("Final")){
						
						fsvc.copyQuerySubject("[PHYSICALUSED]", "[PHYSICAL].[" + query_subject.getValue().getTable_name() + "]");
						fsvc.renameQuerySubject("[PHYSICALUSED].[" + query_subject.getValue().getTable_name() + "]", "FINAL_" + query_subject.getValue().getTable_alias());
						
						fsvc.createQuerySubject("PHYSICALUSED", "FINAL", "FINAL_" + query_subject.getValue().getTable_alias(), query_subject.getValue().getTable_alias());
						
						fsvc.createQuerySubject("FINAL", "DATA", query_subject.getValue().getTable_alias() , query_subject.getValue().getTable_alias());
						//tooltip
						String desc = "";
						if(query_subject.getValue().getDescription() != null) {desc = ": " + query_subject.getValue().getDescription();}
						fsvc.createScreenTip("querySubject", "[DATA].[" + query_subject.getValue().getTable_alias() + "]" , query_subject.getValue().getTable_name() + desc );
						//end tooltip
						
						for(Relation rel: query_subject.getValue().getRelations()){
							if(rel.isFin()){
						
								RelationShip RS = new RelationShip("[FINAL].[" + query_subject.getValue().getTable_alias() + "]" , "[FINAL].[" + rel.getPktable_alias() + "]");
								// changer en qs + refobj
								RS.setExpression(rel.getRelationship());
								RS.setCard_left_min("one");
								RS.setCard_left_max("many");
			
								RS.setCard_right_min("one");
								RS.setCard_right_max("one");
								RS.setParentNamespace("FINAL");
								rsList.add(RS);					
						
							}
							if(rel.isRef()){
								
								String pkAlias = rel.getPktable_alias();
								Integer i = gRefMap.get(pkAlias);
								
								if(i == null){
									gRefMap.put(pkAlias, new Integer(0));
									i = gRefMap.get(pkAlias);
								}
								gRefMap.put(pkAlias, i + 1);
					
								fsvc.copyQuerySubject("[PHYSICALUSED]", "[PHYSICAL].[" + rel.getPktable_name() + "]");	
								fsvc.renameQuerySubject("[PHYSICALUSED].[" + rel.getPktable_name() + "]","REF_" + pkAlias + String.valueOf(i));
								fsvc.createQuerySubject("PHYSICALUSED", "REF","REF_" + pkAlias + String.valueOf(i), pkAlias + String.valueOf(i));
			
								RelationShip RS = new RelationShip("[FINAL].[" + query_subject.getValue().getTable_alias() + "]" , "[REF].[" + rel.getPktable_alias()  + String.valueOf(i) + "]");
								// changer en qs + refobj
								String exp = rel.getRelationship();
								String fixedExp = StringUtils.replace(exp, "[REF].[" + rel.getPktable_alias() + "]", "[REF].[" + rel.getPktable_alias()  + String.valueOf(i) + "]");
								RS.setExpression(fixedExp);
								RS.setCard_left_min("one");
								RS.setCard_left_max("many");
			
								RS.setCard_right_min("one");
								RS.setCard_right_max("one");
								RS.setParentNamespace("AUTOGENERATION");
								rsList.add(RS);
								
								String gFieldName = "";
								String gDirName = "";
								String label = "";
								
								//seq
								if(rel.getKey_type().equalsIgnoreCase("P") || rel.isNommageRep()){
	//								gFieldName = rel.getPktable_alias();
	//								gDirName = "." + rel.getPktable_alias();
									gFieldName = pkAlias;
									gDirName = "." + pkAlias;
									// add labels to map
									if(query_subjects.get(pkAlias + "Ref").getLabel() == null || query_subjects.get(pkAlias + "Ref").getLabel().equals(""))
									{label = pkAlias;} else {label = query_subjects.get(pkAlias + "Ref").getLabel();
									}
									labelMap.put(query_subject.getValue().getTable_alias() + gDirName , label);
									//end labels
								}
								else{
									gFieldName = rel.getSeqs().get(0).getColumn_name();
									gDirName = "." + rel.getSeqs().get(0).getColumn_name();
								}
								String gFieldNameReorder = rel.getSeqs().get(0).getColumn_name();
								fsvc.createSubFolder("[DATA].[" + query_subject.getValue().getTable_alias() + "]", gDirName);
								//tooltip
								desc = "";
								if(query_subjects.get(pkAlias + "Ref").getDescription() != null) {desc = ": " + query_subjects.get(pkAlias + "Ref").getDescription();}
								fsvc.createScreenTip("queryItemFolder", "[DATA].[" + query_subject.getValue().getTable_alias() + "].[" + gDirName + "]", query_subjects.get(pkAlias + "Ref").getTable_name() + desc);
	
								if(rel.getKey_type().equalsIgnoreCase("F")){
									fsvc.ReorderSubFolderBefore("[DATA].[" + query_subject.getValue().getTable_alias() + "].[" + gDirName + "]", "[DATA].[" + query_subject.getValue().getTable_alias() + "].[" + gFieldNameReorder + "]");
								}
								
								for(Field field: query_subjects.get(pkAlias + "Ref").getFields()){
									
									fsvc.createQueryItemInFolder("[DATA].[" + query_subject.getValue().getTable_alias() + "]", gDirName, gFieldName + "." + field.getField_name(), "[REF].["+ pkAlias + String.valueOf(i) +"].[" + field.getField_name() + "]");
									//add label
									if(field.getLabel() == null || field.getLabel().equals(""))
									{label = field.getField_name();} else {label = field.getLabel();
									}
									labelMap.put(query_subject.getValue().getTable_alias() + "." + gFieldName + "." + field.getField_name(), label);
									// end label
									//add tooltip
									desc = "";
									if(field.getDescription() != null) {desc = ": " + field.getDescription();}
									fsvc.createScreenTip("queryItem", "[DATA].[" + query_subject.getValue().getTable_alias() + "].[" + gFieldName + "." + field.getField_name() + "]", query_subjects.get(pkAlias + "Ref").getTable_name() + "." + field.getField_name() + desc);
									//end tooltip
									//change property query item
									fsvc.changeQueryItemProperty("[DATA].[" + query_subject.getValue().getTable_alias() + "].[" + gFieldName + "." + field.getField_name() + "]", "usage", field.getIcon().toLowerCase());
									if (!field.getDisplayType().toLowerCase().equals("value"))
									{
										fsvc.changeQueryItemProperty("[DATA].[" + query_subject.getValue().getTable_alias() + "].[" + gFieldName + "." + field.getField_name() + "]", "displayType", field.getDisplayType().toLowerCase());
										
									}
									//end change
								}
								
								
								for(QuerySubject qs: qsList){
						        	recurseCount.put(qs.getTable_alias(), 0);
						        }
								
								f1(pkAlias, pkAlias + i, gDirName, "[DATA].[" + query_subject.getValue().getTable_alias() + "]", query_subject.getValue().getTable_alias(), recurseCount);
							}
	
						}
						//add label map qs
						labelMap.put(query_subject.getValue().getTable_alias(), query_subject.getValue().getLabel());
						
						//add label map fields
						for(Field field: query_subject.getValue().getFields()) {
							labelMap.put(query_subject.getValue().getTable_alias() + "." + field.getField_name(), field.getLabel());
						//add tooltip
						desc = "";
						if(field.getDescription() != null) {desc = ": " + field.getDescription();}	
						fsvc.createScreenTip("queryItem", "[DATA].[" + query_subject.getValue().getTable_alias() + "].[" + field.getField_name() + "]", query_subject.getValue().getTable_name() + "." + field.getField_name() + desc);
						//end tooltip
						//change property query item
						fsvc.changeQueryItemProperty("[DATA].[" + query_subject.getValue().getTable_alias() + "].[" + field.getField_name() + "]", "usage", field.getIcon().toLowerCase());
						if (!field.getDisplayType().toLowerCase().equals("value"))
						{
							fsvc.changeQueryItemProperty("[DATA].[" + query_subject.getValue().getTable_alias() + "].[" + field.getField_name() + "]", "displayType", field.getDisplayType().toLowerCase());
						}
						//end change
						}
						// end label		
					}
					
				}
				//IICCreateRelation(rsList);
				for(RelationShip rs: rsList){
					fsvc.createRelationship(rs);
				}
				
				fsvc.addLocale("en");
	
		/*
				for(Entry<String, String> map: labelMap.entrySet()){
					System.out.println(map.getKey() + " * * * * * " + map.getValue());
				}
		*/		
				
				
				// tests
				
				csvc.executeAllActions();
				// fin tests
			
				//stop();
				csvc.saveModel();
				csvc.closeModel();
				csvc.logoff();
				System.out.println("END COGNOS API");
				
				// code parser xml for labels
				
				System.out.println("START XML MODIFICATION");
				try {
					
					String modelSharedPath = projectPath + "/model.xml";
								
					Path input = Paths.get(modelSharedPath);
					Path output = Paths.get(modelSharedPath);
					String datas = null;
					String inputSearch = "xmlns=\"http://www.developer.cognos.com/schemas/bmt/60/12\"";
					String outputSearch = "queryMode=\"dynamic\"";
					String outputReplace = outputSearch + " " + inputSearch;  
					
					Charset charset = StandardCharsets.UTF_8;
					if(Files.exists(input)){
						datas = new String(Files.readAllBytes(input), charset);
					}
	
					datas = StringUtils.replace(datas, inputSearch, "");
					
					// modifs
					
	//				File xmlFile = new File(ConfigProperties.modelXML);
					SAXReader reader = new SAXReader();
					Document document = reader.read(new ByteArrayInputStream(datas.getBytes(StandardCharsets.UTF_8)));
					
					String namespaceName = "AUTOGENERATION";
					String spath = "/project/namespace/namespace";
					int k=1;
					
					Element namespace = (Element) document.selectSingleNode(spath + "[" + k + "]/name");			
					while(!namespace.getStringValue().equals(namespaceName) && namespace != null)
					{
					k++;
					namespace = (Element) document.selectSingleNode(spath + "[" + k + "]/name");
					}
					namespaceName = "DATA";
					spath = spath + "[" + k + "]";
								
					k=1;
					namespace = (Element) document.selectSingleNode(spath + "/namespace[" + k + "]/name");
					while(!namespace.getStringValue().equals(namespaceName) && namespace!=null)
					{
					k++;
					namespace = (Element) document.selectSingleNode(spath + "/namespace[" + k + "]/name");
					}
					spath = spath + "/namespace[" + k + "]";
					fsvc.recursiveParserQS(document, spath, "en", labelMap);
	
					try {
		
						datas = document.asXML();
	
						datas = StringUtils.replace(datas, outputSearch, outputReplace);
						Files.write(output, datas.getBytes());
	
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					// fin test writer
					
				} catch (DocumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				System.out.println("END XML MODIFICATION");
				
				//publication
				System.out.println("Create and Publish Package");	
				
				//start
				csvc = new CognosSVC();
				fsvc = new FactorySVC(csvc);
				csvc.logon();
				csvc.openModel(modelName);
				fsvc.setLocale();
				
				String[] locales = {"en"};
				fsvc.changePropertyFixIDDefaultLocale();
				fsvc.createPackage(modelName, modelName, modelName, locales);
				fsvc.publishPackage(modelName,"/content");
				
				csvc.executeAllActions();
				
				csvc.saveModel();
				csvc.closeModel();
				csvc.logoff();
				
				
				System.out.println("FIN DU MAIN");

				result.put("status", "OK");
				result.put("message", projectName + " published sucessfully.");
				result.put("troubleshooting", "");
				result.put("data", data);
				
				}
				catch(Exception e){
					e.printStackTrace(System.err);
				}
			
			}
			
			//response to the browser
			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write(Tools.toJSON(result));
		
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		doGet(request, response);
	}
	
	protected void f1(String qsAlias, String qsAliasInc, String gDirName, String qsFinal, String qsFinalName, Map<String, Integer> recurseCount){
			
			Map<String, Integer> copyRecurseCount = new HashMap<String, Integer>();
			copyRecurseCount.putAll(recurseCount);
			
			QuerySubject query_subject = query_subjects.get(qsAlias + "Ref");
	
			int j = copyRecurseCount.get(qsAlias);
			if(j == query_subject.getRecurseCount()){
				return;
			}
			copyRecurseCount.put(qsAlias, j + 1);
	
			for(Relation rel: query_subject.getRelations()){
				if(rel.isRef()){
					
					String pkAlias = rel.getPktable_alias();
					Integer i = gRefMap.get(pkAlias);
					
					if(i == null){
						gRefMap.put(pkAlias, new Integer(0));
						i = gRefMap.get(pkAlias);
					}
					gRefMap.put(pkAlias, i + 1);
	
					fsvc.copyQuerySubject("[PHYSICALUSED]", "[PHYSICAL].[" + rel.getPktable_name() + "]");	
					fsvc.renameQuerySubject("[PHYSICALUSED].[" + rel.getPktable_name() + "]","REF_" + pkAlias + String.valueOf(i));
					fsvc.createQuerySubject("PHYSICALUSED", "REF","REF_" + pkAlias + String.valueOf(i), pkAlias + String.valueOf(i));
					
					//seq
					String gFieldName = "";
					String gDirNameCurrent = "";
					String label = "";
					if(rel.getKey_type().equalsIgnoreCase("P") || rel.isNommageRep()){
						gFieldName = gDirName.substring(1) + "." + pkAlias;
						gDirNameCurrent = gDirName + "." + pkAlias;
						if(query_subjects.get(pkAlias + "Ref").getLabel() == null || query_subjects.get(pkAlias + "Ref").getLabel().equals(""))
						{label = pkAlias;} else {label = query_subjects.get(pkAlias + "Ref").getLabel();
						}
						labelMap.put(qsFinalName + gDirNameCurrent, label);
					}
					else{
						gFieldName = gDirName.substring(1) + "." + rel.getSeqs().get(0).getColumn_name();
						gDirNameCurrent = gDirName + "." + rel.getSeqs().get(0).getColumn_name();
					}
					String gFieldNameReorder = gDirName.substring(1) + "." + rel.getSeqs().get(0).getColumn_name();
					String rep = qsFinal + ".[" + gDirName + "]";
					
					fsvc.createSubFolderInSubFolderIIC(rep, gDirNameCurrent);
					
					//add tooltip
					String desc = "";
					if(query_subjects.get(pkAlias + "Ref").getDescription() != null) {desc = ": " + query_subjects.get(pkAlias + "Ref").getDescription();}
					fsvc.createScreenTip("queryItemFolder", qsFinal + ".[" + gDirNameCurrent + "]", query_subjects.get(pkAlias + "Ref").getTable_name() + desc);
					//end tooltip
					
					if(rel.getKey_type().equalsIgnoreCase("F")){
						fsvc.ReorderSubFolderBefore(qsFinal + ".[" + gDirNameCurrent + "]", qsFinal + ".[" + gFieldNameReorder + "]");
					}
					
					for(Field field: query_subjects.get(pkAlias + "Ref").getFields()){
						
						fsvc.createQueryItemInFolder(qsFinal, gDirNameCurrent, gFieldName + "." + field.getField_name(), "[REF].["+ pkAlias + String.valueOf(i) +"].[" + field.getField_name() + "]");
						
						//add label
						if(field.getLabel() == null || field.getLabel().equals(""))
						{label = field.getField_name();} else {label = field.getLabel();
						}
						labelMap.put(qsFinalName + "." + gFieldName + "." + field.getField_name(), label);
						// end label
						// add tooltip
						desc = "";
						if(field.getDescription() != null) {desc = ": " + field.getDescription();}
						fsvc.createScreenTip("queryItem", qsFinal + ".[" + gFieldName + "." + field.getField_name() + "]", query_subjects.get(pkAlias + "Ref").getTable_name() + "." + field.getField_name() + desc);
						// end tooltip
						//change property query item
						fsvc.changeQueryItemProperty(qsFinal + ".[" + gFieldName + "." + field.getField_name() + "]", "usage", field.getIcon().toLowerCase());
						if (!field.getDisplayType().toLowerCase().equals("value"))
						{
							fsvc.changeQueryItemProperty(qsFinal + ".[" + gFieldName + "." + field.getField_name() + "]", "displayType", field.getDisplayType().toLowerCase());
							
						}
						//end change
					}
					
					RelationShip RS = new RelationShip("[REF].[" + qsAliasInc + "]" , "[REF].[" + pkAlias + String.valueOf(i) + "]");
					// changer en qs + refobj
					String fixedExp = StringUtils.replace(rel.getRelationship(), "[REF].[" + qsAlias + "]", "[REF].[" + qsAliasInc + "]");
					fixedExp = StringUtils.replace(fixedExp, "[REF].[" + pkAlias + "]", "[REF].[" + pkAlias + String.valueOf(i) + "]");
					RS.setExpression(fixedExp);
					RS.setCard_left_min("one");
					RS.setCard_left_max("many");
	
					RS.setCard_right_min("one");
					RS.setCard_right_max("one");
					RS.setParentNamespace("REF");
					rsList.add(RS);
					
	
					f1(pkAlias, pkAlias + String.valueOf(i), gDirNameCurrent, qsFinal, qsFinalName, copyRecurseCount);
					
				}
			}
			
			
			
		}

}
