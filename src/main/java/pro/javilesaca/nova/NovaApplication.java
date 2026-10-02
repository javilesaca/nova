package pro.javilesaca.nova;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * NOVA: el asistente del portfolio.
 *
 * <p>Hito 1: RAG con citas por API. Sin cara todavía: primero que responda
 * bien, luego que sea vistosa (hito 2).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NovaApplication {

	public static void main(String[] args) {
		SpringApplication.run(NovaApplication.class, args);
	}

}
