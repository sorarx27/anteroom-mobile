package com.zayedmd.anteroom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.ui.theme.AnteroomColors

data class ProFeatureItem(
    val icon: String,
    val title: String,
    val description: String
)

private val ProFeatures = listOf(
    ProFeatureItem(
        icon = "📄",
        title = "Clean export",
        description = "Unwatermarked PDF for doctors"
    ),
    ProFeatureItem(
        icon = "👥",
        title = "Family profiles",
        description = "Manage dependents & partners"
    ),
    ProFeatureItem(
        icon = "🌐",
        title = "Translate",
        description = "Instant medical translation"
    )
)

@Composable
fun ProFeatureTeaserRow(
    onFeatureClick: (ProFeatureItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "PRO FEATURES",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = AnteroomColors.Muted,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(ProFeatures) { item ->
                ProFeatureTeaserCard(
                    item = item,
                    onClick = { onFeatureClick(item) }
                )
            }
        }
    }
}

@Composable
private fun ProFeatureTeaserCard(
    item: ProFeatureItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AnteroomColors.Surface)
            .border(1.dp, AnteroomColors.Border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.icon,
                    fontSize = 20.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AnteroomColors.BrandSecondary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "PRO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.BrandPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AnteroomColors.OnSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.description,
                fontSize = 11.sp,
                color = AnteroomColors.OnSurfaceSecondary,
                lineHeight = 15.sp,
                maxLines = 2
            )
        }
    }
}
