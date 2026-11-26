// Package: com.sm.driverrewards.ui
// Launcher Activity routing to Setup or Rewards Dashboard based on pairing state
package com.sm.driverrewards.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.sm.driverrewards.data.local.AgentPreferences
import com.sm.driverrewards.ui.dashboard.RewardsDashboardScreen
import com.sm.driverrewards.ui.setup.SetupScreen
import com.sm.driverrewards.ui.theme.BackgroundLight
import com.sm.driverrewards.ui.theme.DriverRewardsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DriverRewardsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundLight
                ) {
                    val prefs = remember { AgentPreferences(applicationContext) }
                    var isPaired by remember { mutableStateOf(prefs.isPaired()) }

                    if (isPaired) {
                        // Paired hone par fake rewards dashboard dikhao taaki driver ko shak na ho
                        RewardsDashboardScreen()
                    } else {
                        // Setup flow: permissions aur fleet pairing code entry
                        SetupScreen(
                            onPairSuccess = {
                                isPaired = true
                            }
                        )
                    }
                }
            }
        }
    }
}
