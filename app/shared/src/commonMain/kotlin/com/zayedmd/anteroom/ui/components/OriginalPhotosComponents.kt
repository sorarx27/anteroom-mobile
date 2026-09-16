package com.zayedmd.anteroom.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.zayedmd.anteroom.model.BriefPhoto
import com.zayedmd.anteroom.ui.theme.AnteroomColors

@Composable
fun OriginalPhotosSection(
    photos: List<BriefPhoto>,
    onPhotoClick: (BriefPhoto, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SOURCE DOCUMENTS (${photos.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AnteroomColors.Muted,
                letterSpacing = 0.5.sp
            )

            TextButton(
                onClick = { isExpanded = !isExpanded },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isExpanded) "Hide thumbnails" else "View all",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnteroomColors.BrandPrimary
                )
            }
        }

        // Horizontal thumbnail strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            photos.forEachIndexed { index, photo ->
                SourcePageThumbnail(
                    photo = photo,
                    pageIndex = index,
                    onClick = { onPhotoClick(photo, index) }
                )
            }
        }

        // Expanded detail drawer
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AnteroomColors.Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Clinical Verification Tip",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.OnSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap any page thumbnail above to inspect handwriting, medication dosages, and laboratory stamps side-by-side with the extracted brief.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = AnteroomColors.Muted
                    )
                }
            }
        }
    }
}

@Composable
fun SourcePageThumbnail(
    photo: BriefPhoto,
    pageIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFEBEFED),
        border = BorderStroke(1.dp, AnteroomColors.Border),
        modifier = modifier
            .width(88.dp)
            .height(116.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "📄", fontSize = 28.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Page ${pageIndex + 1}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AnteroomColors.BrandPrimary
                )
            }

            // Top-right inspect icon
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🔍", fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun PhotoVerificationModal(
    photo: BriefPhoto,
    pageIndex: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "✕",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "Page ${pageIndex + 1} of $totalPages",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                // Placeholder for symmetry
                Spacer(modifier = Modifier.size(36.dp))
            }

            // Document display container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "📄", fontSize = 64.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Original Document Preview",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AnteroomColors.OnSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Source: ${photo.filename.ifBlank { "Page_${pageIndex + 1}.jpg" }}",
                            fontSize = 13.sp,
                            color = AnteroomColors.Muted
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Surface(
                            color = AnteroomColors.SurfaceSecondary,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "High-resolution source rendering active for clinical cross-examination.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = AnteroomColors.Muted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
