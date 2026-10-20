package app.homeward

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

// Base class for tests that run the whole app against the real test database.
@SpringBootTest
abstract class IntegrationTest {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) = TestDatabase.registerAppDataSource(registry)
    }
}
