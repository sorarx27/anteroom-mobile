package com.zayedmd.anteroom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.model.Brief
import com.zayedmd.anteroom.model.BriefStatus
import com.zayedmd.anteroom.model.DocType
import com.zayedmd.anteroom.ui.theme.AnteroomColors

@Composable
fun BriefCard(
    brief: Brief,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isComplete = brief.status == BriefStatus.complete
    val docTypeIcon = when (brief.doc_type) {
        DocType.referral -> "✉️"
        DocType.med_list -> "💊"
        DocType.lab_result -> "🧪"
        DocType.other -> "📄"
    }

    val statusBg = if (isComplete) AnteroomColors.BrandSecondary else Color(0xFFFFF3CD)
    val statusText = if (isComplete) AnteroomColors.BrandPrimary else Color(0xFF856404)
    val statusLabel = if (isComplete) "Doctor-ready" else "Draft"

    val content = brief.content
    val sourceLang = brief.source_language

    val title = content?.referral_reason
        ?: if (brief.photos.isNotEmpty()) "${brief.doc_type.label} (${brief.photos.size} pages)"
        else "${brief.doc_type.label} (Draft)"

    val previewText: String = when {
        content?.referral_reason != null -> content.referral_reason!!
        brief.photos.isNotEmpty() -> "${brief.photos.size} photo(s) attached · Tap to review and generate brief"
        else -> "Empty draft · Tap to add documents"
    }

    val flagsCount = content?.flagged_items?.size ?: 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AnteroomColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, AnteroomColors.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Doc Type + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AnteroomColors.BrandSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = docTypeIcon,
                            fontSize = 16.sp
                        )
                    }

                    Column {
                        Text(
                            text = brief.doc_type.label,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AnteroomColors.OnSurface
                        )
                        Text(
                            text = if (brief.photos.size == 1) "1 photo" else "${brief.photos.size} photos",
                            fontSize = 11.sp,
                            color = AnteroomColors.Muted
                        )
                    }
                }

                // Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(statusBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusText
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Body preview
            Text(
                text = previewText,
                fontSize = 13.sp,
                color = AnteroomColors.OnSurfaceSecondary,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // High risk flags badge if any
            if (flagsCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFBEBEB))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "⚠️", fontSize = 11.sp)
                    Text(
                        text = if (flagsCount == 1) "1 high-risk alert" else "$flagsCount high-risk alerts",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AnteroomColors.Error
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap to view full brief →",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = AnteroomColors.BrandPrimary
                )

                if (sourceLang != null) {
                    Text(
                        text = sourceLang.nativeName,
                        fontSize = 11.sp,
                        color = AnteroomColors.Muted
                    )
                }
            }
        }
    }
}
