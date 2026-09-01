package com.novastream.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastream.app.ui.theme.*

data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

@Composable
fun PremiumBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    watchlistCount: Int = 0
) {
    val items = listOf(
        NavItem("Home", Icons.Filled.Home, Icons.Outlined.Home, "home"),
        NavItem("Entdecken", Icons.Filled.Explore, Icons.Outlined.Explore, "browse"),
        NavItem("Liste", Icons.Filled.Bookmark, Icons.Outlined.Bookmark, "watchlist"),
        NavItem("Suche", Icons.Filled.Search, Icons.Outlined.Search, "search"),
        NavItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "settings")
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.3f to BgPure
                )
            )
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 16.dp,
                    vertical = 8.dp
                )
                .padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val iconColor by animateColorAsState(
                    targetValue = if (selected) Primary else TextTertiary,
                    animationSpec = tween(300),
                    label = "iconColor"
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) TextPrimary else TextTertiary,
                    animationSpec = tween(300),
                    label = "textColor"
                )

                Box {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { if (!selected) onNavigate(item.route) }
                            .focusable()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = if (selected) "${item.label}, ausgewählt" else item.label,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            color = textColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                    // Badge für Watchlist-Count
                    if (item.route == "watchlist" && watchlistCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 4.dp, top = 0.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (watchlistCount > 99) "99+" else watchlistCount.toString(),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * TV Top Tab Bar - für Android TV / Fire TV Navigation.
 * Verwendet eine horizontale Tab-Leiste am oberen Bildschirmrand
 * (Amazon Fire TV + Android TV Guidelines empfehlen Top-Navigation statt Bottom Bar).
 */
@Composable
fun PremiumTopTabBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        NavItem("Home", Icons.Filled.Home, Icons.Outlined.Home, "home"),
        NavItem("Entdecken", Icons.Filled.Explore, Icons.Outlined.Explore, "browse"),
        NavItem("Liste", Icons.Filled.Bookmark, Icons.Outlined.Bookmark, "watchlist"),
        NavItem("Suche", Icons.Filled.Search, Icons.Outlined.Search, "search"),
        NavItem("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "settings")
    )

    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .background(BgPure)
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottom = 8.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            val iconColor by animateColorAsState(
                targetValue = if (selected) Primary else TextTertiary,
                animationSpec = tween(300),
                label = "tvIconColor"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) TextPrimary else TextTertiary,
                animationSpec = tween(300),
                label = "tvTextColor"
            )
            val bgColor by animateColorAsState(
                targetValue = if (selected) Primary.copy(alpha = 0.15f) else Color.Transparent,
                animationSpec = tween(300),
                label = "tvBgColor"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(bgColor)
                    .clickable { if (!selected) onNavigate(item.route) }
                    .focusable()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = if (selected) "${item.label}, ausgewählt" else item.label,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = item.label,
                    color = textColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}
