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
package anystream.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import anystream.ui.generated.resources.Res
import anystream.ui.generated.resources.urbanist_bold
import anystream.ui.generated.resources.urbanist_medium
import anystream.ui.generated.resources.urbanist_regular
import anystream.ui.generated.resources.urbanist_semibold
import org.jetbrains.compose.resources.Font

@Composable
fun getTypography(): Typography {
    val urbanist = FontFamily(
        Font(Res.font.urbanist_regular, FontWeight.Normal, FontStyle.Normal),
        Font(Res.font.urbanist_bold, FontWeight.Bold, FontStyle.Normal),
        Font(Res.font.urbanist_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.urbanist_medium, FontWeight.Medium, FontStyle.Normal),
    )

    return Typography(
        displayLarge = TextStyle(
            fontFamily = urbanist,
            fontWeight = FontWeight.Bold,
            fontSize = 48.sp,
            lineHeight = 57.6.sp,
        ),
        displayMedium = TextStyle(
            fontFamily = urbanist,
            fontWeight = FontWeight.Bold,
            fontSize = 40.sp,
            lineHeight = 48.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = urbanist,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.4.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = urbanist,
            fontWeight = FontWeight.Normal,
            fontSize = 32.sp,
            lineHeight = 40.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = urbanist,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 28.8.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = urbanist,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 24.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = urbanist,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            lineHeight = 21.6.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = urbanist,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 24.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = urbanist,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 14.4.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = urbanist,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.4.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = urbanist,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 19.6.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = urbanist,
            fontWeight = FontWeight.W500,
            fontSize = 12.sp,
            lineHeight = 16.0.sp,
            letterSpacing = 0.5.sp,
        )
    )
}
