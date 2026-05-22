package com.example.upbeat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.upbeat.ui.AddSongScreen
import com.example.upbeat.ui.SampleScreen
import com.example.upbeat.ui.SongListScreen
import com.example.upbeat.ui.SongScreen
import com.example.upbeat.ui.theme.UpBeatTheme
import com.example.upbeat.viewmodel.SongsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UpBeatTheme {
                UpBeatApp()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun UpBeatApp(songsViewModel: SongsViewModel = viewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        songsViewModel.loadSongs(context)
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            val destination = AppDestinations.HOME
            item(
                icon = {
                    Icon(
                        destination.icon,
                        contentDescription = destination.label
                    )
                },
                label = { Text(destination.label) },
                selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                onClick = {
                    navController.navigate(destination.route)
                }
            )
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestinations.HOME.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(AppDestinations.HOME.route) {
                    SongListScreen(
                        songs = songsViewModel.songs,
                        isLoading = songsViewModel.isLoading.value,
                        loadError = songsViewModel.loadError.value,
                        onSongClick = { song ->
                            navController.navigate(AppDestinations.SONG_DETAIL.route.replace("{songName}", song.name))
                        },
                        onAddSong = {
                            navController.navigate(AppDestinations.ADD_SONG.route)
                        },
                        onRetry = {
                            songsViewModel.loadSongs(context)
                        }
                    )
                }
                composable(AppDestinations.ADD_SONG.route) {
                    AddSongScreen(
                        onSongAdded = { songName, s3Key ->
                            songsViewModel.addSong(songName, s3Key)
                            navController.popBackStack()
                        },
                        onSongExists = { songName -> songsViewModel.songExists(songName) }
                    )
                }
                composable(
                    route = AppDestinations.SONG_DETAIL.route,
                    arguments = listOf(navArgument("songName") { type = NavType.StringType })
                ) { backStackEntry ->
                    val songName = backStackEntry.arguments?.getString("songName")
                    SongScreen(songName = songName, songsViewModel = songsViewModel)
                }
                composable(AppDestinations.FAVORITES.route) {
                    SampleScreen(title = "Favorites Screen")
                }
                composable(AppDestinations.PROFILE.route) {
                    SampleScreen(title = "Profile Screen")
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
    val route: String
) {
    HOME("Songs", Icons.Default.Home, "songs_list"),
    ADD_SONG("Add Song", Icons.Default.Add, "add_song"),
    SONG_DETAIL("Song Detail", Icons.Default.Info, "song_detail/{songName}"),
    FAVORITES("Favorites", Icons.Default.Favorite, "favorites"),
    PROFILE("Profile", Icons.Default.AccountBox, "profile"),
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    UpBeatTheme {
        UpBeatApp()
    }
}
