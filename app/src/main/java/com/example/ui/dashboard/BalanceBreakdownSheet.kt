package com.example.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.calculator.LedgerCalculator
import com.example.data.model.Account
import com.example.domain.model.AccountBreakdownRow
import com.example.data.model.TransactionEntity
import com.example.domain.util.Formatters
import com.example.ui.common.liquidGlass
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceBreakdownSheet(
    accounts: List<Account>,
    transactions: List<TransactionEntity>,
    allTransactions: List<TransactionEntity> = transactions,
    asOf: Long = Long.MAX_VALUE,
    periodLabel: String? = null,
    hideBalances: Boolean = false,
    currencySymbol: String = Formatters.currentCurrencySymbol(),
    onDismiss: () -> Unit,
    onAccountClick: (Long) -> Unit
) {
    val breakdown = LedgerCalculator.computeBalanceBreakdown(
        accounts = accounts,
        transactions = transactions,
        allTransactions = allTransactions,
        asOf = asOf
    )
    val nonCardRows = breakdown.rows.filter { !it.isCreditCard }
    val cardRows = breakdown.rows.filter { it.isCreditCard }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("balance_breakdown_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Balance Breakdown",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (!periodLabel.isNullOrBlank()) {
                        Text(
                            text = periodLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("balance_breakdown_close")) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(nonCardRows, key = { it.account.id }) { row ->
                    AccountBreakdownItem(
                        row = row,
                        hideBalances = hideBalances,
                        currencySymbol = currencySymbol,
                        onClick = {
                            onAccountClick(row.account.id)
                            onDismiss()
                        }
                    )
                }

                if (cardRows.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                        Text(
                            text = "Credit Cards (excluded from Current Balance)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted,
                            modifier = Modifier
                                .padding(vertical = 2.dp)
                                .testTag("breakdown_credit_cards_header")
                        )
                    }

                    items(cardRows, key = { it.account.id }) { row ->
                        AccountBreakdownItem(
                            row = row,
                            hideBalances = hideBalances,
                            currencySymbol = currencySymbol,
                            onClick = {
                                onAccountClick(row.account.id)
                                onDismiss()
                            }
                        )
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    TotalBreakdownItem(
                        breakdown = breakdown,
                        hideBalances = hideBalances,
                        currencySymbol = currencySymbol
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountBreakdownItem(
    row: AccountBreakdownRow,
    hideBalances: Boolean,
    currencySymbol: String = Formatters.currentCurrencySymbol(),
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(12.dp)
            .clickable(onClick = onClick)
            .testTag("breakdown_row_${row.account.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.account.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                val balText = if (hideBalances) "••••••" else Formatters.formatIndianCurrency(row.currentBalance, currencySymbol = currencySymbol)
                Text(
                    text = balText,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    maxLines = 1,
                    color = if (row.currentBalance < 0) ExpenseRed else TextPrimary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val openText = if (hideBalances) "••••" else Formatters.formatIndianCurrency(row.openingBalance, includeDecimals = false, currencySymbol = currencySymbol)
                val inText = if (hideBalances) "••••" else "+${Formatters.formatIndianCurrency(row.totalIn, includeDecimals = false, currencySymbol = currencySymbol)}"
                val outText = if (hideBalances) "••••" else "-${Formatters.formatIndianCurrency(row.totalOut, includeDecimals = false, currencySymbol = currencySymbol)}"

                Text(
                    text = "Opening: $openText",
                    fontSize = 11.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "In: $inText", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = IncomeGreen, maxLines = 1)
                    Text(text = "Out: $outText", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ExpenseRed, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TotalBreakdownItem(
    breakdown: com.example.domain.model.BalanceBreakdownSummary,
    hideBalances: Boolean,
    currencySymbol: String = Formatters.currentCurrencySymbol()
) {
    val hasCreditCards = breakdown.rows.any { it.isCreditCard }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(12.dp, tint = MaterialTheme.colorScheme.primaryContainer)
            .testTag("breakdown_total_row")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Balance",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                val totalText = if (hideBalances) "••••••" else Formatters.formatIndianCurrency(breakdown.liquidTotal, currencySymbol = currencySymbol)
                Text(
                    text = totalText,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (hasCreditCards) {
                val netWorthText = if (hideBalances) "••••••" else Formatters.formatIndianCurrency(breakdown.netWorthInclDebt, currencySymbol = currencySymbol)
                Text(
                    text = "Net worth incl. card debt: $netWorthText",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted,
                    modifier = Modifier.testTag("breakdown_net_worth_footer")
                )
            }
        }
    }
}
