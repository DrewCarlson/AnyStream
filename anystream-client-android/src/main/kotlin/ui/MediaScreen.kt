package anystream.android.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anystream.android.AppTypography
import anystream.android.R
import anystream.android.router.BackStack
import anystream.client.AnyStreamClient
import anystream.models.MediaReference
import anystream.models.frontend.MediaItem
import anystream.models.frontend.toMediaItem
import anystream.models.api.MediaLookupResponse
import anystream.routing.Routes
import coil.compose.rememberImagePainter
import kotlinx.coroutines.flow.*

@Composable
fun MediaScreen(
    client: AnyStreamClient,
    mediaId: String,
    onPlayClick: (mediaRefId: String?) -> Unit,
    backStack: BackStack<Routes>
) {
    val lookupIdFlow = remember(mediaId) { MutableStateFlow<Int?>(null) }
    val refreshMetadata: () -> Unit = remember {
        { lookupIdFlow.update { (it ?: 0) + 1 } }
    }
    val mediaResponse by produceState<MediaLookupResponse?>(null, mediaId) {
        value = try {
            client.lookupMedia(mediaId)
        } catch (e: Throwable) {
            null
        }
        lookupIdFlow
            .onEach { it }
            .filterNotNull()
            .debounce(1_000L)
            .collect {
                try {
                    client.refreshStreamDetails(mediaId)
                    value = client.refreshMetadata(mediaId)
                } catch (_: Throwable) {
                }
            }
    }
//    DisposableEffect(mediaId) {
//        onDispose { backdropImageUrl.value = null }
//    }

    mediaResponse?.movie?.let { response ->
        BaseDetailsView(
            mediaItem = response.toMediaItem(),
            refreshMetadata = refreshMetadata,
            client = client,
            backStack = backStack,
            onPlayClick = onPlayClick
        )
    }

    mediaResponse?.tvShow?.let { response ->
        BaseDetailsView(
            mediaItem = response.toMediaItem(),
            refreshMetadata = refreshMetadata,
            client = client,
            backStack = backStack,
            onPlayClick = onPlayClick
        )
    }
}


@Composable
private fun BaseDetailsView(
    mediaItem: MediaItem,
    refreshMetadata: () -> Unit,
    client: AnyStreamClient,
    backStack: BackStack<Routes>,
    onPlayClick: (mediaRefId: String?) -> Unit,
) {
    val cardWidth = (LocalConfiguration.current.screenWidthDp / 3).coerceAtMost(130).dp
    Column(Modifier.fillMaxSize()) {

        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(.37f),
        ) {
            val painter2 = rememberImagePainter(
                data = "https://image.tmdb.org/t/p/w500${mediaItem.backdropPath}",
                builder = {
                    crossfade(true)
                }
            )
            Image(
                painter = painter2,
                contentDescription = "",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(.7f)
            )
            Column(
                modifier = Modifier
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0x99000000), Color(0x10000000))
                        )
                    )
                    .fillMaxWidth()
                    .fillMaxHeight(.8f)
            ) {

            }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = { backStack.pop() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "",
                            tint = MaterialTheme.colors.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(
                            Icons.Default.Search, //LiveTv,
                            contentDescription = "",
                            tint = MaterialTheme.colors.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                val painter = rememberImagePainter(
                    data = "https://image.tmdb.org/t/p/w500${mediaItem.posterPath}",
                    builder = {
                        crossfade(true)
                    }
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, start = 16.dp, end = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(cardWidth)
                            .align(Alignment.Bottom)
                    ) {
                        Card(elevation = 2.dp, shape = RectangleShape) {
                            Image(
                                painter = painter,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth(),
                                alignment = Alignment.BottomCenter
                            )
                        }
                    }

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = mediaItem.contentTitle,
                            color = Color(0xBFFFFFFF),
                            style = AppTypography.body1,
                        )
                        Text(text = "80%", color = Color(0x80FFFFFF))
                    }
                }
            }
        }

        Button(
            onClick = { onPlayClick(mediaItem.mediaRefs.firstOrNull()?.id) },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red),
            modifier = Modifier
                .padding(top = 24.dp, start = 16.dp)
                .width(cardWidth),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "",
                modifier = Modifier.size(26.dp),
                tint = Color(0xCCFFFFFF)
            )
            Text(
                stringResource(R.string.media_resume),
                Modifier.padding(start = 4.dp),
                color = Color(0xCCFFFFFF)
            )
        }

        var maxLines by remember { mutableStateOf(3) }

        Text(
            text = mediaItem.overview,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colors.onSurface,
            lineHeight = 24.sp,
            maxLines = maxLines
        )

        TextButton(
            onClick = {
                maxLines = if (maxLines == 3) Int.MAX_VALUE else 3
            },
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = if (maxLines == 3) "MORE" else "LESS",
                color = MaterialTheme.colors.primary,
                lineHeight = 24.sp,
            )
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun MockMediaScreen() {
//    AppTheme {
//        MediaScreen {
//        }
//    }
//}