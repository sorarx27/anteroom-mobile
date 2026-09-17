package com.zayedmd.anteroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.auth.AuthService
import com.zayedmd.anteroom.data.BriefsRepository
import com.zayedmd.anteroom.data.BriefsRepositoryImpl
import com.zayedmd.anteroom.data.ProfilesRepository
import com.zayedmd.anteroom.data.ProfilesRepositoryImpl
import com.zayedmd.anteroom.model.AppUser
import com.zayedmd.anteroom.model.Brief
import com.zayedmd.anteroom.model.Profile
import com.zayedmd.anteroom.ui.components.BriefCard
import com.zayedmd.anteroom.ui.components.DashboardHeader
import com.zayedmd.anteroom.ui.components.EmptyDashboardView
import com.zayedmd.anteroom.ui.components.FamilyProfileStrip
import com.zayedmd.anteroom.ui.components.ProFeatureTeaserRow
import com.zayedmd.anteroom.ui.components.SnapPaperworkBottomBar
import com.zayedmd.anteroom.ui.components.UpgradeBanner
import com.zayedmd.anteroom.ui.runSafely
import com.zayedmd.anteroom.ui.theme.AnteroomColors
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    user: AppUser,
    authService: AuthService,
    profilesRepository: ProfilesRepository = remember { ProfilesRepositoryImpl() },
    briefsRepository: BriefsRepository = remember { BriefsRepositoryImpl() },
    isSubscribed: Boolean = false,
    onUpgradeClick: () -> Unit = {},
    onAddProfileClick: () -> Unit = {},
    onEditProfileClick: (Profile) -> Unit = {},
    onSnapClick: () -> Unit = {},
    onBriefClick: (Brief) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var briefs by remember { mutableStateOf<List<Brief>>(emptyList()) }
    var activeProfileId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Load profiles once
    LaunchedEffect(user.user_id) {
        isLoading = true
        // finally, so a failed profile read leaves an empty dashboard the user
        // can retry from rather than a spinner that never stops.
        try {
            runSafely {
                val list = profilesRepository.getProfiles(user.user_id)
                profiles = list
                activeProfileId =
                    list.find { it.is_self }?.profile_id ?: list.firstOrNull()?.profile_id
            }
        } finally {
            isLoading = false
        }
    }

    // Load briefs whenever the active profile changes
    LaunchedEffect(activeProfileId) {
        if (activeProfileId != null) {
            runSafely { briefs = briefsRepository.getBriefs(activeProfileId) }
        }
    }

    val activeProfile = profiles.find { it.profile_id == activeProfileId }

    Scaffold(
        containerColor = AnteroomColors.SurfaceSecondary,
        topBar = {
            Column(
                modifier = Modifier
                    .background(AnteroomColors.Surface)
                    .statusBarsPadding()
            ) {
                DashboardHeader(
                    user = user,
                    activeProfile = activeProfile,
                    isSubscribed = isSubscribed,
                    onUpgradeClick = onUpgradeClick,
                    onAvatarClick = {
                        scope.launch { authService.signOut() }
                    }
                )

                HorizontalDivider(color = AnteroomColors.Border, thickness = 0.5.dp)

                FamilyProfileStrip(
                    profiles = profiles,
                    activeProfileId = activeProfileId,
                    isSubscribed = isSubscribed,
                    onSelectProfile = { selected ->
                        if (!selected.is_self && !isSubscribed) {
                            onUpgradeClick()
                        } else {
                            activeProfileId = selected.profile_id
                        }
                    },
                    onAddProfileClick = onAddProfileClick,
                    onEditProfileClick = onEditProfileClick
                )

                HorizontalDivider(color = AnteroomColors.Border, thickness = 0.5.dp)
            }
        },
        bottomBar = {
            SnapPaperworkBottomBar(onSnapClick = onSnapClick)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Free Tier Upgrade Banner & Features
            if (!isSubscribed) {
                Spacer(modifier = Modifier.height(12.dp))
                UpgradeBanner(onUpgradeClick = onUpgradeClick)

                Spacer(modifier = Modifier.height(10.dp))
                ProFeatureTeaserRow(onFeatureClick = { onUpgradeClick() })
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Briefs or Empty State
            if (briefs.isEmpty()) {
                EmptyDashboardView(
                    profileName = activeProfile?.name ?: "this profile",
                    onSnapClick = onSnapClick
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BRIEFS (${briefs.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AnteroomColors.Muted,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = activeProfile?.name ?: "",
                            fontSize = 12.sp,
                            color = AnteroomColors.OnSurfaceSecondary
                        )
                    }

                    briefs.forEach { brief ->
                        BriefCard(
                            brief = brief,
                            onClick = { onBriefClick(brief) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
