package com.juridireader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.juridireader.app.ui.screens.*
import com.juridireader.app.ui.theme.JuridireaderTheme
import com.juridireader.app.ui.viewmodel.LawViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: LawViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            JuridireaderTheme(themeMode = themeMode) {
                MainAppNavHost(viewModel = viewModel)
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Screen("home", "Leis", Icons.Default.Book)
    data object Search : Screen("search", "Buscar", Icons.Default.Search)
    data object Favorites : Screen("favorites", "Favoritos", Icons.Default.Star)
    data object Notes : Screen("notes", "Grifos & Notas", Icons.Default.EditNote)
}

@Composable
fun MainAppNavHost(viewModel: LawViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Search,
        Screen.Favorites,
        Screen.Notes
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToLaw = { lawCode ->
                        navController.navigate("reader/$lawCode")
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.route)
                    }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    viewModel = viewModel,
                    onNavigateToLaw = { lawCode ->
                        navController.navigate("reader/$lawCode")
                    }
                )
            }

            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    viewModel = viewModel,
                    onNavigateToLaw = { lawCode ->
                        navController.navigate("reader/$lawCode")
                    }
                )
            }

            composable(Screen.Notes.route) {
                NotesScreen(
                    viewModel = viewModel,
                    onNavigateToLaw = { lawCode ->
                        navController.navigate("reader/$lawCode")
                    }
                )
            }

            composable(
                route = "reader/{lawCode}",
                arguments = listOf(navArgument("lawCode") { type = NavType.StringType })
            ) { backStackEntry ->
                val lawCode = backStackEntry.arguments?.getString("lawCode") ?: "CF88"
                LawReaderScreen(
                    lawCode = lawCode,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
