package br.com.lojabaterias.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.lojabaterias.LojaApp
import br.com.lojabaterias.ui.components.AppIcons
import br.com.lojabaterias.ui.components.FitText
import br.com.lojabaterias.ui.components.SyncIndicator
import br.com.lojabaterias.ui.screens.BackupScreen
import br.com.lojabaterias.ui.screens.ExpensesScreen
import br.com.lojabaterias.ui.screens.FinanceScreen
import br.com.lojabaterias.ui.screens.HomeScreen
import br.com.lojabaterias.ui.screens.InvoiceDetailScreen
import br.com.lojabaterias.ui.screens.InvoiceFormScreen
import br.com.lojabaterias.ui.screens.InvoicesScreen
import br.com.lojabaterias.ui.screens.ProductDetailScreen
import br.com.lojabaterias.ui.screens.ProductFormScreen
import br.com.lojabaterias.ui.screens.ReportsScreen
import br.com.lojabaterias.ui.screens.SaleDetailScreen
import br.com.lojabaterias.ui.screens.SaleFormScreen
import br.com.lojabaterias.ui.screens.SalesScreen
import br.com.lojabaterias.ui.screens.ScrapsScreen
import br.com.lojabaterias.ui.screens.StockScreen
import br.com.lojabaterias.ui.screens.WarrantiesScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Routes {
    const val HOME = "home"
    const val SALES = "sales"
    const val STOCK = "stock"
    const val REPORTS = "reports"
    const val NEW_SALE = "newsale?productId={productId}"
    const val SALE_DETAIL = "saledetail/{id}"
    const val SALE_EDIT = "saleedit/{id}"
    const val PRODUCT_NEW = "productnew"
    const val PRODUCT_DETAIL = "productdetail/{id}"
    const val PRODUCT_EDIT = "productedit/{id}"
    const val BACKUP = "backup"
    const val SCRAPS = "scraps"
    const val WARRANTIES = "warranties"
    const val EXPENSES = "expenses"
    const val FINANCE = "finance"
    const val INVOICES = "invoices"
    const val INVOICE_NEW = "invoicenew?import={import}"
    const val INVOICE_EDIT = "invoiceedit/{id}"
    const val INVOICE_DETAIL = "invoicedetail/{id}"

    fun invoiceNew(withImport: Boolean = false) = if (withImport) "invoicenew?import=true" else "invoicenew"
    fun invoiceEdit(id: Long) = "invoiceedit/$id"
    fun invoiceDetail(id: Long) = "invoicedetail/$id"

    fun newSale(productId: Long? = null) = if (productId == null) "newsale" else "newsale?productId=$productId"
    fun saleDetail(id: Long) = "saledetail/$id"
    fun saleEdit(id: Long) = "saleedit/$id"
    fun productDetail(id: Long) = "productdetail/$id"
    fun productEdit(id: Long) = "productedit/$id"
}

/** Uma aba de uma área do app. */
private data class AreaTab(val route: String, val label: String)

/** Áreas da barra de baixo. As que têm mais de uma tela mostram abas no topo. */
private enum class Area(val label: String, val icon: ImageVector, val tabs: List<AreaTab>) {
    HOME("Início", Icons.Filled.Home, listOf(AreaTab(Routes.HOME, "Início"))),
    SALES("Vendas", Icons.Filled.ShoppingCart, listOf(AreaTab(Routes.SALES, "Vendas"))),
    STOCK(
        "Estoque", AppIcons.Battery,
        listOf(AreaTab(Routes.STOCK, "Baterias"), AreaTab(Routes.SCRAPS, "Sucatas"), AreaTab(Routes.WARRANTIES, "Garantias")),
    ),
    MONEY(
        "Dinheiro", AppIcons.BarChart,
        listOf(
            AreaTab(Routes.FINANCE, "Resumo"),
            AreaTab(Routes.INVOICES, "Notas"),
            AreaTab(Routes.EXPENSES, "Despesas"),
            AreaTab(Routes.REPORTS, "Relatórios"),
        ),
    ),
    ;

    companion object {
        fun of(route: String?): Area? = entries.firstOrNull { a -> a.tabs.any { it.route == route } }
    }
}

