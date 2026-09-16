package com.zayedmd.anteroom.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.model.BriefLanguage
import com.zayedmd.anteroom.ui.theme.AnteroomColors

@Composable
fun BriefTranslationRow(
    currentLanguage: BriefLanguage,
    sourceLanguage: BriefLanguage,
    availableLanguages: List<BriefLanguage>,
    isSubscribed: Boolean,
    isTranslating: Boolean,
    translateError: String? = null,
    onLanguageSelected: (BriefLanguage) -> Unit,
    onUpgradeRequired: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LANGUAGE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AnteroomColors.Muted,
                letterSpacing = 0.5.sp
            )

            if (!isSubscribed) {
                Surface(
                    onClick = onUpgradeRequired,
                    shape = RoundedCornerShape(12.dp),
                    color = AnteroomColors.BrandSecondary.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "★ Pro to translate",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AnteroomColors.BrandPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val languages = listOf(BriefLanguage.en, BriefLanguage.es)

            languages.forEach { lang ->
                val isSelected = currentLanguage == lang
                val isSource = lang == sourceLanguage
                val showLock = !isSubscribed && !isSource

                Surface(
                    onClick = {
                        if (showLock) {
                            onUpgradeRequired()
                        } else if (!isTranslating && !isSelected) {
                            onLanguageSelected(lang)
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) AnteroomColors.BrandPrimary else Color.White,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) AnteroomColors.BrandPrimary else AnteroomColors.Border
                    ),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isTranslating && isSelected && !isSource) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            val flag = if (lang == BriefLanguage.en) "🇬🇧" else "🇪🇸"
                            Text(text = flag, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                            text = lang.nativeName,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else AnteroomColors.OnSurface
                        )

                        if (showLock) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "🔒", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Translation status hints
        if (!translateError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = translateError,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFC62828)
            )
        } else if (isTranslating) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Translating your brief with clinical precision…",
                fontSize = 12.sp,
                color = AnteroomColors.BrandPrimary
            )
        } else if (currentLanguage != sourceLanguage) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Translated · medication names, dosages and dates stay verbatim.",
                fontSize = 12.sp,
                color = AnteroomColors.Muted
            )
        }
    }
}
