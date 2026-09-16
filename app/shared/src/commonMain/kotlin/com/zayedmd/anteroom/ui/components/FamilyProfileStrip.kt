package com.zayedmd.anteroom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.model.Profile
import com.zayedmd.anteroom.model.Relationship
import com.zayedmd.anteroom.ui.theme.AnteroomColors

@Composable
fun FamilyProfileStrip(
    profiles: List<Profile>,
    activeProfileId: String?,
    isSubscribed: Boolean,
    onSelectProfile: (Profile) -> Unit,
    onAddProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedProfiles = profiles.sortedWith(
        compareByDescending<Profile> { it.is_self }
            .thenBy { it.name }
    )

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(sortedProfiles, key = { it.profile_id }) { profile ->
            val isSelected = profile.profile_id == activeProfileId
            val isLocked = !profile.is_self && !isSubscribed

            ProfileStripItem(
                profile = profile,
                isSelected = isSelected,
                isLocked = isLocked,
                onClick = { onSelectProfile(profile) }
            )
        }

        // Add Member Button
        item {
            AddProfileStripItem(
                isSubscribed = isSubscribed,
                onClick = onAddProfileClick
            )
        }
    }
}

@Composable
private fun ProfileStripItem(
    profile: Profile,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) AnteroomColors.BrandSecondary
                        else AnteroomColors.Surface
                    )
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) AnteroomColors.BrandPrimary
                        else AnteroomColors.Border,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.initials(),
                    fontSize = 17.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) AnteroomColors.BrandPrimary else AnteroomColors.OnSurfaceSecondary
                )
            }

            if (isLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(AnteroomColors.Muted),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔒",
                        fontSize = 9.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (profile.is_self) "You" else profile.name,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) AnteroomColors.BrandPrimary else AnteroomColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (!profile.is_self) {
            Text(
                text = profile.relationship.label,
                fontSize = 10.sp,
                color = AnteroomColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AddProfileStripItem(
    isSubscribed: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(AnteroomColors.Surface)
                .border(
                    width = 1.dp,
                    color = AnteroomColors.Border,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = AnteroomColors.BrandPrimary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Add",
            fontSize = 12.sp,
            color = AnteroomColors.OnSurfaceSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        Text(
            text = if (isSubscribed) "Member" else "Pro",
            fontSize = 10.sp,
            color = AnteroomColors.Muted,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
