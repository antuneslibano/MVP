package br.com.lojabaterias.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import br.com.lojabaterias.LojaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.lojabaterias.ui.components.AppIcons
import br.com.lojabaterias.ui.screens.BackupScreen
import br.com.lojabaterias.ui.screens.ChargeDetailScreen
import br.com.lojabaterias.ui.screens.ChargeFormScreen
import br.com.lojabaterias.ui.screens.ChargesScreen
import br.com.lojabaterias.ui.screens.ExpensesScreen
import br.com.lojabaterias.ui.screens.FinanceScreen
import br.com.lojabaterias.ui.screens.InvoiceDetailScreen
import br.com.lojabaterias.ui.screens.InvoiceFormScreen
import br.com.lojabaterias.ui.screens.InvoicesScreen
import br.com.lojabaterias.ui.screens.VouchersScreen
import br.com.lojabaterias.ui.screens.WarrantiesScreen
import br.com.lojabaterias.ui.screens.HomeScreen
import br.com.lojabaterias.ui.screens.MovementsScreen
import br.com.lojabaterias.ui.screens.ProductDetailScreen
import br.com.lojabaterias.ui.screens.ProductFormScreen
import br.com.lojabaterias.ui.screens.ReportsScreen
import br.com.lojabaterias.ui.screens.SaleDetailScreen
import br.com.lojabaterias.ui.screens.ScrapPricesScreen
import br.com.lojabaterias.ui.screens.ScrapsScreen
import br.com.lojabaterias.ui.screens.SaleFormScreen
import br.com.lojabaterias.ui.screens.SalesScreen
import br.com.lojabaterias.ui.screens.StockScreen

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
    const val MOVEMENTS = "movements"
    const val BACKUP = "backup"
    const val SCRAPS = "scraps"
    const val SCRAP_PRICES = "scrapprices"
    const val CHARGES = "charges"
    const val CHARGE_NEW = "chargenew"
    const val CHARGE_EDIT = "chargeedit/{id}"
    const val CHARGE_DETAIL = "chargedetail/{id}"
    const val WARRANTIES = "warranties"
    const val VOUCHERS = "vouchers"
    const val EXPENSES = "expenses"
    const val FINANCE = "finance"
    const val INVOICES = "invoices"
    const val INVOICE_NEW = "invoicenew"
    const val INVOICE_EDIT = "invoiceedit/{id}"
    const val INVOICE_DETAIL = "invoicedetail/{id}"

    fun invoiceEdit(id: Long) = "invoiceedit/$id"
    fun invoiceDetail(id: Long) = "invoicedetail/$id"

    fun chargeEdit(id: Long) = "chargeedit/$id"
    fun chargeDetail(id: Long) = "chargedetail/$id"

    fun newSale(productId: Long? = null) = if (productId == null) "newsale" else "newsale?productId=$productId"
    fun saleDetail(id: Long) = "saledetail/$id"
    fun saleEdit(id: Long) = "saleedit/$id"
    fun productDetail(id: Long) = "productdetail/$id"
    fun productEdit(id: Long) = "productedit/$id"
}

/** Uma aba de uma área do app. */
private data class AreaTab(val route: String, val label: String)

/**
 * O app é dividido em áreas. Cada área tem um botão na barra de baixo e abas no topo
 * que ligam as telas da mesma área (ex.: Estoque → Baterias | Sucatas | Garantias e extras).
 */
private enum class Area(val label: String, val icon: ImageVector, val tabs: List<AreaTab>) {
    HOME("Início", Icons.Filled.Home, listOf(AreaTab(Routes.HOME, "Início"))),
    SALES(
        "Vendas", Icons.Filled.ShoppingCart,
        listOf(AreaTab(Routes.SALES, "Vendas"), AreaTab(Routes.CHARGES, "Na carga"), AreaTab(Routes.VOUCHERS, "Vales de casco")),
    ),
    STOCK(
        "Estoque", AppIcons.Battery,
        listOf(AreaTab(Routes.STOCK, "Baterias"), AreaTab(Routes.SCRAPS, "Sucatas"), AreaTab(Routes.WARRANTIES, "Garantias e extras")),
    ),
    MONEY(
        "Dinheiro", AppIcons.BarChart,
        listOf(
            AreaTab(Routes.FINANCE, "Resumo"),
            AreaTab(Routes.INVOICES, "Notas e boletos"),
            AreaTab(Routes.EXPENSES, "Despesas"),
            AreaTab(Routes.REPORTS, "Relatórios e PDF"),
        ),
    ),
    ;

    companion object {
        fun of(route: String?): Area? = entries.firstOrNull { a -> a.tabs.any { it.route == route } }
    }
}

private data class MenuEntry(val label: String, val icon: ImageVector, val route: String, val topLevel: Boolean)

