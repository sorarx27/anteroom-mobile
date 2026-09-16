package com.zayedmd.anteroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.model.BiologicalSex
import com.zayedmd.anteroom.model.Profile
import com.zayedmd.anteroom.model.Relationship
import com.zayedmd.anteroom.ui.components.*
import com.zayedmd.anteroom.ui.theme.AnteroomColors

@Composable
fun ProfileAddEditScreen(
    editingProfile: Profile? = null,
    isSubscribed: Boolean = false,
    onBack: () -> Unit,
    onSaveProfile: (name: String, relationship: Relationship, dob: String?, sex: BiologicalSex?) -> Unit,
    onDeleteProfile: ((profileId: String) -> Unit)? = null,
    onUpgradeRequired: () -> Unit = {}
) {
    val isEditing = editingProfile != null
    val isEditingSelf = editingProfile?.is_self == true

    var name by remember(editingProfile) { mutableStateOf(editingProfile?.name ?: "") }
    var relationship by remember(editingProfile) {
        mutableStateOf(editingProfile?.relationship ?: Relationship.partner)
    }

    val initialDobParts = remember(editingProfile) {
        val parts = editingProfile?.dob?.split("-") ?: emptyList()
        Triple(
            parts.getOrNull(0) ?: "",
            parts.getOrNull(1) ?: "",
            parts.getOrNull(2) ?: ""
        )
    }

    var dobYear by remember(initialDobParts) { mutableStateOf(initialDobParts.first) }
    var dobMonth by remember(initialDobParts) { mutableStateOf(initialDobParts.second) }
    var dobDay by remember(initialDobParts) { mutableStateOf(initialDobParts.third) }

    var sex by remember(editingProfile) { mutableStateOf(editingProfile?.sex) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val formState = ProfileFormState(
        name = name,
        relationship = if (isEditingSelf) Relationship.self else relationship,
        dobYear = dobYear,
        dobMonth = dobMonth,
        dobDay = dobDay,
        sex = sex,
        isSelf = isEditingSelf
    )

    Scaffold(
        containerColor = AnteroomColors.Surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back / Close circular button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AnteroomColors.SurfaceSecondary)
                        .border(1.dp, AnteroomColors.Border, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.OnSurface
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEditing) "EDIT PROFILE" else "NEW PROFILE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.BrandPrimary,
                        letterSpacing = 1.8.sp
                    )
                    Text(
                        text = if (isEditing) {
                            if (isEditingSelf) "You" else "Edit member"
                        } else {
                            "Add family member"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.OnSurface
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.96f),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            if (!isEditing && !isSubscribed) {
                                onUpgradeRequired()
                                return@Button
                            }
                            if (formState.canSubmit && !isSubmitting) {
                                isSubmitting = true
                                onSaveProfile(
                                    formState.name.trim(),
                                    formState.relationship,
                                    formState.formattedDob,
                                    formState.sex
                                )
                            }
                        },
                        enabled = formState.canSubmit && !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AnteroomColors.BrandPrimary,
                            contentColor = Color.White,
                            disabledContainerColor = AnteroomColors.BrandPrimary.copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.8f)
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isEditing) {
                                    "Save changes"
                                } else if (isSubscribed) {
                                    "Add member"
                                } else {
                                    "Upgrade to add"
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Informational banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AnteroomColors.BrandTertiary)
                    .border(1.dp, AnteroomColors.BrandSecondary, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "🛡️", fontSize = 18.sp)
                    Text(
                        text = "Briefs, medication lists, and medical documents will be securely organized under this profile.",
                        fontSize = 13.sp,
                        color = AnteroomColors.OnSurfaceSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            // Pro notice if not subscribed and adding new
            if (!isSubscribed && !isEditing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFFF9E6))
                        .border(1.dp, Color(0xFFFFE082), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "✦", fontSize = 16.sp, color = AnteroomColors.BrandPrimary)
                        Text(
                            text = "Family profiles are an Anteroom Pro feature. Upgrade to manage health records for dependents & partners.",
                            fontSize = 13.sp,
                            color = Color(0xFF6D4C00),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Name field
            ProfileNameInput(
                value = name,
                onValueChange = { name = it }
            )

            // Relationship selector (only for non-self profiles)
            if (!isEditingSelf) {
                RelationshipSelector(
                    selected = relationship,
                    onSelect = { relationship = it }
                )
            }

            // Date of birth
            ProfileDobInput(
                dobYear = dobYear,
                dobMonth = dobMonth,
                dobDay = dobDay,
                onYearChange = { dobYear = it },
                onMonthChange = { dobMonth = it },
                onDayChange = { dobDay = it },
                isValid = formState.isDobValid
            )

            // Biological sex selector
            BiologicalSexSelector(
                selected = sex,
                onSelect = { sex = it }
            )

            // Delete action for existing non-self profile
            if (isEditing && !isEditingSelf && onDeleteProfile != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = AnteroomColors.Border)
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AnteroomColors.Error
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AnteroomColors.Error.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Remove & archive briefs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AnteroomColors.Error
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && editingProfile != null && onDeleteProfile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Remove ${editingProfile.name}?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AnteroomColors.OnSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove this profile? All associated briefs and medical records will be archived.",
                    fontSize = 14.sp,
                    color = AnteroomColors.OnSurfaceSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteProfile(editingProfile.profile_id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnteroomColors.Error,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.SemiBold,
                        color = AnteroomColors.OnSurfaceSecondary
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
