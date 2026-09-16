package com.zayedmd.anteroom.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
fun BriefDraftScreen(
    briefId: String,
    briefsRepository: BriefsRepository,
    isSubscribed: Boolean,
    onBack: () -> Unit,
    onAddMorePhotos: () -> Unit,
    onBriefGenerated: (Brief) -> Unit,
    onDiscardDraft: () -> Unit,
    onUpgradeRequired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var brief by remember { mutableStateOf<Brief?>(null) }
    var selectedDocType by remember { mutableStateOf(DocType.other) }
    var selectedLanguage by remember { mutableStateOf(BriefLanguage.en) }
    var isDetecting by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var deletingPhotoId by remember { mutableStateOf<String?>(null) }
    var inspectingPhoto by remember { mutableStateOf<Pair<BriefPhoto, Int>?>(null) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    // Load initial brief data
    LaunchedEffect(briefId) {
        val loaded = briefsRepository.getBrief(briefId)
        if (loaded != null) {
            brief = loaded
            selectedDocType = loaded.doc_type
            if (loaded.detected_doc_type == null && loaded.photos.isNotEmpty()) {
                isDetecting = true
                val detected = briefsRepository.detectDocType(briefId)
                brief = briefsRepository.getBrief(briefId)
                selectedDocType = detected
                isDetecting = false
            }
        }
    }

    val currentBrief = brief
    val photos = currentBrief?.photos ?: emptyList()

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
                        text = "DRAFT BRIEF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.BrandPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Review & label",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.OnSurface
                    )
                }

                IconButton(
                    onClick = { showDiscardConfirmDialog = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFEEEE))
                ) {
                    Text(
                        text = "🗑️",
                        fontSize = 14.sp
                    )
                }
            }

            HorizontalDivider(color = AnteroomColors.Border, thickness = 0.5.dp)

            // Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Document Type Selection
                DocTypeSelectorSection(
                    selectedDocType = selectedDocType,
                    onSelectDocType = { newType ->
                        selectedDocType = newType
                        scope.launch {
                            briefsRepository.updateBriefDocType(briefId, newType)
                        }
                    },
                    detectedDocType = currentBrief?.detected_doc_type,
                    confidence = currentBrief?.detected_confidence,
                    isDetecting = isDetecting,
                    onRedetectClick = {
                        scope.launch {
                            isDetecting = true
                            val detected = briefsRepository.detectDocType(briefId)
                            brief = briefsRepository.getBrief(briefId)
                            selectedDocType = detected
                            isDetecting = false
                        }
                    },
                    hasPhotos = photos.isNotEmpty()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Language Selection
                BriefLanguageSelectorSection(
                    selectedLanguage = selectedLanguage,
                    onSelectLanguage = { selectedLanguage = it },
                    defaultLanguage = BriefLanguage.en,
                    isSubscribed = isSubscribed,
                    onUpgradeRequired = onUpgradeRequired
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Photos Grid
                DraftPhotoGrid(
                    photos = photos,
                    onAddMoreClick = onAddMorePhotos,
                    onDeletePhotoClick = { photoId ->
                        scope.launch {
                            deletingPhotoId = photoId
                            brief = briefsRepository.removePhotoFromBrief(briefId, photoId)
                            deletingPhotoId = null
                        }
                    },
                    onPhotoClick = { photo, pageIndex ->
                        inspectingPhoto = Pair(photo, pageIndex)
                    },
                    deletingPhotoId = deletingPhotoId
                )

                Spacer(modifier = Modifier.height(32.dp))
            }

            // Bottom Sticky Action Bar
            Surface(
                color = AnteroomColors.Surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    val canGenerate = photos.isNotEmpty() && !isGenerating

                    Button(
                        onClick = {
                            scope.launch {
                                isGenerating = true
                                val generated = briefsRepository.generateBrief(briefId, selectedLanguage)
                                isGenerating = false
                                onBriefGenerated(generated)
                            }
                        },
                        enabled = canGenerate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AnteroomColors.BrandPrimary,
                            disabledContainerColor = Color(0xFFC4D1CB)
                        ),
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "✨", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (photos.isEmpty()) "Add a photo to continue" else "Generate brief",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Fullscreen Inspection Modal
        if (inspectingPhoto != null) {
            val (photo, pageIndex) = inspectingPhoto!!
            FullscreenPhotoInspectionModal(
                photo = photo,
                pageIndex = pageIndex,
                totalPages = photos.size,
                onDismiss = { inspectingPhoto = null },
                onDelete = {
                    scope.launch {
                        brief = briefsRepository.removePhotoFromBrief(briefId, photo.photo_id)
                        inspectingPhoto = null
                    }
                }
            )
        }

        // Processing Dialog
        if (isGenerating) {
            BriefGenerationProcessingDialog(
                isTranslating = selectedLanguage != BriefLanguage.en,
                outputLanguage = selectedLanguage
            )
        }

        // Discard Confirmation Dialog
        if (showDiscardConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirmDialog = false },
                title = {
                    Text(
                        text = "Discard draft?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to discard this draft brief and its photos? This action cannot be undone.",
                        fontSize = 14.sp,
                        color = AnteroomColors.Muted
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDiscardConfirmDialog = false
                            scope.launch {
                                briefsRepository.deleteBrief(briefId)
                                onDiscardDraft()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                    ) {
                        Text("Discard", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardConfirmDialog = false }) {
                        Text("Keep editing", color = AnteroomColors.OnSurface)
                    }
                }
            )
        }
    }
}
