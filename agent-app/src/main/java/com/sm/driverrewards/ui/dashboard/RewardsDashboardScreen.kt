// Package: com.sm.driverrewards.ui.dashboard
// Disguised delivery driver loyalty and mileage rewards dashboard
package com.sm.driverrewards.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sm.driverrewards.data.local.AgentPreferences
import com.sm.driverrewards.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsDashboardScreen() {
    val context = LocalContext.current
    val prefs = remember { AgentPreferences(context) }

    var points by remember { mutableStateOf(prefs.getRewardPoints()) }
    var bonusClaimedToday by remember {
        val lastClaim = prefs.getLastBonusTime()
        val isToday = (System.currentTimeMillis() - lastClaim) < 24 * 60 * 60 * 1000L
        mutableStateOf(isToday && lastClaim > 0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "SM Driver Rewards",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* Refresh status */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BluePrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BackgroundLight)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Driver greeting banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hello, ${prefs.getDriverName()}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = "Partner Tier: Gold Level Driver",
                        fontSize = 13.sp,
                        color = BluePrimary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    color = SuccessGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(SuccessGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Active Shift",
                            color = SuccessGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Big Points Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BluePrimary),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Available Reward Points",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MonetizationOn,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format("%,d pts", points),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (!bonusClaimedToday) {
                                prefs.addRewardPoints(50)
                                prefs.setLastBonusTime(System.currentTimeMillis())
                                points += 50
                                bonusClaimedToday = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (bonusClaimedToday) Color.White.copy(alpha = 0.2f) else GoldAccent
                        ),
                        shape = RoundedCornerShape(20.dp),
                        enabled = !bonusClaimedToday
                    ) {
                        Text(
                            text = if (bonusClaimedToday) "Daily Bonus Claimed (+50 pts)" else "Claim Daily Streak Bonus (+50 pts)",
                            color = if (bonusClaimedToday) Color.White else Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Metric Summary Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Today's Shifts",
                    value = "2 / 2",
                    sub = "Completed",
                    icon = Icons.Default.CheckCircle,
                    iconTint = SuccessGreen
                )

                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Fuel Cashback",
                    value = "$24.50",
                    sub = "Processing",
                    icon = Icons.Default.LocalGasStation,
                    iconTint = GoldAccent
                )

                MetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Safety Score",
                    value = "98%",
                    sub = "Excellent",
                    icon = Icons.Default.VerifiedUser,
                    iconTint = BluePrimary
                )
            }

            // Today's Verified Route Activity
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Recent Eligible Shifts",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TripRow(
                        route = "Hub A -> Downtown Delivery Route",
                        time = "10:30 AM - 1:45 PM",
                        miles = "42.8 mi",
                        pts = "+120 pts"
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    TripRow(
                        route = "Industrial Park Express Delivery",
                        time = "2:30 PM - 5:15 PM",
                        miles = "38.2 mi",
                        pts = "+95 pts"
                    )
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    TripRow(
                        route = "City Center Return Haul",
                        time = "Yesterday, 6:00 PM",
                        miles = "19.5 mi",
                        pts = "+60 pts"
                    )
                }
            }

            // Driver partner safety guidelines card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Driver Mileage Program Rules",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Keep telemetry active to qualify for the monthly \$250 driver safety bonus. Points can be redeemed at participating Shell and Chevron gas stations.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    sub: String,
    icon: ImageVector,
    iconTint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text(title, fontSize = 11.sp, color = TextMuted)
            Text(sub, fontSize = 10.sp, color = iconTint, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TripRow(
    route: String,
    time: String,
    miles: String,
    pts: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(route, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = TextDark)
            Text("$time • $miles", fontSize = 11.sp, color = TextMuted)
        }
        Text(pts, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SuccessGreen)
    }
}
