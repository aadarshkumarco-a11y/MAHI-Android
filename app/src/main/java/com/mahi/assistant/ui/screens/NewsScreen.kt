package com.mahi.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahi.assistant.api.Article
import com.mahi.assistant.ui.components.GlowCard
import com.mahi.assistant.ui.theme.*
import com.mahi.assistant.ui.viewmodel.MahiViewModel

/**
 * News feed screen — JARVIS intel briefing.
 * Now connected to ViewModel for live news data from GNews API.
 */
@Composable
fun NewsScreen(
    viewModel: MahiViewModel,
    onArticleClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val newsState by viewModel.newsState.collectAsState()
    var selectedCategory by remember { mutableStateOf(newsState.selectedCategory) }
    val categories = listOf("general", "technology", "science", "business", "entertainment", "sports", "health")

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // ── Header ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NeonCyan,
                )
            }
            Text(
                text = "INTEL BRIEFING",
                style = MaterialTheme.typography.headlineSmall,
                color = NeonCyan,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Category Tabs ───────────────────────────────────────
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
            containerColor = DeepSpaceBlack,
            contentColor = NeonCyan,
            edgePadding = 16.dp,
            divider = {},
        ) {
            categories.forEachIndexed { index, category ->
                Tab(
                    selected = selectedCategory == category,
                    onClick = {
                        selectedCategory = category
                        viewModel.processInput("show me $category news")
                    },
                    text = {
                        Text(
                            text = category.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedCategory == category) NeonCyan else TextTertiary,
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (newsState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = NeonCyan)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Fetching headlines...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        } else if (newsState.error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Newspaper,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Could not load news",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = newsState.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set your GNews API key in Settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = Amber,
                    )
                }
            }
        } else if (newsState.articles.isNotEmpty()) {
            // ── Articles List ───────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(newsState.articles, key = { it.url ?: it.title ?: Math.random().toString() }) { article ->
                    NewsArticleCard(
                        article = article,
                        onClick = { article.url?.let { onArticleClick(it) } },
                    )
                }
            }
        } else {
            // No articles yet
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Article,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ask MAHI for news",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Say \"Show me the news\" to get started",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsArticleCard(
    article: Article,
    onClick: () -> Unit,
) {
    GlowCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        glowColor = NeonCyan,
        borderAlpha = 0.2f,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            // Source
            article.source?.name?.let { source ->
                Text(
                    text = source.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricPurple,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            article.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Description
            article.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Read more indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "READ MORE",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.OpenInNew,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
