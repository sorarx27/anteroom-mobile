package com.zayedmd.anteroom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zayedmd.anteroom.model.BriefPhoto
import com.zayedmd.anteroom.ui.theme.AnteroomColors

@Composable
fun DraftPhotoGrid(
    photos: List<BriefPhoto>,
    onAddMoreClick: () -> Unit,
    onDeletePhotoClick: (String) -> Unit,
    onPhotoClick: (BriefPhoto, Int) -> Unit,
    deletingPhotoId: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Photos (${photos.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AnteroomColors.Muted
            )

            Surface(
                onClick = onAddMoreClick,
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, AnteroomColors.BrandPrimary)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "+",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnteroomColors.BrandPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add more",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AnteroomColors.BrandPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AnteroomColors.Surface)
                    .border(1.dp, AnteroomColors.Border, RoundedCornerShape(16.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📑", fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No photos left. Add some or discard this draft.",
                        fontSize = 13.sp,
                        color = AnteroomColors.Muted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Display items in a 2-column grid
            val rows = (photos.size + 1) / 2
            val gridHeight = (rows * 150 + (rows - 1) * 12).dp

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = gridHeight),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false
            ) {
                itemsIndexed(photos, key = { _, photo -> photo.photo_id }) { index, photo ->
                    DraftPhotoCard(
                        photo = photo,
                        pageIndex = index + 1,
                        isDeleting = deletingPhotoId == photo.photo_id,
                        onClick = { onPhotoClick(photo, index + 1) },
                        onDeleteClick = { onDeletePhotoClick(photo.photo_id) }
                    )
                }
            }
        }
    }
}

@Composable
fun DraftPhotoCard(
    photo: BriefPhoto,
    pageIndex: Int,
    isDeleting: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AnteroomColors.Surface)
            .border(1.dp, AnteroomColors.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        // Document representation placeholder / thumbnail background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE9ECEF)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "📄", fontSize = 28.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Page $pageIndex",
                    fontSize = 12.sp,
                    color = AnteroomColors.Muted,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Page Number Indicator Badge (top-left)
        Box(
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(Color(0xCC000000))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$pageIndex",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Delete Button (top-right)
        Box(
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xCC333333))
                .clickable(enabled = !isDeleting, onClick = onDeleteClick),
            contentAlignment = Alignment.Center
        ) {
            if (isDeleting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Text(
                    text = "✕",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun FullscreenPhotoInspectionModal(
    photo: BriefPhoto?,
    pageIndex: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    if (photo == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0000000))
        ) {
            // Top Bar
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x40FFFFFF))
                ) {
                    Text(
                        text = "✕",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Page $pageIndex of $totalPages",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )

                TextButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = Color(0xFFFF6B6B),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            // Preview Container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .fillMaxHeight(0.75f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(text = "📄", fontSize = 64.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Medical Document Page $pageIndex",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Full-resolution inspection view\nAll text, labels, and dosages should be legible.",
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}
