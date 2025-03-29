# UI Documentation

## Overview

The Sesame Android application uses Jetpack Compose for its user interface, following Material Design 3 guidelines
and implementing a modern, responsive design system. This document outlines the UI architecture, components,
and design principles.

## Design System

### 1. Theme

#### Color Scheme
```kotlin
object SesameColors {
    val Primary = Color(0xFF6200EE)
    val Secondary = Color(0xFF03DAC6)
    val Background = Color(0xFFFFFFFF)
    val Surface = Color(0xFFFFFFFF)
    val Error = Color(0xFFB00020)
}
```

#### Typography
```kotlin
val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = RobotoFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp
    ),
    // Other text styles...
)
```

#### Spacing
```kotlin
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}
```

### 2. Components

#### Common Components
```kotlin
@Composable
fun SesameButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier
) {
    // Implementation
}

@Composable
fun SesameTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    // Implementation
}
```

#### Screen Components
```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    // Implementation
}

@Composable
fun ListScreen(
    viewModel: ListViewModel,
    modifier: Modifier = Modifier
) {
    // Implementation
}
```

## Screen Architecture

### 1. Screen Structure
```kotlin
@Composable
fun Screen(
    viewModel: ViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    
    Scaffold(
        topBar = { TopBar() },
        content = { Content(state) },
        bottomBar = { BottomBar() }
    )
}
```

### 2. State Management
```kotlin
data class ScreenState(
    val data: List<Item> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

### 3. Navigation
```kotlin
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object List : Screen("list")
    object Detail : Screen("detail/{id}")
}
```

## Responsive Design

### 1. Layout System
```kotlin
@Composable
fun ResponsiveLayout(
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    when {
        screenWidth < 600.dp -> MobileLayout(content)
        screenWidth < 840.dp -> TabletLayout(content)
        else -> DesktopLayout(content)
    }
}
```

### 2. Adaptive Components
```kotlin
@Composable
fun AdaptiveList(
    items: List<Item>,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    when {
        screenWidth < 600.dp -> VerticalList(items)
        else -> GridList(items)
    }
}
```

## Animation System

### 1. Transitions
```kotlin
@Composable
fun AnimatedTransition(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        content()
    }
}
```

### 2. Gestures
```kotlin
@Composable
fun GestureHandler(
    onSwipe: () -> Unit,
    content: @Composable () -> Unit
) {
    val dragState = rememberDraggableState()
    
    Box(
        modifier = Modifier
            .draggable(dragState)
            .pointerInput(Unit) {
                detectSwipeGestures { direction ->
                    when (direction) {
                        SwipeDirection.Left -> onSwipe()
                        else -> {}
                    }
                }
            }
    ) {
        content()
    }
}
```

## Accessibility

### 1. Content Description
```kotlin
@Composable
fun AccessibleButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.semantics {
            contentDescription = contentDescription
        }
    ) {
        content()
    }
}
```

### 2. Focus Management
```kotlin
@Composable
fun FocusableContent(
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    
    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
    ) {
        // Content
    }
}
```

## Performance Optimization

### 1. Composition Optimization
```kotlin
@Composable
fun OptimizedList(
    items: List<Item>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        userScrollEnabled = true
    ) {
        items(
            items = items,
            key = { it.id }
        ) { item ->
            ItemContent(item)
        }
    }
}
```

### 2. State Hoisting
```kotlin
@Composable
fun StatefulComponent(
    state: State,
    onStateChange: (State) -> Unit,
    modifier: Modifier = Modifier
) {
    // Implementation
}
```

## Testing

### 1. UI Testing
```kotlin
@Test
fun `when screen loads, displays loading state`() {
    composeTestRule.setContent {
        SesameTheme {
            HomeScreen(viewModel)
        }
    }
    
    composeTestRule.onNodeWithText("Loading")
        .assertIsDisplayed()
}
```

### 2. Accessibility Testing
```kotlin
@Test
fun `button has correct content description`() {
    composeTestRule.setContent {
        SesameTheme {
            AccessibleButton(
                onClick = {},
                contentDescription = "Submit"
            ) {
                Text("Submit")
            }
        }
    }
    
    composeTestRule.onNodeWithContentDescription("Submit")
        .assertIsDisplayed()
}
```

## Error Handling

### 1. Error States
```kotlin
@Composable
fun ErrorState(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = error)
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
```

### 2. Loading States
```kotlin
@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
```

## Documentation

### 1. Component Documentation
```kotlin
/**
 * A reusable button component that follows the app's design system.
 *
 * @param onClick The callback to be invoked when the button is clicked
 * @param text The text to display on the button
 * @param modifier The modifier to be applied to the button
 * @param enabled Whether the button is enabled
 * @param loading Whether the button is in a loading state
 */
@Composable
fun SesameButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    // Implementation
}
```

### 2. Screen Documentation
```kotlin
/**
 * The main home screen of the application.
 *
 * Features:
 * - Displays a list of places
 * - Shows loading and error states
 * - Handles user interactions
 * - Manages screen state
 *
 * @param viewModel The ViewModel that manages the screen's state
 * @param modifier The modifier to be applied to the screen
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    // Implementation
}
``` 