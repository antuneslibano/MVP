package br.com.lojabaterias.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
fun LojaNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topLevel.any { it.route == currentRoute }

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
