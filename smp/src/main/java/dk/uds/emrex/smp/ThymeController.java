/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dk.uds.emrex.smp;

import dk.uds.emrex.smp.model.VerificationReply;
import dk.uds.emrex.smp.model.VerifiedReport;
import dk.uds.emrex.smp.saml2.WayfUser;
import dk.kmd.emrex.common.PersonalLogger;
import dk.kmd.emrex.common.elmo.ElmoParser;
import dk.kmd.emrex.common.model.Person;
import dk.kmd.emrex.common.util.ShibbolethHeaderHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.bind.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletRequest;
import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.json.simple.parser.ParseException;

/**
 * @author salum
 */
@Controller
@Slf4j
public class ThymeController {

    @Value("${emreg.url}")
    private String emregUrl;

    @Value("${smp.verification.threshold}")
    private double verificationThreshold;

    @Autowired
    private HttpServletRequest context;

    @Autowired
    private SignatureVerifier signatureVerifier;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String smp(HttpServletRequest request, Model model, @AuthenticationPrincipal WayfUser wayfUser) throws Exception {
        return smpsmp(request, model, wayfUser);
    }

    @RequestMapping(value = "/smp/", method = RequestMethod.GET)
    public String smpsmp(HttpServletRequest request, Model model, @AuthenticationPrincipal WayfUser wayfUser) throws Exception {
        String firstName = request.getHeader("shib-givenName");
        model.addAttribute("name", firstName);
        context.getSession().setAttribute("sessionStartTime", LocalDateTime.now());

        return "smp";
    }

    @RequestMapping(value = "/abort", method = RequestMethod.GET)
    public String abort(Model model, @AuthenticationPrincipal WayfUser wayfUser) throws Exception {
        model.addAttribute("url", getLinkToPolishQuestionnaire());
        return "onReturnAbort";
    }

    @RequestMapping(value = "/smp/abort", method = RequestMethod.GET)
    public String smpAbort(Model model, @AuthenticationPrincipal WayfUser wayfUser) throws Exception {
        return abort(model, wayfUser);
    }

    @RequestMapping(value = "/smp/onReturn", method = RequestMethod.POST)
    public String smponReturnelmo(@ModelAttribute ElmoData request,
            Model model,
            @CookieValue(value = "elmoSessionId") String sessionIdCookie,
            @CookieValue(value = "chosenNCP") String chosenNCP,
            @CookieValue(value = "chosenCert") String chosenCert,
            @AuthenticationPrincipal WayfUser wayfUser,
            HttpServletRequest httpRequest) throws Exception {
        return this.onReturnelmo(request, model, sessionIdCookie, chosenNCP, chosenCert, wayfUser, httpRequest);
    }

