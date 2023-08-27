package anystream.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PrimaryButton(
    leftIcon: @Composable (() -> Unit)? = null,
    text: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(
            contentColor = MaterialTheme.colors.onBackground,
            disabledBackgroundColor = MaterialTheme.colors.primary,
        ),
        modifier = modifier,
    ) {
        AnimatedContent(targetState = isLoading) { targetState ->
            when (targetState) {
                true -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colors.onBackground,
                        strokeWidth = 1.dp,
                    )
                }

                false -> {
                    leftIcon?.let { icon ->
                        icon()
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold),
                        letterSpacing = 0.2.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
