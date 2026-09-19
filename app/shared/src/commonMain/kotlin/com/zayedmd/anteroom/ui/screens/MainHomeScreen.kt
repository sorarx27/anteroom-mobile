package com.zayedmd.anteroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.zayedmd.anteroom.auth.AuthService
import com.zayedmd.anteroom.model.AppUser
import com.zayedmd.anteroom.ui.theme.AnteroomColors
import com.zayedmd.anteroom.ui.userMessage
import kotlinx.coroutines.launch

@Composable
fun MainHomeScreen(
    user: AppUser,
    authService: AuthService
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var confirmingDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AnteroomColors.SurfaceSecondary)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ANTEROOM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.BrandPrimary,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hello, ${user.name?.ifEmpty { "Patient" } ?: "Patient"}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.OnSurface
                    )
                }

                // Profile initial avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AnteroomColors.BrandSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = user.name?.firstOrNull()?.uppercase() ?: user.email.firstOrNull()?.uppercase() ?: "A"
                    Text(
                        text = initial,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.BrandPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Profile status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AnteroomColors.Surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Account Status",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AnteroomColors.Muted
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFDCFCE7))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Profile Completed",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AnteroomColors.Success
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = AnteroomColors.Border)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Email: ${user.email}",
                        fontSize = 14.sp,
                        color = AnteroomColors.OnSurfaceSecondary
                    )
                    val dob = user.dob
                    if (!dob.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Date of Birth: $dob",
                            fontSize = 14.sp,
                            color = AnteroomColors.OnSurfaceSecondary
                        )
                    }
                    val lang = user.language
                    if (!lang.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Language: ${lang.uppercase()}",
                            fontSize = 14.sp,
                            color = AnteroomColors.OnSurfaceSecondary
                        )
                    }
                    val country = user.country
                    if (!country.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Country: $country",
                            fontSize = 14.sp,
                            color = AnteroomColors.OnSurfaceSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Sign-in method: ${user.provider.replaceFirstChar { it.uppercase() }}",
                        fontSize = 13.sp,
                        color = AnteroomColors.Muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action / Briefs Overview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AnteroomColors.BrandPrimary)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Doctor-Ready Briefs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Turn medical papers into organized clinical summaries. Ready for your appointment.",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Sign out button
            OutlinedButton(
                onClick = {
                    scope.launch { authService.signOut() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AnteroomColors.Error
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(AnteroomColors.Error.copy(alpha = 0.5f))
                )
            ) {
                Text(
                    text = "Sign out",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Account deletion. Plain text rather than a second red button:
            // it has to be findable without being adjacent enough to Sign out
            // to be hit by mistake. App Store Review Guideline 5.1.1(v)
            // requires it to be here at all -- pointing at a support email
            // does not satisfy it.
            TextButton(
                onClick = { deleteError = null; confirmingDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Delete account",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AnteroomColors.Muted
                )
            }

            if (deleteError != null) {
                Text(
                    text = deleteError!!,
                    fontSize = 12.sp,
                    color = AnteroomColors.Error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmingDelete = false },
            title = { Text("Delete your account?") },
            text = {
                Text(
                    "This erases your account, every brief, every page you photographed, " +
                        "and your family profiles. It cannot be undone and it cannot be " +
                        "recovered by support.\n\n" +
                        "An active subscription is billed by the App Store or Google Play, " +
                        "not by Anteroom. Cancel it in your store account — deleting here " +
                        "does not stop it."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        deleteError = null
                        scope.launch {
                            try {
                                authService.deleteAccount()
                                // No state reset on success: deleteAccount signs
                                // out, and this whole screen leaves the tree.
                            } catch (e: Throwable) {
                                deleteError = e.userMessage()
                                deleting = false
                                confirmingDelete = false
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (deleting) "Deleting…" else "Delete permanently",
                        color = AnteroomColors.Error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { confirmingDelete = false }) {
                    Text("Keep my account")
                }
            }
        )
    }
}
