package com.zayedmd.anteroom.ui.screens

import androidx.compose.foundation.background
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
import com.zayedmd.anteroom.data.BriefsRepository
import com.zayedmd.anteroom.model.*
import com.zayedmd.anteroom.ui.components.*
import com.zayedmd.anteroom.ui.theme.AnteroomColors
import kotlinx.coroutines.launch

@Composable
fun BriefViewScreen(
    briefId: String,
    briefsRepository: BriefsRepository,
    isSubscribed: Boolean,
    onBack: () -> Unit,
    onEditDraft: () -> Unit,
    onUpgradeRequired: () -> Unit,
    onOpenShareLink: (String) -> Unit = {},
    onDownloadPdf: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var brief by remember { mutableStateOf<Brief?>(null) }
    var activeLang by remember { mutableStateOf<BriefLanguage?>(null) }
    var isTranslating by remember { mutableStateOf(false) }
    var translateError by remember { mutableStateOf<String?>(null) }
    var inspectingPhoto by remember { mutableStateOf<Pair<BriefPhoto, Int>?>(null) }

    LaunchedEffect(briefId) {
        brief = briefsRepository.getBrief(briefId)
    }

    val currentBrief = brief
    if (currentBrief == null) {
        Box(
            modifier = modifier.fillMaxSize().background(AnteroomColors.Surface),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AnteroomColors.BrandPrimary)
        }
        return
    }

    val sourceLang = currentBrief.source_language ?: BriefLanguage.en
    val currentLang = activeLang ?: sourceLang

    val activeContent = if (currentLang == sourceLang) {
        currentBrief.content
    } else {
        currentBrief.content_translations?.get(currentLang.key) ?: currentBrief.content
    }

    val photos = currentBrief.photos

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AnteroomColors.Surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AnteroomColors.Surface)
                ) {
                    Text(
                        text = "←",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.OnSurface
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentBrief.doc_type.label.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.BrandPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Pre-visit brief",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.OnSurface
                    )
                }

                IconButton(
                    onClick = onEditDraft,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AnteroomColors.SurfaceSecondary)
                ) {
                    Text(text = "✏️", fontSize = 14.sp)
                }
            }

            HorizontalDivider(color = AnteroomColors.Border, thickness = 0.5.dp)

            // Free upgrade ribbon
            if (!isSubscribed) {
                Surface(
                    onClick = onUpgradeRequired,
                    color = AnteroomColors.BrandPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "✨", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ANTEROOM FREE — Upgrade for clean export & translation",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Scrollable Clinical Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Translation Selector
                BriefTranslationRow(
                    currentLanguage = currentLang,
                    sourceLanguage = sourceLang,
                    availableLanguages = currentBrief.available_languages,
                    isSubscribed = isSubscribed,
                    isTranslating = isTranslating,
                    translateError = translateError,
                    onLanguageSelected = { targetLang ->
                        if (targetLang == sourceLang) {
                            activeLang = targetLang
                            return@BriefTranslationRow
                        }
                        if (currentBrief.content_translations?.containsKey(targetLang.key) == true) {
                            activeLang = targetLang
                            return@BriefTranslationRow
                        }
                        scope.launch {
                            isTranslating = true
                            translateError = null
                            try {
                                val updated = briefsRepository.translateBrief(briefId, targetLang)
                                brief = updated
                                activeLang = targetLang
                            } catch (e: Exception) {
                                translateError = e.message ?: "Could not translate brief"
                            } finally {
                                isTranslating = false
                            }
                        }
                    },
                    onUpgradeRequired = onUpgradeRequired
                )

                Spacer(modifier = Modifier.height(8.dp))

                ClinicalDisclaimerText()

                // Flagged Review Banner
                if (!activeContent?.flagged_items.isNullOrEmpty()) {
                    FlaggedAlertsBanner(flaggedItems = activeContent!!.flagged_items)
                }

                // Patient Info Card
                PatientInfoCard(patient = activeContent?.patient)

                // Referral Reason / Chief Complaint
                ReferralReasonCard(referralReason = activeContent?.referral_reason)

                // Medications List
                MedicationsListCard(medications = activeContent?.medications ?: emptyList())

                // Allergies List
                AllergiesListCard(allergies = activeContent?.allergies ?: emptyList())

                // Clinic QR Card
                ClinicQrCard(
                    shareUrlPath = currentBrief.share_url_path,
                    onOpenLink = onOpenShareLink
                )

                // Original Source Photos
                OriginalPhotosSection(
                    photos = photos,
                    onPhotoClick = { photo, index ->
                        inspectingPhoto = Pair(photo, index)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Bottom Sticky PDF Download Footer
            BriefPdfFooter(
                isSubscribed = isSubscribed,
                onDownloadPdf = onDownloadPdf
            )
        }

        // Photo Fullscreen Inspection Modal
        if (inspectingPhoto != null) {
            val (photo, pageIndex) = inspectingPhoto!!
            PhotoVerificationModal(
                photo = photo,
                pageIndex = pageIndex,
                totalPages = photos.size,
                onDismiss = { inspectingPhoto = null }
            )
        }
    }
}
