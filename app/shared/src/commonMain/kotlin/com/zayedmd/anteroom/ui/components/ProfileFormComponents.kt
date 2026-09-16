package com.zayedmd.anteroom.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zayedmd.anteroom.model.BiologicalSex
import com.zayedmd.anteroom.model.Relationship
import com.zayedmd.anteroom.ui.theme.AnteroomColors

data class ProfileFormState(
    val name: String = "",
    val relationship: Relationship = Relationship.partner,
    val dobYear: String = "",
    val dobMonth: String = "",
    val dobDay: String = "",
    val sex: BiologicalSex? = null,
    val isSelf: Boolean = false
) {
    val isDobValid: Boolean
        get() {
            if (dobYear.isEmpty() && dobMonth.isEmpty() && dobDay.isEmpty()) {
                return true // optional for family members
            }
            if (dobYear.length != 4 || dobMonth.isEmpty() || dobDay.isEmpty()) {
                return false
            }
            val y = dobYear.toIntOrNull() ?: return false
            val m = dobMonth.toIntOrNull() ?: return false
            val d = dobDay.toIntOrNull() ?: return false

            if (y !in 1900..2026 || m !in 1..12 || d !in 1..31) return false

            val maxDaysInMonth = when (m) {
                4, 6, 9, 11 -> 30
                2 -> if ((y % 4 == 0 && y % 100 != 0) || (y % 400 == 0)) 29 else 28
                else -> 31
            }
            return d <= maxDaysInMonth
        }

    val canSubmit: Boolean
        get() = name.trim().isNotEmpty() && isDobValid

    val formattedDob: String?
        get() = if (dobYear.isNotEmpty() && dobMonth.isNotEmpty() && dobDay.isNotEmpty() && isDobValid) {
            "${dobYear.padStart(4, '0')}-${dobMonth.padStart(2, '0')}-${dobDay.padStart(2, '0')}"
        } else null
}

@Composable
fun ProfileNameInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Full name",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AnteroomColors.BrandPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("e.g. David Jenkins", color = Color(0xFFA0AAB0)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = AnteroomColors.BrandPrimary,
                unfocusedBorderColor = Color(0xFFD4DDD7)
            ),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )
    }
}

@Composable
fun RelationshipSelector(
    selected: Relationship,
    onSelect: (Relationship) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val options = listOf(
        Relationship.partner,
        Relationship.child,
        Relationship.parent,
        Relationship.other
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Relationship to you",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AnteroomColors.BrandPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { rel ->
                val isSelected = selected == rel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AnteroomColors.BrandPrimary else Color.White)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) AnteroomColors.BrandPrimary else Color(0xFFD4DDD7),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(enabled = enabled) { onSelect(rel) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rel.label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color.White else AnteroomColors.BrandPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileDobInput(
    dobYear: String,
    dobMonth: String,
    dobDay: String,
    onYearChange: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onDayChange: (String) -> Unit,
    isValid: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Date of birth",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AnteroomColors.BrandPrimary
            )
            Text(
                text = "Optional",
                fontSize = 12.sp,
                color = Color(0xFF8A9A90)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = dobYear,
                onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) onYearChange(it) },
                placeholder = { Text("YYYY", color = Color(0xFFA0AAB0), fontSize = 14.sp) },
                modifier = Modifier.weight(1.4f),
                singleLine = true,
                isError = !isValid && (dobYear.isNotEmpty() || dobMonth.isNotEmpty() || dobDay.isNotEmpty()),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = AnteroomColors.BrandPrimary,
                    unfocusedBorderColor = Color(0xFFD4DDD7)
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )
            OutlinedTextField(
                value = dobMonth,
                onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) onMonthChange(it) },
                placeholder = { Text("MM", color = Color(0xFFA0AAB0), fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = !isValid && (dobYear.isNotEmpty() || dobMonth.isNotEmpty() || dobDay.isNotEmpty()),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = AnteroomColors.BrandPrimary,
                    unfocusedBorderColor = Color(0xFFD4DDD7)
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )
            OutlinedTextField(
                value = dobDay,
                onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) onDayChange(it) },
                placeholder = { Text("DD", color = Color(0xFFA0AAB0), fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = !isValid && (dobYear.isNotEmpty() || dobMonth.isNotEmpty() || dobDay.isNotEmpty()),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = AnteroomColors.BrandPrimary,
                    unfocusedBorderColor = Color(0xFFD4DDD7)
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                )
            )
        }
        if (!isValid && (dobYear.isNotEmpty() || dobMonth.isNotEmpty() || dobDay.isNotEmpty())) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Please enter a valid date (YYYY between 1900–2026)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun BiologicalSexSelector(
    selected: BiologicalSex?,
    onSelect: (BiologicalSex?) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        BiologicalSex.female to "Female",
        BiologicalSex.male to "Male",
        BiologicalSex.other to "Other"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Biological sex",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AnteroomColors.BrandPrimary
            )
            Text(
                text = "Optional",
                fontSize = 12.sp,
                color = Color(0xFF8A9A90)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (sexOpt, label) ->
                val isSelected = selected == sexOpt
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AnteroomColors.BrandPrimary else Color.White)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) AnteroomColors.BrandPrimary else Color(0xFFD4DDD7),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelect(if (isSelected) null else sexOpt) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Color.White else AnteroomColors.BrandPrimary
                    )
                }
            }
        }
    }
}
