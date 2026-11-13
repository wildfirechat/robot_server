package cn.wildfirechat.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:robot_context;DB_CLOSE_DELAY=-1",
		"ROBOT_ADMIN_PASSWORD=ContextAdmin12345",
		"ROBOT_CONFIG_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
public class ApplicationTests {

	@Test
	public void contextLoads() {
	}

}
