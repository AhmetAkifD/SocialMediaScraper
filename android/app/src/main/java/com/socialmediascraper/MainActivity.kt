package com.socialmediascraper

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.socialmediascraper.notify.Notifier
import com.socialmediascraper.ui.FeedScreen
import com.socialmediascraper.ui.MainViewModel
import com.socialmediascraper.ui.DeepFetchScreen
import com.socialmediascraper.ui.SettingsScreen
import com.socialmediascraper.ui.XLoginScreen
import com.socialmediascraper.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) vm.updateSettings { it.copy(notificationsEnabled = false) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            vm.settings.value.notificationsEnabled &&
            !Notifier.canNotify(this)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AppTheme {
                val nav = rememberNavController()
                val snackbar = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    vm.refreshLoginState()
                    vm.refreshOnStart()
                    vm.messages.collect { snackbar.showSnackbar(it) }
                }

                NavHost(navController = nav, startDestination = "feed") {
                    // Ekran kendini kapatmak istediğinde yalnızca hâlâ en üstteyse geri dön.
                    fun close(route: String) {
                        if (nav.currentDestination?.route == route) nav.popBackStack()
                    }
                    composable("feed") {
                        FeedScreen(
                            vm, snackbar,
                            onOpenSettings = { nav.navigate("settings") { launchSingleTop = true } },
                            onLogin = { nav.navigate("login") { launchSingleTop = true } },
                            onDeepFetch = { handle -> nav.navigate("deep/$handle") { launchSingleTop = true } },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            vm, snackbar,
                            onBack = { close("settings") },
                            onLogin = { nav.navigate("login") { launchSingleTop = true } },
                        )
                    }
                    composable("login") {
                        XLoginScreen { loggedIn ->
                            vm.onLoginFinished(loggedIn)
                            close("login")
                        }
                    }
                    composable("deep/{handle}") { entry ->
                        val handle = entry.arguments?.getString("handle").orEmpty()
                        DeepFetchScreen(vm, handle, onDone = { close("deep/{handle}") })
                    }
                }
            }
        }
    }
}
