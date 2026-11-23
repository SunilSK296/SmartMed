import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Standalone Zero-Dependency Test Runner.
 * Discovers and runs all @Test annotated methods across test suites,
 * enabling immediate test execution without external Maven or JUnit dependencies.
 */
public class TestRunner {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("            STANDALONE ZERO-DEPENDENCY TEST RUNNER                              ");
        System.out.println("================================================================================");

        Class<?>[] testClasses = new Class<?>[]{
                Indexed4AryMaxHeapTest.class,
                PriorityCalculatorTest.class,
                SchedulerTest.class
        };

        int total = 0;
        int passed = 0;
        int failed = 0;

        for (Class<?> testClass : testClasses) {
            System.out.println("\nRunning Test Suite: " + testClass.getSimpleName());
            System.out.println("--------------------------------------------------------------------------------");

            Object instance;
            try {
                instance = testClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                System.err.println("Could not instantiate " + testClass.getName() + ": " + e.getMessage());
                continue;
            }

            for (Method method : testClass.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers()) && method.getParameterCount() == 0 &&
                        (method.getName().startsWith("test") || method.isAnnotationPresent(org.junit.jupiter.api.Test.class))) {
                    total++;
                    try {
                        method.invoke(instance);
                        System.out.printf("  [PASS] %s\n", method.getName());
                        passed++;
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        System.out.printf("  [FAIL] %s - %s\n", method.getName(), cause.getMessage());
                        cause.printStackTrace(System.out);
                        failed++;
                    }
                }
            }
        }

        System.out.println("\n================================================================================");
        System.out.printf("TEST SUMMARY: Total: %d | Passed: %d | Failed: %d\n", total, passed, failed);
        System.out.println("================================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }
}
