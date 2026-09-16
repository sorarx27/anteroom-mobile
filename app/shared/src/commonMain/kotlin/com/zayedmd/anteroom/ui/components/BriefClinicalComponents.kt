package com.zayedmd.anteroom.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.model.AllergyItem
import com.zayedmd.anteroom.model.MedicationItem
import com.zayedmd.anteroom.model.PatientInfo
import com.zayedmd.anteroom.ui.theme.AnteroomColors

@Composable
fun BriefSection(
    title: String,
    tintColor: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = tintColor ?: AnteroomColors.Muted,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AnteroomColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    }
}

@Composable
fun NoneDetectedText(
    modifier: Modifier = Modifier,
    text: String = "Not detected on the pages."
) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = AnteroomColors.Muted,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = AnteroomColors.Muted,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = AnteroomColors.OnSurface,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun PatientInfoCard(
    patient: PatientInfo?,
    modifier: Modifier = Modifier
) {
    val name = patient?.name
    val dob = patient?.dob
    val sex = patient?.sex
    val idNumber = patient?.id_number

    val hasData = !name.isNullOrBlank() ||
        !dob.isNullOrBlank() ||
        !sex.isNullOrBlank() ||
        !idNumber.isNullOrBlank()

    BriefSection(title = "Patient", modifier = modifier) {
        if (hasData) {
            Column {
                if (!name.isNullOrBlank()) {
                    KeyValueRow(label = "Name", value = name)
                }
                if (!dob.isNullOrBlank()) {
                    KeyValueRow(label = "Date of birth", value = dob)
                }
                if (!sex.isNullOrBlank()) {
                    KeyValueRow(label = "Sex", value = sex)
                }
                if (!idNumber.isNullOrBlank()) {
                    KeyValueRow(label = "ID / MRN", value = idNumber)
                }
            }
        } else {
            NoneDetectedText()
        }
    }
}

@Composable
fun ReferralReasonCard(
    referralReason: String?,
    modifier: Modifier = Modifier
) {
    BriefSection(title = "Referral reason / Clinical reason", modifier = modifier) {
        if (!referralReason.isNullOrBlank()) {
            Text(
                text = referralReason,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = AnteroomColors.OnSurface
            )
        } else {
            NoneDetectedText()
        }
    }
}

@Composable
fun MedicationsListCard(
    medications: List<MedicationItem>,
    modifier: Modifier = Modifier
) {
    BriefSection(title = "Medications (${medications.size})", modifier = modifier) {
        if (medications.isNotEmpty()) {
            Column {
                medications.forEachIndexed { index, med ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AnteroomColors.SurfaceSecondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "💊", fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = med.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AnteroomColors.OnSurface
                            )
                            val meta = listOfNotNull(med.dose, med.frequency).filter { it.isNotBlank() }
                            if (meta.isNotEmpty()) {
                                Text(
                                    text = meta.joinToString("  ·  "),
                                    fontSize = 12.sp,
                                    color = AnteroomColors.Muted
                                )
                            }
                        }
                    }
                    if (index < medications.size - 1) {
                        HorizontalDivider(
                            color = AnteroomColors.Border,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        } else {
            NoneDetectedText()
        }
    }
}

@Composable
fun AllergiesListCard(
    allergies: List<AllergyItem>,
    modifier: Modifier = Modifier
) {
    BriefSection(title = "Allergies (${allergies.size})", modifier = modifier) {
        if (allergies.isNotEmpty()) {
            Column {
                allergies.forEachIndexed { index, allergy ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFF3CD)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "⚠️", fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = allergy.substance,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AnteroomColors.OnSurface
                            )
                            if (!allergy.reaction.isNullOrBlank()) {
                                Text(
                                    text = "Reaction: ${allergy.reaction}",
                                    fontSize = 12.sp,
                                    color = AnteroomColors.Muted
                                )
                            }
                        }
                    }
                    if (index < allergies.size - 1) {
                        HorizontalDivider(
                            color = AnteroomColors.Border,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        } else {
            NoneDetectedText()
        }
    }
}

@Composable
fun FlaggedAlertsBanner(
    flaggedItems: List<String>,
    modifier: Modifier = Modifier
) {
    if (flaggedItems.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = "FLAGGED FOR CLINICAL REVIEW (${flaggedItems.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFC62828),
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Surface(
            color = Color(0xFFFFF5F5),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                flaggedItems.forEachIndexed { index, flag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "⚠️",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = flag,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB71C1C)
                        )
                    }
                    if (index < flaggedItems.size - 1) {
                        HorizontalDivider(
                            color = Color(0xFFFFCDD2),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClinicalDisclaimerText(
    modifier: Modifier = Modifier
) {
    Text(
        text = "Extracted directly from the patient’s documents. No diagnosis, nothing invented — if a field isn’t shown, it wasn’t clearly on the page.",
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = AnteroomColors.Muted,
        modifier = modifier.padding(vertical = 6.dp)
    )
}
