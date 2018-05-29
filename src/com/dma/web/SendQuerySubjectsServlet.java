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
	Map<String, String> toolTipMap;
	Map<String, String> filterMap;
	Map<String, String> filterMapApply;
	List<QuerySubject> qsList = null;
	CognosSVC csvc;
	FactorySVC fsvc;

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
        qsList = Arrays.asList(mapper.readValue(data, QuerySubject[].class));
        
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
		
		String cognosFolder = (String) request.getSession().getAttribute("cognosFolder");
		String cognosDispatcher = (String) request.getSession().getAttribute("cognosDispatcher");
		String cognosLogin = (String) request.getSession().getAttribute("cognosLogin");
		String cognosPassword = (String) request.getSession().getAttribute("cognosPassword");
		String cognosNamespace = (String) request.getSession().getAttribute("cognosNamespace");
		String pathToXML = getServletContext().getRealPath("/") + "/res/templates";
		if(!Files.exists(Paths.get(pathToXML))){
			result.put("status", "KO");
			result.put("message", "PathToXML " + pathToXML + " not found." );
			result.put("troubleshooting", "Check that '" + pathToXML + "' exists on server and contains XML templates.");
		}
		
		
		// END SETUP COGNOS ENVIRONMENT

		if(((String) result.get("status")).equalsIgnoreCase("OK")){

			try{
				
		        //start();
				String cognosDataSource = (String) request.getSession().getAttribute("cognosDataSource");
				String cognosSchema = (String) request.getSession().getAttribute("cognosSchema");
				String cognosDefaultLocale = (String) request.getSession().getAttribute("cognosDefaultLocale");
				String cognosLocales = (String) request.getSession().getAttribute("cognosLocales");
				System.out.println("cognosLocales=" + cognosLocales);

				csvc = new CognosSVC(cognosDispatcher);
				csvc.setPathToXML(pathToXML);
				fsvc = new FactorySVC(csvc);
				csvc.logon(cognosLogin, cognosPassword, cognosNamespace);
				String modelName = projectName;
				csvc.openModel(modelName, cognosFolder);
				fsvc.setLocale(cognosDefaultLocale);
				
				//IICInitNameSpace();
				fsvc.createNamespace("PHYSICAL", "Model");
				fsvc.createNamespace("PHYSICALUSED", "Model");
				fsvc.createNamespace("AUTOGENERATION", "Model");
				fsvc.createNamespace("FINAL", "AUTOGENERATION");
				fsvc.createNamespace("REF", "AUTOGENERATION");
				fsvc.createNamespace("SEC", "AUTOGENERATION");
				fsvc.createNamespace("FILTER_FINAL", "AUTOGENERATION");
				fsvc.createNamespace("FILTER_REF", "AUTOGENERATION");
				fsvc.createNamespace("DATA", "Model");
				
				//Import();
				fsvc.DBImport("PHYSICAL", cognosDataSource, cognosSchema);
				
				gRefMap = new HashMap<String, Integer>();
				
				rsList = new ArrayList<RelationShip>();

				labelMap = new HashMap<String, String>();
				toolTipMap = new HashMap<String, String>();
				filterMap = new HashMap<String, String>();
				filterMapApply = new HashMap<String, String>();
				
				for(Entry<String, QuerySubject> query_subject: query_subjects.entrySet()){
					
					if (query_subject.getValue().getType().equalsIgnoreCase("Final")){
						
						fsvc.copyQuerySubject("[PHYSICALUSED]", "[PHYSICAL].[" + query_subject.getValue().getTable_name() + "]");
						fsvc.renameQuerySubject("[PHYSICALUSED].[" + query_subject.getValue().getTable_name() + "]", "FINAL_" + query_subject.getValue().getTable_alias());
						
						fsvc.createQuerySubject("PHYSICALUSED", "FINAL", "FINAL_" + query_subject.getValue().getTable_alias(), query_subject.getValue().getTable_alias());
						//ajout filter
						if (!query_subject.getValue().getFilter().equals(""))
						{
							fsvc.createQuerySubject("FINAL", "FILTER_FINAL", query_subject.getValue().getTable_alias() , query_subject.getValue().getTable_alias());
							filterMap.put(query_subject.getValue().getTable_alias(), query_subject.getValue().getFilter());
							filterMapApply.put(query_subject.getValue().getTable_alias(), "[FILTER_FINAL].[" + query_subject.getValue().getTable_alias() + "]");
							
							fsvc.createQuerySubject("FILTER_FINAL", "DATA", query_subject.getValue().getTable_alias() , query_subject.getValue().getTable_alias());
						} else {
							fsvc.createQuerySubject("FINAL", "DATA", query_subject.getValue().getTable_alias() , query_subject.getValue().getTable_alias());
						}
						//end filter
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
								if (rel.isRightJoin())
								{
									RS.setCard_left_min("zero");
								} else {
									RS.setCard_left_min("one");
								}
								RS.setCard_left_max("many");
			
								if (rel.isLeftJoin())
								{
									RS.setCard_right_min("zero");
								} else {
									RS.setCard_right_min("one");
								}
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
								
								
								String gFieldName = "";
								String gDirName = "";
								String label = "";
								
								//seq
								if(rel.getKey_type().equalsIgnoreCase("P") || rel.isNommageRep()){
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
							
								//filtre
								String filterNameSpaceSource = "[REF]";
								//String filterReset = "";
								if (!query_subjects.get(pkAlias + "Ref").getFilter().equals(""))
								{
									//traitement language filter DDtool -> Separé par ; = diffrentes clauses pour ce QSRef
									//séparé par :  Partie 0 du tableau = emplacement QS, Partie 1 = clause filtre
									// Remplacer % par chemin partiel startwith, contains, 
									// remplacer * par le chemin en cours dans l'emplacement et dans la clause.
									
									fsvc.createQuerySubject("REF", "FILTER_REF", pkAlias + String.valueOf(i), pkAlias + String.valueOf(i));
									
									String filterArea = query_subjects.get(pkAlias + "Ref").getFilter();
									String allClauses[] = StringUtils.split(filterArea, ";");
									
									Boolean exit = false;
									for (int x=0; x < 3 && !exit; x++) {
										for (int y=0; y < allClauses.length && !exit; y++) {
											if(allClauses[y].contains(":")) {
												String pathFilter[] = StringUtils.split(allClauses[y], ":");
												String pathRefQs = pathFilter[0].trim();
												String filterRefQs = pathFilter[1];
												//replace *-j dans lexpression du filtre
												String actualPath = query_subject.getValue().getTable_alias() + gDirName;
												String actualPathTable[] = StringUtils.split(actualPath, ".");
												if(filterRefQs.contains("*-")){
													Boolean rok = false;
													for (int z=1; z<100 && !rok;z++){
														if(filterRefQs.contains("*-" + String.valueOf(z))) {
															String pathReplace = "";
															for (int k=0;k < actualPathTable.length - z; k++) {
																pathReplace = pathReplace + actualPathTable[k];
																if (k != actualPathTable.length - z - 1) {
																	pathReplace = pathReplace + ".";
																}
															}
															if(!filterRefQs.contains("*-")){
																rok = true;
															}
															filterRefQs = StringUtils.replace(filterRefQs, "*-" + String.valueOf(z), pathReplace);
														}
													}
												}
												//replace * dans l'expression du filtre
												filterRefQs = StringUtils.replace(filterRefQs, "*", query_subject.getValue().getTable_alias() + gDirName);										
												if (pathRefQs.equals("[" + query_subject.getValue().getTable_alias() + gDirName + "]")) {
													
													filterMap.put(query_subject.getValue().getTable_alias() + gDirName, filterRefQs);
													//set path and pkalias + i correspondancies
													filterMapApply.put(query_subject.getValue().getTable_alias() + gDirName, "[FILTER_REF].[" + pkAlias + String.valueOf(i) + "]");
													exit=true;
												}
											}
										}
										//remmplacement %
										if(!exit && x == 0) {
											String actualPath = query_subject.getValue().getTable_alias() + gDirName;
											for (int y=0; y < allClauses.length; y++) {
												if(allClauses[y].contains(":")) {
													String pathFilter[] = StringUtils.split(allClauses[y], ":");
													String pathRefQs = pathFilter[0].trim();
													String containPaths[] = StringUtils.split(pathRefQs, "%");
													if (containPaths.length == 2) {
														if (actualPath.startsWith(containPaths[0].substring(1))) {
															filterArea = StringUtils.replace(filterArea, pathRefQs,"[" + actualPath + "]");															
														}
													} else if (containPaths.length == 3) {
														if (actualPath.contains(containPaths[1])) {
															filterArea = StringUtils.replace(filterArea, pathRefQs, "[" + actualPath + "]");
														}
													}
												}
											}
											allClauses = StringUtils.split(filterArea, ";");
										}
										//remplacement *
										if(!exit && x == 1) {
											filterArea = StringUtils.replace(filterArea, "*-", "ù");
											filterArea = StringUtils.replace(filterArea, "*", query_subject.getValue().getTable_alias() + gDirName);
											filterArea = StringUtils.replace(filterArea, "ù", "*-");
											allClauses = StringUtils.split(filterArea, ";");
										}
									}
									filterNameSpaceSource = "[FILTER_REF]";
								}
								//end filtre
								
								RelationShip RS = new RelationShip("[FINAL].[" + query_subject.getValue().getTable_alias() + "]" , "[REF].[" + rel.getPktable_alias()  + String.valueOf(i) + "]");
								// changer en qs + refobj
								String exp = rel.getRelationship();
								String fixedExp = StringUtils.replace(exp, "[REF].[" + rel.getPktable_alias() + "]", "[REF].[" + rel.getPktable_alias()  + String.valueOf(i) + "]");
								RS.setExpression(fixedExp);
								if (rel.isRightJoin())
								{
									RS.setCard_left_min("zero");
								} else {
									RS.setCard_left_min("one");
								}
								RS.setCard_left_max("many");
								
								if (rel.isLeftJoin())
								{
									RS.setCard_right_min("zero");
								} else {
									RS.setCard_right_min("one");
								}
								RS.setCard_right_max("one");
								RS.setParentNamespace("AUTOGENERATION");
								rsList.add(RS);
								
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
									
									fsvc.createQueryItemInFolder("[DATA].[" + query_subject.getValue().getTable_alias() + "]", gDirName, gFieldName + "." + field.getField_name(), filterNameSpaceSource + ".["+ pkAlias + String.valueOf(i) +"].[" + field.getField_name() + "]");
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
									if (field.isHidden())
									{
										fsvc.changeQueryItemProperty("[DATA].[" + query_subject.getValue().getTable_alias() + "].[" + gFieldName + "." + field.getField_name() + "]", "hidden", "true");
									}
									//end change
								}
								
								
								for(QuerySubject qs: qsList){
						        	recurseCount.put(qs.getTable_alias(), 0);
						        }
								
								f1(pkAlias, pkAlias + i, gDirName, "[DATA].[" + query_subject.getValue().getTable_alias() + "]", query_subject.getValue().getTable_alias(), recurseCount);

								//Modify Filters
								String QSPath = query_subject.getValue().getTable_alias() + gDirName;
								for(Entry<String, String> map: filterMap.entrySet()){
									String EntirePath = map.getKey();
									String filterReplace = map.getValue();
									if (filterReplace.contains("[REF]") && (QSPath.startsWith(EntirePath) || EntirePath.startsWith(QSPath))) {
										filterReplace = StringUtils.replace(filterReplace, "[REF].[" + pkAlias + "]", "[REF].[" + pkAlias + String.valueOf(i) + "]");
										filterMap.put(map.getKey(), filterReplace);
									}
								}
								//end Modify
								
							}
								
							//debut sec
							if(rel.isSec()) {
								String pkAlias = rel.getPktable_alias();
								
								fsvc.copyQuerySubject("[PHYSICALUSED]", "[PHYSICAL].[" + rel.getPktable_name() + "]");	
								fsvc.renameQuerySubject("[PHYSICALUSED].[" + rel.getPktable_name() + "]","SEC_" + query_subject.getValue().getTable_alias() + ".SEC." + pkAlias);
								fsvc.createQuerySubject("PHYSICALUSED", "SEC","SEC_" + query_subject.getValue().getTable_alias() + ".SEC." + pkAlias, query_subject.getValue().getTable_alias() + ".SEC." + pkAlias);

								RelationShip RS = new RelationShip("[FINAL].[" + query_subject.getValue().getTable_alias() + "]" , "[SEC].[" + query_subject.getValue().getTable_alias() + ".SEC." + pkAlias + "]");
								// changer en qs + refobj
								String exp = rel.getRelationship();
								String fixedExp = StringUtils.replace(exp, "[SEC].[" + rel.getPktable_alias() + "]", "[SEC].[" + query_subject.getValue().getTable_alias() + ".SEC." + pkAlias + "]");
								RS.setExpression(fixedExp);
								if (rel.isRightJoin())
								{
									RS.setCard_left_min("zero");
								} else {
									RS.setCard_left_min("one");
								}
								RS.setCard_left_max("many");
								
								if (rel.isLeftJoin())
								{
									RS.setCard_right_min("zero");
								} else {
									RS.setCard_right_min("one");
								}
								RS.setCard_right_max("one");
								RS.setParentNamespace("AUTOGENERATION");
								rsList.add(RS);
								
								String gDirName = "";
								
								//seq
								if(rel.getKey_type().equalsIgnoreCase("P") || rel.isNommageRep()){
									gDirName = "." + pkAlias;
								}
								else{
									gDirName = "." + rel.getSeqs().get(0).getColumn_name();
								}
								
								for(QuerySubject qs: qsList){
						        	recurseCount.put(qs.getTable_alias(), 0);
						        }
								
								f2(pkAlias, query_subject.getValue().getTable_alias() + ".SEC." + pkAlias, ".SEC" + gDirName, query_subject.getValue().getTable_alias(), recurseCount);
								
							}
							//fin sec
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
						if (field.isHidden())
						{
							fsvc.changeQueryItemProperty("[DATA].[" + query_subject.getValue().getTable_alias() + "].[" + field.getField_name() + "]", "hidden", "true");
							
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
				
				//Filters creation
				for(Entry<String, String> map: filterMap.entrySet()){
					
					String pathRefQS = filterMapApply.get(map.getKey());
					if (pathRefQS != null) {
					fsvc.createQuerySubjectFilter(pathRefQS , map.getValue());
					}
				}
				
				fsvc.addLocale(cognosLocales, cognosDefaultLocale);
	
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
					
					String namespaceName = "DATA";
					String spath = "/project/namespace/namespace";
					int k=1;
					
					Element namespace = (Element) document.selectSingleNode(spath + "[" + k + "]/name");			
					while(!namespace.getStringValue().equals(namespaceName) && namespace != null)
					{
					k++;
					namespace = (Element) document.selectSingleNode(spath + "[" + k + "]/name");
					}
					
					spath = spath + "[" + k + "]";
					fsvc.recursiveParserQS(document, spath, cognosLocales, labelMap);
	
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
				csvc = new CognosSVC(cognosDispatcher);
				csvc.setPathToXML(pathToXML);
				fsvc = new FactorySVC(csvc);
				csvc.logon(cognosLogin, cognosPassword, cognosNamespace);
				csvc.openModel(modelName, cognosFolder);
				fsvc.setLocale(cognosDefaultLocale);
				
				String[] locales = {cognosLocales};
				fsvc.changePropertyFixIDDefaultLocale();
//				fsvc.createPackage(modelName, modelName, modelName, locales);
//				fsvc.publishPackage(modelName,"/content");
				
				csvc.executeAllActions();
				
				csvc.saveModel();
				csvc.closeModel();
				csvc.logoff();
				
				
				System.out.println("Model Generation Finished");

				result.put("status", "OK");
				result.put("message", projectName + " published sucessfully.");
				result.put("troubleshooting", "");
				
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

	protected void f1(String qsAlias, String qsAliasInc, String gDirName, String qsFinal, String qsFinalName, Map<String, Integer> recurseCount) {
		
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
				
				//filtre
				String filterNameSpaceSource = "[REF]";
			//	String filterReset = "";
				if (!query_subjects.get(pkAlias + "Ref").getFilter().equals(""))
				{
					//traitement language filter DDtool -> Separé par ; = diffrentes clauses pour ce QSRef
					//séparé par :  Partie 0 du tableau = emplacement QS, Partie 1 = clause filtre
					// remplacer * par le chemin en cours dans l'emplacement et dans la clause.
					
					fsvc.createQuerySubject("REF", "FILTER_REF", pkAlias + String.valueOf(i), pkAlias + String.valueOf(i));
					
					String filterArea = query_subjects.get(pkAlias + "Ref").getFilter();
					String allClauses[] = StringUtils.split(filterArea, ";");
					
					Boolean exit = false;
					for (int x=0; x < 3 && !exit; x++) {
						for (int y=0; y < allClauses.length && !exit; y++) {
							if(allClauses[y].contains(":")) {
								String pathFilter[] = StringUtils.split(allClauses[y], ":");
								String pathRefQs = pathFilter[0].trim();
								String filterRefQs = pathFilter[1];
								//replace *-j dans lexpression du filtre
								String actualPath = qsFinalName + gDirNameCurrent;
								String actualPathTable[] = StringUtils.split(actualPath, ".");
								if(filterRefQs.contains("*-")){	
									Boolean rok = false;
									for (int z=1; z<100 && !rok;z++){
										if(filterRefQs.contains("*-" + String.valueOf(z))) {
											String pathReplace = "";
											for (int k=0;k < actualPathTable.length - z; k++) {
												pathReplace = pathReplace + actualPathTable[k];
												if (k != actualPathTable.length - z - 1) {
													pathReplace = pathReplace + ".";
												}
											}
											if(!filterRefQs.contains("*-")){
												rok = true;
											}
											filterRefQs = StringUtils.replace(filterRefQs, "*-" + String.valueOf(z), pathReplace);
										}
									}
								}
								//replace * dans l'expression du filtre
								filterRefQs = StringUtils.replace(filterRefQs, "*", qsFinalName + gDirNameCurrent);										
								if (pathRefQs.equals("[" + qsFinalName + gDirNameCurrent + "]")) {
									filterMap.put(qsFinalName + gDirNameCurrent, filterRefQs);
									//Set filter path dans ref pkalias + i correspondancies
									filterMapApply.put(qsFinalName + gDirNameCurrent, "[FILTER_REF].[" + pkAlias + String.valueOf(i) + "]");								
									exit=true;
								}
							}
						}
						//remmplacement %
						if(!exit && x == 0) {
							String actualPath = qsFinalName + gDirNameCurrent;
							for (int y=0; y < allClauses.length; y++) {
								if(allClauses[y].contains(":")) {
									String pathFilter[] = StringUtils.split(allClauses[y], ":");
									String pathRefQs = pathFilter[0].trim();
									String containPaths[] = StringUtils.split(pathRefQs, "%");
									if (containPaths.length == 2) {
										if (actualPath.startsWith(containPaths[0].substring(1))) {
											filterArea = StringUtils.replace(filterArea, pathRefQs, "[" + actualPath + "]");
										}
									} else if (containPaths.length == 3) {
										if (actualPath.contains(containPaths[1])) {
											filterArea = StringUtils.replace(filterArea, pathRefQs, "[" + actualPath + "]");
										}
									}
								}
							}
							allClauses = StringUtils.split(filterArea, ";");
						}
						//remplacement *
						if(!exit && x == 1) {
							filterArea = StringUtils.replace(filterArea, "*-", "ù");
							filterArea = StringUtils.replace(filterArea, "*", qsFinalName + gDirNameCurrent);
							filterArea = StringUtils.replace(filterArea, "ù", "*-");
							allClauses = StringUtils.split(filterArea, ";");
						}
					}
					filterNameSpaceSource = "[FILTER_REF]";
				}
				//end filtre
				
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
					
					fsvc.createQueryItemInFolder(qsFinal, gDirNameCurrent, gFieldName + "." + field.getField_name(), filterNameSpaceSource + ".["+ pkAlias + String.valueOf(i) +"].[" + field.getField_name() + "]");
					
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
					if (field.isHidden())
					{
						fsvc.changeQueryItemProperty(qsFinal + ".[" + gFieldName + "." + field.getField_name() + "]", "hidden", "true");
						
					}
					//end change
				}
				
				RelationShip RS = new RelationShip("[REF].[" + qsAliasInc + "]" , "[REF].[" + pkAlias + String.valueOf(i) + "]");
				// changer en qs + refobj
				String fixedExp = StringUtils.replace(rel.getRelationship(), "[REF].[" + qsAlias + "]", "[REF].[" + qsAliasInc + "]");
				fixedExp = StringUtils.replace(fixedExp, "[REF].[" + pkAlias + "]", "[REF].[" + pkAlias + String.valueOf(i) + "]");
				RS.setExpression(fixedExp);
				if (rel.isRightJoin())
				{
					RS.setCard_left_min("zero");
				} else {
					RS.setCard_left_min("one");
				}
				RS.setCard_left_max("many");

				if (rel.isLeftJoin())
				{
					RS.setCard_right_min("zero");
				} else {
					RS.setCard_right_min("one");
				}
				RS.setCard_right_max("one");
				RS.setParentNamespace("REF");
				rsList.add(RS);
				

				f1(pkAlias, pkAlias + String.valueOf(i), gDirNameCurrent, qsFinal, qsFinalName, copyRecurseCount);
				
				//Modify filters
				String QSPath = qsFinalName + gDirNameCurrent;
				for(Entry<String, String> map: filterMap.entrySet()){
					String EntirePath = map.getKey();
					String filterReplace = map.getValue();
					if (filterReplace.contains("[REF]") && (QSPath.startsWith(EntirePath) || EntirePath.startsWith(QSPath))) {
						filterReplace = StringUtils.replace(filterReplace, "[REF].[" + pkAlias + "]", "[REF].[" + pkAlias + String.valueOf(i) + "]");
						filterMap.put(map.getKey(), filterReplace);
					}
				}
				//end modify
				
			}
			if(rel.isSec()){
				
				String pkAlias = rel.getPktable_alias();
				String qsInitialName = qsFinalName + gDirName + ".SEC";
				
				fsvc.copyQuerySubject("[PHYSICALUSED]", "[PHYSICAL].[" + rel.getPktable_name() + "]");	
				fsvc.renameQuerySubject("[PHYSICALUSED].[" + rel.getPktable_name() + "]","SEC_" + qsInitialName + "." + pkAlias);
				fsvc.createQuerySubject("PHYSICALUSED", "SEC","SEC_" + qsInitialName + "." + pkAlias, qsInitialName + "." + pkAlias);

				RelationShip RS = new RelationShip("[REF].[" + qsAliasInc + "]" , "[SEC].[" + qsInitialName + "." + pkAlias + "]");
				// changer en qs + refobj
				String exp = rel.getRelationship();
				String fixedExp = StringUtils.replace(exp, "[REF].[" + qsAlias + "]", "[REF].[" + qsAliasInc + "]");
				fixedExp = StringUtils.replace(fixedExp, "[SEC].[" + rel.getPktable_alias() + "]", "[SEC].[" + qsInitialName + "." + pkAlias + "]");
				RS.setExpression(fixedExp);
				if (rel.isRightJoin())
				{
					RS.setCard_left_min("zero");
				} else {
					RS.setCard_left_min("one");
				}
				RS.setCard_left_max("many");
				
				if (rel.isLeftJoin())
				{
					RS.setCard_right_min("zero");
				} else {
					RS.setCard_right_min("one");
				}
				RS.setCard_right_max("one");
				RS.setParentNamespace("AUTOGENERATION");
				rsList.add(RS);
				
				String gDirNameSec = "";
				
				//seq
				if(rel.getKey_type().equalsIgnoreCase("P") || rel.isNommageRep()){
					gDirNameSec = "." + pkAlias;
				}
				else{
					gDirNameSec = "." + rel.getSeqs().get(0).getColumn_name();
				}
				
				Map<String, Integer> recurseCountSec = null;
				recurseCountSec = new HashMap<String, Integer>();
				for(QuerySubject qs: qsList){
		        	recurseCountSec.put(qs.getTable_alias(), 0);
		        }
				
				f2(pkAlias, qsInitialName + "." + pkAlias, gDirNameSec, qsInitialName, recurseCountSec);
				
			}
		}
	}

	protected void f2(String qsAlias, String qsAliasInc, String gDirName, String qsInitialName, Map<String, Integer> recurseCount){
		
		Map<String, Integer> copyRecurseCount = new HashMap<String, Integer>();
		copyRecurseCount.putAll(recurseCount);
		
		QuerySubject query_subject = query_subjects.get(qsAlias + "Sec");

		int j = copyRecurseCount.get(qsAlias);
		if(j == query_subject.getRecurseCount()){
			return;
		}
		copyRecurseCount.put(qsAlias, j + 1);

		for(Relation rel: query_subject.getRelations()){
			if(rel.isSec()){
				
				String pkAlias = rel.getPktable_alias();
				
				//seq
				String gDirNameCurrent = "";
				if(rel.getKey_type().equalsIgnoreCase("P") || rel.isNommageRep()){
					gDirNameCurrent = gDirName + "." + pkAlias;
							
				}
				else{
					gDirNameCurrent = gDirName + "." + rel.getSeqs().get(0).getColumn_name();
				}
				
				fsvc.copyQuerySubject("[PHYSICALUSED]", "[PHYSICAL].[" + rel.getPktable_name() + "]");	
				fsvc.renameQuerySubject("[PHYSICALUSED].[" + rel.getPktable_name() + "]","SEC_" + qsInitialName + gDirNameCurrent);
				fsvc.createQuerySubject("PHYSICALUSED", "SEC","SEC_" + qsInitialName + gDirNameCurrent, qsInitialName + gDirNameCurrent);

				
				RelationShip RS = new RelationShip("[SEC].[" + qsAliasInc + "]" , "[SEC].[" + qsInitialName + gDirNameCurrent + "]");
				// changer en qs + refobj
				String fixedExp = StringUtils.replace(rel.getRelationship(), "[SEC].[" + qsAlias + "]", "[SEC].[" + qsAliasInc + "]");
				fixedExp = StringUtils.replace(fixedExp, "[SEC].[" + pkAlias + "]", "[SEC].[" + qsInitialName + gDirNameCurrent + "]");
				RS.setExpression(fixedExp);
				if (rel.isRightJoin())
				{
					RS.setCard_left_min("zero");
				} else {
					RS.setCard_left_min("one");
				}
				RS.setCard_left_max("many");

				if (rel.isLeftJoin())
				{
					RS.setCard_right_min("zero");
				} else {
					RS.setCard_right_min("one");
				}
				RS.setCard_right_max("one");
				RS.setParentNamespace("SEC");
				rsList.add(RS);
				
				f2(pkAlias, qsInitialName + gDirNameCurrent, gDirNameCurrent, qsInitialName, copyRecurseCount);
				
			}
		}
	}
}
