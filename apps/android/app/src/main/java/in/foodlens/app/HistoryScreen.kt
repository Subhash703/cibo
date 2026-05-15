package `in`.foodlens.app

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entriesOf
import `in`.foodlens.app.auth.UserProfile
import `in`.foodlens.app.network.AnalyzeClient
import `in`.foodlens.app.network.HistoryResponse
import `in`.foodlens.app.ui.CiboColors
import `in`.foodlens.app.ui.CiboType
import `in`.foodlens.app.ui.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    analyzer: AnalyzeClient,
    idToken: String?,
    user: UserProfile?,
    onBack: () -> Unit,
) {
    var rangeDays by remember { mutableIntStateOf(7) }
    var history by remember { mutableStateOf<HistoryResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rangeDays, idToken) {
        if (idToken == null) return@LaunchedEffect
        loading = true; error = null
        runCatching { analyzer.getDailySummaries(idToken, days = rangeDays) }
            .onSuccess { history = it }
            .onFailure { error = it.message ?: "Couldn't load history" }
        loading = false
    }

    Scaffold(
        containerColor = CiboColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Your history",
                        style = CiboType.H1, color = CiboColors.OnSurface)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = CiboColors.OnSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CiboColors.Background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "How the last $rangeDays days went.",
                style = CiboType.BodyMd,
                color = CiboColors.OnSurfaceVariant,
            )
            RangeSegmented(rangeDays) { rangeDays = it }

            when {
                loading && history == null -> LoadingCard()
                error != null && history == null -> ErrorCard(error!!)
                history != null -> {
                    StreakCard(history!!)
                    KcalChartCard(history!!)
                    MacroChartCard(history!!)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSegmented(current: Int, onChange: (Int) -> Unit) {
    val options = listOf(7 to "7 days", 30 to "30 days")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (days, label) ->
            SegmentedButton(
                selected = current == days,
                onClick = { onChange(days) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = CiboColors.Primary.copy(alpha = 0.18f),
                    activeContentColor = CiboColors.Primary,
                    inactiveContainerColor = CiboColors.SurfaceContainerHigh,
                    inactiveContentColor = CiboColors.OnSurfaceVariant,
                ),
            ) { Text(label) }
        }
    }
}

@Composable
private fun LoadingCard() {
    GlassCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                color = CiboColors.Primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
            Text("Pulling your history…",
                style = CiboType.BodyMd, color = CiboColors.OnSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    GlassCard {
        Text(message, style = CiboType.BodyMd, color = CiboColors.Error)
    }
}

@Composable
private fun StreakCard(h: HistoryResponse) {
    val daysLogged = h.days.count { it.logCount > 0 }
    GlassCard(glow = true) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StreakStat("${h.streakDays}", "DAY STREAK")
            StreakStat("${h.goalHits}",  "GOAL HITS")
            StreakStat("$daysLogged",    "DAYS LOGGED")
        }
    }
}

@Composable
private fun StreakStat(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value,
            style = CiboType.DisplaySm.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
            color = CiboColors.Primary)
        Text(label,
            style = CiboType.LabelSm.copy(fontWeight = FontWeight.SemiBold),
            color = CiboColors.OnSurfaceVariant)
    }
}

@Composable
private fun KcalChartCard(h: HistoryResponse) {
    val producer = remember(h.days) {
        ChartEntryModelProducer(
            entriesOf(*h.days.mapIndexed { i, d -> i.toFloat() to d.kcal.toFloat() }
                .toTypedArray())
        )
    }
    val labels = remember(h.days) { h.days.map { shortLabel(it.date) } }

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("KCAL TREND",
                    style = CiboType.LabelSm, color = CiboColors.Primary)
                Text("Goal ${h.dailyKcalTarget}",
                    style = CiboType.LabelSm, color = CiboColors.OnSurfaceVariant)
            }
            Chart(
                chart = lineChart(),
                chartModelProducer = producer,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        labels.getOrNull(value.toInt()) ?: ""
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }
    }
}

@Composable
private fun MacroChartCard(h: HistoryResponse) {
    val producer = remember(h.days) {
        ChartEntryModelProducer(
            entriesOf(*h.days.mapIndexed { i, d -> i.toFloat() to d.proteinG.toFloat() }.toTypedArray()),
            entriesOf(*h.days.mapIndexed { i, d -> i.toFloat() to d.fatG.toFloat() }.toTypedArray()),
            entriesOf(*h.days.mapIndexed { i, d -> i.toFloat() to d.carbsG.toFloat() }.toTypedArray()),
        )
    }
    val labels = remember(h.days) { h.days.map { shortLabel(it.date) } }
    val barColors = listOf(CiboColors.Primary, CiboColors.Warning, CiboColors.Secondary)

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MACROS PER DAY (g)",
                style = CiboType.LabelSm, color = CiboColors.Primary)
            Chart(
                chart = columnChart(
                    columns = barColors.map {
                        LineComponent(
                            color = it.toArgb(),
                            thicknessDp = 6f,
                            shape = Shapes.roundedCornerShape(2),
                        )
                    },
                ),
                chartModelProducer = producer,
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = { value, _ ->
                        labels.getOrNull(value.toInt()) ?: ""
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Legend("Protein", CiboColors.Primary)
                Legend("Fat", CiboColors.Warning)
                Legend("Carbs", CiboColors.Secondary)
            }
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).background(color))
        Text(label, style = CiboType.BodyMd, color = CiboColors.OnSurfaceVariant)
    }
}

private fun shortLabel(iso: String): String =
    iso.substringAfterLast('-')   // "2026-05-15" → "15"
