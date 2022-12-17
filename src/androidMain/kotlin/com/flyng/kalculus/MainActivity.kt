package com.flyng.kalculus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.livedata.observeAsState
import com.flyng.kalculus.exposition.visual.primitive.Color
import com.flyng.kalculus.ingredient.conic.Circle2D
import com.flyng.kalculus.ingredient.grid.Grid2D
import com.flyng.kalculus.theme.KalculusTheme
import com.flyng.kalculus.theme.ThemeMode
import com.flyng.kalculus.theme.ThemeProfile
import com.flyng.kalculus.ui.KalculusScreen
import com.google.android.filament.Filament

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val vm: MainViewModel by viewModels(
            factoryProducer = { MainViewModel.Factory(this, this, assets) }
        )

        setContent {
            KalculusTheme(
                profile = vm.profile.observeAsState(ThemeProfile.Firewater).value,
                themeMode = vm.mode.observeAsState(ThemeMode.Light).value
            ) {
                KalculusScreen(
                    surfaceView = vm.core.surfaceView,
                    vm = vm
                ) {

                }
            }
        }

        val color = vm.core.themeManager.baseColor().let {
            Color(it.red, it.green, it.blue, it.alpha)
        }

        val circle2D = Circle2D.Builder()
            .center(0, 0)
            .radius(1.0f)
            .strokeWidth(0.2f)
            .color(color.copy(alpha = 0.1f))
            .build()

        val grid = Grid2D.Builder()
            .center(0, 0)
            .spacing(1.0f)
            .color(color)
            .build()

        vm.core.render(circle2D)
        vm.core.render(grid)
    }

    companion object {
        init {
            // load the JNI library needed by most API calls
            Filament.init()
        }
    }
}
