package com.example.ui.onboarding

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Account
import com.example.data.repository.FinanceRepository
import com.example.ui.common.AppIcons
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

data class AccountSetupItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String,
    var accountType: String,
    var openingBalance: String = "0.00"
)

val ALLOWED_ACCOUNT_TYPES = listOf("BANK", "CASH", "CREDIT_CARD", "INVESTMENT")

fun formatAccountTypeLabel(type: String): String = when (type) {
    "BANK" -> "Bank Account"
    "CASH" -> "Cash"
    "CREDIT_CARD" -> "Credit Card"
    "INVESTMENT" -> "Investment"
    else -> type
}

@Composable
fun OnboardingAccountsScreen(
    repository: FinanceRepository,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    // D1: prefilled editable rows — Cash (CASH) and Bank Account (BANK)
    var accountItems by remember {
        mutableStateOf(
            listOf(
                AccountSetupItem(name = "Cash", accountType = "CASH", openingBalance = "0.00"),
                AccountSetupItem(name = "Bank Account", accountType = "BANK", openingBalance = "0.00")
            )
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(IncomeGreen.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.CreditCard,
                    contentDescription = "Setup Accounts",
                    tint = IncomeGreen,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Set Up Your Accounts",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Customize your accounts and enter opening balances, or add more accounts.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
            )

            // Account Rows
            accountItems.forEachIndexed { index, item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("account_setup_row_$index"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Account #${index + 1}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (accountItems.size > 1) {
                                IconButton(
                                    onClick = {
                                        accountItems = accountItems.filterIndexed { i, _ -> i != index }
                                    },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("account_delete_button_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove account",
                                        tint = ExpenseRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Account Name Field
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { newName ->
                                accountItems = accountItems.toMutableList().also { list ->
                                    list[index] = list[index].copy(name = newName)
                                }
                            },
                            label = { Text("Account Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("account_name_input_$index")
                        )

                        // Type Selector & Opening Balance in a Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Account Type Selector
                            var typeMenuExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = formatAccountTypeLabel(item.accountType),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Account Type") },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Type",
                                            tint = TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("account_type_selector_$index")
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clickable { typeMenuExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = typeMenuExpanded,
                                    onDismissRequest = { typeMenuExpanded = false }
                                ) {
                                    ALLOWED_ACCOUNT_TYPES.forEach { t ->
                                        DropdownMenuItem(
                                            text = { Text(formatAccountTypeLabel(t)) },
                                            onClick = {
                                                accountItems = accountItems.toMutableList().also { list ->
                                                    list[index] = list[index].copy(accountType = t)
                                                }
                                                typeMenuExpanded = false
                                            },
                                            modifier = Modifier.testTag("account_type_option_${t.lowercase()}_$index")
                                        )
                                    }
                                }
                            }

                            // Opening Balance Field
                            OutlinedTextField(
                                value = item.openingBalance,
                                onValueChange = { newBal ->
                                    val filtered = newBal.filter { it.isDigit() || it == '.' }
                                    accountItems = accountItems.toMutableList().also { list ->
                                        list[index] = list[index].copy(openingBalance = filtered)
                                    }
                                },
                                label = { Text("Opening (${com.example.domain.util.Formatters.currentCurrencySymbol()})") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("account_balance_input_$index")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // "+ Add account" row
            OutlinedButton(
                onClick = {
                    accountItems = accountItems + AccountSetupItem(
                        name = "",
                        accountType = "BANK",
                        openingBalance = "0.00"
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_account_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("+ Add account", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Primary Action: Continue (saves configured accounts)
            Button(
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true
                    coroutineScope.launch {
                        // 1. Seed clean starter categories
                        repository.seedCleanDefaults()

                        // 2. Clear default starter accounts
                        val starterAccounts = repository.getActiveAccountsList()
                        starterAccounts.forEach { repository.deleteAccount(it) }

                        // 3. Insert each configured account through same repository path Lists hub uses
                        accountItems.forEachIndexed { idx, itm ->
                            val cleanName = itm.name.trim().ifBlank {
                                if (itm.accountType == "CASH") "Cash" else "Account ${idx + 1}"
                            }
                            val openBal = itm.openingBalance.toDoubleOrNull() ?: 0.0
                            repository.insertAccount(
                                Account(
                                    name = cleanName,
                                    accountType = itm.accountType,
                                    openingBalance = openBal,
                                    sortOrder = idx,
                                    archived = false
                                )
                            )
                        }

                        isSaving = false
                        onContinue()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_accounts_continue_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Continue",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // "Use defaults & continue" button that skips editing
            TextButton(
                onClick = {
                    if (isSaving) return@TextButton
                    isSaving = true
                    coroutineScope.launch {
                        // Uses standard clean defaults: Bank Account @ 0.00, Cash @ 0.00
                        repository.seedCleanDefaults()
                        isSaving = false
                        onContinue()
                    }
                },
                modifier = Modifier.testTag("use_defaults_button")
            ) {
                Text(
                    text = "Use defaults & continue",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
