/**
 * AnyStream
 * Copyright (C) 2025 AnyStream Maintainers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package anystream.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import anystream.routing.Routes
import anystream.ui.generated.resources.*
import org.jetbrains.compose.resources.painterResource


@Composable
internal fun BottomNavigation(
    selectedRoute: Routes,
    onRouteChanged: (Routes) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        containerColor = Color.Transparent,
        modifier = modifier,
    ) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides null
        ) {
            val colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent,
                selectedIconColor = Color(0xFFE21221),
                selectedTextColor = Color(0xFFE21221),
                unselectedIconColor = Color(0xFF9E9E9E),
                unselectedTextColor = Color(0xFF9E9E9E),
                disabledIconColor = Color(0xFF9E9E9E),
                disabledTextColor = Color(0xFF9E9E9E),
            )
            NavigationBarItem(
                selected = selectedRoute == Routes.Home,
                label = { Text("Home") },
                icon = {
                    Icon(
                        painterResource(
                            if (selectedRoute == Routes.Home) {
                                Res.drawable.ic_home_fill
                            } else {
                                Res.drawable.ic_home_outline
                            }
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                colors = colors,
                onClick = { onRouteChanged(Routes.Home) },
            )

            NavigationBarItem(
                selected = selectedRoute == Routes.Profile,
                label = { Text("Profile") },
                icon = {
                    Icon(
                        painterResource(
                            if (selectedRoute == Routes.Profile) {
                                Res.drawable.ic_profile_fill
                            } else {
                                Res.drawable.ic_curved_profile
                            }
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                colors = colors,
                onClick = { onRouteChanged(Routes.Profile) },
            )
        }
    }
}
