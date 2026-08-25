package com.towerbound.laststand.ui.screens

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.towerbound.laststand.MainActivity
import com.towerbound.laststand.R
import com.towerbound.laststand.core.GameEngine
import com.towerbound.laststand.meta.AchievementManager
import com.towerbound.laststand.meta.MetaSession
import com.towerbound.laststand.ui.GameButton
import com.towerbound.laststand.ui.Screen
import com.towerbound.laststand.ui.UiKit
import com.towerbound.laststand.ui.Widgets

/**
 * Quests & achievements screen (design: QUESTS + ACHIEVEMENTS): daily quest
 * set with progress bars and claim buttons; lifetime achievements with
 * progress and one-time rewards.
 */
class QuestsScreen(context: Context) : Screen(context) {

    private val engine = GameEngine.get()
    private lateinit var body: LinearLayout
    private lateinit var currencyBar: LinearLayout

    init {
        applyBackdrop(Color.parseColor("#070A18"), Color.parseColor("#121935"), Color.parseColor("#070A18"))

        val root = LinearLayout(context)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(UiKit.dp(context, 26f), UiKit.dp(context, 18f), UiKit.dp(context, 26f), UiKit.dp(context, 12f))

        val header = LinearLayout(context)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        val title = UiKit.title(context, context.getString(R.string.quests_title), 30f)
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        currencyBar = Widgets.currencyBar(context)
        header.addView(currencyBar)
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        root.addView(body, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val close = GameButton(context, context.getString(R.string.close), GameButton.Style.SECONDARY)
        close.onButtonClick = {
            (context as? MainActivity)?.screenManager?.pop(false)
        }
        root.addView(close, LinearLayout.LayoutParams(UiKit.dp(context, 160f), UiKit.dp(context, 48f)).apply {
            topMargin = UiKit.dp(context, 14f)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        addView(Widgets.scroll(context, root), UiKit.params(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        rebuild()
    }

    private fun rebuild() {
        body.removeAllViews()
        MetaSession.quests.ensureDailySet()
        Widgets.updateCurrency(currencyBar, MetaSession.balanceGold(), MetaSession.balanceGems())

        buildQuestSection()
        buildAchievementSection()
    }

    /* ---------------- daily quests ---------------- */

    private fun questTitle(key: String): String = when (key) {
        "quest_kill" -> context.getString(R.string.quest_kill)
        "quest_waves" -> context.getString(R.string.quest_waves)
        "quest_gold" -> context.getString(R.string.quest_gold)
        else -> key
    }

    private fun buildQuestSection() {
        val panel = Widgets.panel(context)
        panel.addView(Widgets.sectionHeader(context, context.getString(R.string.quests_title), UiKit.cAccentWarm))

        for (quest in MetaSession.quests.quests) {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(0, UiKit.dp(context, 8f), 0, UiKit.dp(context, 8f))

            val top = LinearLayout(context)
            top.orientation = LinearLayout.HORIZONTAL
            top.gravity = Gravity.CENTER_VERTICAL
            val name = UiKit.text(context, questTitle(quest.titleKey), 15f, UiKit.cText, bold = false)
            name.gravity = Gravity.START
            top.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val reward = UiKit.text(
                context,
                "+${quest.rewardGold} ${context.getString(R.string.gold)} +${quest.rewardGems} ${context.getString(R.string.gems)}",
                12f, UiKit.cGold, bold = false
            )
            top.addView(reward, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            row.addView(top)

            val barRow = LinearLayout(context)
            barRow.orientation = LinearLayout.HORIZONTAL
            barRow.gravity = Gravity.CENTER_VERTICAL
            val bar = Widgets.BarView(context)
            bar.fraction = quest.fraction()
            bar.fillColor = if (quest.complete) UiKit.cSuccess else UiKit.cAccent
            barRow.addView(bar, LinearLayout.LayoutParams(0, UiKit.dp(context, 12f), 1f))

            val progressLabel = UiKit.text(
                context,
                "${quest.progress.coerceAtMost(quest.target)}/${quest.target}",
                12f, UiKit.cTextDim, bold = false
            )
            progressLabel.setPadding(UiKit.dp(context, 8f), 0, 0, 0)
            barRow.addView(progressLabel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            row.addView(barRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiKit.dp(context, 4f) })

            if (quest.complete && !quest.claimed) {
                val claim = GameButton(context, context.getString(R.string.claim_reward), GameButton.Style.GOLD)
                claim.onButtonClick = {
                    if (MetaSession.quests.claim(quest, MetaSession.profile.economy)) {
                        MetaSession.persist()
                        rebuild()
                    }
                }
                row.addView(claim, LinearLayout.LayoutParams(UiKit.dp(context, 150f), UiKit.dp(context, 40f)).apply {
                    topMargin = UiKit.dp(context, 6f)
                    gravity = Gravity.CENTER_HORIZONTAL
                })
            } else if (quest.claimed) {
                val done = UiKit.text(context, context.getString(R.string.claimed), 12f, UiKit.cSuccess)
                row.addView(done, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = UiKit.dp(context, 4f) })
            }
            panel.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        body.addView(panel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = UiKit.dp(context, 12f) })
    }

    /* ---------------- achievements ---------------- */

    private fun achievementTitle(key: String): String = when (key) {
        "ach_kill_100" -> context.getString(R.string.ach_kill_100)
        "ach_kill_1000" -> context.getString(R.string.ach_kill_1000)
        "ach_kill_10000" -> context.getString(R.string.ach_kill_10000)
        "ach_wave_10" -> context.getString(R.string.ach_wave_10)
        "ach_wave_25" -> context.getString(R.string.ach_wave_25)
        "ach_wave_50" -> context.getString(R.string.ach_wave_50)
        "ach_heroes_3" -> context.getString(R.string.ach_heroes_3)
        "ach_heroes_all" -> context.getString(R.string.ach_heroes_all)
        "ach_runs_10" -> context.getString(R.string.ach_runs_10)
        "ach_runs_50" -> context.getString(R.string.ach_runs_50)
        "ach_boss_5" -> context.getString(R.string.ach_boss_5)
        "ach_boss_20" -> context.getString(R.string.ach_boss_20)
        "ach_upgrades_50" -> context.getString(R.string.ach_upgrades_50)
        "ach_gold_10000" -> context.getString(R.string.ach_gold_10000)
        else -> key
    }

    private fun achievementDesc(key: String): String = when (key) {
        "ach_kill_100" -> context.getString(R.string.ach_kill_100_desc)
        "ach_kill_1000" -> context.getString(R.string.ach_kill_1000_desc)
        "ach_kill_10000" -> context.getString(R.string.ach_kill_10000_desc)
        "ach_wave_10" -> context.getString(R.string.ach_wave_10_desc)
        "ach_wave_25" -> context.getString(R.string.ach_wave_25_desc)
        "ach_wave_50" -> context.getString(R.string.ach_wave_50_desc)
        "ach_heroes_3" -> context.getString(R.string.ach_heroes_3_desc)
        "ach_heroes_all" -> context.getString(R.string.ach_heroes_all_desc)
        "ach_runs_10" -> context.getString(R.string.ach_runs_10_desc)
        "ach_runs_50" -> context.getString(R.string.ach_runs_50_desc)
        "ach_boss_5" -> context.getString(R.string.ach_boss_5_desc)
        "ach_boss_20" -> context.getString(R.string.ach_boss_20_desc)
        "ach_upgrades_50" -> context.getString(R.string.ach_upgrades_50_desc)
        "ach_gold_10000" -> context.getString(R.string.ach_gold_10000_desc)
        else -> key
    }

    private fun buildAchievementSection() {
        val panel = Widgets.panel(context)
        panel.addView(Widgets.sectionHeader(context, context.getString(R.string.achievements_title)))

        for (ach in MetaSession.achievements.achievements) {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(0, UiKit.dp(context, 8f), 0, UiKit.dp(context, 8f))

            val top = LinearLayout(context)
            top.orientation = LinearLayout.HORIZONTAL
            top.gravity = Gravity.CENTER_VERTICAL
            val name = UiKit.text(
                context, achievementTitle(ach.titleKey), 15f,
                if (ach.complete) UiKit.cSuccess else UiKit.cText, bold = false
            )
            name.gravity = Gravity.START
            top.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val reward = UiKit.text(
                context,
                "+${ach.rewardGold} ${context.getString(R.string.gold)} +${ach.rewardGems} ${context.getString(R.string.gems)}",
                12f, UiKit.cGold, bold = false
            )
            top.addView(reward, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            row.addView(top)

            val desc = UiKit.text(context, achievementDesc(ach.titleKey), 11f, UiKit.cTextDim, bold = false)
            desc.gravity = Gravity.START
            row.addView(desc)

            val barRow = LinearLayout(context)
            barRow.orientation = LinearLayout.HORIZONTAL
            barRow.gravity = Gravity.CENTER_VERTICAL
            val bar = Widgets.BarView(context)
            bar.fraction = ach.fraction()
            bar.fillColor = if (ach.complete) UiKit.cSuccess else UiKit.cPrimary
            barRow.addView(bar, LinearLayout.LayoutParams(0, UiKit.dp(context, 10f), 1f))
            val progressLabel = UiKit.text(
                context,
                "${ach.progress.coerceAtMost(ach.target)}/${ach.target}",
                11f, UiKit.cTextDim, bold = false
            )
            progressLabel.setPadding(UiKit.dp(context, 8f), 0, 0, 0)
            barRow.addView(progressLabel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            row.addView(barRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = UiKit.dp(context, 4f) })

            if (ach.complete && !ach.claimed) {
                val claim = GameButton(context, context.getString(R.string.claim_reward), GameButton.Style.GOLD)
                claim.onButtonClick = {
                    if (MetaSession.achievements.claim(ach, MetaSession.profile.economy)) {
                        MetaSession.persist()
                        rebuild()
                    }
                }
                row.addView(claim, LinearLayout.LayoutParams(UiKit.dp(context, 150f), UiKit.dp(context, 40f)).apply {
                    topMargin = UiKit.dp(context, 6f)
                    gravity = Gravity.CENTER_HORIZONTAL
                })
            } else if (ach.claimed) {
                val done = UiKit.text(context, context.getString(R.string.claimed), 12f, UiKit.cSuccess)
                row.addView(done, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = UiKit.dp(context, 4f) })
            }
            panel.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        body.addView(panel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = UiKit.dp(context, 12f) })
    }

    override fun onBack(): Boolean {
        (context as? MainActivity)?.screenManager?.pop(false)
        return true
    }
}
