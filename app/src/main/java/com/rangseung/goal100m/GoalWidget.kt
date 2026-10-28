package com.rangseung.goal100m

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen widget: this month's remaining living budget, goal progress, and quick-add buttons. */
class GoalWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Store.load(ctx)
        val views = build(ctx)
        ids.forEach { mgr.updateAppWidget(it, views) }
    }

    companion object {
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, GoalWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(ctx)
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        private fun build(ctx: Context): RemoteViews {
            val d = Store.data
            val sum = Calc.month(d, Calc.curYm())
            val bud = d.settings.budget
            val saved = Calc.totalSaved(d)
            val goal = d.settings.goal
            val pct = if (goal > 0) (saved.toDouble() / goal).coerceIn(0.0, 1.0) else 0.0

            val v = RemoteViews(ctx.packageName, R.layout.widget_goal)
            if (bud > 0) {
                val left = bud - sum.out
                v.setTextViewText(R.id.label, "이번 달 생활비 · 예산 ${Calc.man(bud)}")
                v.setTextViewText(R.id.remain, if (left >= 0) "${Calc.fmt(left)}원 남음" else "${Calc.fmt(-left)}원 초과")
            } else {
                v.setTextViewText(R.id.label, "이번 달 지출")
                v.setTextViewText(R.id.remain, "${Calc.fmt(sum.out)}원")
            }
            v.setProgressBar(R.id.progress, 1000, (pct * 1000).toInt(), false)
            v.setTextViewText(R.id.goal, "${Calc.man(goal)} 목표 · ${Calc.man(saved)} (${"%.1f".format(pct * 100)}%)")

            v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ))
            v.setOnClickPendingIntent(R.id.btnOut, quickAdd(ctx, OUT, 1))
            v.setOnClickPendingIntent(R.id.btnIn, quickAdd(ctx, IN, 2))
            return v
        }

        private fun quickAdd(ctx: Context, type: String, code: Int): PendingIntent =
            PendingIntent.getActivity(
                ctx, code,
                Intent(ctx, QuickAddActivity::class.java)
                    .putExtra(QuickAddActivity.EXTRA_TYPE, type)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