private data class MenuEntry(val label: String, val icon: ImageVector, val route: String)

/** Menu ☰: todas as telas, agrupadas pelas mesmas áreas da barra de baixo. */
private val menuGroups = listOf(
    "Estoque" to listOf(
        MenuEntry("Baterias", AppIcons.Battery, Routes.STOCK),
        MenuEntry("Sucatas", Icons.Filled.Refresh, Routes.SCRAPS),
        MenuEntry("Garantias, extras e Vitor", Icons.Filled.CheckCircle, Routes.WARRANTIES),
    ),
    "Dinheiro" to listOf(
        MenuEntry("Resumo financeiro", Icons.Filled.Info, Routes.FINANCE),
        MenuEntry("Notas fiscais e boletos", Icons.Filled.Email, Routes.INVOICES),
        MenuEntry("Despesas", Icons.Filled.DateRange, Routes.EXPENSES),
        MenuEntry("Relatórios e PDF", AppIcons.BarChart, Routes.REPORTS),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LojaNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val currentArea = Area.of(currentRoute)
    val lastTab = remember { mutableStateMapOf<Area, String>() }
    LaunchedEffect(currentRoute) {
        if (currentArea != null && currentRoute != null) lastTab[currentArea] = currentRoute
    }
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val container = (context.applicationContext as LojaApp).container
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentArea != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    Area.entries.forEach { area ->
                        NavigationBarItem(
                            selected = currentArea == area,
                            // Volta para a última aba usada naquela área
                            onClick = { nav.navigateTopLevel(lastTab[area] ?: area.tabs.first().route) },
                            icon = { Icon(area.icon, contentDescription = null) },
                            label = { Text(area.label, maxLines = 1) },
                        )
                    }
                    NavigationBarItem(
                        selected = false,
                        onClick = { menuOpen = true },
                        icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                        label = { Text("Menu") },
                    )
                }
            }
        },
    ) { inner ->
        // Puxar a tela para baixo sincroniza com a nuvem (em qualquer tela).
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    val ok = withContext(Dispatchers.IO) { container.syncManager.syncNow() }
                    refreshing = false
                    val msg = if (ok) "Sincronizado" else container.syncManager.status.value.message ?: "Não foi possível sincronizar agora"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = inner.calculateBottomPadding()),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .let { if (currentArea != null) it.windowInsetsPadding(WindowInsets.statusBars) else it },
            ) {
                if (currentArea != null && currentArea.tabs.size > 1) {
                    AreaHeader(currentArea, currentRoute) { route -> if (route != currentRoute) nav.navigateTopLevel(route) }
                }
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    enterTransition = { androidx.compose.animation.EnterTransition.None },
                    exitTransition = { androidx.compose.animation.ExitTransition.None },
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            onNewSale = { nav.navigate(Routes.newSale()) },
                            onOpenSale = { nav.navigate(Routes.saleDetail(it)) },
                            onSeeAllSales = { nav.navigateTopLevel(Routes.SALES) },
                            onSettings = { nav.navigate(Routes.BACKUP) },
                            onInvoices = { nav.navigateTopLevel(Routes.INVOICES) },
                            onStock = { nav.navigateTopLevel(Routes.STOCK) },
                        )
                    }
                    composable(Routes.SALES) {
                        SalesScreen(
                            onNewSale = { nav.navigate(Routes.newSale()) },
                            onOpenSale = { nav.navigate(Routes.saleDetail(it)) },
                        )
                    }
                    composable(Routes.STOCK) {
                        StockScreen(
                            onOpenProduct = { nav.navigate(Routes.productDetail(it)) },
                            onNewProduct = { nav.navigate(Routes.PRODUCT_NEW) },
                        )
                    }
                    composable(Routes.REPORTS) { ReportsScreen() }
                    composable(
                        Routes.NEW_SALE,
                        arguments = listOf(navArgument("productId") { type = NavType.LongType; defaultValue = -1L }),
                    ) { entry ->
                        val productId = entry.arguments?.getLong("productId")?.takeIf { it > 0 }
                        SaleFormScreen(saleId = null, productId = productId, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                    }
                    composable(Routes.SALE_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments?.getLong("id") ?: 0L
                        SaleFormScreen(saleId = id, productId = null, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                    }
                    composable(Routes.SALE_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments?.getLong("id") ?: 0L
                        SaleDetailScreen(
                            saleId = id,
                            onEdit = { nav.navigate(Routes.saleEdit(id)) },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.PRODUCT_NEW) {
                        ProductFormScreen(productId = null, onDone = { nav.popBackStack() }, onDeleted = {}, onBack = { nav.popBackStack() })
                    }
                    composable(Routes.PRODUCT_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments?.getLong("id") ?: 0L
                        ProductFormScreen(
                            productId = id,
                            onDone = { nav.popBackStack() },
                            onDeleted = { nav.popBackStack(Routes.STOCK, inclusive = false) },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.PRODUCT_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments?.getLong("id") ?: 0L
                        ProductDetailScreen(
                            productId = id,
                            onEdit = { nav.navigate(Routes.productEdit(id)) },
                            onSell = { nav.navigate(Routes.newSale(id)) },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.BACKUP) { BackupScreen(onBack = { nav.popBackStack() }) }
                    composable(Routes.WARRANTIES) { WarrantiesScreen() }
                    composable(Routes.EXPENSES) { ExpensesScreen() }
                    composable(Routes.FINANCE) { FinanceScreen() }
                    composable(Routes.SCRAPS) { ScrapsScreen() }
                    composable(Routes.INVOICES) {
                        InvoicesScreen(
                            onNew = { nav.navigate(Routes.invoiceNew()) },
                            onImport = { nav.navigate(Routes.invoiceNew(withImport = true)) },
                            onOpen = { nav.navigate(Routes.invoiceDetail(it)) },
                        )
                    }
                    composable(
                        Routes.INVOICE_NEW,
                        arguments = listOf(navArgument("import") { type = NavType.BoolType; defaultValue = false }),
                    ) { entry ->
                        InvoiceFormScreen(
                            invoiceId = null,
                            onDone = { nav.popBackStack() },
                            onBack = { nav.popBackStack() },
                            autoImport = entry.arguments?.getBoolean("import") ?: false,
                        )
                    }
                    composable(Routes.INVOICE_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments?.getLong("id") ?: 0L
                        InvoiceFormScreen(invoiceId = id, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                    }
                    composable(Routes.INVOICE_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments?.getLong("id") ?: 0L
                        InvoiceDetailScreen(invoiceId = id, onEdit = { nav.navigate(Routes.invoiceEdit(id)) }, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    if (menuOpen) {
        ModalBottomSheet(onDismissRequest = { menuOpen = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text("Art das Baterias", style = MaterialTheme.typography.titleLarge)
                    SyncIndicator(Modifier.padding(top = 2.dp))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { menuOpen = false; nav.navigate(Routes.newSale()) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Venda", modifier = Modifier.padding(start = 6.dp))
                    }
                    FilledTonalButton(
                        onClick = { menuOpen = false; nav.navigate(Routes.invoiceNew()) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("Nota fiscal", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                menuGroups.forEach { (title, entries) ->
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 4.dp),
                    )
                    entries.forEach { entry ->
                        NavigationDrawerItem(
                            label = { Text(entry.label, style = MaterialTheme.typography.bodyLarge) },
                            icon = { Icon(entry.icon, contentDescription = null) },
                            selected = currentRoute == entry.route,
                            onClick = {
                                menuOpen = false
                                nav.navigateTopLevel(entry.route)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
                NavigationDrawerItem(
                    label = { Text("Ajustes, backup e atualizações", style = MaterialTheme.typography.bodyLarge) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    selected = false,
                    onClick = {
                        menuOpen = false
                        nav.navigate(Routes.BACKUP)
                    },
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                )
            }
        }
    }
}

/** Título da área + abas que ligam as telas dela. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AreaHeader(area: Area, currentRoute: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            area.label,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        PrimaryTabRow(
            selectedTabIndex = area.tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            area.tabs.forEach { tab ->
                Tab(
                    selected = tab.route == currentRoute,
                    onClick = { onSelect(tab.route) },
                    text = { FitText(tab.label, style = MaterialTheme.typography.titleSmall) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
