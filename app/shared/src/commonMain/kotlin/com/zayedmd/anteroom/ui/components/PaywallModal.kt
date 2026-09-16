package com.zayedmd.anteroom.ui.components

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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zayedmd.anteroom.ui.theme.AnteroomColors

data class PaywallPackage(
    val id: String,
    val title: String,
    val price: String,
    val cadence: String,
    val badge: String? = null
)

@Composable
fun PaywallModal(
    onDismiss: () -> Unit,
    onUpgradeSuccess: () -> Unit,
    triggerReason: String = "family-profiles"
) {
    val packages = listOf(
        PaywallPackage("\$rc_annual", "Annual", "$49.99", "per year", badge = "Best value"),
        PaywallPackage("\$rc_monthly", "Monthly", "$7.99", "per month"),
        PaywallPackage("\$rc_lifetime", "Lifetime", "$129.99", "one time")
    )

    var selectedPkgId by remember { mutableStateOf("\$rc_annual") }
    var isUpgrading by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(AnteroomColors.Surface),
            color = AnteroomColors.Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Header with Close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AnteroomColors.SurfaceSecondary)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AnteroomColors.OnSurface)
                    }

                    Text(
                        text = "ANTEROOM PRO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.BrandPrimary,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.size(38.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title & pitch
                    Text(
                        text = if (triggerReason == "family-profiles") {
                            "Add family profiles with Pro"
                        } else {
                            "Unlock the full power of Anteroom"
                        },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.OnSurface,
                        lineHeight = 30.sp
                    )

                    Text(
                        text = "Manage health paperwork for your kids, partner, or parents under one unified account.",
                        fontSize = 14.sp,
                        color = AnteroomColors.OnSurfaceSecondary,
                        lineHeight = 20.sp
                    )

                    // Features list
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AnteroomColors.SurfaceSecondary)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ProFeatureRow(
                            icon = "👥",
                            title = "Family profiles",
                            desc = "Add unlimited dependents and family members."
                        )
                        HorizontalDivider(color = AnteroomColors.Border)
                        ProFeatureRow(
                            icon = "📄",
                            title = "Clean doctor export",
                            desc = "Download watermarked-free PDFs and generate clinic QR codes."
                        )
                        HorizontalDivider(color = AnteroomColors.Border)
                        ProFeatureRow(
                            icon = "🌐",
                            title = "Instant translation",
                            desc = "Translate briefs to English or Spanish with one tap."
                        )
                    }

                    // Package selection
                    Text(
                        text = "CHOOSE A PLAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.BrandPrimary,
                        letterSpacing = 1.5.sp
                    )

                    packages.forEach { pkg ->
                        val isSelected = selectedPkgId == pkg.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) AnteroomColors.BrandTertiary else Color.White)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) AnteroomColors.BrandPrimary else AnteroomColors.Border,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedPkgId = pkg.id }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = pkg.title,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AnteroomColors.OnSurface
                                        )
                                        if (pkg.badge != null) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(AnteroomColors.BrandPrimary)
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = pkg.badge,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = pkg.cadence,
                                        fontSize = 12.sp,
                                        color = AnteroomColors.Muted
                                    )
                                }

                                Text(
                                    text = pkg.price,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AnteroomColors.BrandPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // CTA Button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!isUpgrading) {
                                    isUpgrading = true
                                    onUpgradeSuccess()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AnteroomColors.BrandPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            if (isUpgrading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Continue with ${packages.firstOrNull { it.id == selectedPkgId }?.title ?: "Plan"}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProFeatureRow(
    icon: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = icon, fontSize = 20.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AnteroomColors.OnSurface
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = AnteroomColors.OnSurfaceSecondary,
                lineHeight = 16.sp
            )
        }
    }
}
