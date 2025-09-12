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
package anystream.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import anystream.client.AnyStreamClient
import anystream.models.api.SearchResponse
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.koin.compose.koinInject

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(
    onBackClicked: () -> Unit,
    onMetadataClick: (metadataId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val client: AnyStreamClient = koinInject()
    var searchResults by remember { mutableStateOf<SearchResponse?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.statusBars)
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onBackClicked) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            
            SearchBar(
                modifier = Modifier.weight(1f),
                onSearch = { query ->
                    isLoading = true
                    searchResults = try {
                        client.library.search(query)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    } finally {
                        isLoading = false
                    }
                }
            )
        }
        
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            searchResults != null -> {
                SearchResults(
                    searchResults = searchResults!!,
                    onMetadataClick = onMetadataClick
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    modifier: Modifier = Modifier,
    onSearch: suspend (String) -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    val searchFlow = remember { MutableStateFlow("") }
    
    LaunchedEffect(searchFlow) {
        searchFlow
            .debounce(500)
            .filter { it.length >= 3 }
            .distinctUntilChanged()
            .collect { query ->
                onSearch(query)
            }
    }
    
    OutlinedTextField(
        value = searchText,
        onValueChange = { 
            searchText = it
            searchFlow.value = it
        },
        placeholder = { 
            Text(
                text = "Search movies and shows...",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            ) 
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        },
        trailingIcon = {
            if (searchText.isNotEmpty()) {
                IconButton(
                    onClick = { 
                        searchText = ""
                        searchFlow.value = ""
                    }
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun SearchResults(
    searchResults: SearchResponse,
    onMetadataClick: (metadataId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(searchResults.movies) { movie ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMetadataClick(movie.id) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        items(searchResults.tvShows) { tvShowResult ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMetadataClick(tvShowResult.tvShow.id) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Text(
                    text = tvShowResult.tvShow.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        items(searchResults.episodes) { episodeResult ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMetadataClick(episodeResult.episode.id) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = episodeResult.episode.name,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = episodeResult.tvShow.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}