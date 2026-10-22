package org.totschnig.myexpenses.compose.commitments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.totschnig.myexpenses.viewmodel.LoanViewModel

enum class CommitmentTab(val title: String, val icon: ImageVector) {
    THIS_MONTH("This Month", Icons.Default.CalendarMonth),
    LOANS("Loans & EMIs", Icons.Default.AccountBalance),
    BILLS("Bills & Utilities", Icons.Default.Bolt),
    INSURANCE("Insurance", Icons.Default.Security),
    SUBSCRIPTIONS("Subscriptions", Icons.Default.Subscriptions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitmentsScreen(
    viewModel: LoanViewModel,
    initialTab: Int = 0,
    onClose: () -> Unit
) {
    var selectedTabIndex by remember {
        mutableIntStateOf(initialTab.coerceIn(0, CommitmentTab.values().size - 1))
    }

    LaunchedEffect(Unit) {
        viewModel.resyncAllCommitments()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Commitments",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resyncAllCommitments() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Resync")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                divider = {}
            ) {
                CommitmentTab.values().forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                label = "CommitmentTabTransition"
            ) { targetIndex ->
                when (targetIndex) {
                    0 -> CommitmentsThisMonthView(
                        viewModel = viewModel,
                        onNavigateToTab = { selectedTabIndex = it }
                    )
                    1 -> CommitmentEmisView(
                        viewModel = viewModel
                    )
                    2 -> CommitmentBillsView(
                        viewModel = viewModel
                    )
                    3 -> CommitmentInsuranceView(
                        viewModel = viewModel
                    )
                    4 -> CommitmentSubscriptionsView(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
