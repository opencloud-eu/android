package eu.opencloud.android.ui.activity

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.Px
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentActivity

fun FragmentActivity.enableEdgeToEdgePreSetContentView(
    isNavigationBackgroundPrimary: Boolean
) {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle =
            if (isNavigationBackgroundPrimary)
                SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
    )
}

fun FragmentActivity.enableEdgeToEdgePostSetContentView(onInsets: (Insets) -> Unit) {
    ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout(),
        )
        onInsets(insets)
        WindowInsetsCompat.CONSUMED
    }
}

fun FragmentActivity.getActionBarSize(): Int {
    val tv = TypedValue()
    if (theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
        return TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
    }
    return 0
}

fun View.updatePaddingTop(@Px paddingTop: Int) {
    updatePadding(top = paddingTop)
}

fun View.updatePaddingBottom(@Px paddingBottom: Int) {
    updatePadding(bottom = paddingBottom)
}