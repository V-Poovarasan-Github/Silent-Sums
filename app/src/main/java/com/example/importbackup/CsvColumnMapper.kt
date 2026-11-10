package com.example.importbackup

object CsvColumnMapper {

    fun normalize(header: String): String =
        header.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

    fun resolveStandardColumn(header: String): String? {
        val norm = normalize(header)
        return when (norm) {
            "date", "txdate", "datetime", "timestamp", "time", "dateandtime",
            "transactiondate", "txndate", "valuedate", "postingdate", "entrydate" -> "date"

            "type", "txtype", "transactiontype", "debitcredit", "direction", "flow",
            "drcr", "trxtype", "crdr", "entrytype", "txntype" -> "type"

            "amount", "amt", "value", "price", "cost", "spend", "spendamount",
            "transactionamount", "amountinr", "amountrs", "netamount", "total", "totalamount" -> "amount"

            "debit", "dr", "debitamount", "withdrawal", "withdraw", "withdrawalamount",
            "withdrawalamt", "drinr", "debitinr", "paidout", "moneyout", "expenseamt" -> "debit"

            "credit", "cr", "creditamount", "deposit", "depositamount", "depositamt",
            "crinr", "creditinr", "paidin", "moneyin", "incomeamt" -> "credit"

            "sources", "source", "fromaccount", "from", "account", "bank", "fromacc",
            "accountused", "sourceaccount", "paymentmode", "wallet", "accountname",
            "acc", "bankname", "paymentmethod", "mode", "paidfrom", "debitedfrom",
            "accountnumber", "accno" -> "fromaccount"

            "toaccount", "to", "destination", "targetaccount", "toacc", "destinationaccount",
            "transferto", "beneficiary", "paidto", "creditedto" -> "toaccount"

            "expense", "category", "cat", "categoryname", "expensetype", "tag", "tagcategory",
            "expensefor", "purpose", "head", "expensehead", "subcategory", "subcat" -> "category"

            "spenddesc", "description", "desc", "title", "item", "narration", "particulars",
            "remarks", "details", "payee", "merchant", "party", "transactiondetails",
            "transactionremarks" -> "description"

            "notes", "note", "comment", "memo", "transremarks", "referenceno", "refno",
            "chequeno", "chqno", "utr", "referencenumber" -> "notes"

            "id", "txid" -> "id"

            "mediaref", "media_ref", "attachmentref" -> "mediaref"

            else -> null
        }
    }

    val KNOWN_ALIASES = setOf(
        "id", "txid",
        "date", "time", "timestamp", "txdate", "datetime", "dateandtime", "transactiondate",
        "txndate", "valuedate", "postingdate", "entrydate",
        "type", "txtype", "transactiontype", "debitcredit", "direction", "flow", "drcr",
        "trxtype", "crdr", "entrytype", "txntype",
        "amount", "amt", "value", "price", "cost", "spend", "spendamount", "transactionamount",
        "amountinr", "amountrs", "netamount", "total", "totalamount",
        "debit", "dr", "debitamount", "withdrawal", "withdraw", "withdrawalamount", "withdrawalamt",
        "drinr", "debitinr", "paidout", "moneyout", "expenseamt",
        "credit", "cr", "creditamount", "deposit", "depositamount", "depositamt", "crinr",
        "creditinr", "paidin", "moneyin", "incomeamt",
        "sources", "source", "fromaccount", "from", "account", "bank", "fromacc", "accountused",
        "sourceaccount", "paymentmode", "wallet", "accountname", "acc", "bankname", "paymentmethod",
        "mode", "paidfrom", "debitedfrom", "accountnumber", "accno",
        "toaccount", "to", "destination", "targetaccount", "toacc", "destinationaccount",
        "transferto", "beneficiary", "paidto", "creditedto",
        "expense", "category", "cat", "categoryname", "expensetype", "tag", "tagcategory",
        "expensefor", "purpose", "head", "expensehead", "subcategory", "subcat",
        "spenddesc", "description", "desc", "title", "item", "narration", "particulars",
        "remarks", "details", "payee", "merchant", "party", "transactiondetails", "transactionremarks",
        "notes", "note", "comment", "memo", "transremarks", "referenceno", "refno",
        "chequeno", "chqno", "utr", "referencenumber",
        "balance", "closingbalance", "runningbalance", "availbalance"
    )

    fun isKnownStandardHeader(header: String): Boolean {
        val norm = normalize(header)
        return resolveStandardColumn(norm) != null || KNOWN_ALIASES.contains(norm)
    }

    fun normalizeType(rawType: String): String {
        val norm = rawType.trim().uppercase().replace(Regex("[^A-Z]"), "")
        return when (norm) {
            "INCOME", "CR", "CREDIT", "DEPOSIT", "PAIDIN", "MONEYIN", "RECEIVED" -> "INCOME"
            "TRANSFER", "TXFR", "CONTRA" -> "TRANSFER"
            else -> "EXPENSE"
        }
    }
}
