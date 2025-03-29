# Dependency Injection Documentation

## Overview

The Sesame Android application uses Hilt for dependency injection, providing a clean and efficient
way to manage dependencies throughout the application. This document outlines the DI architecture,
modules, and implementation details.

## DI Architecture

### 1. Application Module

#### Core Application Module
```kotlin
/**
 * Main application module for dependency injection.
 *
 * Features:
 * - Application-level dependencies
 * - Singleton scoped dependencies
 * - Context providers
 * - System service providers
 *
 * Dependencies:
 * - Context
 * - Logger
 * - SecurityManager
 * - NavigationController
 *
 * @property context Application context
 * @property logger Logger instance
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideLogger(
        @ApplicationContext context: Context
    ): Logger = Logger(context)

    @Provides
    @Singleton
    fun provideSecurityManager(
        @ApplicationContext context: Context,
        logger: Logger
    ): SecurityManager = SecurityManager(context, logger)
}
```

### 2. Repository Module

#### Repository Dependencies
```kotlin
/**
 * Repository module for data layer dependencies.
 *
 * Features:
 * - Repository implementations
 * - Data source providers
 * - Mapper providers
 * - Cache providers
 *
 * Dependencies:
 * - PlaceRepository
 * - UserRepository
 * - LocationRepository
 * - CacheManager
 *
 * @property context Application context
 * @property logger Logger instance
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun providePlaceRepository(
        remoteDataSource: PlaceRemoteDataSource,
        localDataSource: PlaceLocalDataSource,
        mapper: PlaceMapper,
        logger: Logger
    ): PlaceRepository = PlaceRepositoryImpl(
        remoteDataSource = remoteDataSource,
        localDataSource = localDataSource,
        mapper = mapper,
        logger = logger
    )
}
```

### 3. ViewModel Module

#### ViewModel Dependencies
```kotlin
/**
 * ViewModel module for presentation layer dependencies.
 *
 * Features:
 * - ViewModel providers
 * - UseCase providers
 * - State management
 * - Event handling
 *
 * Dependencies:
 * - HomeViewModel
 * - ListViewModel
 * - DetailViewModel
 * - SettingsViewModel
 *
 * @property context Application context
 * @property logger Logger instance
 */
@Module
@InstallIn(ViewModelComponent::class)
object ViewModelModule {
    @Provides
    fun provideHomeViewModel(
        placeRepository: PlaceRepository,
        locationRepository: LocationRepository,
        logger: Logger
    ): HomeViewModel = HomeViewModel(
        placeRepository = placeRepository,
        locationRepository = locationRepository,
        logger = logger
    )
}
```

## DI Components

### 1. Data Source Module
```kotlin
/**
 * Data source module for remote and local data providers.
 *
 * Features:
 * - Remote data source providers
 * - Local data source providers
 * - Network client providers
 * - Database providers
 *
 * @property context Application context
 * @property logger Logger instance
 */
@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {
    @Provides
    @Singleton
    fun providePlaceRemoteDataSource(
        apiService: PlaceApiService,
        logger: Logger
    ): PlaceRemoteDataSource = PlaceRemoteDataSourceImpl(
        apiService = apiService,
        logger = logger
    )

    @Provides
    @Singleton
    fun providePlaceLocalDataSource(
        database: AppDatabase,
        logger: Logger
    ): PlaceLocalDataSource = PlaceLocalDataSourceImpl(
        database = database,
        logger = logger
    )
}
```

### 2. Network Module
```kotlin
/**
 * Network module for API communication dependencies.
 *
 * Features:
 * - API service providers
 * - Network client providers
 * - Interceptor providers
 * - Converter providers
 *
 * @property context Application context
 * @property logger Logger instance
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(
        securityInterceptor: SecurityInterceptor,
        logger: Logger
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(securityInterceptor)
        .addInterceptor(HttpLoggingInterceptor(logger))
        .build()

    @Provides
    @Singleton
    fun providePlaceApiService(
        client: OkHttpClient
    ): PlaceApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(PlaceApiService::class.java)
}
```

## DI Scopes

### 1. Scope Definitions
```kotlin
/**
 * Custom scopes for dependency injection.
 *
 * Features:
 * - Activity scope
 * - Fragment scope
 * - Feature scope
 * - Custom scopes
 *
 * @property scopeName Scope identifier
 */
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class ActivityScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class FragmentScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class FeatureScope
```

### 2. Scope Usage
```kotlin
/**
 * Example of scope usage in modules.
 *
 * Features:
 * - Scope annotations
 * - Scope providers
 * - Scope dependencies
 * - Scope lifecycle
 *
 * @property context Application context
 * @property logger Logger instance
 */
@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {
    @Provides
    @ActivityScope
    fun provideActivityViewModel(
        repository: PlaceRepository,
        logger: Logger
    ): ActivityViewModel = ActivityViewModel(
        repository = repository,
        logger = logger
    )
}
```

## DI Testing

### 1. Test Module
```kotlin
/**
 * Test module for dependency injection testing.
 *
 * Features:
 * - Mock providers
 * - Test dependencies
 * - Test scopes
 * - Test configurations
 *
 * @property context Test context
 * @property logger Test logger
 */
@Module
@InstallIn(SingletonComponent::class)
object TestModule {
    @Provides
    @Singleton
    fun provideTestRepository(
        logger: Logger
    ): PlaceRepository = TestPlaceRepository(logger)

    @Provides
    @Singleton
    fun provideTestApiService(
        logger: Logger
    ): PlaceApiService = TestPlaceApiService(logger)
}
```

### 2. Test Configuration
```kotlin
/**
 * Test configuration for dependency injection.
 *
 * Features:
 * - Test setup
 * - Test teardown
 * - Test scopes
 * - Test dependencies
 *
 * @property context Test context
 * @property logger Test logger
 */
@HiltAndroidTest
class TestApplication : Application(), HiltTestApplication {
    override fun onCreate() {
        super.onCreate()
        // Test configuration
    }
}
```

## DI Documentation

### 1. Module Documentation
```kotlin
/**
 * Module documentation template.
 *
 * Features:
 * - Module description
 * - Dependency providers
 * - Scope definitions
 * - Usage guidelines
 *
 * @property module The DI module
 * @property logger Logger instance
 */
class ModuleDocumentation @Inject constructor(
    private val module: Any,
    private val logger: Logger
) {
    // Implementation
}
```

### 2. Provider Documentation
```kotlin
/**
 * Provider documentation template.
 *
 * Features:
 * - Provider description
 * - Dependency requirements
 * - Scope information
 * - Usage examples
 *
 * @property provider The dependency provider
 * @property logger Logger instance
 */
class ProviderDocumentation @Inject constructor(
    private val provider: Any,
    private val logger: Logger
) {
    // Implementation
} 