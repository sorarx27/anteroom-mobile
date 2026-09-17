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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zayedmd.anteroom.subscription.AnteroomPurchases
import com.zayedmd.anteroom.subscription.RevenueCatConfig
import com.zayedmd.anteroom.subscription.RevenueCatService
import com.zayedmd.anteroom.subscription.SubscriptionPackage
import com.zayedmd.anteroom.ui.theme.AnteroomColors
import kotlinx.coroutines.launch

@Composable
fun PaywallModal(
    onDismiss: () -> Unit,
    onUpgradeSuccess: () -> Unit,
    triggerReason: String = "family-profiles",
    revenueCatService: RevenueCatService = AnteroomPurchases.service
) {
    val scope = rememberCoroutineScope()
    val offering by revenueCatService.activeOffering.collectAsState()
    val isProcessing by revenueCatService.isProcessing.collectAsState()
    val lastError by revenueCatService.lastError.collectAsState()
    val isSubscribed by revenueCatService.isSubscribed.collectAsState()

    // Prices are whatever RevenueCat says they are right now, in the shopper's own currency.
    LaunchedEffect(Unit) {
        revenueCatService.clearError()
        revenueCatService.fetchOfferings()
    }

    // The entitlement can also turn on from outside this sheet - a restore on another device, a
    // renewal, a promoted App Store purchase - so close on the state, not just on the tap.
    LaunchedEffect(isSubscribed) {
        if (isSubscribed) onUpgradeSuccess()
    }

    val packages = remember(offering) {
        offering?.availablePackages?.takeIf { it.isNotEmpty() } ?: listOf(
            RevenueCatConfig.FALLBACK_MONTHLY,
            RevenueCatConfig.FALLBACK_LIFETIME
        )
    }

    var selectedPkgId by remember(packages) {
        mutableStateOf(packages.firstOrNull()?.identifier ?: RevenueCatConfig.PACKAGE_MONTHLY)
    }

    Dialog(
        onDismissRequest = {
            if (!isProcessing) {
                revenueCatService.clearError()
                onDismiss()
            }
        },
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
                            .clickable(enabled = !isProcessing) {
                                revenueCatService.clearError()
                                onDismiss()
                            },
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
                        text = when (triggerReason) {
                            "family-profiles" -> "Add family profiles with Pro"
                            "clean-export" -> "Clean doctor export with Pro"
                            "translate" -> "Instant translation with Pro"
                            else -> "Unlock the full power of Anteroom"
                        },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.OnSurface,
                        lineHeight = 30.sp
                    )

                    Text(
                        text = "Manage health paperwork for your kids, partner, or parents with watermark-free doctor summaries and instant translation.",
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
                            desc = "Download watermark-free PDFs and generate clinic QR codes."
                        )
                        HorizontalDivider(color = AnteroomColors.Border)
                        ProFeatureRow(
                            icon = "🌐",
                            title = "Instant translation",
                            desc = "Translate briefs to English or Spanish with one tap."
                        )
                    }

                    // Package selection (Monthly & Lifetime only)
                    Text(
                        text = "CHOOSE A PLAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.BrandPrimary,
                        letterSpacing = 1.5.sp
                    )

                    packages.forEach { pkg ->
                        val isSelected = selectedPkgId == pkg.identifier
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
                                .clickable(enabled = !isProcessing) { selectedPkgId = pkg.identifier }
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
                                            text = if (pkg.isLifetime) "Lifetime access" else "Monthly plan",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AnteroomColors.OnSurface
                                        )
                                        if (pkg.isLifetime) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(AnteroomColors.BrandPrimary)
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Pay once, keep forever",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = if (pkg.isLifetime) "One-time payment" else "Renews monthly, cancel anytime",
                                        fontSize = 12.sp,
                                        color = AnteroomColors.Muted
                                    )
                                }

                                Text(
                                    text = pkg.priceString,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AnteroomColors.BrandPrimary
                                )
                            }
                        }
                    }

                    // Error message banner
                    if (!lastError.isNullOrBlank()) {
                        Text(
                            text = lastError!!,
                            fontSize = 13.sp,
                            color = Color(0xFFC62828),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // CTA Button & Restore Purchases
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val selectedPackage = packages.find { it.identifier == selectedPkgId } ?: packages.firstOrNull()

                        Button(
                            onClick = {
                                if (selectedPackage != null && !isProcessing) {
                                    scope.launch {
                                        // Success means the anteroom_pro entitlement is actually
                                        // active, not merely that the call returned.
                                        if (revenueCatService.purchasePackage(selectedPackage)
                                                .getOrDefault(false)
                                        ) {
                                            onUpgradeSuccess()
                                        }
                                    }
                                }
                            },
                            enabled = !isProcessing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AnteroomColors.BrandPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "Continue with ${selectedPackage?.priceString ?: "$7.99/mo"}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (revenueCatService.isSimulated) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Sandbox simulation on this platform - no store charge.",
                                fontSize = 11.sp,
                                color = AnteroomColors.Muted,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                if (!isProcessing) {
                                    scope.launch {
                                        if (revenueCatService.restorePurchases()
                                                .getOrDefault(false)
                                        ) {
                                            onUpgradeSuccess()
                                        }
                                    }
                                }
                            },
                            enabled = !isProcessing
                        ) {
                            Text(
                                text = "Restore purchases",
                                fontSize = 12.sp,
                                color = AnteroomColors.Muted,
                                fontWeight = FontWeight.Medium
                            )
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
