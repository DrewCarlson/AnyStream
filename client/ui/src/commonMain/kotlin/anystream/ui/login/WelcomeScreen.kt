/**
 * AnyStream
 * Copyright (C) 2023 AnyStream Maintainers
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
package anystream.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anystream.ui.components.PrimaryButton
import anystream.ui.generated.resources.Res
import anystream.ui.generated.resources.welcome_bg
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun WelcomeScreen(onCtaClicked: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) {
        Box {
            Image(
                painter = painterResource(Res.drawable.welcome_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            Spacer(
                Modifier
                    .fillMaxSize()
                    .padding(top = 200.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0x00181A20), Color(0xFF181A20)),
                        ),
                    ),
            )

            Column(
                Modifier
                    .padding(bottom = 36.dp)
                    .padding(horizontal = 24.dp)
                    .align(Alignment.BottomCenter),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Welcome to AnyStream",
                    style = MaterialTheme.typography.h2.copy(textAlign = TextAlign.Center),
                )

                Text(
                    text = "Your private streaming service.",
                    style = MaterialTheme.typography.h6.copy(
                        lineHeight = 25.2.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                )

                PrimaryButton(
                    text = "Get Started",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCtaClicked,
                )
            }
        }
    }
}
