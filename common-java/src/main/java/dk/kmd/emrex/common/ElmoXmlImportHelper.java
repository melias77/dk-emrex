package dk.kmd.emrex.common;

import java.util.ArrayList;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import lombok.extern.slf4j.Slf4j;
@Slf4j
public class ElmoXmlImportHelper {

    public ElmoDocument getDocument(Node report) throws Exception {
        ElmoDocument doc = new ElmoDocument();
        String firstName = getValueForTag(report.getParentNode(), "learner/givenNames");
        String lastName = getValueForTag(report.getParentNode(), "learner/familyName");
        String birthday = getValueForTag(report.getParentNode(), "learner/bday");
        
        doc.setBirthday(birthday);
        doc.setPersonName(firstName + " " + lastName);
        doc.setInstitutionName(getValueForTag(report, "issuer/title"));

        List<ElmoResult> results = new ArrayList<ElmoResult>();

        XPath xpath = XPathFactory.newInstance().newXPath();

        loopChildren(results, report, xpath, "learningOpportunitySpecification");

        doc.setResults(results);
        return doc;
    }

    private ElmoResult resultFromDegree(List<ElmoResult> results, Node los, XPath xpath) throws Exception {
        ElmoResult res = new ElmoResult();
        getLearningOpportunityValues(los, xpath, res);
        loopChildren(results, los, xpath, "hasPart/learningOpportunitySpecification");

        return res;
    }

    private ElmoResult resultFromModule(List<ElmoResult> results, Node los, XPath xpath) throws Exception {
        ElmoResult res = new ElmoResult();
        getLearningOpportunityValues(los, xpath, res);
        res.setResult(getValueForTag(los, "specifies/learningOpportunityInstance/resultLabel"));
        loopChildren(results, los, xpath, "hasPart/learningOpportunitySpecification");

        return res;
    }

    private void getLearningOpportunityValues(Node los, XPath xpath, ElmoResult res) throws Exception {
        res.setCode(getLocalContentValue(los, xpath, "identifier"));
        res.setType(getValueForTag(los, "type"));
        res.setLevel(getValueForTag(los, "specifies/learningOpportunityInstance/credit/level"));
        res.setName(getEnglishContentValue(los, xpath, "title"));
        res.setCredits(getCredits(los));
        res.setDate(getDate(los));

    }
    
    private String getCredits(Node los) throws Exception{
    	String credits = getValueForTag(los, "specifies/learningOpportunityInstance/credit/value");
    	try{
    		double d = (credits != null && !credits.isEmpty())? Double.parseDouble(credits) : 0.00; 
    		return String.format("%3.2f", d);
    	}catch(NumberFormatException nfe){
    		try{
    			double d = Double.parseDouble(credits.replace(",","."));
    			return String.format("%3.2f", d);
    		}catch(NumberFormatException e){
    			return String.format("%3.2f", 0.00);
    		}
    	}
    }

    private String getDate(Node los) throws Exception {

        String endDate;
        endDate = getValueForTag(los, "specifies/learningOpportunityInstance/date");
        if (endDate.isEmpty())
            endDate = getValueForTag(los, "specifies/learningOpportunityInstance/start");
        endDate = endDate.isEmpty()? endDate : endDate.replaceAll("Z$",""); 
        return endDate;
    }

    @SuppressWarnings("unused")
	private String getLevel(Node los, XPath xpath) throws Exception {
        String simpleLevel = getValueForTag(los, "level");
        if (!simpleLevel.isEmpty()) {
            return simpleLevel;
        } else {
            return getPreferredContentValue(los, xpath, "qualification/educationLevel", "xml:lang", "en");
        }
    }

    private void loopChildren(List<ElmoResult> results, Node los, XPath xpath, String path) throws Exception {
        NodeList topLevelLOS = (NodeList) xpath.evaluate(path, los,
                XPathConstants.NODESET);
        for (int i = 0; i < topLevelLOS.getLength(); i++) {
            Node node = topLevelLOS.item(i);
            Node type = (Node) xpath.evaluate("type", node, XPathConstants.NODE);

            if (type == null) {
                continue;
            }

            if (type.getTextContent().equalsIgnoreCase("degree")) {
                results.add(resultFromDegree(results, node, xpath));
            } else if (type.getTextContent().equalsIgnoreCase("module")) {
                results.add(resultFromModule(results, node, xpath));
            } else if (type.getTextContent().equalsIgnoreCase("module group")) {
                results.add(resultFromModule(results, node, xpath));
            } else if (type.getTextContent().equalsIgnoreCase("course")) {
                results.add(resultFromModule(results, node, xpath));
            }
        }
    }

    private String getValueForTag(Node node, String exp) {
        XPath xpath = XPathFactory.newInstance().newXPath();
        try {
            return xpath.evaluate(exp, node);
        } catch (Exception e) {
        	log.info("XPATH error", e);
            return null;
        }
    }

    private String getLocalContentValue(Node node, XPath xpath, String expr) throws Exception {
        return getPreferredContentValue(node, xpath, expr, "type", "local");
    }

    // returns english content, if not available any, if anything is not available we return default text
    private String getEnglishContentValue(Node node, XPath xpath, String expr) throws Exception {
        return getPreferredContentValue(node, xpath, expr, "xml:lang", "en");
    }

    private String getPreferredContentValue(Node node, XPath xpath, String expr, String itemName, String preferredValue) throws XPathExpressionException {
        NodeList nList = (NodeList) xpath.evaluate(expr, node, XPathConstants.NODESET);
        String content = " - ";

        for (int i = 0; i < nList.getLength(); i++) {
            Node n = (Node) nList.item(i);
            String possibleContent = n.getTextContent();
            if (possibleContent != null && !possibleContent.isEmpty()) {
                content = possibleContent;
            }
            NamedNodeMap map = n.getAttributes();
            Node item = map.getNamedItem(itemName);

            if (preferredValue.equals(item.getTextContent())) {
                return content;
            }
        }
        return content;
    }

}
