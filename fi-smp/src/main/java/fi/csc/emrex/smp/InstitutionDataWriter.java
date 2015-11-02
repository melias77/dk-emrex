package fi.csc.emrex.smp;

import fi.csc.emrex.smp.model.Person;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by jpentika on 02/11/15.
 */
public class InstitutionDataWriter {

    @Value("${smp.university.directory.map}")
    String dirMap;

    @Value("${smp.university.base.directory}")
    String pdfBaseDir;

    private Person user;


    public InstitutionDataWriter(Person user) {
        this.user = user;
    }

    public void writeDataToInstitutionFolder(byte[] bytePDF, String fileType) {

        createPath();
        writeToFile(bytePDF, fileType);
    }

    private void writeToFile(byte[] bytePDF, String fileType) {
        try (FileOutputStream fos = new FileOutputStream(generatePath() + "/" + generateFileName() + fileType)) {
            fos.write(bytePDF);
        } catch (IOException ioe) {
            Logger.getLogger(JsonController.class.getName()).log(Level.SEVERE, null, ioe);
            ioe.printStackTrace();
        }
    }

    private String generateFileName() {
        String filename = "emrex_";
        filename += user.getFirstName() + "_" + user.getLastName() + "_";
        filename += new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + "_";
        return filename;
    }

    private void createPath()    {
        String path = generatePath();
        new File(path).mkdirs();
    }

    private String generatePath() {
        String dirname = this.pdfBaseDir;
        try {

            String json = FileUtils.readFileToString(new File(dirMap), "UTF-8");
            JSONObject root = (JSONObject)JSONValue.parse(json);

            String home = (String) root.get(user.getHomeOrganization());
            if (home != null)
                dirname += home;
            else
                dirname += "unknown_organization";

        } catch (Exception ex) {
            Logger.getLogger(JsonController.class.getName()).log(Level.SEVERE, null, ex);
        }
        return dirname;
    }
}