/** Menu ☰: atalhos e todas as telas, agrupadas por área. */
private val menuGroups = listOf(
    "Atalhos" to listOf(
        MenuEntry("Nova venda", Icons.Filled.Add, Routes.newSale(), false),
        MenuEntry("Nova nota fiscal", Icons.Filled.Email, Routes.INVOICE_NEW, false),
    ),
    "Vendas" to listOf(
        MenuEntry("Vendas", Icons.Filled.ShoppingCart, Routes.SALES, true),
        MenuEntry("Baterias na carga", Icons.Filled.Build, Routes.CHARGES, true),
        MenuEntry("Vales de casco", Icons.Filled.Star, Routes.VOUCHERS, true),
    ),
    "Estoque" to listOf(
        MenuEntry("Baterias", AppIcons.Battery, Routes.STOCK, true),
        MenuEntry("Sucatas", Icons.Filled.Refresh, Routes.SCRAPS, true),
        MenuEntry("Tabela de sucatas", Icons.Filled.Edit, Routes.SCRAP_PRICES, false),
        MenuEntry("Garantias e extras", Icons.Filled.CheckCircle, Routes.WARRANTIES, true),
        MenuEntry("Movimentações de estoque", Icons.AutoMirrored.Filled.List, Routes.MOVEMENTS, false),
    ),
    "Dinheiro" to listOf(
        MenuEntry("Resumo financeiro", Icons.Filled.Info, Routes.FINANCE, true),
        MenuEntry("Notas fiscais e boletos", Icons.Filled.Email, Routes.INVOICES, true),
        MenuEntry("Despesas", Icons.Filled.DateRange, Routes.EXPENSES, true),
        MenuEntry("Relatórios e PDF", AppIcons.BarChart, Routes.REPORTS, true),
    ),
    "Sistema" to listOf(
        MenuEntry("Backup, sincronização e atualizações", Icons.Filled.Settings, Routes.BACKUP, false),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LojaNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val currentArea = Area.of(currentRoute)
    val showBottomBar = currentArea != null
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
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
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
              .let { if (currentArea != null && currentArea.tabs.size > 1) it.windowInsetsPadding(WindowInsets.statusBars) else it },
      ) {
        // Abas da área (ligam as telas relacionadas)
        if (currentArea != null && currentArea.tabs.size > 1) {
            PrimaryScrollableTabRow(
                selectedTabIndex = currentArea.tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0),
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                currentArea.tabs.forEach { tab ->
                    Tab(
                        selected = tab.route == currentRoute,
                        onClick = { if (tab.route != currentRoute) nav.navigateTopLevel(tab.route) },
                        text = { Text(tab.label, maxLines = 1, style = MaterialTheme.typography.titleSmall) },
                    )
                }
            }
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
                    onBackup = { nav.navigate(Routes.BACKUP) },
                    onCharges = { nav.navigateTopLevel(Routes.CHARGES) },
                    onInvoices = { nav.navigateTopLevel(Routes.INVOICES) },
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
                    onMovements = { nav.navigate(Routes.MOVEMENTS) },
                )
            }
            composable(Routes.REPORTS) {
                ReportsScreen()
            }
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
            composable(Routes.MOVEMENTS) {
                MovementsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.BACKUP) {
                BackupScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.CHARGES) {
                ChargesScreen(onNew = { nav.navigate(Routes.CHARGE_NEW) }, onOpen = { nav.navigate(Routes.chargeDetail(it)) })
            }
            composable(Routes.CHARGE_NEW) {
                ChargeFormScreen(chargeId = null, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
            }
            composable(Routes.CHARGE_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                ChargeFormScreen(chargeId = id, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
            }
            composable(Routes.CHARGE_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                ChargeDetailScreen(chargeId = id, onEdit = { nav.navigate(Routes.chargeEdit(id)) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.WARRANTIES) { WarrantiesScreen() }
            composable(Routes.VOUCHERS) { VouchersScreen() }
            composable(Routes.EXPENSES) { ExpensesScreen() }
            composable(Routes.FINANCE) { FinanceScreen() }
            composable(Routes.INVOICES) {
                InvoicesScreen(onNew = { nav.navigate(Routes.INVOICE_NEW) }, onOpen = { nav.navigate(Routes.invoiceDetail(it)) })
            }
            composable(Routes.INVOICE_NEW) {
                InvoiceFormScreen(invoiceId = null, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
            }
            composable(Routes.INVOICE_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                InvoiceFormScreen(invoiceId = id, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
            }
            composable(Routes.INVOICE_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                InvoiceDetailScreen(invoiceId = id, onEdit = { nav.navigate(Routes.invoiceEdit(id)) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.SCRAPS) {
                ScrapsScreen(onPriceTable = { nav.navigate(Routes.SCRAP_PRICES) })
            }
            composable(Routes.SCRAP_PRICES) {
                ScrapPricesScreen(onBack = { nav.popBackStack() })
            }
        }
      }
      }
    }

    if (menuOpen) {
        ModalBottomSheet(onDismissRequest = { menuOpen = false }) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    "Art das Baterias",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                menuGroups.forEach { (title, entries) ->
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 4.dp),
                    )
                    entries.forEach { entry ->
                        NavigationDrawerItem(
                            label = { Text(entry.label, style = MaterialTheme.typography.titleMedium) },
                            icon = { Icon(entry.icon, contentDescription = null) },
                            selected = currentRoute == entry.route,
                            onClick = {
                                menuOpen = false
                                if (entry.topLevel) nav.navigateTopLevel(entry.route) else nav.navigate(entry.route)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
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
