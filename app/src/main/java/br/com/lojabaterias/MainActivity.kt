package br.com.lojabaterias

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.lojabaterias.ui.LojaNavHost
import br.com.lojabaterias.ui.theme.LojaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LojaTheme {
                LojaNavHost()
            }
        }
    }
}
