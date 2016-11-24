package dk.kmd.emrex.common.elmo;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.github.ooxi.jdatauri.DataUri;

import dk.kmd.emrex.common.elmo.jaxb.Util;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ElmoParser {
	private Document document;

	private ElmoParser(String elmo) throws ParserConfigurationException, SAXException, IOException {
		this.document = buildDOM(elmo);
	}

	private static Document buildDOM(String elmo) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		StringReader sr = new StringReader(elmo);
		InputSource s = new InputSource(sr);
		return builder.parse(s);
	}

	/**
	 * Creates a dom model of elmo xml
	 *
	 * @param elmo
	 */
	public static ElmoParser elmoParser(String elmo) throws Exception {
		return new ElmoParser(elmo);
	}

	/**
	 * Creates a dom model of elmo xml and adds elmo identifiers to courses and
	 * flattens the learning opportunity specification hierarchy
	 *
	 * @param elmo
	 */
	public static ElmoParser elmoParserFromVirta(String elmo) throws Exception {
		String betterElmo = Util.virtaJAXBParser(elmo);

		ElmoParser parser = new ElmoParser(betterElmo);
		// parser.addElmoIdentifiers();
		// parser.flattenLearningOpportunityHierarchy();
		// parser.document.normalizeDocument();
		Element documentElement = parser.document.getDocumentElement();
		if (null == documentElement) {
			log.debug("document elemnt null");
		} else {
			log.debug(documentElement.getTagName());
		}
		return parser;

	}

	public byte[] getAttachedPDF() throws Exception {
		NodeList elmos = document.getElementsByTagName("elmo");
		if (elmos.getLength() > 0) {

			Element elmo = (Element) elmos.item(0);
			NodeList attachments = elmo.getElementsByTagName("attachment");
			log.debug(attachments.getLength() + " attachments found");
			for (int i = 0; i < attachments.getLength(); i++) {
				// NodeList childs = attachments.item(0).getChildNodes();
				Element attachment = (Element) attachments.item(i);
				if (attachment.getParentNode().equals(elmo)) {
					NodeList types = attachment.getElementsByTagName("type");
					Element type = (Element) types.item(0);
					if (type != null) {
						if ("EMREX transcript".equals(type.getTextContent())) { // need
																				// to
																				// check
																				// for
																				// "Emrex
																				// trenscript"

							NodeList content = attachment.getElementsByTagName("content");

							for (int j = 0; j < content.getLength(); j++) {

								// log.debug(content.item(j).getTextContent());
								DataUri parse = DataUri.parse(content.item(j).getTextContent(),
										Charset.forName("UTF-8"));
								if ("application/pdf".equals(parse.getMime())) {
									return parse.getData();
								}
								// return
								// DatatypeConverter.parseBase64Binary(content.item(0).getTextContent());
							}
						}
					}
					throw new Exception("no content attachment in elmo in  xml");
				}

			}
			throw new Exception("no attachments in elmo in  xml");
		}
		throw new Exception("No elmo in xml");
	}

	public void addPDFAttachment(byte[] pdf) {
		NodeList elmos = document.getElementsByTagName("elmo");
		if (elmos.getLength() > 0) {

			// remove existing attachments to avoid duplicates
			NodeList removeNodes = document.getElementsByTagName("attachment");
			for (int i = 0; i < removeNodes.getLength(); i++) {
				Node parent = removeNodes.item(i).getParentNode();
				if (parent != null) {
					parent.removeChild(removeNodes.item(i));
				}
			}

			// Add pdf attachment
			Element attachment = document.createElement("attachment");

			Element title = document.createElement("title");
			// title.setAttribute("xml:lang", "en");
			title.setTextContent("EMREX transcript");
			Attr langAttribute = document.createAttributeNS("http://www.w3.org/XML/1998/namespace", "lang");
			langAttribute.setTextContent("en");
			title.setAttributeNode(langAttribute);
			attachment.appendChild(title);

			Element type = document.createElement("type");
			type.setTextContent("EMREX transcript");
			attachment.appendChild(type);

			String data = "data:application/pdf;base64," + DatatypeConverter.printBase64Binary(pdf);
			Element content = document.createElement("content");
			content.setTextContent(data);
			attachment.appendChild(content);
			elmos.item(0).appendChild(attachment); // we assume that only one
													// report exists
		}
	}

	/**
	 * Complete XML of found Elmo
	 *
	 * @return String representation of Elmo-xml
	 * @throws ParserConfigurationException
	 */
	public String getCourseData() throws ParserConfigurationException {
		return getStringFromDoc(document);
	}

	/**
	 * Elmo with a learning instance selection removes all learning
	 * opportunities not selected even if a learning opprtunity has a child that
	 * is among the selected courses.
	 *
	 * @param courses
	 * @return String representation of Elmo-xml with selected courses
	 * @throws ParserConfigurationException
	 */
	public String getCourseData(List<String> courses) throws ParserConfigurationException {
		String copyElmo = getStringFromDoc(document);
		return Util.getCourses(copyElmo, courses);
	}

	public int getETCSCount() throws Exception {
		HashMap<String, Integer> result = new HashMap<>();
		XPath xpath = XPathFactory.newInstance().newXPath();
		XPathExpression learningOpportunityExpression = xpath.compile("//learningOpportunitySpecification");
		NodeList learningOpportunities = (NodeList) learningOpportunityExpression.evaluate(document,
				XPathConstants.NODESET);
		for (int i = 0; i < learningOpportunities.getLength(); i++) {
			String type = "undefined";
			Element opportunitySpecification = (Element) learningOpportunities.item(i);
			NodeList types = opportunitySpecification.getElementsByTagName("type");
			for (int j = 0; j < types.getLength(); j++) {
				if (types.item(j).getParentNode() == opportunitySpecification) {
					type = types.item(j).getTextContent();
				}
			}

			Integer credits = 0;
			// XPathExpression valueExpression =
			// xpath.compile("//specifies/learningOpportunityInstance/credit");
			// Element credit = (Element) valueExpression.evaluate(
			// XPathConstants.NODE);
			List<Element> specifications = this
					.toElementList(opportunitySpecification.getElementsByTagName("specifies"));
			for (Element spec : specifications) {
				if (opportunitySpecification.equals(spec.getParentNode())) {
					List<Element> instances = this
							.toElementList(spec.getElementsByTagName("learningOpportunityInstance"));
					for (Element instance : instances) {
						if (spec.equals(instance.getParentNode())) {
							List<Element> creditElemnets = this.toElementList(spec.getElementsByTagName("credit"));
							for (Element credit : creditElemnets) {
								NodeList schemes = credit.getElementsByTagName("scheme");
								for (int j = 0; j < schemes.getLength(); j++) {
									Node scheme = schemes.item(j);
									if ("ects".equalsIgnoreCase(scheme.getTextContent())) {
										Node item = credit.getElementsByTagName("value").item(0);
										if (item != null) {
											String valueContent = item.getTextContent();
											double doubleValue = Double.parseDouble(valueContent);
											// log.debug(type + " double: " +
											// doubleValue);
											credits = (int) doubleValue;
											// log.debug(type + " int: " +
											// credits);
											if (result.containsKey(type)) {
												credits += result.get(type);
												result.replace(type, credits);
											} else {
												result.put(type, credits);
											}
										}

									}

								}
							}
						}
					}
				}

			}
		}
		// lets take biggest number by type so same numbers are not counted
		// several times
		int count = 0;
		for (Map.Entry<String, Integer> entry : result.entrySet()) {
			// log.debug(entry.toString());
			if (entry.getValue() > count) {
				count = entry.getValue().intValue();
			}
		}
		return count;
	}

	public int getCoursesCount() throws Exception {
		int result = 0;
		XPath xpath = XPathFactory.newInstance().newXPath();
		XPathExpression learningOpportunityExpression = xpath.compile("//learningOpportunitySpecification");
		NodeList learningOpportunities = (NodeList) learningOpportunityExpression.evaluate(document,
				XPathConstants.NODESET);
		for (int i = 0; i < learningOpportunities.getLength(); i++) {
			String type = "undefined";
			NodeList types = ((Element) learningOpportunities.item(i)).getElementsByTagName("type");
			for (int j = 0; j < types.getLength(); j++) {
				if (types.item(j).getParentNode() == learningOpportunities.item(i)) {
					type = types.item(j).getTextContent();
				}
			}

			if (type.toLowerCase().equals("module")) {
				result++;
			}
		}
		return result;
	}

	public String getHostInstitution() {

		String hostInstitution = "unknown";
		NodeList reports = document.getElementsByTagName("report");
		if (reports.getLength() == 1) {
			NodeList issuers = ((Element) reports.item(0)).getElementsByTagName("issuer");
			if (issuers.getLength() == 1) {
				NodeList titles = ((Element) issuers.item(0)).getElementsByTagName("identifier");
				for (int i = 0; i < titles.getLength(); i++) {
					Element title = (Element) titles.item(i);
					String type = title.getAttribute("type").toLowerCase();
					hostInstitution = titles.item(i).getTextContent();
					if (type.equals("schac")) {
						log.info("instution identifier type schac");
						return hostInstitution;
					}
				}
			}
		}
		return hostInstitution;
	}

	public String getHostCountry() {

		String hostCountry = "unknown";
		NodeList reports = document.getElementsByTagName("report");
		if (reports.getLength() == 1) {
			NodeList issuers = ((Element) reports.item(0)).getElementsByTagName("issuer");
			if (issuers.getLength() == 1) {
				NodeList titles = ((Element) issuers.item(0)).getElementsByTagName("country");
				for (int i = 0; i < titles.getLength(); i++) {
					Element title = (Element) titles.item(i);
					hostCountry = title.getTextContent();
				}
			}
		}

		return hostCountry;
	}

	private List<Element> toElementList(NodeList nodeList) {
		List<Element> list = new ArrayList<Element>();
		for (int i = 0; i < nodeList.getLength(); i++) {
			try {
				list.add((Element) nodeList.item(i));
			} catch (ClassCastException cce) {
				log.trace(cce.getMessage());
			}
		}
		return list;
	}

	private static String getStringFromDoc(org.w3c.dom.Document doc) {
		// this.sortReport(doc);
		DOMImplementationLS domImplementation = (DOMImplementationLS) doc.getImplementation();
		LSSerializer lsSerializer = domImplementation.createLSSerializer();

		LSOutput lsOutput = domImplementation.createLSOutput();
		lsOutput.setEncoding(StandardCharsets.UTF_8.name());
		Writer stringWriter = new StringWriter();
		lsOutput.setCharacterStream(stringWriter);
		lsSerializer.write(doc, lsOutput);
		return stringWriter.toString();
	}
}
