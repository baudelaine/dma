package com.dma.svc;

import java.io.File;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import com.cognos.developer.schemas.bibus._3.BiBusHeader;
import com.cognos.developer.schemas.bibus._3.XmlEncodedXML;
import com.cognos.org.apache.axis.client.Stub;
import com.cognos.org.apache.axis.message.SOAPHeaderElement;
import com.dma.cognos.CRNConnect;
import com.dma.properties.ConfigProperties;
import com.dma.web.QuerySubject;

import sapphire.util.Logger;

public class CognosSVC {

	public static CRNConnect crnConnect;
	private static Map<String, Element> actionsMap;
	private static int i;

	static {
		crnConnect = new CRNConnect();
		crnConnect.connectToCognosServer();
		actionsMap = new HashMap<String, Element>();
		i=1;
	}
	
	public Document doc = null;

	public static boolean logon() {
		try {
			StringBuilder credentialXML = new StringBuilder();

			credentialXML.append("<credential>");
			credentialXML.append("<namespace>").append(ConfigProperties.nm).append("</namespace>");
			credentialXML.append("<username>").append(ConfigProperties.login).append("</username>");
			credentialXML.append("<password>").append(ConfigProperties.pwd).append("</password>");
			credentialXML.append("</credential>");

			String encodedCredentials = credentialXML.toString();

			crnConnect.getCMService().logon(new XmlEncodedXML(encodedCredentials), null);

			// TODO Set the BiBusHeader
			SOAPHeaderElement temp = ((Stub) crnConnect.getCMService())
					.getResponseHeader("http://developer.cognos.com/schemas/bibus/3/", "biBusHeader");
			BiBusHeader cmBiBusHeader = (BiBusHeader) temp
					.getValueAsType(new QName("http://developer.cognos.com/schemas/bibus/3/", "biBusHeader"));
			((Stub) crnConnect.getCMService()).setHeader("http://developer.cognos.com/schemas/bibus/3/", "biBusHeader",
					cmBiBusHeader);
			System.out.println("Logon successful as " + ConfigProperties.login);

		} catch (RemoteException ex) {
			lg(ex.getMessage());
			ex.printStackTrace();
		} catch (Exception ex) {
			lg(ex.getMessage());
		}
		return true;

	}

	public static void logoff() {

		try {
			crnConnect.getCMService().logoff();
			System.out.println("logoff");
		} catch (RemoteException ex) {
			lg(ex.getMessage());
			ex.printStackTrace();
		}

	}

	public static void executeModel(Document D) {
		try {
			File rootFile = new File(ConfigProperties.PathToXML + "/executeModel.xml");

			SAXReader reader = new SAXReader();
			Document scriptDocument = D;

			Document rootDocument = reader.read(rootFile);
			Element root = rootDocument.getRootElement();

			Node node = rootDocument.selectSingleNode("//@model");
			node.setText(ConfigProperties.model);

			// 1/ remove root
			List<Node> nodes = scriptDocument.selectNodes("//transaction/action");
			
			
			for (Node n : nodes) {

				Element e = (Element) n.detach();
				
				actionsMap.put(String.valueOf(i), e);
				i++;
				
				XmlEncodedXML xex = new XmlEncodedXML(root.asXML());
				
			}
		} catch (DocumentException ex) {
			lg(ex.getMessage());
			ex.printStackTrace();
			TaskerSVC.stop();   // ajout test Nico
			System.exit(0);
		} 

	}

	public static void executeAllActions() {
		try {
			File rootFile = new File(ConfigProperties.PathToXML + "/executeModel.xml");

			SAXReader reader = new SAXReader();

			Document rootDocument = reader.read(rootFile);
			Element root = rootDocument.getRootElement();

			Node node = rootDocument.selectSingleNode("//@model");
			node.setText(ConfigProperties.model);
			
			int j=1;
			List<Element> lst = new ArrayList<Element>();
			root.addElement("transaction");
			Element t = (Element) root.selectSingleNode("//transaction");
			
			while (actionsMap.get(String.valueOf(j)) != null) {

				Element e = actionsMap.get(String.valueOf(j));
				e.addAttribute("seq", String.valueOf(j));
				lst.add(e);
				j++;
				
			}
			
			t.setContent(lst);
			// t.addAttribute("commit", "y");   //pour balise transaction
			XmlEncodedXML xex = new XmlEncodedXML(root.asXML());
//			System.out.println(root.asXML());
			String res = crnConnect.getMetadataService().updateMetadata(xex).toString();
			
		} catch (DocumentException ex) {
			lg(ex.getMessage());
			ex.printStackTrace();
			TaskerSVC.stop();   // ajout test Nico
			System.exit(0);
		} catch (RemoteException ex) {
			lg(ex.getMessage());
			ex.printStackTrace();
			TaskerSVC.stop();    // ajout test Nico
			System.exit(0);
		}

	}

	
	public static void lg(String msg) {
		Logger.logInfo(" BuildModel.java ", msg);
	}
}
