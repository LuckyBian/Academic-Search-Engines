package hk.edu.hkbu.comp;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.IOException;

//The entrance of this processing

@SpringBootApplication
public class SearchEngineApplication {
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(SearchEngineApplication.class);
    //process Start here
    public static void main(String[] args) throws IOException, InterruptedException {
        // Start serving web
        SpringApplication.run(SearchEngineApplication.class, args);
    }
}
