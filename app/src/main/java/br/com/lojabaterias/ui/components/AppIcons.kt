package br.com.lojabaterias.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** Ícones extras desenhados localmente (evita a biblioteca "icons-extended", que é pesada). */
object AppIcons {

    val BarChart: ImageVector by lazy {
        materialIcon(name = "Loja.BarChart") {
            materialPath {
                moveTo(5f, 9.2f); horizontalLineToRelative(3f); verticalLineTo(19f); horizontalLineTo(5f); close()
                moveTo(10.6f, 5f); horizontalLineToRelative(2.8f); verticalLineToRelative(14f); horizontalLineToRelative(-2.8f); close()
                moveTo(16.2f, 13f); horizontalLineTo(19f); verticalLineToRelative(6f); horizontalLineToRelative(-2.8f); close()
            }
        }
    }

    val Battery: ImageVector by lazy {
        materialIcon(name = "Loja.Battery") {
            materialPath {
                // Corpo
                moveTo(3f, 7f); horizontalLineTo(21f); verticalLineTo(19f); horizontalLineTo(3f); close()
                // Polos
                moveTo(5.5f, 4f); horizontalLineTo(9f); verticalLineTo(6f); horizontalLineTo(5.5f); close()
                moveTo(15f, 4f); horizontalLineTo(18.5f); verticalLineTo(6f); horizontalLineTo(15f); close()
            }
        }
    }
}
