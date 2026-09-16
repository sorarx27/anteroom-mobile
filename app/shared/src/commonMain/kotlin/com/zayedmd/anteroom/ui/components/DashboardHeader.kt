package com.zayedmd.anteroom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.model.AppUser
import com.zayedmd.anteroom.model.Profile
import com.zayedmd.anteroom.ui.theme.AnteroomColors

@Composable
fun DashboardHeader(
    user: AppUser,
    activeProfile: Profile?,
    isSubscribed: Boolean,
    onUpgradeClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtitle = if (activeProfile != null) {
        "${activeProfile.relationship.label} · ${activeProfile.name}"
    } else {
        user.name ?: "You"
    }

    val initials = user.name?.trim()?.split("\\s+".toRegex())
        ?.filter { it.isNotEmpty() }
        ?.let { parts ->
            if (parts.size >= 2) "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
            else parts.firstOrNull()?.take(1)?.uppercase() ?: "A"
        } ?: "A"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Anteroom",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AnteroomColors.BrandPrimary,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = AnteroomColors.OnSurfaceSecondary,
                maxLines = 1
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isSubscribed) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = AnteroomColors.BrandPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "★",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                        Text(
                            text = "PRO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = AnteroomColors.Surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AnteroomColors.Border),
                    modifier = Modifier.clickable { onUpgradeClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "✦",
                            fontSize = 12.sp,
                            color = AnteroomColors.BrandPrimary
                        )
                        Text(
                            text = "Upgrade",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AnteroomColors.BrandPrimary
                        )
                    }
                }
            }

            // User Avatar (Click to sign out / account menu)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AnteroomColors.BrandSecondary)
                    .border(1.dp, AnteroomColors.BrandPrimary.copy(alpha = 0.2f), CircleShape)
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AnteroomColors.BrandPrimary
                )
            }
        }
    }
}
