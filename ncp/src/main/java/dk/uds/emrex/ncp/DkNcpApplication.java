package dk.uds.emrex.ncp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootApplication
@Configuration
@ComponentScan
@EnableAutoConfiguration
public class DkNcpApplication {

    private static final String ELMO_XML_FIN = "src/main/resources/Example-elmo-Finland.xml";
    private static final String ELMO_XML_NOR = "src/main/resources/Example-elmo-Norway.xml";
    private static final String ELMO_XML_FIN_URL = "https://raw.githubusercontent.com/EMREXEU/fi-ncp/master/src/main/resources/Example-elmo-Finland.xml";
    private static final String ELMO_XML_SWE = "src/main/resources/Example-elmo-Sweden-1.0.xml";
    private static final String ELMO_XML_NOR_10 = "src/main/resources/nor-emrex-1.0.xml";
    private static final String ELMO_XML_KAISA = "src/main/resources/kaisa.xml";
    public static String getElmo() throws Exception {
        return new String(Files.readAllBytes(Paths.get(new File(ELMO_XML_KAISA).getAbsolutePath())));
    }

    public static void main(String[] args) {
        SpringApplication.run(DkNcpApplication.class, args);
    }

    @Autowired
    private ResourceLoader resourceLoader;

    @Bean
    public StudyFetcher studyFetcher() {
        return new StudyFetcher() {
            @Cacheable
            @Override
            public String fetchStudies(String ssn) throws IOException {
                try (InputStream resourceStream = resourceLoader.getResource("classpath:/kaisa.xml").getInputStream()) {
                    return StreamUtils.copyToString(resourceStream, Charset.forName("UTF-8"));
                }
            }
        };
    }
}
