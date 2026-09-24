package br.com.lojabaterias.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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

    fun newSale(productId: Long? = null) = if (productId == null) "newsale" else "newsale?productId=$productId"
    fun saleDetail(id: Long) = "saledetail/$id"
    fun saleEdit(id: Long) = "saleedit/$id"
    fun productDetail(id: Long) = "productdetail/$id"
    fun productEdit(id: Long) = "productedit/$id"
}

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(Routes.HOME, "Início", Icons.Filled.Home),
    TopLevel(Routes.SALES, "Vendas", Icons.Filled.ShoppingCart),
    TopLevel(Routes.STOCK, "Estoque", AppIcons.Battery),
    TopLevel(Routes.REPORTS, "Relatórios", AppIcons.BarChart),
)

/** Telas principais que exibem a barra inferior mas ficam no menu (não têm botão próprio). */
private val menuOnlyTopLevel = setOf(Routes.SCRAPS)

private data class MenuEntry(val label: String, val icon: ImageVector, val route: String, val topLevel: Boolean)

private val menuEntries = listOf(
    MenuEntry("Início", Icons.Filled.Home, Routes.HOME, true),
    MenuEntry("Nova venda", Icons.Filled.Add, Routes.newSale(), false),
    MenuEntry("Vendas", Icons.Filled.ShoppingCart, Routes.SALES, true),
    MenuEntry("Estoque de baterias", AppIcons.Battery, Routes.STOCK, true),
    MenuEntry("Sucatas", Icons.Filled.Refresh, Routes.SCRAPS, true),
    MenuEntry("Relatórios", AppIcons.BarChart, Routes.REPORTS, true),
    MenuEntry("Movimentações de estoque", Icons.AutoMirrored.Filled.List, Routes.MOVEMENTS, false),
    MenuEntry("Tabela de sucatas", Icons.Filled.Edit, Routes.SCRAP_PRICES, false),
    MenuEntry("Backup dos dados", Icons.Filled.Settings, Routes.BACKUP, false),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LojaNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topLevel.any { it.route == currentRoute } || currentRoute in menuOnlyTopLevel
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { nav.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                    NavigationBarItem(
                        selected = currentRoute in menuOnlyTopLevel,
                        onClick = { menuOpen = true },
                        icon = { Icon(Icons.Filled.Menu, contentDescription = null) },
                        label = { Text("Menu") },
                    )
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = inner.calculateBottomPadding()),
            enterTransition = { androidx.compose.animation.EnterTransition.None },
            exitTransition = { androidx.compose.animation.ExitTransition.None },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNewSale = { nav.navigate(Routes.newSale()) },
                    onOpenSale = { nav.navigate(Routes.saleDetail(it)) },
                    onSeeAllSales = { nav.navigateTopLevel(Routes.SALES) },
                    onBackup = { nav.navigate(Routes.BACKUP) },
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
            composable(Routes.SCRAPS) {
                ScrapsScreen(onPriceTable = { nav.navigate(Routes.SCRAP_PRICES) })
            }
            composable(Routes.SCRAP_PRICES) {
                ScrapPricesScreen(onBack = { nav.popBackStack() })
            }
        }
    }

    if (menuOpen) {
        ModalBottomSheet(onDismissRequest = { menuOpen = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Art das Baterias",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                menuEntries.forEach { entry ->
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

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
