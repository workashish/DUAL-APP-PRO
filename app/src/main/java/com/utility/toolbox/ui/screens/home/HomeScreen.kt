package com.utility.toolbox.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.IntrinsicSize
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shortcut
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.utility.toolbox.domain.model.ClonedApp
import com.utility.toolbox.domain.model.Workspace
import com.utility.toolbox.ui.components.AppCard
import com.utility.toolbox.ui.components.EmptyState
import com.utility.toolbox.ui.components.SlideBar
import com.utility.toolbox.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showCreateDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    var showBubbleToggle by remember { mutableStateOf(false) }
    var showDeleteWorkspaceDialog by remember { mutableStateOf<Workspace?>(null) }

    // Delete workspace confirmation dialog
    showDeleteWorkspaceDialog?.let { ws ->
        AlertDialog(
            onDismissRequest = { showDeleteWorkspaceDialog = null },
            title = { Text("Delete Workspace") },
            text = { Text("Delete workspace '${ws.name}'? All cloned apps inside it will also be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWorkspace(ws.id)
                    showDeleteWorkspaceDialog = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteWorkspaceDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Delete Clone") },
            text = { Text("Delete ${state.appToDelete?.displayName}? All its data will be removed.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create workspace dialog
    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Workspace") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Workspace Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            viewModel.createWorkspace(name)
                            showCreateDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Context menu
    if (state.showContextMenu) {
        val app = state.contextMenuApp
        if (app != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissContextMenu() },
                title = { Text(app.displayName) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { viewModel.launchApp(app); viewModel.dismissContextMenu() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Launch", modifier = Modifier.weight(1f))
                        }
                        TextButton(
                            onClick = {
                                viewModel.dismissContextMenu()
                                navController.navigate(Screen.Customize.createRoute(app.id))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ColorLens, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Customize", modifier = Modifier.weight(1f))
                        }
                        TextButton(
                            onClick = { viewModel.createShortcut(app); viewModel.dismissContextMenu() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !app.hasShortcut
                        ) {
                            Icon(Icons.Default.Shortcut, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (app.hasShortcut) "Shortcut exists" else "Add Shortcut", modifier = Modifier.weight(1f))
                        }
                        TextButton(
                            onClick = {
                                viewModel.dismissContextMenu()
                                navController.navigate(Screen.IconFake.createRoute(app.id))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Change App Icon", modifier = Modifier.weight(1f))
                        }

                        TextButton(
                            onClick = {
                                viewModel.dismissContextMenu()
                                navController.navigate(Screen.DeviceInfo.createRoute(app.id))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeviceHub, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Device Info", modifier = Modifier.weight(1f))
                        }
                        TextButton(
                            onClick = {
                                viewModel.dismissContextMenu()
                                navController.navigate(Screen.GsfLicense.createRoute(app.id))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("License Manager (GSF)", modifier = Modifier.weight(1f))
                        }
                        TextButton(
                            onClick = { viewModel.showDeleteConfirmation(app) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("Delete", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissContextMenu() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // Quick Switch toggle dialog
    if (showBubbleToggle) {
        AlertDialog(
            onDismissRequest = { showBubbleToggle = false },
            title = { Text("Quick Switch Bubble") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Floating bubble overlay", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Quickly switch between running cloned apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.isBubbleEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.toggleBubble(enabled)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBubbleToggle = false }) { Text("Done") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(
                                "DualSpace",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (state.activeWorkspace != null) {
                                Text(
                                    text = state.activeWorkspace!!.name.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showBubbleToggle = !showBubbleToggle },
                            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Widgets, contentDescription = "Quick Switch")
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.refresh() },
                            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        Spacer(Modifier.width(16.dp))
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.Clone.route) },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(20.dp),
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Clone App",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Workspace section
                    item {
                        WorkspaceSection(
                            activeWorkspace = state.activeWorkspace,
                            workspaces = state.workspaces,
                            onSwitchWorkspace = { viewModel.switchWorkspace(it) },
                            onCreateWorkspace = { showCreateDialog = true },
                            onDeleteWorkspace = { showDeleteWorkspaceDialog = it }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // Stats row
                    if (state.clonedApps.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatChip(
                                    label = "${state.clonedApps.size} apps",
                                    icon = Icons.Default.Storage
                                )
                                val runningCount = state.clonedApps.count { it.isRunning }
                                if (runningCount > 0) {
                                    StatChip(
                                        label = "$runningCount running",
                                        icon = Icons.Default.Workspaces
                                    )
                                }
                                val resetCount = state.clonedApps.count { it.deviceInfoResetCount > 0 }
                                if (resetCount > 0) {
                                    StatChip(
                                        label = "$resetCount spoofed",
                                        icon = Icons.Default.DeviceHub
                                    )
                                }
                            }
                        }
                    }

                    // Cloned apps list or empty state
                    if (state.clonedApps.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No cloned apps yet",
                                description = "Tap + to clone your first app",
                                modifier = Modifier.fillParentMaxHeight(0.7f)
                            )
                        }
                    } else {
                        // Group apps by first letter for sticky headers
                        val groupedApps = state.clonedApps.sortedBy { it.displayName }
                            .groupBy { it.displayName.first().uppercase() }

                        groupedApps.forEach { (letter, apps) ->
                            item {
                                Text(
                                    text = letter,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(
                                items = apps,
                                key = { it.id }
                            ) { app ->
                                AppCard(
                                    app = app,
                                    onClick = { viewModel.launchApp(app) },
                                    onLongClick = { viewModel.showContextMenu(app) },
                                    onDelete = { viewModel.showDeleteConfirmation(app) }
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }

                // A-Z SlideBar
                if (state.clonedApps.isNotEmpty()) {
                    SlideBar(
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
                        onLetterSelected = { letter ->
                            val idx = state.clonedApps.indexOfFirst {
                                it.displayName.first().uppercase() == letter
                            }
                            if (idx >= 0) {
                                scope.launch {
                                    listState.animateScrollToItem(idx + 3) // +3 for headers
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSection(
    activeWorkspace: Workspace?,
    workspaces: List<Workspace>,
    onSwitchWorkspace: (Long) -> Unit,
    onCreateWorkspace: () -> Unit,
    onDeleteWorkspace: (Workspace) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Workspaces",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onCreateWorkspace) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("New", modifier = Modifier.padding(start = 4.dp))
                }
            }

            if (workspaces.isEmpty()) {
                Text(
                    text = "Create your first workspace to get started",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    workspaces.forEach { workspace ->
                        FilterChip(
                            selected = workspace.id == activeWorkspace?.id,
                            onClick = { onSwitchWorkspace(workspace.id) },
                            label = {
                                Text(
                                    workspace.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                if (workspace.id == activeWorkspace?.id) {
                                    Icon(Icons.Default.Workspaces, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            },
                            trailingIcon = {
                                // Only show delete on non-active workspaces to prevent accidental deletion
                                if (workspace.id != activeWorkspace?.id) {
                                    IconButton(
                                        onClick = { onDeleteWorkspace(workspace) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete ${workspace.name}",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