    @RequestMapping(value = "/onReturn", method = RequestMethod.POST)
    public String onReturnelmo(@ModelAttribute ElmoData request,
            Model model,
            @CookieValue(value = "elmoSessionId") String sessionIdCookie,
            @CookieValue(value = "chosenNCP") String chosenNCP,
            @CookieValue(value = "chosenCert") String chosenCert,
            @AuthenticationPrincipal WayfUser wayfUser,
            HttpServletRequest httpRequest) throws Exception {
        String sessionId = request.getSessionId();
        String elmo = request.getElmo();

        Person person = (Person) context.getSession().getAttribute("shibPerson");

        if (person == null) {
            ShibbolethHeaderHandler headerHandler = new ShibbolethHeaderHandler(httpRequest);
            log.debug(headerHandler.stringifyHeader());
            person = headerHandler.generatePerson();
            context.getSession().setAttribute("shibPerson", person);
        }

        String source = "SMP";
        String personalLogLine = generatePersonalLogLine(httpRequest, person, source);

        if (elmo == null) {
            PersonalLogger.log(personalLogLine + "\tfailed");
            return abort(model, wayfUser);
        }
        String ncpPubKey = this.getCertificate(chosenNCP);
        String locPubKey =this.getCertificate();
        String difference = StringUtils.difference(ncpPubKey, locPubKey );
        String diff2 = StringUtils.difference(locPubKey , ncpPubKey);
        log.debug("certdiff: " + difference.length() + "\n" +difference);
        log.debug("diff2 " + diff2.length()+ "\n" +diff2);
        final byte[] bytes = DatatypeConverter.parseBase64Binary(elmo);
        final String decodedXml = GzipUtil.gzipDecompress(bytes);
        final boolean verifySignatureResult = signatureVerifier.verifySignatureWithDecodedData(ncpPubKey, decodedXml, StandardCharsets.UTF_8);

        log.info("Verify signature result: {}", verifySignatureResult);
        log.info("providedSessionId: {}", sessionId);

        try {
            DkSmpApplication.verifySessionId(sessionId, sessionIdCookie);
        } catch (Exception e) {
            log.error("Session verification failed", e);
            model.addAttribute("error", "Session verification failed");
            PersonalLogger.log(personalLogLine + "\tfailed");
            return "error";
        }
        try {
            if (!verifySignatureResult) {
                log.error("NCP signature check failed");
                model.addAttribute("error", "NCP signature check failed");
                PersonalLogger.log(personalLogLine + "\tfailed");
                return "error";
            }
        } catch (Exception e) {
            log.error("NCP verification failed", e);
            model.addAttribute("error", "NCP verification failed");
            PersonalLogger.log(personalLogLine + "\tfailed");
            return "error";
        }

        log.info("Returned elmo XML " + decodedXml);
        context.getSession().setAttribute("elmoxmlstring", decodedXml);
        ElmoParser parser = ElmoParser.elmoParser(decodedXml);
        try {
            byte[] pdf = parser.getAttachedPDF();
            context.getSession().setAttribute("pdf", pdf);
        } catch (Exception e) {
            log.error("EMREX transcript missing.");
            model.addAttribute("error", "EMREX transcript missing.");
            PersonalLogger.log(personalLogLine + "\tfailed");
            return "error";
        }
        model.addAttribute("elmoXml", decodedXml);

        Document document;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        //Get the DOM Builder
        DocumentBuilder builder;
        if (person != null) {
            List<VerifiedReport> results = new ArrayList<>();
            try {

                builder = factory.newDocumentBuilder();
                StringReader sr = new StringReader(decodedXml);
                final InputSource inputSource = new InputSource();
                inputSource.setEncoding(StandardCharsets.UTF_8.name());
                inputSource.setCharacterStream(sr);
                //person.setFirstName("test"); person.setLastName("user");
                //person.setHomeOrganizationName("test institution");
                //Load and Parse the XML document
                //document contains the complete XML as a Tree.
                document = builder.parse(inputSource);
                NodeList reports = document.getElementsByTagName("report");
                for (int i = 0; i < reports.getLength(); i++) {
                    VerifiedReport vr = new VerifiedReport();
                    Element report = (Element) reports.item(i);
                    vr.setReport(nodeToString(report));
                    Person elmoPerson = getUserFromElmoReport((Element) report.getParentNode());

                    if (elmoPerson != null) {
                        VerificationReply verification = VerificationReply.verify(person, elmoPerson, verificationThreshold);
                        log.info("Verification messages: " + verification.getMessages());
                        log.info("VerScore: " + verification.getScore());

                        vr.setVerification(verification);

                    } else {
                        vr.addMessage("Elmo learner missing");
                        //TODO fix this
                    }
                    results.add(vr);
                }
                context.getSession().setAttribute("reports", results);
                model.addAttribute("reports", results);

            } catch (ParserConfigurationException | IOException | SAXException ex) {
                log.error("Error in report verification", ex);
                model.addAttribute("error", ex.getMessage());
                PersonalLogger.log(personalLogLine + "\tfailed");
                return "error";
            }
        } else {
            model.addAttribute("error", "HAKA login missing");
            PersonalLogger.log(personalLogLine + "\tfailed");
            return "error";
        }
        PersonalLogger.log(personalLogLine + "\tokay");
        return "review";
    }

    private String generatePersonalLogLine(HttpServletRequest httpRequest, Person person, String source) throws Exception {
        String personalLogLine = source + "\t" + person.getFullName();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDateTime startTime = (LocalDateTime) context.getSession().getAttribute("sessionStartTime");
        if (startTime == null) {
            startTime = LocalDateTime.now();
        }
        personalLogLine += "\t" + startTime.format(dateFormatter);

        String url = httpRequest.getHeader("Referer");
        String NCPDomain = "";
        if (url != null) {
            URI uri = new URI(url);
            NCPDomain = uri.getHost();
        }

        personalLogLine += "\t" + NCPDomain;
        personalLogLine += "\t" + httpRequest.getParameter("returnCode");
        return personalLogLine;
    }

