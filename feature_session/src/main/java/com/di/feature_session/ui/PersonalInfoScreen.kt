// PersonalInfoScreen.kt

package com.di.feature_session.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.di.core.data.UserManager
import com.di.feature_session.PersonalInfoViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInfoScreen(
    onBack: () -> Unit,
    viewModel: PersonalInfoViewModel = hiltViewModel()
) {
    val personalInfo by viewModel.personalInfo.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val hasChanges by viewModel.hasChanges.collectAsState()
    val canDeleteUser by viewModel.canDeleteUser.collectAsState()

    // State for various dialogs
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    // This handles the Date Picker Dialog if showDatePicker is true
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            val selectedDate = LocalDate.ofEpochDay(it / (1000 * 60 * 60 * 24))
                            viewModel.updateDateOfBirth(selectedDate)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // --- HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button with unsaved changes check
            IconButton(
                onClick = {
                    if (hasChanges) {
                        showSaveDialog = true
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Title
            Text(
                text = "Personal Information",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Save Button (visible only when there are changes)
            if (hasChanges) {
                Button(
                    onClick = { viewModel.savePersonalInfo() },
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save")
                    }
                }
            }

            // --- NEW: Delete User Button ---
            // Only show if there's more than one user (can't delete the last one)
            if (canDeleteUser) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = "Delete User",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // --- NEW: Subtitle indicating which user is being edited ---
        if (personalInfo.name.isNotEmpty()) {
            Text(
                text = "Editing profile for: ${personalInfo.name}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 16.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(24.dp))
        }


        // Scrollable content area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Basic Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Basic Information",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = personalInfo.name,
                        onValueChange = { viewModel.updateName(it) },
                        label = { Text("Name", fontSize = 16.sp) },
                        placeholder = { Text("Enter your name") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = personalInfo.age?.toString() ?: "",
                            onValueChange = {
                                if (it.isEmpty()) {
                                    viewModel.updateAge(null)
                                } else {
                                    it.toIntOrNull()?.let { age ->
                                        if (age in 1..150) viewModel.updateAge(age)
                                    }
                                }
                            },
                            label = { Text("Age", fontSize = 16.sp) },
                            placeholder = { Text("Age") },
                            leadingIcon = {
                                Icon(Icons.Default.Cake, contentDescription = null)
                            },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = personalInfo.weight?.toString() ?: "",
                            onValueChange = {
                                if (it.isEmpty()) {
                                    viewModel.updateWeight(null)
                                } else {
                                    it.toFloatOrNull()?.let { weight ->
                                        if (weight > 0) viewModel.updateWeight(weight)
                                    }
                                }
                            },
                            label = { Text("Weight (kg)", fontSize = 16.sp) },
                            placeholder = { Text("Weight") },
                            leadingIcon = {
                                Icon(Icons.Default.MonitorWeight, contentDescription = null)
                            },
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = personalInfo.dateOfBirth?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) ?: "",
                        onValueChange = { },
                        label = { Text("Date of Birth", fontSize = 16.sp) },
                        placeholder = { Text("Select date") },
                        leadingIcon = {
                            Icon(Icons.Default.CalendarToday, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Select Date")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        readOnly = true,
                        singleLine = true
                    )
                }
            }

            // Medical Notes Card (Optional - made more general)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocalHospital,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Health Information (Optional)",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Text(
                        text = "Include any relevant medical information that might affect your exercise routine",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = personalInfo.medicalNotes,
                        onValueChange = { viewModel.updateMedicalNotes(it) },
                        label = { Text("Medical Notes", fontSize = 16.sp) },
                        placeholder = {
                            Text("e.g., medications, conditions, injuries, or limitations")
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Description, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        maxLines = 5
                    )
                }
            }

            // Goals Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fitness Goals",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    OutlinedTextField(
                        value = personalInfo.fitnessGoals,
                        onValueChange = { viewModel.updateFitnessGoals(it) },
                        label = { Text("Your Goals", fontSize = 16.sp) },
                        placeholder = {
                            Text("What would you like to achieve?")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        maxLines = 4
                    )
                }
            }

            // Emergency Contact Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Emergency,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Emergency Contact",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Text(
                        text = "This contact can be reached via the Help button during workouts",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = personalInfo.emergencyContactName,
                        onValueChange = { viewModel.updateEmergencyContactName(it) },
                        label = { Text("Contact Name", fontSize = 16.sp) },
                        placeholder = { Text("Emergency contact name") },
                        leadingIcon = {
                            Icon(Icons.Default.ContactEmergency, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = personalInfo.emergencyContactPhone,
                        onValueChange = { viewModel.updateEmergencyContactPhone(it) },
                        label = { Text("Phone Number", fontSize = 16.sp) },
                        placeholder = { Text("Emergency phone number") },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = personalInfo.emergencyContactEmail,
                        onValueChange = { viewModel.updateEmergencyContactEmail(it) },
                        label = { Text("Email Address", fontSize = 16.sp) },
                        placeholder = { Text("Emergency email (optional)") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true
                    )
                }
            }

            // Privacy Note
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "All information is stored locally on this device and is never shared.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Save confirmation dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("Do you want to save your changes before leaving?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.savePersonalInfo()
                        showSaveDialog = false
                        onBack()
                    }
                ) {
                    Text("Save & Exit")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        onBack()
                    }
                ) {
                    Text("Discard")
                }
            }
        )
    }

    // --- NEW: Delete user confirmation dialog ---
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${personalInfo.name}?") },
            text = { Text("This action is permanent and cannot be undone. All session data and achievements for this user will be lost.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCurrentUser()
                        showDeleteDialog = false
                        onBack() // Navigate back to settings screen after deletion
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("DELETE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}