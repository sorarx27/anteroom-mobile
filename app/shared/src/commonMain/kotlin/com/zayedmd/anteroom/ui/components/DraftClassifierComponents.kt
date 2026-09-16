package com.zayedmd.anteroom.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.model.BriefConfidence
import com.zayedmd.anteroom.model.BriefLanguage
import com.zayedmd.anteroom.model.DocType
import com.zayedmd.anteroom.ui.theme.AnteroomColors

data class DocTypeOption(
    val type: DocType,
    val label: String,
    val icon: String
)

val DOC_TYPE_OPTIONS = listOf(
    DocTypeOption(DocType.referral, "Referral", "✉️"),
    DocTypeOption(DocType.med_list, "Med list", "💊"),
    DocTypeOption(DocType.lab_result, "Lab result", "🧪"),
    DocTypeOption(DocType.other, "Other", "📄")
)

data class LanguageOption(
    val language: BriefLanguage,
    val label: String,
    val flag: String
)

val LANGUAGE_OPTIONS = listOf(
    LanguageOption(BriefLanguage.en, "English", "🇬🇧"),
    LanguageOption(BriefLanguage.es, "Español", "🇪🇸")
)

@Composable
fun DocTypeSelectorSection(
    selectedDocType: DocType,
    onSelectDocType: (DocType) -> Unit,
    detectedDocType: DocType? = null,
    confidence: BriefConfidence? = null,
    isDetecting: Boolean = false,
    onRedetectClick: () -> Unit = {},
    hasPhotos: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DOCUMENT TYPE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AnteroomColors.Muted,
                letterSpacing = 0.5.sp
            )

            Surface(
                onClick = onRedetectClick,
                enabled = hasPhotos && !isDetecting,
                shape = RoundedCornerShape(14.dp),
                color = if (hasPhotos && !isDetecting) Color(0xFFE8F0EC) else Color(0xFFF1F3F5),
                border = BorderStroke(
                    0.5.dp,
                    if (hasPhotos && !isDetecting) AnteroomColors.BrandPrimary.copy(alpha = 0.4f) else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDetecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = AnteroomColors.BrandPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Detecting…",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = AnteroomColors.BrandPrimary
                        )
                    } else {
                        Text(
                            text = "✨ Detect",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasPhotos) AnteroomColors.BrandPrimary else AnteroomColors.Muted
                        )
                    }
                }
            }
        }

        if (detectedDocType != null) {
            Spacer(modifier = Modifier.height(8.dp))
            val detectedOption = DOC_TYPE_OPTIONS.find { it.type == detectedDocType }
            val confidenceSuffix = if (confidence == BriefConfidence.low) " (low confidence)" else ""
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE8F0EC))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "✨", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Detected: ${detectedOption?.label ?: detectedDocType.name}$confidenceSuffix — tap a chip to change",
                    fontSize = 12.sp,
                    color = AnteroomColors.BrandPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DOC_TYPE_OPTIONS.forEach { option ->
                val isSelected = selectedDocType == option.type
                val backgroundColor = if (isSelected) AnteroomColors.BrandPrimary else AnteroomColors.Surface
                val textColor = if (isSelected) Color.White else AnteroomColors.OnSurface
                val borderColor = if (isSelected) AnteroomColors.BrandPrimary else AnteroomColors.Border

                Surface(
                    onClick = { onSelectDocType(option.type) },
                    shape = RoundedCornerShape(20.dp),
                    color = backgroundColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = option.icon, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = option.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BriefLanguageSelectorSection(
    selectedLanguage: BriefLanguage,
    onSelectLanguage: (BriefLanguage) -> Unit,
    defaultLanguage: BriefLanguage,
    isSubscribed: Boolean,
    onUpgradeRequired: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BRIEF LANGUAGE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AnteroomColors.Muted,
                letterSpacing = 0.5.sp
            )

            if (!isSubscribed) {
                Surface(
                    onClick = onUpgradeRequired,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF3CD),
                    border = BorderStroke(0.5.dp, Color(0xFFFFEEBA))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "★", fontSize = 10.sp, color = Color(0xFF856404))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "PRO to switch",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF856404)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LANGUAGE_OPTIONS.forEach { option ->
                val isSelected = selectedLanguage == option.language
                val isLocked = !isSubscribed && option.language != defaultLanguage
                val backgroundColor = if (isSelected) AnteroomColors.BrandPrimary else AnteroomColors.Surface
                val textColor = if (isSelected) Color.White else AnteroomColors.OnSurface
                val borderColor = if (isSelected) AnteroomColors.BrandPrimary else AnteroomColors.Border

                Surface(
                    onClick = {
                        if (isLocked) {
                            onUpgradeRequired()
                        } else {
                            onSelectLanguage(option.language)
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = backgroundColor,
                    border = BorderStroke(1.dp, borderColor),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLocked) {
                            Text(text = "🔒", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                        } else {
                            Text(text = option.flag, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = option.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }
        }

        if (selectedLanguage != defaultLanguage) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your brief will be translated after extraction — drug names, doses, and dates stay verbatim.",
                fontSize = 12.sp,
                color = AnteroomColors.Muted,
                lineHeight = 16.sp
            )
        }
    }
}
