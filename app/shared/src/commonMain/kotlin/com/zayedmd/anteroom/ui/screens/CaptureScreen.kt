package com.zayedmd.anteroom.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.zayedmd.anteroom.media.CapturedPhoto
import com.zayedmd.anteroom.ui.components.CaptureThumbnailStrip
import com.zayedmd.anteroom.media.PermissionResult
import com.zayedmd.anteroom.media.PermissionType
import com.zayedmd.anteroom.media.rememberMediaPicker
import com.zayedmd.anteroom.model.Profile
import com.zayedmd.anteroom.ui.theme.AnteroomColors

@Composable
fun CaptureScreen(
    activeProfile: Profile?,
    briefId: String?,
    photos: List<CapturedPhoto> = emptyList(),
    uploadingCount: Int = 0,
    errorMessage: String? = null,
    onPhotosCaptured: (List<CapturedPhoto>) -> Unit = {},
    onCancel: () -> Unit = {},
    onReviewClick: () -> Unit = {}
) {
    var permissionDialog by remember { mutableStateOf<PermissionType?>(null) }

    val mediaPicker = rememberMediaPicker(
        onPhotosPicked = { pickedPhotos ->
            onPhotosCaptured(pickedPhotos)
        },
        onPermissionDenied = { result ->
            permissionDialog = result.type
        }
    )

    val totalPhotos = photos.size

    Scaffold(
        topBar = {
            CaptureHeader(
                activeProfile = activeProfile,
                onCancel = onCancel
            )
        },
        bottomBar = {
            CaptureBottomBar(
                photoCount = totalPhotos,
                uploadingCount = uploadingCount,
                onReviewClick = onReviewClick
            )
        },
        containerColor = AnteroomColors.SurfaceSecondary
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Intake Guide Card
            CaptureIntakeGuideCard(photoCount = totalPhotos)

            Spacer(modifier = Modifier.height(24.dp))

            // Error Display if any
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFDE8E8),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFC81E1E),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Action Buttons: Take Photo & From Gallery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Camera Button
                Button(
                    onClick = { mediaPicker.launchCamera() },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnteroomColors.BrandPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Text("📷", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Take photo",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Gallery Button
                OutlinedButton(
                    onClick = { mediaPicker.launchGallery() },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = AnteroomColors.BrandPrimary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AnteroomColors.Border)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("🖼️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "From gallery",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.BrandPrimary
                    )
                }
            }

            CaptureThumbnailStrip(photos = photos)

            // Uploading progress row
            AnimatedVisibility(
                visible = uploadingCount > 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AnteroomColors.BrandSecondary)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = AnteroomColors.BrandPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Uploading $uploadingCount photo${if (uploadingCount == 1) "" else "s"}…",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = AnteroomColors.BrandPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Tip Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AnteroomColors.Border, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "💡 QUICK TIPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = AnteroomColors.BrandPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TipItem("📄", "Lay paperwork flat in good lighting.")
                    TipItem("🔍", "Ensure dosages, medicine names, and labels are clear.")
                    TipItem("➕", "You can capture multiple pages before generating.")
                }
            }
        }
    }

    // Permission Dialog if permission denied
    if (permissionDialog != null) {
        val isCamera = permissionDialog == PermissionType.Camera
        AlertDialog(
            onDismissRequest = { permissionDialog = null },
            title = {
                Text(
                    text = if (isCamera) "Camera access required" else "Photos access required",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isCamera) {
                        "Anteroom needs camera access to photograph discharge papers, prescriptions, and lab results."
                    } else {
                        "Anteroom needs photo access so you can select document pictures from your gallery."
                    },
                    fontSize = 14.sp,
                    color = AnteroomColors.Muted
                )
            },
            confirmButton = {
                Button(
                    onClick = { permissionDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AnteroomColors.BrandPrimary)
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun TipItem(icon: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = AnteroomColors.Muted,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun CaptureHeader(
    activeProfile: Profile?,
    onCancel: () -> Unit
) {
    Surface(
        color = AnteroomColors.SurfaceSecondary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, AnteroomColors.Border, CircleShape)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnteroomColors.OnSurface
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "NEW BRIEF",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = AnteroomColors.BrandPrimary
                )
                Text(
                    text = if (activeProfile != null) "For ${activeProfile.name}" else "Snap paperwork",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AnteroomColors.OnSurface
                )
            }

            Spacer(modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun CaptureIntakeGuideCard(photoCount: Int) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AnteroomColors.Border, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(AnteroomColors.BrandSecondary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (photoCount > 0) "📸" else "📑",
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (photoCount > 0) {
                    "$photoCount page${if (photoCount == 1) "" else "s"} captured"
                } else {
                    "Add referral, med list, or lab photos"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AnteroomColors.OnSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Snap a page or pick from your device. You'll be able to review, reorder, or delete pages before saving.",
                fontSize = 13.sp,
                color = AnteroomColors.Muted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun CaptureBottomBar(
    photoCount: Int,
    uploadingCount: Int,
    onReviewClick: () -> Unit
) {
    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, AnteroomColors.Border)
    ) {
        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
            val isEnabled = photoCount > 0 && uploadingCount == 0

            Button(
                onClick = onReviewClick,
                enabled = isEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AnteroomColors.BrandPrimary,
                    disabledContainerColor = AnteroomColors.BrandPrimary.copy(alpha = 0.4f),
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (photoCount > 0) {
                        "Review $photoCount photo${if (photoCount == 1) "" else "s"} →"
                    } else {
                        "Add a photo to continue"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
