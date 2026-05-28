package com.mahi.assistant.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahi.assistant.ui.components.GlowCard
import com.mahi.assistant.ui.theme.*

/**
 * Data model for a news article.
 */
data class NewsArticle(
    val id: String = java.util.UUID.randomUUID().toString(),
    val source: String,
    val title: String,
    val description: String,
    val publishedAt: String,
    val url: String,
    val category: String = "General",
)

/**
 * News feed screen — JARVIS intel briefing.
 */
@Composable
fun NewsScreen(
    articles: List<NewsArticle> = sampleNewsArticles,
    onArticleClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val categories = listOf("General", "Technology", "Science", "Business")
    val pagerState = rememberPagerState(pageCount = { categories.size })

    // Filter articles by selected category
    val selectedCategory = categories[pagerState.currentPage]
    val filteredArticles = if (selectedCategory == "General") {
        articles
    } else {
        articles.filter { it.category == selectedCategory }
    }

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
            selectedTabIndex = pagerState.currentPage,
            containerColor = DeepSpaceBlack,
            contentColor = NeonCyan,
            edgePadding = 16.dp,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    height = 2.dp,
                    color = NeonCyan,
                )
            },
        ) {
            categories.forEachIndexed { index, category ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        // We'd normally use pagerState.animateScrollToPage(index)
                        // but for simplicity, using LaunchedEffect
                    },
                    text = {
                        Text(
                            text = category.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (pagerState.currentPage == index) NeonCyan else TextTertiary,
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Articles List ───────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(filteredArticles, key = { it.id }) { article ->
                NewsArticleCard(
                    article = article,
                    onClick = { onArticleClick(article.url) },
                )
            }
        }
    }
}

@Composable
private fun NewsArticleCard(
    article: NewsArticle,
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
            // Source + Time row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = article.source.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricPurple,
                )
                Text(
                    text = article.publishedAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Description
            Text(
                text = article.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

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

// Sample data for preview
private val sampleNewsArticles = listOf(
    NewsArticle(
        source = "TechCrunch",
        title = "AI Agents Are Getting Smarter: The Next Frontier in Autonomous Systems",
        description = "New breakthroughs in multi-agent AI systems are pushing the boundaries of what autonomous assistants can achieve in real-world scenarios.",
        publishedAt = "2h ago",
        url = "https://example.com/1",
        category = "Technology",
    ),
    NewsArticle(
        source = "Nature",
        title = "Quantum Computing Achieves New Milestone in Error Correction",
        description = "Researchers demonstrate a quantum error correction protocol that could pave the way for practical quantum computers.",
        publishedAt = "4h ago",
        url = "https://example.com/2",
        category = "Science",
    ),
    NewsArticle(
        source = "Reuters",
        title = "Global Markets Rally on Positive Economic Data",
        description = "Stock markets worldwide surged after better-than-expected employment figures and manufacturing output data.",
        publishedAt = "5h ago",
        url = "https://example.com/3",
        category = "Business",
    ),
    NewsArticle(
        source = "Wired",
        title = "The Rise of On-Device AI: Privacy-First Intelligence",
        description = "As AI models shrink, on-device processing is becoming the preferred approach for privacy-conscious consumers and enterprises.",
        publishedAt = "6h ago",
        url = "https://example.com/4",
        category = "Technology",
    ),
    NewsArticle(
        source = "Science Daily",
        title = "New Material Could Revolutionize Solar Energy Efficiency",
        description = "A team of physicists has developed a perovskite-silicon tandem cell achieving record efficiency levels for solar power generation.",
        publishedAt = "8h ago",
        url = "https://example.com/5",
        category = "Science",
    ),
    NewsArticle(
        source = "Bloomberg",
        title = "Central Banks Signal Cautious Approach to Rate Changes",
        description = "Major central banks indicate they will take a measured approach to interest rate adjustments amid mixed economic signals.",
        publishedAt = "10h ago",
        url = "https://example.com/6",
        category = "Business",
    ),
)
