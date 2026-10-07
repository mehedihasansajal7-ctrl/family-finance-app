package com.familyfinance.app.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.familyfinance.app.core.money.Money
import com.familyfinance.app.core.time.AppTime
import com.familyfinance.app.core.util.TimeProvider
import com.familyfinance.app.data.local.dao.NotificationDao
import com.familyfinance.app.data.session.SessionManager
import com.familyfinance.app.domain.repository.FamilyFinanceRepository
import com.familyfinance.app.domain.repository.NotificationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.YearMonth

/**
 * Daily background reminder pass.
 *
 * For the signed-in member it: (1) reminds about unpaid bills inside their reminder window, and
 * (2) alerts when a family budget has crossed a warning/critical/exceeded threshold. Each reminder
 * is de-duplicated to at most once per ~20h per (category, item) so a daily schedule cannot spam.
 *
 * Bills are checked regardless of connectivity. Budget alerts need family-wide spend, which the
 * server aggregate supplies; if it is not available yet (offline / not computed) they are skipped
 * rather than guessed at from private rows.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val session: SessionManager,
    private val familyFinance: FamilyFinanceRepository,
    private val notifications: NotificationRepository,
    private val notificationDao: NotificationDao,
    private val notifier: ReminderNotifier,
    private val time: TimeProvider,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val user = session.currentUserOnce() ?: return Result.success()
        val familyId = user.familyId ?: return Result.success()
        val today = AppTime.today()
        val since = time.nowMillis() - DEDUPE_WINDOW_MILLIS

        val bills = runCatching { familyFinance.observeBills(familyId).first() }.getOrDefault(emptyList())
        val budgets = runCatching { familyFinance.observeBudgets(familyId).first() }.getOrDefault(emptyList())

        val requests = buildList {
            addAll(ReminderPlanner.dueBills(bills, today))
            if (budgets.isNotEmpty()) {
                val month = YearMonth.from(today)
                val aggregate = withTimeoutOrNull(5_000L) {
                    runCatching { familyFinance.observeAggregate(familyId, month).first() }.getOrNull()
                }
                if (aggregate != null) {
                    val byCategory = aggregate.expenseByCategory.associate { it.categoryId to it.total }
                    val actuals = budgets.associate { budget ->
                        val actual = if (budget.categoryId == null) {
                            aggregate.totalExpense
                        } else {
                            byCategory[budget.categoryId] ?: Money.ZERO
                        }
                        budget.id to actual
                    }
                    addAll(ReminderPlanner.budgetAlerts(budgets, actuals, today))
                }
            }
        }

        requests.forEach { request ->
            val alreadySent = notificationDao.countSince(
                ownerUid = user.uid,
                categoryId = request.categoryId,
                relatedId = request.relatedId,
                since = since,
            ) > 0
            if (alreadySent) return@forEach

            notifications.notify(
                ownerUid = user.uid,
                familyId = familyId,
                category = request.categoryId,
                title = request.title,
                body = request.body,
                relatedId = request.relatedId,
            )
            notifier.post(
                id = (request.categoryId + ":" + request.relatedId).hashCode(),
                title = request.title,
                body = request.body,
            )
        }
        return Result.success()
    }

    companion object {
        private const val DEDUPE_WINDOW_MILLIS = 20 * 60 * 60 * 1000L
    }
}
