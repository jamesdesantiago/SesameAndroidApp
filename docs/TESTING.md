# Testing Documentation

## Overview

The Sesame Android application implements a comprehensive testing strategy covering unit tests,
integration tests, UI tests, and performance tests. This document outlines the testing approach,
frameworks, and best practices.

## Testing Strategy

### 1. Test Pyramid

#### Unit Tests (70%)
- Business logic
- Use cases
- ViewModels
- Repositories
- Utilities

#### Integration Tests (20%)
- Repository implementations
- Data sources
- Network layer
- Database operations

#### UI Tests (10%)
- Screen navigation
- User interactions
- UI state management
- Accessibility

### 2. Test Categories

#### Unit Tests
```kotlin
class PlaceRepositoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    private lateinit var remoteDataSource: PlaceRemoteDataSource

    @MockK
    private lateinit var localDataSource: PlaceLocalDataSource

    @Inject
    private lateinit var repository: PlaceRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `when getPlaces is called, returns cached data if available`() {
        // Given
        val cachedPlaces = listOf(createTestPlace())
        whenever(cacheManager.getCachedData<List<Place>>(CACHE_KEY_PLACES))
            .thenReturn(cachedPlaces)

        // When
        val result = repository.getPlaces(PaginationParams())

        // Then
        assertTrue(result.isSuccess)
        assertEquals(cachedPlaces, result.getOrNull())
    }
}
```

#### Integration Tests
```kotlin
@RunWith(AndroidJUnit4::class)
class PlaceRepositoryIntegrationTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: PlaceRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = PlaceRepositoryImpl(
            remoteDataSource = PlaceRemoteDataSourceImpl(apiService),
            localDataSource = PlaceLocalDataSourceImpl(database.placeDao()),
            mapper = PlaceMapper(),
            logger = Logger()
        )
    }

    @Test
    fun `when savePlace is called, saves to both local and remote`() {
        // Given
        val place = createTestPlace()

        // When
        runBlocking {
            repository.savePlace(place)
        }

        // Then
        val savedPlace = database.placeDao().getPlaceById(place.id)
        assertNotNull(savedPlace)
        assertEquals(place.name, savedPlace.name)
    }
}
```

#### UI Tests
```kotlin
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `when screen loads, displays loading state then places`() {
        // Given
        val viewModel = mockk<HomeViewModel>()
        whenever(viewModel.state).thenReturn(
            flowOf(
                HomeState(isLoading = true),
                HomeState(places = listOf(createTestPlace()))
            )
        )

        // When
        composeTestRule.setContent {
            SesameTheme {
                HomeScreen(viewModel)
            }
        }

        // Then
        composeTestRule.onNodeWithText("Loading")
            .assertIsDisplayed()
            .assertExists()

        composeTestRule.onNodeWithText("Place Name")
            .assertIsDisplayed()
            .assertExists()
    }
}
```

## Testing Frameworks

### 1. Unit Testing
- **JUnit**: Test framework
- **MockK**: Mocking framework
- **Kotlin Coroutines Test**: Coroutine testing
- **Truth**: Assertion library

### 2. Integration Testing
- **Room Testing**: Database testing
- **Retrofit Testing**: Network testing
- **Hilt Testing**: DI testing
- **WorkManager Testing**: Background work testing

### 3. UI Testing
- **Compose Testing**: UI testing
- **Accessibility Testing**: A11y testing
- **Screenshot Testing**: Visual testing
- **Performance Testing**: UI performance

## Test Utilities

### 1. Test Rules
```kotlin
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

### 2. Test Extensions
```kotlin
fun <T> Flow<T>.test(
    scope: CoroutineScope = TestScope(),
    validate: suspend TestFlowCollector<T>.() -> Unit
) = TestFlowCollector(this, scope).apply(validate)
```

### 3. Test Helpers
```kotlin
object TestData {
    fun createTestPlace(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Place"
    ) = Place(
        id = id,
        name = name,
        description = "Test Description",
        location = Location(0.0, 0.0, "Test Address"),
        tags = listOf("test"),
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
```

## Performance Testing

### 1. Memory Testing
```kotlin
@Test
fun `when loading large list, memory usage stays within limits`() {
    // Given
    val largeList = List(1000) { createTestPlace() }

    // When
    runBlocking {
        repository.savePlaces(largeList)
    }

    // Then
    val memoryInfo = Runtime.getRuntime().memoryInfo()
    assertTrue(memoryInfo.availMem > MIN_MEMORY_THRESHOLD)
}
```

### 2. Response Time Testing
```kotlin
@Test
fun `when fetching places, response time is within limits`() {
    // Given
    val startTime = System.currentTimeMillis()

    // When
    runBlocking {
        repository.getPlaces(PaginationParams())
    }

    // Then
    val endTime = System.currentTimeMillis()
    assertTrue(endTime - startTime < MAX_RESPONSE_TIME)
}
```

## Test Coverage

### 1. Coverage Requirements
- **Business Logic**: 90% coverage
- **UI Components**: 80% coverage
- **Data Layer**: 85% coverage
- **Utilities**: 95% coverage

### 2. Coverage Reports
```kotlin
// build.gradle.kts
android {
    buildTypes {
        getByName("debug") {
            isTestCoverageEnabled = true
        }
    }
}

tasks.register("jacocoTestReport", JacocoReport::class) {
    dependsOn("testDebugUnitTest")
    reports {
        xml.isEnabled = true
        html.isEnabled = true
    }
}
```

## Continuous Integration

### 1. Test Execution
```yaml
# .github/workflows/test.yml
name: Test
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Run Tests
        run: ./gradlew test
      - name: Upload Coverage
        uses: codecov/codecov-action@v1
```

### 2. Test Automation
- **Pre-commit Hooks**: Local test execution
- **CI Pipeline**: Automated test runs
- **Coverage Reports**: Automated reporting
- **Performance Monitoring**: Automated monitoring

## Best Practices

### 1. Test Organization
- **Clear Naming**: Descriptive test names
- **Single Responsibility**: One assertion per test
- **Proper Setup**: Clean test environment
- **Proper Teardown**: Resource cleanup

### 2. Test Independence
- **Isolated Tests**: No test dependencies
- **Clean State**: Reset between tests
- **Mocked Dependencies**: Controlled environment
- **Deterministic**: Consistent results

### 3. Test Maintenance
- **Regular Updates**: Keep tests current
- **Code Reviews**: Test code review
- **Documentation**: Test documentation
- **Refactoring**: Test refactoring

## Documentation

### 1. Test Documentation
```kotlin
/**
 * Tests for the PlaceRepository implementation.
 *
 * Test cases:
 * - Data retrieval
 * - Data persistence
 * - Error handling
 * - Cache management
 *
 * @see PlaceRepository
 * @see PlaceRepositoryImpl
 */
class PlaceRepositoryTest {
    // Test implementation
}
```

### 2. Test Helper Documentation
```kotlin
/**
 * Utility object for creating test data.
 *
 * Features:
 * - Place creation
 * - List generation
 * - Random data generation
 * - Custom data creation
 *
 * Usage:
 * ```kotlin
 * val testPlace = TestData.createTestPlace()
 * val testList = TestData.createTestPlaces(count = 10)
 * ```
 */
object TestData {
    // Implementation
}
``` 