    // FIXME serti jostain muualta //TODO Flyt certifikat fra klassen.  
    private String getCertificate() {
        return "-----BEGIN CERTIFICATE-----\n"
                + "MIIB+TCCAWICCQDiZILVgSkjojANBgkqhkiG9w0BAQUFADBBMQswCQYDVQQGEwJG\n"
                + "STERMA8GA1UECAwISGVsc2lua2kxETAPBgNVBAcMCEhlbHNpbmtpMQwwCgYDVQQK\n"
                + "DANDU0MwHhcNMTUwMjA1MTEwNTI5WhcNMTgwNTIwMTEwNTI5WjBBMQswCQYDVQQG\n"
                + "EwJGSTERMA8GA1UECAwISGVsc2lua2kxETAPBgNVBAcMCEhlbHNpbmtpMQwwCgYD\n"
                + "VQQKDANDU0MwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAMyVVTyGT1Cp8z1f\n"
                + "jYEO93HEtIpFKnb/tvPb6Ee5b8m8lnuv6YWsF8DBWPVfsOq0KCWD8zE1yD+w+xxM\n"
                + "mp6+zATp089PUrEUYawG/tGu9OG+EX+nhOAj0SBvGHEkXh6lGJgeGxbdFVwZePAN\n"
                + "135ra5L3gYcwYBVOuEyYFZJp7diHAgMBAAEwDQYJKoZIhvcNAQEFBQADgYEAP2E9\n"
                + "YD7djCum5UYn1Od9Z1w55j+SuKRWMnTR3yzy1PXJjb2dGqNcV9tEhdbqWbwTnNfl\n"
                + "6sidCnd1U0p4XdLjg28me8ZmfftH+QU4LkwSFSyF4ajoTFC3QHD0xTtGpQIT/rAD\n"
                + "x/59fhfX5icydMzzNulwXJWImtXq2/AX43/yR+M=\n"
                + "-----END CERTIFICATE-----";
    }

    private String getCertificate(String chosenNCP) {
        try {
            log.debug("chosenNCP: " + chosenNCP);
            List<NCPResult> ncps = DkSmpApplication.getNCPs(emregUrl);
            for (NCPResult ncp : ncps) {

                if (chosenNCP.equals(ncp.getUrl())) {
                    log.debug("ncpUrl: " + ncp.getUrl());
                    log.debug(ncp.getCertificate());
                    return ncp.getCertificate();
                }
            }
        } catch (ParseException | URISyntaxException ex) {
            log.error(ex.getMessage());
        }
        log.error("No certificat for chosenNCP: " +chosenNCP);
        return null;
    }

    private Person getUserFromElmoReport(Element report) {

        Element learner = getOneNode(report, "learner");
        if (learner != null) {
            log.debug("Learner found");
            Person elmoPerson = new Person();
            elmoPerson.setFirstName(getOneNode(learner, "givenNames").getTextContent());
            elmoPerson.setLastName(getOneNode(learner, "familyName").getTextContent());
            Element bday = getOneNode(learner, "bday");
            if (bday != null) {
                elmoPerson.setBirthDate(bday.getTextContent(), bday.getAttribute("dtf"));
            }

            return elmoPerson;

        } else {
            log.error("No learner found");
            return null;
        }

    }

    private Element getOneNode(Element node, String name) {
        NodeList list = node.getElementsByTagName(name);
        if (list.getLength() == 1) {
            log.trace("Found {}", name);
            return (Element) list.item(0);
        } else {
            log.trace("No {} found. Returning null", name);
            return null;
        }
    }

    private String nodeToString(Node node) {
        StringWriter sw = new StringWriter();
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            t.transform(new DOMSource(node), new StreamResult(sw));
        } catch (TransformerException te) {
            log.error("NodeToString Transformer Exception", te);
        }
        return sw.toString();
    }

    private String getLinkToPolishQuestionnaire() throws Exception {
        QuestionnaireLinkBuilder linkBuilder = new QuestionnaireLinkBuilder();
        linkBuilder.setContext(context);
        return linkBuilder.buildLink();
    }
}
