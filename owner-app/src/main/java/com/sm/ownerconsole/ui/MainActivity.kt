// Package: com.sm.ownerconsole.ui
// Root Activity for Owner Console with Compose Navigation
package com.sm.ownerconsole.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sm.ownerconsole.data.repository.OwnerRepository
import com.sm.ownerconsole.ui.auth.LoginScreen
import com.sm.ownerconsole.ui.detail.DeviceDetailScreen
import com.sm.ownerconsole.ui.devices.DeviceListScreen
import com.sm.ownerconsole.ui.theme.DarkBg
import com.sm.ownerconsole.ui.theme.OwnerConsoleTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OwnerConsoleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBg
                ) {
                    OwnerAppNavigation()
                }
            }
        }
    }
}

@Composable
fun OwnerAppNavigation() {
    val navController = rememberNavController()
    val repository = remember { OwnerRepository() }
    val startDestination = if (repository.isUserLoggedIn()) "device_list" else "login"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("device_list") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("device_list") {
            DeviceListScreen(
                onSelectDevice = { deviceId ->
                    navController.navigate("device_detail/$deviceId")
                },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("device_list") { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "device_detail/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            DeviceDetailScreen(
                deviceId = deviceId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
