package com.example.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.datastore.AppSettingsRepository
import com.example.data.repository.FinanceRepository
import com.example.importbackup.UnifiedCsvStorage
import com.example.ui.common.AppIcons
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OnboardingFlowStep {
    SOURCE_SELECTION,
    ACCOUNT_SETUP,
    BACKUP_LOCATION
}

@Composable
fun OnboardingSourceScreen(
    repository: FinanceRepository,
    settingsRepository: AppSettingsRepository,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStep by remember { mutableStateOf(OnboardingFlowStep.SOURCE_SELECTION) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Launcher for selecting backup CSV file
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            errorMessage = null
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Could not open file stream")
                    val result = stream.use {
                        UnifiedCsvStorage.restoreFromInputStream(
                            repository = repository,
                            inputStream = it,
                            clearExisting = true,
                            settingsRepository = settingsRepository,
                            context = context
                        )
                    }
                    if (result.inserted == 0 && result.skippedDup == 0 && result.sourceRows == 0) {
                        throw Exception("No transactions or accounts found in CSV file")
                    }
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        // D2: Reorder onboarding: Import Backup -> backup-folder step LAST
                        currentStep = OnboardingFlowStep.BACKUP_LOCATION
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        errorMessage = "Import failed: ${e.localizedMessage ?: "Corrupt or invalid CSV format"}. Database was not modified."
                    }
                }
            }
        }
    }

    // Launcher for picking custom SAF folder
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (_: Exception) {}

            coroutineScope.launch {
                settingsRepository.setAutoSaveTreeUri(treeUri.toString())
                onOnboardingComplete()
            }
        }
    }

    when (currentStep) {
        OnboardingFlowStep.ACCOUNT_SETUP -> {
            // D1: Account-setup step shown immediately after "Start Fresh"
            OnboardingAccountsScreen(
                repository = repository,
                onContinue = {
                    currentStep = OnboardingFlowStep.BACKUP_LOCATION
                },
                modifier = modifier
            )
        }
        OnboardingFlowStep.SOURCE_SELECTION -> {
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Header
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.AccountBalance,
                            contentDescription = "SilentSums",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Welcome to SilentSums",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Offline-first, zero-telemetry financial ledger. Choose how you want to get started.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)
                    )

                    // Inline Error if any
                    if (errorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = ExpenseRed.copy(alpha = 0.12f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp)
                                .testTag("onboarding_error_message")
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = ExpenseRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        // Card 1: Start Fresh (D4: Subtitle polish: "Set up your accounts with opening balances")
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_start_fresh")
                                .clickable {
                                    // D2: Start Fresh transitions to Account Setup
                                    currentStep = OnboardingFlowStep.ACCOUNT_SETUP
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(IncomeGreen.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.CreditCard,
                                        contentDescription = null,
                                        tint = IncomeGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "NEW USER?",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("onboarding_kicker_new_user")
                                    )
                                    Text(
                                        text = "Start Fresh",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Set up your accounts with opening balances",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Card 2: Import Backup
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_import_backup")
                                .clickable {
                                    errorMessage = null
                                    importFileLauncher.launch(
                                        arrayOf("text/*", "text/comma-separated-values", "application/csv", "*/*")
                                    )
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.FileDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "EXISTING USER?",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("onboarding_kicker_existing_user")
                                    )
                                    Text(
                                        text = "Import Backup",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Restore transactions, dues, and accounts from an existing CSV backup file",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        OnboardingFlowStep.BACKUP_LOCATION -> {
            // Step 3 (LAST): Backup Location Picker (Optional / Skippable)
            Surface(
                modifier = modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Storage,
                            contentDescription = "Storage",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Where should your data files live?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("onboarding_backup_location_title")
                    )

                    Text(
                        text = "Your ledger file, daily auto-backups, and filtered exports all save to this folder so you can open them on a computer or back them up to the cloud.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                    )

                    // Honest Disclosure Line (Mandated)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("backup_location_disclosure")
                    ) {
                        Text(
                            text = "Files in shared folders can be readable by other apps with storage permission. Your live ledger stays safely inside the app. You can open these files in any spreadsheet app.",
                            fontSize = 12.sp,
                            color = TextMuted,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    // Button 1: Keep Default (Downloads)
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                settingsRepository.setAutoSaveTreeUri("")
                                onOnboardingComplete()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_location_default"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Keep Default (Downloads)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Button 2: Pick My Own Folder
                    OutlinedButton(
                        onClick = {
                            folderPickerLauncher.launch(null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_location_custom"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick My Own Folder", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Small note below buttons (replaces "Skip for now")
                    Text(
                        text = "You can change this anytime in Settings.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .testTag("backup_location_settings_hint")
                    )
                }
            }
        }
    }
}
