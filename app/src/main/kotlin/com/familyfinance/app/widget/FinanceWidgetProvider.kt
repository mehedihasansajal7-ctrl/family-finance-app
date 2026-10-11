package com.familyfinance.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.familyfinance.app.MainActivity
import com.familyfinance.app.R
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.money.MoneyFormat
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.model.TransactionType
import com.familyfinance.app.domain.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home-screen widget: this month's spending, with a one-tap "Add expense".
 *
 * The amount is the signed-in member's OWN spending (never another member's), read from the local
 * database on a background scope. Tapping "Add" opens the app straight into the add-expense sheet.
 */
@AndroidEntryPoint
class FinanceWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var transactions: TransactionRepository
    @Inject lateinit var session: SessionManager

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val uid = session.currentUserOnce()?.uid
                val rows = if (uid == null) emptyList() else transactions.observeTransactions(uid).first()
                val range = AppTime.monthRange(AppTime.currentMonth())
                val spent = Money.sum(
                    rows.filter { it.type == TransactionType.EXPENSE && it.dateEpochDay in range }
                        .map { it.amount },
                )
                val views = buildViews(context, spent)
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun buildViews(context: Context, spent: Money): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.finance_widget)
        views.setTextViewText(R.id.widget_amount, MoneyFormat.bdt(spent))

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_title, open)
        views.setOnClickPendingIntent(R.id.widget_hint, open)

        val add = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_ADD, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_add, add)
        return views
    }
}
