package cn.wildfirechat.app;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:robot_context;DB_CLOSE_DELAY=-1"
})
public class ApplicationTests {

	@Test
	public void contextLoads() {
	}

}
