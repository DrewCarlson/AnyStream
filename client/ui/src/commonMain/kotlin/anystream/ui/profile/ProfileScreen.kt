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
package anystream.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import anystream.ui.LocalAnyStreamClient
import anystream.ui.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onPairDeviceClicked: () -> Unit,
) {
    val client = LocalAnyStreamClient.current
    val scope = rememberCoroutineScope()
    val user by produceState(client.authedUser()) {
        if (value == null) {
            value = client.authedUser()
        }
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )

            Text(
                text = user?.displayName.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        ProfileOption(
            label = "Settings",
            icon = Res.drawable.ic_curved_settings,
            onClick = { },
            trailingIcon = Res.drawable.ic_arrow_right,
        )
        ProfileOption(
            label = "Edit Profile",
            icon = Res.drawable.ic_curved_profile,
            onClick = { },
            trailingIcon = Res.drawable.ic_arrow_right,
        )
        ProfileOption(
            label = "Security",
            icon = Res.drawable.ic_curved_shield_done,
            onClick = { },
            trailingIcon = Res.drawable.ic_arrow_right,
        )
        ProfileOption(
            label = "Pair Device",
            icon = Res.drawable.ic_curved_scan,
            onClick = onPairDeviceClicked,
        )
        ProfileOption(
            label = "Logout",
            icon = Res.drawable.ic_curved_logout,
            onClick = { scope.launch { client.logout() } },
        )
    }
}

@Composable
private fun ProfileOption(
    label: String,
    icon: DrawableResource,
    trailingLabel: String? = null,
    trailingIcon: DrawableResource? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 24.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.fillMaxHeight(),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                alignment = Alignment.Center,
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.weight(1f))

        if (trailingLabel != null) {
            Text(
                text = trailingLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (trailingIcon == null) {
            Spacer(Modifier.size(20.dp))
        } else {
            Icon(
                painterResource(trailingIcon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}